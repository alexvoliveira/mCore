package tk.slicecollections.maxteer.queue;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.nms.NMS;
import tk.slicecollections.maxteer.player.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class Queue {

  private BukkitTask task;
  private final List<QueuePlayer> players = new ArrayList<>();

  public void queue(Player player, Profile profile, String server) {
    if (!Bukkit.isPrimaryThread()) {
      Bukkit.getScheduler().runTask(Core.getInstance(), () -> queue(player, profile, server));
      return;
    }

    this.players.add(new QueuePlayer(player, profile, server));
    if (this.task == null) {
      this.task = new BukkitRunnable() {
        private boolean saving;
        private boolean readyToConnect;
        private QueuePlayer current;

        @Override
        public void run() {
          int position = 1;
          for (QueuePlayer queued : new ArrayList<>(players)) {
            if (queued.player == null || !queued.player.isOnline()) {
              players.remove(queued);
              queued.destroy();
              continue;
            }

            NMS.sendActionBar(queued.player, "§aVocê está na Fila para conectar-se a §8" + queued.server + " §7(Posição #" + position + ")");
            position++;
          }

          if (this.current == null) {
            if (players.isEmpty()) {
              Queue.this.task.cancel();
              Queue.this.task = null;
              return;
            }
            this.current = players.get(0);
            this.saving = false;
            this.readyToConnect = false;
            return;
          }

          if (this.current.player == null || !this.current.player.isOnline()) {
            players.remove(this.current);
            this.current.destroy();
            this.current = null;
            this.saving = false;
            this.readyToConnect = false;
            return;
          }

          if (this.readyToConnect) {
            Player player = this.current.player;
            String server = this.current.server;
            player.closeInventory();
            NMS.sendActionBar(player, "");
            player.sendMessage("§aConectando...");
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(Core.getInstance(), "BungeeCord", out.toByteArray());
            players.remove(this.current);
            this.current.destroy();
            this.current = null;
            this.saving = false;
            this.readyToConnect = false;
            return;
          }

          if (!this.saving) {
            this.saving = true;
            final QueuePlayer target = this.current;
            target.profile.saveAsync().whenComplete((ignored, failure) -> {
              try {
                Bukkit.getScheduler().runTask(Core.getInstance(), () -> {
                  if (this.current != target) {
                    return;
                  }
                  this.saving = false;
                  if (failure == null) {
                    this.readyToConnect = true;
                  } else {
                    Core.getInstance().getLogger().log(Level.WARNING,
                      "O salvamento do perfil falhou; a transferência foi cancelada para proteger os dados.", failure);
                    if (target.player != null && target.player.isOnline()) {
                      target.player.sendMessage("§cNão foi possível salvar seus dados. A transferência foi cancelada; tente novamente mais tarde.");
                    }
                    players.remove(target);
                    target.destroy();
                    this.current = null;
                    this.saving = false;
                    this.readyToConnect = false;
                  }
                });
              } catch (RuntimeException ex) {
                Core core = Core.getInstance();
                if (core != null) {
                  core.getLogger().log(Level.FINE, "Queue callback arrived during plugin shutdown.", ex);
                }
              }
            });
          }
        }
      }.runTaskTimer(Core.getInstance(), 0, 20);
    }
  }

  public QueuePlayer getQueuePlayer(Player player) {
    return this.players.stream().filter(qp -> qp.player != null && qp.player.equals(player)).findFirst().orElse(null);
  }

  public void shutdown() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
    for (QueuePlayer queued : new ArrayList<>(this.players)) {
      queued.destroy();
    }
    this.players.clear();
  }

  public static void shutdownAll() {
    VIP.shutdown();
    MEMBER.shutdown();
  }

  public static final Queue VIP = new Queue();
  public static final Queue MEMBER = new Queue();
}
