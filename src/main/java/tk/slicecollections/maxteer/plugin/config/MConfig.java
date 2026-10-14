package tk.slicecollections.maxteer.plugin.config;

import lombok.Getter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import tk.slicecollections.maxteer.plugin.MPlugin;

/**
 * @author Maxteer
 */
public class MConfig {

  private MPlugin plugin;
  @Getter
  private File file;
  @Getter
  private YamlConfiguration rawConfig;

  private MConfig(MPlugin plugin, String path, String name) {
    this.plugin = plugin;
    this.file = new File(path + "/" + name + ".yml");
    if (!file.exists()) {
      this.file.getParentFile().mkdirs();
      InputStream in = plugin.getResource(name + ".yml");
      if (in != null) {
        plugin.getFileUtils().copyFile(in, file);
      } else {
        try {
          this.file.createNewFile();
        } catch (IOException ex) {
          plugin.getLogger().log(Level.SEVERE, "Um erro inesperado ocorreu ao criar o arquivo \"" + file.getName() + "\": ", ex);
        }
      }
    }

    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
      this.rawConfig = YamlConfiguration.loadConfiguration(reader);
    } catch (IOException ex) {
      plugin.getLogger().log(Level.SEVERE, "Um erro inesperado ocorreu ao criar a config \"" + file.getName() + "\": ", ex);
    }
  }

  public boolean createSection(String path) {
    this.rawConfig.createSection(path);
    return save();
  }

  public boolean set(String path, Object obj) {
    this.rawConfig.set(path, obj);
    return save();
  }

  public boolean contains(String path) {
    return this.rawConfig.contains(path);
  }

  public Object get(String path) {
    return this.rawConfig.get(path);
  }

  public int getInt(String path) {
    return this.rawConfig.getInt(path);
  }

  public int getInt(String path, int def) {
    return this.rawConfig.getInt(path, def);
  }

  public double getDouble(String path) {
    return this.rawConfig.getDouble(path);
  }

  public double getDouble(String path, double def) {
    return this.rawConfig.getDouble(path, def);
  }

  public String getString(String path) {
    return this.rawConfig.getString(path);
  }

  public boolean getBoolean(String path) {
    return this.rawConfig.getBoolean(path);
  }

  public boolean getBoolean(String path, boolean def) {
    return this.rawConfig.getBoolean(path, def);
  }

  public List<String> getStringList(String path) {
    return this.rawConfig.getStringList(path);
  }

  public List<Integer> getIntegerList(String path) {
    return this.rawConfig.getIntegerList(path);
  }

  public Set<String> getKeys(boolean flag) {
    return this.rawConfig.getKeys(flag);
  }

  public ConfigurationSection getSection(String path) {
    return this.rawConfig.getConfigurationSection(path);
  }

  public void reload() {
    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
      this.rawConfig = YamlConfiguration.loadConfiguration(reader);
    } catch (IOException ex) {
      plugin.getLogger().log(Level.SEVERE, "Um erro inesperado ocorreu ao recarregar a config \"" + file.getName() + "\": ", ex);
    }
  }

  public boolean save() {
    try {
      this.rawConfig.save(this.file);
      return true;
    } catch (IOException ex) {
      plugin.getLogger().log(Level.SEVERE, "Um erro inesperado ocorreu ao salvar a config \"" + file.getName() + "\": ", ex);
      return false;
    }
  }

  private static final Map<String, MConfig> CACHE = new HashMap<>();

  public static MConfig getConfig(MPlugin plugin, String path, String name) {
    String key = path + "/" + name;
    return CACHE.computeIfAbsent(key, ignored -> new MConfig(plugin, path, name));
  }
}
