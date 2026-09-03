package com.russia.launcher.storage;

import static com.russia.launcher.config.Config.NATIVE_SETTINGS_FILE_PATH;

import android.content.Context;

import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;

public class NativeStorage {

    private static final String CLIENT_SECTION_NAME = "client";

    private static File getSettingsFile(Context context) {
        File externalDir = context.getExternalFilesDir(null);

        if (externalDir == null) {
            return null;
        }

        return new File(externalDir.getAbsolutePath() + NATIVE_SETTINGS_FILE_PATH);
    }

    public static void addClientProperty(String propertyName, String value, Context context) {
        File settingsFile = getSettingsFile(context);

        if (settingsFile == null || !settingsFile.exists()) {
            return;
        }

        try {
            Wini w = new Wini(settingsFile);
            w.put(CLIENT_SECTION_NAME, propertyName, value);
            w.store();
        } catch (IOException ignored) {
            // Não derruba o launcher caso o Android bloqueie a escrita.
        }
    }

    public static String getClientProperty(String property, Context context) {
        File settingsFile = getSettingsFile(context);

        if (settingsFile == null || !settingsFile.exists()) {
            return null;
        }

        try {
            Wini w = new Wini(settingsFile);
            return w.get(CLIENT_SECTION_NAME, property);
        } catch (IOException ignored) {
            return null;
        }
    }
}
