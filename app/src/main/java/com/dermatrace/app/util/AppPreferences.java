package com.dermatrace.app.util;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppPreferences {
    private static final String PREFS = "dermatrace_prefs";
    private AppPreferences() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isDebugImportEnabled(Context c) { return p(c).getBoolean("debug_import", true); }
    public static void setDebugImportEnabled(Context c, boolean v) { p(c).edit().putBoolean("debug_import", v).apply(); }

    public static int getDefaultFollowUpDays(Context c) { return p(c).getInt("follow_days", 30); }
    public static void setDefaultFollowUpDays(Context c, int days) { p(c).edit().putInt("follow_days", Math.min(365, Math.max(1, days))).apply(); }

    public static String getClinicianName(Context c) { return p(c).getString("clinician_name", ""); }
    public static String getClinicianPhone(Context c) { return p(c).getString("clinician_phone", ""); }
    public static String getClinicianEmail(Context c) { return p(c).getString("clinician_email", ""); }

    public static void saveClinician(Context c, String name, String phone, String email) {
        p(c).edit()
                .putString("clinician_name", safe(name))
                .putString("clinician_phone", safe(phone))
                .putString("clinician_email", safe(email))
                .apply();
    }


    public static boolean isOllamaEnabled(Context c) { return p(c).getBoolean("ollama_enabled", false); }
    public static void setOllamaEnabled(Context c, boolean v) { p(c).edit().putBoolean("ollama_enabled", v).apply(); }

    public static String getOllamaUrl(Context c) {
        return p(c).getString("ollama_url", "http://127.0.0.1:11434");
    }

    public static String getOllamaModel(Context c) {
        return p(c).getString("ollama_model", "llama3.2");
    }

    public static void saveOllama(Context c, boolean enabled, String url, String model) {
        String safeUrl = safe(url);
        if (safeUrl.isEmpty()) safeUrl = "http://127.0.0.1:11434";
        while (safeUrl.endsWith("/")) safeUrl = safeUrl.substring(0, safeUrl.length() - 1);
        String safeModel = safe(model);
        if (safeModel.isEmpty()) safeModel = "llama3.2";
        p(c).edit()
                .putBoolean("ollama_enabled", enabled)
                .putString("ollama_url", safeUrl)
                .putString("ollama_model", safeModel)
                .apply();
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
}
