package com.polestar.multiaccount.component.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BounceInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.NativeExpressAdView;
import com.polestar.ad.AdConfig;
import com.polestar.ad.AdConstants;
import com.polestar.ad.AdLog;
import com.polestar.ad.AdUtils;
import com.polestar.ad.adapters.FuseAdLoader;
import com.polestar.ad.adapters.IAd;
import com.polestar.ad.adapters.IAdLoadListener;
import com.polestar.imageloader.widget.BasicLazyLoadImageView;
import com.polestar.multiaccount.R;
import com.polestar.multiaccount.component.BaseFragment;
import com.polestar.multiaccount.component.activity.AppListActivity;
import com.polestar.multiaccount.component.activity.HomeActivity;
import com.polestar.multiaccount.component.activity.LockSettingsActivity;
import com.polestar.multiaccount.constant.AppConstants;
import com.polestar.multiaccount.db.DbManager;
import com.polestar.multiaccount.model.AppModel;
import com.polestar.multiaccount.utils.BitmapUtils;
import com.polestar.multiaccount.utils.AnimatorHelper;
import com.polestar.multiaccount.utils.AppManager;
import com.polestar.multiaccount.utils.CloneHelper;
import com.polestar.multiaccount.utils.CommonUtils;
import com.polestar.multiaccount.utils.ExplosionField;
import com.polestar.multiaccount.utils.MLogs;
import com.polestar.multiaccount.utils.MTAManager;
import com.polestar.multiaccount.utils.PreferencesUtils;
import com.polestar.multiaccount.utils.RemoteConfig;
import com.polestar.multiaccount.widgets.LeftRightDialog;
import com.polestar.multiaccount.widgets.CustomFloatView;
import com.polestar.multiaccount.widgets.GridAppCell;
import com.polestar.multiaccount.widgets.HeaderGridView;
import com.polestar.multiaccount.widgets.TutorialGuides;
import com.polestar.multiaccount.widgets.TutorialGuidesUtils;
import com.polestar.multiaccount.widgets.dragdrop.DragController;
import com.polestar.multiaccount.widgets.dragdrop.DragImageView;
import com.polestar.multiaccount.widgets.dragdrop.DragLayer;
import com.polestar.multiaccount.widgets.dragdrop.DragSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * Created by yxx on 2016/7/19.
 */
public class HomeFragment extends BaseFragment {
    private View contentView;
    private HeaderGridView pkgGridView;
    private PackageGridAdapter pkgGridAdapter;
    private List<AppModel> appInfos;
    private CustomFloatView floatView;
    private ExplosionField mExplosionField;
    private DragController mDragController;
    private DragLayer mDragLayer;
    private LinearLayout nativeAdContainer;


    private FuseAdLoader mNativeAdLoader;
    private NativeExpressAdView mAdmobExpressView;
    private ImageView mLockSettingIcon;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.fragment_home, null);
        mLockSettingIcon = (ImageView) mActivity.findViewById(R.id.lock_setting);
        mExplosionField = ExplosionField.attachToWindow(mActivity);
        initView();
        initData();
        boolean showHeaderAd = RemoteConfig.getBoolean(KEY_HOME_SHOW_HEADER_AD);
        MLogs.d(KEY_HOME_SHOW_HEADER_AD + showHeaderAd);
        headerNativeAdConfigs = RemoteConfig.getAdConfigList(SLOT_HOME_HEADER_NATIVE);
        if (showHeaderAd && headerNativeAdConfigs.size() > 0 ) {
            initAdmobBannerView();
            loadHeadNativeAd();
        }
        mDragController = new DragController(mActivity);
        mDragController.setDragListener(mDragListener);
        mDragController.setWindowToken(contentView.getWindowToken());
        mDragLayer.setDragController(mDragController);
        return contentView;
    }
    DragController.DragListener mDragListener = new DragController.DragListener() {
        @Override
        public void onDragStart(DragSource source, Object info, int dragAction) {
            MLogs.d("onDragStart");
            floatView.animToExtands();
            mDragController.addDropTarget(floatView);
        }

        @Override
        public void onDragEnd(DragSource source, Object info, int action) {
            MLogs.d("onDragEnd + " + floatView.getSelectedState());
            switch (floatView.getSelectedState()) {
                case CustomFloatView.SELECT_BTN_LEFT:
                    MTAManager.addShortCut(mActivity, ((AppModel) info).getPackageName());
                    CommonUtils.createShortCut(mActivity,((AppModel) info));
                    floatView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mActivity, R.string.toast_shortcut_added, Toast.LENGTH_SHORT).show();
                            //CustomToastUtils.showImageWithMsg(mActivity, mActivity.getResources().getString(R.string.toast_shortcut_added), R.mipmap.icon_add_success);
                        }
                    },CustomFloatView.ANIM_DURATION / 2);
                    break;
                case CustomFloatView.SELECT_BTN_RIGHT:
                    pkgGridAdapter.notifyDataSetChanged();
                    floatView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showDeleteDialog((AppModel) info);
                        }
                    },CustomFloatView.ANIM_DURATION / 2);
                    break;
                default:
                    break;
            }
            floatView.animToIdel();
            mDragController.removeDropTarget(floatView);
        }
    };

    private class PackageGridAdapter extends BaseAdapter {

        public int getPosition(AppModel appModel) {
            int ret = 0;
            if (appModel != null ) {
                for (AppModel m : appInfos) {
                    if (m.getPackageName().equals(appModel.getPackageName())) {
                        return ret;
                    }
                    ret++;
                }
            }
            return  -1;
        }
        @Override
        public int getCount() {
            int size = appInfos == null ? 0 : appInfos.size();
            if ( size < 15 ) {
                size = 15;
            } else {
                size = size + 3 - (size % 3);
            }
            return size;
        }

        @Override
        public Object getItem(int position) {
            if ( appInfos == null) {
                return  null;
            }
            if (position < appInfos.size()) {
                return  appInfos.get(position);
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            view = new GridAppCell(mActivity);

            ImageView appIcon = (ImageView) view.findViewById(R.id.app_icon);
            TextView appName = (TextView) view.findViewById(R.id.app_name);

            AppModel appModel = (AppModel) getItem(i);
            if (appModel != null) {
                if (appModel.getCustomIcon() == null) {
                    appModel.setCustomIcon(BitmapUtils.createCustomIcon(mActivity, appModel.initDrawable(mActivity)));
                }

                if (appModel.getCustomIcon() != null) {
                    appIcon.setImageBitmap(appModel.getCustomIcon());
                }
                appName.setText(appModel.getName());
            }

            return view;
        }
    }

//    private LayoutAnimationController getGridLayoutAnimController() {
//        int duration = 100;
//        AnimationSet set = new AnimationSet(true);
//        Animation animation = new AlphaAnimation(0.0f, 1.0f);
//        set.addAnimation(animation);
//
//        animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f,
//                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
//        animation.setDuration(duration);
//        set.addAnimation(animation);
//
//        LayoutAnimationController controller = new LayoutAnimationController(set, 0.5f);
//        controller.setOrder(LayoutAnimationController.ORDER_NORMAL);
//        return controller;
//    }
    private void initView() {
        //nativeAdContainer = (LinearLayout) mActivity.findViewById(R.id.native_ad_container);
        nativeAdContainer = new LinearLayout(mActivity);
        nativeAdContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        nativeAdContainer.setOrientation(LinearLayout.VERTICAL);

        mDragLayer = (DragLayer)contentView.findViewById(R.id.drag_layer);
        pkgGridView = (HeaderGridView) contentView.findViewById(R.id.grid_app);
        pkgGridView.addHeaderView(nativeAdContainer);
        pkgGridAdapter = new PackageGridAdapter();
//        pkgGridView.setLayoutAnimation(getGridLayoutAnimController());
        pkgGridView.setAdapter(pkgGridAdapter);


        floatView = (CustomFloatView) contentView.findViewById(R.id.addApp_btn);
        floatView.startBreath();

        floatView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startAppListActivity();
                PreferencesUtils.setCloneGuideShowed(mActivity);
                MTAManager.homeAdd(mActivity);
            }
        });

        pkgGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                //final int i = pkgGridAdapter.getNatureIndex(position);
                int i =pos - pkgGridView.getGridItemStartOffset();
                MLogs.d("onItemClick " + i);
                if(i >= 0 && i < appInfos.size()){
                    if(floatView.isIdle()){
                        startAppLaunchActivity(appInfos.get(i).getPackageName());
                    }else{
                        floatView.restore();
                        floatView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                startAppLaunchActivity(appInfos.get(i).getPackageName());
                            }
                        },100);
                    }
                }
            }
        });
        pkgGridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int pos, long l) {
                int i = pos - pkgGridView.getGridItemStartOffset();
                MLogs.d("onItemLongClick " + i);
                if (pkgGridAdapter.getItem(i) != null) {
                    DragImageView iv = (DragImageView) view.findViewById(R.id.app_icon);
                    mDragController.startDrag(iv, iv, pkgGridAdapter.getItem(i), DragController.DRAG_ACTION_COPY);
                    return true;
                } else {
                    return  false;
                }

            }
        });
    }

    private void initAdmobBannerView() {
        mAdmobExpressView = new NativeExpressAdView(mActivity);
        String adunit  = null;
        if (headerNativeAdConfigs != null) {
            for (AdConfig adConfig: headerNativeAdConfigs) {
                if (adConfig.source != null && adConfig.source.equals(AdConstants.NativeAdType.AD_SOURCE_ADMOB_NAVTIVE_BANNER)){
                    adunit = adConfig.key;
                    break;
                }
            }
        }
        if (TextUtils.isEmpty(adunit)) {
            return;
        }
        mAdmobExpressView.setAdSize(new AdSize(360, 132));
//        mAdmobExpressView.setAdUnitId("ca-app-pub-5490912237269284/2431070657");
        mAdmobExpressView.setAdUnitId(adunit);
        mAdmobExpressView.setVisibility(View.GONE);
        mAdmobExpressView.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                super.onAdClosed();
                AdLog.d("onAdClosed");
            }

            @Override
            public void onAdFailedToLoad(int i) {
                super.onAdFailedToLoad(i);
                AdLog.d("onAdFailedToLoad " + i);
                mAdmobExpressView.setVisibility(View.GONE);
            }

            @Override
            public void onAdLeftApplication() {
                super.onAdLeftApplication();
            }

            @Override
            public void onAdOpened() {
                super.onAdOpened();
            }

            @Override
            public void onAdLoaded() {
                super.onAdLoaded();
                nativeAdContainer.removeAllViews();
                mAdmobExpressView.setVisibility(View.VISIBLE);
                nativeAdContainer.addView(mAdmobExpressView);
                pkgGridAdapter.notifyDataSetChanged();
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(mAdmobExpressView, "scaleX", 0.7f, 1.0f, 1.0f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(mAdmobExpressView, "scaleY", 0.7f, 1.0f, 1.0f);
                AnimatorSet animSet = new AnimatorSet();
                animSet.play(scaleX).with(scaleY);
                animSet.setInterpolator(new BounceInterpolator());
                animSet.setDuration(800).start();
                animSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {

                    }
                });
                dismissLongClickGuide();
                AdLog.d("onAdLoaded ");
            }
        });
    }
    private void inflateFbNativeAdView(IAd ad) {
        View adView = LayoutInflater.from(mActivity).inflate(R.layout.front_page_native_ad, null);
//        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        adView.setLayoutParams(params);
        if (ad != null && adView != null) {
            BasicLazyLoadImageView coverView = (BasicLazyLoadImageView) adView.findViewById(R.id.ad_cover_image);
            coverView.setDefaultResource(0);
            coverView.requestDisplayURL(ad.getCoverImageUrl());
            BasicLazyLoadImageView iconView = (BasicLazyLoadImageView) adView.findViewById(R.id.ad_icon_image);
            iconView.setDefaultResource(0);
            iconView.requestDisplayURL(ad.getIconImageUrl());
            TextView titleView = (TextView) adView.findViewById(R.id.ad_title);
            titleView.setText(ad.getTitle());
            TextView subtitleView = (TextView) adView.findViewById(R.id.ad_subtitle_text);
            subtitleView.setText(ad.getBody());
            TextView ctaView = (TextView) adView.findViewById(R.id.ad_cta_text);
            ctaView.setText(ad.getCallToActionText());

            nativeAdContainer.removeAllViews();
            nativeAdContainer.addView(adView);
            pkgGridAdapter.notifyDataSetChanged();
            ad.registerViewForInteraction(nativeAdContainer);
            if (ad.getPrivacyIconUrl() != null) {
                BasicLazyLoadImageView choiceIconImage = (BasicLazyLoadImageView) adView.findViewById(R.id.ad_choices_image);
                choiceIconImage.setDefaultResource(0);
                choiceIconImage.requestDisplayURL(ad.getPrivacyIconUrl());
                ad.registerPrivacyIconView(choiceIconImage);
            }
        }
    }

    private TutorialGuides.Builder mTutorialBuilder;

    private int getLockRecommandAppIdx() {
        if (appInfos == null || appInfos.size() == 0) {
            return  -1;
        }
        for (int i = 0; i < appInfos.size(); i++) {
            AppModel model = appInfos.get(i);
            if (CommonUtils.isSocialApp(model.getPackageName())) {
                return i;
            }
        }
        return  -1;
    }

    private void showApplockGuide(int index) {
        try {
            if (mLockSettingIcon == null) {
                return;
            }
            String text = getString(R.string.applock_guide_text);
            mTutorialBuilder = new TutorialGuides.Builder(mActivity);
            mTutorialBuilder.anchorView(mLockSettingIcon);
            mTutorialBuilder.defaultMaxWidth(true);
            mTutorialBuilder.onShowListener(new TutorialGuides.OnShowListener() {
                @Override
                public void onShow(TutorialGuides tooltip) {
                    PreferencesUtils.setApplockGuideShowed();
                }
            });
            mTutorialBuilder.text(text)
                    .gravity(Gravity.BOTTOM)
                    .build()
                    .show();
        }catch (Exception e){
            MLogs.e("error to show guides");
            MLogs.e(e);
        }
    }

    private void showCloneAppGuide(){
        //TutorialGuidesUtils.removeOnGlobalLayoutListener(pkgGridView,this);
        try {
            String text = getString(R.string.start_tips);
            mTutorialBuilder = new TutorialGuides.Builder(mActivity);

            RectF rectF = TutorialGuidesUtils.getRectFInWindow(floatView);
            mTutorialBuilder.anchorView(floatView);
            mTutorialBuilder.defaultMaxWidth(true);
            mTutorialBuilder.onShowListener(new TutorialGuides.OnShowListener() {
                @Override
                public void onShow(TutorialGuides tooltip) {
                    PreferencesUtils.setCloneGuideShowed(mActivity);
                }
            });
            mTutorialBuilder.text(text)
                    .gravity(Gravity.TOP)
                    .build()
                    .show();
        }catch (Exception e){
            MLogs.e("error to show guides");
            MLogs.e(e);
        }
    }

    private TutorialGuides longClickGuide = null;
    private void showLongClickItemGuide(){
        try {
            String text = getString(R.string.long_press_tips);
            mTutorialBuilder = new TutorialGuides.Builder(mActivity);
            mTutorialBuilder.anchorView(pkgGridView.getChildAt(0+pkgGridView.getGridItemStartOffset()));
            mTutorialBuilder.defaultMaxWidth(true);
            mTutorialBuilder.onShowListener(new TutorialGuides.OnShowListener() {
                @Override
                public void onShow(TutorialGuides tooltip) {
                    PreferencesUtils.setLongClickGuideShowed(mActivity);
                }
            });
            longClickGuide = mTutorialBuilder.text(text)
                    .gravity(Gravity.BOTTOM)
                    .build();
            longClickGuide.show();
        }catch (Exception e){
            MLogs.e("error to showLongClickItemGuide");
            MLogs.e(e);
        }
    }

    private void dismissLongClickGuide() {
        if (longClickGuide !=null) longClickGuide.dismiss();
    }

    private void loadAdmobNativeExpress(){

        if (AdConstants.DEBUG) {
            String android_id = AdUtils.getAndroidID(mActivity);
            String deviceId = AdUtils.MD5(android_id).toUpperCase();
            AdRequest request = new AdRequest.Builder().addTestDevice(deviceId).build();
            boolean isTestDevice = request.isTestDevice(mActivity);
            AdLog.d( "is Admob Test Device ? "+deviceId+" "+isTestDevice);
            AdLog.d( "Admob unit id "+ mAdmobExpressView.getAdUnitId());
            mAdmobExpressView.loadAd(request );
        } else {
            mAdmobExpressView.loadAd(new AdRequest.Builder().build());
        }
    }

    private void loadHeadNativeAd() {
        if (mNativeAdLoader == null) {
            mNativeAdLoader = new FuseAdLoader(mActivity);
            mNativeAdLoader.addAdConfigList(headerNativeAdConfigs);
            ///mNativeAdLoader.addAdSource(AdConstants.NativeAdType.AD_SOURCE_FACEBOOK, "1700354860278115_1702636763383258", -1);
        }
        if ( mNativeAdLoader.hasValidAdSource()) {
            mNativeAdLoader.loadAd(1, new IAdLoadListener() {
                @Override
                public void onAdLoaded(IAd ad) {
                    if (ad.getAdType().equals(AdConstants.NativeAdType.AD_SOURCE_FACEBOOK)
                            || ad.getAdType().equals(AdConstants.NativeAdType.AD_SOURCE_VK)) {
                        inflateFbNativeAdView(ad);
                    }
                    dismissLongClickGuide();
                }

                @Override
                public void onAdListLoaded(List<IAd> ads) {

                }

                @Override
                public void onError(String error) {
                    loadAdmobNativeExpress();
                }
            });
        } else {
            loadAdmobNativeExpress();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        pkgGridView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mActivity != null) {
                    if (appInfos.size() > 0 && !PreferencesUtils.hasShownLongClickGuide(mActivity)) {
                        showLongClickItemGuide();
                    } else if (appInfos.size() > 0 && !PreferencesUtils.isApplockGuideShowed()
                            && !PreferencesUtils.isLockerEnabled(mActivity)) {
                        int index = getLockRecommandAppIdx();
                        if ( index != -1) {
                            showApplockGuide(index);
                        }
                    }
                }
            }
        }, 1500);
    }

    private static final String KEY_HOME_SHOW_HEADER_AD = "home_show_header_ad";
    private static final String SLOT_HOME_HEADER_NATIVE = "slot_home_header_native";
    private List<AdConfig> headerNativeAdConfigs ;

    private void initData(){
        CloneHelper.getInstance(mActivity).loadClonedApps(mActivity, new CloneHelper.OnClonedAppChangListener() {
            @Override
            public void onInstalled(List<AppModel> clonedApp) {
                if(pkgGridAdapter != null){
                    pkgGridAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onUnstalled(List<AppModel> clonedApp) {
                if(pkgGridAdapter != null){
                    pkgGridAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onLoaded(List<AppModel> clonedApp) {
                appInfos = clonedApp;
                if(pkgGridAdapter != null){
                    pkgGridAdapter.notifyDataSetChanged();
                }
                if (!PreferencesUtils.hasShownCloneGuide(mActivity) && (clonedApp == null || clonedApp.size() == 0)) {
                    pkgGridView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showCloneAppGuide();
                        }
                    }, 1000);

                }
            }
        });
    }

    private void showDeleteDialog(AppModel appModel){
        LeftRightDialog.show(mActivity,mActivity.getResources().getString(R.string.delete_dialog_title),
                mActivity.getResources().getString(R.string.delete_dialog_content),
                mActivity.getResources().getString(R.string.delete_dialog_left),mActivity.getResources().getString(R.string.delete_dialog_right),
                new DialogInterface.OnClickListener(){

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i){
                            case LeftRightDialog.LEFT_BUTTON:
                                dialogInterface.dismiss();
                                break;
                            case LeftRightDialog.RIGHT_BUTTON:
                                dialogInterface.dismiss();
                                deleteAppWithAnim(appModel);
                                break;
                        }
                    }
                });
    }

    private void deleteAppWithAnim(AppModel appModel){
        if (appModel == null) return;
        appModel.setUnEnable(true);
        pkgGridAdapter.notifyDataSetChanged();
        View view = pkgGridView.getChildAt(pkgGridAdapter.getPosition(appModel) + pkgGridView.getGridItemStartOffset());
        if(view != null) {
            mExplosionField.explode(view, new ExplosionField.OnExplodeFinishListener() {
                @Override
                public void onExplodeFinish(View v) {
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            deleteApp(appModel);
                        }
                    }, 1000);
                }
            });
        } else {
            deleteApp(appModel);
        }
    }

    private void deleteApp(AppModel appModel){
        appInfos.remove(appModel);
        MTAManager.deleteClonedApp(mActivity, appModel.getPackageName());
//        updateModelIndex(itemPosition,appInfos.size() - 1);
        AppManager.uninstallApp(appModel.getPackageName());
        CommonUtils.removeShortCut(mActivity,appModel);
        DbManager.deleteAppModel(mActivity, appModel);
        DbManager.notifyChanged();
        pkgGridAdapter.notifyDataSetChanged();
        //adapter.deleteComplete();
        showFullScreenAd();
    }

    private void startAppLaunchActivity(String packageName){
        if(mActivity instanceof  HomeActivity){
            ((HomeActivity)mActivity).startAppLaunchActivity(packageName);
        }
    }

    private void showFullScreenAd(){
    }

    public void showFromBottom(){
        AnimatorHelper.verticalShowFromBottom(contentView);
        floatView.postDelayed(new Runnable() {
            @Override
            public void run() {
                floatView.startRote();
            }
        },AnimatorHelper.DURATION_NORMAL);
    }

    public void hideToBottom(){
        AnimatorHelper.hideToBottom(contentView);
    }

    private synchronized void update(ArrayList<AppModel> updateList){
        new Thread(new Runnable() {
            @Override
            public void run() {
                DbManager.updateAppModelList(mActivity,updateList);
            }
        }).start();
    }

    /**
     * 启动已安装的App列表页面
     *
     */
    public void startAppListActivity() {
        if(mActivity instanceof  HomeActivity){
            ((HomeActivity)mActivity).startAppListActivity();
        }else {
            Intent i = new Intent(mActivity, AppListActivity.class);
            mActivity.startActivityForResult(i, AppConstants.REQUEST_SELECT_APP);
            mActivity.overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
        }
    }
}
