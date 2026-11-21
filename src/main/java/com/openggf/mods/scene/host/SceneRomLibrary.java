package com.openggf.mods.scene.host;

import com.openggf.data.Rom;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.BuiltInRomDetectors;
import com.openggf.game.GameId;
import com.openggf.catalog.StockGameCatalog;
import com.openggf.mods.scene.SceneRomArt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scene-owned views of the player's available stock ROMs. The active ROM is borrowed;
 * additional logical-ROM views are owned here and closed with the scene. No session,
 * default-game setting or shared secondary-ROM cache is changed. Engine-internal.
 */
public final class SceneRomLibrary implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(SceneRomLibrary.class.getName());
    private final Map<String, SceneRomArt> art = new LinkedHashMap<>();
    private final Map<String, GameModule> modules = new LinkedHashMap<>();
    private final Map<String, Rom> sourceRoms = new LinkedHashMap<>();
    private final List<Rom> ownedRoms = new ArrayList<>();
    private final SceneRomAudioSources audioSources = new SceneRomAudioSources(this);
    private boolean closed;
    private final String activeGame;
    private final Map<String, com.openggf.game.GameDataSource> sources = new LinkedHashMap<>();
    private final List<java.io.Closeable> ownedMedia = new ArrayList<>();

    /** The host owns media discovery; transport code consumes only bounded path/identity values. */
    public static java.util.Optional<java.nio.file.Path> configuredMediaPath(String game) throws IOException {
        var id = GameId.fromCode(game);
        if (!GameModuleRegistry.hostedMediaGames().contains(id)) return java.util.Optional.empty();
        return java.util.Optional.of(GameModuleRegistry.hostedMedia(id)
                .configuredPath(com.openggf.game.GameServices.configuration()));
    }

    /** Reopens and verifies the complete hosted source; cartridges keep their exact reader windows. */
    public static java.util.Optional<String> verifiedMediaIdentity(String game, java.nio.file.Path path) throws IOException {
        var id = GameId.fromCode(game);
        if (!GameModuleRegistry.hostedMediaGames().contains(id)) return java.util.Optional.empty();
        var source = GameModuleRegistry.hostedMedia(id).openMedia(path);
        try { return java.util.Optional.of(source.identity()); }
        finally { if (source instanceof java.io.Closeable closeable) closeable.close(); }
    }

    public String sourceIdentity(String game) { return source(game).identity(); }

    public SceneRomLibrary(GameModule activeModule, com.openggf.game.GameDataSource source, RomManager roms) {
        this(activeModule, source.rom().orElse(null), roms);
        sources.put(activeGame, source);
        var mediaArt = activeModule.getGameService(com.openggf.game.internal.MediaSceneArt.class);
        if (mediaArt != null) {
            art.put(activeGame, SceneRomArtFactory.forMedia(mediaArt.defaultArt(source)));
            modules.put(activeGame, activeModule);
        }
    }

    public SceneRomArt program(String game, String path) {
        requireOpen();
        var provider = module(game).getGameService(com.openggf.game.internal.MediaSceneArt.class);
        if (provider == null) throw new IllegalArgumentException("No file-addressed scene art for " + game);
        return SceneRomArtFactory.forMedia(provider.program(source(game), path));
    }

    public SceneRomLibrary(GameModule activeModule, Rom activeRom, RomManager roms) {
        Objects.requireNonNull(activeModule, "activeModule");

        Objects.requireNonNull(roms, "roms");
        String active = activeModule.getGameId().code();
        activeGame = active;
        for (String code : StockGameCatalog.cartridgeCodes()) {
            if (code.equals(active) && activeRom != null) {
                art.put(code, SceneRomArtFactory.forModule(activeModule, activeRom));
                sourceRoms.put(code, activeRom);
                modules.put(code, activeModule);
                continue;
            }
            RomIdentity identity = GameId.fromCode(code).romGame().identity();
            if (!roms.isLogicalRomAvailable(identity)) continue;
            Rom extra = null;
            try {
                extra = Rom.fromReader(roms.openLogicalRom(identity), "scene " + code);
                GameModule extraModule = stockModule(code);
                SceneRomArt prepared = SceneRomArtFactory.forModule(extraModule, extra);
                ownedRoms.add(extra);
                art.put(code, prepared);
                sourceRoms.put(code, extra);
                modules.put(code, extraModule);
            } catch (IOException | RuntimeException failure) {
                if (extra != null) extra.close();
                LOG.log(Level.WARNING, "Scene ROM unavailable: " + code, failure);
            }
        }
    }

    /** CPU-only supplied-ROM library for offline evaluation; every opened ROM is owned here. */
    public static SceneRomLibrary open(Map<String, java.nio.file.Path> paths) throws IOException {
        return new SceneRomLibrary(paths);
    }

    /** Exact offline logical-ROM views; this library owns wrappers, while readers remain immutable. */
    public static SceneRomLibrary openReaders(Map<String, com.openggf.data.RomByteReader> readers) throws IOException {
        return openReaders(readers,StockGameCatalog.cartridgeCodes().stream().filter(readers::containsKey).findFirst().orElse(""));
    }

    public static SceneRomLibrary openReaders(Map<String, com.openggf.data.RomByteReader> readers, String activeGame) throws IOException {
        return new SceneRomLibrary(readers,"scene logical ",activeGame);
    }

    private SceneRomLibrary(Map<String, com.openggf.data.RomByteReader> readers, String description, String activeGame) throws IOException {
        this(readers, description, activeGame, false);
    }

    private SceneRomLibrary(Map<String, com.openggf.data.RomByteReader> readers, String description, String activeGame, boolean media) throws IOException {
        Objects.requireNonNull(readers);
        if ((!media && readers.isEmpty()) || !StockGameCatalog.cartridgeCodes().containsAll(readers.keySet()))
            throw new IllegalArgumentException("Supply one or more named stock ROMs");
        if (!media && !readers.containsKey(activeGame)) throw new IllegalArgumentException("Supply the active scene ROM: " + activeGame);
        this.activeGame = activeGame;
        try {
            for (String code : StockGameCatalog.cartridgeCodes()) {
                if (!readers.containsKey(code)) continue;
                Rom rom = Rom.fromReader(Objects.requireNonNull(readers.get(code)), description + code);
                ownedRoms.add(rom);
                var detector = BuiltInRomDetectors.forGame(GameId.fromCode(code));
                if (!detector.canHandle(rom)) throw new IOException("ROM does not match requested game " + code);
                GameModule module = detector.createModule();
                modules.put(code, module); sourceRoms.put(code, rom);
                art.put(code, SceneRomArtFactory.forModule(module, rom));
            }
        } catch (IOException | RuntimeException failure) {
            close(); throw failure;
        }
    }

    /** Opens exact cartridge views and independently verified, library-owned media sources. */
    public static SceneRomLibrary openSources(Map<String, com.openggf.data.RomByteReader> readers,
            Map<String, java.nio.file.Path> media, String active) throws IOException {
        var library = new SceneRomLibrary(readers, "scene logical ", active, true);
        try {
            for (var entry : media.entrySet()) {
                if (readers.containsKey(entry.getKey())) throw new IOException("Duplicate scene source");
                var module = GameModuleRegistry.createStockModule(GameId.fromCode(entry.getKey()));
                var host = Objects.requireNonNull(com.openggf.game.internal.HostedGameMedia.of(module));
                var source = host.openMedia(entry.getValue());
                if (source instanceof java.io.Closeable closeable) library.ownedMedia.add(closeable);
                library.sources.put(entry.getKey(), source);
                library.modules.put(entry.getKey(), module);
                module.createGame(source);
                var art = Objects.requireNonNull(module.getGameService(com.openggf.game.internal.MediaSceneArt.class));
                library.art.put(entry.getKey(), SceneRomArtFactory.forMedia(art.defaultArt(source)));
            }
            library.module(active);
            return library;
        } catch (IOException | RuntimeException failure) {
            library.close(); throw failure;
        }
    }

    private SceneRomLibrary(Map<String, java.nio.file.Path> paths) throws IOException {
        Objects.requireNonNull(paths);
        if (paths.isEmpty() || !StockGameCatalog.stockCodes().containsAll(paths.keySet()))
            throw new IllegalArgumentException("Supply one or more named stock ROMs");
        activeGame = paths.keySet().stream().sorted().findFirst().orElseThrow();
        try {
            for (String code : paths.keySet()) {
                var hosted = GameModuleRegistry.mediaKind(GameId.fromCode(code)) == GameModuleRegistry.MediaKind.HOSTED_MEDIA
                        ? GameModuleRegistry.hostedMedia(GameId.fromCode(code)) : null;
                if (hosted != null) {
                    var source = hosted.openMedia(paths.get(code));
                    if (source instanceof java.io.Closeable closeable) ownedMedia.add(closeable);
                    sources.put(code, source);
                    var module = GameModuleRegistry.createStockModule(GameId.fromCode(code));
                    modules.put(code, module);
                    module.createGame(source);
                    var provider = Objects.requireNonNull(module.getGameService(com.openggf.game.internal.MediaSceneArt.class));
                    art.put(code, SceneRomArtFactory.forMedia(provider.defaultArt(source)));
                    continue;
                }
                Rom rom = new Rom(); ownedRoms.add(rom);
                var detector = BuiltInRomDetectors.forGame(GameId.fromCode(code));
                if (!rom.open(paths.get(code).toAbsolutePath().toString()) || !detector.canHandle(rom))
                    throw new IOException("ROM does not match requested game " + code);
                GameModule module = detector.createModule();
                modules.put(code, module); sourceRoms.put(code, rom);
                art.put(code, SceneRomArtFactory.forModule(module, rom));
            }
        } catch (IOException | RuntimeException failure) {
            close(); throw failure;
        }
    }

    /** Engine-internal physical resources from the same exact modules/ROMs used for presentation. */
    public GameModule module(String gameId) {
        requireOpen();
        GameModule module = modules.get(gameId);
        if (module == null) throw new IllegalArgumentException("Supply the " + gameId + " ROM");
        return module;
    }

    public com.openggf.sprites.art.SpriteArtSet characterArt(String gameId, String character) {
        requireOpen(); module(gameId);
        return ((RomSceneArt) art.get(gameId)).playerArt(character);
    }

    public SceneRomArt stageArt(String game, int zone, int act) {
        var provider = module(game).getGameService(com.openggf.game.internal.MediaSceneArt.class);
        return provider == null ? rom(game) : SceneRomArtFactory.forMedia(provider.stage(source(game), zone, act));
    }

    public com.openggf.level.Level level(String gameId, int zone, int act) {
        requireOpen(); module(gameId);
        var level = ((RomSceneArt) stageArt(gameId, zone, act)).puppetLevel(zone, act);
        if (level == null) throw new IllegalArgumentException("Set has no detached native terrain");
        return level;
    }

    public List<String> availableGames() {
        requireOpen();
        return List.copyOf(art.keySet());
    }

    public SceneRomArt rom(String gameId) {
        requireOpen();
        if (!StockGameCatalog.isStockCode(gameId)) throw new IllegalArgumentException("Unknown stock game: " + gameId);
        return art.get(gameId);
    }

    /** Borrowed source; the library retains ownership until close. */
    public com.openggf.game.GameDataSource source(String game) {
        requireOpen();
        var source = sources.get(game);
        if (source != null) return source;
        try {
            var pinned = com.openggf.game.StockGameDataSources.pinned(Objects.requireNonNull(sourceRom(game)), module(game));
            sources.put(game, pinned);
            return pinned;
        }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    public String activeGame() {
        requireOpen();
        return activeGame;
    }

    /** One native route per game and producer epoch; cue/effect leases may close in either order. */
    public SceneRomAudioSources.Lease acquireAudioSource(com.openggf.audio.AudioManager audio, String game) {
        requireOpen();
        return audioSources.acquire(audio,game);
    }

    /** The same supplied-ROM view used by art; borrowed by the scene's audio owner. */
    public Rom sourceRom(String gameId) {
        requireOpen();
        if (!StockGameCatalog.isStockCode(gameId)) {
            throw new IllegalArgumentException("Unknown stock game: " + gameId);
        }
        return sourceRoms.get(gameId);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Scene ROM library is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { audioSources.close(); }
        finally {
            art.clear(); sourceRoms.clear(); modules.clear();
            ownedRoms.forEach(Rom::close); ownedRoms.clear();
            java.io.UncheckedIOException error = null;
            for (var media : ownedMedia) try { media.close(); } catch (IOException failure) {
                if (error == null) error = new java.io.UncheckedIOException(failure); else error.addSuppressed(failure);
            }
            ownedMedia.clear(); sources.clear();
            if (error != null) throw error;
        }
    }

    private static GameModule stockModule(String code) {
        return BuiltInRomDetectors.forGame(GameId.fromCode(code)).createModule();
    }
}
