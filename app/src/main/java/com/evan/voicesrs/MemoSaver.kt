package com.evan.voicesrs

import android.content.Context

/** Writes MemoFront/MemoBack through AnkiConnect. Never creates those fields. */
object MemoSaver {
    fun save(
        context: Context,
        noteId: Long?,
        cardId: Long,
        front: String,
        back: String,
        onError: (Throwable) -> Unit = {},
    ) {
        val app = context.applicationContext
        Thread({
            // Report the failure (so the caller can show a Snackbar) instead of swallowing it.
            val result = runCatching {
                val cfg = Prefs.load(app)
                val anki = AnkiConnect(cfg.url, cfg.apiKey)
                val nid = noteId ?: anki.cardsToNotes(listOf(cardId)).firstOrNull()
                    ?: return@runCatching Unit
                anki.updateNoteFields(nid, CardMemo.fieldsToSave(front, back))
            }
            result.onFailure { onError(it) }
        }, "memo-save").start()
    }
}
