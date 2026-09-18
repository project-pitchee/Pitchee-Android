package io.rovly.pitchee.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal fun loc(zh: String, en: String): String =
    if (LocalConfiguration.current.locales[0].language.startsWith("en")) en else zh
