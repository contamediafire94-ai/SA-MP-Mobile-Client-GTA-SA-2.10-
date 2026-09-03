package com.russia.launcher.storage;

import static com.russia.launcher.config.Config.NATIVE_SETTINGS_FILE_PATH;

import android.content.Context;

import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;

public class NativeStorage {

    private static final String CLIENT_SECTION_NAME = "client";
    private static final String GUI_SECTION_NAME = "gui";
    private static final String DEFAULT_FONT = "visby-round-cf-extra-bold.ttf";

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

    /**
     * Garante que SAMP/settings.ini exista.
     * Nunca lança RuntimeException: em caso de erro, retorna null.
     */
    private static File ensureSettingsFile(Context context) {
        File settingsFile = getSettingsFile(context);

        if (settingsFile == null) {
            return null;
        }

        try {
            File parent = settingsFile.getParentFile();

            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return null;
            }

            if (!settingsFile.exists() && !settingsFile.createNewFile()) {
                return null;
            }

            /*
             * A base nativa espera [gui] Font.
             * Se isso não existir, CSettings pode receber nullptr e crashar.
             */
            Wini wini = new Wini(settingsFile);

            String font = wini.get(GUI_SECTION_NAME, "Font");
            if (font == null || font.trim().isEmpty()) {
                wini.put(GUI_SECTION_NAME, "Font", DEFAULT_FONT);
                wini.store();
            }

            return settingsFile;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void addClientProperty(String propertyName, String value, Context context) {
        File settingsFile = ensureSettingsFile(context);

        if (settingsFile == null || propertyName == null) {
            return;
        }

        try {
            Wini wini = new Wini(settingsFile);
            wini.put(
                    CLIENT_SECTION_NAME,
                    propertyName,
                    value == null ? "" : value
            );

            // Mantém a fonte obrigatória mesmo após alterações no arquivo.
            String font = wini.get(GUI_SECTION_NAME, "Font");
            if (font == null || font.trim().isEmpty()) {
                wini.put(GUI_SECTION_NAME, "Font", DEFAULT_FONT);
            }

            wini.store();
        } catch (Exception ignored) {
            // Falha de armazenamento não deve derrubar o launcher.
        }
    }

    public static String getClientProperty(String property, Context context) {
        File settingsFile = ensureSettingsFile(context);

        if (settingsFile == null || property == null) {
            return null;
        }

        try {
            Wini wini = new Wini(settingsFile);
            return wini.get(CLIENT_SECTION_NAME, property);
        } catch (Exception ignored) {
            return null;
        }
    }
}
