package mirror.android.webkit;

import mirror.RefClass;
import mirror.RefMethod;

/**
 * @author CodeHz
 */

public class IWebViewUpdateService {
    public static Class<?> TYPE = RefClass.load(IWebViewUpdateService.class, "android.webkit.WebViewUpdateService");

    public static RefMethod<String> getCurrentWebViewPackageName;
}
