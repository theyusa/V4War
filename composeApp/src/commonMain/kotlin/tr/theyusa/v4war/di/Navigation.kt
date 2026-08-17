@file:OptIn(KoinExperimentalAPI::class)

package tr.theyusa.v4war.di

import androidx.navigation3.runtime.NavKey
import tr.theyusa.v4war.results.LocalResultEventBus
import tr.theyusa.v4war.ui.AboutScreen
import tr.theyusa.v4war.ui.AssetEditScreen
import tr.theyusa.v4war.ui.AssetsScreen
import tr.theyusa.v4war.ui.GroupScreen
import tr.theyusa.v4war.ui.GroupSettingsScreen
import tr.theyusa.v4war.ui.LibrariesScreen
import tr.theyusa.v4war.ui.DrawerController
import tr.theyusa.v4war.ui.LogcatScreen
import tr.theyusa.v4war.ui.Navigator
import tr.theyusa.v4war.ui.MainScreenScope
import tr.theyusa.v4war.ui.MainViewModel
import tr.theyusa.v4war.ui.NavRoutes
import tr.theyusa.v4war.ui.PluginScreen
import tr.theyusa.v4war.ui.ProfilePickerController
import tr.theyusa.v4war.ui.RouteScreen
import tr.theyusa.v4war.ui.RouteSettingsScreen
import tr.theyusa.v4war.ui.SettingsScreen
import tr.theyusa.v4war.ui.configuration.ConfigurationScreen
import tr.theyusa.v4war.ui.dashboard.ConnectionDetailScreen
import tr.theyusa.v4war.ui.dashboard.DashboardScreen
import tr.theyusa.v4war.ui.profile.ConfigEditScreen
import tr.theyusa.v4war.ui.profile.ProfileEditorScreen
import tr.theyusa.v4war.ui.tools.GetCertScreen
import tr.theyusa.v4war.ui.tools.RuleSetMatchScreen
import tr.theyusa.v4war.ui.tools.SpeedtestScreen
import tr.theyusa.v4war.ui.tools.StunScreen
import tr.theyusa.v4war.ui.tools.ToolsScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

internal val commonNavigationModule = module {
    scope<MainScreenScope> {
        viewModelOf(::MainViewModel)
        scoped { (backStack: MutableList<NavKey>) ->
            Navigator(backStack)
        }
        scoped { (onDrawerClick: () -> Unit) ->
            DrawerController(onDrawerClick)
        }
        scopedOf(::ProfilePickerController)

        navigation<NavRoutes.Configuration> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            val navigator = get<Navigator>()
            ConfigurationScreen(
                mainViewModel = viewModel,
                onNavigationClick = drawerController::toggle,
                onOpenProfileEditor = navigator::navigateTo,
            )
        }

        navigation<NavRoutes.Groups> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            val navigator = get<Navigator>()
            GroupScreen(
                mainViewModel = viewModel,
                onDrawerClick = drawerController::toggle,
                openGroupSettings = { groupId ->
                    navigator.navigateTo(NavRoutes.GroupSettings(groupId = groupId))
                },
            )
        }

        navigation<NavRoutes.Route> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            val navigator = get<Navigator>()
            RouteScreen(
                mainViewModel = viewModel,
                onDrawerClick = drawerController::toggle,
                openRouteSettings = { routeId ->
                    navigator.navigateTo(NavRoutes.RouteSettings(routeId = routeId))
                },
                openAssets = {
                    navigator.navigateTo(NavRoutes.Assets)
                },
            )
        }

        navigation<NavRoutes.Settings> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            val navigator = get<Navigator>()
            SettingsScreen(
                mainViewModel = viewModel,
                onDrawerClick = drawerController::toggle,
                openSettingsPage = { kind ->
                    navigator.navigateTo(NavRoutes.SettingsPage(kind = kind))
                },
                openTools = { navigator.navigateTo(NavRoutes.Tools) },
                openPlugin = { navigator.navigateTo(NavRoutes.Plugin) },
                openAbout = { navigator.navigateTo(NavRoutes.About) },
            )
        }

        navigation<NavRoutes.SettingsPage> { route ->
            val navigator = get<Navigator>()
            SettingsPageScreen(
                kind = route.kind,
                onBackPress = { navigator.popBackStack() },
                openAppManager = {
                    navigator.navigateTo(NavRoutes.AppManager)
                },
            )
        }

        navigation<NavRoutes.Plugin> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            PluginScreen(
                mainViewModel = viewModel,
                onDrawerClick = drawerController::toggle,
            )
        }

        navigation<NavRoutes.Log> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            LogcatScreen(
                mainViewModel = viewModel,
                onDrawerClick = drawerController::toggle,
            )
        }

        navigation<NavRoutes.Dashboard> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            val navigator = get<Navigator>()
            DashboardScreen(
                mainViewModel = viewModel,
                onDrawerClick = drawerController::toggle,
                openConnectionDetail = { uuid ->
                    navigator.navigateTo(NavRoutes.ConnectionsDetail(uuid = uuid))
                },
            )
        }

        navigation<NavRoutes.ConnectionsDetail> { route ->
            val navigator = get<Navigator>()
            ConnectionDetailScreen(
                uuid = route.uuid,
                popup = { navigator.popBackStack() },
                openRouteSettings = { initialState ->
                    navigator.navigateTo(
                        NavRoutes.RouteSettings(
                            routeId = -1L,
                            useDraft = true,
                            initialState = initialState,
                        ),
                    )
                },
            )
        }

        navigation<NavRoutes.ProfileEditor> { route ->
            val navigator = get<Navigator>()
            val profilePickerController = get<ProfilePickerController>()
            val resultBus = LocalResultEventBus.current
            ProfileEditorScreen(
                type = route.type,
                profileId = route.id,
                isSubscription = route.subscription,
                onOpenProfileSelect = profilePickerController::open,
                onOpenConfigEditor = navigator::navigateTo,
                onResult = { updated ->
                    resultBus.sendResult(route.resultKey, updated)
                    navigator.popBackStack()
                },
            )
        }

        navigation<NavRoutes.GroupSettings> { route ->
            val navigator = get<Navigator>()
            val profilePickerController = get<ProfilePickerController>()
            GroupSettingsScreen(
                groupId = route.groupId,
                onBackPress = { navigator.popBackStack() },
                onOpenProfileSelect = profilePickerController::open,
            )
        }

        navigation<NavRoutes.RouteSettings> { route ->
            val navigator = get<Navigator>()
            val profilePickerController = get<ProfilePickerController>()
            RouteSettingsScreen(
                routeId = route.routeId,
                initialState = route.initialState.takeIf { route.useDraft },
                onBackPress = { navigator.popBackStack() },
                onSaved = { navigator.popBackStack() },
                onOpenProfileSelect = profilePickerController::open,
                onOpenAppList = navigator::navigateTo,
                onOpenConfigEditor = navigator::navigateTo,
            )
        }

        navigation<NavRoutes.ConfigEditor> { route ->
            val navigator = get<Navigator>()
            ConfigEditScreen(
                initialText = route.initialText,
                resultKey = route.resultKey,
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.Assets> { _ ->
            val navigator = get<Navigator>()
            AssetsScreen(
                onBackPress = { navigator.popBackStack() },
                onOpenAssetEditor = navigator::navigateTo,
            )
        }

        navigation<NavRoutes.AssetEdit> { route ->
            val navigator = get<Navigator>()
            AssetEditScreen(
                assetName = route.assetName,
                resultKey = route.resultKey,
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.Tools> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            val navigator = get<Navigator>()
            ToolsScreen(
                mainViewModel = viewModel,
                onDrawerClick = drawerController::toggle,
                onOpenTool = navigator::navigateTo,
            )
        }

        navigation<NavRoutes.ToolsPage.Stun> { _ ->
            val navigator = get<Navigator>()
            StunScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.GetCert> { _ ->
            val navigator = get<Navigator>()
            GetCertScreen(
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.SpeedTest> { _ ->
            val navigator = get<Navigator>()
            SpeedtestScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.RuleSetMatch> { _ ->
            val navigator = get<Navigator>()
            RuleSetMatchScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.About> { _ ->
            val drawerController = get<DrawerController>()
            val viewModel = koinViewModel<MainViewModel>()
            val navigator = get<Navigator>()
            AboutScreen(
                mainViewModel = viewModel,
                onDrawerClick = drawerController::toggle,
                onNavigateToLibraries = {
                    navigator.navigateTo(NavRoutes.Libraries)
                },
            )
        }

        navigation<NavRoutes.Libraries> { _ ->
            val navigator = get<Navigator>()
            LibrariesScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }
    }
}
