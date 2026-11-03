import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

class PrivacyDisclosuresTest(unittest.TestCase):
    def test_both_privacy_policies_disclose_uploaded_media(self):
        for path in ('web/src/app/privacy/page.tsx', 'android/app/src/main/res/raw/privacy_policy.txt'):
            with self.subTest(path=path):
                text = ' '.join((ROOT / path).read_text().lower().split())
                self.assertNotIn('collect contacts, sms, photos or files', text)
                self.assertIn('photos and videos', text)

    def test_deletion_information_acknowledges_remaining_content(self):
        for path in ('web/src/app/account-deletion/page.tsx', 'android/app/src/main/res/raw/account_deletion.txt'):
            with self.subTest(path=path):
                text = ' '.join((ROOT / path).read_text().lower().split())
                self.assertIn('may remain after in-app deletion', text)

if __name__ == '__main__':
    unittest.main()
