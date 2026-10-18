package tk.slicecollections.maxteer.skins;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.database.Database;

import javax.sql.rowset.CachedRowSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class SkinHistoryManager {
  private static final long RETENTION_MILLIS = TimeUnit.DAYS.toMillis(3);

  private final Core plugin;
  private final ExecutorService reader;
  private BukkitTask cleanupTask;

  public SkinHistoryManager(Core plugin) {
    this.plugin = plugin;
    this.reader = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "mCore-skin-history-reader");
      thread.setDaemon(true);
      return thread;
    });
  }

  public void start() {
    cleanupExpired();
    this.cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
        plugin, this::cleanupExpired, 20L * 60L * 30L, 20L * 60L * 30L);
  }

  public CompletableFuture<Void> recordSelection(UUID playerUuid, String playerName,
                                                  SkinDefinition skin, String value, String signature) {
    Database database = Database.getInstance();
    if (database == null) {
      return failedFuture(new IllegalStateException("mCore database is not initialized."));
    }

    long now = System.currentTimeMillis();
    long expiresAt = now + RETENTION_MILLIS;
    String uuid = playerUuid.toString();

    CompletableFuture<Void> saveCurrent = database.execute(
        "INSERT INTO mCorePlayerSkin (player_uuid, player_name, skin_id, skin_name, skin_owner, texture_value, texture_signature, selected_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
        "player_name = VALUES(player_name), skin_id = VALUES(skin_id), skin_name = VALUES(skin_name), " +
        "skin_owner = VALUES(skin_owner), texture_value = VALUES(texture_value), " +
        "texture_signature = VALUES(texture_signature), selected_at = VALUES(selected_at)",
        uuid, playerName, skin.getId(), skin.getDisplayName(), skin.getOwnerName(), value, signature, now);

    CompletableFuture<Void> saved = saveCurrent.thenCompose(ignored -> database.execute(
        "INSERT INTO mCoreSkinHistory (player_uuid, player_name, skin_id, skin_name, skin_owner, texture_value, texture_signature, used_at, expires_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
        "player_name = VALUES(player_name), skin_name = VALUES(skin_name), skin_owner = VALUES(skin_owner), " +
        "texture_value = VALUES(texture_value), texture_signature = VALUES(texture_signature), " +
        "used_at = VALUES(used_at), expires_at = VALUES(expires_at)",
        uuid, playerName, skin.getId(), skin.getDisplayName(), skin.getOwnerName(), value, signature, now, expiresAt
    )).thenCompose(ignored -> database.execute(
        "DELETE FROM mCoreSkinHistory WHERE player_uuid = ? AND expires_at <= ?", uuid, now
    )).thenCompose(ignored -> database.execute(
        "DELETE FROM mCoreSkinHistory WHERE player_uuid = ? AND id NOT IN (" +
        "SELECT id FROM (SELECT id FROM mCoreSkinHistory WHERE player_uuid = ? AND expires_at > ? " +
        "ORDER BY used_at DESC, id DESC LIMIT 7) AS recent_skins)",
        uuid, uuid, now
    ));

    saved.whenComplete((ignored, failure) -> {
      if (failure != null) {
        plugin.getLogger().log(Level.SEVERE,
            "Could not persist skin selection/history for " + playerName + ".", failure);
      }
    });
    return saved;
  }

  public CompletableFuture<List<SkinHistoryEntry>> loadHistory(UUID playerUuid) {
    return CompletableFuture.supplyAsync(() -> {
      Database database = Database.getInstance();
      if (database == null) {
        throw new CompletionException(new IllegalStateException("mCore database is not initialized."));
      }
      CachedRowSet rows = database.query(
          "SELECT skin_id, skin_name, skin_owner, texture_value, texture_signature, used_at " +
          "FROM mCoreSkinHistory WHERE player_uuid = ? AND expires_at > ? " +
          "ORDER BY used_at DESC, id DESC LIMIT 7",
          playerUuid.toString(), System.currentTimeMillis());
      return readEntries(rows, "used_at");
    }, reader);
  }

  public CompletableFuture<SkinHistoryEntry> loadSelectedSkin(UUID playerUuid) {
    return CompletableFuture.supplyAsync(() -> {
      Database database = Database.getInstance();
      if (database == null) {
        throw new CompletionException(new IllegalStateException("mCore database is not initialized."));
      }
      CachedRowSet rows = database.query(
          "SELECT skin_id, skin_name, skin_owner, texture_value, texture_signature, selected_at " +
          "FROM mCorePlayerSkin WHERE player_uuid = ?", playerUuid.toString());
      List<SkinHistoryEntry> entries = readEntries(rows, "selected_at");
      return entries.isEmpty() ? null : entries.get(0);
    }, reader);
  }

  private List<SkinHistoryEntry> readEntries(CachedRowSet rows, String timestampColumn) {
    if (rows == null) {
      return Collections.emptyList();
    }
    List<SkinHistoryEntry> result = new ArrayList<>();
    try (CachedRowSet closeable = rows) {
      do {
        result.add(new SkinHistoryEntry(
            closeable.getString("skin_id"),
            closeable.getString("skin_name"),
            closeable.getString("skin_owner"),
            closeable.getString("texture_value"),
            closeable.getString("texture_signature"),
            closeable.getLong(timestampColumn)));
      } while (closeable.next());
    } catch (SQLException ex) {
      throw new CompletionException(ex);
    }
    return Collections.unmodifiableList(result);
  }

  private void cleanupExpired() {
    Database database = Database.getInstance();
    if (database == null) {
      return;
    }
    database.execute("DELETE FROM mCoreSkinHistory WHERE expires_at <= ?", System.currentTimeMillis())
        .whenComplete((ignored, failure) -> {
          if (failure != null && plugin.isEnabled()) {
            plugin.getLogger().log(Level.WARNING, "Could not delete expired skin history.", failure);
          }
        });
  }

  public void shutdown() {
    if (this.cleanupTask != null) {
      this.cleanupTask.cancel();
      this.cleanupTask = null;
    }
    this.reader.shutdownNow();
  }

  private static CompletableFuture<Void> failedFuture(Throwable failure) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    future.completeExceptionally(failure);
    return future;
  }
}
