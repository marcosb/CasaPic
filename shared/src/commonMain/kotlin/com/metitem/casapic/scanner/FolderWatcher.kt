package com.metitem.casapic.scanner

import com.metitem.casapic.model.WatchEvent

interface FolderWatcher {
    fun start(paths: List<String>, onChange: (WatchEvent) -> Unit)
    fun stop()
}

expect fun createFolderWatcher(): FolderWatcher
