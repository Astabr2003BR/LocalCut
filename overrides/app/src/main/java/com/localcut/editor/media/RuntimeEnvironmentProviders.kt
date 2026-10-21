package com.localcut.editor.media

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.os.PowerManager

fun interface ExportCapabilitiesProvider {
    fun current(): ExportCapabilities
}

class AndroidExportCapabilitiesProvider(context: Context) : ExportCapabilitiesProvider {
    private val appContext = context.applicationContext

    private val detectedCapabilities: ExportCapabilities by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            val descriptors = MediaCodecList(MediaCodecList.ALL_CODECS)
                .codecInfos
                .map { codecInfo ->
                    CodecDescriptor(
                        isEncoder = codecInfo.isEncoder,
                        supportedTypes = codecInfo.supportedTypes.toSet(),
                    )
                }
            CodecCapabilityPolicy.capabilities(descriptors)
        }.getOrElse {
            ExportCapabilities(hevcEncoderAvailable = false)
        }
    }

    override fun current(): ExportCapabilities = detectedCapabilities
}

fun interface PreviewRuntimeProvider {
    fun current(): PreviewRuntimeConditions
}

object PreviewThermalPressureMapper {
    fun fromAndroidStatus(status: Int): ThermalPressure = when (status) {
        0 -> ThermalPressure.NONE
        1 -> ThermalPressure.LIGHT
        2 -> ThermalPressure.MODERATE
        3 -> ThermalPressure.SEVERE
        4, 5, 6 -> ThermalPressure.CRITICAL
        else -> ThermalPressure.NONE
    }
}

class AndroidPreviewRuntimeProvider(context: Context) : PreviewRuntimeProvider {
    private val appContext = context.applicationContext

    override fun current(): PreviewRuntimeConditions {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

        val memoryClassMb = activityManager?.memoryClass?.coerceAtLeast(1) ?: CONSERVATIVE_MEMORY_CLASS_MB
        val lowRamDevice = activityManager?.isLowRamDevice ?: true
        val powerSaveMode = powerManager?.isPowerSaveMode ?: true
        val thermalPressure = if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PreviewThermalPressureMapper.fromAndroidStatus(powerManager.currentThermalStatus)
        } else {
            ThermalPressure.NONE
        }

        return PreviewRuntimeConditions(
            memoryClassMb = memoryClassMb,
            lowRamDevice = lowRamDevice,
            powerSaveMode = powerSaveMode,
            thermalPressure = thermalPressure,
        )
    }

    private companion object {
        const val CONSERVATIVE_MEMORY_CLASS_MB = 128
    }
}
