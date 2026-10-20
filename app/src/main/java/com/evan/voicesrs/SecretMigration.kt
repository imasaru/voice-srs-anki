package com.evan.voicesrs

/**
 * Decides where the AnkiConnect key and the Edge token come from on startup.
 * Pure Kotlin: the Android store applies the result and only then deletes the plain copy.
 *
 * For each secret, independently:
 * - A non-null encrypted value is already stored (blank means the user cleared it). Keep it.
 * - Otherwise a non-blank plain value is migrated once and the plain copy is deleted.
 * - Otherwise the build-time default is stored when it is non-blank.
 * - A blank result asks Settings to prompt. A blank plain entry is still deleted.
 */
object SecretMigration {
    fun decide(input: SecretInputs): SecretDecision {
        val api = pick(input.encryptedApiKey, input.plainApiKey, input.buildApiKey)
        val token = pick(input.encryptedNeuralToken, input.plainNeuralToken, input.buildNeuralToken)
        return SecretDecision(
            apiKey = api.value,
            neuralToken = token.value,
            writeApiKey = api.write,
            writeNeuralToken = token.write,
            deletePlainApiKey = api.deletePlain,
            deletePlainNeuralToken = token.deletePlain,
            promptForApiKey = api.value.isBlank(),
            promptForNeuralToken = token.value.isBlank()
        )
    }

    private fun pick(encrypted: String?, plain: String?, build: String): Pick {
        if (encrypted != null) {
            return Pick(encrypted.trim(), write = false, deletePlain = plain != null)
        }
        val plainValue = plain?.trim().orEmpty()
        if (plainValue.isNotEmpty()) {
            return Pick(plainValue, write = true, deletePlain = true)
        }
        val baked = build.trim()
        return Pick(baked, write = baked.isNotEmpty(), deletePlain = plain != null)
    }

    private data class Pick(val value: String, val write: Boolean, val deletePlain: Boolean)
}

data class SecretInputs(
    /** Null when the encrypted store has no entry. A blank string is a stored empty value. */
    val encryptedApiKey: String?,
    val encryptedNeuralToken: String?,
    /** Null when the plain prefs file has no entry. */
    val plainApiKey: String?,
    val plainNeuralToken: String?,
    val buildApiKey: String,
    val buildNeuralToken: String
)

data class SecretDecision(
    val apiKey: String,
    val neuralToken: String,
    val writeApiKey: Boolean,
    val writeNeuralToken: Boolean,
    val deletePlainApiKey: Boolean,
    val deletePlainNeuralToken: Boolean,
    val promptForApiKey: Boolean,
    val promptForNeuralToken: Boolean
)
