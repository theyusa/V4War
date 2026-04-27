package tr.theyusa.v4war.bg

import java.io.Closeable

interface AbstractInstance : Closeable {

    fun launch()

}