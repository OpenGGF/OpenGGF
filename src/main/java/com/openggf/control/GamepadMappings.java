package com.openggf.control;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * SDL-format gamepad mapping table used to recognise controllers that GLFW's compiled-in
 * table does not know.
 *
 * <p>GLFW matches a mapping only on the device's exact 128-bit SDL GUID, which includes the
 * controller's firmware version. A firmware update therefore turns a known controller into
 * an unmapped joystick, and {@code glfwJoystickIsGamepad} returns false until a mapping for
 * the new GUID is supplied. The bundled table is SDL_GameControllerDB (zlib licence, see
 * {@code control/gamecontrollerdb.LICENSE.txt}); {@link #fallbackFor} covers firmware
 * versions newer than that snapshot by borrowing the mapping of the same bus, vendor and
 * product with the nearest version.
 */
public final class GamepadMappings {

    static final String BUNDLED_RESOURCE = "/control/gamecontrollerdb.txt";
    /** SDL's environment variable for extra mappings; honoured the same way SDL does. */
    static final String ENVIRONMENT_VARIABLE = "SDL_GAMECONTROLLERCONFIG";

    private static final int GUID_HEX_LENGTH = 32;

    private GamepadMappings() {
    }

    /** The bundled table, as one newline-separated SDL mapping document. */
    public static String bundled() {
        try (InputStream in = GamepadMappings.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing gamepad mapping resource " + BUNDLED_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read " + BUNDLED_RESOURCE, e);
        }
    }

    /** The GLFW/SDL platform name for the running OS, as used by the {@code platform:} field. */
    public static String currentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "Windows";
        if (os.contains("mac") || os.contains("darwin")) return "Mac OS X";
        return "Linux";
    }

    /**
     * Returns a mapping line for {@code guid} built from the table entry for the same bus,
     * vendor and product on {@code platform} whose firmware version is nearest, re-keyed to
     * {@code guid}. Bytes 2-3 (the name CRC that newer SDL versions write and GLFW leaves
     * zero) and bytes 14-15 (driver data) are ignored.
     */
    public static Optional<String> fallbackFor(String guid, String platform, String table) {
        if (guid == null || guid.length() != GUID_HEX_LENGTH || !isHex(guid)) return Optional.empty();
        String wantedDevice = deviceKey(guid);
        int wantedVersion = version(guid);
        String platformField = "platform:" + platform + ",";
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestVersion = -1;
        for (String raw : table.split("\n")) {
            String line = raw.strip();
            int comma = line.indexOf(',');
            if (comma != GUID_HEX_LENGTH) continue;
            String candidateGuid = line.substring(0, comma);
            if (!isHex(candidateGuid) || !deviceKey(candidateGuid).equalsIgnoreCase(wantedDevice)) continue;
            String body = line.substring(comma + 1);
            if (!(body.endsWith(",") ? body : body + ",").contains(platformField)) continue;
            int candidateVersion = version(candidateGuid);
            int distance = Math.abs(candidateVersion - wantedVersion);
            if (distance < bestDistance || distance == bestDistance && candidateVersion > bestVersion) {
                best = body;
                bestDistance = distance;
                bestVersion = candidateVersion;
            }
        }
        return best == null ? Optional.empty() : Optional.of(guid.toLowerCase(Locale.ROOT) + "," + best);
    }

    /** Bus (bytes 0-1), vendor (4-5) and product (8-9), with their zero padding. */
    private static String deviceKey(String guid) {
        return guid.substring(0, 4) + guid.substring(8, 24);
    }

    /** Firmware version, bytes 12-13 little-endian. */
    private static int version(String guid) {
        int low = Integer.parseInt(guid.substring(24, 26), 16);
        int high = Integer.parseInt(guid.substring(26, 28), 16);
        return high << 8 | low;
    }

    private static boolean isHex(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) return false;
        }
        return true;
    }
}
