package tr.theyusa.v4war.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AppBarRow
import tr.theyusa.v4war.compose.material3.Icon
import androidx.compose.material3.Scaffold
import tr.theyusa.v4war.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tr.theyusa.v4war.GroupOrder
import tr.theyusa.v4war.GroupType
import tr.theyusa.v4war.SubscriptionType
import tr.theyusa.v4war.compose.BackHandler
import tr.theyusa.v4war.compose.BoxedVerticalScrollbar
import tr.theyusa.v4war.compose.LinkOrContentTextField
import tr.theyusa.v4war.compose.MoreOverIcon
import tr.theyusa.v4war.compose.PreferenceCategory
import tr.theyusa.v4war.compose.PreferenceType
import tr.theyusa.v4war.compose.SimpleIconButton
import tr.theyusa.v4war.compose.TextButton
import tr.theyusa.v4war.compose.UIntegerTextField
import tr.theyusa.v4war.compose.withNavigation
import tr.theyusa.v4war.database.SagerDatabase
import tr.theyusa.v4war.ktx.USER_AGENT
import tr.theyusa.v4war.ktx.blankAsNull
import tr.theyusa.v4war.ktx.contentOrUnset
import tr.theyusa.v4war.ktx.intListN
import tr.theyusa.v4war.repository.resolveRepository
import tr.theyusa.v4war.resources.Res
import tr.theyusa.v4war.resources.apply
import tr.theyusa.v4war.resources.auto_update
import tr.theyusa.v4war.resources.auto_update_delay
import tr.theyusa.v4war.resources.close
import tr.theyusa.v4war.resources.deduplication
import tr.theyusa.v4war.resources.deduplication_sum
import tr.theyusa.v4war.resources.delete
import tr.theyusa.v4war.resources.delete_group_prompt
import tr.theyusa.v4war.resources.delete_sweep
import tr.theyusa.v4war.resources.done
import tr.theyusa.v4war.resources.emoji_symbols
import tr.theyusa.v4war.resources.filter_regex
import tr.theyusa.v4war.resources.flip_camera_android
import tr.theyusa.v4war.resources.force_resolve
import tr.theyusa.v4war.resources.force_resolve_sum
import tr.theyusa.v4war.resources.front_proxy
import tr.theyusa.v4war.resources.grid_3x3
import tr.theyusa.v4war.resources.group_basic
import tr.theyusa.v4war.resources.group_name
import tr.theyusa.v4war.resources.group_order
import tr.theyusa.v4war.resources.group_order_by_delay
import tr.theyusa.v4war.resources.group_order_by_name
import tr.theyusa.v4war.resources.group_order_origin
import tr.theyusa.v4war.resources.group_settings
import tr.theyusa.v4war.resources.group_subscription_link
import tr.theyusa.v4war.resources.group_type
import tr.theyusa.v4war.resources.import_contacts
import tr.theyusa.v4war.resources.landing_proxy
import tr.theyusa.v4war.resources.layers
import tr.theyusa.v4war.resources.link
import tr.theyusa.v4war.resources.low_priority
import tr.theyusa.v4war.resources.manage_search
import tr.theyusa.v4war.resources.nfc
import tr.theyusa.v4war.resources.no
import tr.theyusa.v4war.resources.no_thanks
import tr.theyusa.v4war.resources.not_set
import tr.theyusa.v4war.resources.ok
import tr.theyusa.v4war.resources.ooc_subscription_token
import tr.theyusa.v4war.resources.oocv1
import tr.theyusa.v4war.resources.proxy_chain
import tr.theyusa.v4war.resources.public_icon
import tr.theyusa.v4war.resources.question_mark
import tr.theyusa.v4war.resources.raw
import tr.theyusa.v4war.resources.route_profile
import tr.theyusa.v4war.resources.security
import tr.theyusa.v4war.resources.sip008
import tr.theyusa.v4war.resources.ssh_auth_type_none
import tr.theyusa.v4war.resources.subscription
import tr.theyusa.v4war.resources.subscription_custom_sni
import tr.theyusa.v4war.resources.subscription_remove_non_tls_xtls
import tr.theyusa.v4war.resources.subscription_remove_non_tls_xtls_sum
import tr.theyusa.v4war.resources.subscription_settings
import tr.theyusa.v4war.resources.subscription_type
import tr.theyusa.v4war.resources.subscription_user_agent
import tr.theyusa.v4war.resources.unsaved_changes_prompt
import tr.theyusa.v4war.resources.update_settings
import tr.theyusa.v4war.resources.update_when_connected_only
import tr.theyusa.v4war.resources.update_when_connected_only_sum
import tr.theyusa.v4war.resources.vpn_key
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun GroupSettingsScreen(
    groupId: Long,
    onBackPress: () -> Unit,
    onOpenProfileSelect: OpenProfilePicker,
    modifier: Modifier = Modifier,
    viewModel: GroupSettingsViewModel = viewModel { GroupSettingsViewModel(groupId) },
) {
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    var showBackAlert by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty) {
        showBackAlert = true
    }
    var showDeleteAlert by remember { mutableStateOf(false) }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val uiState by viewModel.uiState.collectAsState()

    fun saveAndExit() {
        viewModel.save()
        onBackPress()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.group_settings)) },
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        onBackPress()
                    }
                },
                actions = {
                    AppBarRow(
                        overflowIndicator = ::MoreOverIcon,
                    ) {
                        clickableItem(
                            onClick = {
                                if (viewModel.isNew) {
                                    onBackPress()
                                } else {
                                    showDeleteAlert = true
                                }
                            },
                            icon = {
                                Icon(
                                    vectorResource(Res.drawable.delete),
                                    null,
                                )
                            },
                            label = runBlocking { resolveRepository().getString(Res.string.delete) },
                        )
                        clickableItem(
                            onClick = ::saveAndExit,
                            icon = {
                                Icon(vectorResource(Res.drawable.done), null)
                            },
                            label = runBlocking { resolveRepository().getString(Res.string.apply) },
                        )
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = contentPadding,
                ) {
                    groupSettings(
                        uiState = uiState,
                        viewModel = viewModel,
                        selectFrontProxy = {
                            onOpenProfileSelect(uiState.frontProxy.takeIf { it > 0 }) { id ->
                                viewModel.setFrontProxy(id)
                            }
                        },
                        selectLandingProxy = {
                            onOpenProfileSelect(uiState.landingProxy.takeIf { it > 0 }) { id ->
                                viewModel.setLandingProxy(id)
                            }
                        },
                    )
                }

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
    }

    if (showBackAlert) {
        AlertDialog(
            onDismissRequest = { showBackAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    saveAndExit()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no)) {
                    onBackPress()
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
        )
    }
    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.delete()
                    onBackPress()
                    showDeleteAlert = false
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no_thanks)) {
                    showDeleteAlert = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.delete_group_prompt)) },
        )
    }
}

private fun LazyListScope.groupSettings(
    uiState: GroupSettingsUiState,
    viewModel: GroupSettingsViewModel,
    selectFrontProxy: () -> Unit,
    selectLandingProxy: () -> Unit,
) {
    item("name", PreferenceType.TEXT_FIELD) {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.group_name)) },
            textToValue = { it },
            icon = { Icon(vectorResource(Res.drawable.emoji_symbols), null) },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }

    fun groupType(type: Int) = when (type) {
        GroupType.BASIC -> Res.string.group_basic
        GroupType.SUBSCRIPTION -> Res.string.subscription
        else -> error("impossible")
    }
    item("type", PreferenceType.LIST) {
        ListPreference(
            value = uiState.type,
            onValueChange = { viewModel.setType(it) },
            values = intListN(2),
            title = { Text(stringResource(Res.string.group_type)) },
            icon = { Icon(vectorResource(Res.drawable.layers), null) },
            summary = { Text(stringResource(groupType(uiState.type))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringResource(groupType(it))) },
        )
    }

    fun groupOrder(order: Int) = when (order) {
        GroupOrder.ORIGIN -> Res.string.group_order_origin
        GroupOrder.BY_NAME -> Res.string.group_order_by_name
        GroupOrder.BY_DELAY -> Res.string.group_order_by_delay
        else -> error("impossible")
    }
    item("order", PreferenceType.LIST) {
        ListPreference(
            value = uiState.order,
            onValueChange = { viewModel.setOrder(it) },
            values = intListN(3),
            title = { Text(stringResource(Res.string.group_order)) },
            icon = { Icon(vectorResource(Res.drawable.low_priority), null) },
            summary = { Text(stringResource(groupOrder(uiState.order))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringResource(groupOrder(it))) },
        )
    }

    item("category_chain", PreferenceType.CATEGORY) {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_chain)) })
    }
    fun chainName(id: Long) = runBlocking { SagerDatabase.proxyDao.getById(id) }?.displayName()
    item("font", PreferenceType.LIST) {
        ListPreference(
            value = uiState.frontProxy,
            onValueChange = {
                if (it == -1L) {
                    viewModel.setFrontProxy(it)
                } else {
                    selectFrontProxy()
                }
            },
            values = listOf(-1L, 0L),
            title = { Text(stringResource(Res.string.front_proxy)) },
            icon = { Icon(vectorResource(Res.drawable.low_priority), null) },
            summary = {
                val text = chainName(uiState.frontProxy)
                    ?: stringResource(Res.string.not_set)
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val id = if (it == -1L) {
                    Res.string.ssh_auth_type_none
                } else {
                    Res.string.route_profile
                }
                AnnotatedString(stringResource(id))
            },
        )
    }
    item("landing", PreferenceType.LIST) {
        ListPreference(
            value = uiState.landingProxy,
            onValueChange = {
                if (it == -1L) {
                    viewModel.setLandingProxy(it)
                } else {
                    selectLandingProxy()
                }
            },
            values = listOf(-1L, 0L),
            title = { Text(stringResource(Res.string.landing_proxy)) },
            icon = { Icon(vectorResource(Res.drawable.public_icon), null) },
            summary = {
                val text = chainName(uiState.landingProxy)
                    ?: stringResource(Res.string.not_set)
                Text(text)
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val id = if (it == -1L) {
                    Res.string.ssh_auth_type_none
                } else {
                    Res.string.route_profile
                }
                AnnotatedString(stringResource(id))
            },
        )
    }

    if (uiState.type == GroupType.SUBSCRIPTION) {
        item("category_subscription", PreferenceType.CATEGORY) {
            PreferenceCategory(text = { Text(stringResource(Res.string.subscription_settings)) })
        }
        fun subType(type: Int) = when (type) {
            SubscriptionType.RAW -> Res.string.raw
            SubscriptionType.OOCv1 -> Res.string.oocv1
            SubscriptionType.SIP008 -> Res.string.sip008
            else -> error("impossible")
        }
        item("subscription_type", PreferenceType.LIST) {
            ListPreference(
                value = uiState.subscriptionType,
                onValueChange = { viewModel.setSubscriptionType(it) },
                values = intListN(3),
                title = { Text(stringResource(Res.string.subscription_type)) },
                icon = { Icon(vectorResource(Res.drawable.nfc), null) },
                summary = { Text(stringResource(subType(uiState.subscriptionType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(stringResource(subType(it))) },
            )
        }

        item("subscription_link", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionLink,
                onValueChange = { viewModel.setSubscriptionLink(it) },
                title = { Text(stringResource(Res.string.group_subscription_link)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.link), null) },
                summary = { Text(contentOrUnset(uiState.subscriptionLink)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    LinkOrContentTextField(value, onValueChange, onOk)
                },
            )
        }
        val isOOCv1 = uiState.subscriptionType == SubscriptionType.OOCv1
        if (isOOCv1) {
            item("subscription_token", PreferenceType.TEXT_FIELD) {
                TextFieldPreference(
                    value = uiState.subscriptionToken,
                    onValueChange = { viewModel.setSubscriptionToken(it) },
                    title = { Text(stringResource(Res.string.ooc_subscription_token)) },
                    textToValue = { it },
                    icon = { Icon(vectorResource(Res.drawable.vpn_key), null) },
                    summary = { Text(contentOrUnset(uiState.subscriptionToken)) },
                    valueToText = { it },
                )
            }
        }

        item("subscription_force_resolve", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionForceResolve,
                onValueChange = { viewModel.setSubscriptionForceResolve(it) },
                title = { Text(stringResource(Res.string.force_resolve)) },
                icon = { Icon(vectorResource(Res.drawable.manage_search), null) },
                summary = { Text(stringResource(Res.string.force_resolve_sum)) },
            )
        }
        item("subscription_deduplication", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionDeduplication,
                onValueChange = { viewModel.setSubscriptionDeduplication(it) },
                title = { Text(stringResource(Res.string.deduplication)) },
                icon = { Icon(vectorResource(Res.drawable.import_contacts), null) },
                summary = { Text(stringResource(Res.string.deduplication_sum)) },
            )
        }
        item("subscription_filter_not_regex", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionFilterNotRegex,
                onValueChange = { viewModel.setSubscriptionFilterNotRegex(it) },
                title = { Text(stringResource(Res.string.filter_regex)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.delete_sweep), null) },
                summary = { Text(contentOrUnset(uiState.subscriptionFilterNotRegex)) },
                valueToText = { it },
            )
        }
        item("subscription_custom_sni", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionCustomSni,
                onValueChange = { viewModel.setSubscriptionCustomSni(it) },
                title = { Text(stringResource(Res.string.subscription_custom_sni)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.link), null) },
                summary = { Text(contentOrUnset(uiState.subscriptionCustomSni)) },
                valueToText = { it },
            )
        }
        item("subscription_remove_non_tls_xtls", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionRemoveNonTlsXtls,
                onValueChange = { viewModel.setSubscriptionRemoveNonTlsXtls(it) },
                title = { Text(stringResource(Res.string.subscription_remove_non_tls_xtls)) },
                icon = { Icon(vectorResource(Res.drawable.delete_sweep), null) },
                summary = { Text(stringResource(Res.string.subscription_remove_non_tls_xtls_sum)) },
            )
        }

        item("category_update", PreferenceType.CATEGORY) {
            PreferenceCategory(text = { Text(stringResource(Res.string.update_settings)) })
        }
        item("subscription_update_when_connected_only", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionUpdateWhenConnectedOnly,
                onValueChange = { viewModel.setSubscriptionUpdateWhenConnectedOnly(it) },
                title = { Text(stringResource(Res.string.update_when_connected_only)) },
                icon = { Icon(vectorResource(Res.drawable.security), null) },
                summary = { Text(stringResource(Res.string.update_when_connected_only_sum)) },
            )
        }
        item("subscription_user_agent", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionUserAgent,
                onValueChange = { viewModel.setSubscriptionUserAgent(it) },
                title = { Text(stringResource(Res.string.subscription_user_agent)) },
                textToValue = { it },
                icon = { Icon(vectorResource(Res.drawable.grid_3x3), null) },
                summary = {
                    val text = uiState.subscriptionUserAgent.blankAsNull() ?: USER_AGENT
                    Text(text)
                },
                valueToText = { it },
            )
        }
        item("subscription_auto_update", PreferenceType.SWITCH) {
            SwitchPreference(
                value = uiState.subscriptionAutoUpdate,
                onValueChange = { viewModel.setSubscriptionAutoUpdate(it) },
                title = { Text(stringResource(Res.string.auto_update)) },
                icon = {
                    Icon(
                        vectorResource(Res.drawable.flip_camera_android),
                        null,
                    )
                },
            )
        }
        item("subscription_update_delay", PreferenceType.TEXT_FIELD) {
            TextFieldPreference(
                value = uiState.subscriptionUpdateDelay,
                onValueChange = { viewModel.setSubscriptionUpdateDelay(it) },
                title = { Text(stringResource(Res.string.auto_update_delay)) },
                textToValue = { it.toIntOrNull() ?: 1440 },
                enabled = uiState.subscriptionAutoUpdate,
                icon = { Icon(vectorResource(Res.drawable.grid_3x3), null) },
                summary = { Text(uiState.subscriptionUpdateDelay.toString()) },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}
