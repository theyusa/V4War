package fr.husi.repository

import org.koin.core.context.GlobalContext

fun resolveRepository(): Repository = GlobalContext.get().get()
