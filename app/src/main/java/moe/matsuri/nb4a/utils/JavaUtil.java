package moe.matsuri.nb4a.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.WebView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.nekohasekai.sagernet.BuildConfig;
import io.nekohasekai.sagernet.ktx.Logs;

public class JavaUtil {

    // Webview Utils

    private static final ExecutorService WEBVIEW_IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "webview-dir-cleanup");
        t.setDaemon(true);
        return t;
    });

    /** Sets the per-process WebView data directory suffix synchronously before WebView initialization. */
    public static void initWebviewSuffix(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        try {
            String processName = Application.getProcessName();
            if (!BuildConfig.APPLICATION_ID.equals(processName)) {
                String suffix = TextUtils.isEmpty(processName) ? context.getPackageName() : processName;
                WebView.setDataDirectorySuffix(suffix);
            }
        } catch (Exception e) {
            Logs.INSTANCE.e(e);
        }
    }

    /** Checks/recreates WebView lock files; intended for background I/O off the main thread. */
    public static void cleanWebviewDir(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        try {
            Set<String> pathSet = new HashSet<>();
            String suffix;
            String dataPath = context.getDataDir().getAbsolutePath();
            String webViewDir = "/app_webview";
            String huaweiWebViewDir = "/app_hws_webview";
            String lockFile = "/webview_data.lock";
            String processName = Application.getProcessName();
            if (!BuildConfig.APPLICATION_ID.equals(processName)) { // not the main process
                suffix = TextUtils.isEmpty(processName) ? context.getPackageName() : processName;
                suffix = "_" + suffix;
                pathSet.add(dataPath + webViewDir + suffix + lockFile);
                if (checkIsHuaweiRom()) {
                    pathSet.add(dataPath + huaweiWebViewDir + suffix + lockFile);
                }
            } else {
                // main process
                suffix = "_" + processName;
                pathSet.add(dataPath + webViewDir + lockFile); // without the process-name suffix (default)
                pathSet.add(dataPath + webViewDir + suffix + lockFile); // with the suffix the system adds
                if (checkIsHuaweiRom()) { // some Huawei phones rename the WebView directory
                    pathSet.add(dataPath + huaweiWebViewDir + lockFile);
                    pathSet.add(dataPath + huaweiWebViewDir + suffix + lockFile);
                }
            }
            for (String path : pathSet) {
                File file = new File(path);
                if (file.exists()) {
                    tryLockOrRecreateFile(file);
                    break;
                }
            }
        } catch (Exception e) {
            Logs.INSTANCE.e(e);
        }
    }

    public static void handleWebviewDir(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        initWebviewSuffix(context);
        final Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            WEBVIEW_IO_EXECUTOR.execute(() -> cleanWebviewDir(appContext));
        } else {
            cleanWebviewDir(appContext);
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private static void tryLockOrRecreateFile(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {
            FileLock tryLock = channel.tryLock();
            if (tryLock != null) {
                tryLock.close();
            } else {
                createFile(file, file.delete());
            }
        } catch (Exception e) {
            e.printStackTrace();
            boolean deleted = false;
            if (file.exists()) {
                deleted = file.delete();
            }
            createFile(file, deleted);
        }
    }

    private static void createFile(File file, boolean deleted) {
        try {
            if (deleted && !file.exists()) {
                file.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean checkIsHuaweiRom() {
        return Build.MANUFACTURER.contains("HUAWEI");
    }

    @SuppressLint("PrivateApi")
    public static String getProcessName() {
        if (Build.VERSION.SDK_INT >= 28)
            return Application.getProcessName();

        // Using the same technique as Application.getProcessName() for older devices
        // Using reflection since ActivityThread is an internal API

        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            String methodName = "currentProcessName";
            Method getProcessName = activityThread.getDeclaredMethod(methodName);
            return (String) getProcessName.invoke(null);
        } catch (Exception e) {
            return BuildConfig.APPLICATION_ID;
        }
    }

    // gson

    public static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setNumberToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .setLenient()
            .disableHtmlEscaping()
            .create();

}
