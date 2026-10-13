package tk.slicecollections.maxteer.menus.profile.boosters;

import tk.slicecollections.maxteer.utils.MessageUtils;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.inventory.impl.paged.PagedInventory;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import com.henryfabio.minecraft.inventoryapi.item.supplier.InventoryItemSupplier;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.border.Border;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.impl.ViewerConfigurationImpl;
import com.henryfabio.minecraft.inventoryapi.viewer.impl.paged.PagedViewer;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.booster.Booster;
import tk.slicecollections.maxteer.booster.Booster.BoosterType;
import tk.slicecollections.maxteer.menus.profile.MenuBoosters;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.StringUtils;
import tk.slicecollections.maxteer.utils.TimeUtils;
import tk.slicecollections.maxteer.utils.enums.EnumSound;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MenuBoostersList extends PagedInventory {
  private final Profile profile;
  private final BoosterType type;

  public MenuBoostersList(Profile profile, String name, BoosterType type) {
    super("mcore.booster-list." + profile.getPlayer().getUniqueId() + "." + type.name(),
        "Multiplicadores " + name, 45);
    this.profile = profile;
    this.type = type;
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
          new MenuBoosters(current);
        }));
  }

  @Override
  protected List<InventoryItemSupplier> createPageItems(PagedViewer viewer) {
    List<InventoryItemSupplier> items = new ArrayList<>();
    List<Booster> boosters = profile.getBoostersContainer().getBoosters(type);
    for (Booster booster : boosters) {
      items.add(() -> InventoryItem.of(BukkitUtils.deserializeItemStack(
          "POTION" + (type == BoosterType.NETWORK ? ":8232" : "")
              + " : 1 : esconder>tudo : nome>&aMultiplicador " + (type == BoosterType.NETWORK ? "Geral" : "Pessoal")
              + " : desc>&fMultiplicador: &6" + booster.getMultiplier() + "x\n&fDuração: &7"
              + TimeUtils.getTime(TimeUnit.HOURS.toMillis(booster.getHours()))
              + "\n \n&eClique para ativar o multiplicador!"))
          .defaultCallback(event -> activate(booster)));
    }
    if (items.isEmpty()) {
      items.add(() -> InventoryItem.of(BukkitUtils.deserializeItemStack(
          "GLASS_BOTTLE : 1 : nome>&7Nenhum multiplicador disponível.")));
    }
    return items;
  }

  private void activate(Booster booster) {
    Profile current = Profile.getProfile(profile.getPlayer().getName());
    if (current == null) { profile.getPlayer().closeInventory(); return; }

    if (type == BoosterType.NETWORK) {
      if (!Core.minigames.contains(Core.minigame)) {
        current.getPlayer().sendMessage(MessageUtils.error("MINIGAME NECESSÁRIO", "Você precisa estar em um servidor de minigame para ativar este multiplicador."));
        return;
      }
      if (!Booster.setNetworkBooster(Core.minigame, current, booster)) {
        EnumSound.ENDERMAN_TELEPORT.play(current.getPlayer(), 0.5F, 1.0F);
        current.getPlayer().sendMessage(MessageUtils.error("MULTIPLICADOR ATIVO", "Já existe um multiplicador geral para " + Core.minigame + "."));
        current.getPlayer().closeInventory();
        return;
      }
      EnumSound.LEVEL_UP.play(current.getPlayer(), 0.5F, 1.0F);
      Profile.listProfiles().forEach(other -> other.getPlayer().sendMessage(MessageUtils.success("MULTIPLICADOR GERAL ATIVADO",
          StringUtils.getLastColor(Role.getPlayerRole(current.getPlayer()).getPrefix()) + current.getPlayer().getName()
          + " ativou um multiplicador de Coins para todos.\n§7Bônus: §a§l" + booster.getMultiplier() + "x §7nas partidas de §a" + Core.minigame + "§7.")));
      return;
    }

    if (!current.getBoostersContainer().enable(booster)) {
      EnumSound.ENDERMAN_TELEPORT.play(current.getPlayer(), 0.5F, 1.0F);
      current.getPlayer().sendMessage(MessageUtils.error("MULTIPLICADOR ATIVO", "Você já possui um multiplicador pessoal ativo."));
      current.getPlayer().closeInventory();
      return;
    }
    current.getPlayer().sendMessage(MessageUtils.success("MULTIPLICADOR ATIVADO", "Multiplicador de Coins §a§l" + booster.getMultiplier()
        + "x §7(" + TimeUtils.getTime(TimeUnit.HOURS.toMillis(booster.getHours())) + ") ativado."));
    new MenuBoosters(current);
  }
}
