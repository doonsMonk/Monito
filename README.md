# Monito (rebuild)

A modern rebuild of **Monito Expense Manager** (`com.monito`, by Cygnipro Softwares, last published as
v8.3 in 2020 and removed from Google Play in 2025). The goal of this first version is feature parity with
the old app **and full compatibility with its data**: the same `MonitoDB.sqlite` schema, the same
`.monitobk` backup format, and the same upgrade chain for databases created by any older Monito version.

Built with Kotlin, Jetpack Compose (Material 3), plain SQLite, and WorkManager. Minimum Android 8.0 (API 26).

## Download and install

Get the latest build from the [Releases page](https://github.com/doonsMonk/Monito/releases/latest):

- [Monito-9.2.apk](https://github.com/doonsMonk/Monito/releases/download/v9.2/Monito-9.2.apk)

The same file is also kept in the [release](release/) folder of this repository.

On the phone, download the file, open it, and allow installing from this source when Android asks.
Later versions install over the existing app and keep its data: open the new APK and choose
**Update**, never uninstall first. *Settings → Check for updates* compares the installed version with
the latest release here and offers the download.

From version 9.1 on, builds are signed with a dedicated release key. Version 9.0 was signed with a
development key, so going from 9.0 to 9.1 is the one exception: make a backup in the app, uninstall
9.0, install 9.1 and restore the backup.

## Moving your data from the old app

Pick whichever file you can get hold of; the **Import backup file…** button on *Settings → View / Restore
backups* (or the *Existing user?* card on first launch) accepts all of them:

| Source | Where to find it |
|---|---|
| Manual or automatic backup from old Monito | Phone storage → `Monito/backups/*.monitobk` |
| Google Drive sync of old Monito | Google Drive → `Monito/backups/*.monitobk` (download it to the phone first) |
| Encrypted Drive copy of this app | Google Drive → `Monito/backups/*.monitoenc`, downloaded to the phone; asks for the passphrase |
| Raw database (rooted phone / `adb`) | `/data/data/com.monito/databases/MonitoDB.sqlite` |

The `.monitobk` files are the SQLite database encrypted with AES (fixed key, derived the way the old app
did it). The new app decrypts them, checks the SQLite header, replaces its own database and then runs the
original `DatabaseUpdater` steps so even a very old backup ends up at schema version 7.0.

Receipt photos are *not* inside the backup (the old app stored them in `Monito/monito_images`), so a
restored transaction keeps its `img_path` but the thumbnail only appears if that file still exists.

The new app uses a different package id (`com.monito.expense`), so it can be installed next to the old
app while you migrate.

## What is implemented

- Transactions: add / edit / move to trash, note, date, photo attachment (camera or gallery), amount field
  with a small calculator (`120+35.5`), list grouped by month and day with daily totals.
- Totals: income, expense, balance, carry forward and balance with C/F for the selected time period.
- Time periods: this month, last month, last 3 months, last 6 months, all time, custom range, plus
  ◀ ▶ arrows under the title (and hardware left/right keys) that walk month by month; for
  "last N months" and custom ranges the arrows shift the window by its own length.
- Graphs: donut chart per category (expense / income) with include/exclude checkboxes and percentages;
  tap a category to see its records.
- Categories: add / edit / delete (records move to *Other expenses* / *Other incomes*), colours from the
  original 19-colour palette, favourites shown first in the picker.
- Recurrings: daily / weekly / monthly / yearly rules; due transactions are added automatically
  (also catching up after days without opening the app).
- Trash: restore or delete forever, clear trash, optional auto-delete after 7 days.
- Search.
- Settings: currency symbol/side/separators, carry forward, startup screen, theme (system / light / dark),
  daily reminder notification, automatic daily backup, manual backup, backup list with restore / share /
  export / delete, screen lock (device credential or biometrics), hide content in recents.
- Export to Excel (`.xlsx`, one sheet per month) for the selected period.
- Print (menu → Print…): a report of the selected period, grouped month by month with totals
  (optionally one page per month) or as a single list, through Android's print dialog (printer or
  "Save as PDF").
- Home screen widget "Add transaction".

### New in this rebuild (not in the original)

- **Budgets** (drawer → Budgets): a monthly limit per expense category with a progress bar for the
  current month and the amount left or overspent.
- **Trends** (drawer → Trends): income vs expense bars for any 12-month window, a per-category
  history, monthly averages and a year summary.
- **Search filters**: type, categories, amount range, date range, "with photo" and `#tags` taken from
  notes, on top of the text search.
- **Quick templates** (drawer → Quick templates, or the bookmark icon on the add screen): one-tap
  presets that prefill amount, category and note.
- **Long press** on a transaction for Edit / Duplicate / Delete (with Undo). There is no swipe to
  delete: it fired by accident while scrolling.
- **Add transaction in three steps**, as in the original: calculator for the amount, category picker
  (with "Add category" and a colour choice), then a details page. Tapping the amount or category
  there reopens that step. Tapping a day header on the home screen adds a transaction for that day.
- **Category colours**: a new category starts with the least used palette colour, and
  Categories → palette icon gives every category a different colour.
- **Smarter recurrings**: optional end date, "skip next", a preview of the next five dates and a
  notification when recurring transactions are added automatically.
- **Tablet layout**: on screens 720 dp and wider, transactions and graphs sit side by side.
- **Google Drive** (drawer → Google Drive): see below.

### Google Drive backup

Every upload is the whole database encrypted on the phone with **your passphrase** (AES-256-GCM, key
from PBKDF2-HMAC-SHA256) and stored under `Monito/backups/*.monitoenc` in your Drive. Only someone with
the passphrase can read the copies, Google included. Any device with the same passphrase can restore a
copy (first-run screen → "Restore this copy", or Settings → Google Drive). With "Upload after every
daily backup" on, the nightly backup is also uploaded; the newest 10 copies are kept.

This is snapshot based: it does not merge edits made on two devices at the same time. Use one device
as the one you enter data on, and restore its latest copy on the other.

**One-time setup in Google Cloud (needed before sign-in works):**

1. Open <https://console.cloud.google.com/>, create a project (any name) and enable the **Google
   Drive API** under *APIs & Services → Library*.
2. Under *APIs & Services → OAuth consent screen* create an *External* consent screen, add yourself
   as a test user, and add the scopes `.../auth/drive.file` and `email`.
3. Under *APIs & Services → Credentials → Create credentials → OAuth client ID* choose **Android**,
   package name `com.monito.expense`, and the SHA-1 of the signing key. The published builds (9.1 and
   later) are signed with the release key, SHA-1
   `AF:FB:2E:6D:9C:FB:78:47:BC:1D:82:9C:23:41:62:BC:82:12:40:01`. A build signed with another key
   (for example a debug build) needs its own client with that key's SHA-1; several Android clients
   can exist in the same project.
4. No client ID has to be pasted into the app; Google Play services finds the client by package name
   and signature. Then tap "Link Google Drive" in the app.

Files uploaded by the *old* Monito app are not visible to this app (Drive only shows an app the files
it created itself). Download such a `.monitobk` from Drive and use "Import backup file…" instead.

### Not carried over (yet)

- Donations, feedback mail, rate/share links, app language override (the system locale is used).

## Building

Requirements: JDK 17 or 21 and the Android SDK (`platforms;android-36`, `build-tools;36.0.0`).
`local.properties` must point `sdk.dir` at the SDK.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat assembleDebug          # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat testDebugUnitTest      # unit tests incl. Robolectric database tests
```

Install the debug APK with `adb install app/build/outputs/apk/debug/app-debug.apk` or copy it to the phone.

### Release signing

`.\gradlew.bat assembleRelease` signs with the key described in `keystore.properties` in the project
root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). That file and the keystore are not in
the repository. Without the file the release build falls back to the debug key, and such a build
cannot be installed over a published one.

## Project layout

```
app/src/main/java/com/monito/expense/
  data/        Models, MonitoDatabase (opens MonitoDB.sqlite), DatabaseUpdater (ported version chain),
               Repository (all SQL), Prefs, TimePeriod, AmountFormatter + ExpressionEvaluator,
               BackupCodec + InsecureSHA1PRNGKeyDerivator (.monitobk format), BackupManager, XlsxExporter,
               RecurrenceSchedule
  work/        WorkManager workers: recurrences, auto backup, daily reminder; notification channels
  widget/      Home screen widget
  ui/          Compose screens (main with transactions & graphs tabs, record, categories, recurrences,
               trash, search, settings, backups, first run) and the theme
app/src/test/  JUnit tests for the pure logic and Robolectric tests for the SQL layer
```

## Database schema (unchanged from the original)

```
meta(db_version)                       -- '7.0' when fully upgraded
category(cat_id, cat_name, cat_type 'Expense'|'Income', cat_count, cat_color_id, cat_is_fav)
color(color_id, color_code 'RRGGBB', color_text_type)
main(id, comment, amount text, date 'yyyy-MM-dd', acc_id, cat_id, is_auto_added, img_path)
trash(trash_id, trash_comment, trash_amount, trash_date, trash_cat_id, trash_deleted_on, trash_is_auto_added, trash_img_path)
recurrence(recur_id, recur_amount, recur_note, recur_cat_id, recur_type 1..4, recur_on 'yyyy-MM-dd HH:mm')
donation(...)                          -- kept for compatibility, unused
```

`recur_type`: 1 = daily, 2 = weekly, 3 = monthly, 4 = yearly.

Tables and columns added by this app (ignored by the old app): `budget(cat_id, amount)`,
`template(name, amount, cat_id, comment, sort_order)` and `recurrence.recur_end`.

Time period codes 1–6 match the original app; code 7 (one specific month, reached with the arrows) is new and lives only in this app's preferences.

## License

MIT, see [LICENSE](LICENSE). One file, `InsecureSHA1PRNGKeyDerivator.java`, is taken from the Android Open
Source Project and keeps its Apache License 2.0 header.

The original Monito Expense Manager is the work of Cygnipro Softwares and is not affiliated with this
project. This repository is a rewrite: no code or assets were copied from the original app, but its
database schema, backup file format and colour palette are reproduced so that existing data keeps working.
