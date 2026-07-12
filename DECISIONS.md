# Architecture Decision Records

## ADR-001: No External EPUB Parsing Library

**Status:** Accepted
**Date:** 2026-03-03

### Context
EPUB parsing is required. Libraries like `epublib` exist but add 300KB+ to APK size and bring transitive dependencies.

### Decision
Implement a pure-Kotlin EPUB parser using:
- `java.util.zip.ZipInputStream` (built into Android)
- `org.xmlpull.v1.XmlPullParser` (built into Android)
- `android.util.Base64` (built into Android)

### Consequences
- ✅ Zero extra dependencies
- ✅ Full control over parsing behavior
- ✅ Handles EPUB 2 and EPUB 3
- ⚠️ Does not handle all edge cases of complex EPUBs (addressed in TODO)

---

## ADR-002: WebView for EPUB Rendering

**Status:** Accepted
**Date:** 2026-03-03

### Context
EPUB chapters are HTML+CSS files. Rendering them faithfully requires understanding HTML/CSS.

### Decision
Use Android's `WebView` to render chapter HTML. Inject reader-specific CSS (colors, fonts, sizes) before loading.

### Consequences
- ✅ Faithful rendering of book formatting
- ✅ Images, tables, lists work out of the box
- ✅ CSS-based theming
- ⚠️ WebView is heavier than a custom Text renderer
- ⚠️ No built-in page-turn animation (addressed by tap zones)

---

## ADR-003: Room for Local Storage

**Status:** Accepted
**Date:** 2026-03-03

### Context
Need persistent storage for book metadata, reading progress, and bookmarks.

### Decision
Use Room (SQLite wrapper from Jetpack) as the single source of truth.

### Consequences
- ✅ Type-safe queries
- ✅ Coroutines/Flow integration
- ✅ Schema migrations supported
- ✅ Industry-standard for Android apps

---

## ADR-004: Jetpack Compose UI

**Status:** Accepted
**Date:** 2026-03-03

### Context
Modern Android UI toolkit choice.

### Decision
Use Jetpack Compose with Material Design 3. No XML layouts.

### Consequences
- ✅ Declarative, reactive UI
- ✅ Less boilerplate than XML
- ✅ Material You dynamic colors support
- ⚠️ Requires API 26+ (minSdk set accordingly)

---

## ADR-005: Coil for Image Loading

**Status:** Accepted
**Date:** 2026-03-03

### Context
Book cover images need to be loaded efficiently with caching.

### Decision
Use Coil 2.x — the standard Compose-first image loader for Android.

### Consequences
- ✅ Compose-native API
- ✅ Memory and disk caching built-in
- ✅ Coroutines-based
- Small APK footprint compared to Glide or Picasso

---

## ADR-006: Network Access for User-Initiated Sync and Catalogs

**Status:** Accepted (supersedes the "no INTERNET permission" rule)
**Date:** 2026-07-12

### Context
The app originally shipped without the `INTERNET` permission as a privacy guarantee.
The repository owner has explicitly approved network access for a specific set of
user-facing features: cloud sync of reading progress (Google Drive / OneDrive),
OPDS catalog browsing/downloading, and network file servers (WebDAV, and in the
future FTPS / SFTP / SMB).

### Decision
Add the `INTERNET` permission, constrained by these rules:

1. **User-initiated only.** The app performs a network request only as the direct
   result of a user action (open a catalog, tap download, tap sync). No background
   polling, no telemetry, no analytics, no update checks.
2. **Encrypted transports only.** Cleartext traffic stays disabled
   (`usesCleartextTraffic` remains at its API 28+ default of `false`), so
   `http://` URLs are rejected by the platform. WebDAV/OPDS require `https://`;
   future FTP support must be FTPS, and remote shell must be SFTP.
3. **No embedded cloud SDKs.** Google Drive / OneDrive sync goes through the
   Storage Access Framework (the user picks a folder exposed by the Drive/OneDrive
   document provider) rather than vendor SDKs — no OAuth client secrets in the app,
   no vendor telemetry. Native API integrations would require the owner to register
   OAuth client IDs and are deliberately out of scope.
4. **Pure-Kotlin networking.** Following ADR-001's ethos, OPDS and WebDAV use
   `HttpURLConnection` + `XmlPullParser` — no OkHttp/Retrofit until a concrete
   need arises.
5. **Credentials are encrypted at rest.** WebDAV credentials are encrypted with an
   Android Keystore AES-GCM key before being written to SharedPreferences.

### Consequences
- ✅ OPDS catalogs, WebDAV browsing/sync, and Drive/OneDrive folder sync become possible
- ✅ Privacy posture stays strong: no passive network use, HTTPS-only, no third-party SDKs
- ⚠️ "No internet permission" can no longer be used as a security claim in docs/store listings
- ⚠️ FTPS / SFTP / SMB need third-party libraries — tracked in TODO.md as separate decisions
