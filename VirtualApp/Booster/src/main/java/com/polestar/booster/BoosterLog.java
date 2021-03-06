package com.polestar.booster;

import android.os.Bundle;
import android.util.Log;

/**
 * Created by guojia on 2017/12/8.
 */

public class BoosterLog {

    public static BoosterSdk.IEventReporter sReporter;
    public static void log(String msg) {
        if(BuildConfig.DEBUG) Log.d("Booster", msg);
    }

    public static void reportEvent (String s, Bundle bundle) {
        if (sReporter != null) {
            sReporter.reportEvent(s, bundle);
            if (BuildConfig.DEBUG) {
                log(s+ " data: " + bundle.toString());
            }
        }
    }

    public static void reportWake (String s) {
        if (sReporter != null) {
            sReporter.reportWake(s);
            if (BuildConfig.DEBUG) {
                log(s+ " wake: " + s);
            }
        }
    }

    public static void boostEnter(String from) {
        Bundle bundle = new Bundle();
        bundle.putString("from", from);
        reportEvent("boost_from", bundle);
    }
}
