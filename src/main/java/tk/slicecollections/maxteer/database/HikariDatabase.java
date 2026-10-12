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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
  private final Object saveSubmissionLock = new Object();
  private final AtomicLong saveSequence = new AtomicLong();
  private final ConcurrentMap<String, PendingProfileSave> failedProfileSaves = new ConcurrentHashMap<>();
  private final AtomicBoolean retryScheduled = new AtomicBoolean();

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
      for (DataTable table : DataTable.listTables()) {
        this.updateOrThrow(table.getInfo().create());
      }
      SchemaMigrator.migrate(this);
    } catch (SQLException ex) {
      this.executor.shutdownNow();
      this.closeConnection();
      throw new IllegalStateException("Could not create the mCore base schema.", ex);
    } catch (RuntimeException ex) {
      this.executor.shutdownNow();
      this.closeConnection();
      throw ex;
    }
  }

  @Override
  public void setupBoosters() {
    if (Manager.BUNGEE) {
      return;
    }

    try {
      for (String minigame : tk.slicecollections.maxteer.Core.minigames) {
        this.updateOrThrow(
          "INSERT IGNORE INTO `mCoreNetworkBooster` (`id`, `booster`, `multiplier`, `expires`) VALUES (?, ?, ?, ?)",
          minigame, "Maxteer", 1.0, 0L
        );
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Could not initialize network booster records.", ex);
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
    String cached = RoleCache.get(player);
    if (cached != null) {
      return cached;
    }

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
      if (rs == null) {
        return def;
      }

      final Object parsed;
      try {
        parsed = new JSONParser().parse(rs.getString("preferences"));
      } catch (org.json.simple.parser.ParseException ex) {
        throw new IllegalStateException("Stored preferences JSON is invalid for player '" + player + "'.", ex);
      }
      if (!(parsed instanceof JSONObject)) {
        throw new IllegalStateException("Stored preferences value is not a JSON object for player '" + player + "'.");
      }

      Object value = ((JSONObject) parsed).get(id);
      if (value instanceof Number) {
        return ((Number) value).longValue() == 0L;
      }
      return def;
    } catch (SQLException ex) {
      throw new IllegalStateException("Could not read preferences for player '" + player + "'.", ex);
    }
  }

  @Override
  public CompletableFuture<Boolean> getPreferenceAsync(String player, String id, boolean def) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    try {
      this.executor.execute(() -> {
        try {
          future.complete(this.getPreference(player, id, def));
        } catch (Exception ex) {
          future.completeExceptionally(ex);
        }
      });
    } catch (RejectedExecutionException ex) {
      future.completeExceptionally(ex);
    }
    return future;
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
    boolean terminated = false;
    try {
      terminated = this.executor.awaitTermination(30, TimeUnit.SECONDS);
      if (!terminated) {
        List<Runnable> pending = this.executor.shutdownNow();
        for (Runnable runnable : pending) {
          if (runnable instanceof WriteTask) {
            ((WriteTask) runnable).fail(new IllegalStateException("Database shut down before the queued operation could run."));
          }
        }
        terminated = this.executor.awaitTermination(2, TimeUnit.SECONDS);
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
      if (terminated || this.executor.isTerminated()) {
        this.retryFailedProfileSavesOnShutdown();
      } else if (!this.failedProfileSaves.isEmpty()) {
        LOGGER.severe("Database writer did not terminate; retained profile saves could not be retried safely.");
      }
      if (!this.failedProfileSaves.isEmpty()) {
        LOGGER.severe("Some profile snapshots remain unpersisted at shutdown. Inspect database availability before restarting.");
      }
      this.closeConnection();
      Database.stopBackgroundTasks();
    }
  }

  @Override
  public Map<String, Map<String, DataContainer>> load(String name) throws ProfileLoadException  {
    Map<String, Map<String, DataContainer>> tableMap = new HashMap<>();
    for (DataTable table : DataTable.listTables()) {
      String tableName = table.getInfo().name();
      Map<String, DataContainer> rows = new LinkedHashMap<>();
      tableMap.put(tableName, rows);

      try (CachedRowSet result = this.query(table.getInfo().select(), name.toLowerCase(Locale.ROOT))) {
        if (result != null) {
          this.populateTableRow(rows, result);
          continue;
        }
      } catch (SQLException ex) {
        throw new ProfileLoadException("Could not read table " + tableName + " for profile " + name + ".", ex);
      } catch (RuntimeException ex) {
        throw new ProfileLoadException("Database query failed while loading table " + tableName + " for profile " + name + ".", ex);
      }

      rows = table.getDefaultValues();
      tableMap.put(tableName, rows);
      List<Object> values = new ArrayList<>();
      values.add(name);
      values.addAll(rows.values().stream().map(DataContainer::get).collect(Collectors.toList()));

      try {
        this.updateOrThrow(table.getInfo().insert(), values.toArray());
      } catch (SQLException insertError) {
        // Two server instances may race to create the same profile; accept that only if the row exists.
        try (CachedRowSet existing = this.query(table.getInfo().select(), name.toLowerCase(Locale.ROOT))) {
          if (existing == null) {
            throw insertError;
          }
          this.populateTableRow(rows, existing);
          continue;
        } catch (SQLException | RuntimeException rereadError) {
          insertError.addSuppressed(rereadError);
          throw new ProfileLoadException("Could not create or re-read table " + tableName + " for profile " + name + ".", insertError);
        }
      }

      try (CachedRowSet inserted = this.query(table.getInfo().select(), name.toLowerCase(Locale.ROOT))) {
        if (inserted == null) {
          throw new SQLException("Inserted profile row could not be read back.");
        }
        this.populateTableRow(rows, inserted);
      } catch (SQLException ex) {
        throw new ProfileLoadException("Could not verify inserted row in table " + tableName + " for profile " + name + ".", ex);
      } catch (RuntimeException ex) {
        throw new ProfileLoadException("Database verification failed for table " + tableName + " and profile " + name + ".", ex);
      }
    }

    return tableMap;
  }

  private void populateTableRow(Map<String, DataContainer> rows, CachedRowSet result) throws SQLException {
    rows.clear();
    for (int column = 2; column <= result.getMetaData().getColumnCount(); column++) {
      rows.put(result.getMetaData().getColumnName(column), new DataContainer(result.getObject(column)));
    }
  }

  public void save(String name, Map<String, Map<String, DataContainer>> tableMap) {
    this.saveAsync(name, tableMap);
  }

  @Override
  public CompletableFuture<Void> saveAsync(String name, Map<String, Map<String, DataContainer>> tableMap) {
    if (name == null || tableMap == null) {
      return CompletableFuture.completedFuture(null);
    }

    synchronized (this.saveSubmissionLock) {
      List<TableSnapshot> snapshot = this.captureSnapshot(tableMap);
      if (snapshot.isEmpty()) {
        return CompletableFuture.completedFuture(null);
      }
      PendingProfileSave pending = new PendingProfileSave(name, name.toLowerCase(Locale.ROOT),
        this.saveSequence.incrementAndGet(), snapshot);
      CompletableFuture<Void> future = this.submitWrite("save profile " + name, () -> this.persistProfileSave(pending));
      future.whenComplete((ignored, failure) -> {
        if (failure != null) {
          this.retainFailedSave(pending);
        }
      });
      return future;
    }
  }

  @Override
  public void saveSync(String name, Map<String, Map<String, DataContainer>> tableMap) {
    this.saveSyncChecked(name, tableMap);
  }

  @Override
  public boolean saveSyncChecked(String name, Map<String, Map<String, DataContainer>> tableMap) {
    if (name == null || tableMap == null) {
      return true;
    }

    synchronized (this.saveSubmissionLock) {
      List<TableSnapshot> snapshot = this.captureSnapshot(tableMap);
      if (snapshot.isEmpty()) {
        return true;
      }

      PendingProfileSave pending = new PendingProfileSave(name, name.toLowerCase(Locale.ROOT),
        this.saveSequence.incrementAndGet(), snapshot);
      CompletableFuture<Void> future = this.submitWrite("synchronous save profile " + name,
        () -> this.persistProfileSave(pending));
      future.whenComplete((ignored, failure) -> {
        if (failure != null) {
          this.retainFailedSave(pending);
        }
      });
      try {
        future.get();
        return true;
      } catch (InterruptedException ex) {
        this.retainFailedSave(pending);
        Thread.currentThread().interrupt();
        LOGGER.log(Level.WARNING, "Interrupted while synchronously saving profile '" + name + "'.", ex);
        return false;
      } catch (java.util.concurrent.ExecutionException ex) {
        this.retainFailedSave(pending);
        LOGGER.log(Level.WARNING, "Could not synchronously save profile '" + name + "'.", ex.getCause());
        return false;
      }
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
            int affected = statement.executeUpdate();
            if (affected != 1) {
              throw new SQLException("Profile update expected one row in " + table.name + " but affected " + affected + " row(s).");
            }
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
    if (snapshot.stream().anyMatch(table -> "mCoreProfile".equals(table.name))) {
      RoleCache.invalidate(name);
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

  private void persistProfileSave(PendingProfileSave pending) throws SQLException {
    try {
      this.persistSnapshot(pending.name, pending.snapshot);
      this.acknowledgeSnapshot(pending.snapshot);
      this.failedProfileSaves.computeIfPresent(pending.key,
        (key, failed) -> failed.sequence <= pending.sequence ? null : failed);
    } catch (SQLException ex) {
      this.retainFailedSave(pending);
      throw ex;
    }
  }

  private void retainFailedSave(PendingProfileSave pending) {
    this.failedProfileSaves.compute(pending.key,
      (key, existing) -> existing == null || existing.sequence <= pending.sequence ? pending : existing);
  }

  /** Called by the role-cache timer; retry work is serialized behind profile writes. */
  public void retryPendingSavesAsync() {
    if (this.failedProfileSaves.isEmpty() || !this.retryScheduled.compareAndSet(false, true)) {
      return;
    }

    CompletableFuture<Void> retry = this.submitWrite("retry retained profile saves", () -> {
      for (PendingProfileSave pending : new ArrayList<>(this.failedProfileSaves.values())) {
        if (this.failedProfileSaves.get(pending.key) != pending) {
          continue;
        }
        try {
          this.persistSnapshot(pending.name, pending.snapshot);
          this.acknowledgeSnapshot(pending.snapshot);
          this.failedProfileSaves.remove(pending.key, pending);
        } catch (SQLException ex) {
          LOGGER.log(Level.FINE, "Profile save remains pending for " + pending.name + ".", ex);
        }
      }
    });
    retry.whenComplete((ignored, failure) -> {
      this.retryScheduled.set(false);
      if (failure != null) {
        LOGGER.log(Level.WARNING, "Could not schedule retry of retained profile saves.", failure);
      }
    });
  }

  private void retryFailedProfileSavesOnShutdown() {
    for (PendingProfileSave pending : new ArrayList<>(this.failedProfileSaves.values())) {
      try {
        this.persistSnapshot(pending.name, pending.snapshot);
        this.acknowledgeSnapshot(pending.snapshot);
        this.failedProfileSaves.remove(pending.key, pending);
      } catch (SQLException ex) {
        LOGGER.log(Level.SEVERE, "Profile data is still not persisted for " + pending.name + ".", ex);
      }
    }
  }

  private static final class PendingProfileSave {
    private final String name;
    private final String key;
    private final long sequence;
    private final List<TableSnapshot> snapshot;

    private PendingProfileSave(String name, String key, long sequence, List<TableSnapshot> snapshot) {
      this.name = name;
      this.key = key;
      this.sequence = sequence;
      this.snapshot = snapshot;
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
    try (CachedRowSet rs = this.query("SELECT `name` FROM `mCoreProfile` WHERE LOWER(`name`) = ?", name.toLowerCase(Locale.ROOT))) {
      return rs == null ? null : rs.getString("name");
    } catch (SQLException ex) {
      throw new IllegalStateException("Could not check whether profile '" + name + "' exists.", ex);
    }
  }

  /**
   * Configure a project CA without disabling server certificate or hostname validation.
   * Aiven's project CA can be placed at plugins/mCore/ca.pem, or provided by environment.
   */
  private void configureTlsTrust(HikariConfig config) {
    String configuredCaPath = System.getenv("MCORE_MYSQL_CA_CERT");
    java.io.File caFile = configuredCaPath == null || configuredCaPath.trim().isEmpty()
      ? new java.io.File("plugins/mCore/ca.pem")
      : new java.io.File(configuredCaPath.trim());

    if ((configuredCaPath != null && !configuredCaPath.trim().isEmpty()) || caFile.isFile()) {
      if (!caFile.isFile() || !caFile.canRead()) {
        throw new IllegalStateException("MySQL CA certificate file is not readable: " + caFile.getAbsolutePath());
      }
      try (java.io.InputStream input = new java.io.FileInputStream(caFile)) {
        java.util.Collection<? extends java.security.cert.Certificate> certificates =
          java.security.cert.CertificateFactory.getInstance("X.509").generateCertificates(input);
        if (certificates.isEmpty()) {
          throw new IllegalStateException("MySQL CA certificate file contains no X.509 certificates: " + caFile.getAbsolutePath());
        }

        java.security.KeyStore trustStore = java.security.KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        int index = 1;
        for (java.security.cert.Certificate certificate : certificates) {
          trustStore.setCertificateEntry("mysql-ca-" + index++, certificate);
        }

        // The truststore contains public CA certificates only; generate a per-process password.
        String storePassword = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        java.io.File generatedTrustStore = java.io.File.createTempFile("mcore-mysql-trust-", ".p12");
        generatedTrustStore.deleteOnExit();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(generatedTrustStore)) {
          trustStore.store(output, storePassword.toCharArray());
        }

        config.addDataSourceProperty("trustCertificateKeyStoreUrl", generatedTrustStore.toURI().toURL().toExternalForm());
        config.addDataSourceProperty("trustCertificateKeyStoreType", "PKCS12");
        config.addDataSourceProperty("trustCertificateKeyStorePassword", storePassword);
        LOGGER.info("Loaded MySQL TLS CA certificate(s) from " + caFile.getAbsolutePath());
        return;
      } catch (IllegalStateException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new IllegalStateException("Could not load the MySQL CA certificate from " + caFile.getAbsolutePath() + ".", ex);
      }
    }

    String trustStorePath = System.getenv("MCORE_MYSQL_TRUSTSTORE");
    String trustStorePassword = System.getenv("MCORE_MYSQL_TRUSTSTORE_PASSWORD");
    if (trustStorePath != null && !trustStorePath.trim().isEmpty()) {
      java.io.File trustStore = new java.io.File(trustStorePath.trim());
      if (!trustStore.isFile() || !trustStore.canRead()) {
        throw new IllegalStateException("MCORE_MYSQL_TRUSTSTORE must point to a readable PKCS12 truststore file.");
      }
      if (trustStorePassword == null || trustStorePassword.isEmpty()) {
        throw new IllegalStateException("MCORE_MYSQL_TRUSTSTORE_PASSWORD is required when MCORE_MYSQL_TRUSTSTORE is configured.");
      }
      try {
        config.addDataSourceProperty("trustCertificateKeyStoreUrl", trustStore.toURI().toURL().toExternalForm());
      } catch (java.net.MalformedURLException ex) {
        throw new IllegalStateException("Invalid MySQL TLS truststore path.", ex);
      }
      config.addDataSourceProperty("trustCertificateKeyStoreType", "PKCS12");
      config.addDataSourceProperty("trustCertificateKeyStorePassword", trustStorePassword);
      return;
    }

    if (this.host != null && this.host.trim().toLowerCase(Locale.ROOT).endsWith(".aivencloud.com")) {
      throw new IllegalStateException("Aiven MySQL TLS requires its project CA certificate. Download the CA certificate from Aiven and save it as plugins/mCore/ca.pem, or set MCORE_MYSQL_CA_CERT to its absolute path. Server certificate and hostname verification remain enabled.");
    }
  }

  public void openConnection() {
    HikariConfig config = new HikariConfig();
    config.setPoolName("mConnectionPool");
    // Keep HikariCP defaults unless workload measurements justify pool tuning.
    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
    String jdbcUrl = "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.dbname +
      "?sslMode=VERIFY_IDENTITY&connectTimeout=10000&socketTimeout=30000&useUnicode=yes&characterEncoding=UTF-8";
    config.setJdbcUrl(jdbcUrl);
    config.setUsername(this.username);
    config.setPassword(this.password);

    configureTlsTrust(config);
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
    try (Connection connection = getConnection();
         PreparedStatement statement = connection.prepareStatement(query)) {
      for (int i = 0; i < vars.length; i++) {
        statement.setObject(i + 1, vars[i]);
      }

      try (ResultSet result = statement.executeQuery()) {
        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.populate(result);
        if (rowSet.next()) {
          return rowSet;
        }
        rowSet.close();
        return null;
      }
    } catch (SQLException ex) {
      LOGGER.log(Level.WARNING, "Could not execute a database query.", ex);
      throw new IllegalStateException("Database query failed.", ex);
    }
  }
}
