package openggf.timeattack;

import com.openggf.io.ModAssetRoot;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * This build's multiplayer defaults, from the {@value #RESOURCE} asset that the bundled-mod
 * packager generates into every bundled mod from the build's multiplayer channel. Read once at
 * registration. A development load without the generated asset, or an unreadable one, has no
 * default master (the Settings page then shows "Build default (none)").
 */
final class NetworkDefaults {
    static final String RESOURCE = "network-defaults.properties";
    static final String DEFAULT_MASTER_KEY = "multiplayer.defaultMaster";
    static final int MAX_BYTES = 1024;

    private NetworkDefaults() { }

    /** The build's default master address, or empty. */
    static String defaultMaster(ModAssetRoot assets) {
        byte[] bytes;
        try {
            bytes = assets.readBounded(RESOURCE, MAX_BYTES);
        } catch (IOException | IllegalArgumentException absent) {
            return "";
        }
        return parse(new String(bytes, StandardCharsets.UTF_8));
    }

    static String parse(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException malformed) {
            return "";
        }
        String value = properties.getProperty(DEFAULT_MASTER_KEY, "").strip();
        return value.length() > TimeAttackSettings.MAX_TEXT_LENGTH || value.indexOf('\n') >= 0 ? "" : value;
    }
}
