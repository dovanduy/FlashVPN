package com.polestar.superclone.component;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.lody.virtual.client.core.VirtualCore;
import com.polestar.ad.adapters.FuseAdLoader;
import com.polestar.ad.adapters.IAdAdapter;
import com.polestar.ad.adapters.IAdLoadListener;
import com.polestar.superclone.BuildConfig;
import com.polestar.superclone.MApp;
import com.polestar.superclone.component.activity.AppLockActivity;
import com.polestar.superclone.component.activity.WrapCoverAdActivity;
import com.polestar.superclone.constant.AppConstants;
import com.polestar.superclone.model.AppModel;
import com.polestar.superclone.utils.AppManager;
import com.polestar.superclone.utils.CloneHelper;
import com.polestar.superclone.utils.CommonUtils;
import com.polestar.superclone.utils.MLogs;
import com.polestar.superclone.utils.PreferencesUtils;
import com.polestar.superclone.utils.RemoteConfig;
import com.polestar.superclone.IAppMonitor;

import java.util.HashSet;
import java.util.List;

/**
 * Created by guojia on 2018/5/27.
 */

public class AppMonitorService extends Service {
    //This service in charge of showing interstitial ads and locker
    private final static String TAG = "AppMonitor";

    public static final String SLOT_APP_START_INTERSTITIAL = "slot_app_start";
    private static final String CONFIG_APP_START_AD_FREQ = "slot_app_start_freq_hour";
    private static final String CONFIG_APP_START_AD_RAMP = "slot_app_start_ramp_hour";
    private static final String CONFIG_APP_START_AD_FILTER = "slot_app_start_filter";
    private static final String CONFIG_APP_START_AD_STYLE = "slot_app_start_style"; //native,interstitial,all
    public final static String CONFIG_APPLOCK_PRELOAD_INTERVAL = "applock_preload_interval";
    private static HashSet<String> filterPkgs ;

    private static String lastUnlockKey;

    private final static int MSG_DELAY_LOCK_APP = 0;
    private final static int MSG_PRELOAD_AD = 1;

    private AppMonitor appMonitor;

    private Handler mainHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case MSG_DELAY_LOCK_APP:
                    //QuickSwitchNotification.getInstance(VirtualCore.get().getContext()).updateLruPackages((String)msg.obj);
                    lastUnlockKey = null;
                    MLogs.d("relock lastUnlockKey " + lastUnlockKey);
                    break;
                case MSG_PRELOAD_AD:
                    if (!PreferencesUtils.isAdFree() && PreferencesUtils.isLockerEnabled(AppMonitorService.this)) {
                        FuseAdLoader.get(AppLockActivity.CONFIG_SLOT_APP_LOCK,AppMonitorService.this).loadAd(1, null);
                        long interval = RemoteConfig.getLong(CONFIG_APPLOCK_PRELOAD_INTERVAL);
                        MLogs.d("Applocker schedule next ad at " + interval);
                        if (interval >= 15*60*000) {
                            mainHandler.sendEmptyMessageDelayed(MSG_PRELOAD_AD, interval);
                        }
                    }
                    break;
            }
        }
    };

    private void preloadAd() {
        mainHandler.removeMessages(MSG_PRELOAD_AD);
        mainHandler.sendEmptyMessage(MSG_PRELOAD_AD);
    }
    public static void unlocked(String pkg, int userId) {
        lastUnlockKey = AppManager.getMapKey(pkg,userId);
        MLogs.d("unlocked lastUnlockKey " + lastUnlockKey);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CloneHelper.getInstance(this);
        appMonitor = new AppMonitor();
        preloadAd();
    }

    public static boolean needLoadCoverAd(boolean preload, String pkg) {
        if (PreferencesUtils.isAdFree()) {
            return false;
        }
        String style = RemoteConfig.getString(CONFIG_APP_START_AD_STYLE);
        if (!("interstitial".equals(style) || "all".equals(style))) {
            return false;
        }
        long interval = RemoteConfig.getLong(CONFIG_APP_START_AD_FREQ)*60*60*1000;
        long ramp = RemoteConfig.getLong(CONFIG_APP_START_AD_RAMP)*60*60*1000;
        long last = getLastShowTime();
        if (last == 0 && TextUtils.isEmpty(pkg)) {
            return false;
        }
        long actualLast = last == 0? CommonUtils.getInstallTime(MApp.getApp(), MApp.getApp().getPackageName()): last;
        long delta =
                preload? System.currentTimeMillis() - actualLast + 15*60*1000: System.currentTimeMillis()-actualLast;
        boolean need =  last == 0? delta > ramp: delta > interval;
        if (filterPkgs == null) {
            filterPkgs = new HashSet<>();
            String[] arr = RemoteConfig.getString(CONFIG_APP_START_AD_FILTER).split(":");
            if(arr !=null) {
                for (String s : arr) {
                    filterPkgs.add(s);
                }
            }
        }
        MLogs.d(TAG, "needLoad start app ad: " + need);
        return (need && (!filterPkgs.contains(pkg)||pkg == null)) || BuildConfig.DEBUG;
    }

    private static long getLastShowTime() {
        return PreferencesUtils.getLong(MApp.getApp(), "app_start_last", 0);
    }

    private static void updateShowTime() {
        PreferencesUtils.putLong(MApp.getApp(), "app_start_last", System.currentTimeMillis());
    }

    public static void preloadCoverAd() {
        if(!PreferencesUtils.isAdFree()) {
            FuseAdLoader.get(SLOT_APP_START_INTERSTITIAL, MApp.getApp());
        }
    }

    public static void onCoverAdClosed(String pkg, int userId) {
        String key = AppManager.getMapKey(pkg, userId);
        lastUnlockKey = key;

    }

    private void loadAd(String pkg, int userId) {
        FuseAdLoader adLoader = FuseAdLoader.get(SLOT_APP_START_INTERSTITIAL, VirtualCore.get().getContext());
        if(adLoader.hasValidAdSource()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    adLoader.loadAd(1, new IAdLoadListener() {
                        @Override
                        public void onAdLoaded(IAdAdapter ad) {
                            //ad.show();
                            updateShowTime();
                            WrapCoverAdActivity.start(AppMonitorService.this, SLOT_APP_START_INTERSTITIAL, pkg, userId );
                        }

                        @Override
                        public void onAdListLoaded(List<IAdAdapter> ads) {

                        }

                        @Override
                        public void onError(String error) {
                            MLogs.d(SLOT_APP_START_INTERSTITIAL + " load error:" + error);
                        }

                        @Override
                        public void onAdClicked(IAdAdapter ad) {

                        }

                        @Override
                        public void onAdClosed(IAdAdapter ad) {
                            MLogs.d("onAdClosed");
                            //getActivity().finishAndRemoveTask();
                            //AppManager.launchApp(pkg,userId);
                        }
                    });
                }
            });
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return appMonitor;
    }

    private class AppMonitor extends IAppMonitor.Stub {
        public void onAppSwitchForeground(String pkg, int userId){
            MLogs.d(TAG, "OnAppForeground: " + pkg + " user: " + userId);
            AppModel model = CloneHelper.getInstance(AppMonitorService.this).getAppModel(pkg, userId);
            boolean locked = false;
            String key = AppManager.getMapKey(pkg, userId);
            MLogs.d(TAG, "key unlockKey: " + key + " vs " + lastUnlockKey);
            if(model.getLockerState()!= AppConstants.AppLockState.DISABLED
                    && PreferencesUtils.isLockerEnabled(AppMonitorService.this)) {
                if (!key.equals(lastUnlockKey)){
                    AppLockActivity.start(AppMonitorService.this, pkg, userId);
                    locked = true;
                } else {
                    mainHandler.removeMessages(MSG_DELAY_LOCK_APP);
                }
            }
            if (!locked && needLoadCoverAd(false, pkg)) {
                loadAd(pkg, userId);
            }

        }

        public void onAppSwitchBackground(String pkg, int userId){
            MLogs.d(TAG, "OnAppBackground: " + pkg + " user: " + userId);
            String key = AppManager.getMapKey(pkg, userId);
            long relockDelay = PreferencesUtils.getLockInterval();
            MLogs.d(TAG, "onActivityPause " + pkg + " delay relock: " + relockDelay);
            mainHandler.sendMessageDelayed(mainHandler.obtainMessage(MSG_DELAY_LOCK_APP, key),
                    relockDelay);
        }

        public void onAppLock(String pkg, int userId){
            MLogs.d(TAG, "onAppLock: " + pkg + " user: " + userId);
            AppLockActivity.start(AppMonitorService.this, pkg, userId);
        }
    }
}