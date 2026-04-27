package tr.theyusa.v4war.di

import tr.theyusa.v4war.compose.material3.PlatformMaterialApi
import tr.theyusa.v4war.compose.material3.TvPlatformMaterialApi
import tr.theyusa.v4war.compose.material3.standardPlatformMaterialApi
import tr.theyusa.v4war.compose.theme.PlatformThemeApi
import tr.theyusa.v4war.compose.theme.TvPlatformThemeApi
import tr.theyusa.v4war.compose.theme.standardPlatformThemeApi
import tr.theyusa.v4war.repository.AndroidRepository
import tr.theyusa.v4war.repository.Repository
import tr.theyusa.v4war.repository.resolveRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformMaterialApi(): PlatformMaterialApi {
    return if (resolveRepository().isTv) {
        TvPlatformMaterialApi
    } else {
        standardPlatformMaterialApi()
    }
}

internal actual fun platformThemeApi(): PlatformThemeApi {
    return if (resolveRepository().isTv) {
        TvPlatformThemeApi
    } else {
        standardPlatformThemeApi()
    }
}

internal actual fun platformRepositoryModule(repository: Repository): Module = module {
    val androidRepository = repository as? AndroidRepository
        ?: error("Android platform requires AndroidRepository, got ${repository::class.qualifiedName}")
    single<AndroidRepository> { androidRepository }
    single<Repository> { get<AndroidRepository>() }
}

internal actual fun platformKoinModules(): List<Module> = listOf(androidNavigationModule)
