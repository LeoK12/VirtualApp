package com.blackshark.gamepadservice;

import android.content.Context;
import android.util.Log;
import android.view.InputEvent;

import com.lody.virtual.client.NativeEngine;

import java.io.IOException;
import java.lang.reflect.Method;

import mirror.android.view.InputEventReceiver;
import mirror.android.view.ViewRootImpl;

/**
 * Created by max.ma on 2017/10/19.
 */

public class BsGamePadHookNative extends BsGamePadService {
    private static final boolean DEBUG = true;
    private static final String TAG = "BsGamePadHookNative";

    public BsGamePadHookNative(Context context){
        super(context);
    }

    private void enqueueInputEvent(InputEvent event, Object inputEventReceiver, int flags, boolean processImmediately){
        Object objViewRootImpl = super.getViewRootImplInstance();
        if (objViewRootImpl == null){
            Log.d(TAG, "objViewRootImpl = NULL");
            return;
        }

        ViewRootImpl.enqueueInputEvent.call(objViewRootImpl, event, inputEventReceiver, flags, processImmediately);
    }

    private void finishInputEvent(InputEvent event, boolean handled){
        if (DEBUG) Log.d(TAG, "finishInputEvent : this = " + this);
        if (DEBUG) Log.d(TAG, "finishInputEvent : event = " + event);
        InputEventReceiver.finishInputEvent.call(this, event, handled);
    }

    public void onInputEvent(InputEvent event, int displayId) {
        if (DEBUG) Log.d(TAG, "event = " + event);
        boolean isHandled = super.processInputEvent(event);
        if (!isHandled) {
            this.enqueueInputEvent(event, this,0, true);
        } else {
            this.finishInputEvent(event, true);
        }
    }

    private void hookMethod(){
        if (DEBUG){
            Log.d(TAG, "hookMethod ENTER");
        }
        Method[] srcMethods = {ViewRootImpl.WindowInputEventReceiver.onInputEvent.method()};
        Method[] destMethods = new Method[0];
        try {
            destMethods = new Method[]{getClass().getDeclaredMethod("onInputEvent", InputEvent.class, int.class)};
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        NativeEngine.nativeHookMethod(srcMethods, destMethods);
        if (DEBUG){
            Log.d(TAG, "hookMethod END");
        }
    }

    @Override
    void injectInputEvent(InputEvent event) {
        Object objViewRootImpl = super.getViewRootImplInstance();
        if (objViewRootImpl == null){
            Log.d(TAG, "objViewRootImpl = NULL");
            return;
        }

        Object inputEventReceiver = ViewRootImpl.mInputEventReceiver.get(objViewRootImpl);
        if (inputEventReceiver == null){
            Log.d(TAG, "inputEventReceiver = NULL");
            return;
        }

        enqueueInputEvent(event, inputEventReceiver, 0, true);
    }

    @Override
    public void start() throws IOException {
        super.start();
        hookMethod();
    }
}
