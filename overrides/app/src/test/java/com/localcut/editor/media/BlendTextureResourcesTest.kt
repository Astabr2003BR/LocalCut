package com.localcut.editor.media

import androidx.media3.common.GlTextureInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class BlendTextureResourcesTest {
    @Test
    fun twoInputsNeedOnlyOutputPoolAndThreeOrFourAddScratchBuffers() {
        val allocator = FakeAllocator()
        val resources = BlendTextureResources(allocator, outputCapacity = 2)

        resources.ensureConfigured(640, 360, inputCount = 2, hasOutputInUse = false)
        assertEquals(2, allocator.live.size)

        resources.ensureConfigured(640, 360, inputCount = 3, hasOutputInUse = false)
        assertEquals(3, allocator.live.size)
        resources.ensureConfigured(640, 360, inputCount = 4, hasOutputInUse = false)
        assertEquals(4, allocator.live.size)
    }

    @Test
    fun outputAcquireReturnIsBoundedByCapacity() {
        val allocator = FakeAllocator()
        val resources = BlendTextureResources(allocator, outputCapacity = 1)
        resources.ensureConfigured(100, 50, 2, false)

        val output = resources.acquireOutput()
        assertNull(resources.acquireOutput())
        resources.returnOutput(output!!)
        assertSame(output, resources.acquireOutput())
    }

    @Test
    fun resizeWhileOutputIsInUseFailsClosedAndReleaseDeletesEverything() {
        val allocator = FakeAllocator()
        val resources = BlendTextureResources(allocator, outputCapacity = 1)
        resources.ensureConfigured(100, 50, 4, false)
        val output = resources.acquireOutput()!!

        assertThrows(IllegalStateException::class.java) {
            resources.ensureConfigured(200, 100, 4, hasOutputInUse = true)
        }

        resources.returnOutput(output)
        resources.releaseAll()
        assertEquals(emptySet<Int>(), allocator.live.keys)
    }

    private class FakeAllocator : BlendTextureAllocator {
        var nextId = 1
        val live = linkedMapOf<Int, GlTextureInfo>()

        override fun allocate(width: Int, height: Int): GlTextureInfo {
            val id = nextId++
            return GlTextureInfo(id, id + 1000, -1, width, height).also { live[id] = it }
        }

        override fun release(texture: GlTextureInfo) {
            check(live.remove(texture.texId) != null)
        }
    }
}
