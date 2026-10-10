import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "migrate_mongodb_export.py"
SPEC = importlib.util.spec_from_file_location("mcore_migration", SCRIPT)
MIGRATION = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MIGRATION)


class MongoExportMappingTests(unittest.TestCase):
    def test_profile_id_and_nested_stats_map_without_losing_fields(self):
        columns = ["name"] + list(MIGRATION.DEFAULTS["mCoreProfile"])
        document = {
            "_id": "ExamplePlayer",
            "cash": 25,
            "role": "Membro",
            "totalkills": 14,
            "totalwins": 5,
            "mCoreSkyWars": {"1v1kills": 9, "totalwins": 4},
            "mCoreTheBridge": {"1v1points": 8},
            "mCoreMurder": {"clkills": 5},
        }
        row = MIGRATION.profile_row("mCoreProfile", document, columns)
        self.assertEqual(row["name"], "ExamplePlayer")
        self.assertEqual(row["cash"], 25)
        self.assertEqual(row["role"], "Membro")
        self.assertEqual(row["preferences"], MIGRATION.DEFAULTS["mCoreProfile"]["preferences"])

    def test_stats_rows_fill_missing_fields_from_mcore_defaults(self):
        table = "mCoreSkyWars"
        columns = ["name"] + list(MIGRATION.DEFAULTS[table])
        row = MIGRATION.profile_row(table, {
            "_id": "exampleplayer",
            table: {"1v1kills": 9, "totalkills": 11},
        }, columns)
        self.assertEqual(row["name"], "exampleplayer")
        self.assertEqual(row["1v1kills"], 9)
        self.assertEqual(row["1v1deaths"], 0)
        self.assertEqual(row["cosmetics"], "{}")

    def test_unmapped_source_field_fails_instead_of_being_discarded(self):
        columns = ["name"] + list(MIGRATION.DEFAULTS["mCoreProfile"])
        with self.assertRaisesRegex(ValueError, "unmapped field"):
            MIGRATION.profile_row("mCoreProfile", {
                "_id": "exampleplayer", "cash": 1, "unmapped_secret_data": {"important": True}
            }, columns)

    def test_title_and_booster_identifier_mapping(self):
        self.assertEqual(
            MIGRATION.titled_row({"_id": "custom1", "name": "Custom", "description": "Description"},
                                 ["id", "name", "description"]),
            {"id": "custom1", "name": "Custom", "description": "Description"},
        )
        self.assertEqual(
            MIGRATION.booster_row({"_id": "Sky Wars", "booster": "Membro", "multiplier": 1.5, "expires": 0},
                                  ["id", "booster", "multiplier", "expires"]),
            {"id": "Sky Wars", "booster": "Membro", "multiplier": 1.5, "expires": 0},
        )

    def test_extended_json_scalars_are_decoded(self):
        self.assertEqual(MIGRATION.decode_extended_json({"$numberLong": "123"}), 123)
        self.assertEqual(MIGRATION.decode_extended_json({"$oid": "abc"}), "abc")
        self.assertEqual(MIGRATION.decode_extended_json({"nested": [{"$numberInt": "4"}]}), {"nested": [4]})


if __name__ == "__main__":
    unittest.main()
