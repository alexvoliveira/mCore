package tk.slicecollections.maxteer.skins;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.menus.PlayerMenuInventory;

import java.util.Arrays;
import java.util.List;

public final class MenuSkinCatalog extends PlayerMenuInventory {
  private static final int PAGE_SIZE = 45;
  private final List<SkinDefinition> skins;
  private final int page;
  private final int pageCount;

  public MenuSkinCatalog(Player player, int requestedPage) {
    super(player, "mcore.skins.page." + Math.max(0, requestedPage), "Seleção de skins", 6);
    this.skins = Core.getInstance().getSkinLibrary().getSkins();
    this.pageCount = Math.max(1, (skins.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    this.page = Math.max(0, Math.min(requestedPage, this.pageCount - 1));
    openForPlayer();
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    int start = page * PAGE_SIZE;
    int end = Math.min(start + PAGE_SIZE, skins.size());
    for (int index = start; index < end; index++) {
      SkinDefinition definition = skins.get(index);
      setMenuItem(editor, index - start, makeHead(definition.getOwnerName(), definition.getDisplayName(),
          Arrays.asList("§7Skin: §f" + definition.getOwnerName(), "", "§eClique para aplicar.")));
    }

    setMenuItem(editor, 45, button(Material.BOOK, "§aHistórico de skins",
        Arrays.asList("§7Veja as últimas 7 skins utilizadas.", "", "§eClique para abrir.")));
    if (page > 0) {
      setMenuItem(editor, 48, button(Material.ARROW, "§fPágina anterior", Arrays.asList("§7Página " + page + " de " + pageCount)));
    }
    setMenuItem(editor, 49, button(Material.PAPER, "§ePágina " + (page + 1) + "§7/§e" + pageCount,
        Arrays.asList("§7Skins disponíveis: §f" + skins.size())));
    if (page + 1 < pageCount) {
      setMenuItem(editor, 50, button(Material.ARROW, "§fPróxima página", Arrays.asList("§7Página " + (page + 2) + " de " + pageCount)));
    }
    setMenuItem(editor, 53, button(Material.BARRIER, "§cFechar", Arrays.asList("§7Fechar o menu.")));
  }

  @Override
  protected void onClick(int slot) {
    if (slot >= 0 && slot < PAGE_SIZE) {
      int index = page * PAGE_SIZE + slot;
      if (index < skins.size()) {
        Core.getInstance().getSkinLibrary().applySkin(player, skins.get(index).getId());
      }
      return;
    }
    switch (slot) {
      case 45: MenuSkinHistory.open(player); break;
      case 48: if (page > 0) new MenuSkinCatalog(player, page - 1); break;
      case 50: if (page + 1 < pageCount) new MenuSkinCatalog(player, page + 1); break;
      case 53: player.closeInventory(); break;
      default: break;
    }
  }

  private ItemStack makeHead(String owner, String name, List<String> lore) {
    ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
    ItemMeta raw = item.getItemMeta();
    if (raw instanceof SkullMeta && owner != null && !owner.isEmpty()) {
      ((SkullMeta) raw).setOwner(owner);
    }
    raw.setDisplayName(name);
    raw.setLore(lore);
    item.setItemMeta(raw);
    return item;
  }

  private ItemStack button(Material material, String name, List<String> lore) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(name);
    meta.setLore(lore);
    item.setItemMeta(meta);
    return item;
  }
}
