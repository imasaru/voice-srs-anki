package com.evan.voicesrs

import android.content.Context

object RecognizerFactory {
    fun create(kind: EngineKind, context: Context, cfg: AnkiConfig): Recognizer {
        return when (kind) {
            EngineKind.GOOGLE -> GoogleRecognizer(context)
            EngineKind.WHISPER -> RemoteWhisperRecognizer(
                context,
                SpeechEngine.asrBase(cfg.asrUrl, cfg.neuralUrl).orEmpty(),
                cfg.neuralToken
            )
            EngineKind.VOSK -> VoskRecognizer(context)
        }
    }
}
