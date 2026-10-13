package tk.slicecollections.maxteer;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketListener;
import com.henryfabio.minecraft.inventoryapi.manager.InventoryManager;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.achievements.Achievement;
import tk.slicecollections.maxteer.booster.Booster;
import tk.slicecollections.maxteer.bukkit.BukkitPartyManager;
import tk.slicecollections.maxteer.cmd.Commands;
import tk.slicecollections.maxteer.database.Database;
import tk.slicecollections.maxteer.deliveries.Delivery;
import tk.slicecollections.maxteer.hook.MCoreExpansion;
import tk.slicecollections.maxteer.hook.protocollib.FakeAdapter;
import tk.slicecollections.maxteer.hook.protocollib.HologramAdapter;
import tk.slicecollections.maxteer.hook.protocollib.NPCAdapter;
import tk.slicecollections.maxteer.libraries.MinecraftVersion;
import tk.slicecollections.maxteer.libraries.holograms.HologramLibrary;
import tk.slicecollections.maxteer.libraries.npclib.NPCLibrary;
import tk.slicecollections.maxteer.listeners.Listeners;
import tk.slicecollections.maxteer.menus.InventoryMenuListener;
import tk.slicecollections.maxteer.listeners.PluginMessageListener;
import tk.slicecollections.maxteer.nms.NMS;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.player.fake.FakeManager;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.plugin.MPlugin;
import tk.slicecollections.maxteer.plugin.config.MConfig;
import tk.slicecollections.maxteer.queue.Queue;
import tk.slicecollections.maxteer.queue.QueuePlayer;
import tk.slicecollections.maxteer.servers.ServerItem;
import tk.slicecollections.maxteer.titles.Title;
import tk.slicecollections.maxteer.titles.TitleManager;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * @author Maxter
 */
@SuppressWarnings("unchecked")
public class Core extends MPlugin {

  private static Core instance;
  private final List<PacketListener> registeredPacketListeners = new ArrayList<>();
  private org.bukkit.scheduler.BukkitTask profileCleanupTask;
  private MCoreExpansion expansion;
  public static boolean validInit;
  public static final List<String> warnings = new ArrayList<>();
  public static final List<String> minigames = Arrays.asList("Sky Wars", "The Bridge", "Murder");
  public static String minigame = "";

  @Override
  public void start() {
    instance = this;
  }

  @Override
  public void load() {}

  @Override
  public void enable() {
    if (!NMS.setupNMS()) {
      this.getLogger().warning("O plugin apenas funciona na versao 1_8_R3 (Atual: " + MinecraftVersion.getCurrentVersion().getVersion() + ")");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    saveDefaultConfig();
    lobby = Bukkit.getWorlds().get(0).getSpawnLocation();

    // Remover o spawn-protection-size
    if (Bukkit.getSpawnRadius() != 0) {
      Bukkit.setSpawnRadius(0);
    }

    // Plugins que causaram incompatibilidades
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.getResource("blacklist.txt"), StandardCharsets.UTF_8))) {
      String plugin;
      while ((plugin = reader.readLine()) != null) {
        if (Bukkit.getPluginManager().getPlugin(plugin.split(" ")[0]) != null) {
          warnings.add(" - " + plugin);
        }
      }
    } catch (IOException ex) {
      getLogger().log(Level.SEVERE, "Cannot load blacklist.txt: ", ex);
    }

    if (!warnings.isEmpty()) {
      CommandSender sender = Bukkit.getConsoleSender();
      StringBuilder sb = new StringBuilder(" \n §6§lAVISO IMPORTANTE\n \n §7Aparentemente você utiliza plugins que conflitam com o mCore.\n §7Você não poderá iniciar o servidor com os seguintes plugins:");
      for (String warning : warnings) {
        sb.append("\n§f").append(warning);
      }
      sb.append("\n ");
      sender.sendMessage(sb.toString());
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    // Remover /reload
    try {
      SimpleCommandMap simpleCommandMap = (SimpleCommandMap) Bukkit.getServer().getClass().getDeclaredMethod("getCommandMap").invoke(Bukkit.getServer());
      Field field = simpleCommandMap.getClass().getDeclaredField("knownCommands");
      field.setAccessible(true);
      Map<String, Command> knownCommands = (Map<String, Command>) field.get(simpleCommandMap);
      knownCommands.remove("rl");
      knownCommands.remove("reload");
      knownCommands.remove("bukkit:rl");
      knownCommands.remove("bukkit:reload");
    } catch (ReflectiveOperationException ex) {
      getLogger().log(Level.SEVERE, "Cannot remove reload command: ", ex);
    }

    if (!PlaceholderAPIPlugin.getInstance().getDescription().getVersion().equals("2.10.5")) {
      Bukkit.getConsoleSender().sendMessage(" \n §6§lAVISO IMPORTANTE\n \n §7Utilize a versão 2.10.5 do PlaceHolderAPI, você está utilizando a v" + PlaceholderAPIPlugin.getInstance().getDescription().getVersion() + "\n ");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    this.expansion = new MCoreExpansion();
    PlaceholderAPI.registerExpansion(this.expansion);

    InventoryManager.enable(this);
    Bukkit.getPluginManager().registerEvents(new InventoryMenuListener(), this);

    Database.setupDatabase(
      getConfig().getString("database.mysql.host"),
      getConfig().getString("database.mysql.porta"),
      getConfig().getString("database.mysql.nome"),
      getConfig().getString("database.mysql.usuario"),
      getConfig().getString("database.mysql.senha")
    );
    this.profileCleanupTask = Bukkit.getScheduler().runTaskTimer(this,
      Profile::cleanupStalePreloadedProfiles, 1200L, 1200L);

    NPCLibrary.setupNPCs(this);
    HologramLibrary.setupHolograms(this);

    setupRoles();
    FakeManager.setupFake();
    Title.setupTitles();
    Booster.setupBoosters();
    Delivery.setupDeliveries();
    ServerItem.setupServers();
    Achievement.setupAchievements();

    Commands.setupCommands();
    Listeners.setupListeners();

    this.registerPacketListener(new FakeAdapter());
    this.registerPacketListener(new NPCAdapter());
    this.registerPacketListener(new HologramAdapter());

    getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    getServer().getMessenger().registerOutgoingPluginChannel(this, "mCore");
    getServer().getMessenger().registerIncomingPluginChannel(this, "mCore", new PluginMessageListener());

    validInit = true;
    this.getLogger().info("O plugin foi ativado.");
  }

  private void registerPacketListener(PacketListener listener) {
    ProtocolLibrary.getProtocolManager().addPacketListener(listener);
    this.registeredPacketListeners.add(listener);
  }

  @Override
  public void disable() {
    if (this.profileCleanupTask != null) {
      this.profileCleanupTask.cancel();
      this.profileCleanupTask = null;
    }

    for (PacketListener listener : new ArrayList<>(this.registeredPacketListeners)) {
      try {
        ProtocolLibrary.getProtocolManager().removePacketListener(listener);
      } catch (RuntimeException ex) {
        this.getLogger().log(Level.WARNING, "Could not unregister an mCore packet listener.", ex);
      }
    }
    this.registeredPacketListeners.clear();

    if (this.expansion != null) {
      PlaceholderAPI.unregisterExpansion(this.expansion);
      this.expansion = null;
    }

    Queue.shutdownAll();
    BukkitPartyManager.shutdown();
    TitleManager.shutdown();
    Listeners.shutdown();
    NPCLibrary.unregisterAll();
    HologramLibrary.unregisterAll();

    if (validInit) {
      Bukkit.getOnlinePlayers().forEach(player -> {
        Profile profile = Profile.unloadProfile(player.getName());
        if (profile != null) {
          if (profile.saveSyncChecked()) {
            this.getLogger().info("Saved " + profile.getName() + "!");
          } else {
            this.getLogger().severe("Synchronous profile save failed; verify the database before shutdown: " + profile.getName());
          }
          profile.destroy();
        }
      });
    }

    for (Profile profile : new ArrayList<>(Profile.listProfiles())) {
      String name = profile.getName();
      if (name != null) {
        Profile.unloadProfile(name);
        if (!profile.saveSyncChecked()) {
          this.getLogger().severe("Synchronous save failed for retained profile during shutdown: " + name);
        }
      }
      profile.destroy();
    }

    FakeManager.fakeNames.clear();
    FakeManager.fakeRoles.clear();
    FakeManager.fakeSkins.clear();

    Database database = Database.getInstance();
    if (database != null) {
      try {
        database.close();
      } catch (RuntimeException ex) {
        this.getLogger().log(Level.SEVERE, "Failed to close the database during plugin shutdown.", ex);
      }
    }
    Role.listRoles().clear();
    validInit = false;
    this.getLogger().info("O plugin foi desativado.");
    instance = null;
  }

  private void setupRoles() {
    MConfig config = getConfig("roles");
    for (String key : config.getSection("roles").getKeys(false)) {
      String name = config.getString("roles." + key + ".name");
      String prefix = config.getString("roles." + key + ".prefix");
      String permission = config.getString("roles." + key + ".permission");
      boolean broadcast = config.getBoolean("roles." + key + ".broadcast", true);
      boolean alwaysVisible = config.getBoolean("roles." + key + ".alwaysvisible", false);

      Role.listRoles().add(new Role(name, prefix, permission, alwaysVisible, broadcast));
    }

    if (Role.listRoles().isEmpty()) {
      Role.listRoles().add(new Role("&7Membro", "&7", "", false, false));
    }
  }

  private static Location lobby;

  public static void setLobby(Location location) {
    lobby = location;
  }

  public static Location getLobby() {
    return lobby;
  }

  public static Core getInstance() {
    return instance;
  }

  public static void sendServer(Profile profile, String name) {
    if (!Core.getInstance().isEnabled()) {
      return;
    }

    Player player = profile.getPlayer();
    if (player != null) {
      player.closeInventory();
      Queue queue = player.hasPermission("mcore.queue") ? Queue.VIP : Queue.MEMBER;
      QueuePlayer qp = queue.getQueuePlayer(player);
      if (qp != null) {
        if (qp.server.equalsIgnoreCase(name)) {
          qp.player.sendMessage("§cVocê já está na fila de conexão!");
        } else {
          qp.server = name;
        }
        return;
      }

      queue.queue(player, profile, name);
    }
  }
}
