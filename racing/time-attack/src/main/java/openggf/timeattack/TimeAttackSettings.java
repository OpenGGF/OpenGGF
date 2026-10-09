package openggf.timeattack;

import com.openggf.mods.ModStorage;

import java.util.Objects;

/**
 * The Time Attack mod's own settings (the engine has no {@code timeAttack.*} keys): the port for
 * player-hosted rooms, the last LAN invite, the multiplayer display name, the master server URL
 * and its development-only trust-all switch, and the multiplayer minimap.
 *
 * <p>Stored as UTF-8 {@code key=value} lines in the mod's private storage file
 * {@value #FILE_NAME}; unknown keys and unreadable values fall back to the defaults.
 */
public final class TimeAttackSettings {
    /** Storage file name inside the mod's private storage. */
    public static final String FILE_NAME = "settings.txt";
    public static final int DEFAULT_HOST_PORT = 27888;
    /** Longest accepted text value (a full IPv6 LAN invite fits in 192 characters). */
    public static final int MAX_TEXT_LENGTH = 192;
    public static final int MAX_DISPLAY_NAME_LENGTH = 24;

    private int hostPort = DEFAULT_HOST_PORT;
    private String lastJoinAddress = "";
    private String displayName = "";
    private String masterUrl = "";
    private boolean masterTrustInsecure;
    private boolean minimap = true;

    /** Reads the stored settings, or the defaults when none are stored or the store is null. */
    public static TimeAttackSettings load(ModStorage storage) {
        if (storage == null) {
            return new TimeAttackSettings();
        }
        return parse(storage.read(FILE_NAME).orElse(""));
    }

    /** Writes these settings; false when the store refused the write. */
    public boolean save(ModStorage storage) {
        return storage != null && storage.write(FILE_NAME, encode());
    }

    /** Parses {@link #encode()} output; malformed lines and values keep their defaults. */
    public static TimeAttackSettings parse(String text) {
        TimeAttackSettings settings = new TimeAttackSettings();
        if (text == null) {
            return settings;
        }
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.strip();
            int equals = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            switch (key) {
                case "hostPort" -> {
                    try {
                        settings.setHostPort(Integer.parseInt(value));
                    } catch (IllegalArgumentException ignored) {
                        // keep the default port
                    }
                }
                case "lastJoinAddress" -> settings.setLastJoinAddress(value);
                case "displayName" -> settings.setDisplayName(value);
                case "masterUrl" -> settings.setMasterUrl(value);
                case "masterTrustInsecure" -> settings.masterTrustInsecure = "true".equalsIgnoreCase(value);
                case "minimap" -> settings.minimap = !"false".equalsIgnoreCase(value);
                default -> { }
            }
        }
        return settings;
    }

    /** The stored form: one {@code key=value} line per setting. */
    public String encode() {
        return "# Time Attack settings\n"
                + "hostPort=" + hostPort + "\n"
                + "lastJoinAddress=" + lastJoinAddress + "\n"
                + "displayName=" + displayName + "\n"
                + "masterUrl=" + masterUrl + "\n"
                + "masterTrustInsecure=" + masterTrustInsecure + "\n"
                + "minimap=" + minimap + "\n";
    }

    public int hostPort() {
        return hostPort;
    }

    /** @throws IllegalArgumentException unless {@code port} is 1-65535 */
    public void setHostPort(int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be 1-65535");
        }
        hostPort = port;
    }

    public String lastJoinAddress() {
        return lastJoinAddress;
    }

    public void setLastJoinAddress(String address) {
        lastJoinAddress = singleLine(address, MAX_TEXT_LENGTH);
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String name) {
        displayName = singleLine(name, MAX_DISPLAY_NAME_LENGTH);
    }

    public String masterUrl() {
        return masterUrl;
    }

    public void setMasterUrl(String url) {
        masterUrl = singleLine(url, MAX_TEXT_LENGTH);
    }

    public boolean masterTrustInsecure() {
        return masterTrustInsecure;
    }

    public void setMasterTrustInsecure(boolean trustInsecure) {
        masterTrustInsecure = trustInsecure;
    }

    public boolean minimap() {
        return minimap;
    }

    public void setMinimap(boolean show) {
        minimap = show;
    }

    private static String singleLine(String value, int maxLength) {
        String text = Objects.requireNonNullElse(value, "").replace('\r', ' ').replace('\n', ' ').strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
