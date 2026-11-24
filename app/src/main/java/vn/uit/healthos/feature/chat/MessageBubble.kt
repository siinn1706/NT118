package vn.uit.healthos.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.liquidGlass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.GlassIconButton
import vn.uit.healthos.core.designsystem.component.PlainButton
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import androidx.compose.foundation.background

data class ChatMessageUi(
    val id: String,
    val isUser: Boolean,
    val content: String,
    val attachmentUrls: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val isEmergency: Boolean = false,
    val isError: Boolean = false,
)

@Composable
fun MessageBubble(
    message: ChatMessageUi,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    onCreateReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = when {
        message.isEmergency -> MaterialTheme.colorScheme.errorContainer
        message.isUser -> MaterialTheme.colorScheme.primary
        else -> Color.Unspecified
    }
    val contentColor = when {
        message.isEmergency -> MaterialTheme.colorScheme.onErrorContainer
        message.isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val bubbleShape = if (message.isUser) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        BubbleSurface(
            tint = containerColor,
            contentColor = contentColor,
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 312.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                message.attachmentUrls.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                val formatted = remember(message.content) { renderSimpleMarkdown(message.content) }
                Text(
                    text = when {
                        message.content.isNotBlank() -> formatted
                        message.isStreaming -> AnnotatedString(stringResource(R.string.chat_thinking))
                        message.isError -> AnnotatedString(stringResource(R.string.chat_reply_failed))
                        !message.isUser -> AnnotatedString(stringResource(R.string.chat_empty_reply))
                        else -> formatted
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (message.isError) {
                    Text(
                        text = stringResource(R.string.chat_reply_failed_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                if (!message.isUser && !message.isStreaming && !message.isError && message.content.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        GlassIconButton(
                            onClick = onToggleSpeak,
                            contentDescription = stringResource(R.string.chat_speak_action),
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        PlainButton(
                            text = stringResource(R.string.chat_create_reminder_action),
                            onClick = onCreateReminder,
                            leadingIcon = Icons.Rounded.AlarmAdd,
                        )
                    }
                }
            }
        }
    }
}

/** Lightweight markdown renderer supporting headings, bullet lists, **bold**, and *italic*. */
internal fun renderSimpleMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.split('\n')
    lines.forEachIndexed { lineIdx, rawLine ->
        val line = when {
            rawLine.startsWith("### ") -> rawLine.removePrefix("### ")
            rawLine.startsWith("## ") -> rawLine.removePrefix("## ")
            rawLine.startsWith("# ") -> rawLine.removePrefix("# ")
            rawLine.startsWith("- ") || rawLine.startsWith("* ") -> "• ${rawLine.drop(2)}"
            else -> rawLine
        }
        val isHeading = rawLine.startsWith("#")
        if (isHeading) pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
        appendInlineMarkdown(line)
        if (isHeading) pop()
        if (lineIdx < lines.lastIndex) append('\n')
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(line: String) {
    var i = 0
    while (i < line.length) {
        if (i + 1 < line.length && line[i] == '*' && line[i + 1] == '*') {
            val end = line.indexOf("**", startIndex = i + 2)
            if (end != -1) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(line.substring(i + 2, end))
                pop()
                i = end + 2
                continue
            }
        }
        if (line[i] == '*') {
            val end = line.indexOf('*', startIndex = i + 1)
            if (end != -1) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(line.substring(i + 1, end))
                pop()
                i = end + 1
                continue
            }
        }
        append(line[i])
        i++
    }
}

/**
 * Chat bubble: the assistant's bubbles are frosted Liquid Glass (controls inside become insets); the user's and
 * emergency bubbles are an opaque tint, where glass would be invisible work.
 */
@Composable
private fun BubbleSurface(
    tint: Color,
    contentColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surface = if (tint.isSpecified) {
        Modifier.clip(shape).background(tint)
    } else {
        Modifier.liquidGlass(shape = shape, style = GlassStyle.Material)
    }
    Box(modifier = modifier.then(surface)) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalInsideGlass provides true,
            content = content,
        )
    }
}
