package com.metitem.casapic

import androidx.compose.runtime.mutableStateOf
import com.metitem.casapic.model.ThumbnailCaption

class MenuBridge {
    // Observable state — read by MenuBar's checked/radio items; updated via SideEffect in App.kt.
    val showHiddenPhotos = mutableStateOf(false)
    val thumbnailSizeNormal = mutableStateOf(true)
    val thumbnailCaption = mutableStateOf(ThumbnailCaption.DATE)
    val hasSelection = mutableStateOf(false)
    val hasSingleSelection = mutableStateOf(false)

    // Callbacks populated by App.kt via SideEffect so they always have the latest closures.
    var onAddFolder: () -> Unit = {}
    var onOpenFolderManager: () -> Unit = {}
    var onOpenOptions: () -> Unit = {}
    var onOpenPeople: () -> Unit = {}
    var onOpenAlbums: () -> Unit = {}
    var onOpenSlideshow: () -> Unit = {}
    var onToggleHiddenPhotos: () -> Unit = {}
    var onSetSmallThumbnails: () -> Unit = {}
    var onSetNormalThumbnails: () -> Unit = {}
    var onSetThumbnailCaption: (ThumbnailCaption) -> Unit = {}
    var onSelectAll: () -> Unit = {}
    var onSelectStarred: () -> Unit = {}
    var onInvertSelection: () -> Unit = {}
    var onClearSelection: () -> Unit = {}
    var onRotateCW: () -> Unit = {}
    var onRotateCCW: () -> Unit = {}
    var onUndoAllEdits: () -> Unit = {}
    var onResetFaces: () -> Unit = {}
    var onOpenExport: () -> Unit = {}
    var onAdjustDate: () -> Unit = {}
    var onFindDuplicates: () -> Unit = {}
    var onPrintContactSheet: () -> Unit = {}
    var onPrint: () -> Unit = {}
    var onOpenPhoto: () -> Unit = {}
    var onDeleteSelected: () -> Unit = {}
    var onAbout: () -> Unit = {}
}
