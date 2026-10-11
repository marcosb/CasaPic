package com.metitem.casapic.ml

import com.metitem.casapic.model.DetectedFace

interface FaceDetector {
    suspend fun detectFaces(imagePath: String): List<DetectedFace>
}

expect fun createFaceDetector(): FaceDetector
