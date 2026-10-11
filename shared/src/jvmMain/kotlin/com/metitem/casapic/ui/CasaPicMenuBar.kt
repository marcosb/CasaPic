package com.metitem.casapic.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.metitem.casapic.MenuBridge
import com.metitem.casapic.model.ThumbnailCaption

@Composable
fun FrameWindowScope.CasaPicMenuBar(
    bridge: MenuBridge,
    onExit: () -> Unit
) {
    MenuBar {
        // ── File ──────────────────────────────────────────────────────────────
        Menu("File", mnemonic = 'F') {
            Item("Add Folder to CasaPic...") { bridge.onAddFolder() }
            Separator()
            Item(
                "Export Picture to Folder...",
                enabled = bridge.hasSelection.value
            ) { bridge.onOpenExport() }
            Separator()
            Item(
                "Delete from Disk",
                shortcut = KeyShortcut(Key.Delete),
                enabled = bridge.hasSelection.value
            ) { bridge.onDeleteSelected() }
            Separator()
            Item(
                "Print...",
                shortcut = KeyShortcut(Key.P, ctrl = true)
            ) { bridge.onPrint() }
            Item(
                "Print Contact Sheet...",
                shortcut = KeyShortcut(Key.P, ctrl = true, shift = true)
            ) { bridge.onPrintContactSheet() }
            Separator()
            Item("Exit") { onExit() }
        }

        // ── Edit ──────────────────────────────────────────────────────────────
        Menu("Edit", mnemonic = 'E') {
            Item(
                "View and Edit",
                shortcut = KeyShortcut(Key.Three, ctrl = true),
                enabled = bridge.hasSingleSelection.value
            ) { bridge.onOpenPhoto() }
            Item(
                "Undo All Edits",
                enabled = bridge.hasSelection.value
            ) { bridge.onUndoAllEdits() }
            Separator()
            Item(
                "Select All",
                shortcut = KeyShortcut(Key.A, ctrl = true)
            ) { bridge.onSelectAll() }
            Item("Select Starred") { bridge.onSelectStarred() }
            Item(
                "Invert Selection",
                shortcut = KeyShortcut(Key.I, ctrl = true)
            ) { bridge.onInvertSelection() }
            Item(
                "Clear Selection",
                shortcut = KeyShortcut(Key.D, ctrl = true)
            ) { bridge.onClearSelection() }
            Separator()
            Item(
                "Reset Faces",
                enabled = bridge.hasSelection.value
            ) { bridge.onResetFaces() }
        }

        // ── View ──────────────────────────────────────────────────────────────
        Menu("View", mnemonic = 'V') {
            Item(
                "Small Thumbnails",
                shortcut = KeyShortcut(Key.One, ctrl = true)
            ) { bridge.onSetSmallThumbnails() }
            Item(
                "Normal Thumbnails",
                shortcut = KeyShortcut(Key.Two, ctrl = true)
            ) { bridge.onSetNormalThumbnails() }
            Separator()
            Item("People") { bridge.onOpenPeople() }
            Item(
                "Slideshow",
                shortcut = KeyShortcut(Key.F4, ctrl = true)
            ) { bridge.onOpenSlideshow() }
            Item("Albums") { bridge.onOpenAlbums() }
            Separator()
            CheckboxItem(
                "Hidden Pictures",
                checked = bridge.showHiddenPhotos.value
            ) { bridge.onToggleHiddenPhotos() }
            Separator()
            Menu("Thumbnail Caption") {
                RadioButtonItem(
                    "None",
                    selected = bridge.thumbnailCaption.value == ThumbnailCaption.NONE
                ) { bridge.onSetThumbnailCaption(ThumbnailCaption.NONE) }
                RadioButtonItem(
                    "Filename",
                    selected = bridge.thumbnailCaption.value == ThumbnailCaption.FILENAME
                ) { bridge.onSetThumbnailCaption(ThumbnailCaption.FILENAME) }
                RadioButtonItem(
                    "Capture Date",
                    selected = bridge.thumbnailCaption.value == ThumbnailCaption.DATE
                ) { bridge.onSetThumbnailCaption(ThumbnailCaption.DATE) }
                RadioButtonItem(
                    "Star Rating",
                    selected = bridge.thumbnailCaption.value == ThumbnailCaption.STARS
                ) { bridge.onSetThumbnailCaption(ThumbnailCaption.STARS) }
            }
        }

        // ── Folder ────────────────────────────────────────────────────────────
        Menu("Folder", mnemonic = 'O') {
            Item(
                "Print Contact Sheet...",
                shortcut = KeyShortcut(Key.P, ctrl = true, shift = true)
            ) { bridge.onPrintContactSheet() }
            Item("View Slideshow") { bridge.onOpenSlideshow() }
        }

        // ── Picture ───────────────────────────────────────────────────────────
        Menu("Picture", mnemonic = 'P') {
            Item(
                "Rotate Clockwise",
                shortcut = KeyShortcut(Key.R, ctrl = true),
                enabled = bridge.hasSelection.value
            ) { bridge.onRotateCW() }
            Item(
                "Rotate Counterclockwise",
                shortcut = KeyShortcut(Key.R, ctrl = true, shift = true),
                enabled = bridge.hasSelection.value
            ) { bridge.onRotateCCW() }
        }

        // ── Create ────────────────────────────────────────────────────────────
        Menu("Create", mnemonic = 'C') {
            Item("New Album...") { bridge.onOpenAlbums() }
            Item("Slideshow...") { bridge.onOpenSlideshow() }
        }

        // ── Tools ─────────────────────────────────────────────────────────────
        Menu("Tools", mnemonic = 'T') {
            Item("Folder Manager...") { bridge.onOpenFolderManager() }
            Separator()
            Item(
                "Adjust Date and Time...",
                enabled = bridge.hasSelection.value
            ) { bridge.onAdjustDate() }
            Separator()
            Menu("Experiments") {
                Item("Find Duplicate Photos...") { bridge.onFindDuplicates() }
            }
            Separator()
            Item("Options...") { bridge.onOpenOptions() }
        }

        // ── Help ──────────────────────────────────────────────────────────────
        Menu("Help", mnemonic = 'H') {
            Item("About CasaPic...") { bridge.onAbout() }
        }
    }
}
