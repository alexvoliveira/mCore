package tk.slicecollections.maxteer.skins;

import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tk.slicecollections.maxteer.Core;
import tk.slicecollections.maxteer.menus.PlayerMenuInventory;
import tk.slicecollections.maxteer.utils.MessageUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MenuSkinHistory extends PlayerMenuInventory {
  private final List<SkinHistoryEntry> entries;
  private final Map<Integer, SkinHistoryEntry> slots = new HashMap<>();
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm");

  public static void open(Player player) {
    Core core = Core.getInstance();
    SkinHistoryManager history = core.getSkinHistoryManager();
    if (history == null) {
      player.sendMessage(MessageUtils.error("HISTÓRICO", "O sistema de skins ainda não está pronto."));
      return;
    }

    player.sendMessage(MessageUtils.info("Carregando histórico de skins..."));
    history.loadHistory(player.getUniqueId()).whenComplete((entries, failure) -> {
      if (!core.isEnabled()) return;
      try {
        Bukkit.getScheduler().runTask(core, () -> {
          if (!player.isOnline()) return;
          if (failure != null) {
            core.getLogger().warning("Could not load skin history for " + player.getName() + ": " + failure.getMessage());
            player.sendMessage(MessageUtils.error("HISTÓRICO", "Não foi possível carregar o histórico."));
            return;
          }
          new MenuSkinHistory(player, entries);
        });
      } catch (IllegalStateException ignored) {
        // The plugin was disabled before the read completed.
      }
    });
  }

  public MenuSkinHistory(Player player, List<SkinHistoryEntry> entries) {
    super(player, "mcore.skins.history", "Histórico de skins", 5);
    this.entries = entries == null ? Collections.emptyList() : new ArrayList<>(entries);
    openForPlayer();
  }

  @Override
  protected void configureInventory(Viewer viewer, InventoryEditor editor) {
    slots.clear();
    int[] positions = {10, 11, 12, 13, 14, 15, 16};
    int count = Math.min(positions.length, entries.size());
    for (int index = 0; index < count; index++) {
      SkinHistoryEntry entry = entries.get(index);
      int slot = positions[index];
      slots.put(slot, entry);
      setMenuItem(editor, slot, makeHead(entry));
    }

    if (entries.isEmpty()) {
      setMenuItem(editor, 22, button(Material.BARRIER, "§cHistórico vazio",
          Arrays.asList("§7As skins escolhidas aparecerão aqui.", "§7O histórico expira após 3 dias.")));
    }
    setMenuItem(editor, 40, button(Material.ARROW, "§aVoltar às skins", Arrays.asList("§7Retornar ao catálogo.")));
    setMenuItem(editor, 44, button(Material.BARRIER, "§cFechar", Arrays.asList("§7Fechar o menu.")));
  }

  @Override
  protected void onClick(int slot) {
    SkinHistoryEntry entry = slots.get(slot);
    if (entry != null) {
      Core.getInstance().getSkinLibrary().applyHistorySkin(player, entry);
      return;
    }
    if (slot == 40) new MenuSkinCatalog(player, 0);
    else if (slot == 44) player.closeInventory();
  }

  private ItemStack makeHead(SkinHistoryEntry entry) {
    ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
    ItemMeta raw = item.getItemMeta();
    if (raw instanceof SkullMeta && entry.getOwnerName() != null && !entry.getOwnerName().isEmpty()) {
      ((SkullMeta) raw).setOwner(entry.getOwnerName());
    }
    raw.setDisplayName("§a" + entry.getDisplayName());
    raw.setLore(Arrays.asList(
        "§7Skin: §f" + entry.getOwnerName(),
        "§7Utilizada: §f" + dateFormat.format(new Date(entry.getUsedAt())),
        "",
        "§eClique para reutilizar."));
    item.setItemMeta(raw);
    return item;
  }

  private ItemStack button(Material material, String name, List<String> lore) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.setDisplayName(name);
    meta.setLore(new ArrayList<>(lore));
    item.setItemMeta(meta);
    return item;
  }
}
