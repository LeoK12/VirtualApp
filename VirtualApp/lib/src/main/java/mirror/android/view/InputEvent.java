package mirror.android.view;

import mirror.RefClass;
import mirror.RefObject;

public class InputEvent {
    public static Class<?> TYPE = RefClass.load(InputEvent.class, "android.view.InputEvent");
    public static RefObject<Integer> mSeq;
}
