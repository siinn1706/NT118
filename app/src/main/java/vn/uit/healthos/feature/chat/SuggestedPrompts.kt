package vn.uit.healthos.feature.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.component.GlassChip

@Composable
fun SuggestedPrompts(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prompts = listOf(
        stringResource(R.string.chat_prompt_meals),
        stringResource(R.string.chat_prompt_hr),
        stringResource(R.string.chat_prompt_exercise),
    )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        prompts.forEach { prompt ->
            GlassChip(label = prompt, onClick = { onSelectPrompt(prompt) })
        }
    }
}
