package tk.slicecollections.maxteer.plugin.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import tk.slicecollections.maxteer.utils.StringUtils;

/**
 * Reads human-editable ItemStack definitions from Bukkit 1.8.8 YAML sections.
 *
 * Full Bukkit-supported ItemStack metadata can also be persisted through
 * ConfigurationSection#set(path, item), since ItemStack implements
 * ConfigurationSerializable in Spigot 1.8.8.
 */
public final class ItemStackConfig {

  private ItemStackConfig() {
  }

  public static ItemStack deserialize(ConfigurationSection section) {
    if (section == null) {
      return null;
    }

    ItemStack serialized = section.getItemStack("serialized");
    if (serialized != null) {
      return serialized.clone();
    }

    String materialName = section.getString("material");
    if (materialName == null || materialName.trim().isEmpty()) {
      throw new IllegalArgumentException("Missing item material at " + section.getCurrentPath());
    }

    Material material = Material.getMaterial(materialName.trim().toUpperCase(Locale.ROOT));
    if (material == null) {
      try {
        material = Material.getMaterial(Integer.parseInt(materialName.trim()));
      } catch (NumberFormatException ignored) {
        // The material name was not a legacy numeric ID.
      }
    }
    if (material == null || material == Material.AIR) {
      throw new IllegalArgumentException("Unknown or unsupported item material '" + materialName
        + "' at " + section.getCurrentPath());
    }

    int amount = section.getInt("amount", 1);
    if (amount < 1 || amount > material.getMaxStackSize()) {
      throw new IllegalArgumentException("Invalid amount " + amount + " for " + material
        + " at " + section.getCurrentPath());
    }

    ItemStack item = new ItemStack(material, amount, (short) section.getInt("durability", 0));
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
      return item;
    }

    if (section.isString("display-name")) {
      meta.setDisplayName(StringUtils.formatColors(section.getString("display-name")));
    }

    List<String> configuredLore = section.getStringList("lore");
    if (!configuredLore.isEmpty()) {
      List<String> lore = new ArrayList<>(configuredLore.size());
      for (String line : configuredLore) {
        lore.add(StringUtils.formatColors(line));
      }
      meta.setLore(lore);
    }

    ConfigurationSection enchants = section.getConfigurationSection("enchants");
    if (enchants != null) {
      for (String key : enchants.getKeys(false)) {
        Enchantment enchantment = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
        if (enchantment == null) {
          throw new IllegalArgumentException("Unknown enchantment '" + key + "' at "
            + section.getCurrentPath() + ".enchants");
        }
        int level = enchants.getInt(key, 1);
        if (level < 1) {
          throw new IllegalArgumentException("Invalid enchantment level for '" + key + "'");
        }
        meta.addEnchant(enchantment, level, true);
      }
    }

    if (meta instanceof SkullMeta && section.isString("skull-owner")) {
      ((SkullMeta) meta).setOwner(section.getString("skull-owner"));
    }

    for (String flag : section.getStringList("flags")) {
      try {
        meta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException("Unknown item flag '" + flag + "' at "
          + section.getCurrentPath(), ex);
      }
    }

    item.setItemMeta(meta);
    return item;
  }

  /**
   * Persists all metadata supported by the running Bukkit implementation.
   * The resulting YAML uses Bukkit's native ConfigurationSerializable format.
   */
  public static void serialize(ConfigurationSection section, String path, ItemStack item) {
    if (section == null || path == null || path.trim().isEmpty()) {
      throw new IllegalArgumentException("A configuration section and non-empty path are required");
    }
    section.set(path, item == null ? null : item.clone());
  }
}
