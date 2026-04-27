package tr.theyusa.v4war.database

import android.os.Binder

actual fun callingUserIndex(): Int = Binder.getCallingUserHandle().hashCode()
