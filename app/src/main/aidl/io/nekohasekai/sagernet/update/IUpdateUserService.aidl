package io.nekohasekai.sagernet.update;

import android.os.ParcelFileDescriptor;

interface IUpdateUserService {
    int installApk(in ParcelFileDescriptor apkFd, long size, out String[] errorOut);
    void destroy();
}
