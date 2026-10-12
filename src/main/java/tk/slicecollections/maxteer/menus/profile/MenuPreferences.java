package tk.slicecollections.maxteer.menus.profile;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import tk.slicecollections.maxteer.database.data.container.PreferencesContainer;
import tk.slicecollections.maxteer.menus.MenuProfile;
import tk.slicecollections.maxteer.menus.PlayerMenuInventory;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.player.enums.BloodAndGore;
import tk.slicecollections.maxteer.player.enums.PlayerVisibility;
import tk.slicecollections.maxteer.player.enums.PrivateMessages;
import tk.slicecollections.maxteer.player.enums.ProtectionLobby;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.StringUtils;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

public class MenuPreferences extends PlayerMenuInventory {
  private final Profile profile;

  public MenuPreferences(Profile profile) {
    super(profile.getPlayer(), "mcore.preferences", "Preferências", 5);
    this.profile = profile;
    openForPlayer();
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    PreferencesContainer pc = profile.getPreferencesContainer();
    PlayerVisibility pv = pc.getPlayerVisibility();
    setMenuItem(editor, 11, BukkitUtils.deserializeItemStack("ITEM_FRAME : 1 : nome>&aJogadores : desc>&7Ative ou desative os\n&7jogadores no lobby."));
    setMenuItem(editor, 20, BukkitUtils.deserializeItemStack("INK_SACK:" + pv.getInkSack() + " : 1 : nome>" + pv.getName() + " : desc>&fEstado: &7" + StringUtils.stripColors(pv.getName()) + "\n \n&eClique para modificar!"));

    PrivateMessages pm = pc.getPrivateMessages();
    setMenuItem(editor, 12, BukkitUtils.deserializeItemStack("PAPER : 1 : nome>&aMensagens privadas : desc>&7Ative ou desative as mensagens\n&7enviadas através do tell."));
    setMenuItem(editor, 21, BukkitUtils.deserializeItemStack("INK_SACK:" + pm.getInkSack() + " : 1 : nome>" + pm.getName() + " : desc>&fEstado: &7" + StringUtils.stripColors(pm.getName()) + "\n \n&eClique para modificar!"));

    BloodAndGore bg = pc.getBloodAndGore();
    setMenuItem(editor, 14, BukkitUtils.deserializeItemStack("REDSTONE : 1 : nome>&aViolência : desc>&7Ative ou desative as partículas\n&7de sangue no PvP."));
    setMenuItem(editor, 23, BukkitUtils.deserializeItemStack("INK_SACK:" + bg.getInkSack() + " : 1 : nome>" + bg.getName() + " : desc>&fEstado: &7" + StringUtils.stripColors(bg.getName()) + "\n \n&eClique para modificar!"));

    ProtectionLobby pl = pc.getProtectionLobby();
    setMenuItem(editor, 15, BukkitUtils.deserializeItemStack("NETHER_STAR : 1 : nome>&aProteção no /lobby : desc>&7Ative ou desative o pedido de\n&7confirmação ao utilizar /lobby."));
    setMenuItem(editor, 24, BukkitUtils.deserializeItemStack("INK_SACK:" + pl.getInkSack() + " : 1 : nome>" + pl.getName() + " : desc>&fEstado: &7" + StringUtils.stripColors(pl.getName()) + "\n \n&eClique para modificar!"));
    setMenuItem(editor, 40, BukkitUtils.deserializeItemStack("INK_SACK:1 : 1 : nome>&cVoltar"));
  }

  @Override
  protected void onClick(int slot) {
    Profile current = Profile.getProfile(player.getName());
    if (current == null) { player.closeInventory(); return; }
    if (slot == 11 || slot == 12 || slot == 14 || slot == 15) {
      EnumSound.CLICK.play(player, 0.5F, 2.0F);
      return;
    }
    switch (slot) {
      case 20: EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F); current.getPreferencesContainer().changePlayerVisibility(); if (!current.playingGame()) current.refreshPlayers(); new MenuPreferences(current); break;
      case 21: EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F); current.getPreferencesContainer().changePrivateMessages(); new MenuPreferences(current); break;
      case 23: EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F); current.getPreferencesContainer().changeBloodAndGore(); new MenuPreferences(current); break;
      case 24: EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F); current.getPreferencesContainer().changeProtectionLobby(); new MenuPreferences(current); break;
      case 40: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuProfile(current); break;
      default: break;
    }
  }
}
