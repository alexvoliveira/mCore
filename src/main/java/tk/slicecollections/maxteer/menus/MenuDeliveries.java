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
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.database.data.container.DeliveriesContainer;
import tk.slicecollections.maxteer.deliveries.Delivery;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

import java.util.HashMap;
import java.util.Map;

public class MenuDeliveries extends PlayerMenuInventory implements Listener {
  private final Profile profile;
  private final Map<Integer, Delivery> deliveries = new HashMap<>();
  private Inventory inventory;
  private BukkitTask updateTask;

  public MenuDeliveries(Profile profile) {
    super(profile.getPlayer(), "mcore.deliveries", "Entregas", 5);
    this.profile = profile;
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
    deliveries.clear();
    for (Delivery delivery : Delivery.listDeliveries()) {
      ItemStack item = delivery.getIcon(profile);
      deliveries.put(delivery.getSlot(), delivery);
      setMenuItem(editor, delivery.getSlot(), item);
    }
  }

  @Override
  protected void onClick(int slot) {
    Delivery delivery = deliveries.get(slot);
    if (delivery == null) return;
    DeliveriesContainer container = profile.getDeliveriesContainer();
    if (container.alreadyClaimed(delivery.getId()) || !delivery.hasPermission(player)) {
      EnumSound.ENDERMAN_TELEPORT.play(player, 0.5F, 1.0F);
      return;
    }
    EnumSound.LEVEL_UP.play(player, 1.0F, 1.0F);
    container.claimDelivery(delivery.getId(), delivery.getDays());
    delivery.listRewards().forEach(reward -> reward.dispatch(profile));
    player.sendMessage(delivery.getMessage());
    player.closeInventory();
  }

  private void cancel() {
    if (updateTask != null) { updateTask.cancel(); updateTask = null; }
    HandlerList.unregisterAll(this);
    deliveries.clear();
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
