package tk.slicecollections.maxteer.database;

import tk.slicecollections.maxteer.Manager;
import tk.slicecollections.maxteer.booster.NetworkBooster;
import tk.slicecollections.maxteer.database.cache.RoleCache;
import tk.slicecollections.maxteer.database.data.DataContainer;
import tk.slicecollections.maxteer.database.exception.ProfileLoadException;

import javax.sql.rowset.CachedRowSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public abstract class Database {

  public void setupBoosters() {}

  public abstract void setBooster(String minigame, String booster, double multiplier, long expires);

  public abstract NetworkBooster getBooster(String minigame);

  public abstract String getRankAndName(String player);

  public abstract boolean getPreference(String player, String id, boolean def);

  public abstract List<String[]> getLeaderBoard(String table, String... columns);

  public abstract void close();

  public abstract Map<String, Map<String, DataContainer>> load(String name) throws ProfileLoadException;

  public abstract void save(String name, Map<String, Map<String, DataContainer>> tableMap);

  public abstract CompletableFuture<Void> saveAsync(String name, Map<String, Map<String, DataContainer>> tableMap);

  public abstract void saveSync(String name, Map<String, Map<String, DataContainer>> tableMap);

  public abstract String exists(String name);

  public abstract CachedRowSet query(String sql, Object... parameters);

  public abstract void update(String sql, Object... parameters);

  public abstract CompletableFuture<Void> execute(String sql, Object... parameters);

  private static Database instance;
  public static Logger LOGGER;
  private static Timer cacheTimer;

  /** Creates the only supported production persistence implementation: MySQL via HikariCP. */
  public static synchronized void setupDatabase(String mysqlHost, String mysqlPort, String mysqlDbname, String mysqlUsername, String mysqlPassword) {
    LOGGER = Manager.BUNGEE ? tk.slicecollections.maxteer.bungee.Bungee.getInstance().getLogger() : tk.slicecollections.maxteer.Core.getInstance().getLogger();
    instance = new HikariDatabase(mysqlHost, mysqlPort, mysqlDbname, mysqlUsername, mysqlPassword);
    if (cacheTimer != null) {
      cacheTimer.cancel();
    }
    cacheTimer = new Timer("mCore-role-cache", true);
    cacheTimer.scheduleAtFixedRate(RoleCache.clearCache(), TimeUnit.SECONDS.toMillis(60), TimeUnit.SECONDS.toMillis(60));
  }

  public static synchronized void stopBackgroundTasks() {
    if (cacheTimer != null) {
      cacheTimer.cancel();
      cacheTimer = null;
    }
  }

  public static Database getInstance() {
    return instance;
  }
}
