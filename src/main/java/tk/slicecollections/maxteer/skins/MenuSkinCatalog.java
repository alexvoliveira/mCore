package tk.slicecollections.maxteer.skins;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.menus.PlayerMenuInventory;
import tk.slicecollections.maxteer.utils.BukkitUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class MenuSkinCatalog extends PlayerMenuInventory {
  private static final int PAGE_SIZE = 28;
  private static final int[] ITEM_SLOTS = {
      10, 11, 12, 13, 14, 15, 16,
      19, 20, 21, 22, 23, 24, 25,
      28, 29, 30, 31, 32, 33, 34,
      37, 38, 39, 40, 41, 42, 43
  };

  private final SkinLibrary library;
  private final List<SkinDefinition> skins;
  private final int page;
  private final int pageCount;

  public MenuSkinCatalog(Player player, int requestedPage) {
    super(player, "mcore.skins.page." + Math.max(0, requestedPage), "§8Biblioteca de skins", 6);
    this.library = Core.getInstance().getSkinLibrary();
    this.skins = library.getSkins();
    this.pageCount = Math.max(1, (skins.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    this.page = Math.max(0, Math.min(requestedPage, this.pageCount - 1));
    openForPlayer();
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    fillBorder(editor, 6);
    setMenuItem(editor, 4, makeCurrentHead());

    int start = page * PAGE_SIZE;
    int end = Math.min(start + PAGE_SIZE, skins.size());
    for (int index = start; index < end; index++) {
      SkinDefinition definition = skins.get(index);
      String selectedId = library.getSelectedSkinId(player.getUniqueId());
      boolean selected = selectedId != null && selectedId.equalsIgnoreCase(definition.getId());
      setMenuItem(editor, ITEM_SLOTS[index - start], makeHead(definition, selected));
    }

    if (skins.isEmpty()) {
      setMenuItem(editor, 22, button(Material.BARRIER, "§cNenhuma skin disponível",
          Arrays.asList("§7O catálogo não contém skins válidas.")));
    }

    setMenuItem(editor, 45, button(Material.BOOK, "§aHistórico de skins",
        Arrays.asList("§7Reutilize uma das suas últimas skins.", "", "§eClique para abrir.")));
    if (page > 0) {
      setMenuItem(editor, 48, button(Material.ARROW, "§fPágina anterior",
          Arrays.asList("§7Voltar uma página.")));
    }
    setMenuItem(editor, 49, button(Material.PAPER, "§ePágina " + (page + 1) + "§7/§e" + pageCount,
        Arrays.asList("§7Skins disponíveis: §f" + skins.size())));
    if (page + 1 < pageCount) {
      setMenuItem(editor, 50, button(Material.ARROW, "§fPróxima página",
          Arrays.asList("§7Ver mais skins.")));
    }
    setMenuItem(editor, 53, button(Material.BARRIER, "§cFechar", Arrays.asList("§7Fechar o catálogo.")));
  }

  @Override
  protected void onClick(int slot) {
    for (int index = 0; index < ITEM_SLOTS.length; index++) {
      if (ITEM_SLOTS[index] == slot) {
        int skinIndex = page * PAGE_SIZE + index;
        if (skinIndex < skins.size()) {
          library.applySkin(player, skins.get(skinIndex).getId());
        }
        return;
      }
    }

    switch (slot) {
      case 45: MenuSkinHistory.open(player); break;
      case 48: if (page > 0) new MenuSkinCatalog(player, page - 1); break;
      case 50: if (page + 1 < pageCount) new MenuSkinCatalog(player, page + 1); break;
      case 53: player.closeInventory(); break;
      default: break;
    }
  }

  private ItemStack makeCurrentHead() {
    ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
    item = BukkitUtils.putProfileOnSkull(player, item);
    ItemMeta meta = item.getItemMeta();
    SkinDefinition selected = library.getSkin(library.getSelectedSkinId(player.getUniqueId()));
    meta.setDisplayName("§aSua skin atual");
    meta.setLore(Arrays.asList(
        "§7Aplicada: " + (selected == null ? "§fPadrão" : selected.getDisplayName()),
        "",
        "§8Selecione uma cabeça abaixo",
        "§8para trocar sua aparência."));
    item.setItemMeta(meta);
    return item;
  }

  private ItemStack makeHead(SkinDefinition definition, boolean selected) {
    ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
    ItemMeta raw = item.getItemMeta();

    if (raw instanceof SkullMeta && definition.hasTextureValue()) {
      GameProfile profile = new GameProfile(UUID.randomUUID(), null);
      if (definition.hasStoredTexture()) {
        profile.getProperties().put("textures", new Property("textures",
            definition.getTextureValue(), definition.getTextureSignature()));
      } else {
        profile.getProperties().put("textures", new Property("textures", definition.getTextureValue()));
      }
      item = BukkitUtils.putProfileOnSkull((Object) profile, item);
      raw = item.getItemMeta();
    } else if (raw instanceof SkullMeta && definition.getOwnerName() != null
        && !definition.getOwnerName().isEmpty()) {
      ((SkullMeta) raw).setOwner(definition.getOwnerName());
    }

    raw.setDisplayName((selected ? "§a✔ " : "§f") + definition.getDisplayName());
    List<String> lore = new ArrayList<>();
    lore.add("§7Skin: §f" + definition.getOwnerName());
    lore.add("");
    lore.add(selected ? "§aEsta skin está aplicada." : "§eClique para aplicar.");
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
