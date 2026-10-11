package com.metitem.casapic.model

sealed class ScanProgress {
    data class PhotoAdded(val photoId: Long, val path: String) : ScanProgress()
    data class PhotoUpdated(val photoId: Long) : ScanProgress()
    data class PhotoDeleted(val path: String) : ScanProgress()
    data class Error(val path: String, val cause: Throwable) : ScanProgress()
    data class BatchComplete(val added: Int, val updated: Int) : ScanProgress()
}

sealed class ScanState {
    data object Idle : ScanState()
    data class Scanning(val current: Int, val total: Int, val folderName: String) : ScanState()
    data class Done(val added: Int, val updated: Int, val errors: Int) : ScanState()
}

sealed class WatchEvent {
    data class Created(val path: String) : WatchEvent()
    data class Modified(val path: String) : WatchEvent()
    data class Deleted(val path: String) : WatchEvent()
}
