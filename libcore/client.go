package libcore

import (
	"net"
	"time"

	"libcore/vario"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

type Client struct {
	conn *net.UnixConn
}

func NewClient(basePath string) (*Client, error) {
	path := apiPath(basePath)
	var (
		conn *net.UnixConn
		err  error
	)
	for i := range 10 {
		conn, err = net.DialUnix("unix", nil, &net.UnixAddr{Name: path, Net: "unix"})
		if err == nil {
			break
		}
		time.Sleep(time.Duration(100+i*50) * time.Millisecond)
	}
	if err != nil {
		return nil, E.Cause(err, "dial unix")
	}
	return &Client{conn: conn}, nil
}

func (c *Client) Close() error {
	return common.Close(c.conn)
}

func (c *Client) ImportDeepLinks(deepLinks StringIterator) error {
	err := vario.WriteUint8(c.conn, commandImportDeepLink)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = vario.WriteStringSlice(c.conn, iteratorToArray(deepLinks))
	if err != nil {
		return E.Cause(err, "write deep link")
	}
	return nil
}
