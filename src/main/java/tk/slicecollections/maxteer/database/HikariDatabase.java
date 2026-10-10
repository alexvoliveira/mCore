package tk.slicecollections.maxteer.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import tk.slicecollections.maxteer.Manager;
import tk.slicecollections.maxteer.booster.NetworkBooster;
import tk.slicecollections.maxteer.database.cache.RoleCache;
import tk.slicecollections.maxteer.database.data.DataContainer;
import tk.slicecollections.maxteer.database.data.DataTable;
import tk.slicecollections.maxteer.database.exception.ProfileLoadException;
import tk.slicecollections.maxteer.player.role.Role;
import tk.slicecollections.maxteer.utils.StringUtils;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author Maxter
 */
public class HikariDatabase extends Database {

  private String host;
  private String port;
  private String dbname;
  private String username;
  private String password;
  private HikariDataSource dataSource;
  private ExecutorService executor;

  public HikariDatabase(String host, String port, String dbname, String username, String password) {
    this.host = host;
    this.port = port;
    this.dbname = dbname;
    this.username = username;
    this.password = password;
    this.openConnection();
    this.executor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "mCore-db-writer");
      thread.setDaemon(true);
      return thread;
    });

    try {
      DataTable.listTables().forEach(table -> this.update(table.getInfo().create()));
      SchemaMigrator.migrate(this);
    } catch (RuntimeException ex) {
      this.executor.shutdownNow();
      this.closeConnection();
      throw ex;
    }
  }

  @Override
  public void setupBoosters() {
    if (!Manager.BUNGEE) {
      for (String mg : tk.slicecollections.maxteer.Core.minigames) {
        if (query("SELECT * FROM `mCoreNetworkBooster` WHERE `id` = ?", mg) == null) {
          execute("INSERT INTO `mCoreNetworkBooster` VALUES (?, ?, ?, ?)", mg, "Maxteer", 1.0, 0L);
        }
      }
    }
  }

  @Override
  public void setBooster(String minigame, String booster, double multiplier, long expires) {
    execute("UPDATE `mCoreNetworkBooster` SET `booster` = ?, `multiplier` = ?, `expires` = ? WHERE `id` = ?", booster, multiplier, expires, minigame);
  }

  @Override
  public NetworkBooster getBooster(String minigame) {
    try (CachedRowSet rs = query("SELECT * FROM `mCoreNetworkBooster` WHERE `id` = ?", minigame)) {
      if (rs != null) {
        String booster = rs.getString("booster");
        double multiplier = rs.getDouble("multiplier");
        long expires = rs.getLong("expires");
        if (expires > System.currentTimeMillis()) {
          rs.close();
          return new NetworkBooster(booster, multiplier, expires);
        }
      }
    } catch (SQLException ignored) {}

    return null;
  }

  @Override
  public String getRankAndName(String player) {
    try (CachedRowSet rs = query("SELECT `name`, `role` FROM `mCoreProfile` WHERE LOWER(`name`) = ?", player.toLowerCase(Locale.ROOT))) {
      if (rs != null) {
        String result = rs.getString("role") + " : " + rs.getString("name");
        RoleCache.setCache(player, rs.getString("role"), rs.getString("name"));
        return result;
      }
    } catch (SQLException ignored) {}
    return null;
  }

  @Override
  public boolean getPreference(String player, String id, boolean def) {
    try (CachedRowSet rs = query("SELECT `preferences` FROM `mCoreProfile` WHERE LOWER(`name`) = ?", player.toLowerCase(Locale.ROOT))) {
      if (rs != null) {
        JSONObject preferences = (JSONObject) new JSONParser().parse(rs.getString("preferences"));
        Object value = preferences.get(id);
        if (value instanceof Number) {
          return ((Number) value).longValue() == 0L;
        }
      }
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "Could not read preference '" + id + "' for player '" + player + "'.", ex);
    }
    return def;
  }

  @Override
  public List<String[]> getLeaderBoard(String table, String... columns) {
    List<String[]> result = new ArrayList<>();
    if (columns == null || columns.length == 0) {
      return result;
    }

    final String safeTable;
    final List<String> safeColumns;
    try {
      safeTable = SqlIdentifier.table(table);
      safeColumns = SqlIdentifier.columns(safeTable, columns);
    } catch (IllegalArgumentException ex) {
      LOGGER.log(Level.WARNING, "Rejected invalid leaderboard table/column identifier.", ex);
      return result;
    }

    StringBuilder add = new StringBuilder(), select = new StringBuilder();
    for (String column : safeColumns) {
      add.append('`').append(column).append("` + ");
      select.append('`').append(column).append("`, ");
    }

    try (CachedRowSet rs = query("SELECT " + select + "`name` FROM `" + safeTable + "` ORDER BY " + add + " 0 DESC LIMIT 10")) {
      if (rs != null) {
        rs.beforeFirst();
        while (rs.next()) {
          long count = 0;
          for (String column : safeColumns) {
            count += rs.getLong(column);
          }
          result.add(new String[] {Role.getColored(rs.getString("name"), true), StringUtils.formatNumber(count)});
        }
      }
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Could not load leaderboard for table '" + safeTable + "'.", ex);
    }
    return result;
  }

  @Override
  public void close() {
    this.executor.shutdown();
    try {
      if (!this.executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
        java.util.List<Runnable> pending = this.executor.shutdownNow();
        if (!pending.isEmpty()) {
          LOGGER.warning("Discarded " + pending.size() + " queued database operation(s) during shutdown.");
        }
        if (!this.executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
          LOGGER.warning("Database writer did not stop within the shutdown deadline.");
        }
      }
    } catch (InterruptedException ex) {
      this.executor.shutdownNow();
      Thread.currentThread().interrupt();
      LOGGER.log(Level.WARNING, "Interrupted while stopping the database writer.", ex);
    } finally {
      this.closeConnection();
      Database.stopBackgroundTasks();
    }
  }

  @Override
  public Map<String, Map<String, DataContainer>> load(String name) throws ProfileLoadException  {
    Map<String, Map<String, DataContainer>> tableMap = new HashMap<>();
    for (DataTable table : DataTable.listTables()) {
      Map<String, DataContainer> containerMap = new LinkedHashMap<>();
      tableMap.put(table.getInfo().name(), containerMap);

      try (CachedRowSet rs = this.query(table.getInfo().select(), name.toLowerCase(Locale.ROOT))) {
        if (rs != null) {
          for (int collumn = 2; collumn <= rs.getMetaData().getColumnCount(); collumn++) {
            containerMap.put(rs.getMetaData().getColumnName(collumn), new DataContainer(rs.getObject(collumn)));
          }
          continue;
        }
      } catch (SQLException ex) {
        throw new ProfileLoadException(ex.getMessage());
      }

      containerMap = table.getDefaultValues();
      tableMap.put(table.getInfo().name(), containerMap);
      List<Object> list = new ArrayList<>();
      list.add(name);
      list.addAll(containerMap.values().stream().map(DataContainer::get).collect(Collectors.toList()));
      this.execute(table.getInfo().insert(), list.toArray());
      list.clear();
    }

    return tableMap;
  }

  @Override
  public void save(String name, Map<String, Map<String, DataContainer>> tableMap) {
    this.save0(name, tableMap, true);
  }

  @Override
  public void saveSync(String name, Map<String, Map<String, DataContainer>> tableMap) {
    this.save0(name, tableMap, false);
  }

  private void save0(String name, Map<String, Map<String, DataContainer>> tableMap, boolean async) {
    for (DataTable table : DataTable.listTables()) {
      Map<String, DataContainer> rows = tableMap.get(table.getInfo().name());
      if (rows.values().stream().noneMatch(DataContainer::isUpdated)) {
        continue;
      }

      List<Object> values = rows.values().stream().filter(DataContainer::isUpdated).map(DataContainer::get).collect(Collectors.toList());
      StringBuilder query = new StringBuilder("UPDATE `" + table.getInfo().name() + "` SET ");
      for (Map.Entry<String, DataContainer> collumn : rows.entrySet()) {
        if (collumn.getValue().isUpdated()) {
          collumn.getValue().setUpdated(false);
          query.append("`").append(collumn.getKey()).append("` = ?, ");
        }
      }
      query.deleteCharAt(query.length() - 1);
      query.deleteCharAt(query.length() - 1);
      query.append(" WHERE LOWER(`name`) = ?");
      values.add(name.toLowerCase(Locale.ROOT));
      if (async) {
        this.execute(query.toString(), values.toArray());
      } else {
        this.update(query.toString(), values.toArray());
      }
      values.clear();
    }
  }

  @Override
  public String exists(String name) {
    try {
      return this.query("SELECT `name` FROM `mCoreProfile` WHERE LOWER(`name`) = ?", name.toLowerCase(Locale.ROOT)).getString("name");
    } catch (Exception ex) {
      return null;
    }
  }

  public void openConnection() {
    HikariConfig config = new HikariConfig();
    config.setPoolName("mConnectionPool");
    // Keep HikariCP defaults unless workload measurements justify pool tuning.
    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
    String jdbcUrl = "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.dbname +
      "?sslMode=VERIFY_IDENTITY&useUnicode=yes&characterEncoding=UTF-8";
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(this.username);
    config.setPassword(this.password);
    config.addDataSourceProperty("autoReconnect", "true");
    this.dataSource = new HikariDataSource(config);

    LOGGER.info("Conectado ao MySQL!");
  }

  public void closeConnection() {
    if (isConnected()) {
      this.dataSource.close();
    }
  }

  public Connection getConnection() throws SQLException {
    return this.dataSource.getConnection();
  }

  public boolean isConnected() {
    return !this.dataSource.isClosed();
  }

  @Override
  public void update(String sql, Object... vars) {
    Connection connection = null;
    PreparedStatement ps = null;
    try {
      connection = getConnection();
      ps = connection.prepareStatement(sql);
      for (int i = 0; i < vars.length; i++) {
        ps.setObject(i + 1, vars[i]);
      }
      ps.executeUpdate();
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Nao foi possivel executar um SQL: ", ex);
    } finally {
      try {
        if (connection != null && !connection.isClosed())
          connection.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        if (ps != null && !ps.isClosed())
          ps.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public void execute(String sql, Object... vars) {
    executor.execute(() -> {
      update(sql, vars);
    });
  }

  public int updateWithInsertId(String sql, Object... vars) {
    int id = -1;
    Connection connection = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      connection = getConnection();
      ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
      for (int i = 0; i < vars.length; i++) {
        ps.setObject(i + 1, vars[i]);
      }
      ps.execute();
      rs = ps.getGeneratedKeys();
      if (rs.next()) {
        id = rs.getInt(1);
      }
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Nao foi possivel executar um SQL: ", ex);
    } finally {
      try {
        if (connection != null && !connection.isClosed())
          connection.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        if (ps != null && !ps.isClosed())
          ps.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        if (rs != null && !rs.isClosed())
          rs.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }

    return id;
  }

  @Override
  public CachedRowSet query(String query, Object... vars) {
    Connection connection = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    CachedRowSet rowSet = null;
    try {
      connection = getConnection();
      ps = connection.prepareStatement(query);
      for (int i = 0; i < vars.length; i++) {
        ps.setObject(i + 1, vars[i]);
      }
      rs = ps.executeQuery();
      rowSet = RowSetProvider.newFactory().createCachedRowSet();
      rowSet.populate(rs);

      if (rowSet.next()) {
        return rowSet;
      }
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Nao foi possivel executar um Requisicao: ", ex);
    } finally {
      try {
        if (connection != null && !connection.isClosed())
          connection.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        if (ps != null && !ps.isClosed())
          ps.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
      try {
        if (rs != null && !rs.isClosed())
          rs.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }

    return null;
  }
}
