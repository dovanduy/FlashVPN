package com.polestar.clone.server;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.polestar.clone.GmsSupport;
import com.polestar.clone.client.core.VirtualCore;
import com.polestar.clone.client.env.SpecialComponentList;
import com.polestar.clone.client.ipc.ServiceManagerNative;
import com.polestar.clone.client.stub.DaemonService;
import com.polestar.clone.helper.compat.BundleCompat;
import com.polestar.clone.helper.compat.NotificationChannelCompat;
import com.polestar.clone.helper.utils.VLog;
import com.polestar.clone.remote.InstalledAppInfo;
import com.polestar.clone.server.accounts.VAccountManagerService;
import com.polestar.clone.server.am.BroadcastSystem;
import com.polestar.clone.server.am.VActivityManagerService;
import com.polestar.clone.server.device.VDeviceManagerService;
import com.polestar.clone.server.interfaces.IServiceFetcher;
import com.polestar.clone.server.job.VJobSchedulerService;
import com.polestar.clone.server.location.VirtualLocationService;
import com.polestar.clone.server.net.VNetworkScoreManagerService;
import com.polestar.clone.server.notification.VNotificationManagerService;
import com.polestar.clone.server.pm.VAppManagerService;
import com.polestar.clone.server.pm.VPackageManagerService;
import com.polestar.clone.server.pm.VUserManagerService;
import com.polestar.clone.server.vs.VirtualStorageService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Lody
 */
public final class BinderProvider extends ContentProvider {

    private final ServiceFetcher mServiceFetcher = new ServiceFetcher();

    private final static String TAG = BinderProvider.class.getSimpleName();
    @Override
    public boolean onCreate() {
        VLog.d(TAG, "onCreate");
        Context context = getContext();
        if (!VirtualCore.get().isStartup()) {
            return true;
        }
        NotificationChannelCompat.checkOrCreateChannel(context, NotificationChannelCompat.DEFAULT_CHANNEL_ID, "messages from clone");
        VPackageManagerService.systemReady();
        addService(ServiceManagerNative.PACKAGE, VPackageManagerService.get());
        VActivityManagerService.systemReady(context);
        addService(ServiceManagerNative.ACTIVITY, VActivityManagerService.get());
        addService(ServiceManagerNative.USER, VUserManagerService.get());
        VAppManagerService.systemReady();
        addService(ServiceManagerNative.APP, VAppManagerService.get());
        BroadcastSystem.attach(VActivityManagerService.get(), VAppManagerService.get());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addService(ServiceManagerNative.JOB, VJobSchedulerService.get());
        }
        VNotificationManagerService.systemReady(context);
        addService(ServiceManagerNative.NOTIFICATION, VNotificationManagerService.get());
        VAppManagerService.get().scanApps();
        VAccountManagerService.systemReady();
        addService(ServiceManagerNative.ACCOUNT, VAccountManagerService.get());
        addService(ServiceManagerNative.VS, VirtualStorageService.get());
        addService(ServiceManagerNative.DEVICE, VDeviceManagerService.get());
        addService(ServiceManagerNative.VIRTUAL_LOC, VirtualLocationService.get());
        addService(ServiceManagerNative.NETWORK_SCORE, VNetworkScoreManagerService.get());
        VAppManagerService.get().sendBootCompleted();
		// avoid some customized os will getPackageInfo during start up service
//		at android.app.ApplicationPackageManager.getPackageInfo (ApplicationPackageManager.java:137)
//		at com.android.internal.agui.LimitThirdApp.isThirdApp (LimitThirdApp.java:26)
        cleanStaleUsers();
		DaemonService.startup(context);
        VLog.d(TAG, "Service initialized!");
        return true;
    }

    public void cleanStaleUsers(){
        int[] users = VUserManagerService.get().getUserIds();
        ArrayList<Integer> toRemove = new ArrayList<>();
        if (users != null) {
            for (int i: users) {
                if (i != 0 ) {
                    List<InstalledAppInfo> list = VAppManagerService.get().getInstalledAppsAsUser(i, 0);
                    if (list == null || list.size() == 0) {
                        toRemove.add(i);
                    } else {
                        boolean needRemove = true;
                        for (InstalledAppInfo info: list) {
                            if (!GmsSupport.isGmsFamilyPackage(info.packageName)
                                    && !SpecialComponentList.isPreInstallPackage(info.packageName)) {
                                needRemove = false;
                                break;
                            }
                        }
                        if (needRemove) {
                            toRemove.add(i);
                        }
                    }
                }
            }
        }
        for(int i: toRemove) {
            VLog.d(TAG, "Remove user: " + i);
            VUserManagerService.get().removeUser(i);
        }
    }

    private void addService(String name, IBinder service) {
        ServiceCache.addService(name, service);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if ("@".equals(method)) {
            Bundle bundle = new Bundle();
            BundleCompat.putBinder(bundle, "_VA_|_binder_", mServiceFetcher);
            return bundle;
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private class ServiceFetcher extends IServiceFetcher.Stub {
        @Override
        public IBinder getService(String name) throws RemoteException {
            if (name != null) {
                return ServiceCache.getService(name);
            }
            return null;
        }

        @Override
        public void addService(String name, IBinder service) throws RemoteException {
            if (name != null && service != null) {
                ServiceCache.addService(name, service);
            }
        }

        @Override
        public void removeService(String name) throws RemoteException {
            if (name != null) {
                ServiceCache.removeService(name);
            }
        }
    }
}
