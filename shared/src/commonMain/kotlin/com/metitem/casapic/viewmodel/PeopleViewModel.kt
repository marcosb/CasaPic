package com.metitem.casapic.viewmodel

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.metitem.casapic.db.Albums
import com.metitem.casapic.db.CasaPicDatabase
import com.metitem.casapic.model.AlbumKind
import com.metitem.casapic.db.GetFaceDetectionsWithEmbeddings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModel(private val db: CasaPicDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val faceGroups: StateFlow<List<Albums>> =
        db.casaPicQueries.getAlbumsByKind(AlbumKind.FACE_GROUP)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    val unassignedFaces: StateFlow<List<GetFaceDetectionsWithEmbeddings>> = run {
        val flow = MutableStateFlow<List<GetFaceDetectionsWithEmbeddings>>(emptyList())
        scope.launch {
            // Query face_detections with no face_group_id
            flow.value = db.casaPicQueries
                .getFaceDetectionsWithEmbeddings(200L, 0L)
                .executeAsList()
                .filter { it.face_group_id == null }
        }
        flow.asStateFlow()
    }

    fun selectGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
    }

    fun renameGroup(groupId: Long, newName: String) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.updateAlbumName(
                newName,
                Clock.System.now().toEpochMilliseconds(),
                groupId
            )
        }
    }

    fun deleteGroup(groupId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.deleteAlbum(groupId)
        }
    }

    fun mergeGroups(sourceGroupId: Long, targetGroupId: Long) {
        scope.launch(Dispatchers.Default) {
            // Move all face detections from source → target
            val detections = db.casaPicQueries
                .getFaceDetectionsWithEmbeddings(1000L, 0L)
                .executeAsList()
                .filter { it.face_group_id == sourceGroupId }
            for (det in detections) {
                db.casaPicQueries.updateFaceGroupId(targetGroupId, det.id)
            }
            db.casaPicQueries.deleteAlbum(sourceGroupId)
        }
    }

    fun assignFaceToGroup(detectionId: Long, groupId: Long) {
        scope.launch(Dispatchers.Default) {
            db.casaPicQueries.updateFaceGroupId(groupId, detectionId)
        }
    }

    fun createNewGroup(name: String, onCreated: (Long) -> Unit = {}) {
        scope.launch(Dispatchers.Default) {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            db.casaPicQueries.insertAlbum(
                name = name,
                description = "",
                created_date_millis = now,
                modified_date_millis = now,
                kind = AlbumKind.FACE_GROUP
            )
            val groupId = db.casaPicQueries.lastInsertId().executeAsOne()
            onCreated(groupId)
        }
    }
}
