package tk.slicecollections.maxteer.database;

import tk.slicecollections.maxteer.database.data.DataContainer;
import tk.slicecollections.maxteer.database.data.DataTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Validates SQL identifiers against the schemas declared by mCore. */
final class SqlIdentifier {

  private SqlIdentifier() {}

  static String table(String requested) {
    if (requested == null) {
      throw new IllegalArgumentException("Table name is required");
    }
    for (DataTable schema : DataTable.listTables()) {
      String name = schema.getInfo().name();
      if (name.equalsIgnoreCase(requested)) {
        return name;
      }
    }
    throw new IllegalArgumentException("Unsupported leaderboard table: " + requested);
  }

  static List<String> columns(String table, String... requested) {
    DataTable schema = null;
    for (DataTable candidate : DataTable.listTables()) {
      if (candidate.getInfo().name().equalsIgnoreCase(table)) {
        schema = candidate;
        break;
      }
    }
    if (schema == null) {
      throw new IllegalArgumentException("Unsupported leaderboard table: " + table);
    }

    Map<String, DataContainer> allowedColumns = schema.getDefaultValues();
    List<String> result = new ArrayList<>();
    for (String column : requested) {
      if (column == null) {
        throw new IllegalArgumentException("Column name is required");
      }
      if ("name".equalsIgnoreCase(column)) {
        result.add("name");
        continue;
      }
      String canonical = null;
      for (String allowed : allowedColumns.keySet()) {
        if (allowed.equalsIgnoreCase(column)) {
          canonical = allowed;
          break;
        }
      }
      if (canonical == null) {
        throw new IllegalArgumentException("Unsupported column '" + column + "' for table '" + table + "'");
      }
      result.add(canonical);
    }
    return result;
  }
}
