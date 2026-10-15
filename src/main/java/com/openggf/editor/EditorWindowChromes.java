package com.openggf.editor;

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Finds the editor window chrome on the class path, if any. */
public final class EditorWindowChromes {
    private static final Logger LOGGER = Logger.getLogger(EditorWindowChromes.class.getName());

    private EditorWindowChromes() { }

    /** True when {@code -Dopenggf.editor.ui=classic} asks for the in-game chrome only. */
    public static boolean classicRequested() {
        return "classic".equalsIgnoreCase(System.getProperty("openggf.editor.ui", ""));
    }

    /** The first provider that loads, or null; a broken provider is logged and skipped. */
    public static EditorWindowChrome discover() {
        if (classicRequested()) return null;
        try {
            for (ServiceLoader.Provider<EditorWindowChrome> provider
                    : ServiceLoader.load(EditorWindowChrome.class).stream().toList()) {
                try {
                    return provider.get();
                } catch (java.util.ServiceConfigurationError | RuntimeException | LinkageError failure) {
                    LOGGER.log(Level.WARNING, "Editor window chrome " + provider.type().getName() + " failed to load", failure);
                }
            }
        } catch (java.util.ServiceConfigurationError failure) {
            LOGGER.log(Level.WARNING, "Editor window chrome lookup failed", failure);
        }
        return null;
    }
}
