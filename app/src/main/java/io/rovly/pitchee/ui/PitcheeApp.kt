package io.rovly.pitchee.ui

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rovly.pitchee.R
import io.rovly.pitchee.data.PitcheeResult
import space.pitchee.core.PitcheePhase
import kotlinx.coroutines.launch

private enum class PitcheeDestination(
    val labelRes: Int,
    val iconRes: Int,
) {
    PITCH(R.string.nav_pitch, R.drawable.ic_pitch_analysis),
    ANALYSIS(R.string.nav_analysis, R.drawable.ic_model_analysis),
    ABOUT(R.string.nav_about, R.drawable.ic_about),
    SCORE_RULES(R.string.nav_score_test, R.drawable.ic_model_analysis),
}

@Preview
@Composable
fun PitcheeApp() {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val debugBuild = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
    val destinations = remember(debugBuild) {
        PitcheeDestination.entries.filter { it != PitcheeDestination.SCORE_RULES || debugBuild }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
            ) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (destinations[selectedIndex]) {
                PitcheeDestination.PITCH -> RealtimePitchScreen()
                PitcheeDestination.ANALYSIS -> RecordAnalysisScreen()
                PitcheeDestination.ABOUT -> AboutScreen()
                PitcheeDestination.SCORE_RULES -> ScoreRulesPage(
                    result = remember { demoRuleResult() },
                    previousScore = 52.0,
                    previousMetrics = PreviousMetrics(
                        standardScore = 70.0,
                        naturalnessScore = 75.0,
                        meanF0Hz = 150.0,
                    ),
                    onBack = null,
                )
            }
        }
    }
}

@Composable
private fun RecordAnalysisScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val factory = remember { RecordViewModel.factory(context.applicationContext) }
    val viewModel: RecordViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionDeniedMessage = stringResource(R.string.record_permission_denied)
    var showingRules by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startRecording()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(permissionDeniedMessage)
            }
        }
    }

    fun startRecording() {
        showingRules = false
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val errorMessage = (state as? RecordUiState.Error)?.message
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) snackbarHostState.showSnackbar(errorMessage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val current = state) {
            is RecordUiState.Success -> {
                AnimatedContent(
                    targetState = showingRules,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    transitionSpec = {
                        if (targetState) {
                            slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.84f, stiffness = 280f),
                                initialOffsetX = { it / 2 },
                            ) togetherWith slideOutHorizontally(
                                animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                                targetOffsetX = { -it / 3 },
                            )
                        } else {
                            slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.84f, stiffness = 280f),
                                initialOffsetX = { -it / 3 },
                            ) togetherWith slideOutHorizontally(
                                animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f),
                                targetOffsetX = { it / 2 },
                            )
                        }
                    },
                    label = "score-rules-page",
                ) { rulesVisible ->
                    if (rulesVisible) {
                        ScoreRulesPage(
                            result = current.result,
                            previousScore = current.previousScore,
                            previousMetrics = current.previousMetrics,
                            onBack = { showingRules = false },
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            val animateScore = remember(current.scoreAnimationToken) {
                                viewModel.consumeScoreAnimation(current.scoreAnimationToken)
                            }
                            ScreenColumn {
                                ScoreResultContent(
                                    result = current.result,
                                    previousScore = current.previousScore,
                                    previousMetrics = current.previousMetrics,
                                    animateScore = animateScore,
                                    onOpenRules = { showingRules = true },
                                ) {
                                    RecordedAudioTimeline(
                                        audio = current.audio,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                TextButton(
                                    onClick = {
                                        viewModel.reset()
                                        startRecording()
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Text("重新录音")
                                }
                            }
                        }
                    }
                }
            }
            is RecordUiState.Error -> {
                if (current.audio == null) {
                    RecordingStage(
                        state = RecordUiState.Ready,
                        onStart = {
                            viewModel.reset()
                            startRecording()
                        },
                        onStop = {},
                    )
                } else {
                    ScreenColumn {
                        RecordedAudioTimeline(
                            audio = current.audio,
                        )
                        Spacer(Modifier.height(20.dp))
                        ErrorCard(current.message)
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                viewModel.reset()
                                startRecording()
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("重新录音")
                        }
                    }
                }
            }
            else -> {
                RecordingStage(
                    state = current,
                    onStart = {
                        viewModel.reset()
                        startRecording()
                    },
                    onStop = viewModel::stopAndAnalyze,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ScoreRulesPage(
    result: PitcheeResult,
    previousScore: Double?,
    previousMetrics: PreviousMetrics?,
    onBack: (() -> Unit)?,
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        ScreenColumn {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text("返回结果")
                }
                Spacer(Modifier.height(8.dp))
            }
            ScreenHeader(
                title = "评分规则",
                subtitle = "结合本次指标查看综合分的计算过程",
            )
            Spacer(Modifier.height(20.dp))
            ScoreRulesContent(
                result = result,
                previousScore = previousScore,
                previousMetrics = previousMetrics,
            )
        }
    }
}

private fun demoRuleResult(): PitcheeResult = PitcheeResult.fromJson(
    """
    {
      "schema_version": 2,
      "model_version": "preview",
      "audio": {
        "source_sample_rate": 48000,
        "source_channels": 1,
        "input_seconds": 8.0,
        "analyzed_seconds": 8.0
      },
      "vad": {
        "segment_count": 1,
        "speech_seconds": 7.2,
        "silero_segment_count": 1,
        "discarded_breath_like_count": 0,
        "trimmed_segment_count": 0,
        "segments": [{
          "start_seconds": 0.2,
          "end_seconds": 7.4,
          "speech_start_seconds": 0.0,
          "speech_end_seconds": 7.2
        }]
      },
      "f0": {
        "window_seconds": 0.1,
        "mean_hz": 158.0,
        "standard_deviation_hz": 12.0,
        "voiced_frame_count": 600,
        "voiced_window_count": 60,
        "windows": []
      },
      "vfp": {
        "vfp_standard_score": 76.0,
        "window_count": 4,
        "window_duration_seconds": 1.515,
        "windows": []
      },
      "naturalness": {
        "score": 68.0,
        "window_count": 4,
        "window_duration_seconds": 1.515,
        "windows": []
      },
      "composite": {
        "base_score": 72.0,
        "final_score": 59.0,
        "cap": 59.0,
        "rule": "low_f0_natural_cap",
        "limited": true,
        "boosted": false
      }
    }
    """.trimIndent(),
)

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.analysis_failed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AboutScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember(context) { context.appVersionName() }

    ScreenColumn {
        ScreenHeader(
            title = stringResource(R.string.about_title),
            subtitle = stringResource(R.string.about_subtitle),
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_model_analysis),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Pitchee",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.about_version, versionName),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        InformationCard(
            title = stringResource(R.string.about_privacy_title),
            body = stringResource(R.string.about_privacy_body),
        )
        Spacer(Modifier.height(12.dp))
        InformationCard(
            title = stringResource(R.string.about_engine_title),
            body = stringResource(R.string.about_engine_body),
        )
        Spacer(Modifier.height(12.dp))
        InformationCard(
            title = stringResource(R.string.about_disclaimer_title),
            body = stringResource(R.string.about_disclaimer_body),
        )
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InformationCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        content = content,
    )
}

private fun Context.appVersionName(): String =
    runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "1.0"
