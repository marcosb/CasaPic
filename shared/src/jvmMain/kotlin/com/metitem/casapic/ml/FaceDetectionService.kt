package com.metitem.casapic.ml

import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.model.AlbumKind
import com.metitem.casapic.model.FaceDetectionResult
import com.metitem.casapic.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.math.sqrt

class FaceDetectionService(
    private val db: CasaPicDatabase,
    private val settings: SettingsRepository,
    private val detector: FaceDetector = createFaceDetector()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private var pendingClusterCount = 0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(queue: Channel<Pair<Long, String>>) {
        scope.launch {
            queue.receiveAsFlow()
                .flatMapMerge(concurrency = 2) { (photoId, path) ->
                    flow {
                        if (!settings.faceDetectionEnabled) return@flow
                        _queueSize.value = _queueSize.value + 1
                        val result = runCatching {
                            val faces = detector.detectFaces(path)
                            val now = Clock.System.now().toEpochMilliseconds()
                            for (face in faces) {
                                val embeddingBlob = face.embedding?.let { arr ->
                                    val buf = java.nio.ByteBuffer.allocate(arr.size * 4)
                                    arr.forEach { buf.putFloat(it) }
                                    buf.array()
                                }
                                db.casaPicQueries.insertFaceDetection(
                                    photo_id = photoId,
                                    face_group_id = null,
                                    box_left = face.boundingBox.left.toDouble(),
                                    box_top = face.boundingBox.top.toDouble(),
                                    box_right = face.boundingBox.right.toDouble(),
                                    box_bottom = face.boundingBox.bottom.toDouble(),
                                    confidence = face.confidence.toDouble(),
                                    left_eye_open_prob = face.leftEyeOpenProb?.toDouble(),
                                    right_eye_open_prob = face.rightEyeOpenProb?.toDouble(),
                                    smiling_prob = face.smilingProb?.toDouble(),
                                    embedding = embeddingBlob,
                                    tracking_id = null,
                                    is_manual = 0L
                                )
                            }
                            if (faces.isNotEmpty()) {
                                pendingClusterCount++
                                FaceDetectionResult.FacesFound(photoId, faces.size)
                            } else {
                                FaceDetectionResult.NoFaces(photoId)
                            }
                        }.getOrElse { FaceDetectionResult.DetectionError(photoId, it) }

                        _queueSize.value = (_queueSize.value - 1).coerceAtLeast(0)
                        emit(result)

                        // Debounced clustering: run after every 10 detections
                        if (pendingClusterCount >= 10) {
                            pendingClusterCount = 0
                            clusterFaces()
                        }
                    }
                }
                .collect { /* results handled inside flow */ }
        }
    }

    private suspend fun clusterFaces() {
        val threshold = settings.faceClusterThreshold
        val total = db.casaPicQueries.countFaceDetectionsWithEmbeddings().executeAsOne()
        val pageSize = 200L
        var offset = 0L
        val all = mutableListOf<Pair<Long, FloatArray>>() // detectionId → embedding

        while (offset < total) {
            val page = db.casaPicQueries.getFaceDetectionsWithEmbeddings(pageSize, offset).executeAsList()
            for (row in page) {
                val bytes = row.embedding
                val floats = FloatArray(bytes.size / 4)
                val buf = java.nio.ByteBuffer.wrap(bytes)
                for (i in floats.indices) floats[i] = buf.float
                all.add(row.id to floats)
            }
            offset += pageSize
        }

        if (all.isEmpty()) return

        // Connected-components clustering
        val groups = UnionFind(all.size)
        for (i in all.indices) {
            for (j in i + 1 until all.size) {
                if (cosineSimilarity(all[i].second, all[j].second) >= threshold) {
                    groups.union(i, j)
                }
            }
        }

        // Map roots → face group album IDs
        val now = Clock.System.now().toEpochMilliseconds()
        val rootToGroupId = mutableMapOf<Int, Long>()
        for (i in all.indices) {
            val root = groups.find(i)
            if (root !in rootToGroupId) {
                // Create a new FACE_GROUP album if none exists
                db.casaPicQueries.insertAlbum(
                    name = "Group ${rootToGroupId.size + 1}",
                    description = "",
                    created_date_millis = now,
                    modified_date_millis = now,
                    kind = AlbumKind.FACE_GROUP
                )
                val groupId = db.casaPicQueries.lastInsertId().executeAsOne()
                rootToGroupId[root] = groupId
            }
            val groupId = rootToGroupId[root]!!
            db.casaPicQueries.updateFaceGroupId(groupId, all[i].first)
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom < 1e-8f) 0f else dot / denom
    }
}

private class UnionFind(n: Int) {
    private val parent = IntArray(n) { it }
    private val rank = IntArray(n) { 0 }

    fun find(x: Int): Int {
        if (parent[x] != x) parent[x] = find(parent[x])
        return parent[x]
    }

    fun union(x: Int, y: Int) {
        val rx = find(x); val ry = find(y)
        if (rx == ry) return
        if (rank[rx] < rank[ry]) parent[rx] = ry
        else if (rank[rx] > rank[ry]) parent[ry] = rx
        else { parent[ry] = rx; rank[rx]++ }
    }
}
