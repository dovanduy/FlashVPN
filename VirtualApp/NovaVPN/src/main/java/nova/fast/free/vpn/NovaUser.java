package nova.fast.free.vpn;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;


import nova.fast.free.vpn.utils.MLogs;
import nova.fast.free.vpn.utils.PreferenceUtils;
import nova.fast.free.vpn.utils.RemoteConfig;

public class NovaUser{

    private Context mContext;
    private static NovaUser sInstance;
    private Handler mHandler;
    private long freePremiumTime;

    private final static int MSG_SAVE = 0;

    private NovaUser(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_SAVE:
                        PreferenceUtils.putLong(mContext, "premium_seconds", freePremiumTime);
                        break;
                }
            }
        };
        freePremiumTime = PreferenceUtils.getLong(mContext, "premium_seconds", 3600);
    }

    synchronized public static NovaUser  getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new NovaUser(context);
        }
        return sInstance;
    }

    public long getFreePremiumSeconds() {
        MLogs.d("free premium: " + freePremiumTime);
        return freePremiumTime;
    }

    public void doRewardFreePremium() {
        long add =  RemoteConfig.getLong("gift_reward_sec");
        freePremiumTime += add;
        MLogs.d("add reward: " + add);
        scheduleSave();
    }

    public void costFreePremiumSec(long sec) {
        freePremiumTime -= sec;
        if (freePremiumTime < 0) freePremiumTime = 0;
        scheduleSave();
    }

    private void scheduleSave() {
        if (! mHandler.hasMessages(MSG_SAVE)) {
            mHandler.sendEmptyMessage(MSG_SAVE);
        }
    }

    public boolean isVIP() {
        return PreferenceUtils.getBoolean(NovaApp.getApp(), "is_vip", false);
    }

    public void setVIP(boolean enable) {
        PreferenceUtils.putBoolean(NovaApp.getApp(), "is_vip", enable);
    }
}