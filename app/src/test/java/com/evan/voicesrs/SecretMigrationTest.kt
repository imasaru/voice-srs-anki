package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretMigrationTest {
    @Test
    fun `plain value migrates once and build is ignored`() {
        val decision = SecretMigration.decide(
            SecretInputs(
                encryptedApiKey = null,
                encryptedNeuralToken = null,
                plainApiKey = "plain-key",
                plainNeuralToken = "plain-token",
                buildApiKey = "build-key",
                buildNeuralToken = "build-token"
            )
        )
        assertEquals("plain-key", decision.apiKey)
        assertEquals("plain-token", decision.neuralToken)
        assertTrue(decision.writeApiKey)
        assertTrue(decision.writeNeuralToken)
        assertTrue(decision.deletePlainApiKey)
        assertTrue(decision.deletePlainNeuralToken)
        assertFalse(decision.promptForApiKey)
    }

    @Test
    fun `stored encrypted value is kept and plain copy is cleared`() {
        val decision = SecretMigration.decide(
            SecretInputs(
                encryptedApiKey = "stored",
                encryptedNeuralToken = "stored-token",
                plainApiKey = "old",
                plainNeuralToken = null,
                buildApiKey = "build",
                buildNeuralToken = "build-token"
            )
        )
        assertEquals("stored", decision.apiKey)
        assertEquals("stored-token", decision.neuralToken)
        assertFalse(decision.writeApiKey)
        assertFalse(decision.writeNeuralToken)
        assertTrue(decision.deletePlainApiKey)
        assertFalse(decision.deletePlainNeuralToken)
    }

    @Test
    fun `blank encrypted value does not refill from build`() {
        val decision = SecretMigration.decide(
            SecretInputs(
                encryptedApiKey = "",
                encryptedNeuralToken = null,
                plainApiKey = null,
                plainNeuralToken = null,
                buildApiKey = "build-key",
                buildNeuralToken = "build-token"
            )
        )
        assertEquals("", decision.apiKey)
        assertTrue(decision.promptForApiKey)
        assertFalse(decision.writeApiKey)
        assertEquals("build-token", decision.neuralToken)
        assertTrue(decision.writeNeuralToken)
        assertFalse(decision.promptForNeuralToken)
    }

    @Test
    fun `missing everything uses the build default`() {
        val decision = SecretMigration.decide(
            SecretInputs(null, null, null, null, "build-key", "")
        )
        assertEquals("build-key", decision.apiKey)
        assertTrue(decision.writeApiKey)
        assertEquals("", decision.neuralToken)
        assertFalse(decision.writeNeuralToken)
        assertTrue(decision.promptForNeuralToken)
    }

    @Test
    fun `blank plain entry is deleted and build is used`() {
        val decision = SecretMigration.decide(
            SecretInputs(null, null, "  ", null, "build-key", "tok")
        )
        assertEquals("build-key", decision.apiKey)
        assertTrue(decision.deletePlainApiKey)
        assertTrue(decision.writeApiKey)
    }
}
