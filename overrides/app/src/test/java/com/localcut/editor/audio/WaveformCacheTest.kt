package com.localcut.editor.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WaveformCacheTest {
    @Test
    fun cacheKeyIncludesAssetFingerprintSourceRangeAndBucketCount() {
        val base = WaveformCacheKey(
            assetFingerprint = "sha256:asset-a",
            sourceInUs = 1_000_000L,
            sourceOutUs = 5_000_000L,
            bucketCount = 128,
        )

        assertEquals(base, base.copy())
        org.junit.Assert.assertNotEquals(base, base.copy(assetFingerprint = "sha256:asset-b"))
        org.junit.Assert.assertNotEquals(base, base.copy(sourceInUs = 2_000_000L))
        org.junit.Assert.assertNotEquals(base, base.copy(sourceOutUs = 6_000_000L))
        org.junit.Assert.assertNotEquals(base, base.copy(bucketCount = 256))
    }

    @Test
    fun boundedCacheEvictsLeastRecentlyUsedWaveformBySampleBudget() {
        val cache = WaveformCache(maxSamples = 5)
        val firstKey = WaveformCacheKey("asset-a", 0L, 1_000_000L, 3)
        val secondKey = WaveformCacheKey("asset-b", 0L, 1_000_000L, 2)
        val thirdKey = WaveformCacheKey("asset-c", 0L, 1_000_000L, 2)
        val first = WaveformData(listOf(0.1f, 0.2f, 0.3f))
        val second = WaveformData(listOf(0.4f, 0.5f))
        val third = WaveformData(listOf(0.6f, 0.7f))

        cache.put(firstKey, first)
        cache.put(secondKey, second)
        assertSame(first, cache[firstKey]) // Refresh first; second is now LRU.

        cache.put(thirdKey, third)

        assertSame(first, cache[firstKey])
        assertNull(cache[secondKey])
        assertSame(third, cache[thirdKey])
        assertEquals(5, cache.currentSamples)
    }

    @Test
    fun oversizedWaveformIsNotCachedAndDoesNotEvictExistingData() {
        val cache = WaveformCache(maxSamples = 3)
        val retainedKey = WaveformCacheKey("retained", 0L, 1_000_000L, 2)
        val oversizedKey = WaveformCacheKey("oversized", 0L, 1_000_000L, 4)
        val retained = WaveformData(listOf(0.1f, 0.2f))

        cache.put(retainedKey, retained)
        cache.put(oversizedKey, WaveformData(listOf(0.1f, 0.2f, 0.3f, 0.4f)))

        assertSame(retained, cache[retainedKey])
        assertNull(cache[oversizedKey])
        assertEquals(2, cache.currentSamples)
    }
}
