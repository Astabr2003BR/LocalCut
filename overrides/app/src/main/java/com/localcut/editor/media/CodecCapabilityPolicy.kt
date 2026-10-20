package com.localcut.editor.media

import androidx.media3.common.MimeTypes

data class CodecDescriptor(
    val isEncoder: Boolean,
    val supportedTypes: Set<String>,
)

object CodecCapabilityPolicy {
    fun capabilities(codecs: Iterable<CodecDescriptor>): ExportCapabilities {
        val hevcAvailable = codecs.any { codec ->
            codec.isEncoder && codec.supportedTypes.any { type ->
                type.equals(MimeTypes.VIDEO_H265, ignoreCase = true)
            }
        }
        return ExportCapabilities(hevcEncoderAvailable = hevcAvailable)
    }
}
