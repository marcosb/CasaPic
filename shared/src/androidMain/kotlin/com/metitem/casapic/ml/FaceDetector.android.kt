package com.metitem.casapic.ml

import com.metitem.casapic.model.DetectedFace
import com.metitem.casapic.model.FaceRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createFaceDetector(): FaceDetector = MlKitFaceDetector()

class MlKitFaceDetector : FaceDetector {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val detector = FaceDetection.getClient(options)

    override suspend fun detectFaces(imagePath: String): List<DetectedFace> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromFilePath(
                // Context injection is handled at app level — placeholder here
                // In practice, this would use a provided Context
                throw UnsupportedOperationException("Android face detector requires Context injection"),
                android.net.Uri.fromFile(File(imagePath))
            )
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val results = faces.map { face ->
                        val box = face.boundingBox
                        val imgW = image.width.toFloat().coerceAtLeast(1f)
                        val imgH = image.height.toFloat().coerceAtLeast(1f)
                        DetectedFace(
                            boundingBox = FaceRect(
                                left = box.left / imgW,
                                top = box.top / imgH,
                                right = box.right / imgW,
                                bottom = box.bottom / imgH
                            ),
                            confidence = face.trackingId?.toFloat() ?: 0.9f,
                            leftEyeOpenProb = face.leftEyeOpenProbability,
                            rightEyeOpenProb = face.rightEyeOpenProbability,
                            smilingProb = face.smilingProbability,
                            embedding = null
                        )
                    }
                    cont.resume(results)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
