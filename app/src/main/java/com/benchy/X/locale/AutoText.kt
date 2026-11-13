package com.benchy.X.locale

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.benchy.X.R

@Composable
fun rememberTranslatedString(englishText: String): String {
    val context = LocalContext.current
    val engine = remember(context) { TranslationEngine.getInstance(context) }
    val modelState by engine.state.collectAsStateWithLifecycle()

    var displayText by remember(englishText, modelState) {
        val initial = if (modelState is TranslationModelState.Ready) {
            val code = (modelState as TranslationModelState.Ready).mlKitLanguageCode
            engine.getCachedOrNull(code, englishText) ?: englishText
        } else {
            englishText
        }
        mutableStateOf(initial)
    }

    LaunchedEffect(englishText, modelState) {
        displayText = when (modelState) {
            is TranslationModelState.Ready -> engine.translateOrFallback(englishText)
            else -> englishText
        }
    }

    return displayText
}

@Composable
fun rememberTranslatedResource(@StringRes resId: Int, vararg formatArgs: Any): String {
    val base = if (formatArgs.isEmpty()) {
        stringResource(id = resId)
    } else {
        stringResource(id = resId, formatArgs = formatArgs)
    }
    return rememberTranslatedString(base)
}

@Composable
fun AutoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val resolved = rememberTranslatedString(text)
    Text(
        text = resolved,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun AutoText(
    @StringRes resId: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    vararg formatArgs: Any
) {
    val resolved = rememberTranslatedResource(resId, *formatArgs)
    Text(
        text = resolved,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun TranslationStatusBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember(context) { TranslationEngine.getInstance(context) }
    val modelState by engine.state.collectAsStateWithLifecycle()

    val showBanner = modelState is TranslationModelState.Downloading ||
        modelState is TranslationModelState.ErrorFallback

    AnimatedVisibility(visible = showBanner, modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                when (val s = modelState) {
                    is TranslationModelState.Downloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.translation_downloading_banner, s.languageName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    is TranslationModelState.ErrorFallback -> {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = stringResource(R.string.translation_error_status),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
