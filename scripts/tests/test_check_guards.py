"""Exercise the guard in isolated repositories, without altering real migrations."""

from pathlib import Path
import subprocess
import tempfile
import unittest


GUARD = Path(__file__).resolve().parents[1] / "check_guards.sh"


class BusinessGuardTest(unittest.TestCase):
    def check_source(self, source):
        with tempfile.TemporaryDirectory() as directory:
            migrations = Path(directory) / "supabase/migrations"
            migrations.mkdir(parents=True)
            (migrations / "fixture.sql").write_text(source)
            return subprocess.run(
                ["bash", str(GUARD)], cwd=directory, capture_output=True, text=True
            )

    def test_sql_documentation_can_explain_that_orders_have_no_minimum(self):
        result = self.check_source("-- No minimum order is introduced anywhere.\nSELECT 1;\n")
        self.assertEqual(result.returncode, 0, result.stdout)

    def test_minimum_order_column_is_rejected(self):
        result = self.check_source("ALTER TABLE public.settings ADD minimum_order bigint;\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("minimum-order concept found", result.stdout)

    def test_trailing_comment_does_not_hide_forbidden_code(self):
        result = self.check_source("SELECT minimum_order FROM settings; -- legacy field\n")
        self.assertNotEqual(result.returncode, 0)

    def test_comment_on_another_line_does_not_hide_forbidden_code(self):
        result = self.check_source("-- delivery policy\nSELECT minimum_order FROM settings;\n")
        self.assertNotEqual(result.returncode, 0)

    def test_hardcoded_threshold_remains_rejected(self):
        result = self.check_source("SELECT 25000 AS delivery_threshold;\n")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("hard-coded 25000", result.stdout)

    def test_configured_threshold_is_allowed(self):
        result = self.check_source("SELECT delivery_charge_threshold_paise FROM store_settings;\n")
        self.assertEqual(result.returncode, 0, result.stdout)


if __name__ == "__main__":
    unittest.main()
