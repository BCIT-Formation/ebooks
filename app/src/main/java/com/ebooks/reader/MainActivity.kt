package com.ebooks.reader

import android.os.Bundle
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
import com.ebooks.reader.ui.screens.RssScreen
import com.ebooks.reader.ui.screens.SyncScreen
import com.ebooks.reader.ui.screens.TxtReaderScreen
import com.ebooks.reader.ui.screens.Fb2ReaderScreen
import com.ebooks.reader.ui.theme.DisplayMode
import com.ebooks.reader.ui.theme.EbookReaderTheme
import com.ebooks.reader.widget.CurrentBookWidget
import com.ebooks.reader.data.settings.ThemeSettings
import com.ebooks.reader.data.settings.AppTheme

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
                    val showTabs = currentRoute == "library" || currentRoute == "rss"

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
                                        selected = currentRoute == "rss",
                                        onClick = {
                                            navController.navigate("rss") {
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

                        composable("rss") {
                            RssScreen(
                                onOpenArticle = { articleId -> navController.navigate("rss_reader/$articleId") }
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
