package com.polestar.superclone.component.activity;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.android.billingclient.api.BillingClient;
import com.polestar.ad.adapters.FuseAdLoader;
import com.polestar.ad.adapters.IAdAdapter;
import com.polestar.ad.adapters.IAdLoadListener;
import com.polestar.billing.BillingConstants;
import com.polestar.billing.BillingProvider;
import com.polestar.superclone.BuildConfig;
import com.polestar.superclone.R;
import com.polestar.superclone.component.BaseActivity;
import com.polestar.superclone.component.adapter.NavigationAdapter;
import com.polestar.superclone.component.fragment.HomeFragment;
import com.polestar.superclone.constant.AppConstants;
import com.polestar.superclone.model.AppModel;
import com.polestar.superclone.notification.FastSwitch;
import com.polestar.superclone.reward.AppUser;
import com.polestar.superclone.reward.InviteActivity;
import com.polestar.superclone.reward.RewardCenterFragment;
import com.polestar.superclone.reward.RewardErrorCode;
import com.polestar.superclone.reward.ShareActions;
import com.polestar.superclone.reward.StoreFragment;
import com.polestar.superclone.reward.TaskExecutor;
import com.polestar.superclone.reward.VIPActivity;
import com.polestar.superclone.utils.AppListUtils;
import com.polestar.superclone.utils.CloneHelper;
import com.polestar.superclone.utils.CommonUtils;
import com.polestar.superclone.utils.MLogs;
import com.polestar.superclone.utils.EventReporter;
import com.polestar.superclone.utils.PreferencesUtils;
import com.polestar.superclone.widgets.LeftRightDialog;
import com.polestar.superclone.widgets.RateDialog;
import com.polestar.superclone.widgets.UpDownDialog;
import com.polestar.superclone.utils.RemoteConfig;
import com.polestar.task.ADErrorCode;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HomeActivity extends BaseActivity {

    private HomeFragment mHomeFragment;
    private RewardCenterFragment mRewardCenterFragment;
    private StoreFragment mStoreFragment;
    private DrawerLayout drawer;
    private ListView navigationList;
    private ImageView giftIconView;
    private FuseAdLoader adLoader;
    private boolean isAutoInterstitialShown;
    private boolean isInterstitialAdLoaded;
    private boolean autoShowInterstitial;

    private static final String SLOT_HOME_GIFT_INTERSTITIAL = "slot_home_gift_interstitial";
    //private static final String SLOT_HOME_GIFT_INTERSTITIAL = "slot_test";
    private static final String CONFIG_AUTO_SHOW_INTERSTITIAL = "auto_home_interstitial_rate";
    private static final String CONFIG_AUTO_SHOW_INTERSTITIAL_INTERVAL = "auto_home_interstitial_interval";
    private static final String CONFIG_CLONE_RATE_PACKAGE = "clone_rate_package";
    private static final String CONFIG_CLONE_RATE_INTERVAL = "clone_rate_interval";
    private static final String CONFIG_AD_FREE_DIALOG_INTERVAL = "ad_free_dialog_interval";
    private static final String CONFIG_AD_FREE_DIALOG_INTERVAL_2 = "ad_free_dialog_interval_2";

    private static final String RATE_FROM_QUIT = "quit";
    private static final String RATE_AFTER_CLONE = "clone";
    private static final String RATE_FROM_MENU = "menu";

    private static final int REQUEST_UNLOCK_SETTINGS = 100;
    public static final int REQUEST_APPLY_PERMISSION = 101;

    private String cloningPackage;
    private RelativeLayout iconAdLayout;
    private RelativeLayout giftIconLayout;
    private IAdAdapter interstitialAd;
    private Handler mainHandler;
    private boolean showGift;

    private View bottomNavBar;


    private static final String EXTRA_NEED_UPDATE = "extra_need_update";
    public static void enter(Activity activity, boolean needUpdate) {
        MLogs.d("Enter home: update: " + needUpdate);
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.putExtra(EXTRA_NEED_UPDATE, needUpdate);
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, -1);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler();
        showGift = PreferencesUtils.hasCloned();
        EventReporter.homeShow(this);
        setContentView(R.layout.activity_home);
        initView();
        AppListUtils.getInstance(this); // asyncInit AppListUtils
        int random = new Random().nextInt(100);
        long interval = System.currentTimeMillis() - PreferencesUtils.getAutoInterstialTime();
        autoShowInterstitial = (!PreferencesUtils.isAdFree() && random < RemoteConfig.getLong(CONFIG_AUTO_SHOW_INTERSTITIAL)
                && (interval > RemoteConfig.getLong(CONFIG_AUTO_SHOW_INTERSTITIAL_INTERVAL))) || BuildConfig.DEBUG;
        MLogs.d( " autoInterstitial: " + autoShowInterstitial );

        String from = getIntent().getStringExtra(AppConstants.EXTRA_FROM);
        if (!TextUtils.isEmpty(from)) {
            EventReporter.reportWake(this, "home_from_" + from);
        }
        boolean needUpdate = getIntent().getBooleanExtra(EXTRA_NEED_UPDATE, false);
        if (needUpdate) {
            MLogs.d("need update");
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showUpdateDialog();
                }
            }, 1000);
        }
        else if(!PreferencesUtils.hasCloned() && RemoteConfig.getBoolean("go_clone_first_start")) {
            startAppListActivity();
        }
        if (AppUser.isRewardEnabled() && AppUser.getInstance().isRewardAvailable()) {
            AppUser.getInstance().updatePendingAdTask(this);
        }
    }

    private void showUpdateDialog() {
        EventReporter.generalClickEvent(this, "update_dialog");
        LeftRightDialog.show(this,this.getResources().getString(R.string.update_dialog_title),
                this.getResources().getString(R.string.update_dialog_content, ""+ RemoteConfig.getLong(AppConstants.CONF_LATEST_VERSION)),
                this.getResources().getString(R.string.update_dialog_left),this.getResources().getString(R.string.update_dialog_right),
                new DialogInterface.OnClickListener(){

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i){
                            case LeftRightDialog.LEFT_BUTTON:
                                dialogInterface.dismiss();
                                PreferencesUtils.ignoreVersion(RemoteConfig.getLong(AppConstants.CONF_LATEST_VERSION));
                                break;
                            case LeftRightDialog.RIGHT_BUTTON:
                                dialogInterface.dismiss();
                                String forceUpdateUrl = RemoteConfig.getString("force_update_to");
                                if (!TextUtils.isEmpty(forceUpdateUrl)) {
                                    CommonUtils.jumpToUrl(HomeActivity.this,forceUpdateUrl);
                                } else {
                                    CommonUtils.jumpToMarket(HomeActivity.this, getPackageName());
                                }
                                EventReporter.generalClickEvent(HomeActivity.this, "update_go");
                                break;
                        }
                    }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                PreferencesUtils.ignoreVersion(RemoteConfig.getLong(AppConstants.CONF_LATEST_VERSION));
            }
        });
    }

    private void initView() {
        bottomNavBar = findViewById(R.id.frag_navigate_bar);
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.setScrimColor(Color.TRANSPARENT);
        navigationList = (ListView) findViewById(R.id.navigation_list);
        giftIconView = (ImageView) findViewById(R.id.gift_icon);
        giftIconLayout = (RelativeLayout) findViewById(R.id.gift_icon_layout);
        giftIconView.setVisibility(View.GONE);
        giftIconLayout.setVisibility(View.GONE);

        iconAdLayout = (RelativeLayout)findViewById(R.id.icon_ad);

        navigationList.setAdapter(new NavigationAdapter(this));
//        int width = DisplayUtils.getScreenWidth(this);
//        int listWidth = DisplayUtils.px2dip(this, width*2/3);
//        MLogs.d("width set to " + listWidth);
//        navigationList.setLayoutParams(new LinearLayout.LayoutParams(listWidth, ViewGroup.LayoutParams.MATCH_PARENT));
        navigationList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                onNavigationItemSelected(i);
                drawer.closeDrawer(GravityCompat.START);
//                int drawerLockMode = drawer.getDrawerLockMode(GravityCompat.START);
//                if (drawer.isDrawerVisible(GravityCompat.START)
//                        && (drawerLockMode != DrawerLayout.LOCK_MODE_LOCKED_OPEN)) {
//                    drawer.closeDrawer(GravityCompat.START);
//                }
            }
        });
        drawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                EventReporter.homeMenu(HomeActivity.this);
            }

            @Override
            public void onDrawerClosed(View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
        View view = findViewById(R.id.content_home);
        setImmerseLayout(view);
        mHomeFragment = new HomeFragment();
        if (!AppUser.isRewardEnabled()) {
            bottomNavBar.setVisibility(View.GONE);
        } else {
            EventReporter.setUserProperty(EventReporter.PROP_REWARDED, EventReporter.REWARD_OPEN);
            if (AppUser.getInstance().isRewardAvailable() && RemoteConfig.getBoolean("conf_auto_copy")) {
                new ShareActions(this, AppUser.getInstance().getInviteTask()).copy(true);
            }
            mRewardCenterFragment = new RewardCenterFragment();
        }
        doSwitchToClonesFragment();
    }


    private void showGiftIcon() {
        int giftRes;
        long interval = System.currentTimeMillis() - PreferencesUtils.getLastIconAdClickTime(HomeActivity.this);
        int random = new Random().nextInt(100);
        RelativeLayout layout = (RelativeLayout) giftIconLayout.findViewById(R.id.gift_new_tip);
        if (interval > 24 * 60 * 60 * 1000) {
            layout.setVisibility(View.VISIBLE);
        } else {
            layout.setVisibility(View.INVISIBLE);
        }
        if (PreferencesUtils.isVIP()) {
            giftRes = R.drawable.vip;
            layout.setVisibility(View.INVISIBLE);
            iconAdLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EventReporter.homeGiftClick(HomeActivity.this, "lucky_icon_vip_vip");
                    VIPActivity.start(HomeActivity.this, VIPActivity.FROM_HOME_GIFT_ICON);
                }
            });
        } else if (PreferencesUtils.isAdFree()){
            giftRes = R.drawable.vip;
            layout.setVisibility(View.INVISIBLE);
            iconAdLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    EventReporter.homeGiftClick(HomeActivity.this, "lucky_icon_vip_adfree");
                    VIPActivity.start(HomeActivity.this, VIPActivity.FROM_HOME_GIFT_ICON);
                }
            });
        } else {
            if (random < RemoteConfig.getLong("config_no_ad_icon_percent")) {
                giftRes = R.drawable.no_ad;
                iconAdLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PreferencesUtils.updateIconAdClickTime(HomeActivity.this);
                        EventReporter.homeGiftClick(HomeActivity.this, "lucky_icon_no_ad");
                        doSwitchToStoreFragment();
                        Toast.makeText(HomeActivity.this, R.string.earn_coin_remove_ads, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                giftRes = R.drawable.gift_ad;
                iconAdLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PreferencesUtils.updateIconAdClickTime(HomeActivity.this);
                        Intent intent = new Intent(HomeActivity.this, NativeInterstitialActivity.class);
                        startActivity(intent);
                        EventReporter.homeGiftClick(HomeActivity.this, "lucky_icon_ad");
                    }
                });
            }
        }
        giftIconView.setImageResource(giftRes);
        giftIconView.setVisibility(View.VISIBLE);
        giftIconLayout.setVisibility(View.VISIBLE);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(giftIconView, "scaleX", 0.7f, 1.3f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(giftIconView, "scaleY", 0.7f, 1.3f, 1.0f);
        AnimatorSet animSet = new AnimatorSet();
        animSet.play(scaleX).with(scaleY);
        animSet.setInterpolator(new BounceInterpolator());
        animSet.setDuration(800).start();
    }

    private void loadHomeInterstitial() {
        MLogs.e("start INTERSTITIAL loadHomeInterstitial");
        isInterstitialAdLoaded = false;
        interstitialAd = null;
        adLoader = FuseAdLoader.get(SLOT_HOME_GIFT_INTERSTITIAL, this);
        //adLoader.addAdSource(AdConstants.AdType.AD_SOURCE_ADMOB_INTERSTITIAL, "ca-app-pub-5490912237269284/5384537050", -1);
        if (adLoader.hasValidAdSource()) {
            adLoader.loadAd(this, 1, new IAdLoadListener() {
                @Override
                public void onRewarded(IAdAdapter ad) {

                }

                @Override
                public void onAdClicked(IAdAdapter ad) {

                }

                @Override
                public void onAdClosed(IAdAdapter ad) {

                }

                @Override
                public void onAdLoaded(IAdAdapter ad) {
                    isInterstitialAdLoaded = true;
                    interstitialAd = ad;
                   // giftGifView.setGifResource(R.drawable.front_page_gift_icon);

                }
                @Override
                public void onAdListLoaded(List<IAdAdapter> ads) {

                }
                @Override
                public void onError(String error) {
                        MLogs.e(error);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        try {
            super.onResume();
        }catch (Exception ex) {
            ex.printStackTrace();
            callUpActivity();

        }
//        callUpActivity();
        MLogs.d("isInterstitialAdLoaded " + isInterstitialAdLoaded + " isAutoInterstitialShown " + isAutoInterstitialShown);
        giftIconLayout.setVisibility(View.GONE);
        giftIconView.setVisibility(View.GONE);
        if (showGift) {
            giftIconView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showGiftIcon();
                }
            }, 800);
        }
        if (!PreferencesUtils.isAdFree()) {
            if (autoShowInterstitial && !isAutoInterstitialShown) {
                loadHomeInterstitial();
            }
        } else {
            hideAd();
        }
        if (AppUser.isRewardEnabled()) {
            AppUser.getInstance().preloadRewardVideoTask();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * 启动已安装的App列表页面
     */
    public void startAppListActivity() {
        Intent i = new Intent(this, AppListActivity.class);
        doAnimationExit();
        startActivityForResult(i, AppConstants.REQUEST_SELECT_APP);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void doAnimationExit() {
    }

    private boolean guideRateIfNeeded() {
        if (cloningPackage != null) {
            String pkg = cloningPackage;
            cloningPackage = null;
            MLogs.d("Cloning package: " + pkg);
            if (PreferencesUtils.isRated()) {
                return false;
            }
            String config = RemoteConfig.getString(CONFIG_CLONE_RATE_PACKAGE);
            if ("off".equalsIgnoreCase(config)) {
                MLogs.d("Clone rate off");
                return false;
            }
            if(PreferencesUtils.getLoveApp() == -1) {
                // not love, should wait for interval
                long interval = RemoteConfig.getLong(CONFIG_CLONE_RATE_INTERVAL) * 60 * 60 * 1000;
                if ((System.currentTimeMillis() - PreferencesUtils.getRateDialogTime(this)) < interval) {
                    MLogs.d("Not love, need wait longer");
                    return false;
                }
            }
            boolean match = "*".equals(config);
            if (!match) {
                String[] pkgList = config.split(":");
                if (pkgList != null && pkgList.length > 0) {
                    for (String s: pkgList) {
                        if(s.equalsIgnoreCase(pkg)) {
                            match = true;
                            break;
                        }
                    }
                }
            }
            if (match) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showRateDialog(RATE_AFTER_CLONE, pkg);
                    }
                }, 800);
                return true;
            } else {
                MLogs.d("No matching package for clone rate");
            }
        }
        return false;
    }

    private void doAnimationEnter() {
        mHomeFragment.showFromBottom();
        EventReporter.generalClickEvent(this, "home_animate_enter");
        if (!PreferencesUtils.hasShownLongClickGuide(this)) {
            MLogs.d("Not show long click guide.");
            return;
        }
        boolean needShowRate = guideRateIfNeeded();
        if (!needShowRate) {
            guideQuickSwitchIfNeeded();
        }
        boolean showAdFree = false;
        if (!showAdFree && !isAutoInterstitialShown && autoShowInterstitial) {
            if (interstitialAd != null && !PreferencesUtils.isAdFree()) {
                try {
                    interstitialAd.show();
                    isAutoInterstitialShown = true;
                    PreferencesUtils.updateAutoInterstialTime();
                    EventReporter.homeGiftClick(this, interstitialAd.getAdType() + "_auto_home");
                    interstitialAd = null;
                } catch (Exception ex) {
                    MLogs.logBug("Show interstitial fail: " + MLogs.getStackTraceString(ex));
                }
            }
        }
    }

    private boolean requestAdFree = false;

    private void hideAd() {
        if (mHomeFragment != null) {
            mHomeFragment.hideAd();
        }
    }

    public void onNavigationClick(View view) {
        int drawerLockMode = drawer.getDrawerLockMode(GravityCompat.START);
        if (drawer.isDrawerVisible(GravityCompat.START)
                && (drawerLockMode != DrawerLayout.LOCK_MODE_LOCKED_OPEN)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (drawerLockMode != DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
            drawer.openDrawer(GravityCompat.START);
        }
    }

    public void onLockSettingClick(View view) {
        if (PreferencesUtils.isLockerEnabled(HomeActivity.this) || isDebugMode()) {
            LockPasswordSettingActivity.start(HomeActivity.this, false, getString(R.string.lock_settings_title), REQUEST_UNLOCK_SETTINGS);
        } else {
            LockSettingsActivity.start(HomeActivity.this,"home");
        }
    }

    private final static String QUIT_RATE_RANDOM = "quit_rating_random";
    private final static String QUIT_RATE_INTERVAL = "quit_rating_interval";
    private final static String QUIT_RATE_CLONED_APP_GATE = "quit_rating_cloned_app_gate";
    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            boolean showRate = false;
            if (! PreferencesUtils.isRated()) {
                MLogs.d("Quit Rate config:" +  RemoteConfig.getLong(QUIT_RATE_INTERVAL)+" , "
                        + RemoteConfig.getLong(QUIT_RATE_RANDOM) + " , gate " +RemoteConfig.getLong(QUIT_RATE_CLONED_APP_GATE));
                long interval = RemoteConfig.getLong(QUIT_RATE_INTERVAL) * 60 * 60 * 1000;
                long lastTime = PreferencesUtils.getRateDialogTime(this);
                if (PreferencesUtils.getLoveApp() != -1) {
                    //Don't love app
                    int random = new Random().nextInt(100);
                    int clonedCnt = CloneHelper.getInstance(this).getClonedApps().size();
                    boolean isShowRateDialog = PreferencesUtils.getLoveApp() == 1 ||
                            ((random < RemoteConfig.getLong(QUIT_RATE_RANDOM)) && clonedCnt >= RemoteConfig.getLong(QUIT_RATE_CLONED_APP_GATE));
                    if (isShowRateDialog && (System.currentTimeMillis() - lastTime) > interval) {
                        showRate = true;
                        showRateDialog(RATE_FROM_QUIT, null);
                    }
                }
                if (!showRate) {
                    super.onBackPressed();
                }
            } else {
                super.onBackPressed();
            }
        }
    }

    static boolean existDebugFile = false;
    public static boolean isDebugMode(){
        if (existDebugFile) return  true;
        try {
            File file = new File(Environment.getExternalStorageDirectory().getPath() + File.separator + "polestarunlocktest");
            if (file.exists()) {
                existDebugFile = true;
                return true;
            }
        } catch (Exception e) {
            MLogs.e(e);
        }
        return false;
    }
    public boolean onNavigationItemSelected(int position) {
        switch (position) {
            case 0:
                if (PreferencesUtils.isLockerEnabled(this) || isDebugMode()) {
                    LockPasswordSettingActivity.start(this, false, getString(R.string.lock_settings_title), REQUEST_UNLOCK_SETTINGS);
                } else {
                    LockSettingsActivity.start(this,"home");
                }
                EventReporter.menuPrivacyLocker(this);
                break;
            case 1:
                EventReporter.menuNotification(this);
                Intent notification = new Intent(this, NotificationActivity.class);
                startActivity(notification);
                break;
            case 2:
                EventReporter.menuFAQ(this);
                Intent intentToFAQ = new Intent(this, FaqActivity.class);
                startActivity(intentToFAQ);
                break;
            case 3:
                EventReporter.menuFeedback(this);
                FeedbackActivity.start(this, 0);
                break;
            case 4:
                showRateDialog(RATE_FROM_MENU, null);
                break;
            case 5:
                EventReporter.menuShare(this);
                if (AppUser.isRewardEnabled() && AppUser.getInstance().isRewardAvailable()) {
                    InviteActivity.start(this);
                } else {
                    CommonUtils.shareWithFriends(this);
                }
                break;
            case 6:
                EventReporter.menuSettings(this);
                Intent intentToSettings = new Intent(this, SettingsActivity.class);
                startActivity(intentToSettings);
                break;
        }

//        new Handler().postDelayed(new Runnable() {
//            @Override
//            public void run() {
//                drawer.closeDrawer(GravityCompat.START);
//            }
//        },600);

        return true;
    }

    private boolean rateDialogShowed = false;
    private void showRateDialog(String from, String pkg){
        if (RATE_AFTER_CLONE.equals(from) || RATE_FROM_QUIT.equals(from)){
            if (rateDialogShowed ) {
                MLogs.d("Already showed dialog this time");
                return;
            }
            rateDialogShowed= true;
        }
        EventReporter.reportRate(this,"start", from);
        PreferencesUtils.updateRateDialogTime(this);
        String s = from+"_"+pkg;
        RateDialog rateDialog = new RateDialog(this, s);
        rateDialog.show().setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                EventReporter.reportRate(HomeActivity.this, s+"_cancel", s);
                PreferencesUtils.setLoveApp(false);
            }
        });
    }

    public void startAppLaunchActivity(String packageName, int userId) {
        //AppLaunchActivity.startAppLaunchActivity(this, packageName, drawerBlurHelper.createBitmap());
        AppStartActivity.startAppStartActivity(this, packageName, userId);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_UNLOCK_SETTINGS) {
            switch (resultCode) {
                case RESULT_OK:
                    LockSettingsActivity.start(this, "home");
                    break;
                case RESULT_CANCELED:
                    break;
            }
            return;
        }
        if (resultCode == RESULT_OK
                && data != null) {
            if (requestCode == AppConstants.REQUEST_SELECT_APP) {
                MLogs.e("install time2 = " + System.currentTimeMillis());
                String  pkg = data.getStringExtra(AppConstants.EXTRA_CLONED_APP_PACKAGENAME);
               // AppInstallActivity.startAppInstallActivity(this, model, drawerBlurHelper.createBitmap());
                if(pkg!= null) {
                    AppCloneActivity.startAppCloneActivity(this, pkg);
                    cloningPackage = pkg;
                    mHomeFragment.hideToBottom();
                }
            } else if (requestCode == AppConstants.REQUEST_INSTALL_APP) {

            }
        } else {
            doAnimationEnter();
        }
    }

    public void doSwitchToClonesFragment() {
        getSupportFragmentManager().beginTransaction().replace(R.id.frag_content, mHomeFragment).commitAllowingStateLoss();
    }

    public void doSwitchToRewardsFragment() {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frag_content, mRewardCenterFragment).addToBackStack(null)
                .commitAllowingStateLoss();
    }

    public void doSwitchToStoreFragment() {
        if (mStoreFragment == null) {
            mStoreFragment = new StoreFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frag_content, mStoreFragment).addToBackStack(null)
                .commitAllowingStateLoss();
    }

    public void onSwitchToClonesClick(View view) {
        doSwitchToClonesFragment();
    }

    public void onSwitchToRewardsClick(View view) {
        doSwitchToRewardsFragment();
    }

    @Override
    protected boolean useCustomTitleBar() {
        return false;
    }


    private static final String CONFIG_QUICK_SWITCH_INTERVAL = "guide_quick_switch_interval_s";
    private static final String CONFIG_QUICK_SWITCH_TIMES = "guide_quick_switch_times";
    private boolean guideQuickSwitchIfNeeded() {
        if (FastSwitch.isEnable()) {
            MLogs.d("Already enabled quick switch");
            return false;
        }
        if (CloneHelper.getInstance(this).getCloneNumber() < RemoteConfig.getLong("guide_quick_clone_threshold")) {
            return false;
        }
        long allowCnt = RemoteConfig.getLong(CONFIG_QUICK_SWITCH_TIMES);
        int times = PreferencesUtils.getGuideQuickSwitchTimes();
        if (times >= allowCnt) {
            MLogs.d("Guide quick switch hit cnt");
            return false;
        }
        if( System.currentTimeMillis() - PreferencesUtils.getLastGuideQuickSwitchTime()
                < times*1000*RemoteConfig.getLong(CONFIG_QUICK_SWITCH_INTERVAL)) {
            MLogs.d("not guide quick switch too frequent");
            return false;
        } else {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    PreferencesUtils.updateLastGuideQuickSwitchTime();
                    PreferencesUtils.incGuideQuickSwitchTimes();
                    showQuickSwitchDialog( );
                }
            }, 800);

        }
        return true;
    }

    private void showQuickSwitchDialog() {
        EventReporter.generalEvent("quick_switch_dialog_show");
        MLogs.d("showQuickSwitchDialog");
        UpDownDialog.show(this, this.getResources().getString(R.string.settings_fastswitch_title),
                this.getResources().getString(R.string.fast_switch_dialog_content),
                this.getResources().getString(R.string.no_thanks), this.getResources().getString(R.string.ok),
                R.drawable.dialog_tag_congratulations, R.layout.dialog_up_down,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case UpDownDialog.NEGATIVE_BUTTON:
                                dialogInterface.dismiss();
                                EventReporter.generalEvent("quick_switch_dialog_cancel");
                                break;
                            case UpDownDialog.POSITIVE_BUTTON:
                                dialogInterface.dismiss();
                                FastSwitch.enable();
                                EventReporter.generalEvent("quick_switch_dialog_go");
                                break;
                        }
                    }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {

            }
        });
    }


}
