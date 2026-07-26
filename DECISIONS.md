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
  (SFTP has since shipped: see ADR-009)

---

## ADR-009: sshj for SFTP Network Shares

**Status:** Accepted (supersedes ADR-008's SFTP deferral)
**Date:** 2026-07-25

### Context
ADR-008 shipped FTPS and deferred SFTP because sshj's dependency weight looked
disproportionate. The repository owner's backlog still lists SFTP as an
approved item, and SFTP is the share protocol most home NAS / Linux boxes
expose by default, so the trade-off is being revisited deliberately.

Candidate libraries:
- **sshj** (`com.hierynomus:sshj`): the actively maintained JVM SSH client,
  ships an `AndroidConfig` for Android use. Transitives: BouncyCastle
  (`bcprov`/`bcpkix`), `asn-one`, `slf4j-api` (already present via junrar).
- JSch (original): effectively unmaintained; the `com.github.mwiede` fork is
  maintained but has no Android-specific configuration story.
- Apache MINA SSHD: server-oriented, heavier still.

### Decision
Use **sshj** with `AndroidConfig` (`data/sync/SftpClient.kt`), mirroring the
`WebDavClient` / `FtpsClient` contract (list, download, snapshot up/download)
behind the same Sync-screen card pattern. Scope decisions:

1. **Password *and* key-based auth.** Password auth shipped first; key-based
   auth was added later (see the amendment below).
2. **Trust-on-first-use host keys.** The first key a server presents is pinned
   (fingerprint stored in `sync_prefs`); a changed key fails verification and
   surfaces as a sync error. Blindly accepting host keys (sshj's
   `PromiscuousVerifier`) would allow silent MITM and is not used.
3. **APK cost is contained.** BouncyCastle adds a few MB of classes; R8 keep
   rules cover sshj/BC (both resolve algorithms reflectively), and the
   duplicate `META-INF/versions/9/OSGI-INF/MANIFEST.MF` resource from
   bcprov+bcpkix is excluded in `packaging`.

### Consequences
- ✅ SFTP shares get the same browse / download / progress-sync features as WebDAV/FTPS
- ✅ Encrypted transport (SSH) upholds ADR-006 rule 2; host keys are TOFU-pinned
- ✅ Credentials reuse the Keystore-encrypted store (new `sftp_*` keys)
- ⚠️ Noticeably larger release APK (BouncyCastle); acceptable per owner-approved backlog
- ✅ SMB resolved by ADR-010; SSH key auth resolved by the amendment below

### Amendment (2026-07-26): key-based auth

The deferred key-based auth item is now implemented. A user imports an SSH
private key via SAF (any document, since keys carry no standard MIME type);
`isLikelySshPrivateKey` rejects obvious non-keys (public keys, certificates,
junk) at import, and sshj does the real parse at connect time. Scope:

- **PEM parsed from memory, never written to disk.** `SftpClient` calls
  `SSHClient.loadKeys(pem, null, passwordFinder)` and `authPublickey`; the key
  bytes stay in memory for the duration of the short-lived connection.
- **Formats:** whatever sshj's `AndroidConfig` file-key providers accept, i.e.
  OpenSSH v1 (`openssh-key-v1`, the modern `ssh-keygen` default), PKCS#1/PKCS#8
  PEM, and PuTTY PPK.
- **Encrypted at rest.** Both the PEM and its (optional) passphrase are stored
  through the same Keystore AES-GCM path as passwords (`SyncCredentialStore`,
  `sftp_key_*` keys), never in plaintext.
- **Auth selection:** a client uses key auth when a key is installed, otherwise
  password auth. The two are mutually exclusive in the UI (the password field
  is disabled while a key is installed). Host-key TOFU pinning is unchanged.

---

## ADR-010: jcifs-ng for SMB Network Shares

**Status:** Accepted
**Date:** 2026-07-25

### Context
The last share protocol approved by ADR-006 without a library decision. SMB is
what Windows, most NAS boxes, and macOS (since AFP's deprecation) actually
serve on home networks. Java SMB client options:

- **`jcifs-ng`** (`eu.agno3.jcifs:jcifs-ng`): actively maintained fork of the
  original jCIFS with SMB2/SMB3 support. Transitive deps: `slf4j-api` and
  BouncyCastle `bcprov` — both already in the dependency graph via junrar
  (ADR-007) and sshj (ADR-009), so the marginal APK cost is small.
  The servlet integration is a `provided` dependency and never ships.
- **`smbj`**: SMB2/3-only alternative, but also depends on BouncyCastle and has
  a smaller community; no dependency advantage.
- Original `jcifs`: unmaintained, SMB1-only — insecure, rejected outright.

### Decision
Implement **SMB with jcifs-ng** (`data/sync/SmbClient.kt`) mirroring the
`WebDavClient`/`FtpsClient` contract (list, download book, up/download the
progress snapshot). Security posture:

- **SMB1 is disabled** (`jcifs.smb.client.minVersion=SMB202`, max `SMB311`),
  so connections negotiate SMB2/3 or fail — never the legacy dialect.
- `smb://host[:port]/share[/path]` enforced at the URL boundary; the share
  segment is required. As with FTPS, the platform cleartext ban does not cover
  raw sockets — the dialect floor plus SMB2/3 signing/encryption negotiation is
  what upholds ADR-006 rule 2 here.
- Credentials reuse the Keystore-encrypted `SyncCredentialStore` (`smb_*`
  keys); `DOMAIN\user` names are supported and a blank username maps to
  anonymous/guest access.
- Downloads are size-capped via `data/net/DownloadLimits.kt` like every other
  network path.

### Consequences
- ✅ Windows / NAS / macOS shares get the same browse / download / progress-sync
  features as WebDAV, FTPS and SFTP — the ADR-006 network-share backlog is done
- ✅ SMB1 can never be negotiated, silently or otherwise
- ✅ No new heavyweight transitive dependencies (BouncyCastle and slf4j-api were
  already present via ADR-009 / ADR-007)
- ⚠️ jcifs-ng's kerberos/JGSS and servlet code paths are unused on Android and
  are suppressed with `-dontwarn` rules in `proguard-rules.pro`
