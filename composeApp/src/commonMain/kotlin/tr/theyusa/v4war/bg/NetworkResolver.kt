package tr.theyusa.v4war.bg

import java.net.InetAddress

expect suspend fun resolveByDefaultNetwork(host: String): List<InetAddress>
