package tr.theyusa.v4war.ui

import tr.theyusa.v4war.di.initV4WarKoin
import tr.theyusa.v4war.repository.FakeRepository
import org.koin.core.context.GlobalContext

internal fun ensurePreviewRepository() {
    if (GlobalContext.getOrNull() == null) {
        initV4WarKoin(FakeRepository())
    }
}
