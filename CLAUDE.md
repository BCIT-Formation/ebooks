# CLAUDE.md — EbookReader

Guidance for AI assistants working on this repository.

---

## Project overview

Android ebook reader app. Kotlin + Jetpack Compose + Material Design 3. Supports EPUB, PDF,
TXT, FB2, CBZ. All data stays on-device — no internet permission, no cloud.

- **Min SDK:** 26 (Android 8.0)
- **Compile SDK:** 34 / **Target SDK:** 34
- **Language:** Kotlin 2.0.0
- **UI:** Jetpack Compose (no XML layouts)
- **Architecture:** MVVM + Repository + Kotlin Flow

---

## Repository layout

```
.github/
  workflows/
    ci.yml                 # Lint + unit tests + debug build (runs on every push/PR)
    pr-check.yml           # PR title validation + required checks before merge
    auto-merge.yml         # Enables squash auto-merge when all checks pass
    auto-merge-all-prs.yml # Manual/scheduled: enable auto-merge on every open PR to main
    auto-release.yml       # push to main → semver tag → signed APK → GitHub Release
    release.yml            # Manual one-off release (workflow_dispatch)
    build-apk-multi.yml    # Manual multi-method APK build with fallbacks
    security.yml           # Weekly dependency audit + secret scan
  dependabot.yml           # Dependency + GitHub Action update PRs
  CODEOWNERS, PULL_REQUEST_TEMPLATE.md

app/src/main/java/com/ebooks/reader/
  MainActivity.kt           # Single activity. Compose NavHost (routes below)
  data/
    db/
      AppDatabase.kt        # Room singleton, version 2, exportSchema=false, MIGRATION_1_2
      BookDao.kt            # All queries (Flow-returning and suspend)
      Converters.kt         # Room TypeConverters (e.g. enums ↔ String)
      entities/
        Book.kt             # @Entity "books" + ReadingStatus + FileType enums
        Bookmark.kt         # @Entity "bookmarks"
        ReadingProgress.kt  # @Entity "reading_progress" (chapterIndex, scrollPosition, …)
        ReadingSession.kt   # @Entity "reading_sessions" (reading-time stats, FK → books)
    parser/
      EpubBook.kt           # EpubBook, EpubChapter, TocItem, ManifestItem, SpineItem
      EpubParser.kt         # Pure-Kotlin EPUB parser + ReaderTheme data class
      Fb2Parser.kt          # Pure-Kotlin FB2 (FictionBook 2.0) parser → HTML
    repository/
      BookRepository.kt     # Single source of truth. Wraps DAO + parsers. ImportResult sealed class
  ui/
    components/
      BookCard.kt
      ChapterPanel.kt
      ReaderSettingsSheet.kt
    screens/
      LibraryScreen.kt
      ReaderScreen.kt        # EPUB reader (WebView)
      PdfReaderScreen.kt     # PDF reader (PdfRenderer)
      TxtReaderScreen.kt     # Plain-text reader (Compose)
      Fb2ReaderScreen.kt     # FB2 reader (WebView, HTML from Fb2Parser)
    theme/
      Color.kt
      Theme.kt
      Type.kt
  viewmodel/
    LibraryViewModel.kt     # SortOrder, ViewMode, LibraryUiState, ImportState sealed class
    ReaderViewModel.kt

app/src/test/java/com/ebooks/reader/        # JVM unit tests (no emulator)
  EpubParserTest.kt         # ReaderTheme presets
  LibraryViewModelTest.kt   # filtering/sorting logic

app/src/androidTest/java/com/ebooks/reader/ # Instrumented tests (emulator/device)
  data/db/AppDatabaseTest.kt          # Room + MIGRATION_1_2
  data/parser/EpubParserTest.kt       # parser against real/malformed EPUBs
  ui/screens/LibraryScreenTest.kt     # Compose UI
  ui/screens/ReaderScreenTest.kt      # Compose UI

gradle/
  libs.versions.toml        # Version catalog — single source for all dependency versions
  wrapper/
    gradle-wrapper.jar      # Committed to repo (required for CI)
    gradle-wrapper.properties  # gradle-8.7-bin

scripts/
  build-apk-docker.sh       # Dockerised release APK build (recommended)
  build-apk.sh              # Local release APK build
  apk-manager.sh            # APK helper utilities
  auto-merge-prs.sh         # Local helper mirroring auto-merge-all-prs.yml

DECISIONS.md   # Architecture Decision Records (ADR-001 through ADR-005)
TODO.md        # Prioritised backlog (🔴 critical / 🟠 important / 🟢 nice-to-have)
SECURITY.md    # Security policy
README.md      # User/developer overview
APK_BUILD.md   # Detailed APK build instructions
RELEASES.md    # Release process and history
Dockerfile     # eclipse-temurin:17 + Android SDK 34 build image
setup.sh       # One-shot build+install. BUILD=auto|docker|local (auto-falls back to local Gradle)
```

---

## Build commands

### One-shot (`setup.sh`)

```bash
./setup.sh                     # Build v1.0.0 + auto-install if a device is connected
./setup.sh 1.2.3               # Build v1.2.3
./setup.sh 1.2.3 42            # Build v1.2.3, code=42
BUILD=local  ./setup.sh 1.2.3  # Force a local Gradle build (no Docker)
BUILD=docker ./setup.sh 1.2.3  # Force a Docker build (no local SDK)
DEBUG=1      ./setup.sh 1.2.3  # Verbose build output + diagnostics
```

`setup.sh` picks the build method via `BUILD` (`auto` default): Docker when its
daemon is running, otherwise a **local Gradle build** (`assembleDebug`). It does
not silently dead-end when Docker is missing. Local builds locate the Android SDK
via `ANDROID_HOME`/`ANDROID_SDK_ROOT`, `local.properties` (`sdk.dir`), or common
install paths, and need JDK 17+. The APK is copied to `~/.ebooks-apk/`.

### Docker (Recommended)

```bash
# Build release APK in isolated Docker environment
./scripts/build-apk-docker.sh              # default: v1.0.0
./scripts/build-apk-docker.sh 1.2.3        # custom version: v1.2.3
./scripts/build-apk-docker.sh 1.2.3 42     # with code: v1.2.3, code=42
```

**Advantages:** No SDK installation needed, reproducible, matches CI exactly, works on any OS.

### Local Gradle

```bash
# Debug APK — app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# Release APK (unsigned unless signing props are set)
./gradlew assembleRelease

# Unit tests (JVM)
./gradlew test

# Instrumented tests (requires a connected device/emulator)
./gradlew connectedAndroidTest

# Lint (produces SARIF + HTML in app/build/reports/)
./gradlew lint

# All at once
./gradlew lint test assembleDebug
```

**Advantages:** Faster incremental builds, better IDE integration, local debugging.

`VERSION_NAME` / `VERSION_CODE` are read from env vars or `-P` properties; they fall back to
`1.0.0` / `1` for local dev (see `app/build.gradle.kts`). The `debug` build type uses an
`applicationIdSuffix = ".debug"` so it installs alongside release.

---

Gradle 8.7 is pinned via the wrapper. **Do not upgrade** without updating
`gradle-wrapper.properties` and verifying AGP compatibility.

Docker builds use `eclipse-temurin:17` base image with Android SDK 34 pre-installed.

---

## Key dependencies (from `gradle/libs.versions.toml`)

| Library | Version | Purpose |
|---------|---------|---------|
| AGP | 8.4.2 | Android Gradle Plugin |
| Kotlin | 2.0.0 | Language + compose compiler |
| KSP | 2.0.0-1.0.22 | Annotation processor for Room |
| Compose BOM | 2024.08.00 | Compose library versions |
| Material Icons Extended | 1.6.8 | Extended icon set |
| Room | 2.6.1 | Local SQLite database |
| Coil | 2.7.0 | Compose-native image loading |
| Navigation Compose | 2.8.0 | In-app navigation |
| Coroutines Test | 1.8.1 | Unit test utilities |
| Compose UI Test | 1.6.8 | Instrumented Compose UI tests |
| Espresso / AndroidX JUnit | 3.6.1 / 1.2.1 | Instrumented test runner |

**Dependency updates go in `gradle/libs.versions.toml` only.** Never hardcode versions
in `build.gradle.kts`. Dependabot opens update PRs automatically.

---

## Architecture conventions

### MVVM + Repository

```
UI (Screen) → ViewModel → Repository → DAO / Parser
                ↑ StateFlow/SharedFlow
```

- **ViewModels** hold `StateFlow<UiState>` and expose intent functions (`setSortOrder`, `importBook`, …).
- **Repository** is the only layer that talks to Room or the parsers.
- **Screens** are stateless Compose functions — they observe ViewModel state and emit events upward.
- No direct DAO calls from UI or ViewModel.

### State modelling

- Use `data class` for UI state (e.g. `LibraryUiState`).
- Use `sealed class` for async/one-shot states (e.g. `ImportState`, `BookRepository.ImportResult`).
- Combine multiple flows with `combine { }` rather than nested `flatMapLatest`.
  Stay within the 5-parameter typed overload; group flows into intermediate data classes if needed
  (see `LibraryViewModel.FilterState`).

### Typed import outcomes

Import does not throw or return null — it returns `BookRepository.ImportResult`:

- `Success(book)` — imported (or already present and refreshed)
- `AlreadyExists(book)` — same file path already in the library
- `UnsupportedFormat(extension)` — extension not in `FileType`
- `Unreadable(fileName)` — URI could not be opened/read
- `ParseFailed(fileName)` — a parser returned null

`LibraryViewModel` maps these to `ImportState` (`Idle`/`Loading`/`Success`/`AlreadyExists`/`Error`)
with user-facing messages. Preserve these distinctions — do not collapse them into a generic
success/failure boolean.

### Navigation

Routes are plain strings in `MainActivity.kt`:
- `"library"` — `LibraryScreen`
- `"reader/{bookId}"` — `ReaderScreen` (EPUB)
- `"pdf_reader/{bookId}"` — `PdfReaderScreen`
- `"txt_reader/{bookId}"` — `TxtReaderScreen`
- `"fb2_reader/{bookId}"` — `Fb2ReaderScreen`

Do not add a navigation graph file. Keep navigation simple and co-located in `MainActivity`.

---

## Architecture decisions (do not reverse without discussion)

| ADR | Decision |
|-----|----------|
| ADR-001 | No external EPUB library. Parser is pure Kotlin using `ZipInputStream` + `XmlPullParser`. |
| ADR-002 | EPUB chapters rendered in `WebView`. CSS injected via `ReaderTheme`. |
| ADR-003 | Room (SQLite) for all persistence — books, bookmarks, reading progress, sessions. |
| ADR-004 | Jetpack Compose only — no XML layouts. |
| ADR-005 | Coil 2 for cover image loading. |

See `DECISIONS.md` for full context and trade-offs. FB2 follows ADR-001's pure-Kotlin approach
(`Fb2Parser` converts the FB2 body to HTML, then renders it in a WebView like EPUB).

---

## Room database

- **DB name:** `ebook_reader.db`
- **Version:** 2
- **Tables:** `books`, `reading_progress`, `bookmarks`, `reading_sessions`
- `exportSchema = false` — schema JSONs are **not** generated. (Was `true`, but no schemas
  were ever committed and the debug/release KSP passes collided on `$projectDir/schemas`,
  failing the build with "Empty schema file". Re-enabling export needs per-variant schema
  dirs or the AndroidX Room Gradle plugin.)
- **Migrations:** `MIGRATION_1_2` in `AppDatabase` adds the `reading_sessions` table (v1 → v2).
  When you bump the DB version you MUST add a corresponding `Migration` object (and ideally a
  migration test).
- `fallbackToDestructiveMigration()` is configured as a *safety net only* so an un-migrated
  schema bump wipes rather than crashes. **Do not rely on it** — always write a real `Migration`.
  Never remove the explicit migrations in favour of destructive fallback.
- `reading_sessions` has a `CASCADE` foreign key to `books`; deleting a book removes its sessions.

---

## Parsers

### EPUB (`EpubParser.kt`)

- Handles EPUB 2 (NCX TOC) and EPUB 3 (nav document).
- Inlines images as base64 data URIs so WebView can display them without file access.
- Strips external stylesheets and injects `ReaderTheme` CSS.
- `ReaderTheme` has four presets: `LIGHT`, `DARK`, `SEPIA`, `NIGHT`.
  Add new presets in the companion object only — do not change existing color values
  without updating tests in `EpubParserTest`.
- The parser returns `null` on any error (uses `runCatching`). Callers must handle null
  (the repository maps null to `ImportResult.ParseFailed`).

### FB2 (`Fb2Parser.kt`)

- Pure-Kotlin `XmlPullParser` single-pass parse → extracts title, author, description,
  base64 cover, and an HTML body string for the WebView.
- Returns `null` on error, same boundary contract as the EPUB parser.

---

## Testing

```bash
./gradlew test                  # JVM unit tests
./gradlew test --info           # verbose output
./gradlew connectedAndroidTest  # instrumented tests (needs device/emulator)
```

- **Unit tests** (`app/src/test/`) are pure JVM — no emulator needed.
- **Instrumented tests** (`app/src/androidTest/`) now exist: Room migration/DB tests,
  parser tests against real EPUBs, and Compose UI tests for Library/Reader screens.
- The unit test source still references `android.*` through the main source tree;
  the Android SDK stubs (`android.jar`) are resolved by Gradle from `$ANDROID_HOME`.
  **All CI jobs that run Gradle include `setup-android`** to ensure the SDK is present.
- Do not add `@Ignore`-d tests to pass CI. Fix or delete flaky tests.

---

## CI/CD

### Workflows and triggers

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `ci.yml` | push to `master`/`main`/`develop`/`claude/**`; PR to `master`/`main`/`develop` | `lint`, `test`, `build-debug` (parallel) |
| `pr-check.yml` | PR to `main` or `develop` | `validate-title`, `required-checks` |
| `auto-merge.yml` | PR opened/updated/ready for review → `main` | `enable-auto-merge` |
| `auto-merge-all-prs.yml` | manual `workflow_dispatch` (supports dry-run) | enable auto-merge on all open PRs |
| `auto-release.yml` | push to `main`; manual | `release` (tag → build → publish) |
| `release.yml` | manual `workflow_dispatch` | `build-release` |
| `build-apk-multi.yml` | manual `workflow_dispatch` | multi-method APK build with fallbacks |
| `security.yml` | every Monday 08:00 UTC; push to `main` | `dependency-audit`, `secret-scan` |

### PR title — conventional commits required

`pr-check.yml` enforces the conventional commits format via `amannn/action-semantic-pull-request@v6`.

Valid prefixes: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `build`, `perf`

```
feat: add night mode toggle
fix(epub): handle missing OPF rootfile
ci: add setup-android to lint job
```

**PRs with branch-name-derived titles (e.g. "Claude/android ebook reader …") will fail.**
Always set an explicit conventional-commit title when opening a PR.

### Auto-release flow (on push to `main`)

1. `mathieudutour/github-tag-action@v6.2` reads commit messages since the last tag and bumps semver.
2. If a new tag is produced, builds a signed (or unsigned fallback) release APK.
3. Publishes a GitHub Release with the APK attached.

Bump rules:
- `feat!:` / `BREAKING CHANGE` → major
- `feat:` → minor
- `fix:`, `perf:`, `refactor:` → patch
- `chore:`, `docs:`, `ci:`, `style:` → patch

### Action versions in use

All workflows use these pinned versions — **keep them consistent across every workflow**:

| Action | Version |
|--------|---------|
| `actions/checkout` | `@v6` |
| `actions/setup-java` | `@v5` |
| `actions/cache` | `@v5` |
| `actions/upload-artifact` | `@v7` |
| `actions/download-artifact` | `@v8` |
| `android-actions/setup-android` | `@v4` |
| `github/codeql-action/upload-sarif` | `@v4` |
| `softprops/action-gh-release` | `@v3` |
| `amannn/action-semantic-pull-request` | `@v6` |
| `mathieudutour/github-tag-action` | `@v6.2` |

**Do not bump an action in one workflow only.** Dependabot proposes action bumps; when applying
one, update every occurrence across all workflows in the same change so versions stay uniform.

---

## Commit convention

```
<type>(<optional scope>): <short description>

feat:     new user-facing feature
fix:      bug fix
docs:     documentation only
style:    formatting, no logic change
refactor: restructure without behavior change
test:     add/update tests
ci:       CI/CD configuration
chore:    build system, dependency updates
build:    build scripts
perf:     performance improvement
```

Scope examples: `epub`, `fb2`, `pdf`, `db`, `ui`, `reader`, `library`, `ci`.

---

## Security

- **No internet permission.** The app declares only read-storage/media permissions
  (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`). Do not add `INTERNET`.
- No secrets in source code. CI scans for patterns like `password = "..."` in `*.kt` / `*.xml`.
- Signing secrets (`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
  `SIGNING_KEY_PASSWORD`) live in GitHub repository secrets only.
- `*.jks`, `*.keystore`, `*.p12`, `keystore.properties` are gitignored.
- WebView debugging is gated: `WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)` —
  keep it tied to `BuildConfig.DEBUG`, never hard-code `true`.
- Picked file URIs: `BookRepository` calls `takePersistableUriPermission` on import so files
  remain accessible across restarts. Keep this when changing the import path.

---

## Status / active backlog

Most former 🔴 critical items are now resolved (persistable URI permission, debug-only WebView
debugging, IOException-safe typed imports, DB migration). Current notable open items
(see `TODO.md` for the full prioritised list):

| Priority | Item |
|----------|------|
| 🟠 | In-book text search (JS highlight in WebView) |
| 🟠 | Auto-scroll (JS `window.scrollBy` loop) |
| 🟠 | Cover image rebuild from existing books |
| 🟢 | Bookshelf 3D view mode, TTS, CBZ/CBR reader, custom fonts, widgets |

Do not paper over genuine gaps with workarounds — implement or file them in `TODO.md`.

---

## Common mistakes to avoid

- **Do not add dependencies outside `libs.versions.toml`.**
- **Do not add the `INTERNET` permission** or any network/cloud code — the app is fully offline.
- **Do not use XML layouts.** All UI is Compose.
- **Do not call DAO directly from a ViewModel.** Always go through `BookRepository`.
- **Do not catch and swallow exceptions silently** in business logic — `runCatching` is used
  in the parsers as a deliberate boundary; elsewhere, propagate errors to UI state via the
  typed `ImportResult`/`ImportState`.
- **Do not bump Room's DB version** without adding a corresponding `Migration` (exportSchema is
  currently off, so there is no auto schema test to rely on).
  Do not lean on `fallbackToDestructiveMigration` — it is only a crash safety net.
- **Do not commit `*.jks` / `*.keystore` files.** The `.gitignore` prevents it, but verify.
- **Do not bump a GitHub Action in isolation** — update every occurrence across all workflows at once.
