package com.ebooks.reader.data.opds

/**
 * A parsed OPDS (Open Publication Distribution System) Atom feed.
 * OPDS 1.x is an Atom dialect: navigation feeds link to sub-catalogs,
 * acquisition feeds carry downloadable publications.
 */
data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>
)

data class OpdsEntry(
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    /** Link to a sub-catalog (Atom feed), when this entry is navigation. */
    val navigationHref: String? = null,
    /** Direct download link for the publication, when this entry is a book. */
    val acquisitionHref: String? = null,
    /** MIME type of the acquisition link (e.g. application/epub+zip). */
    val acquisitionType: String? = null
) {
    val isDownloadable: Boolean get() = acquisitionHref != null
    val isNavigation: Boolean get() = navigationHref != null && acquisitionHref == null
}
