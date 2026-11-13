package com.benchy.X.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.benchy.X.R
import com.benchy.X.data.AccentPreset
import com.benchy.X.data.AppMode
import com.benchy.X.data.LanguageMode
import com.benchy.X.data.StorageUnit
import com.benchy.X.data.TempUnit
import com.benchy.X.data.ThemeMode
import com.benchy.X.data.UserPreferences
import com.benchy.X.locale.AutoText
import com.benchy.X.locale.LocaleManagerHelper
import com.benchy.X.locale.TranslationStatusBanner
import com.benchy.X.ui.components.BenchyCard
import com.benchy.X.ui.components.BenchyEmblem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun OnboardingScreen(
    preferences: UserPreferences,
    onSelectLanguage: (LanguageMode) -> Unit,
    onSelectMode: (AppMode) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectAccent: (AccentPreset) -> Unit,
    onSelectTempUnit: (TempUnit) -> Unit,
    onSelectStorageUnit: (StorageUnit) -> Unit,
    onFinishOnboarding: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val scope = rememberCoroutineScope()

    var modeSelectedInWizard by remember { mutableStateOf<AppMode?>(null) }
    var prefsChangedOnPage5 by remember { mutableStateOf(false) }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.Center)
        ) {
            // Top step indicator & live ML Kit download banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(6) { idx ->
                    val active = idx == pagerState.currentPage
                    val passed = idx < pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .width(if (active) 28.dp else 10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    active -> MaterialTheme.colorScheme.primary
                                    passed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                    )
                }
            }

            TranslationStatusBanner()

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingPage1Language(
                        selectedLanguage = preferences.languageMode,
                        onSelectLanguage = onSelectLanguage,
                        onNext = { scope.launch { pagerState.animateScrollToPage(1) } }
                    )
                    1 -> OnboardingPage2Welcome(
                        onNext = { scope.launch { pagerState.animateScrollToPage(2) } }
                    )
                    2 -> OnboardingPage3Mode(
                        selectedMode = modeSelectedInWizard,
                        onChooseMode = { mode ->
                            modeSelectedInWizard = mode
                            onSelectMode(mode)
                        },
                        onNext = { scope.launch { pagerState.animateScrollToPage(3) } }
                    )
                    3 -> OnboardingPage4Permissions(
                        onNext = { scope.launch { pagerState.animateScrollToPage(4) } }
                    )
                    4 -> OnboardingPage5Preferences(
                        preferences = preferences,
                        hasChangedSetting = prefsChangedOnPage5,
                        onThemeChanged = {
                            prefsChangedOnPage5 = true
                            onSelectTheme(it)
                        },
                        onAccentChanged = {
                            prefsChangedOnPage5 = true
                            onSelectAccent(it)
                        },
                        onTempChanged = {
                            prefsChangedOnPage5 = true
                            onSelectTempUnit(it)
                        },
                        onStorageChanged = {
                            prefsChangedOnPage5 = true
                            onSelectStorageUnit(it)
                        },
                        onModeChanged = {
                            prefsChangedOnPage5 = true
                            modeSelectedInWizard = it
                            onSelectMode(it)
                        },
                        onNext = { scope.launch { pagerState.animateScrollToPage(5) } },
                        onSkip = { scope.launch { pagerState.animateScrollToPage(5) } }
                    )
                    5 -> OnboardingPage6Celebration(
                        onDone = onFinishOnboarding
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage1Language(
    selectedLanguage: LanguageMode,
    onSelectLanguage: (LanguageMode) -> Unit,
    onNext: () -> Unit
) {
    val detectedLang = remember { LocaleManagerHelper.getDetectedSystemLanguageName() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            AutoText(
                resId = R.string.onboarding_lang_title,
                style = MaterialTheme.typography.headlineLarge
            )
            AutoText(
                resId = R.string.onboarding_lang_subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            LanguageOptionCard(
                title = stringResource(R.string.onboarding_lang_follow_system),
                subtitle = stringResource(R.string.onboarding_lang_detected, detectedLang),
                selected = selectedLanguage == LanguageMode.FOLLOW_SYSTEM,
                tag = "lang_option_system",
                onClick = { onSelectLanguage(LanguageMode.FOLLOW_SYSTEM) }
            )
            LanguageOptionCard(
                title = stringResource(R.string.onboarding_lang_english),
                subtitle = stringResource(R.string.onboarding_lang_english_sub),
                selected = selectedLanguage == LanguageMode.ENGLISH,
                tag = "lang_option_en",
                onClick = { onSelectLanguage(LanguageMode.ENGLISH) }
            )
            LanguageOptionCard(
                title = stringResource(R.string.onboarding_lang_french),
                subtitle = stringResource(R.string.onboarding_lang_french_sub),
                selected = selectedLanguage == LanguageMode.FRENCH,
                tag = "lang_option_fr",
                onClick = { onSelectLanguage(LanguageMode.FRENCH) }
            )

            Spacer(modifier = Modifier.height(4.dp))
            AutoText(
                resId = R.string.onboarding_lang_helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(52.dp)
                .testTag("onboarding_next_1")
        ) {
            AutoText(resId = R.string.action_next, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LanguageOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val border = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                AutoText(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AutoText(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingPage2Welcome(onNext: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "welcomeBg")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "welcomePhase"
    )
    val primary = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f + cos(phase) * (size.width * 0.08f)
            val cy = size.height * 0.34f + sin(phase) * (size.height * 0.04f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.22f), bg),
                    center = Offset(cx, cy),
                    radius = size.maxDimension * 0.62f
                )
            )
            // Subtle geometric concentric gauge rings
            for (r in 1..3) {
                drawCircle(
                    color = primary.copy(alpha = 0.08f),
                    radius = size.minDimension * (0.24f * r),
                    center = Offset(size.width * 0.5f, size.height * 0.34f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BenchyEmblem(size = 116.dp, accentColor = primary)
                AutoText(
                    resId = R.string.onboarding_welcome_title,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                AutoText(
                    resId = R.string.onboarding_welcome_body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {},
                        label = { AutoText(resId = R.string.onboarding_welcome_pill_speed) },
                        leadingIcon = { Icon(Icons.Outlined.Bolt, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { AutoText(resId = R.string.onboarding_welcome_pill_health) },
                        leadingIcon = { Icon(Icons.Outlined.HealthAndSafety, null, Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {},
                        label = { AutoText(resId = R.string.onboarding_welcome_pill_security) },
                        leadingIcon = { Icon(Icons.Outlined.Security, null, Modifier.size(16.dp)) }
                    )
                }
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(52.dp)
                    .testTag("onboarding_next_2")
            ) {
                AutoText(resId = R.string.action_next, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun OnboardingPage3Mode(
    selectedMode: AppMode?,
    onChooseMode: (AppMode) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AutoText(
                resId = R.string.onboarding_mode_title,
                style = MaterialTheme.typography.headlineLarge
            )
            AutoText(
                resId = R.string.onboarding_mode_subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ModeChoiceCard(
                titleRes = R.string.mode_user_friendly_title,
                descRes = R.string.mode_user_friendly_desc,
                icon = Icons.Outlined.Speed,
                selected = selectedMode == AppMode.USER_FRIENDLY,
                tag = "mode_card_user_friendly",
                onClick = { onChooseMode(AppMode.USER_FRIENDLY) }
            )

            ModeChoiceCard(
                titleRes = R.string.mode_techy_title,
                descRes = R.string.mode_techy_desc,
                icon = Icons.Outlined.Terminal,
                selected = selectedMode == AppMode.TECHY,
                tag = "mode_card_techy",
                onClick = { onChooseMode(AppMode.TECHY) }
            )

            AutoText(
                resId = R.string.onboarding_mode_helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onNext,
            enabled = selectedMode != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(52.dp)
                .testTag("onboarding_next_3")
        ) {
            AutoText(resId = R.string.action_next, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ModeChoiceCard(
    titleRes: Int,
    descRes: Int,
    icon: ImageVector,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AutoText(
                    resId = titleRes,
                    style = MaterialTheme.typography.titleLarge
                )
                AutoText(
                    resId = descRes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage4Permissions(onNext: () -> Unit) {
    val context = LocalContext.current
    var notifGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AutoText(
                resId = R.string.onboarding_perms_title,
                style = MaterialTheme.typography.headlineLarge
            )
            AutoText(
                resId = R.string.onboarding_perms_subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BenchyCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        AutoText(
                            resId = R.string.onboarding_perm_notif_title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        AutoText(
                            resId = R.string.onboarding_perm_notif_desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (notifGranted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        AutoText(
                            resId = R.string.onboarding_perm_granted,
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.testTag("grant_notif_perm_btn")
                    ) {
                        AutoText(resId = R.string.onboarding_perm_needed)
                    }
                }
            }

            BenchyCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        AutoText(
                            resId = R.string.onboarding_perm_pkg_title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        AutoText(
                            resId = R.string.onboarding_perm_pkg_desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        AutoText(
                            resId = R.string.onboarding_perm_auto_active,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(52.dp)
                .testTag("onboarding_next_4")
        ) {
            AutoText(resId = R.string.action_next, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingPage5Preferences(
    preferences: UserPreferences,
    hasChangedSetting: Boolean,
    onThemeChanged: (ThemeMode) -> Unit,
    onAccentChanged: (AccentPreset) -> Unit,
    onTempChanged: (TempUnit) -> Unit,
    onStorageChanged: (StorageUnit) -> Unit,
    onModeChanged: (AppMode) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AutoText(
                resId = R.string.onboarding_prefs_title,
                style = MaterialTheme.typography.headlineLarge
            )
            AutoText(
                resId = R.string.onboarding_prefs_subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            BenchyCard {
                AutoText(resId = R.string.pref_theme_mode, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.AMOLED -> "AMOLED Black"
                        }
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onThemeChanged(mode) },
                            label = { AutoText(text = label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                AutoText(resId = R.string.pref_accent_color, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preferences.accentPreset == preset,
                            onClick = { onAccentChanged(preset) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(preset.hex))
                                )
                            },
                            label = { AutoText(text = preset.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AutoText(resId = R.string.pref_temp_unit, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = preferences.tempUnit == TempUnit.CELSIUS,
                                onClick = { onTempChanged(TempUnit.CELSIUS) },
                                label = { Text("°C") }
                            )
                            FilterChip(
                                selected = preferences.tempUnit == TempUnit.FAHRENHEIT,
                                onClick = { onTempChanged(TempUnit.FAHRENHEIT) },
                                label = { Text("°F") }
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        AutoText(resId = R.string.pref_storage_unit, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = preferences.storageUnit == StorageUnit.GB,
                                onClick = { onStorageChanged(StorageUnit.GB) },
                                label = { Text("GB") }
                            )
                            FilterChip(
                                selected = preferences.storageUnit == StorageUnit.GIB,
                                onClick = { onStorageChanged(StorageUnit.GIB) },
                                label = { Text("GiB") }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                AutoText(resId = R.string.pref_experience_mode, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.appMode == AppMode.USER_FRIENDLY,
                        onClick = { onModeChanged(AppMode.USER_FRIENDLY) },
                        label = { AutoText(resId = R.string.mode_user_friendly_title) }
                    )
                    FilterChip(
                        selected = preferences.appMode == AppMode.TECHY,
                        onClick = { onModeChanged(AppMode.TECHY) },
                        label = { AutoText(resId = R.string.mode_techy_title) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("onboarding_skip_5")
            ) {
                AutoText(resId = R.string.action_skip, style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = onNext,
                enabled = hasChangedSetting,
                modifier = Modifier
                    .weight(1.4f)
                    .height(52.dp)
                    .testTag("onboarding_next_5")
            ) {
                AutoText(resId = R.string.action_next, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private data class ConfettiParticle(
    val xFraction: Float,
    val startYFraction: Float,
    val speed: Float,
    val radiusDp: Float,
    val drift: Float,
    val color: Color
)

@Composable
private fun OnboardingPage6Celebration(onDone: () -> Unit) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        Color(0xFF00E676),
        Color(0xFFFFB300),
        Color(0xFF2979FF),
        Color(0xFFFF4081)
    )
    val particles = remember {
        val rng = Random(2026)
        List(55) {
            ConfettiParticle(
                xFraction = rng.nextFloat(),
                startYFraction = rng.nextFloat() * -0.5f,
                speed = 0.25f + rng.nextFloat() * 0.45f,
                radiusDp = 3f + rng.nextFloat() * 4.5f,
                drift = (rng.nextFloat() - 0.5f) * 0.12f,
                color = palette[it % palette.size]
            )
        }
    }

    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress = (progress + 0.016f) % 1.4f
            delay(16L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { p ->
                val yNorm = (p.startYFraction + progress * p.speed * 2.1f) % 1.15f
                if (yNorm in 0f..1.1f) {
                    val x = (p.xFraction + sin(progress * 6f + p.xFraction * 10f) * p.drift) * size.width
                    val y = yNorm * size.height
                    drawCircle(
                        color = p.color.copy(alpha = 0.85f),
                        radius = p.radiusDp.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BenchyEmblem(size = 110.dp)
                AutoText(
                    resId = R.string.onboarding_done_title,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                AutoText(
                    resId = R.string.onboarding_done_body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(54.dp)
                    .testTag("onboarding_done_btn")
            ) {
                AutoText(resId = R.string.action_done, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
