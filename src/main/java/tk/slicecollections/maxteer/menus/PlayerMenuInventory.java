package tk.slicecollections.maxteer.menus;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.inventory.impl.simple.SimpleInventory;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Common per-player menu behavior backed by inventory-api. */
public abstract class PlayerMenuInventory extends SimpleInventory {
  protected final Player player;

  protected PlayerMenuInventory(Player player, String id, String title, int rows) {
    super(id + "." + player.getUniqueId(), title, Math.min(Math.max(rows, 1), 6) * 9);
    this.player = player;
  }

  protected final void setMenuItem(InventoryEditor editor, int slot, ItemStack item) {
    if (item == null || item.getType() == Material.AIR) {
      editor.setEmptyItem(slot);
      return;
    }
    editor.setItem(slot, InventoryItem.of(item).defaultCallback(event -> onClick(event.getSlot())));
  }

  protected final void openForPlayer() {
    openInventory(player);
  }

  protected abstract void onClick(int slot);
}
