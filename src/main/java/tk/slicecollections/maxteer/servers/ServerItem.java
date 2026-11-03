package tk.slicecollections.maxteer.servers;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.bukkit.scheduler.BukkitRunnable;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.plugin.config.MConfig;
import tk.slicecollections.maxteer.servers.balancer.BaseBalancer;
import tk.slicecollections.maxteer.servers.balancer.Server;
import tk.slicecollections.maxteer.servers.balancer.type.LeastConnection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Maxter
 */
@Getter
@AllArgsConstructor
public class ServerItem {

  private String key;
  private int slot;
  private String icon;
  private BaseBalancer<Server> balancer;

  public void connect(Profile profile) {
    Server server = balancer.next();
    if (server != null) {
      Core.sendServer(profile, server.getName());
    } else {
      profile.getPlayer().sendMessage("§cNão foi possível se conectar a esse servidor no momento!");
    }
  }

  private static final List<ServerItem> SERVERS = new ArrayList<>();
  public static final MConfig CONFIG = Core.getInstance().getConfig("servers");
  public static final List<Integer> DISABLED_SLOTS = CONFIG.getIntegerList("disabled-slots");
  public static final Map<String, Integer> SERVER_COUNT = new ConcurrentHashMap<>();

  public static void setupServers() {
    for (String key : CONFIG.getSection("items").getKeys(false)) {
      ServerItem si = new ServerItem(key, CONFIG.getInt("items." + key + ".slot"), CONFIG.getString("items." + key + ".icon"), new LeastConnection<>());
      SERVERS.add(si);
      CONFIG.getStringList("items." + key + ".servernames").forEach(server -> {
        if (server == null) {
          return;
        }
        String[] parts = server.split(" ; ", 2);
        if (parts.length != 2) {
          Core.getInstance().getLogger().warning("Ignoring invalid server entry for '" + key + "': " + server);
          return;
        }
        String[] address = parts[0].split(":", 2);
        if (address.length != 2) {
          Core.getInstance().getLogger().warning("Ignoring invalid server address for '" + key + "': " + parts[0]);
          return;
        }
        try {
          int port = Integer.parseInt(address[1]);
          if (address[0].trim().isEmpty() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid server address");
          }
          si.getBalancer().add(parts[1].trim(), new Server(address[0].trim() + ":" + port,
            parts[1].trim(), CONFIG.getInt("items." + key + ".max-players")));
        } catch (IllegalArgumentException ex) {
          Core.getInstance().getLogger().warning("Ignoring invalid server address for '" + key + "': " + parts[0]);
        }
      });
    }

    new BukkitRunnable() {
      @Override
      public void run() {
        SERVERS.forEach(server -> server.getBalancer().getList().forEach(Server::fetch));
      }
    }.runTaskTimerAsynchronously(Core.getInstance(), 0, 40);
  }

  public static Collection<ServerItem> listServers() {
    return SERVERS;
  }

  public static ServerItem getServerItem(String key) {
    return SERVERS.stream().filter(si -> si.getKey().equals(key)).findFirst().orElse(null);
  }

  public static boolean alreadyQuerying(String servername) {
    return SERVERS.stream().anyMatch(si -> si.getBalancer().keySet().contains(servername));
  }

  public static int getServerCount(ServerItem serverItem) {
    return serverItem.getBalancer().getTotalNumber();
  }

  public static int getServerCount(String servername) {
    return SERVER_COUNT.get(servername) == null ? 0 : SERVER_COUNT.get(servername);
  }
}
