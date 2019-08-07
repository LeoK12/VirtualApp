package mirror.android.view;

import android.view.InputEvent;

import mirror.MethodParams;
import mirror.RefClass;
import mirror.RefMethod;
import mirror.RefObject;

public class InputEventReceiver {
    public static Class<?> TYPE = RefClass.load(InputEventReceiver.class, "android.view.InputEventReceiver");
    public static RefObject<Object> mSeqMap;
    @MethodParams({InputEvent.class, boolean.class})
    public static RefMethod<Void> finishInputEvent;
    @MethodParams({long.class})
    public static RefMethod<Boolean> consumeBatchedInputEvents;

}
