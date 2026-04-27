package tr.theyusa.v4war.di

import tr.theyusa.v4war.compose.material3.PlatformMaterialApi
import tr.theyusa.v4war.compose.theme.PlatformThemeApi
import tr.theyusa.v4war.repository.Repository
import tr.theyusa.v4war.ui.ImportLinkInteractor
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private fun commonUiModule() = module {
    single<PlatformMaterialApi> { platformMaterialApi() }
    single<PlatformThemeApi> { platformThemeApi() }
    singleOf(::ImportLinkInteractor)
}

internal expect fun platformMaterialApi(): PlatformMaterialApi
internal expect fun platformThemeApi(): PlatformThemeApi
internal expect fun platformRepositoryModule(repository: Repository): Module
internal expect fun platformKoinModules(): List<Module>

fun initV4WarKoin(repository: Repository) {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        modules(
            listOf(platformRepositoryModule(repository), commonUiModule(), commonNavigationModule) +
                platformKoinModules(),
        )
    }
}
