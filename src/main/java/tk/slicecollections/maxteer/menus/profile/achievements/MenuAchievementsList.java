package tk.slicecollections.maxteer.menus.profile.achievements;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.inventory.impl.paged.PagedInventory;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import com.henryfabio.minecraft.inventoryapi.item.supplier.InventoryItemSupplier;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.border.Border;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.impl.ViewerConfigurationImpl;
import com.henryfabio.minecraft.inventoryapi.viewer.impl.paged.PagedViewer;
import tk.slicecollections.maxteer.achievements.Achievement;
import tk.slicecollections.maxteer.menus.profile.MenuAchievements;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

import java.util.ArrayList;
import java.util.List;

public class MenuAchievementsList<T extends Achievement> extends PagedInventory {
  private final Profile profile;
  private final Class<T> achievementClass;

  public MenuAchievementsList(Profile profile, String name, Class<T> achievementClass) {
    super("mcore.achievement-list." + profile.getPlayer().getUniqueId() + "." + name, "Desafios - " + name, 45);
    this.profile = profile;
    this.achievementClass = achievementClass;
    openInventory(profile.getPlayer());
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
          Profile current = Profile.getProfile(profile.getPlayer().getName());
          if (current == null) { profile.getPlayer().closeInventory(); return; }
          EnumSound.CLICK.play(current.getPlayer(), 0.5F, 2.0F);
          new MenuAchievements(current);
        }));
  }

  @Override
  protected List<InventoryItemSupplier> createPageItems(PagedViewer viewer) {
    List<InventoryItemSupplier> items = new ArrayList<>();
    for (T achievement : Achievement.listAchievements(achievementClass)) {
      items.add(() -> InventoryItem.of(achievement.getIcon(profile)));
    }
    return items;
  }
}
