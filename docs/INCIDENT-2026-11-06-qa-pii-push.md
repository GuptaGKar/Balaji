# Incident Report — accidental QA/PII commit pushed to public remote

- **Date detected:** 2026-11-06
- **Detected by:** coding agent during the customer-browsing UX pass
- **Severity:** High (policy violation; untrusted-content directory published; public exposure)
- **Status:** Reported. **No git remediation performed** per owner decision ("do nothing; only report and let you handle GitHub").

## 1. Summary

A commit `2f7acf8` was created on branch `feature/customer-orange-glass-v3` with the raw
commit-editor boilerplate as its message. It swept in **248 files**, including the entire
device-QA scratch directory `.tmp_device_qa/` (which is treated as customer PII and must never
be committed), plus `Changes/` and `ss/` (both of which were to stay untracked pending a decision).

That commit is the **head of the same branch on `origin`**, and the remote repository is **public**.

## 2. Commit facts

| Field | Value |
|---|---|
| Commit | `2f7acf85b43a0e99d9367519a78e15693e2ba8b6` |
| Parent | `aae8b36` (docs: the device pass, the liquid-glass decision, and what is still unverified) |
| Branch | `feature/customer-orange-glass-v3` |
| Message | `Please enter the commit message for your changes. Lines starting with '#' will be ignored, and an empty message aborts the commit.` |
| Files changed | 248 |
| Remote | `https://github.com/GuptaGKar/Balaji.git` (`origin`) |
| Remote visibility | **public** (`"private": false`, `"visibility": "public"`) |
| Remote ref | `2f7acf85b43a0e99d9367519a78e15693e2ba8b6  refs/heads/feature/customer-orange-glass-v3` |

Evidence: `git show --stat 2f7acf8`, `git ls-remote --heads origin`, GitHub REST
`GET /repos/GuptaGKar/Balaji`.

## 3. Contents of the commit

- **214 files under `.tmp_device_qa/`** — device QA screenshots (`*.png`), UI hierarchy dumps
  (`*.xml`), and Python verification scripts. This is the directory the project treats as
  containing customer PII.
- **`Changes/`** — redesign brief (`.md`) and mockup PNGs.
- **`ss/`** — 27 screenshots, store assets, vendor images, `MANIFEST.txt`.
- **Intended files that were swept in alongside them:**
  - `android/app/src/androidTest/java/com/balaji/provision/ui/components/BrowsingControlsTest.kt` (RED-phase UI tests)
  - `android/app/src/test/java/com/balaji/provision/ui/catalog/ProductViewModelTest.kt` (new cached-refresh test)
  - `docs/superpowers/plans/2026-11-06-customer-browsing-ux.md` (UX plan)

## 4. Policy violations

1. **Never commit `.tmp_device_qa/`** (customer PII). Violated.
2. **Do not add `Changes/` / `ss/` without a decision.** Violated.
3. **No push without explicit human approval.** Violated (commit is the remote head).

## 5. Exposure / sensitivity scan (read-only, on committed text)

| Check | Result |
|---|---|
| JWT-like tokens (`eyJ...`) | 0 files |
| `service_role` / `SUPABASE_KEY` / `Bearer ` references | 0 files |
| Email addresses | 0 |
| 10-digit phone-number patterns | 0 |
| Obvious address strings (Flat/Road/Street/Nagar/PIN/Deliver to) | none found in text dumps |

**Caveat:** this scan covered committed *text* (XML/scripts). It does **not** prove the PNG
screenshots are free of personal data — the signed-in account UI (customer name / saved address /
order details) may be visible in the captured frames. Screenshots should be assumed potentially
sensitive until reviewed.

No credential or secret substance was found in the committed text. Any session token that may be
visible in a screenshot should be treated as exposed and rotated defensively.

## 6. Owner decision (2026-11-06)

- "Do nothing; only report and let you handle GitHub." → **No `git reset`, no force-push, no
  `.gitignore` change, no history rewrite performed by the agent.**
- "Yes, draft it for my review" → GitHub Support removal draft at
  `docs/github-support-removal-request-2f7acf8.md`.

## 7. Recommended remediation (for the owner; NOT executed)

1. **Make `GuptaGKar/Balaji` private immediately** to stop ongoing exposure while remediation runs.
2. Rewrite the branch so the bad commit is no longer reachable:
   - `git reset --hard aae8b36` (drops `2f7acf8` locally), then
   - `git push --force-with-lease origin feature/customer-orange-glass-v3`.
3. Add `.gitignore` entries **before** re-adding intended files:
   ```
   .tmp_device_qa/
   Changes/
   ss/
   ```
4. Re-commit only the three intended source/doc files under their own review.
5. Submit the GitHub Support sensitive-data-removal request (draft provided) so the commit is
   purged from GitHub caches; note forks/clones cannot be recalled.
6. Review the screenshots for personal data; if any account/session material is visible, rotate it.

## 8. Open items

- Whether any PNG in the commit depicts personal account data (not yet reviewed).
- Whether any clone/fork already fetched `2f7acf8`.
- This incident is **not** yet reflected in `docs/PROJECT_STATUS.md` / `QA_LOG.md`; recorded here
  as a standalone report per the owner's "only report" instruction.
