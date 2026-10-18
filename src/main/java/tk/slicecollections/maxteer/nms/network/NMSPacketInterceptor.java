package tk.slicecollections.maxteer.nms.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;

/**
 * Intercepts the native 1.8_R3 packet stream without ProtocolLib.
 *
 * Packet handlers return the packet to keep or a null value to cancel it.
 */
public final class NMSPacketInterceptor {

  public interface PacketHandler {
    Object onPacketSending(Player player, Object packet);

    Object onPacketReceiving(Player player, Object packet);
  }

  private static final String HANDLER_NAME = "mcore_nms_packet_interceptor";
  private static final CopyOnWriteArrayList<Registration> HANDLERS = new CopyOnWriteArrayList<>();
  private static final ConcurrentHashMap<UUID, Channel> CHANNELS = new ConcurrentHashMap<>();

  private static volatile boolean active;
  private static Plugin corePlugin;
  private static Listener connectionListener;

  private NMSPacketInterceptor() {}

  public static synchronized void enable(Plugin plugin) {
    if (active) {
      return;
    }
    if (plugin == null) {
      throw new IllegalArgumentException("The owning plugin cannot be null.");
    }

    corePlugin = plugin;
    active = true;
    connectionListener = new Listener() {
      @EventHandler
      public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(corePlugin, new Runnable() {
          @Override
          public void run() {
            inject(player);
          }
        }, 1L);
      }

      @EventHandler
      public void onQuit(PlayerQuitEvent event) {
        uninject(event.getPlayer().getUniqueId());
      }
    };
    Bukkit.getPluginManager().registerEvents(connectionListener, plugin);

    for (Player player : Bukkit.getOnlinePlayers()) {
      inject(player);
    }
  }

  public static void registerListener(Plugin owner, PacketHandler handler) {
    if (!active) {
      throw new IllegalStateException("The NMS packet interceptor has not been enabled.");
    }
    if (owner == null || handler == null) {
      throw new IllegalArgumentException("The owner and packet handler are required.");
    }

    for (Registration registration : HANDLERS) {
      if (registration.owner == owner && registration.handler == handler) {
        return;
      }
    }
    HANDLERS.add(new Registration(owner, handler));

    for (Player player : Bukkit.getOnlinePlayers()) {
      inject(player);
    }
  }

  public static void unregisterListeners(Plugin owner) {
    for (Registration registration : new ArrayList<>(HANDLERS)) {
      if (registration.owner == owner) {
        HANDLERS.remove(registration);
      }
    }

    if (owner == corePlugin) {
      shutdown(owner);
    }
  }

  public static synchronized void shutdown(Plugin owner) {
    if (owner != corePlugin) {
      return;
    }

    active = false;
    HANDLERS.clear();

    if (connectionListener != null) {
      HandlerList.unregisterAll(connectionListener);
      connectionListener = null;
    }

    for (Channel channel : new ArrayList<>(CHANNELS.values())) {
      removeHandler(channel);
    }
    CHANNELS.clear();
    corePlugin = null;
  }

  private static void inject(final Player player) {
    if (!active || player == null || !player.isOnline()) {
      return;
    }

    final Channel channel;
    try {
      channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
    } catch (RuntimeException ex) {
      logFailure("Could not access the 1.8_R3 channel for " + player.getName() + ".", ex);
      return;
    }

    if (channel == null) {
      return;
    }

    final UUID playerId = player.getUniqueId();
    Channel previous = CHANNELS.put(playerId, channel);
    if (previous != null && previous != channel) {
      removeHandler(previous);
    }

    executeOnEventLoop(channel, new Runnable() {
      @Override
      public void run() {
        if (!active || !channel.isOpen()) {
          return;
        }

        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
          return;
        }

        PlayerDuplexHandler handler = new PlayerDuplexHandler(player, playerId, channel);
        if (pipeline.context("packet_handler") != null) {
          pipeline.addBefore("packet_handler", HANDLER_NAME, handler);
        } else {
          pipeline.addLast(HANDLER_NAME, handler);
        }
      }
    });
  }

  private static void uninject(UUID playerId) {
    Channel channel = CHANNELS.remove(playerId);
    if (channel != null) {
      removeHandler(channel);
    }
  }

  private static void removeHandler(final Channel channel) {
    executeOnEventLoop(channel, new Runnable() {
      @Override
      public void run() {
        if (channel.pipeline().get(HANDLER_NAME) != null) {
          channel.pipeline().remove(HANDLER_NAME);
        }
      }
    });
  }

  private static void executeOnEventLoop(final Channel channel, final Runnable task) {
    try {
      if (channel.eventLoop().inEventLoop()) {
        task.run();
      } else {
        channel.eventLoop().execute(task);
      }
    } catch (RejectedExecutionException | IllegalStateException ex) {
      logFailure("Could not update a player's Netty pipeline.", ex);
    }
  }

  private static Object dispatch(Player player, Object packet, boolean sending) {
    Object current = packet;
    for (Registration registration : HANDLERS) {
      if (!registration.owner.isEnabled()) {
        continue;
      }

      try {
        current = sending
            ? registration.handler.onPacketSending(player, current)
            : registration.handler.onPacketReceiving(player, current);
        if (current == null) {
          return null;
        }
      } catch (Throwable ex) {
        if (ex instanceof VirtualMachineError) {
          throw (VirtualMachineError) ex;
        }
        if (ex instanceof ThreadDeath) {
          throw (ThreadDeath) ex;
        }
        logFailure("A native packet handler failed (" + registration.owner.getName() + ").", ex);
      }
    }
    return current;
  }

  private static void logFailure(String message, Throwable error) {
    Plugin plugin = corePlugin;
    if (plugin != null) {
      plugin.getLogger().log(Level.SEVERE, message, error);
    }
  }

  private static final class Registration {
    private final Plugin owner;
    private final PacketHandler handler;

    private Registration(Plugin owner, PacketHandler handler) {
      this.owner = owner;
      this.handler = handler;
    }
  }

  private static final class PlayerDuplexHandler extends ChannelDuplexHandler {
    private final Player player;
    private final UUID playerId;
    private final Channel channel;

    private PlayerDuplexHandler(Player player, UUID playerId, Channel channel) {
      this.player = player;
      this.playerId = playerId;
      this.channel = channel;
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
      Object transformed = dispatch(player, message, true);
      if (transformed != null) {
        context.write(transformed, promise);
      }
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
      Object transformed = dispatch(player, message, false);
      if (transformed != null) {
        context.fireChannelRead(transformed);
      }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
      CHANNELS.remove(playerId, channel);
      super.channelInactive(context);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext context) throws Exception {
      CHANNELS.remove(playerId, channel);
      super.handlerRemoved(context);
    }
  }
}
