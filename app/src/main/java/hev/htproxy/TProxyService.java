package hev.htproxy;

/**
 * Native bridge to libhev-socks5-tunnel.so (pkg: hev/htproxy, class: TProxyService).
 */
public final class TProxyService {
    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public native boolean TProxyStartService(String configPath, int fd);
    public native boolean TProxyStopService();
    public native boolean TProxyIsRunning();
    public native long[] TProxyGetStats();
}
