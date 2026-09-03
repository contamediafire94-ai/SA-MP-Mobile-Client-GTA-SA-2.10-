package com.russia.launcher.storage;

import static com.russia.launcher.config.Config.NATIVE_SETTINGS_FILE_PATH;

import android.content.Context;
import android.widget.Toast;

import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;

public class NativeStorage {

    private static final String CLIENT_SECTION_NAME = "client";

    private static File getSettingsFile(Context context) throws IOException {
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir == null) {
            throw new IOException("External files directory is unavailable");
        }

        File settingsFile = new File(externalDir.getAbsolutePath() + NATIVE_SETTINGS_FILE_PATH);
        File parentDir = settingsFile.getParentFile();

        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create settings directory: " + parentDir.getAbsolutePath());
        }

        if (!settingsFile.exists() && !settingsFile.createNewFile()) {
            throw new IOException("Failed to create settings file: " + settingsFile.getAbsolutePath());
        }

        return settingsFile;
    }

    public static void addClientProperty(String propertyName, String value, Context context) {
        try {
            File settingsFile = getSettingsFile(context);
            Wini w = new Wini(settingsFile);
            w.put(CLIENT_SECTION_NAME, propertyName, value);
            w.store();
        } catch (InvalidFileFormatException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getClientProperty(String property, Context context) {
        try {
            File settingsFile = getSettingsFile(context);
            Wini w = new Wini(settingsFile);
            return w.get(CLIENT_SECTION_NAME, property);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void showMessage(String message, Context context) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
