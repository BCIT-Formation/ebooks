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

---

## ADR-007: junrar for CBR (RAR) Comic Archives

**Status:** Accepted
**Date:** 2026-07-25

### Context
CBZ comics (ZIP of images) are already supported with the built-in `ZipInputStream`
(ADR-001). CBR comics are the same idea in a RAR archive, but RAR is a proprietary
format with no published specification and no support in the Android platform, so
the pure-Kotlin approach of ADR-001 is not practical here. Candidate libraries:

- **junrar** (`com.github.junrar:junrar`): maintained pure-Java port of the unrar
  code. Small (~150 KB), a single `slf4j-api` transitive dependency, works on
  Android with no native code.
- 7-Zip-JBinding and other JNI wrappers: native `.so` binaries per ABI, much
  larger APK impact, more complex packaging.

### Decision
Use **junrar** for CBR page extraction. Extraction lives in
`data/parser/ComicArchive.kt`, which is shared by both comic formats: CBZ keeps
the built-in `ZipInputStream` path (ADR-001 unaffected); only CBR pays the
dependency. The reader screen, cache layout, page ordering, and reading-progress
handling are identical for both formats.

### Consequences
- ✅ CBR comics open in the existing comic reader (including pinch-to-zoom and drawing)
- ✅ No native code; one small dependency plus `slf4j-api` (no-op logger on Android)
- ⚠️ junrar decodes RAR4 archives only: RAR5 and encrypted archives fail with an
  error surfaced to the reader's existing error state
- ⚠️ junrar is licensed under the unrar license (source may not be used to
  re-create the RAR *compression* algorithm), fine for decompression-only use

---

## ADR-008: Apache commons-net for FTPS Network Shares

**Status:** Accepted
**Date:** 2026-07-25

### Context
ADR-006 approved FTPS / SFTP / SMB network shares, each pending a library
decision (plain FTP stays banned: cleartext). The existing share architecture is
`WebDavClient`'s small contract (list a directory, download a book, and
up/download the progress-snapshot text file) driven by `SyncViewModel` with
credentials encrypted at rest by `SyncCredentialStore`.

- **FTPS via Apache `commons-net`**: battle-tested, zero transitive
  dependencies, ~340 KB, plain-Java sockets. `FTPSClient` maps 1:1 onto the
  WebDAV contract (list/retrieve/store).
- **SFTP via `sshj`**: needs BouncyCastle + EdDSA transitive dependencies
  (multi-MB APK impact) and key-management UI to be genuinely useful.

### Decision
Implement **FTPS with Apache commons-net** now (`data/sync/FtpsClient.kt`),
in explicit-TLS mode with `PROT P` so the data channel is encrypted too, and
`ftps://` enforced at the URL boundary (mirroring `WebDavClient`'s `https://`
check). The platform cleartext ban does not cover raw sockets, so the scheme
check plus AUTH TLS + PROT P is what upholds ADR-006 rule 2 here. SFTP stays
in TODO.md until the dependency weight is justified.

### Consequences
- ✅ FTPS shares get the same browse / download / progress-sync features as WebDAV
- ✅ Credentials reuse the existing Keystore-encrypted store (new `ftps_*` keys)
- ✅ Zero transitive dependencies added
- ⚠️ Plain `ftp://` URLs are rejected with a user-facing message, by design
- ⚠️ SFTP / SMB remain open TODO items with separate library decisions
