package com.blackshark.gamepadservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.InputEvent;
import android.view.ViewGroup;

import com.lody.virtual.client.ipc.ActivityClientRecord;
import com.lody.virtual.client.ipc.VActivityManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

import mirror.android.view.View;

public abstract class BsGamePadService extends Service {
    private static final boolean DEBUG = true;
    private static final String TAG = "BsGamePadService";
    private static final String BLACK_SHARK_GAMEPAD_SERVICE_TYPE = "gamepad_service_type";
    public static final String BLACK_SHARK_HOOK_NATIVE_SERVICE = "hooknative";
    public static final String BLACK_SHARK_PROXY_MAKER_SERVICE = "proxymaker";
    private static final String BLACK_SHARK_DEFAULT_SERVICE_TYPE = BLACK_SHARK_PROXY_MAKER_SERVICE;

    private Context mContext;
    private IBinder mBinder;
    private ActivityClientRecord mRecord;
    private BsGamePadMapper mGamePadMapper;
    private static BsGamePadController mGamePadController;

    private static ViewGroup mDecorView;
    private Object mObjViewRootImpl;

    public BsGamePadService(Context context){
        mContext = context;
    }

    public static String getServiceType(Context context){
        String type = Settings.System.getString(context.getContentResolver(),
            BLACK_SHARK_GAMEPAD_SERVICE_TYPE);
        if (type == null){
            type = BLACK_SHARK_DEFAULT_SERVICE_TYPE;
        }

        return type;
    }

    public void setToken(IBinder token){
        ActivityClientRecord r = VActivityManager.get().getActivityRecord(token);
        if (r == null) {
            return;
        }

        mBinder = token;
    }

    public Context getContext() {
        return mContext;
    }

    public IBinder getBinder(){
        return mBinder;
    }

    public ActivityClientRecord getRecord(){
        return mRecord;
    }

    public IBinder getActivityToken(){
        IBinder token = null;
        try {
            Class<?> classActivity = Class.forName("android.app.Activity");
            Field field = classActivity.getDeclaredField("mToken");
            if (DEBUG) {
                Log.d(TAG, "getActivityToken : field = " + field);
            }
            field.setAccessible(true);
            token = (IBinder)field.get(mRecord.activity);
            if (DEBUG) {
                Log.d(TAG, "getActivityToken : token = " + token);
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        return token;
    }

    public void loadConfig(){
        if (!checkBinder()){
            return;
        }

        if (mGamePadMapper == null){
            File dataDir = mContext.getDataDir();
            mGamePadMapper = new BsGamePadMapper(new File(dataDir, "bsgamepadmapper.xml"));
            setLeftDirectionPad();
        }

        if (mGamePadController == null){
            mGamePadController = new BsGamePadController(mContext, mGamePadMapper, this);
        }
    }

    private void setLeftDirectionPad() {
        mGamePadMapper.updateGamePadMapper(
            0x1 << BsGamePadController.DIRECTION_PAD_L,
            460, 878, 200);
    }

    private boolean checkBinder(){
        ActivityClientRecord r = VActivityManager.get().getActivityRecord(mBinder);
        return r != null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public Object getViewRootImplInstance() {
        return mObjViewRootImpl;
    }

    private void getViewRootImpl(){
        ActivityClientRecord r = VActivityManager.get().getActivityRecord(mBinder);
        mRecord = r;

        mDecorView = (ViewGroup)r.activity.getWindow().peekDecorView();
        if (mDecorView == null){
            Log.d(TAG, "mDecorView = " + mDecorView);
        }

        mObjViewRootImpl = View.getViewRootImpl.call(mDecorView);
        if (mObjViewRootImpl == null){
            Log.d(TAG, "mObjViewRootImpl = " + mObjViewRootImpl);
            return;
        }
    }

    public void start() throws IOException {
        getViewRootImpl();
    }

    public boolean processInputEvent(InputEvent event){
        boolean isHandled = false;
        if (mGamePadController != null){
            isHandled = mGamePadController.processInputEvent(event);
        }

        return isHandled;
    }

    abstract void injectInputEvent(InputEvent event);
}
