package tk.slicecollections.maxteer.skins;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.libraries.npclib.npc.skin.Skin;
import tk.slicecollections.maxteer.utils.MessageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class SkinLibrary implements Listener {
  private final Core plugin;
  private final ExecutorService resolver;
  private final Map<String, SkinDefinition> definitions = new LinkedHashMap<>();
  private final ConcurrentMap<String, Skin> resolved = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, AtomicLong> requests = new ConcurrentHashMap<>();

  public SkinLibrary(Core plugin) {
    this.plugin = plugin;
    this.resolver = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "mCore-skin-resolver");
      thread.setDaemon(true);
      return thread;
    });
    loadDefinitions();
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  public List<SkinDefinition> getSkins() {
    return Collections.unmodifiableList(new ArrayList<>(definitions.values()));
  }

  public SkinDefinition getSkin(String id) {
    return id == null ? null : definitions.get(id.toLowerCase(Locale.ROOT));
  }

  private void loadDefinitions() {
    if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
      plugin.getLogger().warning("Could not create the mCore data directory for skins.yml.");
      return;
    }
    File file = new File(plugin.getDataFolder(), "skins.yml");
    if (!file.isFile()) {
      plugin.saveResource("skins.yml", false);
    }

    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection section = yaml.getConfigurationSection("skins");
    if (section == null) {
      plugin.getLogger().warning("skins.yml has no 'skins' section; the skin catalogue is empty.");
      return;
    }

    for (String rawId : section.getKeys(false)) {
      if (rawId == null || !rawId.matches("[A-Za-z0-9_-]{1,64}")) {
        plugin.getLogger().warning("Ignoring invalid skin catalogue id: " + rawId);
        continue;
      }
      ConfigurationSection value = section.getConfigurationSection(rawId);
      if (value == null) {
        continue;
      }

      String id = rawId.toLowerCase(Locale.ROOT);
      String display = value.getString("display-name", rawId);
      String owner = value.getString("owner", "").trim();
      String texture = value.getString("texture-value", "").trim();
      String signature = value.getString("texture-signature", "").trim();
      boolean inlineTexture = !texture.isEmpty() && !signature.isEmpty();

      if (!inlineTexture && !owner.matches("[A-Za-z0-9_]{1,16}")) {
        plugin.getLogger().warning("Ignoring skin '" + rawId + "': provide a valid owner or both signed texture fields.");
        continue;
      }
      if ((!texture.isEmpty() || !signature.isEmpty()) && !inlineTexture) {
        plugin.getLogger().warning("Ignoring skin '" + rawId + "': texture-value and texture-signature must be set together.");
        continue;
      }

      display = ChatColor.translateAlternateColorCodes('&', display);
      definitions.put(id, new SkinDefinition(id, display, owner, texture, signature));
    }

    if (definitions.isEmpty()) {
      plugin.getLogger().warning("No valid skin entries were found in skins.yml.");
    } else {
      plugin.getLogger().info("Loaded " + definitions.size() + " skin definitions.");
    }
  }

  public void applySkin(Player player, String id) {
    SkinDefinition definition = getSkin(id);
    if (definition == null) {
      player.sendMessage(MessageUtils.error("SKIN", "Opção inválida ou indisponível."));
      return;
    }

    long request = beginRequest(player);
    player.sendMessage(MessageUtils.info("Carregando a skin " + definition.getDisplayName() + "§7..."));
    try {
      resolver.execute(() -> {
        // Drop stale clicks before starting any remote API requests.
        if (!isCurrentRequest(player, request)) {
          return;
        }
        final Skin skin;
        try {
          skin = resolve(definition);
          if (!skin.isValid()) {
            throw new IllegalStateException("The provider returned no signed skin texture.");
          }
        } catch (Exception failure) {
          scheduleMain(() -> fail(player, request, definition, failure));
          return;
        }
        scheduleMain(() -> finishApply(player, request, definition, skin, true, true));
      });
    } catch (RejectedExecutionException ex) {
      fail(player, request, definition, ex);
    }
  }

  public void applyHistorySkin(Player player, SkinHistoryEntry entry) {
    SkinDefinition definition = definitionFromHistory(entry);
    Skin skin = Skin.fromData(entry.getTextureValue(), entry.getTextureSignature());
    long request = beginRequest(player);
    if (!skin.isValid()) {
      fail(player, request, definition, new IllegalStateException("The saved skin texture is invalid."));
      return;
    }
    finishApply(player, request, definition, skin, true, true);
  }

  private Skin resolve(SkinDefinition definition) {
    Skin cached = resolved.get(definition.getId());
    if (cached != null) {
      return cached;
    }
    Skin skin = definition.hasStoredTexture()
        ? Skin.fromData(definition.getTextureValue(), definition.getTextureSignature())
        : Skin.fromName(definition.getOwnerName());
    if (!skin.isValid()) {
      throw new IllegalStateException("No valid signed texture could be resolved for " + definition.getOwnerName());
    }
    Skin existing = resolved.putIfAbsent(definition.getId(), skin);
    return existing == null ? skin : existing;
  }

  private long beginRequest(Player player) {
    return requests.computeIfAbsent(player.getUniqueId(), ignored -> new AtomicLong()).incrementAndGet();
  }

  private boolean isCurrentRequest(Player player, long request) {
    AtomicLong current = requests.get(player.getUniqueId());
    return current != null && current.get() == request;
  }

  private void finishApply(Player player, long request, SkinDefinition definition,
                           Skin skin, boolean recordHistory, boolean notify) {
    if (!plugin.isEnabled() || !player.isOnline() || !isCurrentRequest(player, request)) {
      return;
    }
    try {
      if (notify) {
        player.closeInventory();
      }
      skin.apply(player);
      if (recordHistory && plugin.getSkinHistoryManager() != null) {
        plugin.getSkinHistoryManager().recordSelection(
            player.getUniqueId(), player.getName(), definition, skin.getValue(), skin.getSignature());
      }
      if (notify) {
        player.sendMessage(MessageUtils.success("SKIN", "Skin aplicada: " + definition.getDisplayName() + "§7."));
      }
    } catch (RuntimeException ex) {
      plugin.getLogger().log(Level.WARNING, "Could not apply skin '" + definition.getId() + "' to " + player.getName() + ".", ex);
      if (notify && player.isOnline()) {
        player.sendMessage(MessageUtils.error("SKIN", "Não foi possível aplicar essa skin."));
      }
    }
  }

  private void fail(Player player, long request, SkinDefinition definition, Exception failure) {
    if (!plugin.isEnabled() || !player.isOnline() || !isCurrentRequest(player, request)) {
      return;
    }
    plugin.getLogger().log(Level.WARNING, "Could not load skin '" + definition.getId() + "'.", failure);
    player.sendMessage(MessageUtils.error("SKIN", "Não foi possível carregar essa skin. Tente novamente."));
  }

  private SkinDefinition definitionFromHistory(SkinHistoryEntry entry) {
    return new SkinDefinition(entry.getSkinId(), entry.getDisplayName(), entry.getOwnerName(),
        entry.getTextureValue(), entry.getTextureSignature());
  }

  private void scheduleMain(Runnable task) {
    if (!plugin.isEnabled()) {
      return;
    }
    try {
      Bukkit.getScheduler().runTask(plugin, task);
    } catch (IllegalStateException ignored) {
      // The plugin may be shutting down while an external profile request completes.
    }
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    final Player player = event.getPlayer();
    final long request = beginRequest(player);
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      if (!player.isOnline() || !isCurrentRequest(player, request)) {
        return;
      }
      SkinHistoryManager history = plugin.getSkinHistoryManager();
      if (history == null) {
        return;
      }
      history.loadSelectedSkin(player.getUniqueId()).whenComplete((entry, failure) -> scheduleMain(() -> {
        if (!player.isOnline() || !isCurrentRequest(player, request)) {
          return;
        }
        if (failure != null) {
          plugin.getLogger().log(Level.WARNING, "Could not load saved skin for " + player.getName() + ".", failure);
          return;
        }
        if (entry != null) {
          Skin skin = Skin.fromData(entry.getTextureValue(), entry.getTextureSignature());
          if (skin.isValid()) {
            finishApply(player, request, definitionFromHistory(entry), skin, false, false);
          }
        }
      }));
    }, 2L);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    requests.remove(event.getPlayer().getUniqueId());
  }

  public void shutdown() {
    resolver.shutdownNow();
    resolved.clear();
    requests.clear();
    definitions.clear();
  }
}
