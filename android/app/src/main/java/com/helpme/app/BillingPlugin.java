package com.helpme.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@CapacitorPlugin(name = "BillingPlugin")
public class BillingPlugin extends Plugin implements PurchasesUpdatedListener {

    private static final String TAG = "BillingPlugin";
    private static final String PRO_PRODUCT_ID = "helpme_pro_unlock";
    private static final String PREF_NAME = "HelpMeBillingPrefs";
    private static final String PREF_IS_PRO_KEY = "is_pro";

    private BillingClient billingClient;
    private SharedPreferences sharedPrefs;
    private boolean isBillingServiceConnected = false;

    // We store the current call to resolve it after purchase flow finishes
    private PluginCall currentPurchaseCall = null;

    @Override
    public void load() {
        super.load();
        sharedPrefs = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        billingClient = BillingClient.newBuilder(getContext())
                .setListener(this)
                .enablePendingPurchases()
                .build();

        connectToPlayBilling(null);
    }

    private void connectToPlayBilling(Runnable onConnectedSuccess) {
        if (isBillingServiceConnected && billingClient.isReady()) {
            if (onConnectedSuccess != null)
                onConnectedSuccess.run();
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    isBillingServiceConnected = true;
                    // Automatically verify purchases quietly in the background on startup
                    queryPurchasesBackground();
                    if (onConnectedSuccess != null) {
                        onConnectedSuccess.run();
                    }
                } else {
                    Log.e(TAG, "Billing setup failed: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                isBillingServiceConnected = false;
                // Retry connection if needed, though typically done on next request
            }
        });
    }

    private void queryPurchasesBackground() {
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        handlePurchases(purchases);
                    }
                });
    }

    private void cacheIsPro(boolean isPro) {
        sharedPrefs.edit().putBoolean(PREF_IS_PRO_KEY, isPro).apply();
    }

    private void handlePurchases(List<Purchase> purchases) {
        if (purchases != null) {
            boolean hasPro = false;
            for (Purchase purchase : purchases) {
                if (purchase.getProducts().contains(PRO_PRODUCT_ID)
                        && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    hasPro = true;
                    if (!purchase.isAcknowledged()) {
                        AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                        billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                            Log.i(TAG, "Purchase acknowledged: " + billingResult.getResponseCode());
                        });
                    }
                }
            }
            cacheIsPro(hasPro);
        } else {
            cacheIsPro(false);
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases);
            if (currentPurchaseCall != null) {
                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("isPro", true);
                currentPurchaseCall.resolve(ret);
                currentPurchaseCall = null;
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            if (currentPurchaseCall != null) {
                currentPurchaseCall.reject("User canceled purchase");
                currentPurchaseCall = null;
            }
        } else {
            Log.e(TAG, "Purchase failed: " + billingResult.getDebugMessage() + " code: "
                    + billingResult.getResponseCode());
            if (currentPurchaseCall != null) {
                currentPurchaseCall.reject("Purchase failed: " + billingResult.getDebugMessage());
                currentPurchaseCall = null;
            }
        }
    }

    @PluginMethod
    public void getProStatus(PluginCall call) {
        boolean cachedIsPro = sharedPrefs.getBoolean(PREF_IS_PRO_KEY, false);
        JSObject ret = new JSObject();
        ret.put("isPro", cachedIsPro);
        call.resolve(ret);
    }

    @PluginMethod
    public void restorePurchases(PluginCall call) {
        Runnable restoreAction = () -> {
            billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                    (billingResult, purchases) -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            handlePurchases(purchases);
                            boolean isPro = sharedPrefs.getBoolean(PREF_IS_PRO_KEY, false);
                            JSObject ret = new JSObject();
                            ret.put("isPro", isPro);
                            call.resolve(ret);
                        } else {
                            call.reject("Failed to restore purchases: " + billingResult.getDebugMessage());
                        }
                    });
        };

        connectToPlayBilling(restoreAction);
    }

    @PluginMethod
    public void purchasePro(PluginCall call) {
        currentPurchaseCall = call;

        Runnable purchaseAction = () -> {
            // First check if already purchased
            boolean cachedIsPro = sharedPrefs.getBoolean(PREF_IS_PRO_KEY, false);
            if (cachedIsPro) {
                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("isPro", true);
                call.resolve(ret);
                currentPurchaseCall = null;
                return;
            }

            QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                            Collections.singletonList(
                                    QueryProductDetailsParams.Product.newBuilder()
                                            .setProductId(PRO_PRODUCT_ID)
                                            .setProductType(BillingClient.ProductType.INAPP)
                                            .build()))
                    .build();

            billingClient.queryProductDetailsAsync(
                    queryProductDetailsParams,
                    (billingResult, productDetailsList) -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                                && productDetailsList != null && !productDetailsList.isEmpty()) {
                            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                    .setProductDetailsParamsList(
                                            Collections.singletonList(
                                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                                            .setProductDetails(productDetailsList.get(0))
                                                            .build()))
                                    .build();

                            // execute launchBillingFlow on main thread
                            getActivity().runOnUiThread(() -> {
                                BillingResult launchResult = billingClient.launchBillingFlow(getActivity(),
                                        billingFlowParams);
                                if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                    if (currentPurchaseCall != null) {
                                        currentPurchaseCall.reject(
                                                "Could not launch billing flow: " + launchResult.getDebugMessage());
                                        currentPurchaseCall = null;
                                    }
                                }
                            });
                        } else {
                            if (currentPurchaseCall != null) {
                                currentPurchaseCall.reject(
                                        "Product not found or error querying: " + billingResult.getDebugMessage());
                                currentPurchaseCall = null;
                            }
                        }
                    });
        };

        if (isBillingServiceConnected && billingClient.isReady()) {
            purchaseAction.run();
        } else {
            connectToPlayBilling(purchaseAction);
        }
    }
}
