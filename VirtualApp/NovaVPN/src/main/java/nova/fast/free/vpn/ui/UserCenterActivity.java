package nova.fast.free.vpn.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.BounceInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.polestar.ad.adapters.FuseAdLoader;
import com.polestar.ad.adapters.IAdAdapter;
import com.polestar.ad.adapters.IAdLoadListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import nova.fast.free.vpn.NovaUser;
import nova.fast.free.vpn.R;
import nova.fast.free.vpn.billing.BillingManager;
import nova.fast.free.vpn.billing.BillingProvider;
import nova.fast.free.vpn.utils.CommonUtils;
import nova.fast.free.vpn.utils.EventReporter;
import nova.fast.free.vpn.utils.MLogs;

public class UserCenterActivity extends BaseActivity implements SkuDetailsResponseListener {
    private static final String EXTRA_FROM = "from";
    public static final int FROM_HOME_TITLE_ICON = 0;
    public static final int FROM_HOME_GIFT_ICON = 0;
    public static final int FROM_HOME_MENU = 1;
    public static final int FROM_SETTING = 2;
    private IAdAdapter rewardAd;
    private View rewardLayout;
    private boolean isRewarded;
    private ListView cardListView;
    private List<SkuDetails> skuDetailsList;
    private SubscribeCardAdapter cardAdapter;
    private int[] arrCardColor = new int[]{
           R.color.card_first,R.color.card_second, R.color.card_third
    };

    private static final String SLOT_USER_CENTER_REWARD = "slot_user_center_reward";

    public static void start(Activity activity, int from) {
        Intent intent = new Intent();
        intent.setClassName(activity, UserCenterActivity.class.getName());
        intent.putExtra(EXTRA_FROM, from);
        activity.startActivity(intent);
    }
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);
        setTitle(getString(R.string.user_activity_title));
        rewardLayout = findViewById(R.id.reward_layout);
        rewardLayout.setVisibility(View.GONE);
        if (!NovaUser.getInstance(this).isVIP()) {
            loadRewardAd();
        }
        cardListView = findViewById(R.id.card_list);
        skuDetailsList = new ArrayList<>(0);
        cardAdapter = new SubscribeCardAdapter();
        cardListView.setAdapter(cardAdapter);
        cardListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(skuDetailsList.size() > i) {
                    SkuDetails item = skuDetailsList.get(i);
                    EventReporter.subscribeClick(UserCenterActivity.this, getIntent().getStringExtra(EXTRA_FROM), item.getSku());
                    BillingProvider.get().getBillingManager().initiatePurchaseFlow(UserCenterActivity.this,
                            item.getSku(), BillingClient.SkuType.SUBS);
                }
            }
        });
        BillingProvider.get().querySkuDetails(BillingClient.SkuType.SUBS, this);
    }
    
    private void updateRewardLayout () {
        if (NovaUser.getInstance(this).isVIP()|| rewardAd == null) {
            rewardLayout.setVisibility(View.GONE);
        } else {
            if (NovaUser.getInstance(this).usePremiumSeconds()) {
                rewardLayout.setVisibility(View.VISIBLE);
                long premiumTime = NovaUser.getInstance(this).getFreePremiumSeconds();
                TextView text = findViewById(R.id.reward_text);
                ImageView giftIcon = rewardLayout.findViewById(R.id.reward_icon);
                if (premiumTime <= 0) {
                    text.setText(R.string.reward_text_no_premium_time_watch_ad);
                } else {
                    String s = CommonUtils.formatSeconds(this, premiumTime);
                    text.setText(getString(R.string.reward_text_has_premium_time_watch_ad, s));
                }
                giftIcon.setImageResource(R.drawable.icon_reward);

                rewardLayout.setVisibility(View.VISIBLE);
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(rewardLayout, "scaleX", 0.7f, 1.0f, 1.0f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(rewardLayout, "scaleY", 0.7f, 1.0f, 1.0f);
                AnimatorSet animSet = new AnimatorSet();
                animSet.play(scaleX).with(scaleY);
                animSet.setInterpolator(new BounceInterpolator());
                animSet.setDuration(800).start();
            } else {
                rewardLayout.setVisibility(View.GONE);
            }
        }
    }

    private void loadRewardAd() {
        if (NovaUser.getInstance(this).usePremiumSeconds()) {

            FuseAdLoader.get(SLOT_USER_CENTER_REWARD, this).loadAd(this, 2, 1000,
                    new IAdLoadListener() {
                        @Override
                        public void onRewarded(IAdAdapter ad) {
                            //do reward
                            isRewarded = true;
                            MLogs.d("onRewarded ....");
                        }

                        @Override
                        public void onAdLoaded(IAdAdapter ad) {
                            rewardAd = ad;
                            updateRewardLayout();
                        }

                        @Override
                        public void onAdClicked(IAdAdapter ad) {

                        }

                        @Override
                        public void onAdClosed(IAdAdapter ad) {

                        }

                        @Override
                        public void onAdListLoaded(List<IAdAdapter> ads) {

                        }

                        @Override
                        public void onError(String error) {
                        }
                    });
        }
    }


    public void onRewardClick(View view) {
        if (rewardAd != null) {
            rewardAd.show();
        }
    }

    private int periodToDay(String s){
        if (!TextUtils.isEmpty(s)){
            String period = s.toLowerCase();
            char unit = period.charAt(period.length() -1 );
            Integer number = Integer.valueOf(period.substring(1, period.length()-1));
            MLogs.d(s + " " + number + " " + unit );
            switch (unit){
                case 'w':
                    return number.intValue()*7;
                case 'm':
                    return number.intValue()*30;
                case 'd':
                    return number.intValue();
                case 'y':
                    return number.intValue()*12*30;
                    default:
                        return -1;
            }
        }
        return  -1;
    }

    @Override
    protected boolean useCustomTitleBar() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRewardLayout();
        if (isRewarded) {
            NovaUser.getInstance(this).doRewardFreePremium();
            Toast.makeText(this, R.string.get_reward_premium_time, Toast.LENGTH_SHORT).show();
            isRewarded = false;
        }
    }

    private long basePrice;

    @Override
    public void onSkuDetailsResponse(int responseCode, List<SkuDetails> skuDetailsList) {
        MLogs.d("Billing Query SKU Response Code: " + responseCode);
        if (skuDetailsList != null) {
            Collections.sort(skuDetailsList, new Comparator<SkuDetails>() {
                @Override
                public int compare(SkuDetails skuDetails, SkuDetails t1) {
                    return (int) (skuDetails.getPriceAmountMicros() - t1.getPriceAmountMicros());
                }
            });
            this.skuDetailsList = skuDetailsList;
            for (SkuDetails item : this.skuDetailsList) {
                MLogs.d(item.toString());
            }
            if (skuDetailsList.size() > 0) {
                basePrice = skuDetailsList.get(0).getPriceAmountMicros()
                        / periodToDay(skuDetailsList.get(0).getSubscriptionPeriod());
                cardAdapter.notifyDataSetChanged();
            }
        }
    }

    private class SubscribeCardAdapter extends BaseAdapter{
        @Override
        public int getCount() {
            return skuDetailsList.size();
        }

        @Override
        public Object getItem(int i) {
            return skuDetailsList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = LayoutInflater.from(UserCenterActivity.this).inflate(R.layout.subscribe_card_layout, null);
            }
            View bg = view.findViewById(R.id.background);
            TextView main = view.findViewById(R.id.main_title);
            TextView price = view.findViewById(R.id.price_text);
            TextView trial = view.findViewById(R.id.free_trial);
            TextView discount = view.findViewById(R.id.discount_text);
            int colorIdx = i % arrCardColor.length;
            MLogs.d("colorIdx: " + colorIdx);
            bg.setBackgroundResource(arrCardColor[colorIdx]);
            price.setText(skuDetailsList.get(i).getPrice());
            //main.setText(skuDetailsList.get(i).getTitle());
            int day = periodToDay(skuDetailsList.get(i).getFreeTrialPeriod());
            MLogs.d("days: " + day);
            if (day >= 1) {
                trial.setText(getResources().getString(R.string.free_trial_text, day));
            } else {
                trial.setVisibility(View.INVISIBLE);
            }
            day = periodToDay(skuDetailsList.get(i).getSubscriptionPeriod());
            if (day % 360 == 0){
                main.setText(day/360 + " Year");
            } else if(day % 30 == 0) {
                main.setText(day/30 + " Month");
            } else if (day % 7 == 0) {
                main.setText(day/7 + " Week");
            } else {
                main.setText(day + " Days");
            }
            long dayPrice = skuDetailsList.get(i).getPriceAmountMicros()/day;
            if (dayPrice == basePrice) {
                discount.setVisibility(View.INVISIBLE);
            } else {
                if(basePrice != 0) {
                    long relative = 100 - dayPrice * 100 / basePrice;
                    discount.setText("%" + relative + " OFF");
                }
            }
            return view;
        }
    }
}
