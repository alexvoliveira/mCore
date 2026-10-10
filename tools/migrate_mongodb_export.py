#!/usr/bin/env python3
"""Import mongoexport JSONL backups into an existing mCore MySQL schema.

This is an offline migration utility. It is not packaged into the mCore plugin
and intentionally has no MongoDB driver dependency.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from collections import defaultdict
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any, Dict, Iterable, List, Mapping, MutableMapping, Optional

TABLES = (
    "mCoreProfile",
    "mCoreSkyWars",
    "mCoreTheBridge",
    "mCoreMurder",
    "mCoreNetworkBooster",
    "mCoreTitles",
)
PROFILE_TABLES = ("mCoreProfile", "mCoreSkyWars", "mCoreTheBridge", "mCoreMurder")
DERIVED_FIELDS = {"totalkills", "totalwins", "totalpoints"}
BATCH_SIZE = 200
MISSING = object()

DEFAULTS: Dict[str, Dict[str, Any]] = {
    "mCoreProfile": {
        "cash": 0,
        "role": "Membro",
        "deliveries": "{}",
        "preferences": '{"pv": 0, "pm": 0, "bg": 0, "pl": 0}',
        "titles": "[]",
        "boosters": "{}",
        "achievements": "[]",
        "selected": '{"title": "0", "icon": "0"}',
        "created": None,
        "lastlogin": None,
    },
    "mCoreSkyWars": {
        "1v1kills": 0, "1v1deaths": 0, "1v1assists": 0, "1v1games": 0, "1v1wins": 0,
        "2v2kills": 0, "2v2deaths": 0, "2v2assists": 0, "2v2games": 0, "2v2wins": 0,
        "coins": 0.0, "lastmap": 0, "cosmetics": "{}", "selected": "{}", "kitconfig": "{}",
    },
    "mCoreTheBridge": {
        "1v1kills": 0, "1v1deaths": 0, "1v1games": 0, "1v1points": 0, "1v1wins": 0,
        "2v2kills": 0, "2v2deaths": 0, "2v2games": 0, "2v2points": 0, "2v2wins": 0,
        "coins": 0.0, "winstreak": 0, "laststreak": None, "lastmap": 0,
        "hotbar": "{}", "cosmetics": "{}", "selected": "{}",
    },
    "mCoreMurder": {
        "clkills": 0, "clbowkills": 0, "clknifekills": 0, "clthrownknifekills": 0,
        "clwins": 0, "cldetectivewins": 0, "clkillerwins": 0, "clquickestdetective": 0,
        "clquickestkiller": 0, "clchancedetective": 1, "clchancekiller": 1,
        "askills": 0, "asthrownknifekills": 0, "aswins": 0, "coins": 0.0,
        "lastmap": 0, "cosmetics": "{}", "selected": "{}",
    },
}

def decode_extended_json(value: Any) -> Any:
    """Decode common mongoexport Extended JSON scalar wrappers."""
    if isinstance(value, list):
        return [decode_extended_json(item) for item in value]
    if not isinstance(value, dict):
        return value

    if len(value) == 1:
        if "$numberLong" in value or "$numberInt" in value:
            return int(value.get("$numberLong", value.get("$numberInt")))
        if "$numberDouble" in value:
            return float(value["$numberDouble"])
        if "$numberDecimal" in value:
            return Decimal(value["$numberDecimal"])
        if "$oid" in value:
            return str(value["$oid"])
        if "$date" in value:
            date_value = value["$date"]
            if isinstance(date_value, dict) and "$numberLong" in date_value:
                return int(date_value["$numberLong"])
            if isinstance(date_value, str):
                parsed = datetime.fromisoformat(date_value.replace("Z", "+00:00"))
                if parsed.tzinfo is None:
                    parsed = parsed.replace(tzinfo=timezone.utc)
                return int(parsed.timestamp() * 1000)

    return {str(key): decode_extended_json(item) for key, item in value.items()}

def read_jsonl(path: Path) -> Iterable[Dict[str, Any]]:
    """Read one Extended JSON object per line (mongoexport default format)."""
    with path.open("r", encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                doc = decode_extended_json(json.loads(line))
            except (json.JSONDecodeError, ValueError, TypeError) as exc:
                raise ValueError(f"{path}:{line_number}: invalid JSON export: {exc}") from exc
            if not isinstance(doc, dict):
                raise ValueError(f"{path}:{line_number}: expected a JSON object")
            yield doc

def mysql_value(value: Any) -> Any:
    """Convert nested values into a representation accepted by MySQL Connector/Python."""
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    return value

def millis_now() -> int:
    return int(datetime.now(tz=timezone.utc).timestamp() * 1000)

def profile_row(table: str, document: Mapping[str, Any], columns: List[str]) -> Dict[str, Any]:
    if "_id" not in document:
        raise ValueError(f"Profile document is missing _id for {table}")
    if table == "mCoreProfile":
        source = document
    else:
        raw = document.get(table, {})
        if raw is None:
            raw = {}
        if not isinstance(raw, dict):
            raise ValueError(f"Profile {document.get('_id')!r}: {table} must be an object")
        source = raw

    if table == "mCoreProfile":
        result: Dict[str, Any] = {"name": str(document["_id"])}
    else:
        result = {"name": str(document["_id"])}

    defaults = DEFAULTS[table]
    for column in columns:
        if column == "name":
            continue
        if column in source:
            result[column] = mysql_value(source[column])
        elif column in defaults:
            default = defaults[column]
            # These defaults are time-dependent in mCore's Java code. When the
            # export has no original value, apply that same default at migration time.
            if default is None and column in {"created", "lastlogin", "laststreak"}:
                default = millis_now()
            result[column] = mysql_value(default)
        elif column in DERIVED_FIELDS:
            # MongoDB stored cached leaderboard totals that SQL recomputes from columns.
            continue
        else:
            raise ValueError(f"Unknown column {column!r} in schema for {table}; inspect the target schema before importing")

    extra = set(source) - set(columns) - DERIVED_FIELDS
    # A source row may carry fields that do not exist in this version's SQL table.
    # Fail instead of silently discarding such data.
    if extra:
        raise ValueError(f"Profile {document.get('_id')!r}, {table}: unmapped field(s): {', '.join(sorted(extra))}")
    return result

def titled_row(document: Mapping[str, Any], columns: List[str]) -> Dict[str, Any]:
    if "_id" not in document:
        raise ValueError("Custom title document is missing _id")
    row = {"id": str(document["_id"])}
    for col in columns:
        if col == "id":
            continue
        if col not in document:
            raise ValueError(f"Custom title {document['_id']!r} is missing {col!r}")
        row[col] = mysql_value(document[col])
    extra = set(document) - set(columns) - {"_id"}
    if extra:
        raise ValueError(f"Custom title {document['_id']!r}: unmapped field(s): {', '.join(sorted(extra))}")
    return row

def booster_row(document: Mapping[str, Any], columns: List[str]) -> Dict[str, Any]:
    if "_id" not in document:
        raise ValueError("Booster document is missing _id")
    row = {
        "id": str(document["_id"]),
        "booster": mysql_value(document.get("booster", "Maxteer")),
        "multiplier": mysql_value(document.get("multiplier", 1.0)),
        "expires": mysql_value(document.get("expires", 0)),
    }
    extra = set(document) - set(columns) - {"_id"}
    if extra:
        raise ValueError(f"Booster {document['_id']!r}: unmapped field(s): {', '.join(sorted(extra))}")
    return row

def insert_batch(cursor: Any, connection: Any, table: str, rows: List[Dict[str, Any]], overwrite: bool, dry_run: bool) -> int:
    if not rows or dry_run:
        return 0
    # Table and column names come only from the explicit allowlist and live schema.
    columns = list(rows[0].keys())
    quoted_columns = ", ".join(f"\`{col.replace('\`', '\`\`')}\`" for col in columns)
    placeholders = ", ".join(["%s"] * len(columns))
    if overwrite:
        updates = [col for col in columns if col not in {"name", "id"}]
        on_duplicate = ""
        if updates:
            on_duplicate = " ON DUPLICATE KEY UPDATE " + ", ".join(
                f"\`{col.replace('\`', '\`\`')}\` = VALUES(\`{col.replace('\`', '\`\`')}\`)" for col in updates
            )
        sql = f"INSERT INTO \`{table}\` ({quoted_columns}) VALUES ({placeholders}){on_duplicate}"
    else:
        sql = f"INSERT IGNORE INTO \`{table}\` ({quoted_columns}) VALUES ({placeholders})"
    values = [tuple(row.get(col) for col in columns) for row in rows]
    cursor.executemany(sql, values)
    affected = max(cursor.rowcount, 0)
    connection.commit()
    return affected

def schema_columns(cursor: Any) -> Dict[str, List[str]]:
    marks = ", ".join(["%s"] * len(TABLES))
    cursor.execute(
        "SELECT TABLE_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
        f"WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ({marks}) "
        "ORDER BY TABLE_NAME, ORDINAL_POSITION",
        TABLES,
    )
    schemas: Dict[str, List[str]] = defaultdict(list)
    for table, column in cursor.fetchall():
        schemas[table].append(column)
    missing = [table for table in TABLES if table not in schemas]
    if missing:
        raise RuntimeError(
            "Target schema is missing table(s): " + ", ".join(missing) +
            ". Start a compatible mCore build once to create/update the schema, then stop it before importing."
        )
    for table in PROFILE_TABLES:
        columns = set(schemas[table])
        if "name" not in columns:
            raise RuntimeError(f"Target table {table} has no 'name' column")
    for table, pk in (("mCoreNetworkBooster", "id"), ("mCoreTitles", "id")):
        if pk not in schemas[table]:
            raise RuntimeError(f"Target table {table} has no '{pk}' column")
    return schemas

def target_connection():
    try:
        import mysql.connector
    except ImportError as exc:
        raise RuntimeError("Install the migration-only client with: python -m pip install mysql-connector-python") from exc

    settings: Dict[str, Any] = {
        "host": os.environ.get("MCORE_MYSQL_HOST", "localhost"),
        "port": int(os.environ.get("MCORE_MYSQL_PORT", "3306")),
        "database": os.environ.get("MCORE_MYSQL_DATABASE", "server"),
        "user": os.environ.get("MCORE_MYSQL_USER", "mcore"),
        "password": os.environ.get("MCORE_MYSQL_PASSWORD", ""),
        "ssl_verify_cert": True,
        "ssl_verify_identity": True,
        "autocommit": False,
    }
    ssl_ca = os.environ.get("MCORE_MYSQL_SSL_CA")
    if ssl_ca:
        settings["ssl_ca"] = ssl_ca
    return mysql.connector.connect(**settings)

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profiles", type=Path, required=True, help="JSONL export of Mongo collection Profile")
    parser.add_argument("--titles", type=Path, required=True, help="JSONL export of Mongo collection mCoreTitles")
    parser.add_argument("--boosters", type=Path, required=True, help="JSONL export of Mongo collection mCoreNetworkBooster")
    parser.add_argument("--overwrite", action="store_true", help="Update existing target rows on primary-key conflict")
    parser.add_argument("--dry-run", action="store_true", help="Validate and map exports without writing rows")
    args = parser.parse_args()

    for path in (args.profiles, args.titles, args.boosters):
        if not path.is_file():
            parser.error(f"Export file does not exist: {path}")

    connection = None
    try:
        connection = target_connection()
        cursor = connection.cursor()
        schemas = schema_columns(cursor)
        before: Dict[str, int] = {}
        for table in TABLES:
            cursor.execute(f"SELECT COUNT(*) FROM \`{table}\`")
            before[table] = int(cursor.fetchone()[0])

        buffers: Dict[str, List[Dict[str, Any]]] = {table: [] for table in TABLES}
        source_counts: Dict[str, int] = defaultdict(int)
        attempted: Dict[str, int] = defaultdict(int)
        affected: Dict[str, int] = defaultdict(int)

        def flush() -> None:
            for table in TABLES:
                if buffers[table]:
                    affected[table] += insert_batch(
                        cursor, connection, table, buffers[table], args.overwrite, args.dry_run
                    )
                    buffers[table].clear()

        for source_doc in read_jsonl(args.profiles):
            source_counts["Profile"] += 1
            for table in PROFILE_TABLES:
                row = profile_row(table, source_doc, schemas[table])
                buffers[table].append(row)
                attempted[table] += 1
            if any(len(buffers[table]) >= BATCH_SIZE for table in PROFILE_TABLES):
                flush()

        for source_doc in read_jsonl(args.titles):
            source_counts["mCoreTitles"] += 1
            row = titled_row(source_doc, schemas["mCoreTitles"])
            buffers["mCoreTitles"].append(row)
            attempted["mCoreTitles"] += 1
            if len(buffers["mCoreTitles"]) >= BATCH_SIZE:
                flush()

        for source_doc in read_jsonl(args.boosters):
            source_counts["mCoreNetworkBooster"] += 1
            row = booster_row(source_doc, schemas["mCoreNetworkBooster"])
            buffers["mCoreNetworkBooster"].append(row)
            attempted["mCoreNetworkBooster"] += 1
            if len(buffers["mCoreNetworkBooster"]) >= BATCH_SIZE:
                flush()
        flush()

        after: Dict[str, int] = {}
        for table in TABLES:
            cursor.execute(f"SELECT COUNT(*) FROM \`{table}\`")
            after[table] = int(cursor.fetchone()[0])
        connection.commit()

        print("MongoDB export → MySQL migration report")
        print(f"Mode: {'DRY RUN (no writes)' if args.dry_run else ('OVERWRITE' if args.overwrite else 'INSERT IGNORE')}")
        print(f"Source documents — Profile: {source_counts['Profile']}, titles: {source_counts['mCoreTitles']}, boosters: {source_counts['mCoreNetworkBooster']}")
        print("Table                        Rows attempted   Affected rows   Before   After")
        for table in TABLES:
            print(f"{table:<28} {attempted[table]:>14} {affected[table]:>14} {before[table]:>8} {after[table]:>7}")
        print("Migration finished. Compare source counts, target counts, and application behavior before cutover.")
        return 0
    except Exception as exc:
        if connection is not None:
            try:
                connection.rollback()
            except Exception:
                pass
        print(f"Migration failed: {exc}", file=sys.stderr)
        print("Some earlier batches may already be committed. Default INSERT IGNORE mode is safe to rerun.", file=sys.stderr)
        return 1
    finally:
        if connection is not None:
            connection.close()

if __name__ == "__main__":
    raise SystemExit(main())
