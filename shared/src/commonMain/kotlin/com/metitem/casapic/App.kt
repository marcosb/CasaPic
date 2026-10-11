package com.metitem.casapic

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.metitem.casapic.db.Photos
import com.metitem.casapic.ui.albums.AlbumsScreen
import com.metitem.casapic.ui.editor.EditScreen
import com.metitem.casapic.ui.library.LibraryScreen
import com.metitem.casapic.ui.options.OptionsDialog
import com.metitem.casapic.ui.people.PeopleScreen
import com.metitem.casapic.ui.slideshow.SlideshowScreen
import com.metitem.casapic.viewmodel.AlbumsViewModel
import com.metitem.casapic.viewmodel.EditViewModel
import com.metitem.casapic.viewmodel.LibraryViewModel
import com.metitem.casapic.viewmodel.PeopleViewModel
import com.metitem.casapic.viewmodel.SlideshowViewModel

private sealed class Screen {
    data object Library : Screen()
    data class Edit(val photoId: Long) : Screen()
    data object People : Screen()
    data object Albums : Screen()
    data object Slideshow : Screen()
}

@Composable
fun App(
    dependencies: AppDependencies,
    onAddFolder: (LibraryViewModel) -> Unit = {},
    onAddFolderByPath: (String) -> Unit = {},
    onPrintContactSheet: (photos: List<Photos>, columns: Int, title: String) -> Unit = { _, _, _ -> },
    onSaveContactSheetAsJpeg: (photos: List<Photos>, columns: Int, title: String) -> Unit = { _, _, _ -> },
    onPrint: (photos: List<Photos>, options: com.metitem.casapic.model.PrintOptions) -> Unit = { _, _ -> },
    onOpenFile: (filePath: String) -> Unit = {},
    onMovePhotoToFolder: (photoId: Long, currentPath: String) -> Unit = { _, _ -> },
    onMovePhotoToKnownFolder: (photoId: Long, filePath: String, targetFolderPath: String) -> Unit = { _, _, _ -> },
    onSaveDestructive: (photoId: Long) -> Unit = {},
    defaultWatchedPaths: List<String> = emptyList(),
    menuBridge: MenuBridge? = null
) {
    CompositionLocalProvider(LocalAppDependencies provides dependencies) {
        MaterialTheme {
            val libraryViewModel = remember { LibraryViewModel(dependencies.db, dependencies.settings) }
            val peopleViewModel = remember { PeopleViewModel(dependencies.db) }
            val albumsViewModel = remember { AlbumsViewModel(dependencies.db) }
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Library) }
            var showOptions by remember { mutableStateOf(false) }

            val faceGroups by peopleViewModel.faceGroups.collectAsState()
            val albums by albumsViewModel.albums.collectAsState()
            val photos by libraryViewModel.photos.collectAsState()
            val selectedIds by libraryViewModel.selectedPhotoIds.collectAsState()
            val showHidden by libraryViewModel.showHiddenPhotos.collectAsState()
            val thumbnailCaption by libraryViewModel.thumbnailCaption.collectAsState()
            val thumbnailSizeNormal by libraryViewModel.thumbnailSizeNormal.collectAsState()

            var showAbout by remember { mutableStateOf(false) }
            var menuFolderManagerTrigger by remember { mutableStateOf(0) }
            var menuDuplicatesTrigger by remember { mutableStateOf(0) }
            var menuExportTrigger by remember { mutableStateOf(0) }
            var menuAdjustDateTrigger by remember { mutableStateOf(0) }
            var menuContactSheetTrigger by remember { mutableStateOf(0) }
            var menuPrintTrigger by remember { mutableStateOf(0) }

            // Keep bridge up-to-date with current Compose state and callbacks.
            // SideEffect runs after every successful composition — closures always capture fresh state.
            SideEffect {
                if (menuBridge == null) return@SideEffect
                menuBridge.showHiddenPhotos.value = showHidden
                menuBridge.thumbnailCaption.value = thumbnailCaption
                menuBridge.thumbnailSizeNormal.value = thumbnailSizeNormal
                menuBridge.hasSelection.value = selectedIds.isNotEmpty()
                menuBridge.hasSingleSelection.value = selectedIds.size == 1

                menuBridge.onAddFolder = { onAddFolder(libraryViewModel) }
                menuBridge.onOpenFolderManager = { menuFolderManagerTrigger++ }
                menuBridge.onOpenOptions = { showOptions = true }
                menuBridge.onOpenPeople = { currentScreen = Screen.People }
                menuBridge.onOpenAlbums = { currentScreen = Screen.Albums }
                menuBridge.onOpenSlideshow = { currentScreen = Screen.Slideshow }
                menuBridge.onToggleHiddenPhotos = { libraryViewModel.setShowHiddenPhotos(!showHidden) }
                menuBridge.onSetSmallThumbnails = { libraryViewModel.setThumbnailSize(false) }
                menuBridge.onSetNormalThumbnails = { libraryViewModel.setThumbnailSize(true) }
                menuBridge.onSetThumbnailCaption = { cap -> libraryViewModel.setThumbnailCaption(cap) }
                menuBridge.onSelectAll = { libraryViewModel.selectAll() }
                menuBridge.onSelectStarred = { libraryViewModel.selectStarred() }
                menuBridge.onInvertSelection = { libraryViewModel.invertSelection() }
                menuBridge.onClearSelection = { libraryViewModel.clearSelection() }
                menuBridge.onRotateCW = { libraryViewModel.batchRotateCW(selectedIds) }
                menuBridge.onRotateCCW = { libraryViewModel.batchRotateCCW(selectedIds) }
                menuBridge.onUndoAllEdits = { libraryViewModel.batchUndoAllEdits(selectedIds) }
                menuBridge.onResetFaces = { libraryViewModel.batchResetFaces(selectedIds) }
                menuBridge.onDeleteSelected = { libraryViewModel.batchDelete(selectedIds) }
                menuBridge.onOpenExport = { menuExportTrigger++ }
                menuBridge.onAdjustDate = { if (selectedIds.isNotEmpty()) menuAdjustDateTrigger++ }
                menuBridge.onFindDuplicates = { menuDuplicatesTrigger++ }
                menuBridge.onPrintContactSheet = { menuContactSheetTrigger++ }
                menuBridge.onPrint = { menuPrintTrigger++ }
                menuBridge.onOpenPhoto = {
                    val id = selectedIds.singleOrNull()
                    if (id != null) currentScreen = Screen.Edit(id)
                }
                menuBridge.onAbout = { showAbout = true }
            }

            when (val screen = currentScreen) {
                is Screen.Library -> LibraryScreen(
                    viewModel = libraryViewModel,
                    albums = albums,
                    peopleCount = faceGroups.size,
                    defaultWatchedPaths = defaultWatchedPaths,
                    onAddFolder = { onAddFolder(libraryViewModel) },
                    onAddFolderByPath = onAddFolderByPath,
                    onOpenPhoto = { photoId -> currentScreen = Screen.Edit(photoId) },
                    onOpenPeople = { currentScreen = Screen.People },
                    onOpenAlbums = { currentScreen = Screen.Albums },
                    onOpenOptions = { showOptions = true },
                    onOpenSlideshow = { currentScreen = Screen.Slideshow },
                    onPrintContactSheet = onPrintContactSheet,
                    onSaveContactSheetAsJpeg = onSaveContactSheetAsJpeg,
                    onPrint = onPrint,
                    onOpenFile = onOpenFile,
                    onMovePhotoToFolder = onMovePhotoToFolder,
                    onMovePhotoToKnownFolder = onMovePhotoToKnownFolder,
                    onSaveDestructive = onSaveDestructive,
                    externalFolderManagerTrigger = menuFolderManagerTrigger,
                    externalDuplicatesTrigger = menuDuplicatesTrigger,
                    externalExportTrigger = menuExportTrigger,
                    externalAdjustDateTrigger = menuAdjustDateTrigger,
                    externalContactSheetTrigger = menuContactSheetTrigger,
                    externalPrintTrigger = menuPrintTrigger
                )
                is Screen.Edit -> {
                    val editViewModel = remember(screen.photoId) {
                        EditViewModel(dependencies.db, screen.photoId)
                    }
                    EditScreen(
                        viewModel = editViewModel,
                        onBack = { currentScreen = Screen.Library },
                        peopleViewModel = peopleViewModel
                    )
                }
                is Screen.People -> PeopleScreen(
                    viewModel = peopleViewModel,
                    onBack = { currentScreen = Screen.Library }
                )
                is Screen.Albums -> AlbumsScreen(
                    viewModel = albumsViewModel,
                    onBack = { currentScreen = Screen.Library }
                )
                is Screen.Slideshow -> {
                    val slideshowViewModel = remember(photos) {
                        SlideshowViewModel(photos, dependencies.settings)
                    }
                    SlideshowScreen(
                        viewModel = slideshowViewModel,
                        onBack = { currentScreen = Screen.Library }
                    )
                }
            }

            if (showOptions) {
                OptionsDialog(
                    settings = dependencies.settings,
                    onDismiss = { showOptions = false }
                )
            }

            if (showAbout) {
                AlertDialog(
                    onDismissRequest = { showAbout = false },
                    title = { Text("About CasaPic") },
                    text = { Text("CasaPic — A modern Picasa-inspired photo manager.\nBuilt with Kotlin Multiplatform + Compose Multiplatform.") },
                    confirmButton = {
                        TextButton(onClick = { showAbout = false }) { Text("OK") }
                    }
                )
            }
        }
    }
}
