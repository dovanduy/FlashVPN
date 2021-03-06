package com.polestar.superclone.reward;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.polestar.superclone.R;
import com.polestar.task.ADErrorCode;
import com.polestar.task.IProductStatusListener;
import com.polestar.task.network.datamodels.Product;

import java.util.ArrayList;

public class ProductActivity extends Activity implements IProductStatusListener{



    private Product mProduct;
    private TextView mName;
    private TextView mDescription;
    private TextView mPrice;

    private Button mPurchase;
    private ImageView mIcon;

    private AppUser mAppUser;


    public static final String EXTRA_PRODUCT = "product";

    public static void start(Activity activity, Product product) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_PRODUCT, product);
        intent.setClass(activity, ProductActivity.class);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_1);

        mAppUser = AppUser.getInstance();
//
        mName = (TextView) findViewById(R.id.activity_product_product_name);
        mDescription = (TextView) findViewById(R.id.activity_product_product_description);
        mPurchase = (Button) findViewById(R.id.activity_product_purchase);
        mIcon = (ImageView) findViewById(R.id.activity_product_product_icon);
        mPrice = (TextView) findViewById(R.id.activity_product_price);

        Intent intent = getIntent();
        mProduct = intent.getParcelableExtra(EXTRA_PRODUCT);
        mName.setText(mProduct.mName);
        mDescription.setText(mProduct.mDescription);
        mIcon.setImageDrawable(AssetHelper.getDrawable(this, mProduct.mIconUrl));
        mPrice.setText("" + (int) mProduct.mCost);


        mPurchase.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int status = ProductManager.getInstance().canBuyProduct(mProduct);
                if (status != RewardErrorCode.PRODUCT_OK) {
                    RewardErrorCode.toastMessage(ProductActivity.this, status);
                    return;
                }
                ProductManager.getInstance().buyProduct(mProduct, ProductActivity.this);
                //buyProduct();
            }
        });
    }

    @Override
    public void onConsumeSuccess(long id, int amount, float totalCost, float balance) {
        RewardErrorCode.toastMessage(ProductActivity.this, RewardErrorCode.PRODUCT_OK, totalCost);
        finish();
    }

    @Override
    public void onConsumeFail(ADErrorCode code) {

        RewardErrorCode.toastMessage(ProductActivity.this, code.getErrCode());
    }

    @Override
    public void onGetAllAvailableProducts(ArrayList<Product> products) {

    }

    @Override
    public void onGeneralError(ADErrorCode code) {

        RewardErrorCode.toastMessage(ProductActivity.this, code.getErrCode());
    }

    public void onClose(View view) {
        finish();
    }
}
