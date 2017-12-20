package com.polestar.domultiple.db;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import com.polestar.domultiple.AppConstants;
import com.polestar.domultiple.utils.CommonUtils;

import org.greenrobot.greendao.annotation.*;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT. Enable "keep" sections if you want to edit.

/**
 * Entity mapped to table "CLONE_MODEL".
 */
@Entity
public class CloneModel {

    @Id
    private Long id;
    private String packageName;
    private String path;
    private String name;
    private String description;
    private int index;
    private long clonedTime;
    private Boolean notificationEnable;
    private Integer lockerState;
    private Integer launched;
    @Transient
    private PackageInfo packageInfo;
    @Transient
    private Drawable icon;
    @Transient
    private Bitmap customIcon;

    @Generated(hash = 1465161905)
    public CloneModel() {
    }

    public CloneModel(String pkg, Context c) {
        this.packageName = pkg;
        this.path = getApkPath(c);
        this.name = (String)getLabel(c);

    }
    public CloneModel(Long id) {
        this.id = id;
    }

    @Generated(hash = 1295562137)
    public CloneModel(Long id, String packageName, String path, String name, String description, int index, long clonedTime, Boolean notificationEnable, Integer lockerState, Integer launched) {
        this.id = id;
        this.packageName = packageName;
        this.path = path;
        this.name = name;
        this.description = description;
        this.index = index;
        this.clonedTime = clonedTime;
        this.notificationEnable = notificationEnable;
        this.lockerState = lockerState;
        this.launched = launched;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public long getClonedTime() {
        return clonedTime;
    }

    public void setClonedTime(long clonedTime) {
        this.clonedTime = clonedTime;
    }

    public Boolean getNotificationEnable() {
        return notificationEnable == null? false : notificationEnable;
    }

    public void setNotificationEnable(Boolean notificationEnable) {
        this.notificationEnable = notificationEnable;
    }

    public Integer getLockerState() {
        return lockerState == null? AppConstants.AppLockState.DISABLED: lockerState;
    }

    public void setLockerState(Integer lockerState) {
        this.lockerState = lockerState;
    }

    public Integer getLaunched() {
        return launched == null? 0 : launched;
    }

    public void setLaunched(Integer launched) {
        this.launched = launched;
    }

    public Drawable getIconDrawable(Context context) {
        return CommonUtils.getAppIcon(packageName);
    }

    public Bitmap getCustomIcon() {
        return customIcon;
    }

    public void setCustomIcon(Bitmap customIcon) {
        this.customIcon = customIcon;
    }

    public String getApkPath(Context context){
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return ai.sourceDir;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CharSequence getLabel(Context context){
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    // the original index
    int getOriginalIndex() {
        return index & 0xffff;
    }

    // the package user id
    public int getPkgUserId() {
        return (index >> 16) & 0xff;
    }

    public void formatIndex(int index, int userId) {
        setIndex((index & 0xffff) | ((userId & 0xff) << 16));
    }
}
