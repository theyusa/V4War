package io.nekohasekai.sagernet.hotspotshare

import android.content.Context
import android.content.ContextWrapper
import io.nekohasekai.sagernet.hotspotshare.shizuku.ShellContextHelper
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellContextHelperTest {

    private class PlainContextWrapper : ContextWrapper(null)

    @Test
    fun patchContextFieldsFailsFastWhenFieldMissing() {
        val dummyContext = PlainContextWrapper()

        val exception = assertThrows(IllegalStateException::class.java) {
            ShellContextHelper.patchContextFields(dummyContext)
        }
        assertTrue(exception.message!!.contains("mOpPackageName"))
    }

    @Test
    fun injectContextIntoTetheringManagerFailsFastWhenFieldMissing() {
        val dummyTm = Any()
        val dummyContext = PlainContextWrapper()

        val exception = assertThrows(IllegalStateException::class.java) {
            ShellContextHelper.injectContextIntoTetheringManager(dummyTm, dummyContext)
        }
        assertTrue(exception.message!!.contains("mContext field was not found"))
    }
}
