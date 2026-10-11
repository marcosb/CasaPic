package com.metitem.casapic.image

import com.metitem.casapic.db.Photo_edits
import com.metitem.casapic.model.HistogramData

expect fun computeHistogram(filePath: String, editStack: List<Photo_edits>): HistogramData?
