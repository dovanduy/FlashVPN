package mochat.multiple.parallel.whatsclone.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.text.TextUtils;

import mochat.multiple.parallel.whatsclone.MApp;
import mochat.multiple.parallel.whatsclone.constant.AppConstants;
import mochat.multiple.parallel.whatsclone.utils.AppManager;
import mochat.multiple.parallel.whatsclone.utils.BitmapUtils;

/**
 * Created by guojia on 2017/7/29.
 */

public class CustomizeAppData {
    public int hue;
    public int sat;
    public int light;
    public boolean badge;
    public String label;
    public String pkg;
    public boolean customized;
    public int userId;
    public String spName;

    private CustomizeAppData() {
    }

    public static boolean hasLaunched(String pkg, int userId) {
        String spName = AppManager.getCompatibleName(AppConstants.CUSTOMIZE_PREF + pkg, userId);
        SharedPreferences settings = MApp.getApp().getSharedPreferences(spName, Context.MODE_PRIVATE);

        return settings.getBoolean("launched", false);
    }

    public static void setLaunched(String pkg, int userId){
        String spName = AppManager.getCompatibleName(AppConstants.CUSTOMIZE_PREF + pkg, userId);
        SharedPreferences settings = MApp.getApp().getSharedPreferences(spName, Context.MODE_PRIVATE);
        settings.edit().putBoolean("launched", true).commit();

    }

    public static CustomizeAppData loadFromPref(String pkg, int userId) {
        CustomizeAppData data = new CustomizeAppData();
        data.spName = AppManager.getCompatibleName(AppConstants.CUSTOMIZE_PREF + pkg, userId);
        SharedPreferences settings = MApp.getApp().getSharedPreferences(data.spName, Context.MODE_PRIVATE);
        data.badge = settings.getBoolean("badge", true);
        data.hue = settings.getInt("hue", AppConstants.COLOR_MID_VALUE);
        data.sat = settings.getInt("sat", AppConstants.COLOR_MID_VALUE);
        data.light = settings.getInt("light", AppConstants.COLOR_MID_VALUE);
        data.label = settings.getString("label", null);
        data.pkg = pkg;
        data.userId = userId;
        data.customized = data.label != null;
        if (TextUtils.isEmpty(data.label)) {
//            data.label = String.format(ResourcesUtil.getString(R.string.clone_label_tag),
//                    AppManager.getModelName(pkg, userId));
            data.label = AppManager.getModelName(pkg, userId);
        }
        return data;
    }

    public Bitmap getCustomIcon() {
        return  BitmapUtils.getCustomIcon(MApp.getApp(), pkg, userId);
    }
    public void saveToPref() {
        SharedPreferences settings = MApp.getApp().getSharedPreferences(spName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("badge", badge);
        editor.putInt("hue", hue);
        editor.putInt("light", light);
        editor.putString("label", label);
        editor.commit();
    }

    public static void removePerf(String pkg, int userId) {
        String sp = AppManager.getCompatibleName(AppConstants.CUSTOMIZE_PREF + pkg, userId);
        SharedPreferences settings = MApp.getApp().getSharedPreferences(sp, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.clear().commit();
        BitmapUtils.removeCustomIcon(MApp.getApp(), pkg, userId);
    }
}
