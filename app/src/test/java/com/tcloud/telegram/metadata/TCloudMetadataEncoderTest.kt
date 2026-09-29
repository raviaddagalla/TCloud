package com.tcloud.telegram.metadata

import org.junit.Assert.*
import org.junit.Test

class TCloudMetadataEncoderTest {

    @Test
    fun testEncodeAndDecodeMetadata() {
        val original = TCloudObjectMetadata(
            objectId = "file_12345",
            filename = "holiday_photo.jpg",
            mimeType = "image/jpeg",
            size = 4096000L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            chunkIndex = 0,
            chunkCount = 1,
            createdAt = 1700000000000L,
            modifiedAt = 1700000010000L,
            category = "PHOTO",
            parentFolderId = "folder_root",
            duration = null,
            width = 1920,
            height = 1080
        )

        val encodedCaption = TCloudMetadataEncoder.encode(original)
        assertTrue(TCloudMetadataEncoder.isTCloudObject(encodedCaption))

        val decoded = TCloudMetadataEncoder.decode(encodedCaption)
        assertNotNull(decoded)
        assertEquals(original.objectId, decoded?.objectId)
        assertEquals(original.filename, decoded?.filename)
        assertEquals(original.mimeType, decoded?.mimeType)
        assertEquals(original.size, decoded?.size)
        assertEquals(original.sha256, decoded?.sha256)
        assertEquals(original.chunkIndex, decoded?.chunkIndex)
        assertEquals(original.chunkCount, decoded?.chunkCount)
        assertEquals(original.category, decoded?.category)
        assertEquals(original.width, decoded?.width)
        assertEquals(original.height, decoded?.height)
    }

    @Test
    fun testNonTCloudCaptionReturnsNull() {
        val normalCaption = "Here is a normal photo sent to Telegram"
        assertFalse(TCloudMetadataEncoder.isTCloudObject(normalCaption))
        assertNull(TCloudMetadataEncoder.decode(normalCaption))
    }
}
