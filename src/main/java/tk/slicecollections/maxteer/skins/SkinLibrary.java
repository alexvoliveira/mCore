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
import tk.slicecollections.maxteer.libraries.profile.Mojang;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.utils.MessageUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkinLibrary implements Listener {
  private final Core plugin;
  private final ExecutorService resolver;
  private final Map<String, SkinDefinition> definitions = new LinkedHashMap<>();
  private final ConcurrentMap<String, Skin> resolved = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, AtomicLong> requests = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, String> selectedSkins = new ConcurrentHashMap<>();

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

  public String getSelectedSkinId(UUID playerId) {
    return playerId == null ? null : selectedSkins.get(playerId);
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
      boolean hasTexture = !texture.isEmpty();
      boolean hasSignature = !signature.isEmpty();

      if (!hasTexture && hasSignature) {
        plugin.getLogger().warning("Ignoring skin '" + rawId + "': a texture signature cannot be used without a value.");
        continue;
      }
      if (!hasTexture && !owner.matches("[A-Za-z0-9_]{1,16}")) {
        plugin.getLogger().warning("Ignoring skin '" + rawId + "': provide a valid owner or a texture value.");
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
    Skin skin;
    if (definition.hasStoredTexture()) {
      skin = Skin.fromData(definition.getTextureValue(), definition.getTextureSignature());
    } else if (definition.hasTextureValue()) {
      skin = resolveStoredTexture(definition);
    } else {
      skin = Skin.fromName(definition.getOwnerName());
    }
    if (!skin.isValid()) {
      throw new IllegalStateException("No valid signed texture could be resolved for " + definition.getOwnerName());
    }
    Skin existing = resolved.putIfAbsent(definition.getId(), skin);
    return existing == null ? skin : existing;
  }

  /**
   * Resolves a signed texture property for value-only catalogue entries.
   * The returned signature is accepted only when its texture URL matches the configured value.
   */
  private Skin resolveStoredTexture(SkinDefinition definition) {
    String expectedUrl = extractTextureUrl(definition.getTextureValue());
    if (expectedUrl == null) {
      throw new IllegalStateException("The stored skin value does not contain a valid texture URL.");
    }

    List<String> profileIds = new ArrayList<>();
    try {
      String ownerId = Mojang.getUUID(definition.getOwnerName());
      if (ownerId != null && !ownerId.isEmpty()) {
        profileIds.add(ownerId.replace("-", ""));
      }
    } catch (Exception ignored) {
      // The stored profile id can still resolve the original signed property.
    }

    String embeddedId = extractProfileId(definition.getTextureValue());
    if (embeddedId != null && !profileIds.contains(embeddedId)) {
      profileIds.add(embeddedId);
    }

    for (String profileId : profileIds) {
      try {
        String property = Mojang.getSkinProperty(profileId);
        if (property == null) {
          continue;
        }

        String[] parts = property.split(" : ", 3);
        if (parts.length == 3 && "textures".equals(parts[0)
            && !parts[1].isEmpty() && !parts[2].isEmpty()
            && expectedUrl.equals(extractTextureUrl(parts[1]))) {
          return Skin.fromData(parts[1], parts[2]);
        }
      } catch (Exception ignored) {
        // Try the next known profile id if this provider lookup fails.
      }
    }

    throw new IllegalStateException(
        "No current signed profile matches the texture configured for " + definition.getDisplayName());
  }

  private String extractProfileId(String value) {
    try {
      String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
      Matcher matcher = Pattern.compile("\\\"profileId\\\"\\s*:\\s*\\\"([0-9a-fA-F]{32})\\\"").matcher(decoded);
      return matcher.find() ? matcher.group(1) : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private String extractTextureUrl(String value) {
    try {
      String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
      Matcher matcher = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(decoded);
      return matcher.find() ? matcher.group(1) : null;
    } catch (IllegalArgumentException ex) {
      return null;
    }
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
      selectedSkins.put(player.getUniqueId(), definition.getId());
      try {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
          if (!plugin.isEnabled() || !player.isOnline()) {
            return;
          }
          Profile current = Profile.getProfile(player.getName());
          if (current != null) {
            current.refreshHotbarProfileSkull();
          }
        }, 2L);
      } catch (IllegalStateException ignored) {
        // The plugin may be shutting down while the skin is being applied.
      }
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
    UUID playerId = event.getPlayer().getUniqueId();
    requests.remove(playerId);
    selectedSkins.remove(playerId);
  }

  public void shutdown() {
    resolver.shutdownNow();
    resolved.clear();
    requests.clear();
    selectedSkins.clear();
    definitions.clear();
  }
}
