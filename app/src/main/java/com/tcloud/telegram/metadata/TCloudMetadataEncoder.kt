package com.tcloud.telegram.metadata

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tcloud.core.common.Constants

/**
 * Encodes/decodes TCloud metadata into Telegram message captions.
 *
 * This metadata is stored alongside every file in Telegram, enabling
 * cloud index reconstruction from scratch (reinstall/device migration).
 *
 * Format: JSON blob in message caption, prefixed with TCLOUD_OBJ marker.
 */
object TCloudMetadataEncoder {

    private val gson = Gson()

    /**
     * Encode file metadata into a Telegram message caption.
     */
    fun encode(metadata: TCloudObjectMetadata): String {
        val json = gson.toJson(metadata)
        return "${Constants.TCLOUD_METADATA_PREFIX}|$json"
    }

    /**
     * Decode metadata from a Telegram message caption.
     * Returns null if the caption doesn't contain TCloud metadata.
     */
    fun decode(caption: String?): TCloudObjectMetadata? {
        if (caption == null) return null
        if (!caption.startsWith(Constants.TCLOUD_METADATA_PREFIX)) return null

        return try {
            val json = caption.substringAfter("${Constants.TCLOUD_METADATA_PREFIX}|")
            gson.fromJson(json, TCloudObjectMetadata::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a caption contains TCloud metadata.
     */
    fun isTCloudObject(caption: String?): Boolean {
        return caption?.startsWith(Constants.TCLOUD_METADATA_PREFIX) == true
    }
}

/**
 * Metadata stored with every TCloud object in Telegram.
 * Contains enough info to reconstruct the logical filesystem.
 */
data class TCloudObjectMetadata(
    @SerializedName("v") val version: Int = Constants.TCLOUD_METADATA_VERSION,
    @SerializedName("id") val objectId: String,
    @SerializedName("fn") val filename: String,
    @SerializedName("mt") val mimeType: String,
    @SerializedName("sz") val size: Long,
    @SerializedName("h") val sha256: String,
    @SerializedName("ci") val chunkIndex: Int = 0,
    @SerializedName("cc") val chunkCount: Int = 1,
    @SerializedName("ct") val createdAt: Long,
    @SerializedName("md") val modifiedAt: Long,
    @SerializedName("cat") val category: String = "",
    @SerializedName("pid") val parentFolderId: String? = null,
    @SerializedName("dur") val duration: Long? = null,
    @SerializedName("w") val width: Int? = null,
    @SerializedName("ht") val height: Int? = null
)
