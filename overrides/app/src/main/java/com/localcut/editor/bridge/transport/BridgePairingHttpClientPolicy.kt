package com.localcut.editor.bridge.transport

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

internal object BridgePairingHttpClientPolicy {
    fun newClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
