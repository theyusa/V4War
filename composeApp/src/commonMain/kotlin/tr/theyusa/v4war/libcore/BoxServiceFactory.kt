package tr.theyusa.v4war.libcore

expect fun createBoxService(isBgProcess: Boolean): Service?

expect fun loadCA(provider: Int)