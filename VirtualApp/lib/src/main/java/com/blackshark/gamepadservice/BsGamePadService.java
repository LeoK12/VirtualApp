package com.blackshark.gamepadservice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.IBinder;
import android.renderscript.ScriptGroup;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.ViewGroup;

import com.lody.virtual.client.NativeEngine;
import com.lody.virtual.client.ipc.ActivityClientRecord;
import com.lody.virtual.client.ipc.VActivityManager;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by max.ma on 2017/10/19.
 */

public class BsGamePadService extends Service implements InputManager.InputDeviceListener {
    private static final boolean DEBUG = true;
    private static final String TAG = "BsGamePadService";
    private static final int GAMEPAD_SOURCE = InputDevice.SOURCE_CLASS_JOYSTICK | InputDevice.SOURCE_GAMEPAD;

    private Context mContext;
    private IBinder mBinder;
    private ActivityClientRecord mRecord;
    private InputManager mIm;
    private InputDevice mInputDevice;
    private BsGamePadMapper mGamePadMapper;
    private static BsGamePadController mGamePadController;

    private static ViewGroup mDecorView;
    private static Object mObjViewRootImpl;
    private static Object mObjInputEventReceiver;
    private static Method mMethodEnqueueInputEvent;
    private static Method mMethodfinishInputEvent;

    public static Method mtdOnInputEvent;
    public static Method mtdOnInputEventProxy;
    public static Method mtdfinishInputEvent;
    public static Method mtdfinishInputEventProxy;
    private static Field mObjEventInQueuedInputEvent;

    private static final int METHOD_ON_INPUT_EVENT  = 0;
    private static final int METHOD_FINISH_INPUT_EVENT  = 1;

    public BsGamePadService(Context context, IBinder token){
        mContext = context;
        mBinder = token;
    }

    private void checkGamePadStatus(){
        for (int deviceId : mIm.getInputDeviceIds()){
            InputDevice device = mIm.getInputDevice(deviceId);
            if ((device.getSources() & GAMEPAD_SOURCE) != 0) {
                enableGamePadController(device, true);
                break;
            }
        }
    }

    public void start(){
        mIm = mContext.getSystemService(InputManager.class);
        mIm.registerInputDeviceListener(this, new Handler());
        if (mGamePadMapper == null){
            File dataDir = mContext.getDataDir();
            mGamePadMapper = new BsGamePadMapper(new File(dataDir, "bsgamepadmapper.xml"));
            setLeftDirectionPad();
        }
        getViewRootImpleInstance();
        getSrcMethodAndDestMethod();
        checkGamePadStatus();
    }

    public void stop(){
        backupMethod();
    }

    private void setLeftDirectionPad() {
        mGamePadMapper.updateGamePadMapper(
            0x1 << BsGamePadController.DIRECTION_PAD_L,
            235, 857, 200);
    }

    private void getSrcMethodAndDestMethod(){
        if (mtdOnInputEvent == null) {
            try {
                for (Method mth : Class.forName("android.view.ViewRootImpl$WindowInputEventReceiver").getDeclaredMethods()) {
                    if ("onInputEvent".equals(mth.getName())) {
                        mtdOnInputEvent = mth;
                        mth.setAccessible(true);
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        if (mtdOnInputEventProxy == null){
            try {
                for (Method mth : Class.forName("com.blackshark.gamepadservice.BsGamePadService").getDeclaredMethods()) {
                    if ("onInputEvent".equals(mth.getName())) {
                        mtdOnInputEventProxy = mth;
                        mth.setAccessible(true);
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        if (mtdfinishInputEvent == null){
            try {
                for (Method mth : Class.forName("android.view.ViewRootImpl").getDeclaredMethods()) {
                    if ("finishInputEvent".equals(mth.getName())) {
                        mtdfinishInputEvent = mth;
                        mth.setAccessible(true);
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        if (mtdfinishInputEventProxy == null){
            try {
                for (Method mth : Class.forName("com.blackshark.gamepadservice.BsGamePadService").getDeclaredMethods()) {
                    if ("finishInputEvent".equals(mth.getName())) {
                        mtdfinishInputEventProxy = mth;
                        mth.setAccessible(true);
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void getViewRootImpleInstance(){
        try {
            ActivityClientRecord r = VActivityManager.get().getActivityRecord(mBinder);
            mRecord = r;

            mDecorView = (ViewGroup)r.activity.getWindow().peekDecorView();
            if (DEBUG) {
                Log.d(TAG, "peekDecorView = " + mDecorView);
            }

            Class<?> classViewRootImpl = Class.forName("android.view.ViewRootImpl");
            for (Method mth : classViewRootImpl.getDeclaredMethods()){
                if ("enqueueInputEvent".equals(mth.getName()) && mth.getParameterTypes().length == 4){
                    mMethodEnqueueInputEvent = mth;
                    mth.setAccessible(true);
                    break;
                }
            }

            if (DEBUG) {
                Log.d(TAG, "mMethodEnqueueInputEvent = " + mMethodEnqueueInputEvent);
            }

            Class<?> classWindowInputEventReceiver = Class.forName("android.view.InputEventReceiver");
            for (Method mth : classWindowInputEventReceiver.getDeclaredMethods()){
                if ("finishInputEvent".equals(mth.getName()) && mth.getParameterTypes().length == 2){
                    mMethodfinishInputEvent = mth;
                    mth.setAccessible(true);
                    break;
                }
            }

            if (DEBUG) {
                Log.d(TAG, "mMethodfinishInputEvent = " + mMethodfinishInputEvent);
            }

            Class<?> classQueuedInputEvent = Class.forName("android.view.ViewRootImpl$QueuedInputEvent");
            for (Field filed : classQueuedInputEvent.getDeclaredFields()){
                if ("mEvent".equals(filed.getName())){
                    mObjEventInQueuedInputEvent = filed;
                }

            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private Object getObjViewRootImpl(){
        if (mObjViewRootImpl != null){
            return mObjViewRootImpl;
        }

        try {
            Class<?> classView = Class.forName("android.view.View");
            Method methodgetViewRootImpl = classView.getDeclaredMethod("getViewRootImpl");
            if (methodgetViewRootImpl == null){
                Log.d(TAG, "methodgetViewRootImpl should NOT be NULL!");
            }

            Object objViewRootImpl = methodgetViewRootImpl.invoke(mDecorView);
            if (objViewRootImpl == null){
                return null;
            }

            mObjViewRootImpl = objViewRootImpl;
        } catch (Exception e){
            e.printStackTrace();
        }

        return mObjViewRootImpl;
    }

    private Object getObjInputEventReceiver(){
        if (mObjInputEventReceiver != null){
            return mObjInputEventReceiver;
        }

        try {
            Class<?> classViewRootImpl = Class.forName("android.view.ViewRootImpl");

            Field fieldInputEventReceiver = classViewRootImpl.getDeclaredField("mInputEventReceiver");
            if (!fieldInputEventReceiver.isAccessible()){
                fieldInputEventReceiver.setAccessible(true);
            }

            Object objInputEventReceiver = fieldInputEventReceiver.get(getObjViewRootImpl());
            if (objInputEventReceiver == null){
                return null;
            }

            mObjInputEventReceiver = objInputEventReceiver;
        } catch (Exception e){
            e.printStackTrace();
        }

        return mObjInputEventReceiver;
    }

    public void injectInputEvent(InputEvent event, boolean processImmediately){
        if (DEBUG) {
            Log.d(TAG, "injectInputEvent ENTER");
        }
        try {
            mMethodEnqueueInputEvent.invoke(getObjViewRootImpl(), event, null, 0, processImmediately);
        } catch (Exception e){
            e.printStackTrace();
        }
        if (DEBUG) {
            Log.d(TAG, "injectInputEvent END");
        }
    }

    private void enqueueInputEvent(InputEvent event, int flags, boolean processImmediately){
        try {
            mMethodEnqueueInputEvent.invoke(getObjViewRootImpl(), event, getObjInputEventReceiver(), flags, processImmediately);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void removeInputEvent(InputEvent event, boolean handled){
        finishInputEvent(event, handled);
    }

    private void finishInputEvent(InputEvent event, boolean handled){
        if (DEBUG) {
            Log.d(TAG, "finishInputEvent ENTER event = " + event);
        }
        try {
            mMethodfinishInputEvent.invoke(getObjInputEventReceiver(), event, handled);
        } catch (Exception e){
            e.printStackTrace();
        }
        if (DEBUG) {
            Log.d(TAG, "finishInputEvent END");
        }
    }

    public void onInputEvent(InputEvent event) {
        if (mGamePadController == null) {
            return;
        }

        if (DEBUG) Log.d(TAG, "event = " + event);
        boolean isHandled = mGamePadController.processInputEvent(event);
        if (!isHandled) {
            enqueueInputEvent(event, 0, true);
        } else {
            finishInputEvent(event, true);
        }
    }

    public void finishInputEvent(Object q){
        if (DEBUG) {
            Log.e(TAG, "finishInputEvent : q = " + q);
        }
        int deviceId = -1;
        try {
            InputEvent event = (InputEvent) mObjEventInQueuedInputEvent.get(q);
            deviceId = event.getDeviceId();
        } catch (Exception e){
            e.printStackTrace();
        }

        NativeEngine.nativeCallMethod(mObjViewRootImpl, q, METHOD_FINISH_INPUT_EVENT);

        if (DEBUG){
            Log.e(TAG, "finishInputEvent : deviceId = " + deviceId);
        }
        if (deviceId == 0){
            mGamePadController.getInjectMotionEventThread().nofityForFinish();
        }
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

    private void enableGamePadController(InputDevice device, boolean enable){
        if (DEBUG) {
            Log.d(TAG, "enableGamePadController : enable = " + enable);
        }
        if (enable){
            if (DEBUG) {
                Log.d(TAG, "enableGamePadController : mGamePadController = " + mGamePadController);
            }
            if (mGamePadController == null) {
                mGamePadController = new BsGamePadController(mContext, mGamePadMapper, this);
                if (DEBUG) {
                    Log.d(TAG, "enableGamePadController : mGamePadController = " + mGamePadController);
                    Log.d(TAG, "enableGamePadController : nativeHookMethod : mtdOnInputEvent = " + mtdOnInputEvent);
                    Log.d(TAG, "enableGamePadController : nativeHookMethod : mtdOnInputEventProxy = " + mtdOnInputEventProxy);
                }
                mInputDevice = device;
            }

            if (DEBUG) {
                Log.d(TAG, "enableGamePadController : nativeHookMethod ENTER");
            }
            hookMethod();
            if (DEBUG) {
                Log.d(TAG, "enableGamePadController : nativeHookMethod END");
            }
        } else {
            mInputDevice = null;
            backupMethod();
            mGamePadController = null;
        }
    }

    public void hookMethod(){
        if (DEBUG){
            Log.d(TAG, "hookMethod ENTER");
        }
        Method[] srcMethods = {mtdOnInputEvent, mtdfinishInputEvent};
        Method[] destMethods = {mtdOnInputEventProxy, mtdfinishInputEventProxy};
        NativeEngine.nativeHookMethod(srcMethods, destMethods);
        if (DEBUG){
            Log.d(TAG, "hookMethod END");
        }
    }

    public void backupMethod(){
        if (DEBUG){
            Log.d(TAG, "backupMethod ENTER");
        }
        Method[] srcMethods = {mtdOnInputEvent, mtdfinishInputEvent};
        NativeEngine.nativeBackupMethod(srcMethods);
        if (DEBUG){
            Log.d(TAG, "backupMethod END");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        InputDevice device = mIm.getInputDevice(deviceId);
        if (device == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "onInputDeviceAdded : device = " + device);
        }

        if ((device.getSources() & GAMEPAD_SOURCE) != 0) {
            enableGamePadController(device, true);
        }
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        if (DEBUG) {
            Log.d(TAG, "onInputDeviceRemoved : deviceId = " + deviceId);
        }

        if (mInputDevice != null && mInputDevice.getId() == deviceId){
            enableGamePadController(mInputDevice, false);
        }
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {

    }
}
