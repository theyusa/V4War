package libcore

import (
	"path/filepath"
)

const Socket = "api.sock"

const (
	commandQueryConnections uint8 = iota
	commandSubscribeConnections
	commandCloseConnection
	commandQueryMemory
	commandQueryGoroutines
	commandQueryClashModes
	commandSubscribeClashMode
	commandSetClashMode
	commandUrlTest
	commandNewInstanceURLTest
	commandGroupURLTest
	commandSelectOutbound
	commandQueryProxySets
	commandResetNetwork
	commandClearLog
	commandSubscribeLogs
	commandImportDeepLink
)

const (
	resultNoError uint8 = iota
	resultCommonError
)

func apiPath(basePath string) string {
	if basePath == "" {
		basePath = internalAssetsPath
	}
	return filepath.Join(basePath, Socket)
}
