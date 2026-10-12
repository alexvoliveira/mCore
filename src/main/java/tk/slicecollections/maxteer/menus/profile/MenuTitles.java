package tk.slicecollections.maxteer.menus.profile;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.inventory.impl.paged.PagedInventory;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import com.henryfabio.minecraft.inventoryapi.item.supplier.InventoryItemSupplier;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.border.Border;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.impl.ViewerConfigurationImpl;
import com.henryfabio.minecraft.inventoryapi.viewer.impl.paged.PagedViewer;
import org.bukkit.entity.Player;
import tk.slicecollections.maxteer.menus.MenuProfile;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.titles.Title;
import tk.slicecollections.maxteer.titles.TitleManager;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

import java.util.ArrayList;
import java.util.List;

public class MenuTitles extends PagedInventory {
  private final Profile profile;
  private final Player player;

  public MenuTitles(Profile profile) {
    super("mcore.titles." + profile.getPlayer().getUniqueId(), "Títulos", 45);
    this.profile = profile;
    this.player = profile.getPlayer();
    openInventory(player);
  }

  @Override
  protected void configureViewer(PagedViewer viewer) {
    ViewerConfigurationImpl.Paged configuration = viewer.getConfiguration();
    configuration.border(Border.of(1, 1, 2, 1));
    configuration.itemPageLimit(14);
    configuration.previousPageSlot(36);
    configuration.nextPageSlot(44);
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    editor.setItem(40, InventoryItem.of(BukkitUtils.deserializeItemStack("INK_SACK:1 : 1 : nome>&cVoltar"))
        .defaultCallback(event -> {
          Profile current = Profile.getProfile(player.getName());
          if (current == null) { player.closeInventory(); return; }
          EnumSound.CLICK.play(player, 0.5F, 2.0F);
          new MenuProfile(current);
        }));
  }

  @Override
  protected List<InventoryItemSupplier> createPageItems(PagedViewer viewer) {
    List<Title> ordered = new ArrayList<>();
    List<Title> unowned = new ArrayList<>();
    for (Title title : Title.listTitles()) {
      if (title.has(profile)) ordered.add(title);
      else unowned.add(title);
    }
    ordered.addAll(unowned);

    List<InventoryItemSupplier> items = new ArrayList<>();
    for (Title title : ordered) {
      items.add(() -> InventoryItem.of(title.getIcon(profile)).defaultCallback(event -> selectTitle(title)));
    }
    return items;
  }

  private void selectTitle(Title title) {
    Profile current = Profile.getProfile(player.getName());
    if (current == null) { player.closeInventory(); return; }
    if (!title.has(current)) {
      EnumSound.ENDERMAN_TELEPORT.play(player, 0.5F, 1.0F);
      return;
    }

    EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F);
    Title selected = current.getSelectedContainer().getTitle();
    if (title.equals(selected)) {
      current.getSelectedContainer().setTitle("0");
      TitleManager.deselect(current);
    } else {
      current.getSelectedContainer().setTitle(title.getId());
      TitleManager.select(current, title);
    }
    new MenuTitles(current);
  }
}
