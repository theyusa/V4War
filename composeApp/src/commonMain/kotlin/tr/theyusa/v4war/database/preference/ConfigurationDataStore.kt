package tr.theyusa.v4war.database.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

internal expect fun createConfigurationDataStore(): DataStore<Preferences>
