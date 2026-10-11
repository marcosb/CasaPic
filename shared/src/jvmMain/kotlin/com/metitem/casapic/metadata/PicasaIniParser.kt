package com.metitem.casapic.metadata

import java.io.File

data class ParsedPicasaIni(
    val folderName: String? = null,
    val folderDescription: String? = null,
    val photos: Map<String, PicasaPhotoEntry> = emptyMap(),
    val albums: List<PicasaAlbumEntry> = emptyList(),
    val contacts: Map<String, String> = emptyMap()   // contactId → displayName
)

data class PicasaPhotoEntry(
    val star: Boolean = false,
    val caption: String? = null,
    val keywords: List<String> = emptyList(),
    val edits: List<PhotoEdit> = emptyList(),
    val faces: List<FaceEntry> = emptyList(),
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val albumTokens: List<String> = emptyList()
)

data class PicasaAlbumEntry(
    val token: String,
    val name: String,
    val description: String? = null,
    val date: String? = null
)

object PicasaIniParser {

    private val UNASSIGNED_CONTACT = "ffffffffffffffff"

    fun parse(file: File): ParsedPicasaIni? {
        if (!file.exists()) return null
        return runCatching { parseLines(file.readLines(Charsets.UTF_8)) }.getOrNull()
    }

    private fun parseLines(lines: List<String>): ParsedPicasaIni {
        var folderName: String? = null
        var folderDescription: String? = null
        val photos = mutableMapOf<String, MutableMap<String, String>>()
        val albums = mutableListOf<MutableMap<String, String>>()
        val contacts = mutableMapOf<String, String>()

        var currentSection = ""
        var currentAlbumMap: MutableMap<String, String>? = null
        var inContacts = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.startsWith(";") || line.startsWith("#") -> continue

                line.startsWith("[") && line.endsWith("]") -> {
                    val section = line.substring(1, line.length - 1)
                    currentSection = section
                    currentAlbumMap = null
                    inContacts = false
                    when {
                        section.startsWith(".album:") -> {
                            val map = mutableMapOf<String, String>()
                            albums += map
                            currentAlbumMap = map
                        }
                        section == "Contacts" || section == "Contacts2" -> inContacts = true
                        section != "Picasa" -> {
                            photos.getOrPut(section) { mutableMapOf() }
                        }
                    }
                }

                else -> {
                    val eqIdx = line.indexOf('=')
                    if (eqIdx < 0) continue
                    val key = line.substring(0, eqIdx).trim()
                    val value = line.substring(eqIdx + 1).trim()

                    when {
                        inContacts -> {
                            if (key != UNASSIGNED_CONTACT) {
                                val displayName = value.substringBefore(",").removeSuffix("_lh")
                                    .ifEmpty { value.substringBefore(",") }
                                contacts[key] = displayName
                            }
                        }
                        currentAlbumMap != null -> currentAlbumMap[key] = value
                        currentSection == "Picasa" -> when (key) {
                            "name" -> folderName = value
                            "description" -> folderDescription = value
                        }
                        currentSection.isNotEmpty() && currentSection != "Picasa" -> {
                            photos.getOrPut(currentSection) { mutableMapOf() }[key] = value
                        }
                    }
                }
            }
        }

        val parsedPhotos = photos.mapValues { (_, map) -> parsePhotoEntry(map) }
        val parsedAlbums = albums.mapNotNull { parseAlbumEntry(it) }

        return ParsedPicasaIni(
            folderName = folderName,
            folderDescription = folderDescription,
            photos = parsedPhotos,
            albums = parsedAlbums,
            contacts = contacts
        )
    }

    private fun parsePhotoEntry(map: Map<String, String>): PicasaPhotoEntry {
        val star = map["star"]?.trim()?.lowercase() == "yes"
        val caption = map["caption"]?.ifBlank { null }
        val keywords = map["keywords"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()
        val albumTokens = map["albums"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()

        val geotag = map["geotag"]?.let { parseGeotag(it) }
        val gpsLat = geotag?.first
        val gpsLng = geotag?.second

        val edits = mutableListOf<PhotoEdit>()

        // Standalone rotate key
        val rotateN = map["rotate"]?.toIntOrNull() ?: 0
        if (rotateN != 0) {
            edits += PhotoEdit(rotate = RotateEdit(degrees = rotateN * 90))
        }

        // Standalone crop64 key
        map["crop64"]?.let { raw ->
            parseCrop64(raw)?.let { edits += PhotoEdit(crop = it) }
        }

        // Filters chain: "key1=val1;key2=val2;…"
        val rawFilters = map["filters"] ?: ""
        for (token in rawFilters.split(";").map { it.trim() }.filter { it.isNotEmpty() }) {
            val eqIdx = token.indexOf('=')
            val fKey = if (eqIdx >= 0) token.substring(0, eqIdx) else token
            val fVal = if (eqIdx >= 0) token.substring(eqIdx + 1) else ""
            parseFilterToken(fKey, fVal)?.let { edits += it }
        }

        // Faces: "rect64(hex),contactId;rect64(hex),contactId;…"
        val faces = map["faces"]?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { parseFaceEntry(it) }
            ?: emptyList()

        return PicasaPhotoEntry(
            star = star,
            caption = caption,
            keywords = keywords,
            edits = edits,
            faces = faces,
            gpsLatitude = gpsLat,
            gpsLongitude = gpsLng,
            albumTokens = albumTokens
        )
    }

    private fun parseFilterToken(key: String, value: String): PhotoEdit? = when (key) {
        "crop64"    -> parseCrop64(value)?.let { PhotoEdit(crop = it) }
        "tilt"      -> parseTilt(value)?.let { PhotoEdit(tilt = it) }
        "redeye"    -> parseRedEye(value)?.let { PhotoEdit(redeye = it) }
        "finetune2" -> parseFinetune2(value)?.let { PhotoEdit(finetune = it) }
        "enhance"   -> PhotoEdit(enhance = EnhanceEdit(presence = true))
        "autolight" -> PhotoEdit(autolight = AutoLightEdit(presence = true))
        "autocolor" -> PhotoEdit(autocolor = AutoColorEdit(presence = true))
        "sepia"     -> PhotoEdit(sepia = SepiaEdit(strength = value.toFloatOrNull() ?: 1.0f))
        "bw"        -> PhotoEdit(bw = BwEdit(strength = value.toFloatOrNull() ?: 1.0f))
        "saturation"-> value.toFloatOrNull()?.let { PhotoEdit(saturation = SaturationEdit(value_ = it)) }
        "sharpness" -> value.toFloatOrNull()?.let { PhotoEdit(sharpness = SharpnessEdit(value_ = it)) }
        "softfocus" -> value.toFloatOrNull()?.let { PhotoEdit(softfocus = SoftFocusEdit(strength = it)) }
        "filmgrain" -> value.toFloatOrNull()?.let { PhotoEdit(filmgrain = FilmGrainEdit(strength = it)) }
        "vignette"  -> parseVignette(value)?.let { PhotoEdit(vignette = it) }
        "tintvalue",
        "tint"      -> parseTint(value)?.let { PhotoEdit(tint = it) }
        "lomo"      -> parseLomo(value)?.let { PhotoEdit(lomo = it) }
        "retrolux"  -> PhotoEdit(retrolux = RetroluxEdit(strength = value.toFloatOrNull() ?: 0.5f))
        "glow"      -> PhotoEdit(glow = GlowEdit(strength = value.toFloatOrNull() ?: 0.5f))
        else        -> PhotoEdit(unknown = UnknownEdit(type = key, raw_params = value))
    }

    // rect64 format: "0(16hexchars)" — 64-bit value encoding left/top/right/bottom as 16-bit words
    internal fun parseRect64(raw: String): CropEdit? = runCatching {
        val hex = raw.substringAfter("(").substringBefore(")").padStart(16, '0')
        val long = hex.toLong(16)
        val left   = ((long shr 48) and 0xFFFF).toFloat() / 65536f
        val top    = ((long shr 32) and 0xFFFF).toFloat() / 65536f
        val right  = ((long shr 16) and 0xFFFF).toFloat() / 65536f
        val bottom = ( long         and 0xFFFF).toFloat() / 65536f
        CropEdit(left = left, top = top, right = right, bottom = bottom)
    }.getOrNull()

    private fun parseCrop64(raw: String): CropEdit? = parseRect64(raw)

    private fun parseTilt(value: String): TiltEdit? = runCatching {
        val parts = value.split(";")
        val angle = parts.getOrNull(0)?.toFloat() ?: return null
        val scale = parts.getOrNull(1)?.toFloatOrNull() ?: 1.0f
        TiltEdit(angle = angle, scale = scale)
    }.getOrNull()

    // finetune2: 5 semicolon-separated signed 32-bit hex integers
    // Each encodes a -1.0 to +1.0 float: floatVal = signedInt / 2_147_483_648f
    private fun parseFinetune2(value: String): FinetuneEdit? = runCatching {
        val parts = value.split(";")
        fun hexToFloat(s: String?) = s?.trim()?.let {
            it.toLong(16).let { l ->
                val signed = if (l > 0x7FFFFFFF) (l - 0x100000000L).toInt() else l.toInt()
                signed.toFloat() / 2_147_483_648f
            }
        } ?: 0f
        FinetuneEdit(
            shadows    = hexToFloat(parts.getOrNull(0)),
            highlights = hexToFloat(parts.getOrNull(1)),
            color_temp = hexToFloat(parts.getOrNull(2)),
            brightness = hexToFloat(parts.getOrNull(3)),
            saturation = hexToFloat(parts.getOrNull(4))
        )
    }.getOrNull()

    // redeye: "x1,y1,r1;x2,y2,r2;…" where coords are relative (0.0–1.0)
    private fun parseRedEye(value: String): RedEyeEdit? = runCatching {
        val regions = value.split(";").mapNotNull { token ->
            val parts = token.trim().split(",")
            if (parts.size < 3) return@mapNotNull null
            RedEyeRegion(
                x = parts[0].trim().toFloat(),
                y = parts[1].trim().toFloat(),
                radius = parts[2].trim().toFloat()
            )
        }
        RedEyeEdit(regions = regions)
    }.getOrNull()

    private fun parseVignette(value: String): VignetteEdit? = runCatching {
        val parts = value.split(";")
        VignetteEdit(
            strength  = parts.getOrNull(0)?.toFloatOrNull() ?: 0.5f,
            size      = parts.getOrNull(1)?.toFloatOrNull() ?: 0.5f,
            roundness = parts.getOrNull(2)?.toFloatOrNull() ?: 0.5f
        )
    }.getOrNull()

    private fun parseTint(value: String): TintEdit? = runCatching {
        val parts = value.split(";")
        TintEdit(
            r        = parts.getOrNull(0)?.toFloatOrNull() ?: 1f,
            g        = parts.getOrNull(1)?.toFloatOrNull() ?: 1f,
            b        = parts.getOrNull(2)?.toFloatOrNull() ?: 1f,
            strength = parts.getOrNull(3)?.toFloatOrNull() ?: 1f
        )
    }.getOrNull()

    private fun parseLomo(value: String): LomoEdit? = runCatching {
        val parts = value.split(";")
        LomoEdit(
            saturation = parts.getOrNull(0)?.toFloatOrNull() ?: 0.5f,
            contrast   = parts.getOrNull(1)?.toFloatOrNull() ?: 0.5f
        )
    }.getOrNull()

    private fun parseAlbumEntry(map: Map<String, String>): PicasaAlbumEntry? {
        val token = map["token"] ?: return null
        val name = map["name"] ?: return null
        return PicasaAlbumEntry(
            token = token,
            name = name,
            description = map["description"],
            date = map["date"]
        )
    }

    private fun parseFaceEntry(raw: String): FaceEntry? = runCatching {
        val commaIdx = raw.lastIndexOf(',')
        if (commaIdx < 0) return null
        val rectPart = raw.substring(0, commaIdx).trim()
        val contactId = raw.substring(commaIdx + 1).trim()
        val rect = parseRect64(rectPart) ?: return null
        FaceEntry(
            contact_id = if (contactId == UNASSIGNED_CONTACT) "" else contactId,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            is_manual = false
        )
    }.getOrNull()

    private fun parseGeotag(raw: String): Pair<Double, Double>? = runCatching {
        val parts = raw.split(",")
        if (parts.size < 2) return null
        parts[0].trim().toDouble() to parts[1].trim().toDouble()
    }.getOrNull()
}
