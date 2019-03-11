package amirz.nightcamera;

import android.util.Log;

import java.lang.reflect.Method;

public final class SystemProperties {
    private static final String TAG = "SystemProperties";
    private Method mGetIntMethod;
    private Method mGetLongMethod;
    private Method mGetStringMethod;
    private Method mSetStringMethod;

    public static final SystemProperties INSTANCE = new SystemProperties();

    private SystemProperties() {
        Method tempGetString = null;
        Method tempSetString = null;
        Method tempGetInt = null;
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            tempGetString = systemProperties.getMethod("get", String.class, String.class);
            tempSetString = systemProperties.getMethod("set", String.class, String.class);
            tempGetInt = systemProperties.getMethod("getInt", String.class, Integer.TYPE);
            Method tempGetLong = systemProperties.getMethod("getLong", String.class, Long.TYPE);
            this.mGetStringMethod = tempGetString;
            this.mSetStringMethod = tempSetString;
            this.mGetIntMethod = tempGetInt;
            this.mGetLongMethod = tempGetLong;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to reflect SystemProperties.", e);
        } catch (Throwable th) {
            this.mGetStringMethod = tempGetString;
            this.mSetStringMethod = tempSetString;
            this.mGetIntMethod = tempGetInt;
            this.mGetLongMethod = null;
        }
    }

    public String getString(String key, String defaultValue) {
        try {
            String value = (String) this.mGetStringMethod.invoke(null, new Object[]{key, defaultValue});
            if (defaultValue == null && "".equals(value)) {
                value = null;
            }
            return value;
        } catch (Exception e) {
            Log.e(TAG, "get error", e);
            return defaultValue;
        }
    }

    public boolean setString(String key, String value) {
        try {
            this.mSetStringMethod.invoke(null, key, value);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Could not set SystemProperty key: " + key + " to value: " + value, e);
            return false;
        }
    }

    private boolean getBooleanValue(String key, boolean defaultValue) {
        String value = getString(key, null);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        if ("0".equals(value)) {
            return false;
        }
        if ("1".equals(value)) {
            return true;
        }
        return defaultValue;
    }

    private boolean getKeyEquals(String key, String valueToMatch) {
        String value = getString(key, null);
        if (value == null || value.isEmpty() || valueToMatch == null || valueToMatch.isEmpty()) {
            return false;
        }
        return valueToMatch.equals(value);
    }

    private int getIntValue(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean enableNpfDebugMost() {
        return getBooleanValue("persist.npf.debug", false);
    }

    public boolean enableNpfHwNoiseReduction() {
        return getBooleanValue("persist.npf.hw_noise_reduction", false);
    }

    public boolean forceNpfConfig() {
        return getKeyEquals("persist.camera.cam_component", "npf");
    }

    public boolean forceNpfTuningConfig() {
        return getKeyEquals("persist.camera.cam_component", "npf_tuning");
    }

}
