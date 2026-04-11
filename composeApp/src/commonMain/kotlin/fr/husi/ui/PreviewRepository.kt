package fr.husi.ui

import fr.husi.di.initV4WarKoin
import fr.husi.repository.FakeRepository
import org.koin.core.context.GlobalContext

internal fun ensurePreviewRepository() {
    if (GlobalContext.getOrNull() == null) {
        initV4WarKoin(FakeRepository())
    }
}
