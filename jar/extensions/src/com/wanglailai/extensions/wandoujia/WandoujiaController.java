package com.wanglailai.extensions.wandoujia;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.text.TextUtils;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.wandoujia.mariosdk.plugin.api.api.WandouGamesApi;
import com.wandoujia.mariosdk.plugin.api.model.callback.OnLoginFinishedListener;
import com.wandoujia.mariosdk.plugin.api.model.callback.OnPayFinishedListener;
import com.wandoujia.mariosdk.plugin.api.model.model.LoginFinishType;
import com.wandoujia.mariosdk.plugin.api.model.model.PayResult;
import com.wandoujia.mariosdk.plugin.api.model.model.UnverifiedPlayer;
import com.wandoujia.mariosdk.plugin.api.model.model.WandouPlayer;
import com.wanglailai.extensions.PurchasedItem;
import com.wanglailai.extensions.UserProfile;

public class WandoujiaController {

	private static final int TRANSACTION_STATE_FAILED = 1;
	private static final int TRANSACTION_STATE_PURCHASED = 2;
	private static final int TRANSACTION_STATE_PURCHASING = 3;
	
	private static final String TAG = "com.wanglailai.extensions.wandoujia.WandoujiaController";
	private static final String PURCHASED_EVENT = "WdjExtensionEvent.TransactionsReceived";
	
	private static final String AUTHORIZED_EVENT = "WdjExtensionEvent.AuthorationReceived";
	
	private static final String AUTHENTICATE_SUCCESS = "SUCCESS";
	
	private static final String AUTHENTICATE_FAIL = "FAIL";
	
    
    private static WandoujiaController instance = null;
    
    public static WandoujiaController getInstance(FREContext context) {
    	if (instance == null) {
    		instance = new WandoujiaController(context);
    	}
    	Log.i(TAG, "instance()");
    	return instance;
    }
    
    public FREContext mContext;
    
    public Activity _activity;
    
	private UserProfile _user;
	
	public boolean inited = false;
	
    private ArrayList<PurchasedItem> mOwnedItems;
    private HashMap<String, PurchasedItem> mProcessedItems = new HashMap<String, PurchasedItem>();
    
    private WandouGamesApi wandouGamesApi;
    protected WandoujiaController(FREContext context){
    	mContext = context;
    	_activity = context.getActivity();
    	
    	mOwnedItems = new ArrayList<PurchasedItem>();
    	
//    	WandouGamesApi.initPlugin(_activity, APP_KEY, SECURITY_KEY);
    }
    
    private String _appName;
    public void init(String appkey_id, String seckey, String appName){
    	if(inited){
			return;
		}
    	_appName = appName;
//    	WandouGamesApi.initPlugin(_activity, Long.parseLong(appkey_id), seckey);
    	
    	wandouGamesApi = new WandouGamesApi.Builder(_activity, Long.parseLong(appkey_id), seckey).create();
    	wandouGamesApi.init(_activity);
        wandouGamesApi.setLogEnabled(false);
    	inited = true;
    }
    
    /**登陆豌豆荚平台*/
    public void login(){
    	wandouGamesApi.login(mLoginCallback);
    }
    
	public UserProfile getUser() {
		return _user;
	}
	
    /**
     * 使用豌豆荚支付接口
     */
    private PurchasedItem _currentItem;
    public void startPayment(String orderId, String productName, long amount) {
    	if(_user == null || _currentItem != null){
    		Log.w(TAG, "User is not login or another payment is not complete!!! Cannot start payment!");
    		return;
    	}
         // 三个参数分别是 游戏名(String)，商品(String)，价格(Long)单位是分
    	_currentItem = new PurchasedItem(orderId, productName, (int)amount);
    	_currentItem.state = TRANSACTION_STATE_PURCHASING;
    	wandouGamesApi.pay(_activity, productName, amount, orderId, mPayCallback);
    }
    
	@SuppressWarnings("unchecked")
	public ArrayList<PurchasedItem> getPurchaseItems() {
		ArrayList<PurchasedItem> copiedItems;

		synchronized (mOwnedItems) {
			copiedItems = (ArrayList<PurchasedItem>) mOwnedItems.clone();
			mOwnedItems.clear();
		}
		Log.i(TAG, "getPurchasedItems read " + copiedItems.size() + " items.");
		
		return copiedItems;
	}
	
	public void finishPayment(){
		//add finish code
	}
	
	public void dispose(){
		WandoujiaController.instance = null;
	}
	
    //--------------调用支付接口--------------
    // 支付的回调
    private OnPayFinishedListener mPayCallback = new OnPayFinishedListener() {

		private void dispatchEvent(){
			Boolean raiseEvent = false;
			 synchronized(mOwnedItems) {
				 if (!mProcessedItems.containsKey(_currentItem.transactionID)) {
              		mProcessedItems.put(_currentItem.transactionID, _currentItem);
                      mOwnedItems.add(_currentItem);
                      raiseEvent = true;
          		}
			 }

        	 if(raiseEvent) {
                 mContext.dispatchStatusEventAsync(WandoujiaController.PURCHASED_EVENT, "");
                 Log.i(TAG, "PayCallBack() PURCHASED_EVENT raised");
         	}else{
                 Log.i(TAG, "PayCallBack() already processed: " + _currentItem.transactionID);
         	}
        	 _currentItem = null;
		}

		@Override
		public void onPayFail(PayResult result) {
			Log.w(TAG, "onError:"+result.getNick() + "Trade Failed:" + result.getOutTradeNo());
			_currentItem.state = TRANSACTION_STATE_FAILED;
			_currentItem.platformPayload = result.getData();
			dispatchEvent();
		}

		@Override
		public void onPaySuccess(PayResult result) {
			Log.i(TAG, "onSuccess:" + result.getOutTradeNo() + " status:" + result.getStatus());
			_currentItem.state = TRANSACTION_STATE_PURCHASED;
			dispatchEvent();			
		}
    };
    
    //豌豆荚 登录回调
    private OnLoginFinishedListener mLoginCallback = new OnLoginFinishedListener(){
		@Override
		public void onLoginFinished(LoginFinishType loginFinishType, UnverifiedPlayer unverifiedPlayer) {
			 WandouPlayer wandouPlayer = wandouGamesApi.getCurrentPlayerInfo();
			 
			 if(wandouPlayer == null || TextUtils.isEmpty(wandouPlayer.getId()))
			 {
				 mContext.dispatchStatusEventAsync(AUTHORIZED_EVENT, AUTHENTICATE_FAIL); 
			 }
			 else
			 {
				 Log.i(TAG, "user info:" + wandouPlayer.toString() + "\ntype:" + loginFinishType);
				 _user = new UserProfile();
				_user.uid = wandouPlayer.getId();
				_user.name = wandouPlayer.getNick();
				_user.token = unverifiedPlayer.getToken();
				_user.nick = wandouPlayer.getNick();
				mContext.dispatchStatusEventAsync(AUTHORIZED_EVENT, AUTHENTICATE_SUCCESS);
			 }
		}
    	
    };
    
}
