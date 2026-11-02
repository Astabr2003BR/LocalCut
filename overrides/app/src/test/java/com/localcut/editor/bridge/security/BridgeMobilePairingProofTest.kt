package com.localcut.editor.bridge.security

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMobilePairingProofTest {
    private class FakeIdentityStore : DeviceIdentityStore {
        var signedPayload: ByteArray? = null

        override fun getOrCreatePublicIdentity(): DevicePublicIdentity =
            DevicePublicIdentity("lc_test", "EC_P256", "AQID")

        override fun signChallenge(challenge: ByteArray): ByteArray {
            signedPayload = challenge.copyOf()
            return byteArrayOf(1, 2, 3, 4)
        }
    }

    @Test
    fun createsCanonicalSignedProofForSixDigitPairingCode() {
        val identity = FakeIdentityStore()
        val nonce = "0123456789abcdef0123456789abcdef".toByteArray(StandardCharsets.UTF_8)
        val proof = BridgeMobilePairingProofFactory(
            identityStore = identity,
            nowEpochMs = { 1_000L },
            nonceGenerator = { nonce },
        ).create("105223")

        val expectedNonceBase64 = Base64.getEncoder().encodeToString(nonce)
        assertEquals(1_000L, proof.issuedAtEpochMs)
        assertEquals(expectedNonceBase64, proof.nonceBase64)
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4)), proof.signatureBase64)
        assertEquals(
            "LOCALCUT_MOBILE_CLAIM_V1\n105223\n1000\n$expectedNonceBase64",
            identity.signedPayload?.toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun rejectsInvalidPairingCodeBeforeSigning() {
        val identity = FakeIdentityStore()
        val result = runCatching {
            BridgeMobilePairingProofFactory(
                identityStore = identity,
                nowEpochMs = { 1_000L },
                nonceGenerator = { ByteArray(32) { 7 } },
            ).create("12345")
        }

        assertTrue(result.isFailure)
        assertEquals(null, identity.signedPayload)
    }
}
