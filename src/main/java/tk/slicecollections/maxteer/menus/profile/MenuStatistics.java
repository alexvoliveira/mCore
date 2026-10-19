package tk.slicecollections.maxteer.menus.profile;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import me.clip.placeholderapi.PlaceholderAPI;
import tk.slicecollections.maxteer.menus.MenuProfile;
import tk.slicecollections.maxteer.menus.PlayerMenuInventory;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

public class MenuStatistics extends PlayerMenuInventory {
  public MenuStatistics(Profile profile) {
    super(profile.getPlayer(), "mcore.statistics", "Estatísticas", 4);
    openForPlayer();
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    fillBorder(editor, 4);
    setMenuItem(editor, 11, BukkitUtils.deserializeItemStack(PlaceholderAPI.setPlaceholders(player,
        "GRASS : 1 : nome>&aSky Wars : desc>&eSolo:\n &8▪ &fAbates: &7%mCore_SkyWars_1v1kills%\n &8▪ &fMortes: &7%mCore_SkyWars_1v1deaths%\n &8▪ &fVitórias: &7%mCore_SkyWars_1v1wins%\n &8▪ &fPartidas: &7%mCore_SkyWars_1v1games%\n &8▪ &fAssistências: &7%mCore_SkyWars_1v1assists%\n \n&eDupla:\n &8▪ &fAbates: &7%mCore_SkyWars_2v2kills%\n &8▪ &fMortes: &7%mCore_SkyWars_2v2deaths%\n &8▪ &fVitórias: &7%mCore_SkyWars_2v2wins%\n &8▪ &fPartidas: &7%mCore_SkyWars_2v2games%\n &8▪ &fAssistências: &7%mCore_SkyWars_2v2assists%\n \n&fCoins: &6%mCore_SkyWars_coins%")));
    setMenuItem(editor, 13, BukkitUtils.deserializeItemStack(PlaceholderAPI.setPlaceholders(player,
        "STAINED_CLAY:11 : 1 : nome>&aThe Bridge : desc>&e1v1:\n &8▪ &fAbates: &7%mCore_TheBridge_1v1kills%\n &8▪ &fMortes: &7%mCore_TheBridge_1v1deaths%\n &8▪ &fPontos: &7%mCore_TheBridge_1v1points%\n &8▪ &fVitórias: &7%mCore_TheBridge_1v1wins%\n &8▪ &fPartidas: &7%mCore_TheBridge_1v1games%\n \n&e2v2:\n &8▪ &fAbates: &7%mCore_TheBridge_2v2kills%\n &8▪ &fMortes: &7%mCore_TheBridge_2v2deaths%\n &8▪ &fPontos: &7%mCore_TheBridge_2v2points%\n &8▪ &fVitórias: &7%mCore_TheBridge_2v2wins%\n &8▪ &fPartidas: &7%mCore_TheBridge_2v2games%\n \n&eWinstreak:\n &8▪ &fDiário: &7%mCore_TheBridge_winstreak%\n \n&fCoins: &6%mCore_TheBridge_coins%")));
    setMenuItem(editor, 15, BukkitUtils.deserializeItemStack(PlaceholderAPI.setPlaceholders(player,
        "BOW : 1 : nome>&aMurder : desc>&eClássico: \n &8▪ &fAbates: &7%mCore_Murder_classic_kills%\n &8▪ &fVitórias: &7%mCore_Murder_classic_wins%\n \n&eAssassinos: \n &8▪ &fAbates: &7%mCore_Murder_assassins_kills%\n &8▪ &fVitórias: &7%mCore_Murder_assassins_wins%\n \n&fCoins: &6%mCore_Murder_coins%")));
    setMenuItem(editor, 31, BukkitUtils.deserializeItemStack("INK_SACK:1 : 1 : nome>&cVoltar"));
  }

  @Override
  protected void onClick(int slot) {
    Profile current = Profile.getProfile(player.getName());
    if (current == null) { player.closeInventory(); return; }
    if (slot == 11 || slot == 13 || slot == 15) EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F);
    else if (slot == 31) { EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuProfile(current); }
  }
}
