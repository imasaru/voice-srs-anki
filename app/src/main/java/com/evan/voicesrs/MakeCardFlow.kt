package com.evan.voicesrs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object MakeCardFlow {
    private val main = Handler(Looper.getMainLooper())

    fun start(context: Context, turn: ReviewTurn) {
        val explanation = when (val state = turn.explanation) {
            is ExplState.Ready -> state.text
            is ExplState.Streaming -> state.partial
            else -> ""
        }
        draft(context, turn.frontText, turn.backText, explanation)
    }

    fun startAsk(context: Context, question: String, reply: String) {
        draft(context, question, "", reply)
    }

    private fun draft(context: Context, front: String, back: String, explanation: String) {
        val app = context.applicationContext
        Toast.makeText(context, context.getString(R.string.make_card_drafting), Toast.LENGTH_SHORT).show()
        Thread({
            try {
                val cfg = Prefs.load(app)
                val raw = AiClient().complete(
                    cfg.aiBaseUrl,
                    cfg.aiApiKey,
                    cfg.aiModel,
                    MakeCard.system(),
                    MakeCard.userMessage(front, back, explanation)
                )
                val draft = MakeCard.parse(raw)
                main.post {
                    if (draft == null) {
                        Toast.makeText(app, app.getString(R.string.make_card_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        preview(context, cfg.generatedDeck.ifBlank { MakeCard.DEFAULT_DECK }, draft.first, draft.second)
                    }
                }
            } catch (_: Exception) {
                main.post {
                    Toast.makeText(app, app.getString(R.string.make_card_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }, "make-card").start()
    }

    private fun preview(context: Context, deck: String, front: String, back: String) {
        val density = context.resources.displayMetrics.density
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        val frontView = EditText(context).apply {
            hint = context.getString(R.string.make_card_front)
            setText(front)
            minHeight = (48 * density).toInt()
        }
        val backView = EditText(context).apply {
            hint = context.getString(R.string.make_card_back)
            setText(back)
            minHeight = (48 * density).toInt()
        }
        column.addView(frontView)
        column.addView(backView)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.make_card)
            .setView(column)
            .setPositiveButton(R.string.make_card_add) { _, _ ->
                add(context, deck, frontView.text.toString(), backView.text.toString())
            }
            .setNegativeButton(R.string.make_card_cancel, null)
            .show()
    }

    private fun add(context: Context, deck: String, front: String, back: String) {
        val q = front.trim()
        val a = back.trim()
        if (q.isEmpty() || a.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.make_card_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val app = context.applicationContext
        Thread({
            var reported = false
            val ok = try {
                val cfg = Prefs.load(app)
                val id = AnkiConnect(cfg.url, cfg.apiKey).addNote(
                    deck.ifBlank { MakeCard.DEFAULT_DECK },
                    MakeCard.MODEL,
                    mapOf("Front" to q, "Back" to a),
                    listOf(MakeCard.TAG)
                )
                id != 0L
            } catch (e: Exception) {
                ErrorSnackbar.report(context, e, SaveErrors.Op.MAKE_CARD)
                reported = true
                false
            }
            // The snackbar already explained the failure; do not stack a generic toast on it.
            if (reported) return@Thread
            main.post {
                Toast.makeText(
                    app,
                    app.getString(if (ok) R.string.make_card_added else R.string.make_card_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, "make-card-add").start()
    }
}
