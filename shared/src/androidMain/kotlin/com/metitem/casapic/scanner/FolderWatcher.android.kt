package com.metitem.casapic.scanner

import android.os.FileObserver
import com.metitem.casapic.model.WatchEvent
import java.io.File

actual fun createFolderWatcher(): FolderWatcher = AndroidFileWatcher()

class AndroidFileWatcher : FolderWatcher {
    private val observers = mutableListOf<FileObserver>()

    override fun start(paths: List<String>, onChange: (WatchEvent) -> Unit) {
        paths.forEach { path ->
            val observer = object : FileObserver(File(path), CREATE or MODIFY or DELETE) {
                override fun onEvent(event: Int, relPath: String?) {
                    if (relPath == null) return
                    val fullPath = "$path/$relPath"
                    when (event and ALL_EVENTS) {
                        CREATE -> onChange(WatchEvent.Created(fullPath))
                        MODIFY -> onChange(WatchEvent.Modified(fullPath))
                        DELETE -> onChange(WatchEvent.Deleted(fullPath))
                    }
                }
            }
            observer.startWatching()
            observers += observer
        }
    }

    override fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}
