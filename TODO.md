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
- [ ] Add cover image rebuild functionality (re-import covers from existing books)
- [x] Support FB2 format (XML-based Russian ebook format — Fb2Parser + Fb2ReaderScreen implemented)
- [ ] Tilt-to-scroll (using `SensorManager` accelerometer)
- [x] Screen orientation lock per-book

## 🟢 Nice to Have

- [ ] Bookshelf view mode (3D perspective like a real bookshelf)
- [x] Reading statistics (time read per book, pages per session)
- [x] Sleep timer for auto-scroll
- [ ] Text-to-speech integration
- [ ] Share book excerpt feature
- [ ] Cloud sync (reading progress across devices)
- [ ] OPDS catalog support (download books from servers)
- [ ] Custom fonts — user can add TTF/OTF files
- [x] Comic book (CBZ) reader — basic vertical page reader (`CbzReaderScreen`); CBR and pinch-to-zoom still open
- [x] Night light / warm color filter overlay
- [ ] Widget for current reading book
- [ ] Android 13+ per-app language preferences
