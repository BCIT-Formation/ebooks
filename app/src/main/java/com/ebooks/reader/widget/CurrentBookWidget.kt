package com.ebooks.reader.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ebooks.reader.MainActivity
import com.ebooks.reader.R
import com.ebooks.reader.data.db.entities.Book
import com.ebooks.reader.data.repository.BookRepository

/**
 * Home screen widget showing the most recently read book. Tapping it opens
 * the app. Refreshed by the launcher's periodic update and by [MainActivity]
 * whenever the user leaves the app.
 */
class CurrentBookWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = BookRepository(context.applicationContext)
        val book = repository.getMostRecentlyReadBook()
        val progressText = book?.let { current ->
            val progress = repository.getReadingProgress(current.id)
            if (progress != null && current.totalChapters > 0) {
                context.getString(R.string.widget_chapter_progress, progress.chapterIndex + 1, current.totalChapters)
            } else null
        }
        val cover = book?.coverPath?.let(::loadCoverBitmap)
        val strings = WidgetStrings(
            noBook = context.getString(R.string.widget_no_book),
            browseLibrary = context.getString(R.string.widget_browse_library),
            continueReading = context.getString(R.string.widget_continue_reading)
        )

        provideContent {
            WidgetContent(book = book, progressText = progressText, cover = cover, strings = strings)
        }
    }

    /** Decodes the cover downsampled to widget size (RemoteViews bitmaps must stay small). */
    private fun loadCoverBitmap(path: String): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outHeight <= 0) return@runCatching null
        val options = BitmapFactory.Options().apply {
            inSampleSize = (bounds.outHeight / 200).coerceAtLeast(1)
        }
        BitmapFactory.decodeFile(path, options)
    }.getOrNull()
}

private data class WidgetStrings(
    val noBook: String,
    val browseLibrary: String,
    val continueReading: String
)

@androidx.compose.runtime.Composable
private fun WidgetContent(book: Book?, progressText: String?, cover: Bitmap?, strings: WidgetStrings) {
    val background = ColorProvider(day = Color(0xFFFDF6EC), night = Color(0xFF1E1B16))
    val titleColor = ColorProvider(day = Color(0xFF1E1B16), night = Color(0xFFE9E1D8))
    val subtitleColor = ColorProvider(day = Color(0xFF6F6558), night = Color(0xFFB0A599))

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = book?.title,
                modifier = GlanceModifier.width(44.dp).fillMaxHeight().cornerRadius(6.dp)
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (book == null) {
                Text(
                    text = strings.noBook,
                    style = TextStyle(color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = strings.browseLibrary,
                    style = TextStyle(color = subtitleColor, fontSize = 12.sp)
                )
            } else {
                Text(
                    text = book.title,
                    maxLines = 1,
                    style = TextStyle(color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                )
                if (book.author.isNotBlank() && book.author != "Unknown") {
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = book.author,
                        maxLines = 1,
                        style = TextStyle(color = subtitleColor, fontSize = 12.sp)
                    )
                }
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = progressText ?: strings.continueReading,
                    maxLines = 1,
                    style = TextStyle(color = subtitleColor, fontSize = 12.sp)
                )
            }
        }
    }
}

class CurrentBookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrentBookWidget()
}
