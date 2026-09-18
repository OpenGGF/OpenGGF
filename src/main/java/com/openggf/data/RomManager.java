package com.openggf.data;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Singleton manager for ROM access across the engine.
 *
 * Provides centralized ROM lifecycle management:
 * - Opens the ROM once on first access
 * - Provides thread-safe access to ROM data
 * - Closes the ROM on engine shutdown
 *
 * <p>Every ROM the engine opens for itself comes from the
 * {@link RomImageCatalogue}: the game asks for a {@link RomIdentity} and gets
 * back an in-memory {@link Rom} over the catalogue's view, whether that view
 * is one whole file, a window of a lock-on dump, or a composite of several
 * user-supplied images. The catalogue is rebuilt when the ROM configuration
 * keys change or when {@link #reloadCatalogue()} is called.
 *
 * Usage:
 * <pre>
 * Rom rom = GameServices.rom().getRom();
 * byte[] data = rom.readBytes(offset, length);
 * </pre>
 */
public class RomManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(RomManager.class.getName());
    private static final String MISSING_ROM_PREFIX = "ROM file does not exist: ";

    private static RomManager instance;

    private Rom rom;
    private boolean initialized = false;
    private final Map<String, Rom> secondaryRoms = new HashMap<>();
    private RomImageCatalogue catalogue;
    private List<Object> catalogueKey;

    private RomManager() {
    }

    private SonicConfigurationService configService() {
        return GameServices.configuration();
    }

    /**
     * Gets the singleton instance of RomManager.
     */
    public static synchronized RomManager getInstance() {
        if (instance == null) {
            instance = new RomManager();
        }
        return instance;
    }

    /**
     * Gets the ROM instance, opening it if necessary.
     *
     * @return The open ROM instance
     * @throws IOException If the ROM cannot be opened
     */
    public synchronized Rom getRom() throws IOException {
        if (!initialized || rom == null || !rom.isOpen()) {
            openRom();
        }
        return rom;
    }

    /**
     * Injects a pre-opened ROM instance. Useful for tests that open
     * specific ROMs directly rather than relying on config resolution.
     */
    public synchronized void setRom(Rom rom) {
        // Do NOT close the previous ROM here — its lifecycle is managed
        // by whoever created it (e.g., RomCache in tests). Closing it here
        // would invalidate cached ROM instances shared across tests.
        this.rom = rom;
        this.initialized = rom != null && rom.isOpen();
    }

    /**
     * Checks if the ROM is currently open and available.
     */
    public synchronized boolean isRomAvailable() {
        return initialized && rom != null && rom.isOpen();
    }

    /**
     * Opens a secondary ROM by game ID without changing the active game module.
     * Used for cross-game feature donation (e.g., loading S2 sprites while playing S1).
     *
     * @param gameId "s1", "s2", or "s3k"
     * @return The open ROM instance
     * @throws IOException If the ROM cannot be opened
     */
    public synchronized Rom getSecondaryRom(String gameId) throws IOException {
        Rom existing = secondaryRoms.get(gameId);
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        Rom secondaryRom = openCatalogueRom(legacyRomGame(gameId).identity(), true, gameId);
        LOGGER.info("Opened secondary ROM (" + gameId + "): " + secondaryRom.readDomesticName());
        secondaryRoms.put(gameId, secondaryRom);
        return secondaryRom;
    }

    /**
     * The catalogue of user-supplied images, rebuilt when the ROM keys change.
     */
    public synchronized RomImageCatalogue catalogue() {
        SonicConfigurationService configuration = configService();
        List<Object> key = catalogueKey(configuration);
        if (catalogue == null || !key.equals(catalogueKey)) {
            catalogue = RomImageCatalogue.forCurrentWorkingDirectory(configuration);
            catalogueKey = key;
        }
        return catalogue;
    }

    /** Rescans the ROM directory and per-game keys on the next catalogue use. */
    public synchronized void reloadCatalogue() {
        catalogue = null;
        catalogueKey = null;
    }

    /** How the catalogue would serve the logical ROM, or empty when no image contains it. */
    public synchronized Optional<RomImageCatalogue.Resolution> resolveLogicalRom(RomIdentity identity) {
        return catalogue().resolve(identity);
    }

    /** True when some user-supplied image (or composite of images) contains the logical ROM. */
    public synchronized boolean isLogicalRomAvailable(RomIdentity identity) {
        return resolveLogicalRom(identity).isPresent();
    }

    /**
     * Opens a reader over the logical ROM's bytes.
     *
     * @throws IOException when no image contains it, or an image cannot be read
     */
    public synchronized RomByteReader openLogicalRom(RomIdentity identity) throws IOException {
        return catalogue().open(identity);
    }

    /**
     * Opens a logical ROM through the catalogue, keeping the diagnostics the
     * engine and its tests rely on: an explicit key naming a missing file
     * reports that exact configured value, a blank key with nothing in the
     * catalogue reports the unconfigured-ROM message, and an unreadable image
     * reports the configured value it was opened as.
     */
    private Rom openCatalogueRom(RomIdentity identity, boolean secondary, String gameId) throws IOException {
        RomImageCatalogue images = catalogue();
        Optional<RomImageCatalogue.Resolution> resolution = images.resolve(identity);
        if (resolution.isEmpty()) {
            Optional<String> missing = images.explicitMissingValue(identity);
            if (missing.isPresent()) {
                throw new IOException((secondary ? "Failed to open secondary ROM: " : MISSING_ROM_PREFIX) + missing.get());
            }
            // A default hint (s1.gen/s2.gen/s3k.gen) whose file is absent lets the
            // catalogue scan apply; when the scan finds nothing either, report the
            // hinted file as missing exactly as before the catalogue existed, so
            // isConfiguredRomMissing() keeps classifying ROM-less runs (CI) as a
            // skip rather than a failure. Only a truly blank key is "not configured".
            Optional<String> hinted = images.configuredValue(identity).filter(value -> !value.isBlank());
            if (hinted.isPresent()) {
                throw new IOException((secondary ? "Failed to open secondary ROM: " : MISSING_ROM_PREFIX) + hinted.get());
            }
            throw new IOException(secondary
                    ? "No ROM configured for game: " + gameId
                    : "ROM filename not configured (DEFAULT_ROM not set or per-game ROM key empty)");
        }
        try {
            return Rom.fromReader(images.open(identity), resolution.get().describe());
        } catch (IOException e) {
            String configured = images.configuredValue(identity).orElse(resolution.get().describe());
            throw new IOException((secondary ? "Failed to open secondary ROM: " : "Failed to open ROM file: ")
                    + configured, e);
        }
    }

    /**
     * Opens the ROM the {@code DEFAULT_ROM} game boots from.
     *
     * @throws IOException If the ROM cannot be opened
     */
    private void openRom() throws IOException {
        // Close existing ROM if any
        if (rom != null) {
            rom.close();
        }
        rom = null;
        initialized = false;

        String gameId = configService().getString(SonicConfiguration.DEFAULT_ROM);
        RomIdentity identity = legacyRomGame(gameId).identity();
        LOGGER.info("Opening ROM for " + gameId + " (logical ROM " + identity + ")");
        rom = openCatalogueRom(identity, false, gameId);
        initialized = true;
    }

    public static boolean isConfiguredRomMissing(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.startsWith(MISSING_ROM_PREFIX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Returns the configured per-game ROM value verbatim (including blank or
     * whitespace-only text) for tools that open a ROM by path. This legacy
     * forwarder does not consult the catalogue; callers that want catalogue
     * resolution use {@link RomLocationResolver} or {@link #openLogicalRom}.
     *
     * @param gameId "s1", "s2", or "s3k"
     * @return the configured ROM value for that game
     */
    @Deprecated
    public static String resolveRomForGame(String gameId) {
        SonicConfigurationService configuration = GameServices.configuration();
        if (gameId == null) {
            throw new IllegalArgumentException("Game id must not be null");
        }
        RomGame game = switch (gameId.toLowerCase(Locale.ROOT)) {
            case "s1" -> RomGame.S1;
            case "s2" -> RomGame.S2;
            case "s3k" -> RomGame.S3K;
            default -> throw new IllegalArgumentException("No stock ROM mapping for game: " + gameId);
        };
        return configuration.getString(game.configurationKey());
    }

    private static List<Object> catalogueKey(SonicConfigurationService configuration) {
        return List.of(
                RomLocationResolver.currentWorkingDirectory().toString(),
                String.valueOf(configuration.getString(SonicConfiguration.ROMS_DIRECTORY)),
                String.valueOf(configuration.getString(SonicConfiguration.SONIC_1_ROM)),
                String.valueOf(configuration.getString(SonicConfiguration.SONIC_2_ROM)),
                String.valueOf(configuration.getString(SonicConfiguration.SONIC_3K_ROM)),
                configuration.getBoolean(SonicConfiguration.ROMS_PREFER_COMPOSITE));
    }

    private static RomGame legacyRomGame(String gameId) {
        return switch (gameId != null ? gameId.toLowerCase(Locale.ROOT) : "s2") {
            case "s1" -> RomGame.S1;
            case "s3k" -> RomGame.S3K;
            default -> RomGame.S2;
        };
    }

    /**
     * Closes the ROM and releases resources.
     * Should be called on engine shutdown.
     */
    @Override
    public synchronized void close() {
        if (rom != null) {
            LOGGER.fine("Closing ROM via RomManager");
            rom.close();
            rom = null;
        }
        for (Rom secondary : secondaryRoms.values()) {
            if (secondary != null) {
                secondary.close();
            }
        }
        secondaryRoms.clear();
        initialized = false;
        catalogue = null;
        catalogueKey = null;
    }

}
