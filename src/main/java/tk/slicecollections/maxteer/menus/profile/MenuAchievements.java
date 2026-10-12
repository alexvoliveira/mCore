package tk.slicecollections.maxteer.menus.profile;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import tk.slicecollections.maxteer.achievements.Achievement;
import tk.slicecollections.maxteer.achievements.types.MurderAchievement;
import tk.slicecollections.maxteer.achievements.types.SkyWarsAchievement;
import tk.slicecollections.maxteer.achievements.types.TheBridgeAchievement;
import tk.slicecollections.maxteer.menus.MenuProfile;
import tk.slicecollections.maxteer.menus.PlayerMenuInventory;
import tk.slicecollections.maxteer.menus.profile.achievements.MenuAchievementsList;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

import java.util.List;

public class MenuAchievements extends PlayerMenuInventory {
  private final Profile profile;

  public MenuAchievements(Profile profile) {
    super(profile.getPlayer(), "mcore.achievements", "Desafios", 4);
    this.profile = profile;
    openForPlayer();
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    List<SkyWarsAchievement> skywars = Achievement.listAchievements(SkyWarsAchievement.class);
    long max = skywars.size();
    long completed = skywars.stream().filter(a -> a.isCompleted(profile)).count();
    String color = completed == max ? "&a" : completed > max / 2 ? "&7" : "&c";
    setMenuItem(editor, 11, BukkitUtils.deserializeItemStack("GRASS : 1 : nome>&aSky Wars : desc>&fDesafios: " + color + completed + "/" + max + "\n \n&eClique para visualizar!"));

    List<TheBridgeAchievement> thebridge = Achievement.listAchievements(TheBridgeAchievement.class);
    max = thebridge.size();
    completed = thebridge.stream().filter(a -> a.isCompleted(profile)).count();
    color = completed == max ? "&a" : completed > max / 2 ? "&7" : "&c";
    setMenuItem(editor, 13, BukkitUtils.deserializeItemStack("STAINED_CLAY:11 : 1 : nome>&aThe Bridge : desc>&fDesafios: " + color + completed + "/" + max + "\n \n&eClique para visualizar!"));

    List<MurderAchievement> murder = Achievement.listAchievements(MurderAchievement.class);
    max = murder.size();
    completed = murder.stream().filter(a -> a.isCompleted(profile)).count();
    color = completed == max ? "&a" : completed > max / 2 ? "&7" : "&c";
    setMenuItem(editor, 15, BukkitUtils.deserializeItemStack("BOW : 1 : nome>&aMurder : desc>&fDesafios: " + color + completed + "/" + max + "\n \n&eClique para visualizar!"));
    setMenuItem(editor, 31, BukkitUtils.deserializeItemStack("INK_SACK:1 : 1 : nome>&cVoltar"));
  }

  @Override
  protected void onClick(int slot) {
    Profile current = Profile.getProfile(player.getName());
    if (current == null) { player.closeInventory(); return; }
    switch (slot) {
      case 11: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuAchievementsList<>(current, "Sky Wars", SkyWarsAchievement.class); break;
      case 13: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuAchievementsList<>(current, "The Bridge", TheBridgeAchievement.class); break;
      case 15: EnumSound.CLICK.play(player, 0.5F, 1.0F); new MenuAchievementsList<>(current, "Murder", MurderAchievement.class); break;
      case 31: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuProfile(current); break;
      default: break;
    }
  }
}
