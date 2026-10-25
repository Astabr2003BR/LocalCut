package com.localcut.editor.bridge.transport

import com.localcut.editor.bridge.security.BridgeMobilePairingProof
import com.localcut.editor.bridge.security.BridgeStoredCredential
import com.localcut.editor.bridge.security.DevicePublicIdentity
import com.localcut.editor.data.project.JNull
import com.localcut.editor.data.project.MiniJson
import com.localcut.editor.data.project.asLong
import com.localcut.editor.data.project.asObject
import com.localcut.editor.data.project.asString
import com.localcut.editor.data.project.jsonString
import com.localcut.editor.data.project.optional
import com.localcut.editor.data.project.required
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpBridgePairingClient(
    baseUrl: String,
    private val client: OkHttpClient = BridgePairingHttpClientPolicy.newClient(),
) : BridgePairingClient {
    private val baseUrl = baseUrl.trimEnd('/').also {
        require(it.startsWith("https://")) { "bridge pairing baseUrl must use https://" }
    }

    override fun start(identity: DevicePublicIdentity): Result<BridgePairingStart> = runCatching {
        val body = """{"deviceId":${jsonString(identity.deviceId)},"publicKeySpkiBase64":${jsonString(identity.publicKeySpkiBase64)}}"""
        val root = post("/v1/pair/start", body)
        BridgePairingStart(
            code = root.required("code").asString(),
            expiresAtEpochMs = root.required("expiresAtEpochMs").asLong(),
        )
    }

    override fun status(code: String, signatureBase64: String?): Result<BridgePairingStatus> = runCatching {
        require(code.isNotBlank()) { "pairing code must not be blank" }
        val body = buildString {
            append("{\"code\":"); append(jsonString(code))
            if (signatureBase64 != null) {
                append(",\"signatureBase64\":"); append(jsonString(signatureBase64))
            }
            append('}')
        }
        val root = post("/v1/pair/status", body)
        val state = root.required("state").asString()
        val challenge = when (val value = root.optional("challengeBase64")) {
            null, JNull -> null
            else -> value.asString()
        }
        val credential = if (state == "PAIRED") credentialFrom(root) else null
        BridgePairingStatus(state, challenge, credential)
    }

    override fun refresh(refreshToken: String): Result<BridgeStoredCredential> = runCatching {
        require(refreshToken.isNotBlank()) { "refreshToken must not be blank" }
        val root = post(
            "/v1/session/refresh",
            """{"refreshToken":${jsonString(refreshToken)}}""",
        )
        credentialFrom(root)
    }

    override fun recover(proof: BridgeSessionRecoveryProof): Result<BridgeStoredCredential> = runCatching {
        val root = post(
            "/v1/session/recover",
            buildString {
                append("{\"deviceId\":"); append(jsonString(proof.deviceId))
                append(",\"publicKeySpkiBase64\":"); append(jsonString(proof.publicKeySpkiBase64))
                append(",\"recoveryToken\":"); append(jsonString(proof.recoveryToken))
                append(",\"issuedAtEpochMs\":"); append(proof.issuedAtEpochMs)
                append(",\"nonceBase64\":"); append(jsonString(proof.nonceBase64))
                append(",\"signatureBase64\":"); append(jsonString(proof.signatureBase64))
                append('}')
            },
        )
        credentialFrom(root)
    }

    override fun mobileClaim(proof: BridgeMobilePairingProof): Result<BridgeMobilePairingClaim> = runCatching {
        val root = post(
            "/v1/pair/mobile-claim",
            """{"code":${jsonString(proof.code)},"issuedAtEpochMs":${proof.issuedAtEpochMs},"nonceBase64":${jsonString(proof.nonceBase64)},"signatureBase64":${jsonString(proof.signatureBase64)}}""",
        )
        BridgeMobilePairingClaim(
            deviceId = root.required("deviceId").asString(),
            challengeBase64 = root.required("challengeBase64").asString(),
            expiresAtEpochMs = root.required("expiresAtEpochMs").asLong(),
        )
    }

    override fun approveOAuth(requestId: String, accessToken: String): Result<String> = runCatching {
        require(requestId.matches(REQUEST_ID)) { "OAUTH_APPROVAL_REQUEST_INVALID" }
        require(accessToken.isNotBlank()) { "AUTH_REQUIRED" }
        val root = post(
            path = "/v1/oauth/device-approve",
            jsonBody = """{"requestId":${jsonString(requestId)}}""",
            bearerToken = accessToken,
        )
        root.required("completionUrl").asString()
    }

    override fun approveRemoteControl(requestId: String, accessToken: String): Result<Unit> = runCatching {
        require(requestId.matches(REQUEST_ID)) { "REMOTE_APPROVAL_REQUEST_INVALID" }
        require(accessToken.isNotBlank()) { "AUTH_REQUIRED" }
        post(
            path = "/v1/remote/device-approve",
            jsonBody = """{"requestId":${jsonString(requestId)}}""",
            bearerToken = accessToken,
        )
        Unit
    }

    override fun unpair(accessToken: String): Result<Unit> = runCatching {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        post(
            path = "/v1/device/unpair-self",
            jsonBody = "{}",
            bearerToken = accessToken,
        )
        Unit
    }

    private fun post(
        path: String,
        jsonBody: String,
        bearerToken: String? = null,
    ) = client.newCall(
        Request.Builder()
            .url(baseUrl + path)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .apply {
                if (bearerToken != null) {
                    header("Authorization", "Bearer $bearerToken")
                }
            }
            .build(),
    ).execute().use { response ->
        val body = response.body.string()
        val root = runCatching { MiniJson.parse(body).asObject() }
            .getOrElse { throw IllegalStateException("bridge returned invalid JSON") }
        if (!response.isSuccessful) {
            val code = runCatching { root.required("code").asString() }.getOrNull() ?: "BRIDGE_HTTP_${response.code}"
            throw IllegalStateException(code)
        }
        root
    }

    private fun credentialFrom(root: com.localcut.editor.data.project.JObject): BridgeStoredCredential =
        BridgeStoredCredential(
            accessToken = root.required("accessToken").asString(),
            refreshToken = when (val value = root.optional("refreshToken")) {
                null, JNull -> null
                else -> value.asString()
            },
            expiresAtEpochMs = root.required("accessExpiresAtEpochMs").asLong(),
            recoveryToken = when (val value = root.optional("recoveryToken")) {
                null, JNull -> null
                else -> value.asString()
            },
        )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val REQUEST_ID = Regex("^[A-Za-z0-9_-]{16,256}$")
    }
}
