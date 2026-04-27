package tr.theyusa.v4war.repository

import org.koin.core.context.GlobalContext

fun resolveRepository(): Repository = GlobalContext.get().get()
