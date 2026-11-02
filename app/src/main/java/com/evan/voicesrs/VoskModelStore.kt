package com.evan.voicesrs

import java.io.File

/** On-disk check for the unpacked small English Vosk model. The APK does not bundle it. */
object VoskModelStore {
    const val MODEL_NAME = "vosk-model-small-en-us-0.15"

    fun modelDir(filesDir: File): File = File(filesDir, "vosk/$MODEL_NAME")

    /** True only when both the acoustic model and the config are present. */
    fun isReady(dir: File): Boolean {
        return File(dir, "am/final.mdl").isFile && File(dir, "conf/model.conf").isFile
    }
}
