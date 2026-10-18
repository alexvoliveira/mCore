package tk.slicecollections.maxteer;

import tk.slicecollections.maxteer.utils.MessageUtils;

import lombok.Getter;
import lombok.Setter;

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
import tk.slicecollections.maxteer.nms.network.FakePacketHandler;
import tk.slicecollections.maxteer.nms.network.HologramPacketHandler;
import tk.slicecollections.maxteer.nms.network.NMSPacketInterceptor;
import tk.slicecollections.maxteer.nms.network.NPCPacketHandler;
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
import tk.slicecollections.maxteer.skins.SkinHistoryManager;
import tk.slicecollections.maxteer.skins.SkinLibrary;
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

  @Getter
  private static Core instance;
  private org.bukkit.scheduler.BukkitTask profileCleanupTask;
  private MCoreExpansion expansion;
  @Getter
  private SkinLibrary skinLibrary;
  @Getter
  private SkinHistoryManager skinHistoryManager;
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
      this.getLogger().warning("§4§lVERSÃO INCOMPATÍVEL §7O mCore requer Spigot 1.8_R3 (detectada: " + MinecraftVersion.getCurrentVersion().getVersion() + ").");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    NMSPacketInterceptor.enable(this);
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
          warnings.add(plugin);
        }
      }
    } catch (IOException ex) {
      getLogger().log(Level.SEVERE, "§4§lERRO CRÍTICO §7Não foi possível carregar blacklist.txt.", ex);
    }

    if (!warnings.isEmpty()) {
      CommandSender sender = Bukkit.getConsoleSender();
      StringBuilder sb = new StringBuilder(MessageUtils.critical("CONFLITO DE PLUGINS", "Foram detectados plugins incompatíveis. O mCore não será iniciado:"));
      for (String warning : warnings) {
        sb.append("\n§c• §7").append(warning);
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
      Bukkit.getConsoleSender().sendMessage(MessageUtils.critical("DEPENDÊNCIA INVÁLIDA", "É necessário PlaceholderAPI 2.10.5; versão encontrada: " + PlaceholderAPIPlugin.getInstance().getDescription().getVersion() + "."));
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
    this.skinHistoryManager = new SkinHistoryManager(this);
    this.skinHistoryManager.start();
    this.skinLibrary = new SkinLibrary(this);

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

    NMSPacketInterceptor.registerListener(this, new FakePacketHandler());
    NMSPacketInterceptor.registerListener(this, new NPCPacketHandler());
    NMSPacketInterceptor.registerListener(this, new HologramPacketHandler());

    getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    getServer().getMessenger().registerOutgoingPluginChannel(this, "mCore");
    getServer().getMessenger().registerIncomingPluginChannel(this, "mCore", new PluginMessageListener());

    validInit = true;
    this.getLogger().info("§a§lINICIADO §7com sucesso.");
  }

  @Override
  public void disable() {
    if (this.profileCleanupTask != null) {
      this.profileCleanupTask.cancel();
      this.profileCleanupTask = null;
    }

    if (this.skinLibrary != null) {
      this.skinLibrary.shutdown();
      this.skinLibrary = null;
    }
    if (this.skinHistoryManager != null) {
      this.skinHistoryManager.shutdown();
      this.skinHistoryManager = null;
    }

    NMSPacketInterceptor.unregisterListeners(this);

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
            this.getLogger().info("§a§lSALVO §7Perfil de " + profile.getName() + " gravado com sucesso.");
          } else {
            this.getLogger().severe("§4§lFALHA CRÍTICA AO SALVAR §7Perfil de " + profile.getName() + " não foi persistido antes do desligamento.");
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
          this.getLogger().severe("§4§lFALHA CRÍTICA AO SALVAR §7O perfil retido de " + name + " não foi persistido.");
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
        this.getLogger().log(Level.SEVERE, "§4§lERRO CRÍTICO §7Falha ao encerrar o banco de dados durante o desligamento.", ex);
      }
    }
    Role.listRoles().clear();
    validInit = false;
    this.getLogger().info("§a§lDESLIGADO §7com segurança.");
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

  @Getter
  @Setter
  private static Location lobby;


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
          qp.player.sendMessage(MessageUtils.error("FILA", "Você já está na fila de conexão."));
        } else {
          qp.server = name;
        }
        return;
      }

      queue.queue(player, profile, name);
    }
  }
}
