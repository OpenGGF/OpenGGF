package com.openggf.game;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomGame;
import com.openggf.data.RomImageCatalogue;
import com.openggf.game.internal.HostedGameMedia;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
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
    public enum Status { READY, FOUND, KEPT, UNVERIFIED, MISSING, INCOMPLETE, WRONG_RELEASE, UNREADABLE }

    /** One shelf game. {@code hosted} games use media other than a cartridge ROM. */
    public record Game(String id, SonicConfiguration key, boolean hosted) { }

    /** What a scan or a chosen file found for one game: its file, setup status and exact diagnostic. */
    public record Finding(String gameId, Path file, Status status, String detail) {
        public boolean usable() {
            return status == Status.READY || status == Status.FOUND || status == Status.UNVERIFIED;
        }
    }

    /** All findings, any gaps in scan coverage, and advice for unsupported file formats. */
    public record Findings(List<Finding> findings, String notice, String formatNotice) {
        public Findings { findings = List.copyOf(findings); }
        public Optional<Finding> best(String gameId) {
            Finding best = null;
            for (Finding f : findings) {
                if (!f.gameId().equals(gameId)) continue;
                if (f.usable() && f.status() != Status.UNVERIFIED) return Optional.of(f);
                if (best == null || f.usable() && !best.usable()) best = f;
            }
            return Optional.ofNullable(best);
        }
    }

    /** A different file found for a game that already works; applied only when the player chooses it. */
    public record Conflict(Game game, Path current, Path found) { }

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
        default boolean unverifiedCartridge(Game game) { return false; }
        /** The file the game currently plays from, if it is one whole file. */
        Optional<Path> file(Game game);
    }

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
        RomImageCatalogue catalogue = RomImageCatalogue.forConfiguration(config);
        return new Current() {
            private final Map<Game, Boolean> unverified = new HashMap<>();

            @Override public boolean unverifiedCartridge(Game game) {
                return !game.hosted() && unverified.computeIfAbsent(game, key -> catalogue
                        .resolve(RomGame.valueOf(key.id().toUpperCase(java.util.Locale.ROOT)).identity())
                        .map(resolution -> !resolution.verified()).orElse(false));
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

    /** Scans a folder: cartridges with the onboarding walker, hosted media with each game's own probe. */
    public static Findings scan(Path folder, List<Game> games) throws IOException {
        var cartridges = MenuGameOnboarding.discover(folder, true, 20000);
        List<Finding> findings = cartridgeFindings(cartridges.parts());
        findings.addAll(hostedUnder(folder, games));
        return new Findings(findings, cartridges.notice(), cartridges.formatNotice());
    }

    /** Identifies one chosen file as a cartridge, else as any hosted game's media. */
    public static Findings locate(Path file, List<Game> games) throws IOException {
        var cartridges = MenuGameOnboarding.discover(file, false, 20000);
        List<Finding> findings = cartridgeFindings(cartridges.parts());
        if (findings.isEmpty()) findings.addAll(inspect(file.toAbsolutePath().normalize(), games));
        return new Findings(findings, null, cartridges.formatNotice());
    }

    private static List<Finding> cartridgeFindings(Map<RomGame, RomImageCatalogue.Part> cartridges) {
        List<Finding> findings = new ArrayList<>();
        cartridges.forEach((game, part) -> {
            Path path = part.image().path();
            findings.add(new Finding(game.name().toLowerCase(java.util.Locale.ROOT), path,
                    part.verified() ? Status.READY : Status.UNVERIFIED, path.toString()));
        });
        return findings;
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
            Path file = found.get().file().toAbsolutePath().normalize();
            if (current.available(game)) {
                Optional<Path> existing = current.file(game);
                if (existing.isPresent() && !same(existing.get(), file)) conflicts.add(new Conflict(game, existing.get(), file));
                continue;
            }
            writes.put(game.key(), file.toString());
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
        ConfigurationPersistence.apply(config, Map.of(conflict.game().key(), conflict.found().toString()));
    }

    /** A game's status for the guide's list, combining the live library with this visit's findings. */
    public static Status status(Game game, Current current, Findings findings, boolean upgrade) {
        if (current.available(game)) {
            if (current.unverifiedCartridge(game)) return Status.UNVERIFIED;
            return upgrade ? Status.KEPT : game.hosted() ? Status.FOUND : Status.READY;
        }
        if (findings == null) return Status.MISSING;
        return findings.best(game.id()).map(Finding::status).orElse(Status.MISSING);
    }

    private static List<Finding> hostedUnder(Path folder, List<Game> games) {
        List<Finding> found = new ArrayList<>();
        int[] visited = {0};
        try {
            Files.walkFileTree(folder, EnumSet.noneOf(java.nio.file.FileVisitOption.class), 8, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (++visited[0] > 20000 || Thread.currentThread().isInterrupted()) return FileVisitResult.TERMINATE;
                    if (attrs.isRegularFile()) found.addAll(inspect(path.toAbsolutePath().normalize(), games));
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException unreadable) {
            // The cartridge walker reports access problems; hosted findings made so far are kept.
        }
        return found;
    }

    private static List<Finding> inspect(Path file, List<Game> games) {
        List<Finding> found = new ArrayList<>();
        for (Game game : games) {
            if (!game.hosted()) continue;
            Optional<HostedGameMedia> media = hosted(game);
            if (media.isEmpty() || !media.get().candidate(file)) continue;
            var inspection = media.get().inspect(file);
            Status status = switch (inspection.kind()) {
                case READY -> Status.FOUND;
                case INCOMPLETE -> Status.INCOMPLETE;
                case OTHER_RELEASE -> Status.WRONG_RELEASE;
                case UNREADABLE -> Status.UNREADABLE;
                case NOT_THIS_GAME -> Status.MISSING;
            };
            if (status != Status.MISSING) found.add(new Finding(game.id(), file, status, inspection.detail()));
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
