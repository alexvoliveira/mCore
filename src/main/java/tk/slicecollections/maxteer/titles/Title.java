package tk.slicecollections.maxteer.titles;

import lombok.Getter;

import javax.sql.rowset.CachedRowSet;
import java.sql.SQLException;
import java.util.logging.Level;
import org.bukkit.inventory.ItemStack;
import tk.slicecollections.maxteer.database.Database;
import tk.slicecollections.maxteer.player.Profile;
import tk.slicecollections.maxteer.plugin.config.MConfig;
import tk.slicecollections.maxteer.utils.BukkitUtils;
import tk.slicecollections.maxteer.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Maxter
 */
public class Title {

  @Getter
  private String id;
  private String icon;
  @Getter
  private String title;

  public Title(String id, String title, String desc) {
    this.id = id;
    this.icon = "%material%:%durability% : 1 : esconder>tudo : nome>%name% : desc>&fTítulo: " + title + "\n \n" + desc + "\n \n%action%";
    this.title = title;
  }

  public void give(Profile profile) {
    if (!this.has(profile)) {
      profile.getTitlesContainer().add(this);
    }
  }

  public boolean has(Profile profile) {
    return profile.getTitlesContainer().has(this);
  }

  public ItemStack getIcon(Profile profile) {
    boolean has = this.has(profile);
    Title selected = profile.getSelectedContainer().getTitle();

    return BukkitUtils.deserializeItemStack(
      this.icon.replace("%material%", has ? (selected != null && selected.equals(this)) ? "MAP" : "EMPTY_MAP" : "STAINED_GLASS_PANE").replace("%durability%", has ? "0" : "14")
        .replace("%name%", (has ? "&a" : "&c") + StringUtils.stripColors(this.title))
        .replace("%action%", (has ? (selected != null && selected.equals(this)) ? "&eClique para deselecionar!" : "&eClique para selecionar!" : "&cVocê não possui este título.")));
  }

  private static final List<Title> TITLES = new ArrayList<>();

  public static void setupTitles() {
    TITLES.clear();
    MConfig config = tk.slicecollections.maxteer.Core.getInstance().getConfig("titles");
    if (config.getSection("titles") != null) {
      for (String key : config.getSection("titles").getKeys(false)) {
        String name = config.getString("titles." + key + ".name");
        String description = config.getString("titles." + key + ".description");
        if (name == null || description == null || name.trim().isEmpty()) {
          Database.LOGGER.warning("Ignoring invalid title configuration: " + key);
          continue;
        }
        TITLES.add(new Title(key, StringUtils.formatColors(name), description));
      }
    }

    try (CachedRowSet titles = Database.getInstance().query("SELECT `id`, `name`, `description` FROM `mCoreTitles` ORDER BY `id`")) {
      if (titles != null) {
        titles.beforeFirst();
      }
      while (titles != null && titles.next()) {
        String id = titles.getString("id");
        if (getById(id) == null) {
          TITLES.add(new Title(id, titles.getString("name"), titles.getString("description")));
        }
      }
    } catch (SQLException ex) {
      Database.LOGGER.log(Level.WARNING, "Could not load custom titles from MySQL.", ex);
    }
  }

  public static Title getById(String id) {
    return TITLES.stream().filter(title -> title.getId().equals(id)).findFirst().orElse(null);
  }

  public static Collection<Title> listTitles() {
    return TITLES;
  }
}
