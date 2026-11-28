package com.openggf.game;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.PhysicalImage;
import com.openggf.data.RomGame;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.data.RomImageClassifier;
import com.openggf.game.MasterTitleScreen.GameEntry;
import com.openggf.game.internal.HostedGameMedia.MediaKind;
import com.openggf.game.internal.HostedGameMedia;
import com.openggf.i18n.UiMessages;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The setup guide's game library: what each stock game's status is, what a scan or a chosen file found, and
 * the configuration writes that adding it needs. Images are identified by content (cartridges by the ROM
 * catalogue's classifier, hosted media such as the Sonic CD disc by its own cheap header probe), never by
 * file name. Adding never replaces a game that already works: a different file for such a game becomes a
 * conflict the player must accept explicitly. Hosted media is identified, not verified; the whole-media
 * check runs when the game starts.
 */
public final class SetupGuideLibrary {
    /** A game's status as the guide shows it. */
    public enum Status { READY, FOUND, KEPT, MISSING, INCOMPLETE, WRONG_RELEASE, UNREADABLE }

    /** One shelf game. {@code hosted} games use media other than a cartridge ROM. */
    public record Game(String id, SonicConfiguration key, boolean hosted) { }

    /** What a scan or a chosen file found for one game: its file, kind and exact English diagnostic. */
    public record Finding(String gameId, Path file, MediaKind kind, String detail, List<Path> paths,
                          Map<SonicConfiguration, String> settings) {
        Finding(String gameId, Path file, MediaKind kind, String detail) {
            this(gameId, file, kind, detail, List.of(file), Map.of());
        }

        public boolean usable() { return kind == MediaKind.READY; }
    }

    /** All findings, the walker's notice (null when nothing was skipped) and whether anything was readable. */
    public record Findings(List<Finding> findings, String notice) {
        public Findings { findings = List.copyOf(findings); }
        public Optional<Finding> best(String gameId) {
            Finding problem = null;
            for (Finding f : findings) {
                if (!f.gameId().equals(gameId)) continue;
                if (f.usable()) return Optional.of(f);
                if (problem == null) problem = f;
            }
            return Optional.ofNullable(problem);
        }
    }

    /** A different file found for a game that already works; applied only when the player chooses it. */
    public record Conflict(Game game, Path current, Path found, List<Path> paths,
                           Map<SonicConfiguration, String> settings) {
        public Conflict(Game game, Path current, Path found) {
            this(game, current, found, List.of(found), Map.of(game.key(), found.toString()));
        }
    }

    /** The writes adding findings needs, and the conflicts left for the player. */
    public record Plan(Map<SonicConfiguration, Object> writes, List<Game> added, List<Conflict> conflicts) {
        public Plan {
            writes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(writes));
            added = List.copyOf(added);
            conflicts = List.copyOf(conflicts);
        }
    }

    /** Reads the current library: which games are available now, and through which file. */
    public interface Current {
        boolean available(Game game);
        default boolean hasTierTwoDump() { return false; }
        /** The file the game currently plays from, if it is one whole file. */
        Optional<Path> file(Game game);
    }

    private static final int MAX_SCAN_ENTRIES = 20_000;

    private SetupGuideLibrary() { }

    /** Every shelf game: the three cartridges, then the hosted-media games the registry lists. */
    public static List<Game> games() {
        List<Game> games = new ArrayList<>();
        for (RomGame game : RomGame.values())
            games.add(new Game(game.name().toLowerCase(java.util.Locale.ROOT), game.configurationKey(), false));
        for (GameId hosted : GameModuleRegistry.hostedMediaGames()) {
            var entry = MasterTitleScreen.GameEntry.fromGameId(hosted.code());
            if (entry != null) games.add(new Game(hosted.code(), entry.romConfigKey, true));
        }
        return games;
    }

    /** The live library from the ROM catalogue and each hosted game's probe. */
    public static Current current(SonicConfigurationService config) {
        RomImageCatalogue catalogue = RomImageCatalogue.forCurrentWorkingDirectory(config);
        return new Current() {
            @Override public boolean hasTierTwoDump() {
                return catalogue.isAvailable(RomIdentity.KIS2_CHIP);
            }
            @Override public boolean available(Game game) {
                if (game.hosted()) return hosted(game).map(media -> media.probe(config).available()).orElse(false);
                return catalogue.isAvailable(RomGame.valueOf(game.id().toUpperCase(java.util.Locale.ROOT)).identity());
            }
            @Override public Optional<Path> file(Game game) {
                if (game.hosted()) {
                    var media = hosted(game);
                    if (media.isEmpty() || !media.get().probe(config).available()) return Optional.empty();
                    try { return Optional.of(media.get().configuredPath(config).toAbsolutePath().normalize()); }
                    catch (IOException unavailable) { return Optional.empty(); }
                }
                return catalogue.resolve(RomGame.valueOf(game.id().toUpperCase(java.util.Locale.ROOT)).identity())
                        .flatMap(RomImageCatalogue.Resolution::wholeImagePath)
                        .map(path -> path.toAbsolutePath().normalize());
            }
        };
    }

    record DiscoveryResult(Map<GameEntry, List<Path>> games, Map<SonicConfiguration, String> settings,
                           boolean limitReached, boolean unreadable, List<Finding> hostedFindings) {
        DiscoveryResult(Map<GameEntry, List<Path>> games, Map<SonicConfiguration, String> settings,
                        boolean limitReached, boolean unreadable) {
            this(games, settings, limitReached, unreadable, List.of());
        }

        DiscoveryResult {
            var ordered = new EnumMap<GameEntry, List<Path>>(GameEntry.class);
            ordered.putAll(games);
            games = Collections.unmodifiableMap(ordered);
            settings = Map.copyOf(settings);
            hostedFindings = List.copyOf(hostedFindings);
        }

        /** Report incomplete coverage if the scan could have missed another game. */
        String notice() {
            if (games.size() == GameEntry.values().length || !limitReached && !unreadable) return null;
            if (limitReached && unreadable) return "Stopped early, skipped unreadable files; "
                    + (games.isEmpty() ? "scan their folder" : "scan the rest");
            if (limitReached) return "Stopped early; " + (games.isEmpty() ? "scan the folder that holds them" : "scan the folder with the rest");
            return games.isEmpty() ? "Some files could not be read; check access to them"
                    : "Skipped files that could not be read; check access to them";
        }
    }

    static DiscoveryResult discover(Path selection, boolean folder) throws IOException {
        return discover(selection, folder, MAX_SCAN_ENTRIES, false);
    }

    static DiscoveryResult discover(Path selection, boolean folder, int maxEntries) throws IOException {
        return discover(selection, folder, maxEntries, false);
    }

    static DiscoveryResult discover(Path selection, boolean folder, boolean preferComposite) throws IOException {
        return discover(selection, folder, MAX_SCAN_ENTRIES, preferComposite);
    }

    private static DiscoveryResult discover(Path selection, boolean folder, int maxEntries, boolean preferComposite)
            throws IOException {
        Path source = selection.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        List<PhysicalImage> cartridges = new ArrayList<>();
        boolean[] limited = {false};
        boolean[] unreadable = {false};
        if (!folder) {
            if (!Files.isRegularFile(source)) throw new IOException(UiMessages.text("menu.onboarding.choose_a_rom_file"));
            probeFile(source, files, cartridges);
        } else {
            if (!Files.isDirectory(source)) throw new IOException(UiMessages.text("menu.onboarding.choose_a_folder"));
            // Do not follow directory links. Limits and denied subtrees preserve prior discoveries.
            Files.walkFileTree(source, EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8,
                    new SimpleFileVisitor<>() {
                        private int visited;

                        private FileVisitResult boundary() throws IOException {
                            checkInterrupted();
                            if (++visited > maxEntries) {
                                limited[0] = true;
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                                throws IOException {
                            return boundary();
                        }

                        @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                            FileVisitResult next = boundary();
                            if (next == FileVisitResult.TERMINATE) return next;
                            if (attrs.isDirectory()) limited[0] = true; // Maximum walk depth reached.
                            if (attrs.isRegularFile()) {
                                try { probeFile(path, files, cartridges); }
                                catch (IOException | SecurityException failure) { unreadable[0] = true; }
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                            if (file.equals(source)) throw failure;
                            unreadable[0] = true;
                            return boundary();
                        }

                        @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                            if (failure != null) {
                                if (dir.equals(source) && files.isEmpty()) throw failure;
                                unreadable[0] = true;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        }

        var hosted = new ArrayList<Finding>();
        List<Game> hostedGames = games().stream().filter(Game::hosted).toList();
        for (Path file : files) {
            checkInterrupted();
            hosted.addAll(inspect(file, hostedGames));
        }
        var games = new EnumMap<GameEntry, List<Path>>(GameEntry.class);
        var settings = new EnumMap<SonicConfiguration, String>(SonicConfiguration.class);
        var catalogue = RomImageCatalogue.forDiscovery(cartridges, preferComposite);
        for (GameEntry game : GameEntry.values()) {
            checkInterrupted();
            GameId id = GameId.fromCode(game.gameId);
            if (game.cartridge()) {
                catalogue.resolve(id.romGame().identity()).ifPresent(resolution -> {
                    List<Path> paths = resolution.parts().stream().map(part -> part.image().path()).distinct().toList();
                    games.put(game, paths);
                    if (paths.size() == 1) {
                        settings.put(game.romConfigKey, paths.getFirst().toString());
                    } else {
                        // A split layout uses the catalogue recipe and each part's own path.
                        settings.put(game.romConfigKey, "");
                        for (var part : resolution.parts()) {
                            settings.put(RomImageCatalogue.SUPPLEMENTAL_KEYS.get(part.rom()), part.image().path().toString());
                        }
                    }
                });
            } else {
                hosted.stream().filter(Finding::usable).filter(f -> f.gameId().equals(game.gameId)).findFirst()
                        .map(Finding::file).ifPresent(path -> {
                    games.put(game, List.of(path));
                    settings.put(game.romConfigKey, path.toString());
                });
            }
        }
        // The chip's source is the lock-on dump even when the catalogue prefers composites.
        catalogue.resolve(RomIdentity.KIS2_CHIP)
                .ifPresent(chip -> settings.put(SonicConfiguration.KNUCKLES_IN_SONIC_2_ROM,
                        chip.parts().getFirst().image().path().toString()));
        checkInterrupted();
        return new DiscoveryResult(games, settings, limited[0], unreadable[0], hosted);
    }

    private static void checkInterrupted() throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException(UiMessages.text("menu.menugameonboarding.scan_cancelled"));
        }
    }

    private static void probeFile(Path path, List<Path> files, List<PhysicalImage> cartridges) throws IOException {
        PhysicalImage image = PhysicalImage.probe(path);
        if (RomImageClassifier.layoutOf(image).isPresent()) cartridges.add(image);
        files.add(path);
    }

    /** Scans a bounded folder tree and keeps hosted-media diagnostics. */
    public static Findings scan(Path folder, List<Game> games) throws IOException {
        return find(folder, true, games, false);
    }

    public static Findings locate(Path file, List<Game> games) throws IOException {
        return find(file, false, games, false);
    }

    static Findings find(Path selection, boolean folder, List<Game> games, boolean preferComposite) throws IOException {
        var result = discover(selection, folder, preferComposite);
        List<Finding> findings = new ArrayList<>();
        for (Game game : games) {
            var entry = MasterTitleScreen.GameEntry.fromGameId(game.id());
            var paths = result.games().get(entry);
            if (paths == null) continue;
            Map<SonicConfiguration, String> settings = new LinkedHashMap<>();
            settings.put(game.key(), result.settings().get(game.key()));
            if (paths.size() > 1) {
                for (var key : List.of(SonicConfiguration.SONIC_3_ROM, SonicConfiguration.SONIC_KNUCKLES_ROM))
                    settings.put(key, result.settings().get(key));
            }
            if (game.key() == SonicConfiguration.SONIC_2_ROM) {
                var key = SonicConfiguration.KNUCKLES_IN_SONIC_2_ROM;
                if (result.settings().containsKey(key)) settings.put(key, result.settings().get(key));
            }
            Path first = paths.getFirst();
            findings.add(new Finding(game.id(), first, MediaKind.READY, first.toString(), paths, Map.copyOf(settings)));
        }
        result.hostedFindings().stream().filter(f -> !f.usable())
                .filter(f -> games.stream().anyMatch(game -> game.id().equals(f.gameId())))
                .forEach(findings::add);
        return new Findings(findings, folder ? result.notice() : null);
    }

    /**
     * What adding {@code findings} writes. A game with nothing usable is skipped; a game that already works is
     * never rewritten (a different file becomes a conflict); the ROM folder is set only while no game works,
     * so an earlier library's folder is never moved.
     */
    public static Plan plan(Findings findings, List<Game> games, Current current) {
        Map<SonicConfiguration, Object> writes = new LinkedHashMap<>();
        List<Game> added = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        boolean anyAvailable = games.stream().anyMatch(current::available);
        Path folder = null;
        for (Game game : games) {
            Optional<Finding> found = findings.best(game.id()).filter(Finding::usable);
            if (found.isEmpty()) continue;
            Finding finding = found.get();
            Path file = finding.file().toAbsolutePath().normalize();
            var settings = finding.settings().isEmpty() ? Map.of(game.key(), file.toString()) : finding.settings();
            var tierTwo = SonicConfiguration.KNUCKLES_IN_SONIC_2_ROM;
            if (settings.containsKey(tierTwo) && !current.hasTierTwoDump()) writes.put(tierTwo, settings.get(tierTwo));
            if (current.available(game)) {
                Optional<Path> existing = current.file(game);
                if (existing.isPresent() && (finding.paths().size() > 1 || !same(existing.get(), file)))
                    conflicts.add(new Conflict(game, existing.get(), file, finding.paths(), settings));
                continue;
            }
            writes.putAll(settings);
            added.add(game);
            if (folder == null && !game.hosted()) folder = file.getParent();
        }
        if (!anyAvailable && folder != null) writes.put(SonicConfiguration.ROMS_DIRECTORY, folder.toString());
        return new Plan(writes, added, conflicts);
    }

    /** Applies a plan's writes atomically; nothing is written when it has none. */
    public static void apply(SonicConfigurationService config, Plan plan) throws IOException {
        if (!plan.writes().isEmpty()) ConfigurationPersistence.apply(config, plan.writes());
    }

    /** Replaces one game's file after the player chose the new file in the conflict prompt. */
    public static void replace(SonicConfigurationService config, Conflict conflict) throws IOException {
        ConfigurationPersistence.apply(config, Map.copyOf(conflict.settings()));
    }

    /** A game's status for the guide's list, combining the live library with this visit's findings. */
    public static Status status(Game game, Current current, Findings findings, boolean upgrade) {
        if (current.available(game)) return upgrade ? Status.KEPT : game.hosted() ? Status.FOUND : Status.READY;
        if (findings == null) return Status.MISSING;
        return findings.best(game.id()).map(f -> switch (f.kind()) {
            case READY -> game.hosted() ? Status.FOUND : Status.READY;
            case INCOMPLETE -> Status.INCOMPLETE;
            case OTHER_RELEASE -> Status.WRONG_RELEASE;
            case UNREADABLE -> Status.UNREADABLE;
            case NOT_THIS_GAME -> Status.MISSING;
        }).orElse(Status.MISSING);
    }

    private static List<Finding> inspect(Path file, List<Game> games) {
        List<Finding> found = new ArrayList<>();
        for (Game game : games) {
            if (!game.hosted()) continue;
            Optional<HostedGameMedia> media = hosted(game);
            if (media.isEmpty() || !media.get().candidate(file)) continue;
            var inspection = media.get().inspect(file);
            if (inspection.kind() != MediaKind.NOT_THIS_GAME)
                found.add(new Finding(game.id(), file, inspection.kind(), inspection.detail()));
        }
        return found;
    }

    private static Optional<HostedGameMedia> hosted(Game game) {
        return Optional.ofNullable(GameModuleRegistry.hostedMedia(GameId.fromCode(game.id())));
    }

    private static boolean same(Path a, Path b) {
        try { return Files.isSameFile(a, b); }
        catch (IOException e) { return Objects.equals(a.toAbsolutePath().normalize(), b.toAbsolutePath().normalize()); }
    }
}
