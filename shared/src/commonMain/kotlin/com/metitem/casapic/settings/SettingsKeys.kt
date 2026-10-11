package com.metitem.casapic.settings

object SettingsKeys {
    // UI
    const val UI_SPECIAL_EFFECTS = "ui_special_effects"
    const val UI_SHOW_TOOLTIPS = "ui_show_tooltips"
    const val UI_SINGLE_CLICK_EXIT_EDIT = "ui_single_click_exit_edit"
    const val THUMBNAIL_CAPTION = "thumbnail_caption"
    const val THUMBNAIL_SIZE = "thumbnail_size"
    const val THUMBNAIL_SIZE_FLOAT = "thumbnail_size_float"
    const val SHOW_HIDDEN_PHOTOS = "show_hidden_photos"

    // Files / scanning
    const val FILES_DETECT_DUPLICATES = "files_detect_duplicates"
    const val FILES_DELETE_WITHOUT_CONFIRM = "files_delete_without_confirm"
    const val FILES_REMOVE_ALBUM_WITHOUT_CONFIRM = "files_remove_album_without_confirm"
    const val IMPORT_SAVE_PATH = "import_save_path"
    const val FILETYPES_BMP = "filetypes_bmp"
    const val FILETYPES_GIF = "filetypes_gif"
    const val FILETYPES_PNG = "filetypes_png"
    const val FILETYPES_TGA = "filetypes_tga"
    const val FILETYPES_TIFF = "filetypes_tiff"
    const val FILETYPES_WEBP = "filetypes_webp"
    const val FILETYPES_PSD = "filetypes_psd"
    const val FILETYPES_RAW = "filetypes_raw"
    const val FILETYPES_MOVIES = "filetypes_movies"
    const val FILETYPES_QUICKTIME = "filetypes_quicktime"

    // Email
    const val EMAIL_PROGRAM = "email_program"
    const val EMAIL_RESIZE_MULTIPLE_PX = "email_resize_multiple_px"
    const val EMAIL_RESIZE_SINGLE = "email_resize_single"
    const val EMAIL_MOVIES_AS = "email_movies_as"
    const val EMAIL_INLINE_PHOTOS = "email_inline_photos"

    // Slideshow
    const val SLIDESHOW_LOOP = "slideshow_loop"
    const val SLIDESHOW_MUSIC = "slideshow_music"
    const val SLIDESHOW_MUSIC_FOLDER = "slideshow_music_folder"
    const val SLIDESHOW_DELAY_MS = "slideshow_delay_ms"

    // Printing
    const val PRINT_SIZES = "print_sizes"
    const val PRINT_HQ_PREVIEW = "print_hq_preview"
    const val PRINT_QUALITY = "print_quality"
    const val PRINT_RESAMPLER = "print_resampler"

    // Network
    const val NETWORK_PROXY_HOST = "network_proxy_host"
    const val NETWORK_PROXY_PORT = "network_proxy_port"
    const val NETWORK_PROXY_USER = "network_proxy_user"
    const val NETWORK_PROXY_PASS = "network_proxy_pass"

    // Web albums / upload
    const val UPLOAD_SIZE_PX = "upload_size_px"
    const val UPLOAD_PREVIEW_FIRST = "upload_preview_first"
    const val UPLOAD_PRESERVE_QUALITY = "upload_preserve_quality"
    const val UPLOAD_SYNC_STARRED_ONLY = "upload_sync_starred_only"
    const val UPLOAD_NO_CONFIRM_SYNC = "upload_no_confirm_sync"
    const val UPLOAD_WATERMARK_ENABLED = "upload_watermark_enabled"
    const val UPLOAD_WATERMARK_TEXT = "upload_watermark_text"

    // Telemetry / updates
    const val TELEMETRY_ENABLED = "telemetry_enabled"
    const val AUTO_UPDATE = "auto_update"
    const val LANGUAGE = "language"

    // Face detection / name tags
    const val FACE_DETECTION_ENABLED = "face_detection_enabled"
    const val FACE_SUGGESTIONS_ENABLED = "face_suggestions_enabled"
    const val FACE_SUGGESTION_THRESHOLD = "face_suggestion_threshold"
    const val FACE_CLUSTER_THRESHOLD = "face_cluster_threshold"
    const val FACE_STORE_IN_PHOTO = "face_store_in_photo"

    // Cache
    const val CACHE_SIZE_MB = "cache_size_mb"

    // Defaults
    val DEFAULTS: Map<String, String> = mapOf(
        UI_SPECIAL_EFFECTS to "1",
        UI_SHOW_TOOLTIPS to "1",
        UI_SINGLE_CLICK_EXIT_EDIT to "0",
        THUMBNAIL_CAPTION to "DATE",
        THUMBNAIL_SIZE to "NORMAL",
        THUMBNAIL_SIZE_FLOAT to "0.667",
        SHOW_HIDDEN_PHOTOS to "0",
        FILES_DETECT_DUPLICATES to "1",
        FILES_DELETE_WITHOUT_CONFIRM to "1",
        FILES_REMOVE_ALBUM_WITHOUT_CONFIRM to "0",
        IMPORT_SAVE_PATH to "Pictures",
        FILETYPES_BMP to "1",
        FILETYPES_GIF to "0",
        FILETYPES_PNG to "0",
        FILETYPES_TGA to "0",
        FILETYPES_TIFF to "1",
        FILETYPES_WEBP to "1",
        FILETYPES_PSD to "1",
        FILETYPES_RAW to "1",
        FILETYPES_MOVIES to "1",
        FILETYPES_QUICKTIME to "0",
        EMAIL_PROGRAM to "CHOOSE",
        EMAIL_RESIZE_MULTIPLE_PX to "480",
        EMAIL_RESIZE_SINGLE to "SAME_AS_MULTIPLE",
        EMAIL_MOVIES_AS to "FIRST_FRAME",
        EMAIL_INLINE_PHOTOS to "0",
        SLIDESHOW_LOOP to "0",
        SLIDESHOW_MUSIC to "1",
        SLIDESHOW_MUSIC_FOLDER to "",
        SLIDESHOW_DELAY_MS to "3000",
        PRINT_SIZES to "WALLET,3.5x5,4x6,5x7,8x10",
        PRINT_HQ_PREVIEW to "0",
        PRINT_QUALITY to "COMPATIBLE",
        PRINT_RESAMPLER to "LANCZOS3",
        NETWORK_PROXY_HOST to "",
        NETWORK_PROXY_PORT to "",
        NETWORK_PROXY_USER to "",
        NETWORK_PROXY_PASS to "",
        UPLOAD_SIZE_PX to "1600",
        UPLOAD_PREVIEW_FIRST to "1",
        UPLOAD_PRESERVE_QUALITY to "0",
        UPLOAD_SYNC_STARRED_ONLY to "0",
        UPLOAD_NO_CONFIRM_SYNC to "0",
        UPLOAD_WATERMARK_ENABLED to "0",
        UPLOAD_WATERMARK_TEXT to "",
        TELEMETRY_ENABLED to "1",
        AUTO_UPDATE to "AUTOMATIC",
        LANGUAGE to "system",
        FACE_DETECTION_ENABLED to "1",
        FACE_SUGGESTIONS_ENABLED to "1",
        FACE_SUGGESTION_THRESHOLD to "85",
        FACE_CLUSTER_THRESHOLD to "85",
        FACE_STORE_IN_PHOTO to "0",
        CACHE_SIZE_MB to "512"
    )
}
