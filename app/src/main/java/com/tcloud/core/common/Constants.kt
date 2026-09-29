package com.tcloud.core.common

/**
 * Application-wide constants.
 * Telegram-specific limits are NOT hardcoded throughout the codebase —
 * they are centralized here and read via the CloudStorageBackend interface.
 */
object Constants {
    // Telegram storage
    const val TCLOUD_CHANNEL_TITLE = "TCloud Storage"
    const val TCLOUD_CHANNEL_DESCRIPTION = "TCloud personal cloud storage. Do not delete."
    const val TCLOUD_METADATA_PREFIX = "TCLOUD_OBJ"
    const val TCLOUD_METADATA_VERSION = 1

    // File identity
    const val HASH_ALGORITHM = "SHA-256"
    const val HASH_BUFFER_SIZE = 8192

    // Sync
    const val MAX_CONCURRENT_UPLOADS = 2
    const val MAX_CONCURRENT_DOWNLOADS = 2
    const val SYNC_WORK_NAME = "tcloud_sync"
    const val PERIODIC_SYNC_INTERVAL_MINUTES = 15L

    // Retry
    const val MAX_RETRY_ATTEMPTS = 5
    const val INITIAL_RETRY_DELAY_MS = 1000L
    const val MAX_RETRY_DELAY_MS = 300_000L // 5 minutes
    const val RETRY_BACKOFF_MULTIPLIER = 2.0

    // Cache
    const val DEFAULT_MAX_CACHE_SIZE_BYTES = 5L * 1024 * 1024 * 1024 // 5 GB
    const val THUMBNAIL_MAX_DIMENSION = 512
    const val THUMBNAIL_QUALITY = 80

    // Database
    const val DATABASE_NAME = "tcloud_database"
    const val DATABASE_VERSION = 1

    // Preferences
    const val PREFS_NAME = "tcloud_encrypted_prefs"
    const val PREF_TELEGRAM_CHAT_ID = "telegram_chat_id"
    const val PREF_ONBOARDING_COMPLETE = "onboarding_complete"
    const val PREF_AUTO_BACKUP = "auto_backup"
    const val PREF_BACKUP_PHOTOS = "backup_photos"
    const val PREF_BACKUP_VIDEOS = "backup_videos"
    const val PREF_BACKUP_SCREENSHOTS = "backup_screenshots"
    const val PREF_BACKUP_DOCUMENTS = "backup_documents"
    const val PREF_BACKUP_AUDIO = "backup_audio"
    const val PREF_BACKUP_DOWNLOADS = "backup_downloads"
    const val PREF_WIFI_ONLY = "wifi_only"
    const val PREF_MOBILE_DATA = "mobile_data"
    const val PREF_ROAMING = "roaming"
    const val PREF_CHARGING_ONLY = "charging_only"
    const val PREF_MIN_BATTERY = "min_battery"
    const val PREF_MAX_CACHE = "max_cache"
    const val PREF_DARK_MODE = "dark_mode"

    // Notifications
    const val SYNC_NOTIFICATION_ID = 1001
    const val COMPLETE_NOTIFICATION_ID = 1002
    const val ERROR_NOTIFICATION_ID = 1003
}

/**
 * File status in TCloud's system.
 */
enum class FileStatus {
    LOCAL_ONLY,
    QUEUED,
    PREPARING,
    HASHING,
    UPLOADING,
    VERIFYING,
    BACKED_UP,
    PARTIALLY_BACKED_UP,
    MODIFIED_SINCE_BACKUP,
    DOWNLOAD_QUEUED,
    DOWNLOADING,
    AVAILABLE_OFFLINE,
    FAILED,
    DELETED_LOCALLY,
    DELETED_REMOTELY,
    CONFLICT
}

/**
 * Upload task status.
 */
enum class TaskStatus {
    QUEUED,
    PREPARING,
    HASHING,
    UPLOADING,
    DOWNLOADING,
    VERIFYING,
    COMPLETED,
    PAUSED,
    FAILED,
    RETRYING,
    CANCELLED
}

/**
 * Error classification for retry logic.
 */
enum class ErrorType {
    TEMPORARY_NETWORK,
    TELEGRAM_TEMPORARY,
    AUTHENTICATION,
    STORAGE,
    INVALID_FILE,
    PERMISSION,
    USER_CANCELLED,
    UNKNOWN
}

/**
 * File categories for backup rules and UI grouping.
 */
enum class FileCategory {
    PHOTOS,
    VIDEOS,
    SCREENSHOTS,
    DOCUMENTS,
    AUDIO,
    ARCHIVES,
    DOWNLOADS,
    OTHER
}
