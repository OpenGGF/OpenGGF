package com.openggf.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Opens a fixed https address in the player's default browser through the platform's own opener
 * ({@code xdg-open}, {@code open} or the Windows URL handler). AWT's {@code Desktop} is avoided: it
 * is absent from native packages and contends with GLFW for the display on Linux.
 */
public final class ExternalBrowser {
    private static final Logger LOGGER = Logger.getLogger(ExternalBrowser.class.getName());

    /** Starts the platform opener; a test substitutes one that records the command. */
    @FunctionalInterface
    public interface Launcher { void start(List<String> command) throws IOException; }

    public static final Launcher SYSTEM = command -> new ProcessBuilder(command)
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();

    private ExternalBrowser() { }

    /**
     * Opens {@code address}, written as shown to players ({@code openggf.com/discord}), over https.
     * Returns false, after logging, when the address is not a plain web address or no opener started.
     */
    public static boolean open(String address, Launcher launcher) {
        Objects.requireNonNull(launcher, "launcher");
        URI uri;
        try {
            uri = new URI("https://" + Objects.requireNonNull(address, "address"));
        } catch (URISyntaxException invalid) {
            LOGGER.warning("Not a web address: " + address);
            return false;
        }
        if (uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            LOGGER.warning("Not a plain web address: " + address);
            return false;
        }
        try {
            launcher.start(command(uri.toASCIIString(), System.getProperty("os.name", "")));
            return true;
        } catch (IOException failure) {
            LOGGER.warning("Could not open " + uri + " in a browser: " + failure.getMessage());
            return false;
        }
    }

    static List<String> command(String url, String osName) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) return List.of("rundll32", "url.dll,FileProtocolHandler", url);
        if (os.contains("mac")) return List.of("open", url);
        return List.of("xdg-open", url);
    }
}
