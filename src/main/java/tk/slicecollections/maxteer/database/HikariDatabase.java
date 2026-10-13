package tk.slicecollections.maxteer.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import tk.slicecollections.maxteer.Manager;
import tk.slicecollections.maxteer.booster.NetworkBooster;
import tk.slicecollections.maxteer.database.cache.RoleCache;
import tk.slicecollections.maxteer.database.data.DataContainer;
import tk.slicecollections.maxteer.database.data.DataTable;
import tk.slicecollections.maxteer.database.exception.ProfileLoadException;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.StringUtils;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author Maxter
 */
public class HikariDatabase extends Database {

  private String host;
  private String port;
  private String dbname;
  private String username;
  private String password;
  private HikariDataSource dataSource;
  private ThreadPoolExecutor executor;
  private final Object saveSubmissionLock = new Object();
  private final Object pendingSaveLock = new Object();
  private final AtomicLong saveSequence = new AtomicLong();
  private final ConcurrentMap<String, PendingProfileSave> failedProfileSaves = new ConcurrentHashMap<>();
  private final AtomicBoolean retryScheduled = new AtomicBoolean();

  public HikariDatabase(String host, String port, String dbname, String username, String password) {
    this.host = host;
    this.port = port;
    this.dbname = dbname;
    this.username = username;
    this.password = password;
    this.openConnection();
    this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(1024), runnable -> {
        Thread thread = new Thread(runnable, "mCore-db-writer");
        thread.setDaemon(true);
        return thread;
      }, new ThreadPoolExecutor.AbortPolicy());

    try {
      for (DataTable table : DataTable.listTables()) {
        this.updateOrThrow(table.getInfo().create());
      }
      SchemaMigrator.migrate(this);
      this.retryPendingSavesFromDisk();
    } catch (SQLException ex) {
      this.executor.shutdownNow();
      this.closeConnection();
      throw new IllegalStateException("Could not create the mCore base schema.", ex);
    } catch (RuntimeException ex) {
      this.executor.shutdownNow();
      this.closeConnection();
      throw ex;
    }
  }

  @Override
  public void setupBoosters() {
    if (Manager.BUNGEE) {
      return;
    }

    try {
      for (String minigame : tk.slicecollections.maxteer.Core.minigames) {
        this.updateOrThrow(
          "INSERT IGNORE INTO `mCoreNetworkBooster` (`id`, `booster`, `multiplier`, `expires`) VALUES (?, ?, ?, ?)",
          minigame, "Maxteer", 1.0, 0L
        );
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Could not initialize network booster records.", ex);
    }
  }

  @Override
  public void setBooster(String minigame, String booster, double multiplier, long expires) {
    execute("UPDATE `mCoreNetworkBooster` SET `booster` = ?, `multiplier` = ?, `expires` = ? WHERE `id` = ?", booster, multiplier, expires, minigame);
  }

  @Override
  public NetworkBooster getBooster(String minigame) {
    try (CachedRowSet rs = query("SELECT * FROM `mCoreNetworkBooster` WHERE `id` = ?", minigame)) {
      if (rs != null) {
        String booster = rs.getString("booster");
        double multiplier = rs.getDouble("multiplier");
        long expires = rs.getLong("expires");
        if (expires > System.currentTimeMillis()) {
          rs.close();
          return new NetworkBooster(booster, multiplier, expires);
        }
      }
    } catch (SQLException ignored) {}

    return null;
  }

  @Override
  public String getRankAndName(String player) {
    String cached = RoleCache.get(player);
    if (cached != null) {
      return cached;
    }

    try (CachedRowSet rs = query("SELECT `name`, `role` FROM `mCoreProfile` WHERE LOWER(`name`) = ?", player.toLowerCase(Locale.ROOT))) {
      if (rs != null) {
        String result = rs.getString("role") + " : " + rs.getString("name");
        RoleCache.setCache(player, rs.getString("role"), rs.getString("name"));
        return result;
      }
    } catch (SQLException ignored) {}
    return null;
  }

  @Override
  public boolean getPreference(String player, String id, boolean def) {
    try (CachedRowSet rs = query("SELECT `preferences` FROM `mCoreProfile` WHERE LOWER(`name`) = ?", player.toLowerCase(Locale.ROOT))) {
      if (rs == null) {
        return def;
      }

      final Object parsed;
      try {
        parsed = new JSONParser().parse(rs.getString("preferences"));
      } catch (org.json.simple.parser.ParseException ex) {
        throw new IllegalStateException("Stored preferences JSON is invalid for player '" + player + "'.", ex);
      }
      if (!(parsed instanceof JSONObject)) {
        throw new IllegalStateException("Stored preferences value is not a JSON object for player '" + player + "'.");
      }

      Object value = ((JSONObject) parsed).get(id);
      if (value instanceof Number) {
        return ((Number) value).longValue() == 0L;
      }
      return def;
    } catch (SQLException ex) {
      throw new IllegalStateException("Could not read preferences for player '" + player + "'.", ex);
    }
  }

  @Override
  public CompletableFuture<Boolean> getPreferenceAsync(String player, String id, boolean def) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    try {
      this.executor.execute(() -> {
        try {
          future.complete(this.getPreference(player, id, def));
        } catch (Exception ex) {
          future.completeExceptionally(ex);
        }
      });
    } catch (RejectedExecutionException ex) {
      future.completeExceptionally(ex);
    }
    return future;
  }

  @Override
  public List<String[]> getLeaderBoard(String table, String... columns) {
    List<String[]> result = new ArrayList<>();
    if (columns == null || columns.length == 0) {
      return result;
    }

    final String safeTable;
    final List<String> safeColumns;
    try {
      safeTable = SqlIdentifier.table(table);
      safeColumns = SqlIdentifier.columns(safeTable, columns);
    } catch (IllegalArgumentException ex) {
      LOGGER.log(Level.WARNING, "Rejected invalid leaderboard table/column identifier.", ex);
      return result;
    }

    StringBuilder add = new StringBuilder(), select = new StringBuilder();
    for (String column : safeColumns) {
      add.append('`').append(column).append("` + ");
      select.append('`').append(column).append("`, ");
    }

    try (CachedRowSet rs = query("SELECT " + select + "`name` FROM `" + safeTable + "` ORDER BY " + add + " 0 DESC LIMIT 10")) {
      if (rs != null) {
        rs.beforeFirst();
        while (rs.next()) {
          long count = 0;
          for (String column : safeColumns) {
            count += rs.getLong(column);
          }
          result.add(new String[] {Role.getColored(rs.getString("name"), true), StringUtils.formatNumber(count)});
        }
      }
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Could not load leaderboard for table '" + safeTable + "'.", ex);
    }
    return result;
  }

  @Override
  public void close() {
    this.executor.shutdown();
    boolean terminated = false;
    try {
      terminated = this.executor.awaitTermination(30, TimeUnit.SECONDS);
      if (!terminated) {
        List<Runnable> pending = this.executor.shutdownNow();
        for (Runnable runnable : pending) {
          if (runnable instanceof WriteTask) {
            ((WriteTask) runnable).fail(new IllegalStateException("Database shut down before the queued operation could run."));
          }
        }
        terminated = this.executor.awaitTermination(2, TimeUnit.SECONDS);
      }
    } catch (InterruptedException ex) {
      List<Runnable> pending = this.executor.shutdownNow();
      for (Runnable runnable : pending) {
        if (runnable instanceof WriteTask) {
          ((WriteTask) runnable).fail(ex);
        }
      }
      Thread.currentThread().interrupt();
      LOGGER.log(Level.WARNING, "Interrupted while stopping the database writer.", ex);
    } finally {
      if (terminated || this.executor.isTerminated()) {
        this.retryFailedProfileSavesOnShutdown();
      } else if (!this.failedProfileSaves.isEmpty()) {
        LOGGER.severe("Database writer did not terminate; retained profile saves could not be retried safely.");
      }
      if (!this.failedProfileSaves.isEmpty()) {
        LOGGER.severe("Some profile snapshots remain only in memory because pending-save storage is unavailable.");
      }
      if (this.hasPendingSaveFiles()) {
        LOGGER.severe("Pending profile saves were preserved under plugins/mCore/pending-saves for retry on the next startup.");
      }
      this.closeConnection();
      Database.stopBackgroundTasks();
    }
  }

  @Override
  public Map<String, Map<String, DataContainer>> load(String name) throws ProfileLoadException  {
    Map<String, Map<String, DataContainer>> tableMap = new HashMap<>();
    for (DataTable table : DataTable.listTables()) {
      String tableName = table.getInfo().name();
      Map<String, DataContainer> rows = new LinkedHashMap<>();
      tableMap.put(tableName, rows);

      try (CachedRowSet result = this.query(table.getInfo().select(), name.toLowerCase(Locale.ROOT))) {
        if (result != null) {
          this.populateTableRow(rows, result);
          continue;
        }
      } catch (SQLException ex) {
        throw new ProfileLoadException("Could not read table " + tableName + " for profile " + name + ".", ex);
      } catch (RuntimeException ex) {
        throw new ProfileLoadException("Database query failed while loading table " + tableName + " for profile " + name + ".", ex);
      }

      rows = table.getDefaultValues();
      tableMap.put(tableName, rows);
      List<Object> values = new ArrayList<>();
      values.add(name);
      values.addAll(rows.values().stream().map(DataContainer::get).collect(Collectors.toList()));

      try {
        this.updateOrThrow(table.getInfo().insert(), values.toArray());
      } catch (SQLException insertError) {
        // Two server instances may race to create the same profile; accept that only if the row exists.
        try (CachedRowSet existing = this.query(table.getInfo().select(), name.toLowerCase(Locale.ROOT))) {
          if (existing == null) {
            throw insertError;
          }
          this.populateTableRow(rows, existing);
          continue;
        } catch (SQLException | RuntimeException rereadError) {
          insertError.addSuppressed(rereadError);
          throw new ProfileLoadException("Could not create or re-read table " + tableName + " for profile " + name + ".", insertError);
        }
      }

      try (CachedRowSet inserted = this.query(table.getInfo().select(), name.toLowerCase(Locale.ROOT))) {
        if (inserted == null) {
          throw new SQLException("Inserted profile row could not be read back.");
        }
        this.populateTableRow(rows, inserted);
      } catch (SQLException ex) {
        throw new ProfileLoadException("Could not verify inserted row in table " + tableName + " for profile " + name + ".", ex);
      } catch (RuntimeException ex) {
        throw new ProfileLoadException("Database verification failed for table " + tableName + " and profile " + name + ".", ex);
      }
    }

    return tableMap;
  }

  private void populateTableRow(Map<String, DataContainer> rows, CachedRowSet result) throws SQLException {
    rows.clear();
    for (int column = 2; column <= result.getMetaData().getColumnCount(); column++) {
      rows.put(result.getMetaData().getColumnName(column), new DataContainer(result.getObject(column)));
    }
  }

  public void save(String name, Map<String, Map<String, DataContainer>> tableMap) {
    this.saveAsync(name, tableMap);
  }

  @Override
  public CompletableFuture<Void> saveAsync(String name, Map<String, Map<String, DataContainer>> tableMap) {
    if (name == null || tableMap == null) {
      return CompletableFuture.completedFuture(null);
    }

    synchronized (this.saveSubmissionLock) {
      List<TableSnapshot> snapshot = this.captureSnapshot(tableMap);
      if (snapshot.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }
      PendingProfileSave pending = new PendingProfileSave(name, name.toLowerCase(Locale.ROOT),
        this.saveSequence.incrementAndGet(), snapshot);
      CompletableFuture<Void> future = this.submitWrite("save profile " + name, () -> this.persistProfileSave(pending));
      future.whenComplete((ignored, failure) -> {
        if (failure != null) {
          this.retainFailedSave(pending);
        }
      });
      return future;
    }
  }

  @Override
  public void saveSync(String name, Map<String, Map<String, DataContainer>> tableMap) {
    this.saveSyncChecked(name, tableMap);
  }

  @Override
  public boolean saveSyncChecked(String name, Map<String, Map<String, DataContainer>> tableMap) {
    if (name == null || tableMap == null) {
      return true;
    }

    synchronized (this.saveSubmissionLock) {
      List<TableSnapshot> snapshot = this.captureSnapshot(tableMap);
      if (snapshot.isEmpty()) {
        return true;
      }

      PendingProfileSave pending = new PendingProfileSave(name, name.toLowerCase(Locale.ROOT),
        this.saveSequence.incrementAndGet(), snapshot);
      CompletableFuture<Void> future = this.submitWrite("synchronous save profile " + name,
        () -> this.persistProfileSave(pending));
      future.whenComplete((ignored, failure) -> {
        if (failure != null) {
          this.retainFailedSave(pending);
        }
      });
      try {
        future.get();
        return true;
      } catch (InterruptedException ex) {
        this.retainFailedSave(pending);
        Thread.currentThread().interrupt();
        LOGGER.log(Level.WARNING, "Interrupted while synchronously saving profile '" + name + "'.", ex);
        return false;
      } catch (java.util.concurrent.ExecutionException ex) {
        this.retainFailedSave(pending);
        LOGGER.log(Level.WARNING, "Could not synchronously save profile '" + name + "'.", ex.getCause());
        return false;
      }
    }
  }


  private List<TableSnapshot> captureSnapshot(Map<String, Map<String, DataContainer>> tableMap) {
    List<TableSnapshot> snapshot = new ArrayList<>();
    for (DataTable table : DataTable.listTables()) {
      Map<String, DataContainer> rows = tableMap.get(table.getInfo().name());
      if (rows == null || rows.isEmpty()) {
        continue;
      }
      Map<String, PendingValue> changed = new LinkedHashMap<>();
      for (Map.Entry<String, DataContainer> entry : rows.entrySet()) {
        DataContainer container = entry.getValue();
        synchronized (container) {
          if (container.isUpdated()) {
            changed.put(entry.getKey(), new PendingValue(container, container.get()));
          }
        }
      }
      if (!changed.isEmpty()) {
        snapshot.add(new TableSnapshot(table.getInfo().name(), changed));
      }
    }
    return snapshot;
  }

  private void persistSnapshot(String name, List<TableSnapshot> snapshot) throws SQLException {
    try (Connection connection = getConnection()) {
      boolean originalAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        for (TableSnapshot table : snapshot) {
          StringBuilder sql = new StringBuilder("UPDATE ").append(quoteIdentifier(table.name)).append(" SET ");
          for (String column : table.values.keySet()) {
            sql.append(quoteIdentifier(column)).append(" = ?, ");
          }
          sql.setLength(sql.length() - 2);
          sql.append(" WHERE LOWER(`name`) = ?");
          try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (PendingValue field : table.values.values()) {
              statement.setObject(index++, field.value);
            }
            statement.setString(index, name.toLowerCase(Locale.ROOT));
            int affected = statement.executeUpdate();
            if (affected != 1) {
              throw new SQLException("Profile update expected one row in " + table.name + " but affected " + affected + " row(s).");
            }
          }
        }
        connection.commit();
      } catch (SQLException ex) {
        try {
          connection.rollback();
        } catch (SQLException rollbackError) {
          ex.addSuppressed(rollbackError);
        }
        throw ex;
      } finally {
        try {
          connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException resetError) {
          LOGGER.log(Level.WARNING, "Could not reset auto-commit on a pooled connection.", resetError);
        }
      }
    }
    if (snapshot.stream().anyMatch(table -> "mCoreProfile".equals(table.name))) {
      RoleCache.invalidate(name);
    }
  }

  private void acknowledgeSnapshot(List<TableSnapshot> snapshot) {
    for (TableSnapshot table : snapshot) {
      for (PendingValue field : table.values.values()) {
        if (field.container == null) {
          continue;
        }
        synchronized (field.container) {
          if (Objects.equals(field.container.get(), field.value)) {
            field.container.setUpdated(false);
          }
        }
      }
    }
  }

  private void persistProfileSave(PendingProfileSave pending) throws SQLException {
    List<TableSnapshot> snapshot = pending.snapshot;
    long snapshotSequence = pending.sequence;
    PendingProfileSave retained = this.failedProfileSaves.get(pending.key);
    if (retained != null) {
      if (retained.sequence > snapshotSequence) {
        snapshot = mergeSnapshots(snapshot, retained.snapshot);
        snapshotSequence = retained.sequence;
      } else {
        snapshot = mergeSnapshots(retained.snapshot, snapshot);
      }
    }

    File pendingFile = null;
    String pendingContent = null;
    try {
      pendingFile = this.pendingSaveFile(pending.name);
      if (pendingFile.isFile()) {
        pendingContent = new String(Files.readAllBytes(pendingFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        PendingProfileSave onDisk = this.readPendingSave(pendingFile, pendingContent);
        if (onDisk.sequence > snapshotSequence) {
          snapshot = mergeSnapshots(snapshot, onDisk.snapshot);
          snapshotSequence = onDisk.sequence;
        } else {
          snapshot = mergeSnapshots(onDisk.snapshot, snapshot);
        }
      }
    } catch (IOException ex) {
      LOGGER.log(Level.SEVERE, "Could not read a retained profile snapshot; leaving its file untouched.", ex);
      pendingFile = null;
      pendingContent = null;
    }

    this.persistSnapshot(pending.name, snapshot);
    this.acknowledgeSnapshot(pending.snapshot);
    if (retained != null) {
      this.failedProfileSaves.remove(pending.key, retained);
    }
    if (pendingFile != null && pendingContent != null) {
      try {
        this.deletePendingFileIfUnchanged(pendingFile, pendingContent);
      } catch (IOException ex) {
        LOGGER.log(Level.WARNING, "Profile data was saved, but its pending file could not be removed; refreshing the recovery file.", ex);
        try {
          this.storePendingSave(new PendingProfileSave(pending.name, pending.key, snapshotSequence, snapshot));
        } catch (IOException recoveryError) {
          ex.addSuppressed(recoveryError);
          LOGGER.log(Level.SEVERE, "Could not refresh the pending profile recovery file.", ex);
        }
      }
    }
  }

  /** Called by the role-cache timer; retry work is serialized behind profile writes. */
  public void retryPendingSavesAsync() {
    if ((this.failedProfileSaves.isEmpty() && !this.hasPendingSaveFiles())
        || !this.retryScheduled.compareAndSet(false, true)) {
      return;
    }

    CompletableFuture<Void> retry = this.submitWrite("retry retained profile saves", this::retryPendingSaves);
    retry.whenComplete((ignored, failure) -> {
      this.retryScheduled.set(false);
      if (failure != null) {
        LOGGER.log(Level.WARNING, "Could not schedule retry of retained profile saves.", failure);
      }
    });
  }

  private void retryPendingSaves() {
    // Spill in-memory fallbacks to disk first so RAM remains bounded during long outages.
    for (PendingProfileSave pending : new ArrayList<>(this.failedProfileSaves.values())) {
      if (this.failedProfileSaves.get(pending.key) != pending) {
        continue;
      }
      try {
        this.storePendingSave(pending);
        this.failedProfileSaves.remove(pending.key, pending);
      } catch (IOException ex) {
        LOGGER.log(Level.SEVERE, "Could not move pending profile data to disk for " + pending.name + ".", ex);
      }
    }

    this.retryPendingSavesFromDisk();

    // If disk is unavailable, retain the in-memory fallback and keep retrying without discarding data.
    for (PendingProfileSave pending : new ArrayList<>(this.failedProfileSaves.values())) {
      if (this.failedProfileSaves.get(pending.key) != pending) {
        continue;
      }
      try {
        this.persistSnapshot(pending.name, pending.snapshot);
        this.acknowledgeSnapshot(pending.snapshot);
        this.failedProfileSaves.remove(pending.key, pending);
      } catch (SQLException ex) {
        LOGGER.log(Level.FINE, "Profile save remains pending for " + pending.name + ".", ex);
      }
    }
  }

  private void retryPendingSavesFromDisk() {
    File directory = new File("plugins/mCore/pending-saves");
    if (!directory.isDirectory()) {
      return;
    }
    File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
    if (files == null || files.length == 0) {
      return;
    }
    Arrays.sort(files, Comparator.comparing(File::getName));
    for (File file : files) {
      String content;
      PendingProfileSave pending;
      try {
        content = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        pending = this.readPendingSave(file, content);
      } catch (Exception ex) {
        LOGGER.log(Level.SEVERE, "Could not read pending profile save file " + file.getName() + "; preserving it for recovery.", ex);
        continue;
      }

      PendingProfileSave inMemory = this.failedProfileSaves.get(pending.key);
      List<TableSnapshot> snapshot = inMemory == null ? pending.snapshot
        : inMemory.sequence > pending.sequence
          ? mergeSnapshots(pending.snapshot, inMemory.snapshot)
          : mergeSnapshots(inMemory.snapshot, pending.snapshot);
      try {
        this.persistSnapshot(pending.name, snapshot);
        this.acknowledgeSnapshot(snapshot);
        boolean deleted = this.deletePendingFileIfUnchanged(file, content);
        if (deleted && inMemory != null) {
          this.failedProfileSaves.remove(pending.key, inMemory);
        }
      } catch (SQLException ex) {
        LOGGER.log(Level.FINE, "Disk-backed profile save remains pending for " + pending.name + ".", ex);
      } catch (IOException ex) {
        LOGGER.log(Level.WARNING, "Could not remove pending profile save file " + file.getName() + "; refreshing recovery data.", ex);
        long sequence = inMemory == null ? pending.sequence : Math.max(pending.sequence, inMemory.sequence);
        try {
          this.storePendingSave(new PendingProfileSave(pending.name, pending.key, sequence, snapshot));
          if (inMemory != null) {
            this.failedProfileSaves.remove(pending.key, inMemory);
          }
        } catch (IOException recoveryError) {
          ex.addSuppressed(recoveryError);
          LOGGER.log(Level.SEVERE, "Could not refresh pending profile recovery data; preserving the in-memory copy.", ex);
        }
      }
    }
  }

  private void retryFailedProfileSavesOnShutdown() {
    for (PendingProfileSave pending : new ArrayList<>(this.failedProfileSaves.values())) {
      try {
        this.storePendingSave(pending);
        this.failedProfileSaves.remove(pending.key, pending);
      } catch (IOException ex) {
        LOGGER.log(Level.SEVERE, "Could not durably retain profile data for " + pending.name + ".", ex);
      }
    }

    this.retryPendingSavesFromDisk();
    for (PendingProfileSave pending : new ArrayList<>(this.failedProfileSaves.values())) {
      try {
        this.persistSnapshot(pending.name, pending.snapshot);
        this.acknowledgeSnapshot(pending.snapshot);
        this.failedProfileSaves.remove(pending.key, pending);
      } catch (SQLException ex) {
        LOGGER.log(Level.SEVERE, "Profile data is still not persisted for " + pending.name + ".", ex);
      }
    }
  }

  private void retainFailedSave(PendingProfileSave pending) {
    synchronized (this.pendingSaveLock) {
      PendingProfileSave previous = this.failedProfileSaves.get(pending.key);
      PendingProfileSave merged = previous == null ? pending
        : new PendingProfileSave(pending.name, pending.key, Math.max(previous.sequence, pending.sequence),
          previous.sequence > pending.sequence
            ? mergeSnapshots(pending.snapshot, previous.snapshot)
            : mergeSnapshots(previous.snapshot, pending.snapshot));
      try {
        this.storePendingSave(merged);
        this.failedProfileSaves.remove(pending.key);
      } catch (IOException ex) {
        LOGGER.log(Level.SEVERE, "Could not write a durable pending profile save for " + pending.name
          + "; the data will remain in memory while disk storage is unavailable.", ex);
        this.failedProfileSaves.put(pending.key, merged);
      }
    }
  }

  private File pendingSaveFile(String name) throws IOException {
    String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (!key.matches("[a-z0-9_]{1,32}")) {
      throw new IOException("Invalid player key for pending profile save.");
    }
    File directory = new File("plugins/mCore/pending-saves");
    if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
      throw new IOException("Could not create pending profile save directory: " + directory.getAbsolutePath());
    }
    return new File(directory, key + ".json");
  }

  private boolean hasPendingSaveFiles() {
    File directory = new File("plugins/mCore/pending-saves");
    if (!directory.isDirectory()) {
      return false;
    }
    File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
    return files == null || files.length > 0;
  }

  @SuppressWarnings("unchecked")
  private void storePendingSave(PendingProfileSave pending) throws IOException {
    synchronized (this.pendingSaveLock) {
      File file = this.pendingSaveFile(pending.name);
      List<TableSnapshot> snapshot = pending.snapshot;
      long sequence = pending.sequence;
      if (file.isFile()) {
        String existingContent = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        PendingProfileSave existing = this.readPendingSave(file, existingContent);
        if (existing.sequence > pending.sequence) {
          snapshot = mergeSnapshots(snapshot, existing.snapshot);
          sequence = existing.sequence;
        } else {
          snapshot = mergeSnapshots(existing.snapshot, snapshot);
          sequence = Math.max(existing.sequence, pending.sequence);
        }
      }

      JSONObject root = new JSONObject();
      root.put("version", 2L);
      root.put("name", pending.name);
      root.put("sequence", sequence);
      JSONArray tables = new JSONArray();
      for (TableSnapshot table : snapshot) {
        JSONObject tableJson = new JSONObject();
        tableJson.put("name", table.name);
        JSONObject values = new JSONObject();
        for (Map.Entry<String, PendingValue> entry : table.values.entrySet()) {
          if (!entry.getKey().matches("[A-Za-z0-9_]+")) {
            throw new IOException("Invalid column name in pending profile save.");
          }
          values.put(entry.getKey(), entry.getValue().value);
        }
        tableJson.put("values", values);
        tables.add(tableJson);
      }
      root.put("tables", tables);
      this.writePendingFileAtomically(file, root.toJSONString());
    }
  }

  private PendingProfileSave readPendingSave(File file, String content) throws IOException {
    try {
      Object parsed = new JSONParser().parse(content);
      if (!(parsed instanceof JSONObject)) {
        throw new IOException("Invalid pending profile save document.");
      }
      JSONObject root = (JSONObject) parsed;
      Object version = root.get("version");
      if (!(version instanceof Number)) {
        throw new IOException("Unsupported pending profile save version.");
      }
      long formatVersion = ((Number) version).longValue();
      if (formatVersion != 1L && formatVersion != 2L) {
        throw new IOException("Unsupported pending profile save version.");
      }
      Object nameValue = root.get("name");
      if (!(nameValue instanceof String)) {
        throw new IOException("Pending profile save has no player name.");
      }
      String name = (String) nameValue;
      String key = name.toLowerCase(Locale.ROOT);
      if (!key.matches("[a-z0-9_]{1,32}")) {
        throw new IOException("Invalid player name in pending profile save.");
      }
      Object rawTables = root.get("tables");
      if (!(rawTables instanceof JSONArray)) {
        throw new IOException("Pending profile save has no tables.");
      }

      List<TableSnapshot> snapshot = new ArrayList<>();
      for (Object rawTable : (JSONArray) rawTables) {
        if (!(rawTable instanceof JSONObject)) {
          throw new IOException("Invalid table in pending profile save.");
        }
        JSONObject tableJson = (JSONObject) rawTable;
        Object tableNameValue = tableJson.get("name");
        if (!(tableNameValue instanceof String)) {
          throw new IOException("Pending profile save has an invalid table name.");
        }
        String tableName = (String) tableNameValue;
        boolean knownTable = DataTable.listTables().stream().anyMatch(table -> table.getInfo().name().equals(tableName));
        if (!knownTable) {
          throw new IOException("Unknown table in pending profile save: " + tableName);
        }
        Object rawValues = tableJson.get("values");
        if (!(rawValues instanceof JSONObject)) {
          throw new IOException("Pending profile save has invalid column values.");
        }
        Map<String, PendingValue> values = new LinkedHashMap<>();
        JSONObject valuesJson = (JSONObject) rawValues;
        for (Object rawEntry : valuesJson.entrySet()) {
          Map.Entry<?, ?> entry = (Map.Entry<?, ?>) rawEntry;
          if (!(entry.getKey() instanceof String) || !((String) entry.getKey()).matches("[A-Za-z0-9_]+")) {
            throw new IOException("Invalid column in pending profile save.");
          }
          Object value = entry.getValue();
          if (value instanceof JSONObject && ((JSONObject) value).containsKey("type")) {
            value = this.decodePendingValue((JSONObject) value);
          } else if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
            throw new IOException("Unsupported value type in pending profile save.");
          }
          values.put((String) entry.getKey(), new PendingValue(null, value));
        }
        if (!values.isEmpty()) {
          snapshot.add(new TableSnapshot(tableName, values));
        }
      }
      Object rawSequence = root.get("sequence");
      long sequence = rawSequence instanceof Number ? ((Number) rawSequence).longValue() : 0L;
      this.saveSequence.accumulateAndGet(sequence, Math::max);
      return new PendingProfileSave(name, key, sequence, snapshot);
    } catch (IOException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IOException("Could not parse pending profile save " + file.getName() + ".", ex);
    }
  }

  private Object decodePendingValue(JSONObject encoded) throws IOException {
    Object typeValue = encoded.get("type");
    Object rawValue = encoded.get("value");
    if (!(typeValue instanceof String) || !(rawValue instanceof String)) {
      throw new IOException("Invalid typed value in pending profile save.");
    }
    String value = (String) rawValue;
    switch ((String) typeValue) {
      case "null": return null;
      case "string": return value;
      case "long": return Long.valueOf(value);
      case "integer": return Integer.valueOf(value);
      case "short": return Short.valueOf(value);
      case "byte": return Byte.valueOf(value);
      case "double": return Double.valueOf(value);
      case "float": return Float.valueOf(value);
      case "big-decimal": return new BigDecimal(value);
      case "big-integer": return new BigInteger(value);
      case "boolean": return Boolean.valueOf(value);
      default: throw new IOException("Unsupported typed value in pending profile save: " + typeValue);
    }
  }

  private void writePendingFileAtomically(File file, String content) throws IOException {
    File temporary = File.createTempFile("mcore-pending-", ".tmp", file.getParentFile());
    try {
      Files.write(temporary.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      try {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary.toPath());
    }
  }

  private boolean deletePendingFileIfUnchanged(File file, String expectedContent) throws IOException {
    synchronized (this.pendingSaveLock) {
      if (!file.isFile()) {
        return true;
      }
      String currentContent = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
      if (!currentContent.equals(expectedContent)) {
        return false;
      }
      Files.deleteIfExists(file.toPath());
      return true;
    }
  }

  private static List<TableSnapshot> mergeSnapshots(List<TableSnapshot> older, List<TableSnapshot> newer) {
    Map<String, Map<String, PendingValue>> merged = new LinkedHashMap<>();
    for (TableSnapshot table : older) {
      merged.computeIfAbsent(table.name, ignored -> new LinkedHashMap<>()).putAll(table.values);
    }
    for (TableSnapshot table : newer) {
      merged.computeIfAbsent(table.name, ignored -> new LinkedHashMap<>()).putAll(table.values);
    }
    List<TableSnapshot> result = new ArrayList<>();
    for (Map.Entry<String, Map<String, PendingValue>> table : merged.entrySet()) {
      result.add(new TableSnapshot(table.getKey(), new LinkedHashMap<>(table.getValue())));
    }
    return result;
  }

  private static final class PendingProfileSave {
    private final String name;
    private final String key;
    private final long sequence;
    private final List<TableSnapshot> snapshot;

    private PendingProfileSave(String name, String key, long sequence, List<TableSnapshot> snapshot) {
      this.name = name;
      this.key = key;
      this.sequence = sequence;
      this.snapshot = snapshot;
    }
  }

  private static String quoteIdentifier(String identifier) {
    if (identifier == null || !identifier.matches("[A-Za-z0-9_]+")) {
      throw new IllegalArgumentException("Invalid internal SQL identifier: " + identifier);
    }
    return "`" + identifier + "`";
  }

  private static final class PendingValue {
    private final DataContainer container;
    private final Object value;

    private PendingValue(DataContainer container, Object value) {
      this.container = container;
      this.value = value;
    }
  }

  private static final class TableSnapshot {
    private final String name;
    private final Map<String, PendingValue> values;

    private TableSnapshot(String name, Map<String, PendingValue> values) {
      this.name = name;
      this.values = values;
    }
  }

  @FunctionalInterface
  private interface WriteOperation {
    void run() throws Exception;
  }

  private final class WriteTask implements Runnable {
    private final String description;
    private final WriteOperation operation;
    private final CompletableFuture<Void> future;

    private WriteTask(String description, WriteOperation operation, CompletableFuture<Void> future) {
      this.description = description;
      this.operation = operation;
      this.future = future;
    }

    @Override
    public void run() {
      try {
        operation.run();
        future.complete(null);
      } catch (Exception ex) {
        future.completeExceptionally(ex);
        LOGGER.log(Level.WARNING, "Failed to " + description + ".", ex);
      }
    }

    private void fail(Throwable cause) {
      if (future.completeExceptionally(cause)) {
        LOGGER.log(Level.WARNING, "Could not execute " + description + ".", cause);
      }
    }
  }

  private CompletableFuture<Void> submitWrite(String description, WriteOperation operation) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    WriteTask task = new WriteTask(description, operation, future);
    try {
      this.executor.execute(task);
    } catch (RejectedExecutionException ex) {
      task.fail(ex);
    }
    return future;
  }

  @Override
  public String exists(String name) {
    try (CachedRowSet rs = this.query("SELECT `name` FROM `mCoreProfile` WHERE LOWER(`name`) = ?", name.toLowerCase(Locale.ROOT))) {
      return rs == null ? null : rs.getString("name");
    } catch (SQLException ex) {
      throw new IllegalStateException("Could not check whether profile '" + name + "' exists.", ex);
    }
  }

  /**
   * Configure a project CA without disabling server certificate or hostname validation.
   * Aiven's project CA can be placed at plugins/mCore/ca.pem, or provided by environment.
   */
  private void configureTlsTrust(HikariConfig config) {
    String configuredCaPath = System.getenv("MCORE_MYSQL_CA_CERT");
    java.io.File caFile = configuredCaPath == null || configuredCaPath.trim().isEmpty()
      ? new java.io.File("plugins/mCore/ca.pem")
      : new java.io.File(configuredCaPath.trim());

    if ((configuredCaPath != null && !configuredCaPath.trim().isEmpty()) || caFile.isFile()) {
      if (!caFile.isFile() || !caFile.canRead()) {
        throw new IllegalStateException("MySQL CA certificate file is not readable: " + caFile.getAbsolutePath());
      }
      try (java.io.InputStream input = new java.io.FileInputStream(caFile)) {
        java.util.Collection<? extends java.security.cert.Certificate> certificates =
          java.security.cert.CertificateFactory.getInstance("X.509").generateCertificates(input);
        if (certificates.isEmpty()) {
          throw new IllegalStateException("MySQL CA certificate file contains no X.509 certificates: " + caFile.getAbsolutePath());
        }

        java.security.KeyStore trustStore = java.security.KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        int index = 1;
        for (java.security.cert.Certificate certificate : certificates) {
          trustStore.setCertificateEntry("mysql-ca-" + index++, certificate);
        }

        // The truststore contains public CA certificates only; generate a per-process password.
        String storePassword = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        java.io.File generatedTrustStore = java.io.File.createTempFile("mcore-mysql-trust-", ".p12");
        generatedTrustStore.deleteOnExit();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(generatedTrustStore)) {
          trustStore.store(output, storePassword.toCharArray());
        }

        config.addDataSourceProperty("trustCertificateKeyStoreUrl", generatedTrustStore.toURI().toURL().toExternalForm());
        config.addDataSourceProperty("trustCertificateKeyStoreType", "PKCS12");
        config.addDataSourceProperty("trustCertificateKeyStorePassword", storePassword);
        LOGGER.info("Loaded MySQL TLS CA certificate(s) from " + caFile.getAbsolutePath());
        return;
      } catch (IllegalStateException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new IllegalStateException("Could not load the MySQL CA certificate from " + caFile.getAbsolutePath() + ".", ex);
      }
    }

    String trustStorePath = System.getenv("MCORE_MYSQL_TRUSTSTORE");
    String trustStorePassword = System.getenv("MCORE_MYSQL_TRUSTSTORE_PASSWORD");
    if (trustStorePath != null && !trustStorePath.trim().isEmpty()) {
      java.io.File trustStore = new java.io.File(trustStorePath.trim());
      if (!trustStore.isFile() || !trustStore.canRead()) {
        throw new IllegalStateException("MCORE_MYSQL_TRUSTSTORE must point to a readable PKCS12 truststore file.");
      }
      if (trustStorePassword == null || trustStorePassword.isEmpty()) {
        throw new IllegalStateException("MCORE_MYSQL_TRUSTSTORE_PASSWORD is required when MCORE_MYSQL_TRUSTSTORE is configured.");
      }
      try {
        config.addDataSourceProperty("trustCertificateKeyStoreUrl", trustStore.toURI().toURL().toExternalForm());
      } catch (java.net.MalformedURLException ex) {
        throw new IllegalStateException("Invalid MySQL TLS truststore path.", ex);
      }
      config.addDataSourceProperty("trustCertificateKeyStoreType", "PKCS12");
      config.addDataSourceProperty("trustCertificateKeyStorePassword", trustStorePassword);
      return;
    }

    if (this.host != null && this.host.trim().toLowerCase(Locale.ROOT).endsWith(".aivencloud.com")) {
      throw new IllegalStateException("Aiven MySQL TLS requires its project CA certificate. Download the CA certificate from Aiven and save it as plugins/mCore/ca.pem, or set MCORE_MYSQL_CA_CERT to its absolute path. Server certificate and hostname verification remain enabled.");
    }
  }

  public void openConnection() {
    HikariConfig config = new HikariConfig();
    config.setPoolName("mConnectionPool");
    // Keep HikariCP defaults unless workload measurements justify pool tuning.
    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
    String jdbcUrl = "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.dbname +
      "?sslMode=VERIFY_IDENTITY&connectTimeout=10000&socketTimeout=30000&useUnicode=yes&characterEncoding=UTF-8";
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(this.username);
    config.setPassword(this.password);

    configureTlsTrust(config);
    config.addDataSourceProperty("autoReconnect", "true");
    this.dataSource = new HikariDataSource(config);

    LOGGER.info("Conectado ao MySQL!");
  }

  public void closeConnection() {
    if (isConnected()) {
      this.dataSource.close();
    }
  }

  public Connection getConnection() throws SQLException {
    return this.dataSource.getConnection();
  }

  public boolean isConnected() {
    return !this.dataSource.isClosed();
  }

  @Override
  public void update(String sql, Object... vars) {
    try {
      updateOrThrow(sql, vars);
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Could not execute SQL update.", ex);
    }
  }

  private void updateOrThrow(String sql, Object... vars) throws SQLException {
    try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < vars.length; i++) {
        statement.setObject(i + 1, vars[i]);
      }
      statement.executeUpdate();
    }
  }

  @Override
  public CompletableFuture<Void> execute(String sql, Object... vars) {
    Object[] parameters = Arrays.copyOf(vars, vars.length);
    return submitWrite("SQL update", () -> updateOrThrow(sql, parameters));
  }

  public int updateWithInsertId(String sql, Object... vars) {
    int id = -1;
    Connection connection = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      connection = getConnection();
      ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      for (int i = 0; i < vars.length; i++) {
        ps.setObject(i + 1, vars[i]);
      }
      ps.execute();
      rs = ps.getGeneratedKeys();
      if (rs.next()) {
        id = rs.getInt(1);
      }
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Nao foi possivel executar um SQL: ", ex);
    } finally {
      try {
        if (connection != null && !connection.isClosed())
          connection.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        if (ps != null && !ps.isClosed())
          ps.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        if (rs != null && !rs.isClosed())
          rs.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }

    return id;
  }

  @Override
  public CachedRowSet query(String query, Object... vars) {
    try (Connection connection = getConnection();
         PreparedStatement statement = connection.prepareStatement(query)) {
      for (int i = 0; i < vars.length; i++) {
        statement.setObject(i + 1, vars[i]);
      }

      try (ResultSet result = statement.executeQuery()) {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.populate(result);
        if (rowSet.next()) {
          return rowSet;
        }
        rowSet.close();
        return null;
      }
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Could not execute a database query.", ex);
      throw new IllegalStateException("Database query failed.", ex);
    }
  }
}
