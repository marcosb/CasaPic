package com.metitem.casapic.scanner

import com.metitem.casapic.model.WatchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.util.concurrent.ConcurrentHashMap

actual fun createFolderWatcher(): FolderWatcher = NioFileWatcher()

class NioFileWatcher : FolderWatcher {
    private val watchService = FileSystems.getDefault().newWatchService()
    private val watchedKeys = ConcurrentHashMap<WatchKey, Path>()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var watchJob: Job? = null

    override fun start(paths: List<String>, onChange: (WatchEvent) -> Unit) {
        paths.forEach { registerRecursively(Paths.get(it)) }
        watchJob = scope.launch {
            while (true) {
                val key = watchService.take()
                val dir = watchedKeys[key] ?: run { key.cancel(); continue }

                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue

                    @Suppress("UNCHECKED_CAST")
                    val relativePath = (event as java.nio.file.WatchEvent<Path>).context()
                    val fullPath = dir.resolve(relativePath)

                    when {
                        kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(fullPath) -> {
                            registerRecursively(fullPath)
                        }
                        kind == StandardWatchEventKinds.ENTRY_CREATE -> {
                            onChange(WatchEvent.Created(fullPath.toString()))
                        }
                        kind == StandardWatchEventKinds.ENTRY_MODIFY -> {
                            if (!Files.isDirectory(fullPath)) {
                                onChange(WatchEvent.Modified(fullPath.toString()))
                            }
                        }
                        kind == StandardWatchEventKinds.ENTRY_DELETE -> {
                            onChange(WatchEvent.Deleted(fullPath.toString()))
                        }
                    }
                }

                if (!key.reset()) {
                    watchedKeys.remove(key)
                }
            }
        }
    }

    override fun stop() {
        watchJob?.cancel()
        watchJob = null
        watchedKeys.keys.forEach { it.cancel() }
        watchedKeys.clear()
        runCatching { watchService.close() }
    }

    private fun registerRecursively(path: Path) {
        if (!Files.isDirectory(path)) return
        try {
            val key = path.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            )
            watchedKeys[key] = path
            Files.newDirectoryStream(path).use { stream ->
                stream.filter { Files.isDirectory(it) }.forEach { registerRecursively(it) }
            }
        } catch (_: Exception) {}
    }
}
