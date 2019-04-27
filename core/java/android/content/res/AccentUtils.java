package android.content.res;

import android.graphics.Color;
import android.os.SystemProperties;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class AccentUtils {
    private static ArrayList<String> accentResources = new ArrayList<>(
            Arrays.asList("user_icon_1",
                    "accent_device_default_700",
                    "accent_device_default_light",
                    "accent_device_default_dark"));

    private static final String ACCENT_COLOR_PROP = "persist.sys.theme.accentcolor";

    private static final String TAG = "AccentUtils";

    static boolean isResourceAccent(String resName) {
        for (String ar : accentResources)
            if (resName.contains(ar))
                return true;
        return false;
    }

    public static int getAccentColor(int defaultColor) {
        try {
            String colorValue = SystemProperties.get(ACCENT_COLOR_PROP, "-1");
            return "-1".equals(colorValue)
                    ? defaultColor
                    : Color.parseColor("#" + colorValue);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set accent: " + e.getMessage() +
                    "\nSetting default: " + defaultColor);
            return defaultColor;
        }
    }
}
