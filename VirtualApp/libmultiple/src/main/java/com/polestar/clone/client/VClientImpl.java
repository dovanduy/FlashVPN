package com.polestar.clone.client;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Instrumentation;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;

import com.polestar.clone.client.core.CrashHandler;
import com.polestar.clone.client.core.InvocationStubManager;
import com.polestar.clone.client.core.VirtualCore;
import com.polestar.clone.client.env.SpecialComponentList;
import com.polestar.clone.client.env.VirtualRuntime;
import com.polestar.clone.client.fixer.ContextFixer;
import com.polestar.clone.client.hook.delegate.AppInstrumentation;
import com.polestar.clone.client.hook.providers.ProviderHook;
import com.polestar.clone.client.hook.proxies.am.HCallbackStub;
import com.polestar.clone.client.hook.secondary.ProxyServiceFactory;
import com.polestar.clone.client.ipc.VActivityManager;
import com.polestar.clone.client.ipc.VDeviceManager;
import com.polestar.clone.client.ipc.VPackageManager;
import com.polestar.clone.client.ipc.VirtualStorageManager;
import com.polestar.clone.client.stub.VASettings;
import com.polestar.clone.helper.compat.BuildCompat;
import com.polestar.clone.helper.compat.StorageManagerCompat;
import com.polestar.clone.helper.compat.StrictModeCompat;
import com.polestar.clone.helper.utils.FileUtils;
import com.polestar.clone.helper.utils.VLog;
import com.polestar.clone.os.VEnvironment;
import com.polestar.clone.os.VUserHandle;
import com.polestar.clone.remote.InstalledAppInfo;
import com.polestar.clone.remote.PendingResultData;
import com.polestar.clone.remote.VDeviceInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import mirror.android.app.ActivityThread;
import mirror.android.app.ActivityThreadNMR1;
import mirror.android.app.AlarmManager;
import mirror.android.app.ContextImpl;
import mirror.android.app.ContextImplKitkat;
import mirror.android.app.IActivityManager;
import mirror.android.app.LoadedApk;
import mirror.android.content.ContentProviderHolderOreo;
import mirror.android.content.res.CompatibilityInfo;
import mirror.android.providers.Settings;
import mirror.android.renderscript.RenderScriptCacheDir;
import mirror.android.view.DisplayAdjustments;
import mirror.android.view.HardwareRenderer;
import mirror.android.view.RenderScript;
import mirror.android.view.ThreadedRenderer;
import mirror.com.android.internal.content.ReferrerIntent;
import mirror.dalvik.system.VMRuntime;
import mirror.java.lang.ThreadGroupN;

import static com.polestar.clone.os.VUserHandle.getUserId;

/**
 * @author Lody
 */

public final class VClientImpl extends IVClient.Stub {

    private static final int NEW_INTENT = 11;
    private static final int RECEIVER = 12;

    private static final String TAG = VClientImpl.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static final VClientImpl gClient = new VClientImpl();
    private final H mH = new H();
    private ConditionVariable mTempLock;
    private Instrumentation mInstrumentation = AppInstrumentation.getDefault();
    private IBinder token;
    private int vuid;
    private int vpid;
    private VDeviceInfo deviceInfo;
    private AppBindData mBoundApplication;
    private Application mInitialApplication;
    private CrashHandler crashHandler;
    private final WatchDog mBindAppWatchDog = new WatchDog();
    private final static int WATCH_BIND_APP = 0;

    public static VClientImpl get() {
        return gClient;
    }

    public boolean isBound() {
        return mBoundApplication != null;
    }

    public VDeviceInfo getDeviceInfo() {
        if (deviceInfo == null) {
            synchronized (this) {
                if (deviceInfo == null) {
                    deviceInfo = VDeviceManager.get().getDeviceInfo(getUserId(vuid));
                }
            }
        }
        return deviceInfo;
    }

    public Application getCurrentApplication() {
        return mInitialApplication;
    }

    public String getCurrentPackage() {
        return mBoundApplication != null ?
                mBoundApplication.appInfo.packageName : VPackageManager.get().getNameForUid(getVUid());
    }

    public ApplicationInfo getCurrentApplicationInfo() {
        return mBoundApplication != null ? mBoundApplication.appInfo : null;
    }

    public CrashHandler getCrashHandler() {
        return crashHandler;
    }

    public void setCrashHandler(CrashHandler crashHandler) {
        this.crashHandler = crashHandler;
    }

    public int getVUid() {
        return vuid;
    }

	public int getVPid() {
		return vpid;
	}

    public int getBaseVUid() {
        return VUserHandle.getAppId(vuid);
    }

    public ClassLoader getClassLoader(ApplicationInfo appInfo) {
        Context context = createPackageContext(appInfo.packageName);
        return context.getClassLoader();
    }

    private void sendMessage(int what, Object obj) {
        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = obj;
        mH.sendMessage(msg);
    }

    @Override
    public IBinder getAppThread() {
        return ActivityThread.getApplicationThread.call(VirtualCore.mainThread());
    }

    @Override
    public IBinder getToken() {
        return token;
    }

    public void initProcess(IBinder token, int vuid, int vpid) {
        this.token = token;
        this.vuid = vuid;
		this.vpid = vpid;
		VLog.d(TAG, "initProcess for vuid: " + vuid + " vpid: " + vpid + " actual pid: " + Process.myPid() + " actual uid: " + Process.myUid());
    }

    private void handleNewIntent(NewIntentData data) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            intent = ReferrerIntent.ctor.newInstance(data.intent, data.creator);
        } else {
            intent = data.intent;
        }
        if (ActivityThread.performNewIntents != null) {
            ActivityThread.performNewIntents.call(
                    VirtualCore.mainThread(),
                    data.token,
                    Collections.singletonList(intent)
            );
        } else {
            ActivityThreadNMR1.performNewIntents.call(
                    VirtualCore.mainThread(),
                    data.token,
                    Collections.singletonList(intent),
                    true);
        }
    }

    @Override
    public void bindApplication(final String packageName, final String processName) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            bindApplicationNoCheck(packageName, processName, new ConditionVariable());
        } else {
            final ConditionVariable lock = new ConditionVariable();
            VirtualRuntime.getUIHandler().post(new Runnable() {
                @Override
                public void run() {
                    bindApplicationNoCheck(packageName, processName, lock);
                    lock.open();
                }
            });
            lock.block();
        }
    }


    private void bindApplicationNoCheck(String packageName, String processName, ConditionVariable lock) {
        VLog.d(TAG, "bindApplicationNoCheck " + packageName + " proc: " + processName);
        VDeviceInfo deviceInfo = getDeviceInfo();
        if (processName == null) {
            processName = packageName;
        }
        mBindAppWatchDog.watch(WATCH_BIND_APP, 75*1000);
        mTempLock = lock;
        if (isBound()) {
            mTempLock = null;
            if (lock != null) {
                lock.open();
            }
            VLog.logbug(TAG, "Already bound process: " + processName + " for package: " + packageName);
            return;
        }
        try {
            setupUncaughtHandler();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            fixInstalledProviders();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        mirror.android.os.Build.SERIAL.set(deviceInfo.serial);
        mirror.android.os.Build.DEVICE.set(Build.DEVICE.replace(" ", "_"));
        ActivityThread.mInitialApplication.set(
                VirtualCore.mainThread(),
                null
        );
        AppBindData data = new AppBindData();
        InstalledAppInfo info = VirtualCore.get().getInstalledAppInfo(packageName, 0);
        if (info == null) {
            new Exception("App not exist!").printStackTrace();
            Process.killProcess(0);
            System.exit(0);
        }
        data.appInfo = VPackageManager.get().getApplicationInfo(packageName, 0, getUserId(vuid));
        data.processName = processName;
        data.providers = VPackageManager.get().queryContentProviders(processName, getVUid(), PackageManager.GET_META_DATA);
        VLog.i(TAG, String.format("Binding application %s, (%s)", data.appInfo.packageName, data.processName));
        mBoundApplication = data;
        VLog.d(TAG, "bindApplicationNoCheck step 1" + packageName + " proc: " + processName);
        VirtualRuntime.setupRuntime(data.processName, data.appInfo);
        int targetSdkVersion = data.appInfo.targetSdkVersion;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && targetSdkVersion < Build.VERSION_CODES.N) {
            StrictModeCompat.disableDeathOnFileUriExposure();
        }

        Object v0_2 = VirtualCore.get().getContext().getSystemService(Context.ALARM_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && AlarmManager.mTargetSdkVersion != null) {
            try {
                AlarmManager.mTargetSdkVersion.set(v0_2, targetSdkVersion);
            }
            catch(Exception v0_3) {
                v0_3.printStackTrace();
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (mirror.android.os.StrictMode.sVmPolicyMask != null) {
                mirror.android.os.StrictMode.sVmPolicyMask.set(0);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && targetSdkVersion < Build.VERSION_CODES.LOLLIPOP) {
            mirror.android.os.Message.updateCheckRecycle.call(targetSdkVersion);
        }
        VLog.d(TAG, "bindApplicationNoCheck step 2" + packageName + " proc: " + processName);
        if (VASettings.ENABLE_IO_REDIRECT && SpecialComponentList.needIORedirect(packageName)) {
            startIOUniformer();
        }
        VLog.d(TAG, "bindApplicationNoCheck step 3 " + packageName + " proc: " + processName);
        NativeEngine.hookNative();
        Object mainThread = VirtualCore.mainThread();
        NativeEngine.startDexOverride();
        VLog.d(TAG, "bindApplicationNoCheck step 4 " + packageName + " proc: " + processName);
        Context context = createPackageContext(data.appInfo.packageName);
        try {
            // anti-virus, fuck ESET-NOD32: a variant of Android/AdDisplay.AdLock.AL potentially unwanted
            // we can make direct call... use reflect to bypass.
            // System.setProperty("java.io.tmpdir", context.getCacheDir().getAbsolutePath());
            System.class.getDeclaredMethod("setProperty", String.class, String.class)
                    .invoke(null, "java.io.tmpdir", new File(VEnvironment.getDataUserPackageDirectory(getUserId(vuid), packageName), "cache").getAbsolutePath());
        } catch (Throwable ignored) {
            VLog.e(TAG, "set tmp dir error:", ignored);
        }
        File codeCacheDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            codeCacheDir = context.getCodeCacheDir();
        } else {
            codeCacheDir = context.getCacheDir();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            if (HardwareRenderer.setupDiskCache != null) {
                HardwareRenderer.setupDiskCache.call(codeCacheDir);
            }
        } else {
            if (ThreadedRenderer.setupDiskCache != null) {
                ThreadedRenderer.setupDiskCache.call(codeCacheDir);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (RenderScriptCacheDir.setupDiskCache != null) {
                RenderScriptCacheDir.setupDiskCache.call(codeCacheDir);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (RenderScript.setupDiskCache != null) {
                RenderScript.setupDiskCache.call(codeCacheDir);
            }
        }
        Object boundApp = fixBoundApp(mBoundApplication);
        if (mainThread != null) {
            mBoundApplication.info = ActivityThread.getPackageInfoNoCheck.call(mainThread, data.appInfo,
                    CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO.get());
        }
        if (mBoundApplication.info == null) {
            VLog.logbug(TAG, "getPackageInfoNoCheck mainThread : " + mainThread == null ? "null" : "not null" + " error");
            mBoundApplication.info = ContextImpl.mPackageInfo.get(context);
        }
        mirror.android.app.ActivityThread.AppBindData.info.set(boundApp, data.info);
        VMRuntime.setTargetSdkVersion.call(VMRuntime.getRuntime.call(), data.appInfo.targetSdkVersion);

        if(LoadedApk.mSecurityViolation != null) {
            LoadedApk.mSecurityViolation.set(this.mBoundApplication.info, false);
        }
        Configuration v6 = context.getResources().getConfiguration();
        v0_2 = null;
        if(CompatibilityInfo.ctor != null) {
            v0_2 = CompatibilityInfo.ctor.newInstance(new Object[]{data.appInfo, Integer.valueOf(v6.screenLayout), Integer.valueOf(v6.smallestScreenWidthDp), Boolean.valueOf(false)});
        }

        if(CompatibilityInfo.ctorLG != null) {
            v0_2 = CompatibilityInfo.ctorLG.newInstance(new Object[]{data.appInfo, Integer.valueOf(v6.screenLayout), Integer.valueOf(v6.smallestScreenWidthDp), Boolean.valueOf(false), Integer.valueOf(0)});
        }

        if(v0_2 != null) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    DisplayAdjustments.setCompatibilityInfo.call(ContextImplKitkat.mDisplayAdjustments.get(context), new Object[]{v0_2});
                }
                DisplayAdjustments.setCompatibilityInfo.call(LoadedApk.mDisplayAdjustments.get(this.mBoundApplication.info), new Object[]{v0_2});
            }
        }

        boolean conflict = SpecialComponentList.isConflictingInstrumentation(packageName);
        if (!conflict) {
            InvocationStubManager.getInstance().checkEnv(AppInstrumentation.class);
        }
        if (data.info == null) {
            VLog.logbug("VClientImpl", "bindApplicationNoCheck:" + packageName + ":" + processName + ":data.info null");
            //should return here
        }
        VLog.d(TAG, "bindApplicationNoCheck step 4.1 " + packageName + " proc: " + processName);
        mInitialApplication = LoadedApk.makeApplication.call(data.info, false, null);
        VLog.d(TAG, "bindApplicationNoCheck step 4.2 " + packageName + " proc: " + processName);
        if (mInitialApplication == null) {
            VLog.logbug(TAG, "mInitialApplication is null");
            if (data.info != null) {
                mInitialApplication = LoadedApk.makeApplication.call(data.info, false, null);
            }
        }
        VLog.d(TAG, "bindApplicationNoCheck step 4.3 " + packageName + " proc: " + processName);
        mirror.android.app.ActivityThread.mInitialApplication.set(mainThread, mInitialApplication);
        ContextFixer.fixContext(mInitialApplication);
//        if (Build.VERSION.SDK_INT >= 24 && "com.tencent.mm:recovery".equals(processName)) {
//            fixWeChatRecovery(mInitialApplication);
//        }

        if("com.android.vending".equals("packageName")) {
            try {
                context.getSharedPreferences("vending_preferences", 0).edit().putBoolean("notify_updates", false).putBoolean("notify_updates_completion", false).apply();
                context.getSharedPreferences("finsky", 0).edit().putBoolean("auto_update_enabled", false).apply();
            }
            catch(Throwable v0) {
                v0.printStackTrace();
            }
        }
        if (data.providers != null) {
            installContentProviders(mInitialApplication, data.providers);
        }
        VLog.d(TAG, "bindApplicationNoCheck step 4.4 " + packageName + " proc: " + processName);
        if (lock != null) {
            lock.open();
            mTempLock = null;
        }
        VLog.d(TAG, "bindApplicationNoCheck step 5 " + packageName + " proc: " + processName);
        VirtualCore.get().getComponentDelegate().beforeApplicationCreate(mInitialApplication);
        try {
            mInstrumentation.callApplicationOnCreate(mInitialApplication);
            InvocationStubManager.getInstance().checkEnv(HCallbackStub.class);
            if (conflict) {
                InvocationStubManager.getInstance().checkEnv(AppInstrumentation.class);
            }
            Application createdApp = ActivityThread.mInitialApplication.get(mainThread);
            if (createdApp != null) {
                mInitialApplication = createdApp;
            }
        } catch (Exception e) {
            if (!mInstrumentation.onException(mInitialApplication, e)) {
                if (data!=null && data.appInfo!=null) {
                    VLog.logbug(TAG, "Unable to create application " + data.appInfo.name + ": " + e.toString());
                }
                System.exit(0);
//                throw new RuntimeException(
//                        "Unable to create application " + mInitialApplication.getClass().getName()
//                                + ": " + e.toString(), e);
            }
        }
        VLog.d(TAG, "bindApplicationNoCheck OK " + packageName + " proc: " + processName);
        mBindAppWatchDog.feed(WATCH_BIND_APP);
        VActivityManager.get().appDoneExecuting();
        VLog.d(TAG, "initProcess for vuid: " + vuid + " vpid: " + vpid + " actual pid: " + Process.myPid() + " actual uid: " + Process.myUid());
        VirtualCore.get().getComponentDelegate().afterApplicationCreate(mInitialApplication);
    }

//    private void fixWeChatRecovery(Application app) {
//        try {
//            Field field = app.getClassLoader().loadClass("com.tencent.recovery.Recovery").getField("context");
//            field.setAccessible(true);
//            if (field.get(null) != null) {
//                return;
//            }
//            field.set(null, app.getBaseContext());
//        } catch (Throwable e) {
//            e.printStackTrace();
//        }
//    }

    private void setupUncaughtHandler() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        ThreadGroup newRoot = new RootThreadGroup(root);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            final List<ThreadGroup> groups = mirror.java.lang.ThreadGroup.groups.get(root);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (groups) {
                List<ThreadGroup> newGroups = new ArrayList<>(groups);
                newGroups.remove(newRoot);
                mirror.java.lang.ThreadGroup.groups.set(newRoot, newGroups);
                groups.clear();
                groups.add(newRoot);
                mirror.java.lang.ThreadGroup.groups.set(root, groups);
                for (ThreadGroup group : newGroups) {
                    mirror.java.lang.ThreadGroup.parent.set(group, newRoot);
                }
            }
        } else {
            final ThreadGroup[] groups = ThreadGroupN.groups.get(root);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (groups) {
                ThreadGroup[] newGroups = groups.clone();
                ThreadGroupN.groups.set(newRoot, newGroups);
                ThreadGroupN.groups.set(root, new ThreadGroup[]{newRoot});
                for (Object group : newGroups) {
                    ThreadGroupN.parent.set(group, newRoot);
                }
                ThreadGroupN.ngroups.set(root, 1);
            }
        }
    }

    @SuppressLint("SdCardPath")
    private void startIOUniformer() {
        long time = System.currentTimeMillis();
        VLog.d(TAG, "E startIOUniformer");
        ApplicationInfo info = mBoundApplication.appInfo;
        int userId = VUserHandle.myUserId();
        String wifiMacAddressFile = deviceInfo.getWifiFile(userId).getPath();
        NativeEngine.redirectDirectory("/sys/class/net/wlan0/address", wifiMacAddressFile);
        NativeEngine.redirectDirectory("/sys/class/net/eth0/address", wifiMacAddressFile);
        NativeEngine.redirectDirectory("/sys/class/net/wifi/address", wifiMacAddressFile);
        NativeEngine.redirectDirectory("/tmp/",
                new File(VEnvironment.getDataUserPackageDirectory(userId,info.packageName), "cache").getAbsolutePath());


        NativeEngine.redirectDirectory("/data/data/" + info.packageName, info.dataDir);
        NativeEngine.redirectDirectory("/data/user/0/" + info.packageName, info.dataDir);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NativeEngine.redirectDirectory("/data/user_de/0/" + info.packageName, info.dataDir);
        }

//        if () {
//            NativeEngine.whiteList("/data/data/do.multiple.cloner/virtual/data/user/0/com.google.android.gms/app_chimera/current_config.fb");
//        }
//        String libPath = VEnvironment.getAppLibDirectory(info.packageName).getAbsolutePath();
//        String userLibPath = new File(VEnvironment.getUserSystemDirectory(userId), info.packageName + "/lib").getAbsolutePath();
//        NativeEngine.redirectDirectory(userLibPath, libPath);

//        NativeEngine.whitelist("/data/data/" + v6 + "/lib/");
//        NativeEngine.whitelist("/data/user/0/" + v6 + "/lib/");
        NativeEngine.whiteList("/data/data/" + info.packageName + "/lib/");
        NativeEngine.whiteList("/data/user/0/" + info.packageName + "/lib/");
        NativeEngine.whiteList("/data/app/" + info.packageName + "/lib/");
//        NativeEngine.redirectFile("/data/user/0/do.multiple.cloner/virtual/data/app/com.supercell.clashofclans/lib/libfmod.so",
//                "/data/app/com.supercell.clashofclans-1/lib/arm/libfmod.so");
//        NativeEngine.redirectFile("/data/user/0/do.multiple.cloner/virtual/data/app/com.supercell.clashofclans/lib/libg.so",
//                "/data/app/com.supercell.clashofclans-1/lib/arm/libg.so");
//        try {
//            File path = new File(libPath + "/arm");
//            if (!path.exists()) {
//                path.mkdirs();
//            }
//            FileUtils.copyFile(new File("/data/app/com.supercell.clashofclans-1/lib/arm/libfmod.so"),
//                    new File(libPath + "/arm/libfmod.so"));
//            FileUtils.copyFile(new File("/data/app/com.supercell.clashofclans-1/lib/arm/libg.so"),
//                    new File(libPath + "/arm/libg.so"));
//        }catch (Exception ex) {
//            ex.printStackTrace();
//        }

        NativeEngine.readOnly(VEnvironment.getDataAppDirectory().getPath());
        VirtualStorageManager vsManager = VirtualStorageManager.get();
        String vsPath = vsManager.getVirtualStorage(info.packageName, userId);
        boolean enable = vsManager.isVirtualStorageEnable(info.packageName, userId);
        if (enable && vsPath != null) {
            File vsDirectory = new File(vsPath);
            if (vsDirectory.exists() || vsDirectory.mkdirs()) {
                HashSet<String> mountPoints = getMountPoints();
                for (String mountPoint : mountPoints) {
                    NativeEngine.redirectDirectory(mountPoint, vsPath);
                }
            }
        }
        VLog.d(TAG, "startIOUniformer before hook");
        NativeEngine.hook();
        long cost = (System.currentTimeMillis() - time);
        VLog.d(TAG, "X startIOUniformer " + cost + " ms");
    }

    @SuppressLint("SdCardPath")
    private HashSet<String> getMountPoints() {
        HashSet<String> mountPoints = new HashSet<>(3);
        mountPoints.add("/mnt/sdcard/");
        mountPoints.add("/sdcard/");
        String[] points = StorageManagerCompat.getAllPoints(VirtualCore.get().getContext());
        if (points != null) {
            Collections.addAll(mountPoints, points);
        }
        return mountPoints;

    }

    private Context createPackageContext(String packageName) {
        try {
            Context hostContext = VirtualCore.get().getContext();
            return hostContext.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            VirtualRuntime.crash(new RemoteException());
        }
        throw new RuntimeException();
    }

    private Object fixBoundApp(AppBindData data) {
        Object thread = VirtualCore.mainThread();
        Object boundApp = mirror.android.app.ActivityThread.mBoundApplication.get(thread);
        mirror.android.app.ActivityThread.AppBindData.appInfo.set(boundApp, data.appInfo);
        mirror.android.app.ActivityThread.AppBindData.processName.set(boundApp, data.processName);
        mirror.android.app.ActivityThread.AppBindData.instrumentationName.set(
                boundApp,
                new ComponentName(data.appInfo.packageName, Instrumentation.class.getName())
        );
        ActivityThread.AppBindData.providers.set(boundApp, data.providers);
        return boundApp;
    }

    private void installContentProviders(Context app, List<ProviderInfo> providers) {
        long origId = Binder.clearCallingIdentity();
        Object mainThread = VirtualCore.mainThread();
        try {
            for (ProviderInfo cpi : providers) {
                try {
                    ActivityThread.installProvider(mainThread, app, cpi, null);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public IBinder acquireProviderClient(ProviderInfo info) {
        VLog.d(TAG, "enter acquireProviderClient " + info.authority);
        if (mTempLock != null) {
            mTempLock.block();
        }
        VLog.d(TAG, "no lock acquireProviderClient " + info.authority + " process " + info.processName);
        if (!isBound()) {
            VClientImpl.get().bindApplication(info.packageName, info.processName);
        }
        IInterface provider = null;
        String[] authorities = info.authority.split(";");
        String authority = authorities.length == 0 ? info.authority : authorities[0];
        ContentResolver resolver = VirtualCore.get().getContext().getContentResolver();
        ContentProviderClient client = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                client = resolver.acquireUnstableContentProviderClient(authority);
            } else {
                client = resolver.acquireContentProviderClient(authority);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if (client != null) {
            provider = mirror.android.content.ContentProviderClient.mContentProvider.get(client);
            client.release();
        } else {
            VLog.logbug(TAG, "acquireProviderClient client is null");
        }
        VLog.d(TAG, "acquireProviderClient return " + provider);
        return provider != null ? provider.asBinder() : null;
    }

    private void fixInstalledProviders() {
        clearSettingProvider();
        Map clientMap = ActivityThread.mProviderMap.get(VirtualCore.mainThread());
        for (Object clientRecord : clientMap.values()) {
            if (BuildCompat.isOreo()) {
                IInterface provider = ActivityThread.ProviderClientRecordJB.mProvider.get(clientRecord);
                Object holder = ActivityThread.ProviderClientRecordJB.mHolder.get(clientRecord);
                if (holder == null) {
                    continue;
                }
                ProviderInfo info = ContentProviderHolderOreo.info.get(holder);
                if (!info.authority.startsWith(VASettings.STUB_CP_AUTHORITY)) {
                    provider = ProviderHook.createProxy(true, info.authority, provider);
                    ActivityThread.ProviderClientRecordJB.mProvider.set(clientRecord, provider);
                    ContentProviderHolderOreo.provider.set(holder, provider);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                IInterface provider = ActivityThread.ProviderClientRecordJB.mProvider.get(clientRecord);
                Object holder = ActivityThread.ProviderClientRecordJB.mHolder.get(clientRecord);
                if (holder == null) {
                    continue;
                }
                ProviderInfo info = IActivityManager.ContentProviderHolder.info.get(holder);
                if (!info.authority.startsWith(VASettings.STUB_CP_AUTHORITY)) {
                    provider = ProviderHook.createProxy(true, info.authority, provider);
                    ActivityThread.ProviderClientRecordJB.mProvider.set(clientRecord, provider);
                    IActivityManager.ContentProviderHolder.provider.set(holder, provider);
                }
            } else {
                String authority = ActivityThread.ProviderClientRecord.mName.get(clientRecord);
                IInterface provider = ActivityThread.ProviderClientRecord.mProvider.get(clientRecord);
                if (provider != null && !authority.startsWith(VASettings.STUB_CP_AUTHORITY)) {
                    provider = ProviderHook.createProxy(true, authority, provider);
                    ActivityThread.ProviderClientRecord.mProvider.set(clientRecord, provider);
                }
            }
        }

    }

    private void clearSettingProvider() {
        Object cache;
        cache = Settings.System.sNameValueCache.get();
        if (cache != null) {
            clearContentProvider(cache);
        }
        cache = Settings.Secure.sNameValueCache.get();
        if (cache != null) {
            clearContentProvider(cache);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && Settings.Global.TYPE != null) {
            cache = Settings.Global.sNameValueCache.get();
            if (cache != null) {
                clearContentProvider(cache);
            }
        }
    }

    private static void clearContentProvider(Object cache) {
        if (BuildCompat.isOreo()) {
            Object holder = Settings.NameValueCacheOreo.mProviderHolder.get(cache);
            if (holder != null) {
                Settings.ContentProviderHolder.mContentProvider.set(holder, null);
            }
        } else {
            Settings.NameValueCache.mContentProvider.set(cache, null);
        }
    }

    @Override
    public void finishActivity(IBinder token) {
        VActivityManager.get().finishActivity(token);
    }

    @Override
    public void scheduleNewIntent(String creator, IBinder token, Intent intent) {
        NewIntentData data = new NewIntentData();
        data.creator = creator;
        data.token = token;
        data.intent = intent;
        sendMessage(NEW_INTENT, data);
    }

    @Override
    public void scheduleReceiver(String processName, ComponentName component, Intent intent, PendingResultData resultData) {
        ReceiverData receiverData = new ReceiverData();
        receiverData.resultData = resultData;
        receiverData.intent = intent;
        receiverData.component = component;
        receiverData.processName = processName;
        sendMessage(RECEIVER, receiverData);
    }

    private void handleReceiver(ReceiverData data) {
        BroadcastReceiver.PendingResult result = data.resultData.build();
        VLog.d(TAG, "handleReceiver " + data.intent + " on " + data.component);
        try {
            if (!isBound()) {
                bindApplication(data.component.getPackageName(), data.processName);
            }
            Context context = mInitialApplication.getBaseContext();
            Context receiverContext = ContextImpl.getReceiverRestrictedContext.call(context);
            String className = data.component.getClassName();
            BroadcastReceiver receiver = (BroadcastReceiver) context.getClassLoader().loadClass(className).newInstance();
			if (! (receiver instanceof AppWidgetProvider)) {
            mirror.android.content.BroadcastReceiver.setPendingResult.call(receiver, result);
            data.intent.setExtrasClassLoader(context.getClassLoader());
            if (data.intent.getComponent() == null) {
                data.intent.setComponent(data.component);
            }
            receiver.onReceive(receiverContext, data.intent);
			}
            if (mirror.android.content.BroadcastReceiver.getPendingResult.call(receiver) != null) {
                result.finish();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Exception exc = new RuntimeException(
                    "Unable to start receiver " + data.component
                            + ": " + e.toString(), e);
            VLog.logbug(TAG, "Unable to start receiver " + data.component
                    + ": " + e.toString());
            VLog.logbug(TAG, VLog.getStackTraceString(exc));
        } finally {
            VActivityManager.get().broadcastFinish(data.resultData);
        }
    }

    @Override
    public IBinder createProxyService(ComponentName component, IBinder binder) {
        return ProxyServiceFactory.getProxyService(getCurrentApplication(), component, binder);
    }

    @Override
    public String getDebugInfo() {
        return "process : " + VirtualRuntime.getProcessName() + "\n" +
                "initialPkg : " + VirtualRuntime.getInitialPackageName() + "\n" +
                "vuid : " + vuid;
    }

    private static class RootThreadGroup extends ThreadGroup {

        RootThreadGroup(ThreadGroup parent) {
            super(parent, "VA-Root");
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            CrashHandler handler = VClientImpl.gClient.crashHandler;
            if (handler != null) {
                handler.handleUncaughtException(t, e);
            } else {
                VLog.e("uncaught", e);
                System.exit(0);
            }
        }
    }

    private final class NewIntentData {
        String creator;
        IBinder token;
        Intent intent;
    }

    private final class AppBindData {
        String processName;
        ApplicationInfo appInfo;
        List<ProviderInfo> providers;
        Object info;
    }

    private final class ReceiverData {
        PendingResultData resultData;
        Intent intent;
        ComponentName component;
        String processName;
    }

    private class H extends Handler {

        private H() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NEW_INTENT: {
                    handleNewIntent((NewIntentData) msg.obj);
                }
                break;
                case RECEIVER: {
                    handleReceiver((ReceiverData) msg.obj);
                }
                break;
            }
        }
    }
}
