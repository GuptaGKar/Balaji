# GitHub Support — sensitive-data removal request (DRAFT — owner to review and submit)

**Status:** DRAFT. Not sent. This file is prepared for the repository owner to review, edit, and
submit via GitHub Support (https://support.github.com/contact) or the sensitive-data removal form.

---

## Subject

Request to purge a commit containing non-source QA/device data from a public repository

## Repository

- Owner/org: `GuptaGKar`
- Repository: `Balaji`
- URL: https://github.com/GuptaGKar/Balaji

## Commit to purge

- SHA: `2f7acf85b43a0e99d9367519a78e15693e2ba8b6`
- Branch: `feature/customer-orange-glass-v3`
- Committed: 2026-11-06
- Reason: accidentally committed a device-QA scratch directory (`.tmp_device_qa/`, screenshots,
  UI dumps and scripts) together with design assets. These files are not source code and are
  considered personal/sensitive QA data. The commit was also the branch head and therefore
  publicly reachable.

## Request

Please purge commit `2f7acf8` (and its tree/blob objects) from GitHub's caches so it is not
reachable by direct SHA, and advise whether any fork or clone copies can be reached.

## Actions already planned/taken by the owner

- [ ] Repository visibility changed to private (owner action).
- [ ] Branch rewritten so `2f7acf8` is no longer reachable from the branch tip.
- [ ] The three directories added to `.gitignore` to prevent recurrence.
- [ ] Screenshots reviewed for personal data; any exposed session material rotated.

## Notes to the owner before submitting

- GitHub cannot purge forks or already-made clones; confirm whether that risk is acceptable.
- GitHub Support may ask for the specific file paths; the commit adds 214 files under
  `.tmp_device_qa/`, plus `Changes/` and `ss/`.
- Fill in any account/contact details GitHub Support requires (this draft intentionally omits them).
