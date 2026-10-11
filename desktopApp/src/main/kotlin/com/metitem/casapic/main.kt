package com.metitem.casapic

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.db.DatabaseDriverFactory
import com.metitem.casapic.image.FingerprintCalculator
import com.metitem.casapic.image.ThumbnailGenerationService
import com.metitem.casapic.ml.FaceDetectionService
import com.metitem.casapic.model.ScanMode
import com.metitem.casapic.model.ScanProgress
import com.metitem.casapic.model.ScanState
import com.metitem.casapic.model.WatchEvent
import com.metitem.casapic.MenuBridge
import com.metitem.casapic.image.SkiaImageOps
import com.metitem.casapic.print.ContactSheetPrinter
import com.metitem.casapic.print.PhotoPrinter
import com.metitem.casapic.scanner.FolderScanner
import com.metitem.casapic.scanner.createFolderWatcher
import com.metitem.casapic.settings.SettingsRepository
import com.metitem.casapic.ui.CasaPicMenuBar
import com.metitem.casapic.ui.pickFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun main() {
    val db = CasaPicDatabase(DatabaseDriverFactory().createDriver())
    val settings = SettingsRepository(db)
    val deps = AppDependencies(db = db, settings = settings)
    val homeDir = System.getProperty("user.home") ?: System.getenv("HOME") ?: ""
    val defaultWatchedPaths = listOf("Pictures", "Documents", "Desktop", "Videos")
        .map { "$homeDir${File.separator}$it" }
    val scanner = FolderScanner(db, settings)
    val faceService = FaceDetectionService(db, settings)
    val thumbnailService = ThumbnailGenerationService(db)
    val appScope = CoroutineScope(Dispatchers.Default)

    // ── Face detection (existing) ─────────────────────────────────────────────
    faceService.start(scanner.faceDetectionQueue)

    // ── Fingerprint queue consumer ────────────────────────────────────────────
    // FolderScanner enqueues (photoId, path) for every new/updated photo;
    // consume here so dHash fingerprints are always computed.
    appScope.launch(Dispatchers.IO) {
        scanner.fingerprintQueue.receiveAsFlow().collect { (photoId, path) ->
            val hash = FingerprintCalculator.compute(File(path))
            if (hash != null) db.casaPicQueries.updatePhotoFingerprint(hash, photoId)
        }
    }

    // ── Thumbnail generation loop ─────────────────────────────────────────────
    // Polls every 5 s; generatePending() drains photos with thumbnail_path IS NULL
    // and breaks when the batch is empty, so we restart after a short delay.
    appScope.launch(Dispatchers.IO) {
        while (true) {
            thumbnailService.generatePending().collect {}
            delay(5_000)
        }
    }

    // ── File-size backfill ────────────────────────────────────────────────────
    // Fixes any photos that were stored with NULL or 0 file_size (legacy DB state).
    appScope.launch(Dispatchers.IO) {
        val stale = db.casaPicQueries.getPhotosWithMissingFileSize().executeAsList()
        if (stale.isNotEmpty()) {
            db.casaPicQueries.transaction {
                for (row in stale) {
                    val size = File(row.file_path).length()
                    if (size > 0) db.casaPicQueries.updateFileSize(size, row.id)
                }
            }
        }
    }

    // ── File watcher ──────────────────────────────────────────────────────────
    // stop() closes the underlying WatchService, so re-registration after adding
    // a new folder requires creating a fresh NioFileWatcher instance.
    var watcher = createFolderWatcher()

    val watcherHandler: (WatchEvent) -> Unit = { event ->
        appScope.launch(Dispatchers.IO) {
            scanner.processEvent(event).collect {}
        }
    }

    fun restartWatcher() {
        val paths = db.casaPicQueries.getAllFolders().executeAsList()
            .filter { it.scan_mode != ScanMode.NONE && it.scan_mode != ScanMode.REMOVE }
            .map { it.path }
        watcher.stop()
        watcher = createFolderWatcher()
        if (paths.isNotEmpty()) watcher.start(paths, watcherHandler)
    }

    restartWatcher()

    application {
        Window(
            onCloseRequest = {
                watcher.stop()
                exitApplication()
            },
            title = "CasaPic",
        ) {
            val menuBridge = androidx.compose.runtime.remember { MenuBridge() }
            CasaPicMenuBar(menuBridge) { exitApplication() }

            // OS → App file drop: any files dropped from the OS file manager are scanned
            androidx.compose.runtime.LaunchedEffect(Unit) {
                window.dropTarget = java.awt.dnd.DropTarget(window,
                    object : java.awt.dnd.DropTargetAdapter() {
                        override fun drop(event: java.awt.dnd.DropTargetDropEvent) {
                            event.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                            val files = runCatching {
                                @Suppress("UNCHECKED_CAST")
                                event.transferable.getTransferData(
                                    java.awt.datatransfer.DataFlavor.javaFileListFlavor
                                ) as? List<File>
                            }.getOrNull() ?: emptyList()
                            val parentDirs = files.map { it.parent }.distinct()
                            parentDirs.forEach { dir ->
                                appScope.launch(Dispatchers.IO) {
                                    scanner.scanFolder(dir).collect {}
                                    thumbnailService.generatePending().collect {}
                                }
                            }
                            event.dropComplete(true)
                        }
                    }
                )
            }

            App(
                dependencies = deps,
                menuBridge = menuBridge,
                defaultWatchedPaths = defaultWatchedPaths,
                onAddFolderByPath = { path ->
                    appScope.launch(Dispatchers.IO) {
                        scanner.scanFolder(path).collect {}
                        restartWatcher()
                        thumbnailService.generatePending().collect {}
                    }
                },
                onPrint = { photos, options ->
                    appScope.launch(Dispatchers.IO) {
                        PhotoPrinter.print(photos, options)
                    }
                },
                onPrintContactSheet = { photos, columns, title ->
                    appScope.launch(Dispatchers.IO) {
                        ContactSheetPrinter.print(photos, columns, title)
                    }
                },
                onSaveContactSheetAsJpeg = { photos, columns, title ->
                    val dialog = java.awt.FileDialog(
                        null as java.awt.Frame?,
                        "Save Contact Sheet",
                        java.awt.FileDialog.SAVE
                    ).apply {
                        file = "${title.ifBlank { "contact_sheet" }}.jpg"
                        isVisible = true
                    }
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        val outputPath = File(dir, file).absolutePath
                            .let { if (it.endsWith(".jpg", ignoreCase = true)) it else "$it.jpg" }
                        appScope.launch(Dispatchers.IO) {
                            ContactSheetPrinter.exportToFile(photos, outputPath, columns)
                        }
                    }
                },
                onOpenFile = { filePath ->
                    runCatching { Desktop.getDesktop().open(File(filePath)) }
                },
                onMovePhotoToFolder = { photoId, currentPath ->
                    appScope.launch(Dispatchers.IO) {
                        val destDir = pickFolder() ?: return@launch
                        val srcFile = File(currentPath)
                        val destFile = File(destDir, srcFile.name)
                        runCatching {
                            Files.move(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }.onSuccess {
                            val destFolder = db.casaPicQueries.getFolderByPath(destDir).executeAsOneOrNull()
                            db.casaPicQueries.updatePhotoFilePath(destFile.absolutePath, destFolder?.id, photoId)
                        }
                    }
                },
                onMovePhotoToKnownFolder = { photoId, currentPath, targetFolderPath ->
                    appScope.launch(Dispatchers.IO) {
                        val srcFile = File(currentPath)
                        val destFile = File(targetFolderPath, srcFile.name)
                        runCatching {
                            Files.move(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                        }.onSuccess {
                            val destFolder = db.casaPicQueries.getFolderByPath(targetFolderPath).executeAsOneOrNull()
                            db.casaPicQueries.updatePhotoFilePath(destFile.absolutePath, destFolder?.id, photoId)
                        }
                    }
                },
                onSaveDestructive = { photoId ->
                    appScope.launch(Dispatchers.IO) {
                        val photo = db.casaPicQueries.getPhotoById(photoId).executeAsOneOrNull() ?: return@launch
                        val edits = db.casaPicQueries.getActiveEditsForPhoto(photoId).executeAsList()
                        runCatching {
                            SkiaImageOps.exportToFile(photo.file_path, edits, photo.file_path)
                        }.onSuccess {
                            db.casaPicQueries.undoAllEditsForPhoto(photoId)
                            db.casaPicQueries.updatePhotoThumbnail(null, photoId)
                        }
                    }
                },
                onAddFolder = { viewModel ->
                    appScope.launch(Dispatchers.IO) {
                        val path = pickFolder() ?: return@launch
                        val folderName = File(path).name
                        var added = 0
                        var updated = 0
                        var scanned = 0
                        scanner.scanFolder(path).collect { progress ->
                            when (progress) {
                                is ScanProgress.PhotoAdded -> { added++; scanned++ }
                                is ScanProgress.PhotoUpdated -> { updated++; scanned++ }
                                is ScanProgress.BatchComplete -> {
                                    viewModel.updateScanState(
                                        ScanState.Scanning(scanned, scanned + 1, folderName)
                                    )
                                }
                                else -> {}
                            }
                        }
                        viewModel.updateScanState(ScanState.Done(added, updated, 0))
                        val newFolder = db.casaPicQueries.getFolderByPath(path).executeAsOneOrNull()
                        if (newFolder != null) viewModel.selectFolder(newFolder.id)
                        // Include new folder in file watcher and kick thumbnail generation
                        restartWatcher()
                        thumbnailService.generatePending().collect {}
                    }
                }
            )
        }
    }
}
