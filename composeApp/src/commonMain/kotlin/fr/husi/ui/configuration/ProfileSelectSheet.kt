@file:OptIn(ExperimentalMaterial3Api::class)

package fr.husi.ui.configuration

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProfileSelectSheet(
    preSelected: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    val state = rememberProfilePickerState(preSelected = preSelected)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        ProfilePickerContent(
            state = state,
            onDismiss = onDismiss,
            onSelected = onSelected,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            bottomPadding = 0.dp,
        )
    }
}
