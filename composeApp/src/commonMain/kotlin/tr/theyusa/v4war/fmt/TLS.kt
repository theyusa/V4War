package tr.theyusa.v4war.fmt

import tr.theyusa.v4war.database.DataStore

fun effectiveAllowInsecure(profileAllowInsecure: Boolean): Boolean {
    return profileAllowInsecure || DataStore.globalAllowInsecure
}
