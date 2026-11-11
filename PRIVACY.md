# Privacy Policy — Monito

_Last updated: 11 November 2026_

Monito is a personal expense manager for Android. It is designed so that your data stays with you.

## What the app stores and where

- Your transactions, categories, budgets, recurring rules, templates and settings are stored in a
  database on your device, in the app's private storage. Photos you attach to transactions are stored
  in the app's own folder on your device.
- Local backups you create (manually or with the daily automatic backup) are written to the app's
  folder on your device storage (`Android/data/com.monito.expense/files/backups`).
- The developer does not run any server. The app does not send your data anywhere unless you link
  Google Drive (below). There are no ads, no analytics and no tracking.

## Google Drive (optional)

If you choose to link Google Drive:

- The app asks Google for permission to **see, create and delete only the files it creates itself in
  your Drive** (`drive.file` scope) and for your **email address**, which is shown in the app so you
  know which account is linked.
- What is uploaded is a copy of your whole database, **encrypted on your device with a passphrase you
  choose** (AES-256-GCM, key derived with PBKDF2-HMAC-SHA256). The passphrase never leaves the
  device; it is stored there protected by the Android Keystore. Neither Google nor the developer can
  read the uploaded copies.
- Copies are stored in the `Monito/backups` folder of your own Google Drive and are subject to
  Google's terms and privacy policy. You can delete them from the app or directly in Drive at any
  time, and you can unlink the account in Settings → Google Drive → Unlink.
- Monito's use and transfer to any other app of information received from Google APIs adheres to the
  [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy),
  including the Limited Use requirements.

## Permissions

- **Camera / photos**: only when you attach a photo to a transaction.
- **Notifications**: for the optional daily reminder and for recurring transactions that were added
  automatically.
- **Biometrics / screen lock**: only if you enable the app lock; the check is done by Android, the
  app never sees your fingerprint or PIN.
- **Internet**: used only for Google Drive when linked.

## Data sharing

The developer does not collect, receive, sell or share any personal data. The only third party that
receives data is Google, and only when you link Google Drive, as described above.

## Children

The app is not directed at children under 13.

## Changes

Changes to this policy are published in this repository with an updated date.

## Contact

Questions can be raised as an issue on <https://github.com/doonsMonk/Monito>.
