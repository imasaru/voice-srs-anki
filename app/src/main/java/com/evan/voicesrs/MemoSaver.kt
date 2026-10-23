package com.evan.voicesrs

import android.content.Context

/** Writes MemoFront/MemoBack through AnkiConnect. Never creates those fields. */
object MemoSaver {
    fun save(context: Context, noteId: Long?, cardId: Long, front: String, back: String) {
        val app = context.applicationContext
        Thread({
            try {
                val cfg = Prefs.load(app)
                val anki = AnkiConnect(cfg.url, cfg.apiKey)
                val nid = noteId ?: anki.cardsToNotes(listOf(cardId)).firstOrNull() ?: return@Thread
                anki.updateNoteFields(nid, CardMemo.fieldsToSave(front, back))
            } catch (_: Exception) {
            }
        }, "memo-save").start()
    }
}
