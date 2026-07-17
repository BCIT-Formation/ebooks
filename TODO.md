# TODO

## 🔴 Critical

- [x] Generate proper PNG launcher icons (`mipmap-hdpi/`, `mipmap-xhdpi/`, etc.) — required for APK build
- [x] Add `gradlew` wrapper script and `gradle-wrapper.jar` — run `gradle wrapper` locally
- [x] Test EPUB parser against malformed/unusual EPUBs (missing OPF, non-standard paths)
- [x] Handle `IOException` when file URI becomes invalid (moved/deleted file)
- [x] WebView `setWebContentsDebuggingEnabled` must be disabled in release builds
- [x] Validate URI permissions are taken with `takePersistableUriPermission` so files remain accessible across app restarts

## 🟠 Important

- [x] Add Room database migration strategy (added MIGRATION_1_2, version bumped to 2)
- [x] Implement in-book text search (JavaScript-based highlight in WebView)
- [x] Add PDF rendering screen using `android.graphics.pdf.PdfRenderer` (PdfReaderScreen implemented)
- [x] Add TXT reader screen (plain text with Compose `LazyColumn`) (TxtReaderScreen implemented)
- [x] Implement auto-scroll (JavaScript `window.scrollBy` loop via WebView)
- [x] Add instrumented tests (Espresso/Compose test) for UI flows (tests in `app/src/androidTest/`)
- [x] Add cover image rebuild functionality (Library → Settings → Rebuild Covers; `BookRepository.rebuildCovers`)
- [x] Support FB2 format (XML-based Russian ebook format — Fb2Parser + Fb2ReaderScreen implemented)
- [x] Tilt-to-scroll (accelerometer listener in `ReaderScreen`, toggle in reader settings)
- [x] Screen orientation lock per-book

## 🟢 Nice to Have

- [x] Bookshelf view mode (3D perspective like a real bookshelf — `BookshelfView` with tilted covers on wooden shelf boards)
- [x] Reading statistics (time read per book, pages per session)
- [x] Sleep timer for auto-scroll
- [x] Text-to-speech integration (`TtsSpeaker` — EPUB reader + TXT reader, chunked chapter speech)
- [x] Share book excerpt feature (share WebView selection with title/author attribution)
- [x] Custom fonts — user can add TTF/OTF files (imported to app storage, embedded as `@font-face` in the reader)
- [x] Comic book (CBZ) reader — basic vertical page reader (`CbzReaderScreen`)
- [x] Pinch-to-zoom for the comic reader (two-finger scale/pan per page; single-finger scroll
      is left untouched so it doesn't fight the page list)
- [ ] CBR support (needs a RAR-decoding library decision — see network follow-ups below for
      the shape of that kind of call)
- [x] Night light / warm color filter overlay
- [x] Widget for current reading book (Glance app widget showing the most recently read book)
- [x] Android 13+ per-app language preferences (`localeConfig` + en/fr resources for the main screens)
- [x] Finish string extraction for full localization (ViewModel error messages, PDF/FB2/CBZ reader labels, chapter panel, drawing toolbar; font family names and Markdown-export fallbacks intentionally left as-is)
- [x] Cloud sync (reading progress across devices) — via a user-picked cloud folder
      (Google Drive / OneDrive document providers through SAF) and via WebDAV;
      newer-wins merge keyed by title+author (`data/sync/`, ADR-006)
- [x] OPDS catalog support (browse catalogs, download books into the library —
      `data/opds/` + `OpdsScreen`, ADR-006)

## ✨ Advanced Features (New)

- [x] Offline dictionary (StarDict format: `.ifo`, `.idx`, `.dict`) — word lookup on selected text in readers (`data/dictionary/StarDictParser`, `DictionaryRepository`)
- [x] Bionic Reading algorithm — automatic bold formatting of word fragments for improved speed-reading (`util/BionicReading` with HTML + Compose support)
- [x] E-Ink mode enhancements — volume key pagination without scroll, animation disable for e-readers (Boox, Kobo, etc.) (`ui/theme/EInkMode`, `data/settings/EInkSettings`)
- [x] Smooth scrolling optimization — `remember`, `key`, `contentType` for LazyColumn/BookshelfView (reduce recompositions) — ready for integration

## 🌐 Network follow-ups (ADR-006)

Approved by the repository owner; each needs a library or credential decision first:

- [ ] FTPS support (e.g. Apache `commons-net`) — plain FTP stays banned (cleartext)
- [ ] SFTP support (e.g. `sshj`) — SSH key or password auth
- [ ] SMB/Windows network shares (e.g. `jcifs-ng`); AFP has no maintained Java client —
      macOS shares are reachable over SMB
- [ ] Native Google Drive / OneDrive API sync — requires the owner to register OAuth
      client IDs (Google Cloud Console / Azure AD); the SAF cloud-folder sync already
      covers both providers without credentials
