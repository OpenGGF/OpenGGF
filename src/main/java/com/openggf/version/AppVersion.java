package com.openggf.version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {
    private static final String RESOURCE_PATH = "/version.properties";
    private static final String VERSION_PROPERTY = "app.version";
    private static final String BASE_VERSION_PROPERTY = "app.baseVersion";
    private static final String COMMIT_PROPERTY = "app.commit";
    private static final String DIRTY_PROPERTY = "app.dirty";
    private static final String DEFAULT_VERSION = "dev";
    private static final BuildIdentity IDENTITY = loadIdentity();

    private AppVersion() {
    }

    public static String get() {
        return identity().displayVersion();
    }

    public static BuildIdentity identity() {
        return IDENTITY;
    }

    private static BuildIdentity loadIdentity() {
        return loadIdentity(AppVersion.class.getResourceAsStream(RESOURCE_PATH));
    }

    private static String loadVersion(InputStream input) {
        return loadIdentity(input).displayVersion();
    }

    static BuildIdentity loadIdentity(InputStream input) {
        try (InputStream stream = input) {
            if (stream == null) {
                return defaultIdentity();
            }
            Properties properties = new Properties();
            properties.load(stream);
            String baseVersion = firstNonBlank(
                    properties.getProperty(BASE_VERSION_PROPERTY),
                    properties.getProperty(VERSION_PROPERTY),
                    DEFAULT_VERSION);
            String commit = trim(properties.getProperty(COMMIT_PROPERTY));
            boolean dirty = Boolean.parseBoolean(trim(properties.getProperty(DIRTY_PROPERTY)));
            return new BuildIdentity(baseVersion, commit, dirty);
        } catch (IOException | IllegalArgumentException e) {
            return defaultIdentity();
        }
    }

    private static BuildIdentity defaultIdentity() {
        return new BuildIdentity(DEFAULT_VERSION, "", false);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trim(value);
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return DEFAULT_VERSION;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
