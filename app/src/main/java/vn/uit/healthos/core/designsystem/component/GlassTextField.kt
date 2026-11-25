package vn.uit.healthos.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import vn.uit.healthos.R
import vn.uit.healthos.core.designsystem.glass.GlassStyle
import vn.uit.healthos.core.designsystem.glass.GlassTokens
import vn.uit.healthos.core.designsystem.glass.LocalContainerRadius
import vn.uit.healthos.core.designsystem.glass.LocalInsideGlass
import vn.uit.healthos.core.designsystem.glass.glassInset
import vn.uit.healthos.core.designsystem.glass.liquidGlass

private val FieldMinHeight = 52.dp

/**
 * Text field on glass. The [label] sits above the field, [supportingText] or [errorText] below it, all inside the
 * field's own semantics node, so TalkBack reads the label, the value and the error together. On the ambient
 * background the field is frosted Material glass; inside a glass card ([inset], default from [LocalInsideGlass]) it
 * becomes an inset with a concentric radius. A focused field gets an accent rim; an error gets an error rim and tint.
 *
 * [isPassword] masks the value and adds a show/hide toggle (and defaults the keyboard to password).
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    inset: Boolean = LocalInsideGlass.current,
) {
    val scheme = MaterialTheme.colorScheme
    val isError = errorText != null
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val containerRadius: Dp = LocalContainerRadius.current.takeIf { inset && it != Dp.Unspecified }
        ?: FieldRadius
    val shape = remember(containerRadius) { RoundedRectangle(containerRadius) }
    val transformation = when {
        isPassword && !passwordVisible -> PasswordVisualTransformation()
        else -> visualTransformation
    }
    val options = if (isPassword && keyboardOptions.keyboardType == KeyboardType.Unspecified) {
        keyboardOptions.copy(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
    } else {
        keyboardOptions
    }
    val textColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f)
    val rimColor = when {
        isError -> scheme.error
        focused -> scheme.primary
        else -> Color.Transparent
    }
    val errorTint = if (isError) scheme.errorContainer.copy(alpha = 0.35f) else Color.Unspecified
    val surface = if (inset) {
        Modifier.glassInset(shape, tint = errorTint)
    } else {
        Modifier.liquidGlass(shape, GlassStyle.Material, tint = errorTint)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { if (errorText != null) error(errorText) },
        enabled = enabled,
        readOnly = readOnly,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
        keyboardOptions = options,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        visualTransformation = transformation,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(if (isError) scheme.error else scheme.primary),
        decorationBox = { innerTextField ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isError) scheme.error else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = FieldMinHeight)
                        .then(surface)
                        .border(if (focused || isError) 1.5.dp else 0.dp, rimColor, shape)
                        .padding(start = if (leadingIcon != null) 12.dp else 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp, horizontal = 0.dp)
                            .padding(end = if (trailing == null && !isPassword) 12.dp else 0.dp),
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    when {
                        isPassword -> IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.glass_hide_password else R.string.glass_show_password,
                                ),
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                        trailing != null -> Box(
                            Modifier.defaultMinSize(GlassTokens.MinTouchTarget, GlassTokens.MinTouchTarget),
                            contentAlignment = Alignment.Center,
                        ) { trailing() }
                    }
                }
                val below = errorText ?: supportingText
                if (below != null) {
                    Text(
                        text = below,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) scheme.error else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        },
    )
}

private val FieldRadius = 16.dp
