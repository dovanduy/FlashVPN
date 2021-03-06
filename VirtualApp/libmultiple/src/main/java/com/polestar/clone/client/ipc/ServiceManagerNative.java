package com.polestar.clone.client.ipc;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.polestar.clone.client.core.VirtualCore;
import com.polestar.clone.helper.compat.BundleCompat;
import com.polestar.clone.helper.utils.VLog;
import com.polestar.clone.server.ServiceCache;
import com.polestar.clone.server.interfaces.IServiceFetcher;

/**
 * @author Lody
 */
public class ServiceManagerNative {

    public static final String PACKAGE = "package";
    public static final String ACTIVITY = "activity";
    public static final String USER = "user";
    public static final String APP = "app";
    public static final String ACCOUNT = "account";
    public static final String JOB = "job";
    public static final String NOTIFICATION = "notification";
    public static final String VS = "vs";
    public static final String DEVICE = "device";
    public static final String VIRTUAL_LOC = "virtual-loc";
    public static final String NETWORK_SCORE = "network_score";

    public static final String SERVICE_DEF_AUTH = "virtual.service.BinderProvider";
    private static final String TAG = ServiceManagerNative.class.getSimpleName();
    public static String SERVICE_CP_AUTH = "virtual.service.BinderProvider";

    private static IServiceFetcher sFetcher;

    private static IServiceFetcher getServiceFetcher() {
        if (sFetcher == null || !sFetcher.asBinder().isBinderAlive()) {
            synchronized (ServiceManagerNative.class) {
                Context context = VirtualCore.get().getContext();
                Bundle response = new ProviderCall.Builder(context, SERVICE_CP_AUTH).methodName("@").call();
                if (response != null) {
                    IBinder binder = BundleCompat.getBinder(response, "_VA_|_binder_");
                    linkBinderDied(binder);
                    sFetcher = IServiceFetcher.Stub.asInterface(binder);
                }
            }
        }
        if (sFetcher == null) {
            VLog.logbug(TAG, "Cannot fetch service");
        }
        return sFetcher;
    }

    public static void ensureServerStarted() {
        new ProviderCall.Builder(VirtualCore.get().getContext(), SERVICE_CP_AUTH).methodName("ensure_created").call();
    }

    public static void clearServerFetcher() {
        sFetcher = null;
    }

    private static void linkBinderDied(final IBinder binder) {
        if (binder == null) {
            VLog.logbug(TAG, "linkBinderDied null");
            return;
        }
        IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                binder.unlinkToDeath(this, 0);
				clearServerFetcher();
				if (VirtualCore.get().isMainProcess()) {
					VirtualCore.get().clearRemote();
				}
				VLog.logbug(TAG, "Ops, the server has crashed.");
				for (int i = 5; i > 0; i--) {
					try{
						Thread.sleep(400);
					}catch (Throwable e){

					}
					VLog.logbug(TAG, "Try hot recover " + i);
					if (getService(ServiceManagerNative.APP) != null) {
						return;
					}
				}
				VLog.logbug(TAG, "Hot recover failed. Exit.");
            }
        };
        try {
            binder.linkToDeath(deathRecipient, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static IBinder getService(String name) {
        if (VirtualCore.get().isServerProcess()) {
            return ServiceCache.getService(name);
        }
        IServiceFetcher fetcher = getServiceFetcher();
        if (fetcher != null) {
            try {
                return fetcher.getService(name);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        VLog.e(TAG, "GetService(%s) return null.", name);
        return null;
    }

    public static void addService(String name, IBinder service) {
        IServiceFetcher fetcher = getServiceFetcher();
        if (fetcher != null) {
            try {
                fetcher.addService(name, service);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    public static void removeService(String name) {
        IServiceFetcher fetcher = getServiceFetcher();
        if (fetcher != null) {
            try {
                fetcher.removeService(name);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

}
