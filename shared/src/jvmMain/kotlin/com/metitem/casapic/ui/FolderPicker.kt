package com.metitem.casapic.ui

import javax.swing.JFileChooser
import javax.swing.SwingUtilities

fun pickFolder(): String? {
    var result: String? = null
    val latch = java.util.concurrent.CountDownLatch(1)
    SwingUtilities.invokeLater {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = "Select Folder to Add"
        val returnCode = chooser.showOpenDialog(null)
        if (returnCode == JFileChooser.APPROVE_OPTION) {
            result = chooser.selectedFile?.absolutePath
        }
        latch.countDown()
    }
    latch.await()
    return result
}
