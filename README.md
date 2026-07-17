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
| Dictionary &amp; translate on selected text | ✅ |
| Collections / tags &amp; series grouping | ✅ |
| Full backup &amp; restore (.zip) | ✅ |
| Chapter navigation | ✅ |
| Bookmarks with notes | ✅ |
| Text highlights + notes, export to Markdown | ✅ |
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
| RSS reader tab (OPML import/export, offline articles) | ✅ |
| Annotate & share RSS articles (same drawing tools) | ✅ |
| Material You dynamic colors | ✅ |
| Offline dictionary support (StarDict format) | ✅ |
| Enhanced sharing: ebook file + annotations (markdown) | ✅ |
| RSS article export to markdown with annotations | ✅ |
| Bionic Reading algorithm (auto-bold word fragments) | ✅ |
| E-Ink display mode (e-readers: Boox, Kobo; volume nav, no animations) | ✅ |
| Smooth scrolling optimization (LazyColumn, BookshelfView) | ✅ |
| Offline-first — network only for opt-in sync/catalogs, HTTPS-only, no telemetry | ✅ |
| RSS reader tab (OPML import/export, full-text extraction) | 🚧 |
| Biometric App Lock & Encrypted credential storage | 🚧 |

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

## Play Store Publication & Release Automation

### Prerequisites

1. **Google Play Developer Account** ($25 one-time fee)
   - Create account at [Google Play Console](https://play.google.com/console)
   - Accept Developer Agreement & Policies

2. **Signing Configuration**
   - Generate a release signing key:
   ```bash
   keytool -genkey -v -keystore ebooks-release.jks -keyalg RSA -keysize 2048 -validity 10000 \
     -alias ebooks-release -storepass <STORE_PASSWORD> -keypass <KEY_PASSWORD>
   ```
   - Store `ebooks-release.jks` securely (not in repo)

3. **GitHub Secrets** (Settings → Secrets and variables → Actions)
   ```
   SIGNING_KEYSTORE_BASE64    # Base64-encoded .jks file
   SIGNING_STORE_PASSWORD     # Keystore password
   SIGNING_KEY_ALIAS          # Key alias (e.g., ebooks-release)
   SIGNING_KEY_PASSWORD       # Key password
   PLAY_STORE_SERVICE_ACCOUNT # Google Play Service Account JSON (base64)
   ```

### Step 1: Create App on Google Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Click **Create app**
3. **App name:** EbookReader
4. **Default language:** English
5. **App or game:** App
6. **Category:** Books & Reference
7. **Accept policies** and create

### Step 2: Set Up App Listing

1. **App info**
   - Screenshots (min 2-8 per device type)
   - Short description (80 chars max)
   - Full description
   - Privacy policy URL
   - Support email

2. **Content rating**
   - Fill questionnaire
   - Get rating

3. **Target audience**
   - Everyone

4. **Content**
   - Privacy policy: Link to GitHub
   - Target Android version: 34

### Step 3: Set Up Google Play Service Account

1. Go to **Settings → API access**
2. Create Service Account
3. Grant **Admin (all permissions)**
4. Create JSON key
5. Encode key as base64:
   ```bash
   base64 service-account.json | tr -d '\n' | pbcopy
   ```
6. Paste into GitHub **PLAY_STORE_SERVICE_ACCOUNT** secret

### Step 4: Automated Release via GitHub PR

The release process is **fully automated** via GitHub Actions:

1. **Create a PR** with your changes (e.g., new features, bug fixes)
2. **Set conventional commit title** (e.g., `feat: add offline dictionary`)
3. **Merge to `main`** once CI is green
4. **GitHub Actions triggers auto-release workflow:**
   - Bumps version (semver based on commit message)
   - Creates git tag
   - Builds **signed release APK**
   - Publishes to **Google Play Console** (alpha track by default)
   - Creates **GitHub Release** with APK attached

**Bump rules:**
- `feat!:` or `BREAKING CHANGE` → major version
- `feat:` → minor version
- `fix:`, `perf:`, `refactor:` → patch version
- `chore:`, `docs:`, `ci:`, `style:` → patch version (skipped if no version bump needed)

### Step 5: Manual Release (if needed)

If you need to trigger a release manually:

```bash
# Using GitHub CLI
gh workflow run auto-release.yml

# Or via web: Actions → auto-release.yml → Run workflow
```

### Step 6: Testing Track

Before going public:

1. Go to **Google Play Console → Testing → Internal testing**
2. Add testers (Google accounts)
3. Deploy APK to internal track
4. Send testers link to [Internal testing URL](https://play.google.com/apps/internaltest/YOUR_APP_ID)
5. Collect feedback
6. Fix issues
7. Deploy to **Beta track** for wider testing
8. Finally, **Production track** for all users

### Step 7: Monitor Analytics

Once live, monitor:

1. **Statistics → Overview** — downloads, crash rates, ratings
2. **Vitals** — ANRs, crashes, warnings
3. **Reviews** — User feedback
4. **Ratings** — 1-5 star distribution

### Post-Release Checklist

- [ ] GitHub Release created with APK attached
- [ ] Play Store listing has screenshots & description
- [ ] Privacy policy linked
- [ ] App rating obtained
- [ ] Testers report no critical bugs
- [ ] Analytics showing normal crash rates (<1%)
- [ ] Reviews being monitored
- [ ] Push notification system (if applicable) tested

### Troubleshooting

**"Build failed due to signing"**
- Verify `SIGNING_KEYSTORE_BASE64` is properly base64-encoded
- Check passwords match keystore generation

**"Upload failed: Invalid APK"**
- Ensure `versionCode` is higher than previous release
- Check for `debuggable=true` in manifest (should be false)

**"Permission denied for Play Store"**
- Verify Service Account has **Admin** role
- Confirm JSON key is less than 90 days old

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
- ✨ **Advanced Features** — offline dictionary, bionic reading, E-Ink mode (all done)
- 📰 **RSS Client** — Room modeling, OPML import/export, WorkManager sync, and Readability.js full-text extraction
- 🔒 **Security & Privacy** — EncryptedSharedPreferences, Biometric app lock, and WebView tracker blocking
- 🌐 **Network follow-ups** — FTPS / SFTP / SMB shares and native Drive/OneDrive API sync (see ADR-006)
