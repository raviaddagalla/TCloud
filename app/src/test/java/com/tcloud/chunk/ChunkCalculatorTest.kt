package com.tcloud.chunk

import com.tcloud.core.database.entity.FileEntry
import com.tcloud.core.common.FileCategory
import com.tcloud.core.common.FileStatus
import org.junit.Assert.*
import org.junit.Test

class ChunkCalculatorTest {

    @Test
    fun testSmallFileDoesNotExceedMaxObjectSize() {
        val maxObjectSize = 1_932_735_283L // ~1.8 GB
        val smallFileSize = 500_000_000L // 500 MB

        val needsChunking = smallFileSize > maxObjectSize
        assertFalse(needsChunking)
    }

    @Test
    fun testLargeFileChunkCountCalculation() {
        val maxObjectSize = 1_000_000_000L // 1 GB test boundary
        val largeFileSize = 3_500_000_000L // 3.5 GB file

        val chunkCount = ((largeFileSize + maxObjectSize - 1) / maxObjectSize).toInt()
        assertEquals(4, chunkCount)

        // Verify boundary offsets
        val chunks = (0 until chunkCount).map { index ->
            val offsetStart = index.toLong() * maxObjectSize
            val offsetEnd = minOf(offsetStart + maxObjectSize, largeFileSize)
            Pair(offsetStart, offsetEnd)
        }

        assertEquals(0L to 1_000_000_000L, chunks[0])
        assertEquals(1_000_000_000L to 2_000_000_000L, chunks[1])
        assertEquals(2_000_000_000L to 3_000_000_000L, chunks[2])
        assertEquals(3_000_000_000L to 3_500_000_000L, chunks[3])
    }
}
