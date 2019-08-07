package com.lody.virtual.client.hook.proxies.am;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.blackshark.gamepadservice.BsGamePadHookNative;
import com.blackshark.gamepadservice.BsGamePadProxyMaker;
import com.blackshark.gamepadservice.BsGamePadService;
import com.lody.virtual.client.VClientImpl;
import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.client.interfaces.IInjector;
import com.lody.virtual.client.ipc.VActivityManager;
import com.lody.virtual.helper.utils.ComponentUtils;
import com.lody.virtual.helper.utils.Reflect;
import com.lody.virtual.helper.utils.VLog;
import com.lody.virtual.remote.InstalledAppInfo;
import com.lody.virtual.remote.StubActivityRecord;

import java.io.IOException;
import java.util.List;

import mirror.android.app.ActivityManagerNative;
import mirror.android.app.ActivityThread;
import mirror.android.app.IActivityManager;
import mirror.android.app.servertransaction.ClientTransaction;
import mirror.android.app.servertransaction.LaunchActivityItem;

import static com.blackshark.gamepadservice.BsGamePadService.BLACK_SHARK_HOOK_NATIVE_SERVICE;
import static com.blackshark.gamepadservice.BsGamePadService.BLACK_SHARK_PROXY_MAKER_SERVICE;

/**
 * @author Lody
 * @see Handler.Callback
 */
public class HCallbackStub implements Handler.Callback, IInjector {
    private static final int CREATE_SERVICE = ActivityThread.H.CREATE_SERVICE.get();
    private static final int EXECUTE_TRANSACTION = ActivityThread.H.EXECUTE_TRANSACTION.get();
    private static final int SCHEDULE_CRASH =
        ActivityThread.H.SCHEDULE_CRASH != null ? ActivityThread.H.SCHEDULE_CRASH.get() : -1;
    private static final int LAUNCH_ACTIVITY =
        ActivityThread.H.LAUNCH_ACTIVITY != null ? ActivityThread.H.LAUNCH_ACTIVITY.get() : -1;
    private static final int ENTER_ANIMATION_COMPLETE = ActivityThread.H.ENTER_ANIMATION_COMPLETE.get();
    private static final int ENABLE_JIT = ActivityThread.H.ENABLE_JIT.get();


    private static final String TAG = HCallbackStub.class.getSimpleName();
    private static final HCallbackStub sCallback = new HCallbackStub();

    private boolean mCalling = false;

    private BsGamePadService mBsGamePadService = null;

    private Handler.Callback otherCallback;

    private HCallbackStub() {
    }

    public static HCallbackStub getDefault() {
        return sCallback;
    }

    private static Handler getH() {
        return ActivityThread.mH.get(VirtualCore.mainThread());
    }

    private static Handler.Callback getHCallback() {
        try {
            Handler handler = getH();
            return mirror.android.os.Handler.mCallback.get(handler);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean handleMessage(Message msg) {
        Log.d(TAG, "msg = " + msg.what);
        if (!mCalling) {
            mCalling = true;
            try {
                if (EXECUTE_TRANSACTION == msg.what){
                    if (!handleExecuteTransaction(msg)){
                        return true;
                    }
                } else if (LAUNCH_ACTIVITY == msg.what) {
                    if (!handleLaunchActivity(msg)) {
                        return true;
                    }
                } else if (CREATE_SERVICE == msg.what) {
                    if (!VClientImpl.get().isBound()) {
                        ServiceInfo info = Reflect.on(msg.obj).get("info");
                        VClientImpl.get().bindApplication(info.packageName, info.processName);
                    }
                } else if (SCHEDULE_CRASH == msg.what) {
                    // to avoid the exception send from System.
                    return true;
                } else if (ENTER_ANIMATION_COMPLETE == msg.what) {
                    if (mBsGamePadService == null) {
                        String service = BsGamePadService.getServiceType(VirtualCore.get().getContext());
                        switch (service){
                            case BLACK_SHARK_HOOK_NATIVE_SERVICE:
                                mBsGamePadService = new BsGamePadHookNative(VirtualCore.get().getContext());
                                break;
                            case BLACK_SHARK_PROXY_MAKER_SERVICE:
                                mBsGamePadService = new BsGamePadProxyMaker(VirtualCore.get().getContext());
                                break;
                            default:
                                return false;
                        }
                    }

                    if (mBsGamePadService != null) {
                        mBsGamePadService.setToken((IBinder) msg.obj);
                        mBsGamePadService.loadConfig();
                    }
                } else if (ENABLE_JIT == msg.what) {
                    if (mBsGamePadService != null) {
                        try {
                            mBsGamePadService.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
//                else if (STOP_ACTIVITY_HIDE == msg.what){
//                    if (mBsGamePadService != null){
//                        mBsGamePadService.stop();
//                    }
//                }

                if (otherCallback != null) {
                    boolean desired = otherCallback.handleMessage(msg);
                    mCalling = false;
                    return desired;
                } else {
                    mCalling = false;
                }
            } finally {
                mCalling = false;
            }
        }
        return false;
    }

    private boolean handleExecuteTransaction(Message msg){
        Object r = msg.obj;
        List ClientTransactionItemList = ClientTransaction.getCallbacks.call(r);
        if (ClientTransactionItemList == null ||
            ClientTransactionItemList.size() != 1 ||
            !ClientTransactionItemList.get(0).getClass().equals(LaunchActivityItem.TYPE)){
            return true;
        }

        Object ClientTransactionItem = ClientTransactionItemList.get(0);
        StubActivityRecord activityRecord =
                new StubActivityRecord(LaunchActivityItem.mIntent.get(ClientTransactionItem));
        if (activityRecord.intent == null){
            return true;
        }

        Intent localIntent = activityRecord.intent;
        ComponentName localComponentName = activityRecord.caller;
        IBinder localIBinder = ClientTransaction.getActivityToken.call(r);
        ActivityInfo localActivityInfo = activityRecord.info;
        if (VClientImpl.get().getToken() == null) {
            InstalledAppInfo installedAppInfo =
                    VirtualCore.get().getInstalledAppInfo(localActivityInfo.packageName, 0);
            if(installedAppInfo == null){
                return true;
            }
            VActivityManager.get().processRestarted(localActivityInfo.packageName,
                    localActivityInfo.processName, activityRecord.userId);
            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
            return false;
        }

        if (!VClientImpl.get().isBound()) {
            VClientImpl.get().bindApplication(localActivityInfo.packageName, localActivityInfo.processName);
            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
            return false;
        }

        int taskId = IActivityManager.getTaskForActivity.call(
            ActivityManagerNative.getDefault.call(),
            localIBinder,
            false
        );

        VActivityManager.get().onActivityCreate(ComponentUtils.toComponentName(localActivityInfo),
            localComponentName, localIBinder, localActivityInfo, localIntent,
            ComponentUtils.getTaskAffinity(localActivityInfo), taskId, localActivityInfo.launchMode,
            localActivityInfo.flags);
        ClassLoader appClassLoader = VClientImpl.get().getClassLoader(localActivityInfo.applicationInfo);
        localIntent.setExtrasClassLoader(appClassLoader);
        ActivityThread.ActivityClientRecord.intent.set(r, localIntent);
        ActivityThread.ActivityClientRecord.activityInfo.set(r, localActivityInfo);
        LaunchActivityItem.mIntent.set(ClientTransactionItem, localIntent);
        LaunchActivityItem.mInfo.set(ClientTransactionItem, localActivityInfo);
        return true;
    }

    private boolean handleLaunchActivity(Message msg) {
        Object r = msg.obj;
        Intent stubIntent = ActivityThread.ActivityClientRecord.intent.get(r);
        StubActivityRecord saveInstance = new StubActivityRecord(stubIntent);
        if (saveInstance.intent == null) {
            return true;
        }
        Intent intent = saveInstance.intent;
        ComponentName caller = saveInstance.caller;
        IBinder token = ActivityThread.ActivityClientRecord.token.get(r);
        ActivityInfo info = saveInstance.info;
        if (VClientImpl.get().getToken() == null) {
            InstalledAppInfo installedAppInfo = VirtualCore.get().getInstalledAppInfo(info.packageName, 0);
            if(installedAppInfo == null){
                return true;
            }
            VActivityManager.get().processRestarted(info.packageName, info.processName, saveInstance.userId);
            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
            return false;
        }
        if (!VClientImpl.get().isBound()) {
            VClientImpl.get().bindApplication(info.packageName, info.processName);
            getH().sendMessageAtFrontOfQueue(Message.obtain(msg));
            return false;
        }
        int taskId = IActivityManager.getTaskForActivity.call(
            ActivityManagerNative.getDefault.call(),
            token,
            false
        );
        VActivityManager.get().onActivityCreate(ComponentUtils.toComponentName(info), caller, token,
            info, intent, ComponentUtils.getTaskAffinity(info), taskId, info.launchMode, info.flags);
        ClassLoader appClassLoader = VClientImpl.get().getClassLoader(info.applicationInfo);
        intent.setExtrasClassLoader(appClassLoader);
        ActivityThread.ActivityClientRecord.intent.set(r, intent);
        ActivityThread.ActivityClientRecord.activityInfo.set(r, info);
        return true;
    }

    @Override
    public void inject() throws Throwable {
        otherCallback = getHCallback();
        mirror.android.os.Handler.mCallback.set(getH(), this);
    }

    @Override
    public boolean isEnvBad() {
        Handler.Callback callback = getHCallback();
        boolean envBad = callback != this;
        if (callback != null && envBad) {
            VLog.d(TAG, "HCallback has bad, other callback = " + callback);
        }
        return envBad;
    }
}
