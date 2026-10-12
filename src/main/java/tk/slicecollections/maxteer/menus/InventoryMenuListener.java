package tk.slicecollections.maxteer.menus;

import com.henryfabio.minecraft.inventoryapi.manager.InventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Protects custom menus against outside clicks and inventory dragging. */
public final class InventoryMenuListener implements Listener {
  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (event.getClickedInventory() == null && hasViewer(event.getWhoClicked().getName())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (hasViewer(event.getWhoClicked().getName())) {
      event.setCancelled(true);
    }
  }

  private boolean hasViewer(String playerName) {
    return InventoryManager.getViewerController().findViewer(playerName).isPresent();
  }
}
