package com.russia.launcher.storage;

import static com.russia.launcher.config.Config.NATIVE_SETTINGS_FILE_PATH;

import android.content.Context;
import android.content.SharedPreferences;

import org.ini4j.Wini;

import java.io.File;

public class NativeStorage {

    private static final String CLIENT_SECTION_NAME = "client";
    private static final String GUI_SECTION_NAME = "gui";
    private static final String DEFAULT_FONT = "visby-round-cf-extra-bold.ttf";

    // Fallback interno do launcher. Funciona mesmo quando o Android bloqueia
    // escrita no settings.ini dentro de Android/data.
    private static final String PREFS_NAME = "launcher_native_storage";

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static File getSettingsFile(Context context) {
        if (context == null) {
            return null;
        }

        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            return null;
        }

        return new File(externalDir.getAbsolutePath() + NATIVE_SETTINGS_FILE_PATH);
    }

    private static File ensureSettingsFile(Context context) {
        File settingsFile = getSettingsFile(context);

        if (settingsFile == null) {
            return null;
        }

        try {
            File parent = settingsFile.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!settingsFile.exists()) {
                settingsFile.createNewFile();
            }

            return settingsFile.exists() ? settingsFile : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void ensureGuiFont(Wini wini) {
        try {
            String font = wini.get(GUI_SECTION_NAME, "Font");
            if (font == null || font.trim().isEmpty()) {
                wini.put(GUI_SECTION_NAME, "Font", DEFAULT_FONT);
            }
        } catch (Exception ignored) {
        }
    }

    public static void addClientProperty(String propertyName, String value, Context context) {
        if (context == null || propertyName == null) {
            return;
        }

        String safeValue = value == null ? "" : value;

        // 1) Salva primeiro no armazenamento interno do launcher.
        // Isso garante que o nick apareça na interface e passe pela validação.
        try {
            SharedPreferences prefs = getPrefs(context);
            if (prefs != null) {
                prefs.edit()
                        .putString(propertyName, safeValue)
                        .apply();
            }
        } catch (Exception ignored) {
        }

        // 2) Tenta espelhar também no SAMP/settings.ini.
        // Se o Android negar permissão, o launcher continua funcionando.
        try {
            File settingsFile = ensureSettingsFile(context);
            if (settingsFile == null) {
                return;
            }

            Wini wini = new Wini(settingsFile);
            wini.put(CLIENT_SECTION_NAME, propertyName, safeValue);
            ensureGuiFont(wini);
            wini.store();
        } catch (Exception ignored) {
        }
    }

    public static String getClientProperty(String property, Context context) {
        if (context == null || property == null) {
            return null;
        }

        // 1) Preferir o valor salvo internamente pelo launcher.
        try {
            SharedPreferences prefs = getPrefs(context);
            if (prefs != null && prefs.contains(property)) {
                return prefs.getString(property, null);
            }
        } catch (Exception ignored) {
        }

        // 2) Se ainda não existir no fallback, tenta ler o settings.ini.
        try {
            File settingsFile = getSettingsFile(context);

            if (settingsFile == null || !settingsFile.exists()) {
                return null;
            }

            Wini wini = new Wini(settingsFile);
            String value = wini.get(CLIENT_SECTION_NAME, property);

            // Cacheia no fallback interno para próximas leituras.
            if (value != null) {
                SharedPreferences prefs = getPrefs(context);
                if (prefs != null) {
                    prefs.edit()
                            .putString(property, value)
                            .apply();
                }
            }

            return value;
        } catch (Exception ignored) {
            return null;
        }
    }
}
