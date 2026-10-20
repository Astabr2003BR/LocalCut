package com.localcut.editor.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewThermalPressureMapperTest {
    @Test
    fun androidThermalStatusesMapToPreviewPressureWithoutThrowing() {
        val expected = mapOf(
            0 to ThermalPressure.NONE,
            1 to ThermalPressure.LIGHT,
            2 to ThermalPressure.MODERATE,
            3 to ThermalPressure.SEVERE,
            4 to ThermalPressure.CRITICAL,
            5 to ThermalPressure.CRITICAL,
            6 to ThermalPressure.CRITICAL,
            -1 to ThermalPressure.NONE,
            99 to ThermalPressure.NONE,
        )

        expected.forEach { (status, pressure) ->
            assertEquals(
                "unexpected mapping for Android thermal status $status",
                pressure,
                PreviewThermalPressureMapper.fromAndroidStatus(status),
            )
        }
    }
}
