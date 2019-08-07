package com.blackshark.gamepadservice;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Choreographer;
import android.view.InputEvent;

import com.android.dx.stock.ProxyBuilder;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

import mirror.android.view.InputEventReceiver;
import mirror.android.view.ViewRootImpl;

/**
 * Created by max.ma on 2017/10/19.
 */

public class BsGamePadProxyMaker extends BsGamePadService {
    private static final boolean DEBUG = false;
    private static final String TAG = "BsGamePadProxyMaker";

    private Object mProxy;
    private ProxyBuildInvocationHandler mProxyBuildInvocationHandler;

    private static final String BSGMAMEPAD_FINISH_INPUT_BASE_MILLIS_SECOND =
        "bsgamepad_finish_input_base";
    private static final String BSGMAMEPAD_FINISH_INPUT_DELAY_MILLIS_SECOND =
        "bsgamepad_finish_input_delay";
    private static final int TYPE_FINISH_INPUT_EVENT = 0;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case TYPE_FINISH_INPUT_EVENT:
                    finishInputEvent((InputEvent) msg.obj);
                    break;
                default:
                    break;
            }
        }
    };

    public BsGamePadProxyMaker(Context context) {
        super(context);
        mProxyBuildInvocationHandler = new ProxyBuildInvocationHandler();
    }

    @Override
    public void start() throws IOException {
        super.start();
        Object objViewRootImpl = getViewRootImplInstance();
        if (objViewRootImpl == null){
            Log.d(TAG, "objViewRootImpl = NULL");
            return;
        }

        Object inputChannel = ViewRootImpl.mInputChannel.get(objViewRootImpl);
        if (inputChannel == null) {
            Log.d(TAG, "inputChannel = " + inputChannel);
            return;
        }

        Object inputEventReceiver = ViewRootImpl.mInputEventReceiver.get(objViewRootImpl);
        if (inputEventReceiver == null){
            Log.d(TAG, "inputEventReceiver = " + inputEventReceiver);
            return;
        }

        ViewRootImpl.mInputEventReceiver.set(objViewRootImpl, null);
        InputEventReceiver.consumeBatchedInputEvents.call(inputEventReceiver, -1);
        ViewRootImpl.WindowInputEventReceiver.dispose.call(inputEventReceiver);
        Class<?> clsInputEventReceiver = inputEventReceiver.getClass().getSuperclass();
        if (!"android.view.InputEventReceiver".equals(clsInputEventReceiver.getName())){
            Log.d(TAG, String.format(Locale.US,
                "clsInputEventReceiver[%s] should be android.view.InputEventReceiver",
                clsInputEventReceiver.getName()));
            return;
        }

        ProxyBuilder proxyBuilder = ProxyBuilder.forClass(clsInputEventReceiver);
        proxyBuilder.dexCache(getContext().getDir("dx", Context.MODE_PRIVATE));
        proxyBuilder.handler(mProxyBuildInvocationHandler);
        proxyBuilder.constructorArgTypes(inputChannel.getClass(), Looper.class);
        proxyBuilder.constructorArgValues(inputChannel, Looper.myLooper());
        Object proxy = proxyBuilder.build();
        if (proxy == null){
            Log.d(TAG,"can not create proxy");
            return;
        }

        mProxy = proxy;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void injectInputEvent(InputEvent event){
        Object objViewRootImpl = getViewRootImplInstance();
        if (objViewRootImpl == null){
            Log.d(TAG, "objViewRootImpl = NULL");
            return;
        }

        if (DEBUG) Log.d(TAG, "injectInputEvent : event = " + event);
        ViewRootImpl.enqueueInputEvent.call(objViewRootImpl, event, mProxy, 0, true);
    }

    public void finishInputEvent(InputEvent event){
        if (DEBUG) Log.d(TAG, "finishInputEvent : event = " + event);
        InputEventReceiver.finishInputEvent.call(mProxy, event, true);
    }

    class ProxyBuildInvocationHandler implements InvocationHandler{
        Choreographer choreographer = Choreographer.getInstance();
        private final ConsumeBatchedInputEventsRunnable runnable = new ConsumeBatchedInputEventsRunnable();
        private boolean consume = false;

        private long getDelayMilliSecond(InputEvent event){
            int base = Settings.System.getInt(getContext().getContentResolver(),
                BSGMAMEPAD_FINISH_INPUT_BASE_MILLIS_SECOND,500);
            int delay = Settings.System.getInt(getContext().getContentResolver(),
                BSGMAMEPAD_FINISH_INPUT_DELAY_MILLIS_SECOND,20);
            return base + delay*((SparseIntArray)InputEventReceiver.mSeqMap.get(mProxy)).
                    indexOfKey(mirror.android.view.InputEvent.mSeq.get(event));
        }

        private void onInputEvent(InputEvent event){
            if (processInputEvent(event)){
                Message msg = mHandler.obtainMessage(TYPE_FINISH_INPUT_EVENT);
                msg.obj = event;
                long delay = getDelayMilliSecond(event);
                mHandler.sendMessageDelayed(msg, delay);
            }
        }

        private void onBatchedInputEventPending(){
            if (consume){
                return;
            }

            if (DEBUG) Log.d(TAG, "onBatchedInputEventPending");
            consume = true;
            mirror.android.view.Choreographer.postCallback.call(
              choreographer, mirror.android.view.Choreographer.CALLBACK_INPUT.get(), runnable, null);
        }

        private void dispose(){
            if (!consume){
                return;
            }

            consume = false;
            if (DEBUG) Log.d(TAG, "dispose");
            mirror.android.view.Choreographer.removeCallbacks.call(
                choreographer,mirror.android.view.Choreographer.CALLBACK_INPUT.get(), runnable, null);
        }

        private void consumeBatchedInputEvents(long frameTimeNanos){
            if (!consume){
                return;
            }

            consume = false;

            if (mProxy == null){
                return;
            }
            if (DEBUG) Log.d(TAG, "consumeBatchedInputEvents");
            boolean ret = InputEventReceiver.consumeBatchedInputEvents.call(mProxy, frameTimeNanos);
            if (ret && frameTimeNanos != -1L){
                onBatchedInputEventPending();
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (DEBUG)Log.d(TAG, String.format(Locale.US, "Method:%s args:%s",
                method.getName(), Arrays.toString(args)));
            String name = method.getName();
            switch (name){
                case "onInputEvent":
                    onInputEvent((InputEvent) args[0]);
                    return null;
                case "onBatchedInputEventPending":
                    onBatchedInputEventPending();
                    return null;
                case "dispose":
                    dispose();
                    break;
                default:
                    break;
            }

            Object result = ProxyBuilder.callSuper(proxy, method, args);
            if (DEBUG)Log.d(TAG, String.format(Locale.US, "Method:%s args:%s, result:%s",
                method.getName(), Arrays.toString(args), result == null ? "null" : result.toString()));
            return result;
        }

        private final class ConsumeBatchedInputEventsRunnable implements Runnable {

            @Override
            public void run() {
                consumeBatchedInputEvents(mirror.android.view.Choreographer.getFrameTimeNanos.call(choreographer));
            }
        }
    }
}
