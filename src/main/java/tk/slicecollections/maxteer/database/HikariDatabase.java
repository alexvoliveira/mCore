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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
  private ThreadPoolExecutor executor;

  public HikariDatabase(String host, String port, String dbname, String username, String password) {
    this.host = host;
    this.port = port;
    this.dbname = dbname;
    this.username = username;
    this.password = password;
    this.openConnection();
    this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(1024), runnable -> {
        Thread thread = new Thread(runnable, "mCore-db-writer");
        thread.setDaemon(true);
        return thread;
      }, new ThreadPoolExecutor.AbortPolicy());

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
      if (!this.executor.awaitTermination(30, TimeUnit.SECONDS)) {
        List<Runnable> pending = this.executor.shutdownNow();
        for (Runnable runnable : pending) {
          if (runnable instanceof WriteTask) {
            ((WriteTask) runnable).fail(new IllegalStateException("Database shut down before the queued operation could run."));
          }
        }
        if (!this.executor.awaitTermination(2, TimeUnit.SECONDS)) {
          LOGGER.warning("Database writer did not stop within the shutdown deadline.");
        }
      }
    } catch (InterruptedException ex) {
      List<Runnable> pending = this.executor.shutdownNow();
      for (Runnable runnable : pending) {
        if (runnable instanceof WriteTask) {
          ((WriteTask) runnable).fail(ex);
        }
      }
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
    this.saveAsync(name, tableMap);
  }

  @Override
  public CompletableFuture<Void> saveAsync(String name, Map<String, Map<String, DataContainer>> tableMap) {
    if (name == null || tableMap == null) {
      return CompletableFuture.completedFuture(null);
    }
    List<TableSnapshot> snapshot = this.captureSnapshot(tableMap);
    if (snapshot.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return this.submitWrite("save profile " + name, () -> {
      this.persistSnapshot(name, snapshot);
      this.acknowledgeSnapshot(snapshot);
    });
  }

  @Override
  public void saveSync(String name, Map<String, Map<String, DataContainer>> tableMap) {
    if (name == null || tableMap == null) {
      return;
    }
    List<TableSnapshot> snapshot = this.captureSnapshot(tableMap);
    if (snapshot.isEmpty()) {
      return;
    }
    try {
      this.persistSnapshot(name, snapshot);
      this.acknowledgeSnapshot(snapshot);
    } catch (Exception ex) {
      LOGGER.log(Level.WARNING, "Could not synchronously save profile " + name + "; dirty values were retained for retry.", ex);
    }
  }

  private List<TableSnapshot> captureSnapshot(Map<String, Map<String, DataContainer>> tableMap) {
    List<TableSnapshot> snapshot = new ArrayList<>();
    for (DataTable table : DataTable.listTables()) {
      Map<String, DataContainer> rows = tableMap.get(table.getInfo().name());
      if (rows == null || rows.isEmpty()) {
        continue;
      }
      Map<String, PendingValue> changed = new LinkedHashMap<>();
      for (Map.Entry<String, DataContainer> entry : rows.entrySet()) {
        DataContainer container = entry.getValue();
        synchronized (container) {
          if (container.isUpdated()) {
            changed.put(entry.getKey(), new PendingValue(container, container.get()));
          }
        }
      }
      if (!changed.isEmpty()) {
        snapshot.add(new TableSnapshot(table.getInfo().name(), changed));
      }
    }
    return snapshot;
  }

  private void persistSnapshot(String name, List<TableSnapshot> snapshot) throws SQLException {
    try (Connection connection = getConnection()) {
      boolean originalAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        for (TableSnapshot table : snapshot) {
          StringBuilder sql = new StringBuilder("UPDATE ").append(quoteIdentifier(table.name)).append(" SET ");
          for (String column : table.values.keySet()) {
            sql.append(quoteIdentifier(column)).append(" = ?, ");
          }
          sql.setLength(sql.length() - 2);
          sql.append(" WHERE LOWER(`name`) = ?");
          try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (PendingValue field : table.values.values()) {
              statement.setObject(index++, field.value);
            }
            statement.setString(index, name.toLowerCase(Locale.ROOT));
            statement.executeUpdate();
          }
        }
        connection.commit();
      } catch (SQLException ex) {
        try {
          connection.rollback();
        } catch (SQLException rollbackError) {
          ex.addSuppressed(rollbackError);
        }
        throw ex;
      } finally {
        try {
          connection.setAutoCommit(originalAutoCommit);
        } catch (SQLException resetError) {
          LOGGER.log(Level.WARNING, "Could not reset auto-commit on a pooled connection.", resetError);
        }
      }
    }
  }

  private void acknowledgeSnapshot(List<TableSnapshot> snapshot) {
    for (TableSnapshot table : snapshot) {
      for (PendingValue field : table.values.values()) {
        synchronized (field.container) {
          if (Objects.equals(field.container.get(), field.value)) {
            field.container.setUpdated(false);
          }
        }
      }
    }
  }

  private static String quoteIdentifier(String identifier) {
    if (identifier == null || !identifier.matches("[A-Za-z0-9_]+")) {
      throw new IllegalArgumentException("Invalid internal SQL identifier: " + identifier);
    }
    return "`" + identifier + "`";
  }

  private static final class PendingValue {
    private final DataContainer container;
    private final Object value;

    private PendingValue(DataContainer container, Object value) {
      this.container = container;
      this.value = value;
    }
  }

  private static final class TableSnapshot {
    private final String name;
    private final Map<String, PendingValue> values;

    private TableSnapshot(String name, Map<String, PendingValue> values) {
      this.name = name;
      this.values = values;
    }
  }

  @FunctionalInterface
  private interface WriteOperation {
    void run() throws Exception;
  }

  private final class WriteTask implements Runnable {
    private final String description;
    private final WriteOperation operation;
    private final CompletableFuture<Void> future;

    private WriteTask(String description, WriteOperation operation, CompletableFuture<Void> future) {
      this.description = description;
      this.operation = operation;
      this.future = future;
    }

    @Override
    public void run() {
      try {
        operation.run();
        future.complete(null);
      } catch (Exception ex) {
        future.completeExceptionally(ex);
        LOGGER.log(Level.WARNING, "Failed to " + description + ".", ex);
      }
    }

    private void fail(Throwable cause) {
      if (future.completeExceptionally(cause)) {
        LOGGER.log(Level.WARNING, "Could not execute " + description + ".", cause);
      }
    }
  }

  private CompletableFuture<Void> submitWrite(String description, WriteOperation operation) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    WriteTask task = new WriteTask(description, operation, future);
    try {
      this.executor.execute(task);
    } catch (RejectedExecutionException ex) {
      task.fail(ex);
    }
    return future;
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
    try {
      updateOrThrow(sql, vars);
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Could not execute SQL update.", ex);
    }
  }

  private void updateOrThrow(String sql, Object... vars) throws SQLException {
    try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < vars.length; i++) {
        statement.setObject(i + 1, vars[i]);
      }
      statement.executeUpdate();
    }
  }

  @Override
  public CompletableFuture<Void> execute(String sql, Object... vars) {
    Object[] parameters = Arrays.copyOf(vars, vars.length);
    return submitWrite("SQL update", () -> updateOrThrow(sql, parameters));
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
