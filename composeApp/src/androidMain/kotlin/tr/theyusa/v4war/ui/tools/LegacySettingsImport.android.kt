package tr.theyusa.v4war.ui.tools

import android.os.Parcel
import tr.theyusa.v4war.database.DataStore
import tr.theyusa.v4war.database.preference.KeyValuePair
import tr.theyusa.v4war.database.preference.importLegacyPairs
import tr.theyusa.v4war.ktx.b64Decode
import kotlinx.parcelize.parcelableCreator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

internal actual suspend fun importLegacySettingPairs(rawSettings: Any) {
    val array = rawSettings as JsonArray
    val pairs = mutableListOf<KeyValuePair>()
    for (element in array) {
        val data = element.jsonPrimitive.content.b64Decode()
        val parcel = Parcel.obtain()
        parcel.unmarshall(data, 0, data.size)
        parcel.setDataPosition(0)
        pairs.add(parcelableCreator<KeyValuePair>().createFromParcel(parcel))
        parcel.recycle()
    }
    DataStore.configurationStore.importLegacyPairs(pairs)
}
