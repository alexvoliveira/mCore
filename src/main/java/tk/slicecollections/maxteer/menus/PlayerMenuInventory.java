package tk.slicecollections.maxteer.menus;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.inventory.impl.simple.SimpleInventory;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

  /** Fills the outer edge without changing the menu's existing content layout. */
  protected final void fillBorder(InventoryEditor editor, int rows) {
    int size = Math.min(Math.max(rows, 1), 6) * 9;
    ItemStack pane = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
    ItemMeta meta = pane.getItemMeta();
    meta.setDisplayName("§r");
    pane.setItemMeta(meta);

    for (int slot = 0; slot < size; slot++) {
      if (slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8) {
        setMenuItem(editor, slot, pane.clone());
      }
    }
  }

  protected final void openForPlayer() {
    openInventory(player);
  }

  protected abstract void onClick(int slot);
}
