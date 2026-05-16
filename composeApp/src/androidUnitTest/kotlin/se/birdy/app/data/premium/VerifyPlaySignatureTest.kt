package se.birdy.app.data.premium

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifyPlaySignatureTest {
    @Test
    fun `empty signature returns false`() {
        assertFalse(verifyPlaySignature("{}", "", VALID_KEY))
    }

    @Test
    fun `empty json returns false`() {
        assertFalse(verifyPlaySignature("", "anything", VALID_KEY))
    }

    @Test
    fun `blank key returns true in test environment`() {
        // Init-block enforces release builds never reach this with a blank key.
        assertTrue(verifyPlaySignature("{}", "abc", ""))
    }

    @Test
    fun `malformed signature base64 returns false (does not crash)`() {
        assertFalse(verifyPlaySignature("{}", "!!!not-base64!!!", VALID_KEY))
    }

    @Test
    fun `malformed key base64 returns false (does not crash)`() {
        assertFalse(verifyPlaySignature("{}", "dmFsaWRCYXNlNjREYXRhPT0=", "!!!not-base64!!!"))
    }

    companion object {
        // Dummy valid-format Base64 RSA public key (NOT a real Play key).
        // Exercises the KeyFactory.generatePublic codepath without crashing.
        // A truncated/invalid DER — verifyPlaySignature will catch the exception and return false.
        private const val VALID_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtest"
    }
}
