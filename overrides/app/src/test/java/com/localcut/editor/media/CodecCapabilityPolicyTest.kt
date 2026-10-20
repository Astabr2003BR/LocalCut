package com.localcut.editor.media

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecCapabilityPolicyTest {
    @Test
    fun hevcRequiresAnEncoderAdvertisingHevcMimeType() {
        val decodersOnly = listOf(
            CodecDescriptor(isEncoder = false, supportedTypes = setOf(MimeTypes.VIDEO_H265)),
        )
        val h264EncoderOnly = listOf(
            CodecDescriptor(isEncoder = true, supportedTypes = setOf(MimeTypes.VIDEO_H264)),
        )
        val hevcEncoder = listOf(
            CodecDescriptor(isEncoder = true, supportedTypes = setOf(MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H265)),
        )

        assertFalse(CodecCapabilityPolicy.capabilities(decodersOnly).hevcEncoderAvailable)
        assertFalse(CodecCapabilityPolicy.capabilities(h264EncoderOnly).hevcEncoderAvailable)
        assertTrue(CodecCapabilityPolicy.capabilities(hevcEncoder).hevcEncoderAvailable)
    }

    @Test
    fun mimeMatchingIsCaseInsensitive() {
        val codecs = listOf(
            CodecDescriptor(isEncoder = true, supportedTypes = setOf("VIDEO/HEVC")),
        )

        assertTrue(CodecCapabilityPolicy.capabilities(codecs).hevcEncoderAvailable)
    }
}
