package com.evan.voicesrs

import android.content.Context

object RecognizerFactory {
    fun create(kind: EngineKind, context: Context, cfg: AnkiConfig): Recognizer {
        return when (kind) {
            EngineKind.GOOGLE -> GoogleRecognizer(context, cfg.asrLanguage.googleTag)
            EngineKind.WHISPER -> RemoteWhisperRecognizer(
                context,
                SpeechEngine.asrBase(cfg.asrUrl).orEmpty(),
                cfg.neuralToken,
                cfg.asrLanguage.code
            )
            EngineKind.VOSK -> VoskRecognizer(context)
        }
    }
}
