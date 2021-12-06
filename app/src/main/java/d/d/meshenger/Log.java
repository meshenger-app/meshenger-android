package d.d.meshenger;

/*
 * Wrapper for android.util.Log to disable logging
 */
public class Log {
    private static String contextString(Object context) {
        if (context instanceof String) {
            return (String) context;
        } else {
            return context.getClass().getSimpleName();
        }
    }

    public static void d(Object context, String message) {
        if (BuildConfig.DEBUG) {
            String tag = contextString(context);
            android.util.Log.d(tag, message);
        }
    }
}
