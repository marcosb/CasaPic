package com.metitem.casapic.model

data class DetectedFace(
    val boundingBox: FaceRect,
    val confidence: Float,
    val leftEyeOpenProb: Float?,
    val rightEyeOpenProb: Float?,
    val smilingProb: Float?,
    val embedding: FloatArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedFace) return false
        return boundingBox == other.boundingBox &&
            confidence == other.confidence &&
            leftEyeOpenProb == other.leftEyeOpenProb &&
            rightEyeOpenProb == other.rightEyeOpenProb &&
            smilingProb == other.smilingProb &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = boundingBox.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + (leftEyeOpenProb?.hashCode() ?: 0)
        result = 31 * result + (rightEyeOpenProb?.hashCode() ?: 0)
        result = 31 * result + (smilingProb?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

data class FaceRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun FloatArray?.contentEquals(other: FloatArray?): Boolean {
    if (this == null && other == null) return true
    if (this == null || other == null) return false
    return this.contentEquals(other)
}

sealed class FaceDetectionResult {
    data class FacesFound(val photoId: Long, val count: Int) : FaceDetectionResult()
    data class NoFaces(val photoId: Long) : FaceDetectionResult()
    data class DetectionError(val photoId: Long, val cause: Throwable) : FaceDetectionResult()
}
