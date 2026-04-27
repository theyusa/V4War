@file:OptIn(ExperimentalLayoutApi::class)

package tr.theyusa.v4war.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import tr.theyusa.v4war.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tr.theyusa.v4war.compose.theme.AppTheme
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.permission.LocalPermissionPlatform
import tr.theyusa.v4war.permission.rememberAndroidPermissionPlatform
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.ui.configuration.ProfilePickerContent
import tr.theyusa.v4war.ui.configuration.rememberProfilePickerState

class SwitchActivity : ComposeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val platformPermission = rememberAndroidPermissionPlatform()
            CompositionLocalProvider(
                LocalPermissionPlatform provides platformPermission,
            ) {
                AppTheme {
                    val dismissInteractionSource = remember { MutableInteractionSource() }
                    val pickerState = rememberProfilePickerState(
                        preSelected = DataStore.selectedProxy,
                    )
                    val bottomPadding = WindowInsets.navigationBarsIgnoringVisibility
                        .asPaddingValues()
                        .calculateBottomPadding()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = dismissInteractionSource,
                                    indication = null,
                                    onClick = ::finish,
                                ),
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(0.75f),
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        ) {
                            ProfilePickerContent(
                                state = pickerState,
                                onDismiss = ::finish,
                                onSelected = ::returnProfile,
                                modifier = Modifier.fillMaxSize(),
                                bottomPadding = bottomPadding,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun returnProfile(profileId: Long) {
        DataStore.selectedProxy = profileId
        resolveRepository().reloadService()
        finish()
    }
}
