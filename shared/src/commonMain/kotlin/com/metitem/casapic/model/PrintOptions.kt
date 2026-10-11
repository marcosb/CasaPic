package com.metitem.casapic.model

enum class PrintLayout(val label: String, val cellsPerPage: Int) {
    WALLET("Wallet (9)", 9),
    SIZE_3_5X5("3.5 × 5 (4)", 4),
    SIZE_4X6("4 × 6 (2)", 2),
    FULL_PAGE("Full Page (1)", 1),
    CONTACT_SHEET("Contact Sheet", 0)
}

enum class PrintFitMode(val label: String) {
    SHRINK_TO_FIT("Shrink to Fit"),
    CROP_TO_FIT("Crop to Fit")
}

enum class PrintTextLabel(val label: String) {
    NONE("None"),
    FILENAME("Filename"),
    DATE("Capture Date"),
    CAPTION("Caption")
}

data class PrintOptions(
    val layout: PrintLayout = PrintLayout.SIZE_4X6,
    val fitMode: PrintFitMode = PrintFitMode.SHRINK_TO_FIT,
    val showBorder: Boolean = false,
    val textLabel: PrintTextLabel = PrintTextLabel.NONE,
    val copiesPerPhoto: Int = 1,
    val contactSheetColumns: Int = 4
)
