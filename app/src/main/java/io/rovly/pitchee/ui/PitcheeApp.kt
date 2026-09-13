package io.rovly.pitchee.ui

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import io.rovly.pitchee.data.FeminineTimeline
import space.pitchee.core.PitcheePhase
import kotlinx.coroutines.launch

private enum class PitcheeDestination(
    val labelRes: Int,
    val iconRes: Int,
) {
    PITCH(R.string.nav_pitch, R.drawable.ic_pitch_analysis),
    ANALYSIS(R.string.nav_analysis, R.drawable.ic_model_analysis),
    ABOUT(R.string.nav_about, R.drawable.ic_about),
    SCORE_TEST(R.string.nav_score_test, R.drawable.ic_model_analysis),
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
        PitcheeDestination.entries.filter { it != PitcheeDestination.SCORE_TEST || debugBuild }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
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
                PitcheeDestination.PITCH -> PitchComingSoonScreen()
                PitcheeDestination.ANALYSIS -> RecordAnalysisScreen()
                PitcheeDestination.ABOUT -> AboutScreen()
                PitcheeDestination.SCORE_TEST -> ScoreTestScreen()
            }
        }
    }
}

@Composable
private fun ScoreTestScreen() {
    var score by rememberSaveable { mutableFloatStateOf(68f) }

    ScreenColumn {
        ScreenHeader(
            title = stringResource(R.string.score_test_title),
            subtitle = stringResource(R.string.score_test_subtitle),
        )
        Spacer(Modifier.height(20.dp))
        ScoreIndexChart(
            score = score.toDouble(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.score_test_slider_label),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = score,
            onValueChange = { score = it },
            valueRange = 0f..100f,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("女性化 %.1f%%".format(score))
            Text("男性化 %.1f%%".format(100f - score))
        }
    }
}

@Composable
private fun PitchComingSoonScreen() {
    ScreenColumn {
        ScreenHeader(
            title = stringResource(R.string.pitch_title),
            subtitle = stringResource(R.string.pitch_subtitle),
        )
        Spacer(Modifier.height(32.dp))
        Surface(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .align(Alignment.CenterHorizontally),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_pitch_analysis),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.pitch_coming_soon),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.pitch_coming_soon_body),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        InformationCard(
            title = stringResource(R.string.pitch_permission_title),
            body = stringResource(R.string.pitch_permission_body),
        )
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

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is RecordUiState.Success -> {
                val timeline = remember(current.audio, current.result) {
                    FeminineTimeline.from(
                        result = current.result,
                        durationSeconds = current.audio.durationSeconds,
                    )
                }
                ScreenColumn {
                    ScoreResultContent(
                        result = current.result,
                        previousScore = current.previousScore,
                    ) {
                        RecordedAudioTimeline(
                            audio = current.audio,
                            timeline = timeline,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            viewModel.reset()
                            startRecording()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("重新录音")
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
                            timeline = null,
                        )
                        Spacer(Modifier.height(20.dp))
                        ErrorCard(current.message)
                        Spacer(Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                viewModel.reset()
                                startRecording()
                            },
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
