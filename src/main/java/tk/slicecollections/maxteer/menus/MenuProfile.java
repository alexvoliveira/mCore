package tk.slicecollections.maxteer.menus;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import org.bukkit.inventory.ItemStack;
import tk.slicecollections.maxteer.menus.profile.*;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class MenuProfile extends PlayerMenuInventory {
  private static final SimpleDateFormat SDF =
      new SimpleDateFormat("d 'de' MMMM. yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"));
  private final Profile profile;

  public MenuProfile(Profile profile) {
    super(profile.getPlayer(), "mcore.profile", "Meu Perfil", 3);
    this.profile = profile;
    openForPlayer();
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    fillBorder(editor, 3);
    ItemStack skull = BukkitUtils.putProfileOnSkull(player, BukkitUtils.deserializeItemStack(
        "SKULL_ITEM:3 : 1 : nome>&aInformações Pessoais : desc>&fRank: "
            + Role.getRoleByName(profile.getDataContainer("mCoreProfile", "role").getAsString()).getName()
            + "\n \n&fCadastrado: &7" + SDF.format(profile.getDataContainer("mCoreProfile", "created").getAsLong())
            + "\n&fÚltimo acesso: &7" + SDF.format(profile.getDataContainer("mCoreProfile", "lastlogin").getAsLong())));
    editor.setItem(10, InventoryItem.of(skull).defaultCallback(event -> onClick(event.getSlot())));
    setMenuItem(editor, 11, BukkitUtils.deserializeItemStack(
        "PAPER : 1 : nome>&aEstatísticas : desc>&7Visualize as suas estatísticas de\n&7cada Minigame do nosso servidor.\n \n&eClique para ver as estatísticas!"));
    setMenuItem(editor, 12, BukkitUtils.putProfileOnSkull(player, BukkitUtils.deserializeItemStack(
        "SKULL_ITEM:3 : 1 : nome>&aSkins : desc>&7Personalize a aparência do seu personagem.\n&7Escolha uma skin no catálogo.\n \n&eClique para escolher!")));
    setMenuItem(editor, 13, BukkitUtils.deserializeItemStack(
        "REDSTONE_COMPARATOR : 1 : nome>&aPreferências : desc>&7Em nosso servidor você pode personalizar\n&7sua experiência de jogo por completo.\n&7Personalize várias opções únicas como\n&7você desejar!\n \n&8As opções incluem ativar ou desativar as\n&8mensagens privadas, os jogadores e outros.\n \n&eClique para personalizar as opções!"));
    setMenuItem(editor, 14, BukkitUtils.deserializeItemStack(
        "MAP : 1 : esconder>tudo : nome>&aTítulos : desc>&7Esbanje estilo com um título exclusivo\n&7que ficará acima da sua cabeça para\n&7os outros jogadores.\n \n&8Lembrando que você não verá o título,\n&8apenas os outros jogadores.\n \n&eClique para selecionar um título!"));
    setMenuItem(editor, 15, BukkitUtils.deserializeItemStack(
        "POTION:8232 : 1 : esconder>tudo : nome>&aMultiplicadores de Coins : desc>&7Em nosso servidor existe um sistema\n&7de &6Multiplicadores de Coins &7que afetam\n&7a quantia de &6Coins &7ganhos nas partidas.\n \n&8Os Multiplicadores podem variar de\n&8pessoais ou gerais, podendo beneficiar\n&8você e até mesmo os outros jogadores.\n \n&eClique para ver seus multiplicadores!"));
    setMenuItem(editor, 16, BukkitUtils.deserializeItemStack(
        "GOLD_INGOT : 1 : nome>&aDesafios : desc>&7Em nosso servidor existe um sistema\n&7de &6Desafios &7que se consiste em missões\n&7de realização única que lhe garante\n&7vários prêmios vitalícios.\n \n&8Os Prêmios variam entre títulos, coins,\n&8ícones de prestígio e outros cosméticos.\n \n&eClique para ver os desafios!"));
  }

  @Override
  protected void onClick(int slot) {
    Profile current = Profile.getProfile(player.getName());
    if (current == null) { player.closeInventory(); return; }
    switch (slot) {
      case 10: EnumSound.ITEM_PICKUP.play(player, 0.5F, 2.0F); break;
      case 11: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuStatistics(current); break;
      case 12: EnumSound.CLICK.play(player, 0.5F, 2.0F); new tk.slicecollections.maxteer.skins.MenuSkinCatalog(player, 0); break;
      case 13: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuPreferences(current); break;
      case 14: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuTitles(current); break;
      case 15: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuBoosters(current); break;
      case 16: EnumSound.CLICK.play(player, 0.5F, 2.0F); new MenuAchievements(current); break;
      default: break;
    }
  }
}
