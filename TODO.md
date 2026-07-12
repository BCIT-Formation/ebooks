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
- [x] Comic book (CBZ) reader — basic vertical page reader (`CbzReaderScreen`); CBR and pinch-to-zoom still open
- [x] Night light / warm color filter overlay
- [x] Widget for current reading book (Glance app widget showing the most recently read book)
- [x] Android 13+ per-app language preferences (`localeConfig` + en/fr resources for the main screens; ViewModel error strings and secondary readers still hardcoded English)
- [ ] Finish string extraction for full localization (ViewModel error messages, PDF/FB2/CBZ reader labels, chapter panel, drawing toolbar)
- [ ] CBR support + pinch-to-zoom for the comic reader

## ❌ Won't do (conflicts with offline-only design)

These require network access, which the app deliberately does not have
(no `INTERNET` permission — see SECURITY.md and CLAUDE.md). Do not implement
without first reversing that architecture decision:

- Cloud sync (reading progress across devices)
- OPDS catalog support (download books from servers)
