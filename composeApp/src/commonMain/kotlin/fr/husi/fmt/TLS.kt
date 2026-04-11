package fr.husi.fmt

import fr.husi.database.DataStore

fun effectiveAllowInsecure(profileAllowInsecure: Boolean): Boolean {
    return profileAllowInsecure || DataStore.globalAllowInsecure
}
