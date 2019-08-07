package mirror.android.view;

import mirror.MethodParams;
import mirror.RefClass;
import mirror.RefMethod;
import mirror.RefStaticInt;

public class Choreographer {
    public static Class<?> TYPE = RefClass.load(Choreographer.class, "android.view.Choreographer");
    public static RefStaticInt CALLBACK_INPUT;
    public static RefMethod<Long> getFrameTimeNanos;
    @MethodParams({int.class, Runnable.class, Object.class})
    public static RefMethod<Void> postCallback;
    @MethodParams({int.class, Runnable.class, Object.class})
    public static RefMethod<Void> removeCallbacks;
}
