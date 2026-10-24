package com.evan.voicesrs

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/**
 * Surfaces a save/create failure to the user. [message] is rendered as a Snackbar
 * on the main thread; if no usable view anchor is available it falls back to a Toast.
 */
object ErrorSnackbar {
    private val main = Handler(Looper.getMainLooper())

    /** Show [message] anchored to [anchor] on the UI thread. */
    fun onMain(anchor: View, message: CharSequence) {
        main.post {
            try {
                Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(anchor.context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Map [e] to a user message via [SaveErrors] and show it, anchored to the host activity. */
    fun report(context: Context, e: Throwable, op: SaveErrors.Op) {
        val message = SaveErrors.message(op, e)
        val anchor = (context as? Activity)?.window?.decorView
        if (anchor != null) {
            onMain(anchor, message)
        } else {
            main.post {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
