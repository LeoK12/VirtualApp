package mirror.android.app.servertransaction;

import android.os.IBinder;
import java.util.List;
import mirror.RefClass;
import mirror.RefMethod;

public class ClientTransaction {
    public static Class<?> TYPE = RefClass.load(ClientTransaction.class, "android.app.servertransaction.ClientTransaction");
    public static RefMethod<IBinder> getActivityToken;
    public static RefMethod<List<?>> getCallbacks;
}
