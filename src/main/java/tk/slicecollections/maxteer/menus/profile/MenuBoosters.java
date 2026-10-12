package tk.slicecollections.maxteer.menus.profile;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.booster.Booster;
import tk.slicecollections.maxteer.booster.Booster.BoosterType;
import tk.slicecollections.maxteer.booster.NetworkBooster;
import tk.slicecollections.maxteer.menus.MenuProfile;
import tk.slicecollections.maxteer.menus.PlayerMenuInventory;
import tk.slicecollections.maxteer.menus.profile.boosters.MenuBoostersList;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.TimeUtils;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

public class MenuBoosters extends PlayerMenuInventory {
  private final Profile profile;

  public MenuBoosters(Profile profile) {
    super(profile.getPlayer(), "mcore.boosters", "Multiplicadores", 4);
    this.profile = profile;
    openForPlayer();
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    setMenuItem(editor, 12, BukkitUtils.deserializeItemStack("POTION : 1 : nome>&aMultiplicadores Pessoais : desc>&7Concede um &6Multiplicador de Coins &7apenas\n&7para &bVOCÊ &7em todos os minigames do servidor\n&7por um curto período de tempo.\n \n&eClique para ver seus multiplicadores!"));
    setMenuItem(editor, 14, BukkitUtils.deserializeItemStack("POTION:8232 : 1 : esconder>tudo : nome>&aMultiplicadores Gerais : desc>&7Concede um &6Multiplicador de Coins &7para\n&bTODOS &7os jogadores em apenas um minigame\n&7por um curto período de tempo.\n \n&eClique para ver seus multiplicadores!"));

    String booster = profile.getBoostersContainer().getEnabled();
    StringBuilder network = new StringBuilder(), result = new StringBuilder();
    for (int index = 0; index < Core.minigames.size(); index++) {
      String minigame = Core.minigames.get(index);
      NetworkBooster nb = Booster.getNetworkBooster(minigame);
      network.append(" &8• &b").append(minigame).append(": ")
          .append(nb == null ? "&cDesativado" : "&6" + nb.getMultiplier() + "x &7por " + Role.getColored(nb.getBooster()) + " &8(" + TimeUtils.getTimeUntil(nb.getExpires()) + ")")
          .append(index + 1 == Core.minigames.size() ? "" : "\n");
    }
    result.append("&fMultiplicador Pessoal ativo:\n ");
    if (booster == null) result.append("&cVocê não possui nenhum multiplicador ativo.");
    else {
      String[] split = booster.split(":");
      double all = 50.0 * Double.parseDouble(split[0]);
      result.append("&8• &6Multiplicador ").append(split[0]).append("x &8(").append(TimeUtils.getTimeUntil(Long.parseLong(split[1])))
          .append(")\n \n&fCálculo:\n &7Com o multiplicador ativo ao receber &650 Coins &7o\n &7total recebido será equivalente a &6")
          .append((int) all).append(" Coins&7.");
    }
    setMenuItem(editor, 30, BukkitUtils.deserializeItemStack("INK_SACK:1 : 1 : nome>&cVoltar"));
    setMenuItem(editor, 31, BukkitUtils.deserializeItemStack("PAPER : 1 : nome>&aMultiplicadores de Crédito : desc>&7Os Multiplicadores são acumulativos. Quanto mais\n&7multiplicadores ativos, maior será o bônus recebido.\n \n&fMultiplicadores Gerais:\n" + network + "\n \n" + result));
  }

  @Override
  protected void onClick(int slot) {
    Profile current = Profile.getProfile(player.getName());
    if (current == null) { player.closeInventory(); return; }
    switch (slot) {
      case 12: EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F); new MenuBoostersList(current, "Pessoais", BoosterType.PRIVATE); break;
      case 14: EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F); new MenuBoostersList(current, "Gerais", BoosterType.NETWORK); break;
      case 30: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuProfile(current); break;
      case 31: EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F); break;
      default: break;
    }
  }
}
