package tk.slicecollections.maxteer.menus;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.servers.ServerItem;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.StringUtils;

public class MenuServers extends PlayerMenuInventory implements Listener {
  private Inventory inventory;
  private BukkitTask updateTask;

  public MenuServers(Profile profile) {
    super(profile.getPlayer(), "mcore.servers", ServerItem.CONFIG.getString("title"), ServerItem.CONFIG.getInt("rows"));
    Bukkit.getPluginManager().registerEvents(this, Core.getInstance());
    openForPlayer();
    this.updateTask = Bukkit.getScheduler().runTaskTimer(Core.getInstance(), () -> {
      if (inventory != null && player.getOpenInventory().getTopInventory().equals(inventory)) updateInventory(player);
      else cancel();
    }, 20L, 20L);
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    this.inventory = editor.getInventory();
  }

  @Override
  protected void update(Viewer viewer, InventoryEditor editor) {
    for (ServerItem serverItem : ServerItem.listServers()) {
      setMenuItem(editor, serverItem.getSlot(),
          BukkitUtils.deserializeItemStack(serverItem.getIcon().replace("{players}",
              StringUtils.formatNumber(ServerItem.getServerCount(serverItem)))));
    }
  }

  @Override
  protected void onClick(int slot) {
    Profile profile = Profile.getProfile(player.getName());
    if (profile == null) { player.closeInventory(); return; }
    if (ServerItem.DISABLED_SLOTS.contains(slot)) {
      player.sendMessage("§cVocê já está conectado a este servidor.");
      return;
    }
    ServerItem.listServers().stream().filter(item -> item.getSlot() == slot).findFirst()
        .ifPresent(serverItem -> serverItem.connect(profile));
  }

  private void cancel() {
    if (updateTask != null) { updateTask.cancel(); updateTask = null; }
    HandlerList.unregisterAll(this);
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    if (event.getPlayer().equals(player)) cancel();
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    if (inventory != null && event.getPlayer().equals(player) && event.getInventory().equals(inventory)) cancel();
  }
}
