package tr.theyusa.v4war.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import tr.theyusa.v4war.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.strippedLicenseContent
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.ScrollableDialog
import tr.theyusa.v4war.compose.SimpleIconButton
import tr.theyusa.v4war.compose.SimpleTopAppBar
import tr.theyusa.v4war.compose.TextButton
import tr.theyusa.v4war.compose.withNavigation
import tr.theyusa.v4war.ktx.emptyAsNull
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.arrow_back
import tr.theyusa.v4war.resources.back
import tr.theyusa.v4war.resources.copyright
import tr.theyusa.v4war.resources.ok
import tr.theyusa.v4war.resources.oss_licenses
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun LibrariesScreen(
    onBackPress: () -> Unit,
) {
    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    var showLibraryDialog by remember { mutableStateOf<Library?>(null) }
    val uriHandler = LocalUriHandler.current

    val libraries by produceLibraries {
        Res.readBytes("files/aboutlibraries.json").decodeToString()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SimpleTopAppBar(
                title = { Text(stringResource(Res.string.oss_licenses)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        onClick = onBackPress,
                    )
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            val contentPadding = innerPadding.withNavigation()
            LibrariesContainer(
                libraries = libraries,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                lazyListState = listState,
                contentPadding = contentPadding,
                onLibraryClick = { library ->
                    if (library.strippedLicenseContent.isNotBlank()) {
                        showLibraryDialog = library
                    } else {
                        val url = library.licenses.firstOrNull()?.url
                        if (!url.isNullOrBlank()) {
                            runCatching { uriHandler.openUri(url) }
                        }
                    }
                },
            )
            BoxedVerticalScrollbar(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState),
                style = defaultMaterialScrollbarStyle().copy(
                    thickness = 12.dp,
                ),
            )
        }
    }

    showLibraryDialog?.let { library ->
        LibrariesLicenseDialog(
            library = library,
            onDismiss = { showLibraryDialog = null },
        )
    }
}

@Composable
private fun LibrariesLicenseDialog(
    library: Library,
    onDismiss: () -> Unit,
) {
    val license = remember(library) {
        library.strippedLicenseContent.emptyAsNull()
    } ?: return

    ScrollableDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(stringResource(Res.string.ok)) { onDismiss() }
        },
        icon = { Icon(vectorResource(Res.drawable.copyright), null) },
        title = {
            SelectionContainer {
                Text(library.name)
            }
        },
        text = {
            SelectionContainer {
                Text(
                    text = license,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
    )
}
