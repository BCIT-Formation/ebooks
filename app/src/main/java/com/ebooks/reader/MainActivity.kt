package com.ebooks.reader

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ebooks.reader.ui.screens.CbzReaderScreen
import com.ebooks.reader.ui.screens.LibraryScreen
import com.ebooks.reader.ui.screens.OpdsScreen
import com.ebooks.reader.ui.screens.PdfReaderScreen
import com.ebooks.reader.ui.screens.ReaderScreen
import com.ebooks.reader.ui.screens.RssReaderScreen
import com.ebooks.reader.ui.screens.RssFeedsScreen
import com.ebooks.reader.ui.screens.RssFeedArticlesScreen
import com.ebooks.reader.ui.screens.SyncScreen
import com.ebooks.reader.ui.screens.TxtReaderScreen
import com.ebooks.reader.ui.screens.Fb2ReaderScreen
import com.ebooks.reader.ui.theme.DisplayMode
import com.ebooks.reader.ui.theme.EbookReaderTheme
import com.ebooks.reader.widget.CurrentBookWidget
import com.ebooks.reader.data.settings.ThemeSettings
import com.ebooks.reader.data.settings.AppTheme
import com.ebooks.reader.data.settings.FirstRunManager
import com.ebooks.reader.data.repository.BookRepository
import com.ebooks.reader.data.repository.RssRepository

class MainActivity : ComponentActivity() {

    override fun onStop() {
        super.onStop()
        // Keep the "currently reading" home screen widget in sync when leaving the app
        lifecycleScope.launch {
            runCatching { CurrentBookWidget().updateAll(applicationContext) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto-import default RSS feeds and download popular Gutenberg books on first install
        lifecycleScope.launch {
            val firstRunManager = FirstRunManager.getInstance(this@MainActivity)
            if (firstRunManager.isFirstRun()) {
                try {
                    val rssRepo = RssRepository(this@MainActivity)
                    val bookRepo = BookRepository(this@MainActivity)

                    Log.d("FirstRun", "Importing default RSS feeds...")
                    val feedsAdded = rssRepo.importDefaultFeeds(this@MainActivity)
                    Log.d("FirstRun", "Added $feedsAdded default RSS feeds")

                    Log.d("FirstRun", "Downloading popular Gutenberg books...")
                    val booksAdded = bookRepo.downloadGutenbergPopularBooks(20)
                    Log.d("FirstRun", "Downloaded ${booksAdded.count { it is BookRepository.ImportResult.Success }} Gutenberg books")

                    firstRunManager.markFirstRunComplete()
                    Log.d("FirstRun", "First-run setup complete")
                } catch (e: Exception) {
                    Log.e("FirstRun", "Error during first-run setup", e)
                    // Mark as complete anyway to prevent re-triggering on every launch
                    FirstRunManager.getInstance(this@MainActivity).markFirstRunComplete()
                }
            }
        }

        setContent {
            val context = LocalContext.current
            var displayMode by remember { mutableStateOf(DisplayMode.load(context)) }
            val themeSettings = remember { ThemeSettings.getInstance(context) }
            val appTheme by themeSettings.currentTheme.collectAsStateWithLifecycle(initialValue = AppTheme.LIGHT)
            EbookReaderTheme(displayMode = displayMode, appTheme = appTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route
                    val showTabs = currentRoute == "library" || currentRoute == "rss_feeds"

                    Scaffold(
                        bottomBar = {
                            if (showTabs) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = currentRoute == "library",
                                        onClick = {
                                            navController.navigate("library") {
                                                popUpTo("library") { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                                        label = { Text(stringResource(R.string.tab_library)) }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == "rss_feeds",
                                        onClick = {
                                            navController.navigate("rss_feeds") {
                                                popUpTo("library") { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        },
                                        icon = { Icon(Icons.Default.RssFeed, null) },
                                        label = { Text(stringResource(R.string.tab_rss)) }
                                    )
                                }
                            }
                        }
                    ) { scaffoldPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "library",
                        modifier = Modifier.padding(bottom = scaffoldPadding.calculateBottomPadding())
                    ) {
                        composable("library") {
                            LibraryScreen(
                                displayMode = displayMode,
                                onDisplayModeChange = { mode ->
                                    displayMode = mode
                                    DisplayMode.save(context, mode)
                                },
                                onOpenBook = { bookId, fileType ->
                                    when (fileType) {
                                        "pdf" -> navController.navigate("pdf_reader/$bookId")
                                        "txt" -> navController.navigate("txt_reader/$bookId")
                                        "fb2" -> navController.navigate("fb2_reader/$bookId")
                                        "cbz" -> navController.navigate("cbz_reader/$bookId")
                                        else  -> navController.navigate("reader/$bookId")
                                    }
                                },
                                onOpenOpds = { navController.navigate("opds") },
                                onOpenSync = { navController.navigate("sync") }
                            )
                        }

                        composable("opds") {
                            OpdsScreen(onBack = { navController.popBackStack() })
                        }

                        composable("sync") {
                            SyncScreen(onBack = { navController.popBackStack() })
                        }

                        composable("rss_feeds") {
                            RssFeedsScreen(
                                onOpenFeed = { feedId -> navController.navigate("rss_articles/$feedId") }
                            )
                        }

                        composable(
                            route = "rss_articles/{feedId}",
                            arguments = listOf(navArgument("feedId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val feedId = backStackEntry.arguments?.getString("feedId") ?: return@composable
                            RssFeedArticlesScreen(
                                feedId = feedId,
                                onOpenArticle = { articleId -> navController.navigate("rss_reader/$articleId") },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "rss_reader/{articleId}",
                            arguments = listOf(navArgument("articleId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val articleId = backStackEntry.arguments?.getString("articleId") ?: return@composable
                            RssReaderScreen(
                                articleId = articleId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "reader/{bookId}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                            ReaderScreen(
                                bookId = bookId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "pdf_reader/{bookId}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                            PdfReaderScreen(
                                bookId = bookId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "txt_reader/{bookId}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                            TxtReaderScreen(
                                bookId = bookId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "fb2_reader/{bookId}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                            Fb2ReaderScreen(
                                bookId = bookId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "cbz_reader/{bookId}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                            CbzReaderScreen(
                                bookId = bookId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    } // end Scaffold content
                }
            }
        }
    }
}
