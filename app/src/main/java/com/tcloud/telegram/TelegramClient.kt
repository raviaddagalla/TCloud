package com.tcloud.telegram

import android.content.Context
import android.util.Log
import com.tcloud.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * TDLib client wrapper providing a coroutine-friendly API.
 *
 * IMPORTANT: This class never logs sensitive data (OTP, passwords, tokens, api_hash).
 * All Telegram-specific details are encapsulated here.
 */
@Singleton
class TelegramClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TelegramClient"
        private const val REQUEST_TIMEOUT_MS = 60_000L
    }

    private var client: Client? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Auth state
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // File updates (for download progress tracking)
    private val _fileUpdates = MutableSharedFlow<TdApi.UpdateFile>(extraBufferCapacity = 100)
    val fileUpdates: SharedFlow<TdApi.UpdateFile> = _fileUpdates.asSharedFlow()

    // Current user info
    private val _currentUser = MutableStateFlow<TdApi.User?>(null)
    val currentUser: StateFlow<TdApi.User?> = _currentUser.asStateFlow()

    private var isInitialized = false

    /**
     * Initialize the TDLib client. Must be called before any other operation.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        // Set TDLib log verbosity (minimal in release)
        Client.execute(TdApi.SetLogVerbosityLevel(if (BuildConfig.DEBUG) 2 else 0))

        client = Client.create(
            { update -> handleUpdate(update) },
            { error -> Log.e(TAG, "TDLib error: ${error.message}") },
            { error -> Log.e(TAG, "TDLib exception: ${error.message}") }
        )
    }

    /**
     * Handle incoming TDLib updates.
     */
    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthorizationState(update.authorizationState)
            }
            is TdApi.UpdateFile -> {
                scope.launch { _fileUpdates.emit(update) }
            }
            is TdApi.UpdateUser -> {
                // Track current user
                if (update.user.id == _currentUser.value?.id || _currentUser.value == null) {
                    // Will be set properly after auth
                }
            }
        }
    }

    /**
     * Handle authorization state machine transitions.
     */
    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                Log.d(TAG, "Setting TDLib parameters")
                val dataDir = File(context.filesDir, "tdlib").apply { mkdirs() }

                val params = TdApi.SetTdlibParameters().apply {
                    databaseDirectory = dataDir.absolutePath
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = BuildConfig.TELEGRAM_API_ID
                    apiHash = BuildConfig.TELEGRAM_API_HASH
                    systemLanguageCode = "en"
                    deviceModel = "Android"
                    systemVersion = android.os.Build.VERSION.RELEASE
                    applicationVersion = BuildConfig.VERSION_NAME
                    useChatInfoDatabase = true
                    useFileDatabase = true
                }

                send(params) { result ->
                    if (result is TdApi.Error) {
                        Log.e(TAG, "Failed to set parameters: ${result.message}")
                        _authState.value = AuthState.Error("Failed to initialize: ${result.message}")
                    }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = AuthState.WaitingForPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = AuthState.WaitingForCode
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = AuthState.WaitingForPassword(state.passwordHint)
            }
            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthState.Authorized
                scope.launch { loadCurrentUser() }
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                _authState.value = AuthState.LoggingOut
            }
            is TdApi.AuthorizationStateClosed -> {
                _authState.value = AuthState.Closed
                isInitialized = false
                client = null
            }
            is TdApi.AuthorizationStateClosing -> {
                // Transitional state
            }
            else -> {
                Log.d(TAG, "Unhandled auth state: ${state::class.simpleName}")
            }
        }
    }

    /**
     * Send phone number for authentication.
     */
    suspend fun sendPhoneNumber(phoneNumber: String): Result<Unit> {
        return sendSuspend(
            TdApi.SetAuthenticationPhoneNumber(
                phoneNumber,
                TdApi.PhoneNumberAuthenticationSettings().apply {
                    allowFlashCall = false
                    allowMissedCall = false
                    isCurrentPhoneNumber = false
                    allowSmsRetrieverApi = true
                }
            )
        ).map { }
    }

    /**
     * Send verification code.
     */
    suspend fun sendCode(code: String): Result<Unit> {
        return sendSuspend(TdApi.CheckAuthenticationCode(code)).map { }
    }

    /**
     * Send 2FA password.
     */
    suspend fun sendPassword(password: String): Result<Unit> {
        return sendSuspend(TdApi.CheckAuthenticationPassword(password)).map { }
    }

    /**
     * Log out from Telegram.
     */
    suspend fun logout(): Result<Unit> {
        return sendSuspend(TdApi.LogOut()).map { }
    }

    /**
     * Get current user info.
     */
    private suspend fun loadCurrentUser() {
        val result = sendSuspend(TdApi.GetMe())
        result.onSuccess { obj ->
            if (obj is TdApi.User) {
                _currentUser.value = obj
            }
        }
    }

    /**
     * Create a new private supergroup for TCloud storage.
     */
    suspend fun createPrivateSupergroup(title: String, description: String): Result<TdApi.Chat> {
        val result = sendSuspend(
            TdApi.CreateNewSupergroupChat(title, false, true, description, null, 0, false)
        )
        return result.map { it as TdApi.Chat }
    }

    /**
     * Search for existing TCloud storage channel.
     */
    suspend fun findTCloudChannel(title: String): Result<TdApi.Chat?> {
        val chatsResult = sendSuspend(TdApi.GetChats(TdApi.ChatListMain(), 200))
        return chatsResult.map { obj ->
            val chatIds = (obj as TdApi.Chats).chatIds
            for (chatId in chatIds) {
                val chatResult = sendSuspend(TdApi.GetChat(chatId))
                chatResult.onSuccess { chatObj ->
                    val chat = chatObj as TdApi.Chat
                    if (chat.title == title) {
                        return@map chat
                    }
                }
            }
            null
        }
    }

    /**
     * Send a file as a document to a chat.
     */
    suspend fun sendDocument(
        chatId: Long,
        filePath: String,
        caption: String,
        mimeType: String? = null
    ): Result<TdApi.Message> {
        val inputFile = TdApi.InputFileLocal(filePath)
        val inputDoc = TdApi.InputDocument(inputFile, null, true)
        val document = TdApi.InputMessageDocument(
            inputDoc,
            TdApi.FormattedText(caption, emptyArray())
        )

        return sendSuspend(
            TdApi.SendMessage(chatId, null, null, null, null, document)
        ).map { it as TdApi.Message }
    }

    /**
     * Download a file with optional offset and limit for range downloads.
     */
    suspend fun downloadFile(
        fileId: Int,
        priority: Int = 5,
        offset: Long = 0,
        limit: Long = 0,
        synchronous: Boolean = false
    ): Result<TdApi.File> {
        return sendSuspend(
            TdApi.DownloadFile(fileId, priority, offset, limit, synchronous)
        ).map { it as TdApi.File }
    }

    /**
     * Get the downloaded prefix size from a specific offset.
     */
    suspend fun getFileDownloadedPrefixSize(fileId: Int, offset: Long): Result<Long> {
        return sendSuspend(
            TdApi.GetFileDownloadedPrefixSize(fileId, offset)
        ).map { (it as TdApi.FileDownloadedPrefixSize).size }
    }

    /**
     * Delete messages from a chat.
     */
    suspend fun deleteMessages(chatId: Long, messageIds: LongArray, revoke: Boolean = true): Result<Unit> {
        return sendSuspend(
            TdApi.DeleteMessages(chatId, messageIds, revoke)
        ).map { }
    }

    /**
     * Get a message by ID.
     */
    suspend fun getMessage(chatId: Long, messageId: Long): Result<TdApi.Message> {
        return sendSuspend(TdApi.GetMessage(chatId, messageId)).map { it as TdApi.Message }
    }

    /**
     * Get chat history (for scanning storage channel).
     */
    suspend fun getChatHistory(
        chatId: Long,
        fromMessageId: Long = 0,
        offset: Int = 0,
        limit: Int = 100
    ): Result<TdApi.Messages> {
        return sendSuspend(
            TdApi.GetChatHistory(chatId, fromMessageId, offset, limit, false)
        ).map { it as TdApi.Messages }
    }

    /**
     * Get file information.
     */
    suspend fun getFile(fileId: Int): Result<TdApi.File> {
        return sendSuspend(TdApi.GetFile(fileId)).map { it as TdApi.File }
    }

    /**
     * Get remote file by remote file ID.
     */
    suspend fun getRemoteFile(remoteFileId: String, fileType: TdApi.FileType = TdApi.FileTypeDocument()): Result<TdApi.File> {
        return sendSuspend(TdApi.GetRemoteFile(remoteFileId, fileType)).map { it as TdApi.File }
    }

    /**
     * Cancel file download.
     */
    suspend fun cancelDownload(fileId: Int, onlyIfPending: Boolean = false): Result<Unit> {
        return sendSuspend(TdApi.CancelDownloadFile(fileId, onlyIfPending)).map { }
    }

    /**
     * Low-level send with callback.
     */
    private fun send(function: TdApi.Function<*>, handler: (TdApi.Object) -> Unit) {
        client?.send(function, handler) ?: handler(TdApi.Error(0, "Client not initialized"))
    }

    /**
     * Coroutine-friendly send that suspends until response.
     */
    private suspend fun sendSuspend(function: TdApi.Function<*>): Result<TdApi.Object> {
        val currentClient = client ?: return Result.failure(
            IllegalStateException("Client not initialized")
        )

        return withTimeout(REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                currentClient.send(function) { result ->
                    if (result is TdApi.Error) {
                        continuation.resume(
                            Result.failure(TelegramException(result.code, result.message))
                        )
                    } else {
                        continuation.resume(Result.success(result))
                    }
                }
            }
        }
    }

    /**
     * Destroy the client and clean up resources.
     */
    fun destroy() {
        client?.send(TdApi.Close()) {}
        client = null
        isInitialized = false
    }
}

/**
 * Telegram authorization state for the UI layer.
 */
sealed class AuthState {
    data object Initializing : AuthState()
    data object WaitingForPhoneNumber : AuthState()
    data object WaitingForCode : AuthState()
    data class WaitingForPassword(val hint: String) : AuthState()
    data object Authorized : AuthState()
    data object LoggingOut : AuthState()
    data object Closed : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Telegram API exception.
 */
class TelegramException(val code: Int, message: String) : Exception(message)
