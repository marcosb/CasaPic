package com.metitem.casapic.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.metitem.casapic.model.DetectedFace
import com.metitem.casapic.model.FaceRect
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

actual fun createFaceDetector(): FaceDetector = OnnxFaceDetector()

/**
 * ONNX-based face detector for desktop (JVM).
 *
 * Place model files in ~/.casapic/models/ to enable detection:
 *
 *   face_detection.onnx
 *     YOLOv8-face (e.g. yolov8n-face.onnx from akanametov/yolov8-face)
 *     Input:  images    [1, 3, 640, 640]  RGB normalised [0, 1]
 *     Output: output0   [1, 20, 8400]     cx, cy, w, h, conf, 5×(kx, ky, kv)
 *             (also accepts transposed [1, 8400, 20] from some exporters)
 *
 *   face_embedding.onnx
 *     ArcFace / MobileFaceNet style
 *     Input:  [1, 3, 112, 112]  RGB normalised to [−1, 1]
 *     Output: [1, N]            feature vector (will be L2-normalised)
 *
 * If either model file is absent the corresponding step is skipped gracefully.
 */
class OnnxFaceDetector : FaceDetector {

    private companion object {
        val MODELS_DIR: File = File(System.getProperty("user.home"), ".casapic/models")
        const val DET_SIZE = 640
        const val EMB_SIZE = 112
        const val CONF_THRESHOLD = 0.5f
        const val NMS_IOU_THRESHOLD = 0.4f
    }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val detSession: OrtSession? by lazy { loadSession("face_detection.onnx") }
    private val embSession: OrtSession? by lazy { loadSession("face_embedding.onnx") }

    private fun loadSession(name: String): OrtSession? = runCatching {
        val f = File(MODELS_DIR, name)
        if (!f.exists()) return@runCatching null
        env.createSession(f.absolutePath)
    }.getOrNull()

    override suspend fun detectFaces(imagePath: String): List<DetectedFace> {
        val session = detSession ?: return emptyList()
        val bytes = runCatching { File(imagePath).readBytes() }.getOrNull() ?: return emptyList()
        val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return emptyList()

        // Preprocess: resize to 640×640, CHW float [0, 1]
        val detTensor = imageToTensor(image, DET_SIZE, DET_SIZE, normalised = true)

        val rawBoxes: List<Pair<FaceRect, Float>>
        val inputName = session.inputNames.first()
        val shape = longArrayOf(1L, 3L, DET_SIZE.toLong(), DET_SIZE.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(detTensor), shape).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                val outTensor = result.first().value as OnnxTensor
                val outShape = outTensor.info.shape   // [1, dim1, dim2]
                val raw = outTensor.floatBuffer.array()
                val dim1 = outShape[1].toInt()
                val dim2 = outShape[2].toInt()
                rawBoxes = if (dim1 < dim2) {
                    // [1, features, anchors]: standard YOLOv8 export
                    parseFeaturesFirst(raw, dim2)
                } else {
                    // [1, anchors, features]: transposed export
                    parseAnchorsFirst(raw, dim1, dim2)
                }
            }
        }

        val kept = nms(rawBoxes)

        return kept.map { (box, conf) ->
            val embedding = runCatching { computeEmbedding(image, box) }.getOrNull()
            DetectedFace(
                boundingBox      = box,
                confidence       = conf,
                leftEyeOpenProb  = null,
                rightEyeOpenProb = null,
                smilingProb      = null,
                embedding        = embedding
            )
        }
    }

    // ── Detection output parsing ──────────────────────────────────────────────

    // Layout: data[featureIdx * anchors + anchorIdx]
    // Features: 0=cx, 1=cy, 2=w, 3=h, 4=conf, 5..=landmarks
    private fun parseFeaturesFirst(data: FloatArray, anchors: Int): List<Pair<FaceRect, Float>> {
        val result = mutableListOf<Pair<FaceRect, Float>>()
        for (a in 0 until anchors) {
            val conf = data[4 * anchors + a]
            if (conf < CONF_THRESHOLD) continue
            val cx = data[0 * anchors + a]
            val cy = data[1 * anchors + a]
            val w  = data[2 * anchors + a]
            val h  = data[3 * anchors + a]
            result += yoloPixelToRect(cx, cy, w, h) to conf
        }
        return result
    }

    // Layout: data[anchorIdx * features + featureIdx]
    private fun parseAnchorsFirst(
        data: FloatArray, anchors: Int, features: Int
    ): List<Pair<FaceRect, Float>> {
        val result = mutableListOf<Pair<FaceRect, Float>>()
        for (a in 0 until anchors) {
            val base = a * features
            val conf = data[base + 4]
            if (conf < CONF_THRESHOLD) continue
            result += yoloPixelToRect(data[base], data[base + 1], data[base + 2], data[base + 3]) to conf
        }
        return result
    }

    // YOLOv8 outputs pixel coords in the 640×640 input space → convert to relative [0, 1]
    private fun yoloPixelToRect(cx: Float, cy: Float, w: Float, h: Float): FaceRect {
        val s = DET_SIZE.toFloat()
        return FaceRect(
            left   = ((cx - w / 2f) / s).coerceIn(0f, 1f),
            top    = ((cy - h / 2f) / s).coerceIn(0f, 1f),
            right  = ((cx + w / 2f) / s).coerceIn(0f, 1f),
            bottom = ((cy + h / 2f) / s).coerceIn(0f, 1f)
        )
    }

    // ── Embedding ─────────────────────────────────────────────────────────────

    private fun computeEmbedding(image: Image, box: FaceRect): FloatArray? {
        val session = embSession ?: return null
        // Crop the face region; clamp coordinates to valid image bounds
        val crop = Rect.makeXYWH(
            (box.left   * image.width).coerceIn(0f, image.width.toFloat()),
            (box.top    * image.height).coerceIn(0f, image.height.toFloat()),
            ((box.right - box.left)  * image.width).coerceAtLeast(1f),
            ((box.bottom - box.top) * image.height).coerceAtLeast(1f)
        )
        val embTensor = imageToTensor(image, EMB_SIZE, EMB_SIZE, normalised = false, srcRect = crop)
        val inputName = session.inputNames.first()
        val shape = longArrayOf(1L, 3L, EMB_SIZE.toLong(), EMB_SIZE.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(embTensor), shape).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                l2Normalise((result.first().value as OnnxTensor).floatBuffer.array())
            }
        }
    }

    // ── Pixel preprocessing ───────────────────────────────────────────────────

    /**
     * Renders [image] (or a [srcRect] crop) into a [w]×[h] bitmap and returns a
     * CHW float tensor.
     *   normalised = true  → values [0, 1]    (YOLOv8 detection)
     *   normalised = false → values [−1, 1]   (ArcFace embedding)
     */
    private fun imageToTensor(
        image: Image, w: Int, h: Int, normalised: Boolean, srcRect: Rect? = null
    ): FloatArray {
        val bmp = Bitmap().apply { allocN32Pixels(w, h) }
        val canvas = Canvas(bmp)
        val src = srcRect ?: Rect.makeXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat())
        canvas.drawImageRect(image, src, Rect.makeXYWH(0f, 0f, w.toFloat(), h.toFloat()))

        // getColor returns 0xAARRGGBB regardless of internal bitmap format
        val tensor = FloatArray(3 * h * w)
        for (i in 0 until h * w) {
            val argb = bmp.getColor(i % w, i / w)
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8)  and 0xFF
            val b =  argb         and 0xFF
            if (normalised) {
                tensor[i]               = r / 255f
                tensor[h * w + i]       = g / 255f
                tensor[2 * h * w + i]   = b / 255f
            } else {
                // ArcFace: (x − 127.5) / 128
                tensor[i]               = (r - 127.5f) / 128f
                tensor[h * w + i]       = (g - 127.5f) / 128f
                tensor[2 * h * w + i]   = (b - 127.5f) / 128f
            }
        }
        return tensor
    }

    // ── NMS ───────────────────────────────────────────────────────────────────

    private fun nms(detections: List<Pair<FaceRect, Float>>): List<Pair<FaceRect, Float>> {
        val sorted = detections.sortedByDescending { it.second }.toMutableList()
        val kept = mutableListOf<Pair<FaceRect, Float>>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept += best
            sorted.removeAll { iou(best.first, it.first) > NMS_IOU_THRESHOLD }
        }
        return kept
    }

    private fun iou(a: FaceRect, b: FaceRect): Float {
        val iLeft   = max(a.left, b.left)
        val iTop    = max(a.top, b.top)
        val iRight  = min(a.right, b.right)
        val iBottom = min(a.bottom, b.bottom)
        val iArea = max(0f, iRight - iLeft) * max(0f, iBottom - iTop)
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        return iArea / (aArea + bArea - iArea).coerceAtLeast(1e-6f)
    }

    private fun l2Normalise(v: FloatArray): FloatArray {
        val norm = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return if (norm > 1e-6f) FloatArray(v.size) { v[it] / norm } else v
    }
}
