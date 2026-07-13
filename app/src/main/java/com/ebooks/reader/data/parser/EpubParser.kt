package com.ebooks.reader.data.parser

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Pure Kotlin/Android EPUB parser — no external library dependencies.
 * EPUB is a ZIP archive containing HTML, CSS, images and OPF/NCX XML files.
 */
class EpubParser(private val context: Context) {

    // In-memory cache of the most recently unpacked EPUB so chapter turns and
    // theme/font changes don't re-read and re-inflate the whole ZIP each time.
    // Scoped to a single book (the reader reuses one parser instance), so peak
    // memory matches what a single openZip() call already required.
    private val cacheLock = Any()
    private var cachedZipUri: String? = null
    private var cachedEntries: Map<String, ByteArray>? = null

    // Cache of the encoded @font-face block, keyed by font path — avoids
    // re-reading and base64-encoding the font file on every chapter load.
    private var cachedFontPath: String? = null
    private var cachedFontCss: String? = null

    /** Drops cached ZIP entries. Call when the current book is no longer open. */
    fun clearCache() {
        synchronized(cacheLock) {
            cachedZipUri = null
            cachedEntries = null
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun parse(uri: Uri): EpubBook? = runCatching {
        openZip(uri) { entries ->
            val opfPath = findOpfPath(entries) ?: return@openZip null
            val opfDir = opfPath.substringBeforeLast("/", "")

            val opfContent = entries[opfPath] ?: return@openZip null
            val (metadata, manifest, spine) = parseOpf(opfContent.decodeToString())

            val tocHref = resolvePath(opfDir, findTocHref(manifest, spine))
            val toc = tocHref?.let { href ->
                entries[href]?.let { content ->
                    parseToc(content.decodeToString(), href.substringBeforeLast("/", ""))
                }
            } ?: emptyList()

            val chapters = buildChapterList(toc, spine, manifest, opfDir)

            val coverBytes = extractCover(entries, manifest, opfDir)
            val cssStyles = extractCss(entries, manifest, opfDir)

            EpubBook(
                title = metadata["title"] ?: "Unknown Title",
                author = metadata["creator"] ?: "Unknown Author",
                description = metadata["description"],
                publisher = metadata["publisher"],
                language = metadata["language"],
                coverBytes = coverBytes,
                chapters = chapters,
                cssStyles = cssStyles
            )
        }
    }.getOrNull()

    fun getChapterHtml(uri: Uri, chapterHref: String, readerTheme: ReaderTheme): String? =
        runCatching {
            openZip(uri) { entries ->
                val rawHtml = entries[chapterHref]?.decodeToString() ?: return@openZip null
                val chapterDir = chapterHref.substringBeforeLast("/", "")
                injectReaderStyles(
                    preprocessHtml(rawHtml, entries, chapterDir),
                    readerTheme
                )
            }
        }.getOrNull()

    fun buildHtmlFromText(text: String, theme: ReaderTheme): String {
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val paragraphs = escaped.split(Regex("\n{2,}"))
            .filter { it.isNotBlank() }
            .joinToString("") { "<p>${it.replace("\n", "<br>")}</p>" }
        val html = "<html><head></head><body>$paragraphs</body></html>"
        return injectReaderStyles(html, theme)
    }

    // ── ZIP Handling ───────────────────────────────────────────────────────────

    private fun <T> openZip(uri: Uri, block: (Map<String, ByteArray>) -> T): T? {
        val key = uri.toString()
        synchronized(cacheLock) {
            if (cachedZipUri == key) cachedEntries?.let { return block(it) }
        }

        val entries = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: return null

        synchronized(cacheLock) {
            cachedZipUri = key
            cachedEntries = entries
        }
        return block(entries)
    }

    // ── OPF / Container Parsing ───────────────────────────────────────────────

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        val containerXml = entries["META-INF/container.xml"]?.decodeToString() ?: return null
        val parser = newPullParser(containerXml.byteInputStream())
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            parser.next()
        }
        return null
    }

    data class OpfData(
        val metadata: Map<String, String>,
        val manifest: List<ManifestItem>,
        val spine: List<SpineItem>
    )

    private fun parseOpf(opfContent: String): OpfData {
        val metadata = mutableMapOf<String, String>()
        val manifest = mutableListOf<ManifestItem>()
        val spine = mutableListOf<SpineItem>()

        val parser = newPullParser(opfContent.byteInputStream())
        var inMetadata = false
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name?.substringAfterLast(":") ?: ""
                    when (currentTag) {
                        "metadata" -> inMetadata = true
                        "item" -> manifest.add(
                            ManifestItem(
                                id = parser.getAttributeValue(null, "id") ?: "",
                                href = parser.getAttributeValue(null, "href") ?: "",
                                mediaType = parser.getAttributeValue(null, "media-type") ?: "",
                                properties = parser.getAttributeValue(null, "properties") ?: ""
                            )
                        )
                        "itemref" -> spine.add(
                            SpineItem(
                                idref = parser.getAttributeValue(null, "idref") ?: "",
                                linear = parser.getAttributeValue(null, "linear") != "no"
                            )
                        )
                    }
                }
                XmlPullParser.END_TAG -> {
                    if ((parser.name?.substringAfterLast(":") ?: "") == "metadata") inMetadata = false
                }
                XmlPullParser.TEXT -> {
                    if (inMetadata && parser.text?.isNotBlank() == true) {
                        val text = parser.text.trim()
                        // Take the first value for each tag
                        if (!metadata.containsKey(currentTag)) {
                            metadata[currentTag] = text
                        }
                    }
                }
            }
            parser.next()
        }

        return OpfData(metadata, manifest, spine)
    }

    // ── TOC Parsing (EPUB 2 NCX + EPUB 3 nav) ────────────────────────────────

    private fun findTocHref(manifest: List<ManifestItem>, spine: List<SpineItem>): String? {
        // EPUB 3: nav document
        manifest.find { "nav" in it.properties }?.let { return it.href }
        // EPUB 2: NCX file
        manifest.find { it.mediaType == "application/x-dtbncx+xml" }?.let { return it.href }
        return null
    }

    private fun parseToc(tocContent: String, tocDir: String): List<TocItem> {
        return when {
            tocContent.contains("nav") && tocContent.contains("epub:type=\"toc\"") ->
                parseNavToc(tocContent, tocDir)
            tocContent.contains("ncx") || tocContent.contains("navMap") ->
                parseNcxToc(tocContent, tocDir)
            else -> parseNavToc(tocContent, tocDir)
        }
    }

    private fun parseNcxToc(ncxContent: String, tocDir: String): List<TocItem> {
        val items = mutableListOf<TocItem>()
        val parser = newPullParser(ncxContent.byteInputStream())
        var order = 0
        var inNavPoint = false
        var currentTitle = ""
        var currentHref = ""
        var currentId = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name?.substringAfterLast(":") ?: "") {
                    "navPoint" -> {
                        inNavPoint = true
                        currentId = parser.getAttributeValue(null, "id") ?: ""
                        currentTitle = ""
                        currentHref = ""
                    }
                    "content" -> if (inNavPoint) {
                        val src = parser.getAttributeValue(null, "src") ?: ""
                        currentHref = resolvePath(tocDir, src.substringBefore("#")) ?: src
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name?.substringAfterLast(":") ?: "") {
                    "navPoint" -> {
                        if (inNavPoint && currentHref.isNotBlank()) {
                            items.add(TocItem(currentId, currentTitle, currentHref, order++))
                        }
                        inNavPoint = false
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inNavPoint && currentTitle.isEmpty() && parser.text?.isNotBlank() == true) {
                        currentTitle = parser.text.trim()
                    }
                }
            }
            parser.next()
        }
        return items
    }

    private fun parseNavToc(navContent: String, tocDir: String): List<TocItem> {
        val items = mutableListOf<TocItem>()
        val parser = newPullParser(navContent.byteInputStream())
        var order = 0
        var inTocNav = false
        var insideA = false
        var currentHref = ""
        var currentTitle = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name?.lowercase() ?: ""
                    when {
                        tag == "nav" -> {
                            val epubType = parser.getAttributeValue(null, "epub:type") ?: ""
                            if ("toc" in epubType) inTocNav = true
                        }
                        inTocNav && tag == "a" -> {
                            insideA = true
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            currentHref = resolvePath(tocDir, href.substringBefore("#")) ?: href
                            currentTitle = ""
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.lowercase() ?: ""
                    when {
                        tag == "nav" -> inTocNav = false
                        inTocNav && tag == "a" -> {
                            if (currentHref.isNotBlank()) {
                                items.add(TocItem("item$order", currentTitle, currentHref, order++))
                            }
                            insideA = false
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideA && inTocNav && parser.text?.isNotBlank() == true) {
                        currentTitle += parser.text.trim()
                    }
                }
            }
            parser.next()
        }
        return items
    }

    // ── Chapter Building ──────────────────────────────────────────────────────

    private fun buildChapterList(
        toc: List<TocItem>,
        spine: List<SpineItem>,
        manifest: List<ManifestItem>,
        opfDir: String
    ): List<EpubChapter> {
        if (toc.isNotEmpty()) {
            return toc.mapIndexed { idx, item ->
                EpubChapter(
                    index = idx,
                    title = item.title.ifBlank { "Chapter ${idx + 1}" },
                    href = item.href
                )
            }
        }
        // Fallback: use spine order
        val manifestById = manifest.associateBy { it.id }
        return spine.filter { it.linear }.mapIndexedNotNull { idx, spineItem ->
            val item = manifestById[spineItem.idref] ?: return@mapIndexedNotNull null
            if (!item.mediaType.contains("html")) return@mapIndexedNotNull null
            val href = resolvePath(opfDir, item.href) ?: item.href
            EpubChapter(index = idx, title = "Chapter ${idx + 1}", href = href)
        }
    }

    // ── HTML Pre-processing ───────────────────────────────────────────────────

    private fun preprocessHtml(
        html: String,
        entries: Map<String, ByteArray>,
        chapterDir: String
    ): String {
        // Inline images as base64 data URIs
        var processed = html
        val imgRegex = Regex("""src=["']([^"']+)["']""")
        processed = imgRegex.replace(processed) { match ->
            val src = match.groupValues[1]
            if (src.startsWith("data:")) return@replace match.value
            val resolved = resolvePath(chapterDir, src)
            val bytes = resolved?.let { entries[it] }
            if (bytes != null) {
                val mime = guessMime(src)
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                """src="data:$mime;base64,$b64""""
            } else match.value
        }

        // Strip external stylesheet links (we inject our own)
        processed = processed.replace(Regex("""<link[^>]+rel=["']stylesheet["'][^>]*>"""), "")
        processed = processed.replace(Regex("""<link[^>]+stylesheet[^>]*>"""), "")

        return processed
    }

    private fun injectReaderStyles(html: String, theme: ReaderTheme): String {
        val style = buildReaderCss(theme)
        return if (html.contains("<head>", ignoreCase = true)) {
            html.replace(Regex("</head>", RegexOption.IGNORE_CASE), "$style</head>")
        } else {
            "$style$html"
        }
    }

    /**
     * Embeds a user-imported TTF/OTF as a base64 @font-face so the WebView can
     * use it without file access. Empty string when no custom font is active.
     */
    private fun buildFontFaceCss(theme: ReaderTheme): String {
        val path = theme.customFontPath ?: return ""
        if (path == cachedFontPath) return cachedFontCss.orEmpty()
        val css = runCatching {
            val bytes = File(path).readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val isOtf = path.endsWith(".otf", ignoreCase = true)
            val mime = if (isOtf) "font/otf" else "font/ttf"
            val format = if (isOtf) "opentype" else "truetype"
            "@font-face { font-family: 'CustomReaderFont'; src: url(data:$mime;base64,$b64) format('$format'); }"
        }.getOrDefault("")
        cachedFontPath = path
        cachedFontCss = css
        return css
    }

    private fun buildReaderCss(theme: ReaderTheme): String = """
        <style>
        ${buildFontFaceCss(theme)}
        * { box-sizing: border-box; }
        html {
            -webkit-text-size-adjust: none;
            text-size-adjust: none;
        }
        body {
            margin: 0;
            padding: 20px 16px 60px 16px;
            background-color: ${theme.backgroundColor};
            color: ${theme.textColor};
            font-family: ${theme.fontFamily};
            font-size: ${theme.fontSize}px;
            line-height: ${theme.lineHeight};
            word-wrap: break-word;
            overflow-wrap: break-word;
        }
        a { color: ${theme.linkColor}; }
        h1, h2, h3, h4, h5, h6 {
            color: ${theme.headingColor};
            line-height: 1.3;
            margin-top: 1.5em;
            margin-bottom: 0.5em;
        }
        img { max-width: 100%; height: auto; display: block; margin: 8px auto; }
        p { margin: 0.5em 0; text-indent: ${if (theme.paragraphIndent) "1.5em" else "0"}; }
        blockquote { margin: 1em 1.5em; padding-left: 1em; border-left: 3px solid ${theme.linkColor}; }
        code, pre { background: ${theme.codeBackground}; border-radius: 4px; padding: 2px 4px; font-size: 0.9em; }
        pre { padding: 12px; overflow-x: auto; }
        </style>
    """.trimIndent()

    // ── Cover Extraction ──────────────────────────────────────────────────────

    private fun extractCover(
        entries: Map<String, ByteArray>,
        manifest: List<ManifestItem>,
        opfDir: String
    ): ByteArray? {
        // EPUB 3: item with properties="cover-image"
        manifest.find { "cover-image" in it.properties }?.let { item ->
            return entries[resolvePath(opfDir, item.href) ?: item.href]
        }
        // EPUB 2: common cover names
        val coverCandidates = listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.gif")
        for (candidate in coverCandidates) {
            entries.keys.find { it.endsWith(candidate, ignoreCase = true) }
                ?.let { return entries[it] }
        }
        // Fallback: first image in manifest
        manifest.find { it.mediaType.startsWith("image/") }?.let { item ->
            return entries[resolvePath(opfDir, item.href) ?: item.href]
        }
        return null
    }

    // ── CSS Extraction ────────────────────────────────────────────────────────

    private fun extractCss(
        entries: Map<String, ByteArray>,
        manifest: List<ManifestItem>,
        opfDir: String
    ): List<String> = manifest
        .filter { it.mediaType == "text/css" }
        .mapNotNull { item ->
            entries[resolvePath(opfDir, item.href) ?: item.href]?.decodeToString()
        }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun resolvePath(base: String, relative: String?): String? {
        if (relative.isNullOrBlank()) return null
        if (relative.startsWith("/")) return relative.trimStart('/')
        if (base.isEmpty()) return relative
        val parts = base.split("/").toMutableList()
        relative.split("/").forEach { segment ->
            when (segment) {
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                "." -> {}
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun guessMime(href: String): String = when {
        href.endsWith(".jpg", ignoreCase = true) || href.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        href.endsWith(".png", ignoreCase = true) -> "image/png"
        href.endsWith(".gif", ignoreCase = true) -> "image/gif"
        href.endsWith(".webp", ignoreCase = true) -> "image/webp"
        href.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
        else -> "image/jpeg"
    }

    private fun newPullParser(stream: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().also { it.isNamespaceAware = false }
        return factory.newPullParser().also { it.setInput(stream, null) }
    }
}

data class ReaderTheme(
    val backgroundColor: String = "#FFFFFF",
    val textColor: String = "#222222",
    val headingColor: String = "#111111",
    val linkColor: String = "#1a73e8",
    val codeBackground: String = "#f0f0f0",
    val fontFamily: String = "Georgia, serif",
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val paragraphIndent: Boolean = false,
    /** Absolute path of a user-imported TTF/OTF to embed as 'CustomReaderFont'. */
    val customFontPath: String? = null
) {
    companion object {
        val LIGHT = ReaderTheme()
        val DARK = ReaderTheme(
            backgroundColor = "#1a1a2e",
            textColor = "#e0e0e0",
            headingColor = "#ffffff",
            linkColor = "#82b4ff",
            codeBackground = "#2d2d2d"
        )
        val SEPIA = ReaderTheme(
            backgroundColor = "#f3ead3",
            textColor = "#3b2f1e",
            headingColor = "#2a1f10",
            linkColor = "#7a4f2d",
            codeBackground = "#e8d9b5"
        )
        val NIGHT = ReaderTheme(
            backgroundColor = "#0d0d0d",
            textColor = "#aaaaaa",
            headingColor = "#cccccc",
            linkColor = "#5a9fd4",
            codeBackground = "#1a1a1a"
        )
    }
}
