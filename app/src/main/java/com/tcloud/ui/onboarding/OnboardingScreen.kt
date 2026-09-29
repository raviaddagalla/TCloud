package com.tcloud.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcloud.core.common.FileCategory
import com.tcloud.telegram.AuthState
import com.tcloud.ui.theme.*

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val phoneNumber by viewModel.phoneNumber.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()

    // Navigate when onboarding completes
    LaunchedEffect(uiState.onboardingComplete) {
        if (uiState.onboardingComplete) onComplete()
    }

    // Auto-initialize storage when authorized
    LaunchedEffect(authState) {
        if (authState is AuthState.Authorized && !uiState.storageInitialized) {
            viewModel.initializeStorage()
        }
    }

    val currentStep = when {
        authState is AuthState.Initializing -> OnboardingStep.WELCOME
        authState is AuthState.WaitingForPhoneNumber -> OnboardingStep.PHONE
        authState is AuthState.WaitingForCode -> OnboardingStep.CODE
        authState is AuthState.WaitingForPassword -> OnboardingStep.PASSWORD
        authState is AuthState.Authorized && !uiState.storageInitialized -> OnboardingStep.SETUP
        authState is AuthState.Authorized && uiState.storageInitialized -> OnboardingStep.CATEGORIES
        else -> OnboardingStep.WELCOME
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        DarkSurface,
                        DarkBackground
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Progress indicator
            OnboardingProgress(currentStep = currentStep)

            // Content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "onboarding_content"
            ) { step ->
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeContent(
                        onConnect = { /* TDLib auto-transitions to phone step */ }
                    )
                    OnboardingStep.PHONE -> PhoneContent(
                        phoneNumber = phoneNumber,
                        onPhoneChange = viewModel::updatePhoneNumber,
                        onSubmit = viewModel::submitPhoneNumber,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onClearError = viewModel::clearError
                    )
                    OnboardingStep.CODE -> CodeContent(
                        code = code,
                        onCodeChange = viewModel::updateCode,
                        onSubmit = viewModel::submitCode,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onClearError = viewModel::clearError
                    )
                    OnboardingStep.PASSWORD -> PasswordContent(
                        password = password,
                        hint = (authState as? AuthState.WaitingForPassword)?.hint ?: "",
                        onPasswordChange = viewModel::updatePassword,
                        onSubmit = viewModel::submitPassword,
                        isLoading = uiState.isLoading,
                        error = uiState.error,
                        onClearError = viewModel::clearError
                    )
                    OnboardingStep.SETUP -> SetupContent(
                        status = uiState.storageStatus,
                        isLoading = uiState.isLoading,
                        error = uiState.error
                    )
                    OnboardingStep.CATEGORIES -> CategoriesContent(
                        categories = uiState.backupCategories,
                        wifiOnly = uiState.wifiOnly,
                        mobileData = uiState.mobileData,
                        chargingOnly = uiState.chargingOnly,
                        minBattery = uiState.minBattery,
                        onCategoryChange = viewModel::updateBackupCategory,
                        onWifiOnlyChange = viewModel::updateWifiOnly,
                        onMobileDataChange = viewModel::updateMobileData,
                        onChargingOnlyChange = viewModel::updateChargingOnly,
                        onMinBatteryChange = viewModel::updateMinBattery,
                        onFinish = viewModel::completeOnboarding
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingProgress(currentStep: OnboardingStep) {
    val steps = OnboardingStep.entries
    val progress = (steps.indexOf(currentStep) + 1).toFloat() / steps.size
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "progress"
    )

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = TCloudPrimary,
            trackColor = DarkSurfaceVariant
        )
    }
}

// ─── Welcome Screen ──────────────────────────────────────────────────────────

@Composable
private fun WelcomeContent(onConnect: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "welcome_anim")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated cloud icon with glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(160.dp)
        ) {
            // Glow effect
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                TCloudPrimary.copy(alpha = glowAlpha * 0.5f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            // Icon
            Icon(
                imageVector = Icons.Filled.Cloud,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = TCloudPrimary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to TCloud",
            style = MaterialTheme.typography.headlineLarge,
            color = DarkOnBackground,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your personal cloud storage,\nbacked by your Telegram account.",
            style = MaterialTheme.typography.bodyLarge,
            color = DarkOnSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Feature cards
        FeatureCard(
            icon = Icons.Outlined.CloudUpload,
            title = "Automatic Backup",
            description = "Photos, videos, and documents backed up automatically"
        )
        Spacer(modifier = Modifier.height(12.dp))
        FeatureCard(
            icon = Icons.Outlined.Security,
            title = "Your Account, Your Data",
            description = "Files stored in your own Telegram account"
        )
        Spacer(modifier = Modifier.height(12.dp))
        FeatureCard(
            icon = Icons.Outlined.Storage,
            title = "No Size Limits",
            description = "Large files are automatically chunked and managed"
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "TCloud needs Telegram authorization to upload and download your files. You will never need to manually upload files to Telegram.",
            style = MaterialTheme.typography.bodySmall,
            color = DarkOnSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(TCloudPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = TCloudPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = DarkOnBackground)
                Text(description, style = MaterialTheme.typography.bodySmall, color = DarkOnSurfaceVariant)
            }
        }
    }
}

// ─── Phone Number Screen ─────────────────────────────────────────────────────

@Composable
private fun PhoneContent(
    phoneNumber: String,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    error: String?,
    onClearError: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    AuthScreenLayout(
        icon = Icons.Filled.Phone,
        title = "Enter your phone number",
        subtitle = "We'll send a verification code to your Telegram account."
    ) {
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = {
                onPhoneChange(it)
                onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Phone number with country code") },
            placeholder = { Text("+1 234 567 8900") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmit()
            }),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = authTextFieldColors(),
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) }
        )

        error?.let { ErrorBanner(it) }

        Spacer(modifier = Modifier.height(24.dp))

        GradientButton(
            text = if (isLoading) "Sending…" else "Send Code",
            onClick = onSubmit,
            enabled = phoneNumber.length >= 7 && !isLoading,
            isLoading = isLoading
        )
    }
}

// ─── Verification Code Screen ────────────────────────────────────────────────

@Composable
private fun CodeContent(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    error: String?,
    onClearError: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    AuthScreenLayout(
        icon = Icons.Filled.Lock,
        title = "Enter verification code",
        subtitle = "Check your Telegram app or SMS for the code."
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = {
                onCodeChange(it)
                onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Verification code") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmit()
            }),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = authTextFieldColors(),
            leadingIcon = { Icon(Icons.Filled.Pin, contentDescription = null) }
        )

        error?.let { ErrorBanner(it) }

        Spacer(modifier = Modifier.height(24.dp))

        GradientButton(
            text = if (isLoading) "Verifying…" else "Verify",
            onClick = onSubmit,
            enabled = code.length >= 4 && !isLoading,
            isLoading = isLoading
        )
    }
}

// ─── 2FA Password Screen ────────────────────────────────────────────────────

@Composable
private fun PasswordContent(
    password: String,
    hint: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    error: String?,
    onClearError: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    AuthScreenLayout(
        icon = Icons.Filled.Shield,
        title = "Two-factor authentication",
        subtitle = if (hint.isNotBlank()) "Hint: $hint" else "Enter your 2FA password."
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = {
                onPasswordChange(it)
                onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmit()
            }),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = authTextFieldColors(),
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) }
        )

        error?.let { ErrorBanner(it) }

        Spacer(modifier = Modifier.height(24.dp))

        GradientButton(
            text = if (isLoading) "Verifying…" else "Submit",
            onClick = onSubmit,
            enabled = password.isNotBlank() && !isLoading,
            isLoading = isLoading
        )
    }
}

// ─── Storage Setup Screen ───────────────────────────────────────────────────

@Composable
private fun SetupContent(
    status: String,
    isLoading: Boolean,
    error: String?
) {
    AuthScreenLayout(
        icon = Icons.Filled.CloudDone,
        title = "Setting up storage",
        subtitle = "Creating your TCloud storage space in Telegram…"
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = TCloudPrimary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant,
            textAlign = TextAlign.Center
        )

        error?.let { ErrorBanner(it) }
    }
}

// ─── Backup Categories Screen ───────────────────────────────────────────────

@Composable
private fun CategoriesContent(
    categories: Map<FileCategory, Boolean>,
    wifiOnly: Boolean,
    mobileData: Boolean,
    chargingOnly: Boolean,
    minBattery: Int,
    onCategoryChange: (FileCategory, Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onMobileDataChange: (Boolean) -> Unit,
    onChargingOnlyChange: (Boolean) -> Unit,
    onMinBatteryChange: (Int) -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Choose what to back up",
            style = MaterialTheme.typography.headlineSmall,
            color = DarkOnBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "You can change these settings anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Backup categories
        SectionHeader("Backup Categories")
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                CategoryToggle("📸", "Photos", FileCategory.PHOTOS, categories[FileCategory.PHOTOS] ?: true, onCategoryChange)
                HorizontalDivider(color = DarkOutline.copy(alpha = 0.3f))
                CategoryToggle("🎥", "Videos", FileCategory.VIDEOS, categories[FileCategory.VIDEOS] ?: true, onCategoryChange)
                HorizontalDivider(color = DarkOutline.copy(alpha = 0.3f))
                CategoryToggle("📱", "Screenshots", FileCategory.SCREENSHOTS, categories[FileCategory.SCREENSHOTS] ?: true, onCategoryChange)
                HorizontalDivider(color = DarkOutline.copy(alpha = 0.3f))
                CategoryToggle("📄", "Documents", FileCategory.DOCUMENTS, categories[FileCategory.DOCUMENTS] ?: true, onCategoryChange)
                HorizontalDivider(color = DarkOutline.copy(alpha = 0.3f))
                CategoryToggle("🎵", "Audio", FileCategory.AUDIO, categories[FileCategory.AUDIO] ?: false, onCategoryChange)
                HorizontalDivider(color = DarkOutline.copy(alpha = 0.3f))
                CategoryToggle("📥", "Downloads", FileCategory.DOWNLOADS, categories[FileCategory.DOWNLOADS] ?: false, onCategoryChange)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Network settings
        SectionHeader("Network")
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                SettingToggle("Wi-Fi backup", "Back up files over Wi-Fi", wifiOnly, onWifiOnlyChange)
                HorizontalDivider(color = DarkOutline.copy(alpha = 0.3f))
                SettingToggle("Mobile data backup", "Also back up over mobile data", mobileData, onMobileDataChange)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Battery settings
        SectionHeader("Battery")
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                SettingToggle("Charging only", "Only back up while charging", chargingOnly, onChargingOnlyChange)
                HorizontalDivider(color = DarkOutline.copy(alpha = 0.3f))
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Minimum battery: $minBattery%", style = MaterialTheme.typography.titleSmall, color = DarkOnBackground)
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = minBattery.toFloat(),
                        onValueChange = { onMinBatteryChange(it.toInt()) },
                        valueRange = 10f..80f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = TCloudPrimary,
                            activeTrackColor = TCloudPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        GradientButton(
            text = "Finish Setup",
            onClick = onFinish,
            enabled = true,
            isLoading = false
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ─── Reusable Components ────────────────────────────────────────────────────

@Composable
private fun AuthScreenLayout(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(TCloudPrimary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TCloudPrimary, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = DarkOnBackground,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = DarkOnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        content()
    }
}

@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TCloudPrimary,
            disabledContainerColor = TCloudPrimary.copy(alpha = 0.3f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Spacer(modifier = Modifier.height(16.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = TCloudError.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Error, contentDescription = null, tint = TCloudError, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = TCloudError)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = TCloudPrimary,
        modifier = Modifier.padding(bottom = 8.dp),
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun CategoryToggle(
    emoji: String,
    label: String,
    category: FileCategory,
    enabled: Boolean,
    onChange: (FileCategory, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = DarkOnBackground,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = enabled,
            onCheckedChange = { onChange(category, it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TCloudPrimary,
                uncheckedThumbColor = DarkOnSurfaceVariant,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = DarkOnBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DarkOnSurfaceVariant)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TCloudPrimary,
                uncheckedThumbColor = DarkOnSurfaceVariant,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}

@Composable
private fun authTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TCloudPrimary,
    unfocusedBorderColor = DarkOutline,
    focusedLabelColor = TCloudPrimary,
    unfocusedLabelColor = DarkOnSurfaceVariant,
    cursorColor = TCloudPrimary,
    focusedTextColor = DarkOnBackground,
    unfocusedTextColor = DarkOnBackground,
    focusedLeadingIconColor = TCloudPrimary,
    unfocusedLeadingIconColor = DarkOnSurfaceVariant
)

enum class OnboardingStep {
    WELCOME, PHONE, CODE, PASSWORD, SETUP, CATEGORIES
}
