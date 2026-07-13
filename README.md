# EbookReader

[![CI](https://github.com/BardinConsulting/ebooks/actions/workflows/ci.yml/badge.svg)](https://github.com/BardinConsulting/ebooks/actions/workflows/ci.yml)
[![Security](https://github.com/BardinConsulting/ebooks/actions/workflows/security.yml/badge.svg)](https://github.com/BardinConsulting/ebooks/actions/workflows/security.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A clean, fast, and fully-featured Android ebook reader built with **Jetpack Compose** and **Material Design 3**. Supports EPUB, PDF, TXT, FB2 and CBZ files. Local-first: your data stays on your device, with optional user-initiated sync (cloud folder / WebDAV) and OPDS catalog downloads — no accounts, no telemetry.

---

## Features

| Feature | Status |
|---------|--------|
| EPUB 2 & 3 support | ✅ |
| PDF reading | ✅ |
| TXT reading | ✅ |
| FB2 reading | ✅ |
| CBZ comics | ✅ |
| Library with sort & filter | ✅ |
| Grid / List / 3D Bookshelf view | ✅ |
| Display modes: LCD / AMOLED / E-reader (high contrast) | ✅ |
| Day / Dark / Sepia / Night reading themes | ✅ |
| Adjustable font size & line spacing | ✅ |
| Custom fonts (import your own TTF/OTF) | ✅ |
| Chapter navigation | ✅ |
| Bookmarks with notes | ✅ |
| In-book text search | ✅ |
| Reading progress sync | ✅ |
| Reading statistics | ✅ |
| Auto-scroll (with sleep timer) & tilt-to-scroll | ✅ |
| Text-to-speech (read aloud) | ✅ |
| Share book excerpts | ✅ |
| Home screen widget (currently reading) | ✅ |
| Per-app language (Android 13+, en/fr) | ✅ |
| OPDS catalogs (browse & download books) | ✅ |
| Progress sync via cloud folder (Google Drive / OneDrive) | ✅ |
| WebDAV (browse books + progress sync) | ✅ |
| Material You dynamic colors | ✅ |
| Offline-first — network only for opt-in sync/catalogs, HTTPS-only, no telemetry | ✅ |

---

## Screenshots

> See `WhatsApp Image *.jpeg` files in the repo root for the original design reference.

---

## Architecture

```
app/
└── src/main/java/com/ebooks/reader/
    ├── data/
    │   ├── db/              # Room database, DAO, entities
    │   ├── parser/          # EPUB parser (zero external deps)
    │   └── repository/      # BookRepository (single source of truth)
    ├── ui/
    │   ├── screens/         # LibraryScreen, ReaderScreen
    │   ├── components/      # BookCard, ChapterPanel, SettingsSheet
    │   └── theme/           # Material3 theme, colors, typography
    ├── viewmodel/           # LibraryViewModel, ReaderViewModel
    └── MainActivity.kt      # Single-activity, Compose NavHost
```

**Pattern:** MVVM + Repository + Flow
**UI:** Jetpack Compose + Material Design 3
**DB:** Room (SQLite)
**Images:** Coil 2

---

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34
- A device or emulator with API 26+

---

## Installation

### 1. Clone

```bash
git clone https://github.com/BardinConsulting/ebooks.git
cd ebooks
```

### 2. Quick Start (One-Shot)

Build and install the APK automatically — no questions asked:

```bash
./setup.sh                 # Build v1.0.0, auto-install if device connected
./setup.sh 1.2.3           # Build v1.2.3
./setup.sh 1.2.3 42        # Build v1.2.3 with code=42
```

**What it does:**
- ✅ Builds the APK with **Docker** when the daemon is running, otherwise falls
  back to a **local Gradle build** automatically (no Docker required)
- ✅ Copies the APK to `~/.ebooks-apk/` (persistent folder)
- ✅ Auto-installs on a connected Android device (if `adb` is available)
- ✅ Shows the ready-to-install APK path
- ✅ Zero interaction

**Choosing the build method:**

`setup.sh` auto-detects the best method. To force one, set the `BUILD` variable:

```bash
BUILD=docker ./setup.sh 1.2.3   # Reproducible Docker build (no local SDK needed)
BUILD=local  ./setup.sh 1.2.3   # Local Gradle build (no Docker needed)
BUILD=auto   ./setup.sh 1.2.3   # Default: Docker if available, else local
```

A **local build** needs JDK 17+ and an Android SDK. The script finds the SDK via
`ANDROID_HOME` / `ANDROID_SDK_ROOT`, `local.properties` (`sdk.dir=…`), or common
install locations (e.g. `~/Android/Sdk`, `~/Library/Android/sdk`). If none is
found it prints exactly how to set one. A **Docker build** needs no local SDK.

**Debugging (if APK not found):**

```bash
DEBUG=1 ./setup.sh 1.2.3   # Show full build output & timestamps
```

Shows detailed progress with timestamps, full build output, and a file listing if the build fails.

### 3a. Build with Docker (Manual Control)

Build a release APK using Docker — works on any system with Docker installed.

```bash
# Debug APK
./scripts/build-apk-docker.sh

# Release APK with custom version
./scripts/build-apk-docker.sh 1.2.3

# Release APK with version and code
./scripts/build-apk-docker.sh 1.2.3 42
```

The APK will be in `./release/`.

**Advantages:**
- ✅ No local Android SDK installation needed
- ✅ Matches CI/CD environment exactly
- ✅ Reproducible builds across machines
- ✅ Clean isolation (no system contamination)

### 3b. Build Locally (without Docker)

#### Step 3b-i: Generate Gradle wrapper (first time only)

```bash
# If you have Gradle 8.7 installed globally:
gradle wrapper

# Or use Android Studio's "Sync Project" button
```

#### Step 3b-ii: Build debug APK

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Install on Device (Manual)

```bash
# From setup.sh (Docker build → release APK)
adb install ~/.ebooks-apk/EbookReader-v1.0.0.apk

# From setup.sh (local Gradle build → debug APK)
adb install ~/.ebooks-apk/EbookReader-v1.0.0-debug.apk

# From Docker build
adb install ./release/EbookReader-v1.0.0.apk

# From local build
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open in Android Studio and click **Run**.

---

## Usage

1. **Add books** — tap the **+** button, select an EPUB, PDF, or TXT file
2. **Open a book** — tap any book cover in the library
3. **Navigate** — tap the left/right edge to turn chapters, or tap center for controls
4. **Customize** — tap the **Aa** icon in the reader toolbar to change font, size, and theme
5. **Chapters** — tap the list icon to open the chapter/bookmark panel
6. **Bookmark** — tap the bookmark icon to save your current position

---

## Environment Variables

No environment variables are needed for basic development.
For signed release builds, see `.env.example`.

| Variable | Description | Required |
|----------|-------------|----------|
| `SIGNING_KEY_ALIAS` | Keystore alias | Release builds |
| `SIGNING_KEY_PASSWORD` | Key password | Release builds |
| `SIGNING_STORE_PASSWORD` | Keystore password | Release builds |

---

## Branch Protection (configure manually on GitHub)

Go to **Settings → Branches** and add rules for:

| Branch | Rules |
|--------|-------|
| `main` | Require PR, require CI green, no force push, require linear history |
| `develop` | Require PR, require CI green |

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feat/my-feature`
3. Commit with Conventional Commits: `git commit -m "feat: add night mode"`
4. Push and open a PR against `develop`
5. Ensure all CI checks pass

### Commit Convention

```
feat:     new feature
fix:      bug fix
docs:     documentation only
refactor: code restructure without behavior change
test:     adding/updating tests
ci:       CI/CD configuration
chore:    build system, dependency updates
```

---

## Proposed Improvements

See [TODO.md](TODO.md) for the full prioritized backlog, organized by:

- 🔴 **Critical** — essential fixes and features (all done)
- 🟠 **Important** — next-tier enhancements (all done)
- 🟢 **Nice to Have** — polish and advanced features (remaining: full localization coverage, CBR + pinch-to-zoom)
- 🌐 **Network follow-ups** — FTPS / SFTP / SMB shares and native Drive/OneDrive API sync (see ADR-006)
