package io.nekohasekai.sagernet.outbound;

import go.Seq;
import io.throneproj.mobile.InterfaceUpdateListener;

/**
 * Factory in a non-`moe.matsuri.nb4a` package returning package-private proxy wrappers
 * matching gomobile's `Mobile$proxyInterfaceUpdateListener` cross-package access boundary.
 */
public final class TestProxyFactory {
    private TestProxyFactory() {
    }

    public static InterfaceUpdateListener newProxyWrapper(int refnum) {
        return new TestProxyInterfaceUpdateListener(refnum);
    }
}

final class TestProxyInterfaceUpdateListener implements Seq.Proxy, InterfaceUpdateListener {
    public final int refnum;

    TestProxyInterfaceUpdateListener(int refnum) {
        this.refnum = refnum;
    }

    @Override
    public int incRefnum() {
        throw new AssertionError("incRefnum() must not be invoked by monitorKey()");
    }

    @Override
    public void updateDefaultInterface(String name, int index, boolean isExpensive, boolean isConstrained) {
    }

    @Override
    public void updateNetworkPath(String path) {
    }
}
