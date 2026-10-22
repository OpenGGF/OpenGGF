package openggf.timeattack;

import com.openggf.mods.ModStorage;
import com.openggf.mods.run.CharacterLooks;
import com.openggf.net.identity.DisplayNames;
import com.openggf.net.identity.PlayerAppearance;
import com.openggf.net.protocol.ReadyRules;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The Time Attack mod's own settings (the engine has no {@code timeAttack.*} keys): the port for
 * player-hosted rooms, the last LAN invite, the multiplayer display name and look, the master
 * server URL and its development-only trust-all switch, the multiplayer minimap and the ready
 * rule for rooms this player owns.
 *
 * <p>The display name is stored canonical ({@link DisplayNames#sanitize}), as hosts require. The
 * look is a base colour ({@code lookBase}, the name and ghost colour; empty for the stock look)
 * and, per {@code game:character}, an optional custom palette ({@code lookPalette.s3k:sonic}).
 * Without a custom palette a character's body ramp follows the base colour
 * ({@link CharacterLooks#withBaseColour}); see {@link #appearanceFor}.
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
    /** Most custom palettes kept, so a hand-edited file cannot grow the settings without bound. */
    public static final int MAX_CUSTOM_PALETTES = 64;

    private int retryKey = com.openggf.mods.scene.SceneKeys.R;
    private int hostPort = DEFAULT_HOST_PORT;
    private String lastJoinAddress = "";
    private String displayName = "";
    private String masterUrl = "";
    private boolean masterTrustInsecure;
    private boolean minimap = true;
    private ReadyRules readyRule = ReadyRules.DEFAULT_BY_SIZE;
    private int lookBase = PlayerAppearance.NO_BASE_COLOUR;
    private final Map<String, List<PlayerAppearance.Entry>> customPalettes = new TreeMap<>();
    private static final int RETRY_TICKS = 60;
    private String publishedEncoding = encode();
    private boolean publicationConfirmed;
    private boolean pendingPublication;
    private int retryTicks;
    private String publicationNotice = "";

    /** Reads the stored settings, or the defaults when none are stored or the store is null. */
    public static TimeAttackSettings load(ModStorage storage) {
        if (storage == null) {
            return new TimeAttackSettings();
        }
        return storage.read(FILE_NAME).map(TimeAttackSettings::parse).orElseGet(TimeAttackSettings::new);
    }

    /** Checked publication: refusals and nonfatal storage faults retain the current accepted values. */
    public boolean save(ModStorage storage) {
        String candidate = encode();
        boolean saved;
        try {
            saved = storage != null && storage.write(FILE_NAME, candidate);
        } catch (RuntimeException failure) {
            saved = false;
        }
        if (saved) {
            publishedEncoding = candidate;
            publicationConfirmed = true;
            pendingPublication = false;
            publicationNotice = "Saved";
        } else {
            pendingPublication = !publicationConfirmed || !candidate.equals(publishedEncoding);
            retryTicks = RETRY_TICKS;
            publicationNotice = pendingPublication ? "Changes active - save pending" : "Save not confirmed";
        }
        return saved;
    }

    /** Pumped by the owning session even while a settings/browser/lobby page is open. */
    public boolean retryPending(ModStorage storage) {
        return pendingPublication() && --retryTicks <= 0 && save(storage);
    }

    /** An owner exit/resume flush bypasses the retry delay, retaining failure state on refusal. */
    public boolean flushPending(ModStorage storage) {
        return !pendingPublication() || save(storage);
    }

    public boolean pendingPublication() {
        // Reverting to the confirmed values cancels an obsolete failed snapshot.
        if (pendingPublication && publicationConfirmed && encode().equals(publishedEncoding)) {
            pendingPublication = false;
            publicationNotice = "";
        }
        return pendingPublication;
    }

    public String publicationNotice() {
        pendingPublication();
        return publicationNotice;
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
                case "retryKey" -> {
                    try { settings.setRetryKey(RetryKey.parse(value)); }
                    catch (IllegalArgumentException ignored) { }
                }
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
                case "readyRule" -> {
                    ReadyRules rule = ReadyRules.parse(value);
                    if (rule != null) settings.readyRule = rule;
                }
                case "lookBase" -> {
                    try { settings.setLookBase(value.isEmpty() ? PlayerAppearance.NO_BASE_COLOUR
                            : PlayerAppearance.decode(value).baseColour()); }
                    catch (IllegalArgumentException ignored) { }
                }
                default -> {
                    if (key.startsWith(PALETTE_PREFIX)) {
                        settings.parsePalette(key.substring(PALETTE_PREFIX.length()), value);
                    }
                }
            }
        }
        settings.publishedEncoding = settings.encode();
        settings.publicationConfirmed = true;
        return settings;
    }

    private static final String PALETTE_PREFIX = "lookPalette.";

    private void parsePalette(String key, String value) {
        if (!validKey(key) || customPalettes.size() >= MAX_CUSTOM_PALETTES) return;
        try {
            // Reuse the canonical wire grammar: any base colour, then the stored entries.
            customPalettes.put(key, PlayerAppearance.decode(value.isEmpty() ? "000" : "000." + value).palette());
        } catch (IllegalArgumentException ignored) {
            // an unreadable palette falls back to the preset ramp
        }
    }

    /** The stored form: one {@code key=value} line per setting. */
    public String encode() {
        StringBuilder out = new StringBuilder("# Time Attack settings\n")
                .append("retryKey=").append(retryKey).append('\n')
                .append("hostPort=").append(hostPort).append('\n')
                .append("lastJoinAddress=").append(lastJoinAddress).append('\n')
                .append("displayName=").append(displayName).append('\n')
                .append("masterUrl=").append(masterUrl).append('\n')
                .append("masterTrustInsecure=").append(masterTrustInsecure).append('\n')
                .append("minimap=").append(minimap).append('\n')
                .append("readyRule=").append(readyRule.name()).append('\n')
                .append("lookBase=").append(lookBase == PlayerAppearance.NO_BASE_COLOUR ? ""
                        : new PlayerAppearance(lookBase, List.of()).encode()).append('\n');
        customPalettes.forEach((key, entries) -> {
            String encoded = new PlayerAppearance(0, entries).encode();
            out.append(PALETTE_PREFIX).append(key).append('=')
                    .append(encoded.length() > 3 ? encoded.substring(4) : "").append('\n');
        });
        return out.toString();
    }

    public int retryKey() { return retryKey; }

    public void setRetryKey(int code) {
        if (!RetryKey.supported(code)) throw new IllegalArgumentException("Unsupported retry key: " + code);
        retryKey = code;
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

    /** Stores the canonical form of {@code name} ({@link DisplayNames#sanitize}); empty clears it. */
    public void setDisplayName(String name) {
        displayName = DisplayNames.sanitize(name);
    }

    /** The look's base colour (Mega Drive {@code 0x0BGR}), or {@link PlayerAppearance#NO_BASE_COLOUR} for the stock look. */
    public int lookBase() {
        return lookBase;
    }

    /** @throws IllegalArgumentException unless {@code colour} is a Mega Drive colour or NO_BASE_COLOUR */
    public void setLookBase(int colour) {
        if (colour != PlayerAppearance.NO_BASE_COLOUR) new PlayerAppearance(colour, List.of());
        lookBase = colour;
    }

    /** The custom palette entries for {@code gameId}'s {@code character}, or null when it follows the base colour. */
    public List<PlayerAppearance.Entry> customPalette(String gameId, String character) {
        return character == null ? null : customPalettes.get(key(gameId, character));
    }

    /**
     * Sets or (with null) clears the custom palette of {@code gameId}'s {@code character}.
     *
     * @param entries overridden entries, strictly ascending by index
     */
    public void setCustomPalette(String gameId, String character, List<PlayerAppearance.Entry> entries) {
        String key = key(gameId, character);
        if (!validKey(key)) throw new IllegalArgumentException("bad game/character: " + key);
        if (entries == null) {
            customPalettes.remove(key);
            return;
        }
        List<PlayerAppearance.Entry> canonical = new PlayerAppearance(0, entries).palette();
        if (!customPalettes.containsKey(key) && customPalettes.size() >= MAX_CUSTOM_PALETTES) {
            throw new IllegalStateException("too many custom palettes");
        }
        customPalettes.put(key, canonical);
    }

    /**
     * The canonical look to announce when racing {@code character} in {@code gameId}: stock when
     * no base colour is set; otherwise the base colour with the character's custom palette, or
     * with its body ramp following the base colour.
     */
    public PlayerAppearance appearanceFor(String gameId, String character) {
        if (lookBase == PlayerAppearance.NO_BASE_COLOUR) return PlayerAppearance.STOCK;
        if (gameId == null || character == null) return new PlayerAppearance(lookBase, List.of());
        List<PlayerAppearance.Entry> custom = customPalette(gameId, character);
        return custom != null ? new PlayerAppearance(lookBase, custom)
                : CharacterLooks.withBaseColour(gameId, character, lookBase);
    }

    private static String key(String gameId, String character) {
        return Objects.requireNonNull(gameId, "gameId").toLowerCase(Locale.ROOT) + ":"
                + Objects.requireNonNull(character, "character").toLowerCase(Locale.ROOT);
    }

    private static boolean validKey(String key) {
        return key.length() <= 40 && key.matches("[a-z0-9]+:[a-z0-9_]+");
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

    /** The ready rule this player last chose for rooms they own. */
    public ReadyRules readyRule() {
        return readyRule;
    }

    public void setReadyRule(ReadyRules rule) {
        readyRule = Objects.requireNonNull(rule, "rule");
    }

    private static String singleLine(String value, int maxLength) {
        String text = Objects.requireNonNullElse(value, "").replace('\r', ' ').replace('\n', ' ').strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
