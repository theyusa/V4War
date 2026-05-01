package tr.theyusa.v4war.bg

import java.net.Socket

expect object NetworkSocketFactory {
    fun createSocket(): Socket?
}