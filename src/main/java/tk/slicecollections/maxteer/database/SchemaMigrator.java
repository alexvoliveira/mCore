package tk.slicecollections.maxteer.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Versioned, idempotent MySQL schema upgrades. A server-level lock prevents
 * Bukkit and BungeeCord instances from applying the same migration concurrently.
 */
final class SchemaMigrator {

  private static final String LOCK_NAME = "mCore_schema_migration";

  private SchemaMigrator() {}

  static void migrate(HikariDatabase database) {
    try (Connection connection = database.getConnection()) {
      acquireLock(connection);
      try {
        createHistoryTable(connection);

        if (!isApplied(connection, 1)) {
          ensureColumn(connection, "mCoreProfile", "cash", "BIGINT NOT NULL DEFAULT 0");
          ensureColumn(connection, "mCoreSkyWars", "lastmap", "BIGINT NOT NULL DEFAULT 0");
          ensureColumn(connection, "mCoreSkyWars", "kitconfig", "TEXT NULL");
          ensureColumn(connection, "mCoreTheBridge", "hotbar", "TEXT NULL");
          ensureColumn(connection, "mCoreMurder", "askills", "BIGINT NOT NULL DEFAULT 0");
          ensureColumn(connection, "mCoreMurder", "asthrownknifekills", "BIGINT NOT NULL DEFAULT 0");
          ensureColumn(connection, "mCoreMurder", "aswins", "BIGINT NOT NULL DEFAULT 0");
          record(connection, 1, "Ensure legacy profile/statistic columns");
        }

        if (!isApplied(connection, 2)) {
          try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS `mCoreNetworkBooster` (" +
              "`id` VARCHAR(32) NOT NULL, `booster` TEXT, `multiplier` DOUBLE, `expires` BIGINT, " +
              "PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin");
            statement.execute("CREATE TABLE IF NOT EXISTS `mCoreTitles` (" +
              "`id` VARCHAR(191) NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, " +
              "PRIMARY KEY (`id`)) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin");
          }
          record(connection, 2, "Create network booster and custom title tables");
        }

        if (!isApplied(connection, 3)) {
          try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS mCorePlayerSkin (" +
              "player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, " +
              "player_name VARCHAR(16) NOT NULL, skin_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, " +
              "skin_name VARCHAR(64) NOT NULL, skin_owner VARCHAR(16) NOT NULL, " +
              "texture_value TEXT NOT NULL, texture_signature TEXT NOT NULL, selected_at BIGINT NOT NULL, " +
              "PRIMARY KEY (player_uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci");
            statement.execute("CREATE TABLE IF NOT EXISTS mCoreSkinHistory (" +
              "id BIGINT NOT NULL AUTO_INCREMENT, " +
              "player_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, " +
              "player_name VARCHAR(16) NOT NULL, skin_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, " +
              "skin_name VARCHAR(64) NOT NULL, skin_owner VARCHAR(16) NOT NULL, " +
              "texture_value TEXT NOT NULL, texture_signature TEXT NOT NULL, " +
              "used_at BIGINT NOT NULL, expires_at BIGINT NOT NULL, " +
              "PRIMARY KEY (id), UNIQUE KEY uq_mCoreSkinHistory_player_skin (player_uuid, skin_id), " +
              "KEY idx_mCoreSkinHistory_expiry (expires_at), KEY idx_mCoreSkinHistory_player_used (player_uuid, used_at)) " +
              "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE utf8mb4_unicode_ci");
          }
          record(connection, 3, "Create persistent selected skins and expiring skin history");
        }
      } finally {
        releaseLock(connection);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Could not migrate the mCore MySQL schema.", ex);
    }
  }

  private static void acquireLock(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
      statement.setString(1, LOCK_NAME);
      statement.setInt(2, 30);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next() || result.getInt(1) != 1) {
          throw new SQLException("Timed out acquiring MySQL schema migration lock");
        }
      }
    }
  }

  private static void releaseLock(Connection connection) {
    try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
      statement.setString(1, LOCK_NAME);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next() || result.getInt(1) != 1) {
          Database.LOGGER.warning("MySQL did not confirm releasing the mCore schema migration lock.");
        }
      }
    } catch (SQLException ex) {
      Database.LOGGER.warning("Could not release mCore schema migration lock: " + ex.getMessage());
    }
  }

  private static void createHistoryTable(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE IF NOT EXISTS `mCoreSchemaHistory` (" +
        "`version` INT NOT NULL, `description` VARCHAR(255) NOT NULL, " +
        "`installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`version`)) " +
        "ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin");
    }
  }

  private static boolean isApplied(Connection connection, int version) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT 1 FROM `mCoreSchemaHistory` WHERE `version` = ?")) {
      statement.setInt(1, version);
      try (ResultSet result = statement.executeQuery()) {
        return result.next();
      }
    }
  }

  private static void record(Connection connection, int version, String description) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "INSERT INTO `mCoreSchemaHistory` (`version`, `description`) VALUES (?, ?)")) {
      statement.setInt(1, version);
      statement.setString(2, description);
      statement.executeUpdate();
    }
  }

  private static void ensureColumn(Connection connection, String table, String column, String definition) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS " +
        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
      statement.setString(1, table);
      statement.setString(2, column);
      try (ResultSet result = statement.executeQuery()) {
        if (result.next()) {
          return;
        }
      }
    }

    // Table/column names and definitions are internal constants, never user input.
    try (Statement statement = connection.createStatement()) {
      statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
    }
  }
}
