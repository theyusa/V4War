@file:OptIn(KoinExperimentalAPI::class)

package tr.theyusa.v4war.di

import tr.theyusa.v4war.ui.AppListScreen
import tr.theyusa.v4war.ui.AppManagerScreen
import tr.theyusa.v4war.ui.Navigator
import tr.theyusa.v4war.ui.MainScreenScope
import tr.theyusa.v4war.ui.NavRoutes
import tr.theyusa.v4war.ui.tools.VPNScannerScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

internal val androidNavigationModule = module {
    scope<MainScreenScope> {
        navigation<NavRoutes.AppManager> { _ ->
            val navigator = get<Navigator>()
            AppManagerScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.AppList> { route ->
            val navigator = get<Navigator>()
            AppListScreen(
                initialPackages = route.initialPackages,
                resultKey = route.resultKey,
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.VPNScanner> { _ ->
            val navigator = get<Navigator>()
            VPNScannerScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }
    }
}
