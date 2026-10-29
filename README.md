# Writer (Obsidian edition)

Essentially a fork of **Writer** by xeasec (`app.Xeasec.writer`), a minimal Android
writing app that was abandoned by its author and is no longer available. This version
keeps the way Writer looks and works, with the same screens, editor, themes, fonts and
icon, but every note is saved as a Markdown file in your **Obsidian** vault, so it syncs
with the rest of your notes.

Writer's source code was never published, so this is a from-scratch rewrite in Kotlin
and Jetpack Compose rather than a fork of its code. The original app (version 4.7.0) was
decompiled only to study how it behaves and how its backups are stored. No code from it
is included. This project is unofficial and not affiliated with xeasec.

## Install

Download the APK from [Releases](../../releases) and open it on your phone. Android asks
you to allow installs from that source; Samsung phones also need **Auto Blocker** off
while installing. The app installs alongside the original Writer and doesn't change it.

On first launch, choose your vault folder, or a folder inside it. Android asks for
access to that folder only, not your whole storage.

## Features

All of these work the way they do in Writer 4.7.0:

- **Note list.** Folders and notes, a long press to rename or delete, sort by name or date,
  search by title, and Day/Night mode.
- **Editor.**
  - **Toolbar:** Undo, Redo, Editor (shows the formatting bar), Print, Speak (dictation)
    and Menu.
  - **Formatting bar:** Bold, Italic, Text Color (30 colors), Underline, Strikethrough,
    Quote, Bullet, Copy, Top, Bottom, Clear and Select All.
  - **Menu:** Text Top, Text End, Details (word and character counts), Listen (read
    aloud), Print and Share.
  - **Saving:** when you leave a note ("Saved."), when the app goes to the background,
    and 2 seconds after you stop typing. Opening a note without changing it never
    rewrites the file.
- **Customize.** The 7 original themes, 31 text colors and 25 fonts.
- **Settings.** Text size and read-only mode.
- **Print.** One note, or every note in a folder.
- **Backup.** *Back up to memory card* saves a `.zip` of your notes. *Restore* imports a
  backup made by the original Writer app (see below).

### Your notes in Obsidian

Formatting is saved as Markdown that Obsidian displays the same way:

| Writer | Saved as |
|---|---|
| Bold, italic, strikethrough | `**bold**`, `*italic*`, `~~strike~~` |
| Underline | `<u>underline</u>` |
| Text color | `<span style="color:#ff1604">text</span>` |
| Bullet, quote | `- item`, `> quote` |

Anything Writer has no button for, such as headings, `[[wikilinks]]`, tags, tables and
`![](image)` embeds, is shown as plain text and saved back unchanged. Notes you edit
in Obsidian therefore keep everything they had.

Writer only saves files. Syncing is done by whatever syncs your vault. With
**Obsidian Sync**, changes upload the next time you open Obsidian on the phone.

## Moving your notes from the original Writer

1. In the original Writer, go to **☰ → Backup → Back up to memory card → BACKUP**.
   Save the `WriterDb_<date>.db` file it shares, for example to Downloads.
2. In this app, go to **☰ → Backup → RESTORE** and pick that file.

Your notes and folders, including folders inside folders, are copied into a new
**Writer import** folder in your vault. Formatting, colors and image references are
converted to Markdown. Not carried over:

- **Dates.** Android doesn't let apps set a file's date, so notes are written in the
  order you last edited them; *Date* sort keeps the original order.
- **Note passwords.**

Backups from Writer 3.x and 4.x both work.

## Not included

- Inserting pictures (a paid feature in the original)
- Note and app locks
- Full-screen mode and the daily writing screen
- Ads, analytics and in-app purchases, which were removed on purpose

## Building

Requires JDK 17 or newer and the Android SDK (API 36).

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Release builds are signed using a `keystore.properties` file in the project root. That
file is never committed; it holds `storeFile`, `storePassword`, `keyAlias` and
`keyPassword`. Without it, `assembleRelease` produces an unsigned APK.

## Reference: the original app

The original APKs aren't redistributed here. To check that your copy is genuine:

| Version | File | SHA-256 |
|---|---|---|
| 4.7.0 (code 51) | `base.apk` | `798447b3ac503cef165636eb403eebd12b9da5f6ccb699a08ec3ccdfc96ad157` |
| | `split_config.en.apk` | `eba8dd6d926b35cb045c658567fae42560ece8ff9b555056c7616038c6628e85` |
| | `split_config.es.apk` | `c77054ad3fb74743535e515f3a5ee53333e61a55d90ef419737fe39760459adb` |
| | `split_config.xxhdpi.apk` | `b30d0759f63c5e0305d9ceb594591b30a7fafdbc3cdb087530e928b12d4c2907` |
| 3.0.7 (code 35) | universal APK | `f167a53d2588332d82a5a5fc87f47f6215ca440adc6b3a6900bb0ed9ed6ac49e` |

Both are signed by `CN=xeasec`, whose certificate SHA-256 is
`5c453c4bcacbecfb516c532c37e3463198e2c9e84722ee92dea37bbf0683201f`.

Backup format, recovered from the APK (a backup is a copy of the app's SQLite database):

| Table | Contents |
|---|---|
| `Items(id, categoryId, name, detail, datetime, type, status, path)` | `type` 1 = folder, 2 = note; `categoryId` = parent folder (0 = top level); `path` (4.x) = folder chain such as `0/7/12/` |
| `Texts(id, fileId, title, context, datetime, type, status)` | one row per note; `context` is the note as HTML from the [Knife](https://github.com/mthli/Knife) editor, with `<font color>` and `<img>` in 4.x |
| `Customize(fileId, pass, passStatus, …)` | per-note password and styling |
| `Settings`, `Parameters` | theme, font, text color and other options |

## License

The source code is under the [MIT License](LICENSE). The app icon (© xeasec) and the
bundled fonts (OFL 1.1 or Apache 2.0) keep their own terms; see [NOTICE.md](NOTICE.md).
