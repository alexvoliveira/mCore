package tk.slicecollections.maxteer.listeners;

import tk.slicecollections.maxteer.utils.MessageUtils;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.spigotmc.WatchdogThread;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.Manager;
import tk.slicecollections.maxteer.database.exception.ProfileLoadException;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.player.enums.PrivateMessages;
import tk.slicecollections.maxteer.player.enums.ProtectionLobby;
import tk.slicecollections.maxteer.player.fake.FakeManager;
import tk.slicecollections.maxteer.player.hotbar.HotbarButton;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.plugin.logger.MLogger;
import tk.slicecollections.maxteer.plugin.config.MConfig;
import tk.slicecollections.maxteer.reflection.Accessors;
import tk.slicecollections.maxteer.reflection.acessors.FieldAccessor;
import tk.slicecollections.maxteer.titles.TitleManager;
import tk.slicecollections.maxteer.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * @author Maxter
 */
public class Listeners implements Listener {

  public static final MLogger LOGGER = ((MLogger) Core.getInstance().getLogger()).getModule("Listeners");
  public static final Map<String, Long> DELAY_PLAYERS = new HashMap<>();
  private static final Map<String, Long> PROTECTION_LOBBY = new HashMap<>();

  private static final FieldAccessor<Map> COMMAND_MAP = Accessors.getField(SimpleCommandMap.class, "knownCommands", Map.class);
  private static final SimpleCommandMap SIMPLE_COMMAND_MAP = (SimpleCommandMap) Accessors.getMethod(Bukkit.getServer().getClass(), "getCommandMap").invoke(Bukkit.getServer());

  public static void setupListeners() {
    Bukkit.getPluginManager().registerEvents(new Listeners(), Core.getInstance());
  }

  public static void shutdown() {
    DELAY_PLAYERS.clear();
    PROTECTION_LOBBY.clear();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent evt) {
    if (evt.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      try {
        Profile.createOrLoadProfile(evt.getName());
      } catch (ProfileLoadException ex) {
        LOGGER.log(Level.SEVERE, "§4§lPERFIL NÃO CARREGADO §7Não foi possível carregar os dados de " + evt.getName() + ".", ex);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerLoginMonitor(PlayerLoginEvent evt) {
    if (evt.getResult() != PlayerLoginEvent.Result.ALLOWED) {
      Profile.discardPreloadedProfile(evt.getPlayer().getName());
      return;
    }

    Profile profile = Profile.getProfile(evt.getPlayer().getName());
    if (profile == null) {
      evt.disallow(PlayerLoginEvent.Result.KICK_OTHER,
        MessageUtils.critical("PERFIL INDISPONÍVEL", "Não foi possível carregar seu perfil.\n\nAguarde alguns instantes e tente entrar novamente."));
      return;
    }

    profile.setPlayer(evt.getPlayer());
  }

  private static final FieldAccessor<WatchdogThread> RESTART_WATCHDOG = Accessors.getField(WatchdogThread.class, "instance", WatchdogThread.class);
  private static final FieldAccessor<Boolean> RESTART_WATCHDOG_STOPPING = Accessors.getField(WatchdogThread.class, "stopping", boolean.class);

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerQuit(PlayerQuitEvent evt) {
    Profile profile = Profile.unloadProfile(evt.getPlayer().getName());
    if (profile != null) {
      if (profile.getGame() != null) {
        profile.getGame().leave(profile, profile.getGame());
      }
      TitleManager.leaveServer(profile);
      if (!((CraftServer) Bukkit.getServer()).getHandle().getServer().isRunning() || RESTART_WATCHDOG_STOPPING.get(RESTART_WATCHDOG.get(null))) {
        // server stopped - save SYNC
        if (!profile.saveSyncChecked()) {
          LOGGER.severe("§4§lFALHA CRÍTICA AO SALVAR §7Não foi possível persistir o perfil de " + profile.getName() + ".");
        }
        Core.getInstance().getLogger().info("§a§lSALVO §7Perfil de " + profile.getName() + " gravado com sucesso.");
      } else {
        // server running - save ASYNC
        profile.save();
      }
      profile.destroy();
    }

    FakeManager.fakeNames.remove(evt.getPlayer().getName());
    FakeManager.fakeRoles.remove(evt.getPlayer().getName());
    FakeManager.fakeSkins.remove(evt.getPlayer().getName());
    DELAY_PLAYERS.remove(evt.getPlayer().getName());
    PROTECTION_LOBBY.remove(evt.getPlayer().getName().toLowerCase());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onAsyncPlayerChat(AsyncPlayerChatEvent evt) {
    if (evt.isCancelled()) {
      return;
    }

    Player player = evt.getPlayer();

    String current = Manager.getCurrent(player.getName());
    Role role = Role.getPlayerRole(player);
    MConfig chatConfig = Core.getInstance().getConfig("chat");
    String rolePrefix = StringUtils.getLastColor(role.getPrefix());
    String format = role.isDefault()
      ? chatConfig.getString("format.member", "{player}: {message}")
      : chatConfig.getString("format.role", "{role_initial} {player}: {message}");

    String roleName = StringUtils.stripColors(role.getName());
    String roleInitial = roleName.isEmpty() ? "" : roleName.substring(0, 1).toUpperCase();
    String message = role.isDefault()
      ? StringUtils.stripColors(evt.getMessage())
      : StringUtils.formatColors(evt.getMessage());
    format = format.replace("{prefix}", rolePrefix)
      .replace("{role}", role.getName())
      .replace("{role_initial}", roleInitial)
      .replace("{player}", current)
      .replace("{message}", message);

    TextComponent component = new TextComponent("");
    component.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + current + " "));
    component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
      TextComponent.fromLegacyText(rolePrefix + current + "\n§7Grupo: §f" + role.getName() + "\n \n§7Clique para enviar uma mensagem privada.")));
    for (BaseComponent components : TextComponent.fromLegacyText(format.toString())) {
      component.addExtra(components);
    }

    evt.setCancelled(true);
    evt.getRecipients().forEach(players -> {
      if (players != null) {
        players.spigot().sendMessage(component);
      }
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent evt) {
    if (evt.isCancelled()) {
      return;
    }

    Player player = evt.getPlayer();
    Profile profile = Profile.getProfile(player.getName());

    if (profile != null) {
      String[] args = evt.getMessage().replace("/", "").split(" ");

      if (args.length > 0) {
        String command = args[0];
        if (COMMAND_MAP.get(SIMPLE_COMMAND_MAP).containsKey("lobby") && command.equals("lobby") && profile.getPreferencesContainer()
          .getProtectionLobby() == ProtectionLobby.ATIVADO) {
          long last = PROTECTION_LOBBY.getOrDefault(player.getName().toLowerCase(), 0L);
          if (last > System.currentTimeMillis()) {
            PROTECTION_LOBBY.remove(player.getName().toLowerCase());
            return;
          }

          evt.setCancelled(true);
          PROTECTION_LOBBY.put(player.getName().toLowerCase(), System.currentTimeMillis() + 3000);
          player.sendMessage(MessageUtils.info("Para confirmar sua saída, utilize /lobby novamente."));
        } else if (COMMAND_MAP.get(SIMPLE_COMMAND_MAP).containsKey("tell") && args.length > 1 && command.equals("tell") && !args[1].equalsIgnoreCase(player.getName())) {
          profile = Profile.getProfile(args[1]);
          if (profile != null && profile.getPreferencesContainer().getPrivateMessages() != PrivateMessages.TODOS) {
            evt.setCancelled(true);
            player.sendMessage(MessageUtils.error("MENSAGENS PRIVADAS", "Este usuário desativou as mensagens privadas."));
          }
        }
      }
    }
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent evt) {
    Player player = evt.getPlayer();
    Profile profile = Profile.getProfile(player.getName());

    if (profile != null && profile.getHotbar() != null) {
      ItemStack item = player.getItemInHand();
      if (evt.getAction().name().contains("CLICK") && item != null && item.hasItemMeta()) {
        HotbarButton button = profile.getHotbar().compareButton(player, item);
        if (button != null) {
          evt.setCancelled(true);
          button.getAction().execute(profile);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent evt) {
    if (evt.getRightClicked() instanceof ArmorStand) {
      if (evt.getPlayer().getGameMode() == GameMode.ADVENTURE) {
        evt.setCancelled(true);
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onInventoryClick(InventoryClickEvent evt) {
    if (evt.getWhoClicked() instanceof Player) {
      Player player = (Player) evt.getWhoClicked();
      Profile profile = Profile.getProfile(player.getName());

      if (profile != null && profile.getHotbar() != null) {
        ItemStack item = evt.getCurrentItem();
        if (item != null && item.getType() != Material.AIR) {
          if (evt.getClickedInventory() != null && evt.getClickedInventory().equals(player.getInventory()) && item.hasItemMeta()) {
            HotbarButton button = profile.getHotbar().compareButton(player, item);
            if (button != null) {
              evt.setCancelled(true);
              button.getAction().execute(profile);
            }
          }
        }
      }
    }
  }
}
