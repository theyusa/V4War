package tr.theyusa.v4war.compose

import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import tr.theyusa.v4war.compose.material3.Icon
import tr.theyusa.v4war.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun SimpleIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TooltipIconButton(
        onClick = onClick,
        icon = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun TooltipIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
) {
    val tooltipState = rememberTooltipState()
    val hapticClick = rememberHapticClick()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip {
                Text(contentDescription)
            }
        },
        state = tooltipState,
    ) {
        IconButton(
            onClick = {
                hapticClick()
                onClick()
            },
            modifier = modifier,
            enabled = enabled,
            colors = colors,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    }
}


@Composable
fun TextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text)
    }
}
