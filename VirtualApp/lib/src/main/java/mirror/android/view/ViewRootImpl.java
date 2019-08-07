package mirror.android.view;

import android.content.Context;
import android.view.InputEvent;

import mirror.MethodParams;
import mirror.RefClass;
import mirror.RefMethod;
import mirror.RefObject;

public class ViewRootImpl {
    public static Class<?> TYPE = RefClass.load(ViewRootImpl.class, "android.view.ViewRootImpl");
    @MethodParams({InputEvent.class, InputEventReceiver.class, int.class, boolean.class})
    public static RefMethod<Void> enqueueInputEvent;
    public static RefObject<Context> mContext;
    public static RefObject<Object> mInputChannel;
    public static RefObject<Object> mInputEventReceiver;
    public static RefObject<View> mView;

    public static class WindowInputEventReceiver {
        public static Class<?> TYPE = RefClass.load(WindowInputEventReceiver.class, "android.view.ViewRootImpl$WindowInputEventReceiver");
        public static RefMethod<Void> dispose;
        @MethodParams({InputEvent.class, int.class})
        public static RefMethod<Void> onInputEvent;
    }
}
