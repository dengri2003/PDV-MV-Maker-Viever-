package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.prefs.UserPreferences
import com.example.i18n.LocalStrings
import com.example.i18n.resolveAppStrings
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImagesToPdfScreen
import com.example.ui.screens.MarkdownToPdfScreen
import com.example.ui.screens.OrganizerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ViewerScreen
import com.example.ui.theme.PdfMvTheme
import com.example.ui.viewmodel.AppViewModelFactory
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.ImagesToPdfViewModel
import com.example.ui.viewmodel.MarkdownToPdfViewModel
import com.example.ui.viewmodel.OrganizerViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.ViewerViewModel
import com.example.ui.viewmodel.ViewerViewModelFactory

private object Routes {
    const val HOME = "home"
    const val VIEWER = "viewer/{docId}"
    const val IMAGES_TO_PDF = "images_to_pdf"
    const val MARKDOWN_TO_PDF = "markdown_to_pdf"
    const val ORGANIZER = "organizer?docId={docId}"
    const val SETTINGS = "settings"

    fun viewer(docId: String) = "viewer/${Uri.encode(docId)}"
    fun organizer(docId: String? = null) =
        if (docId.isNullOrBlank()) "organizer" else "organizer?docId=${Uri.encode(docId)}"
}

class MainActivity : ComponentActivity() {

    private val incomingPdfUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleViewIntent(intent)

        val app = application as PdfMvApplication
        val factory = AppViewModelFactory(app)

        setContent {
            val prefs by app.preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val strings = resolveAppStrings(prefs.language)

            CompositionLocalProvider(LocalStrings provides strings) {
                PdfMvTheme(themeMode = prefs.themeMode) {
                    PdfMvNavHost(
                        app = app,
                        factory = factory,
                        incomingPdfUri = incomingPdfUriState.value,
                        onIncomingUriHandled = { incomingPdfUriState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                incomingPdfUriState.value = uri
            }
        }
    }
}

@Composable
private fun PdfMvNavHost(
    app: PdfMvApplication,
    factory: AppViewModelFactory,
    incomingPdfUri: Uri?,
    onIncomingUriHandled: () -> Unit
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel(factory = factory)

    LaunchedEffect(incomingPdfUri) {
        if (incomingPdfUri != null) {
            homeViewModel.importPdf(incomingPdfUri) { docId ->
                navController.navigate(Routes.viewer(docId)) {
                    launchSingleTop = true
                }
            }
            onIncomingUriHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                renderEngine = app.renderEngine,
                onOpenViewer = { docId ->
                    navController.navigate(Routes.viewer(docId))
                },
                onNavigateImagesToPdf = {
                    navController.navigate(Routes.IMAGES_TO_PDF)
                },
                onNavigateMarkdownToPdf = {
                    navController.navigate(Routes.MARKDOWN_TO_PDF)
                },
                onNavigateOrganizer = { docId ->
                    navController.navigate(Routes.organizer(docId))
                },
                onNavigateSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(navArgument("docId") { type = NavType.StringType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId") ?: return@composable
            val viewerViewModel: ViewerViewModel = viewModel(
                key = "viewer_$docId",
                factory = ViewerViewModelFactory(app, docId)
            )
            ViewerScreen(
                viewModel = viewerViewModel,
                onBack = { navController.popBackStack() },
                onOrganizeDocument = { targetDocId ->
                    navController.navigate(Routes.organizer(targetDocId))
                }
            )
        }

        composable(Routes.IMAGES_TO_PDF) {
            val imagesVm: ImagesToPdfViewModel = viewModel(factory = factory)
            ImagesToPdfScreen(
                viewModel = imagesVm,
                onBack = { navController.popBackStack() },
                onOpenGeneratedPdf = { docId ->
                    navController.navigate(Routes.viewer(docId)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(Routes.MARKDOWN_TO_PDF) {
            val mdVm: MarkdownToPdfViewModel = viewModel(factory = factory)
            MarkdownToPdfScreen(
                viewModel = mdVm,
                onBack = { navController.popBackStack() },
                onOpenGeneratedPdf = { docId ->
                    navController.navigate(Routes.viewer(docId)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(
            route = Routes.ORGANIZER,
            arguments = listOf(
                navArgument("docId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getString("docId")
            val orgVm: OrganizerViewModel = viewModel(factory = factory)
            OrganizerScreen(
                viewModel = orgVm,
                initialDocId = docId,
                onBack = { navController.popBackStack() },
                onOpenGeneratedPdf = { newDocId ->
                    navController.navigate(Routes.viewer(newDocId)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(Routes.SETTINGS) {
            val settingsVm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = settingsVm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
