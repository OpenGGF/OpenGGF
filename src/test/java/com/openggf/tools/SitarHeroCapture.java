package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.mods.code.ExampleModHarness;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneMusicPlayer;
import com.openggf.mods.scene.ScenePreparedMusic;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * Recurring production-ROM acceptance of the actual supplied Sitar Hero startup scene.
 * Inputs: absolute main-ROM directory ({@code --rom-root}), example source directory
 * ({@code --mod}), external artifact directory ({@code --out}), and installed subset
 * ({@code --subset s1|s2|s3k|all}). Public song IDs are derived from that mod's registry.
 * Each visit records source hashes, ROM identities, decoded native art and readable GL
 * scenes. Run each installed subset in a fresh JVM; no ROM files or links are created.
 *
 * <p>Origin: authorized Sitar Hero full-version acceptance, 2026-10-07. The offline
 * audio sink advances real synthesis; frame counts and calibration taps here prove
 * scene behavior, never speaker latency, real-time throughput or hardware parity.
 */
public final class SitarHeroCapture {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 224;
    private static final List<String> ALL = List.of("s1", "s2", "s3k");
    private static final List<String> ROLES = List.of("SITAR", "BONGOS", "SYNTH", "HARP");
    private static final List<String> DIFFICULTIES = List.of("EASY", "MEDIUM", "HARD", "EXPERT");
    private static final List<String> STAGE_FAILURES = new ArrayList<>();
    private static final Duration LOADING_TIMEOUT = Duration.ofMinutes(3);
    private static final List<String> ROSTER = List.of("sonic", "robotnik", "tails", "silver-sonic",
            "knuckles", "mecha-sonic", "egg-robo");

    private SitarHeroCapture() { }

    public static void main(String[] args) throws Exception {
        Path romRoot = null;
        Path out = null;
        Path mod = Path.of("examples/sitar-hero");
        String subset = "all";
        for (int index = 0; index < args.length; index += 2) {
            if (index + 1 == args.length) throw new IllegalArgumentException("Missing option value");
            switch (args[index]) {
                case "--rom-root" -> romRoot = Path.of(args[index + 1]).toAbsolutePath();
                case "--mod" -> mod = Path.of(args[index + 1]).toAbsolutePath();
                case "--out" -> out = Path.of(args[index + 1]).toAbsolutePath();
                case "--subset" -> subset = args[index + 1];
                default -> throw new IllegalArgumentException("Unknown option: " + args[index]);
            }
        }
        if (romRoot == null || out == null) throw new IllegalArgumentException("--rom-root and --out are required");
        mod = mod.toAbsolutePath().normalize();
        List<String> games = subset.equals("all") ? ALL : List.of(subset);
        if (!ALL.containsAll(games)) throw new IllegalArgumentException("Unknown subset: " + subset);
        Path visit = Files.createDirectories(out.resolve(subset));
        require(!Files.exists(visit.resolve("sources.txt")), "Capture output already exists; choose a fresh --out: " + visit);
        System.setProperty(com.openggf.game.save.SavePaths.ROOT_PROPERTY, visit.resolve("saves").toString());
        String sourceIdentity = recordSources(mod, romRoot, games, visit);
        String active = games.getFirst();
        try (HeadlessGameBoot boot = new HeadlessGameBoot(WIDTH, HEIGHT, WIDTH, HEIGHT)) {
            boot.boot(romRoot.resolve(active + ".gen"), 0, 0);
            configureSubset(romRoot, visit, games);
            try (ExampleModHarness harness = ExampleModHarness.build(mod, visit.resolve("build"))) {
                require(sourceIdentity(mod).equals(sourceIdentity), "Mod source changed during compilation; rerun on a stable candidate");
                var effective = harness.apply(GameServices.module());
                GameModuleRegistry.setCurrent(effective);
                harness.open(effective, visit.resolve("saves"), WIDTH, HEIGHT, true);
                try {
                    tick(harness);
                    requireScreen(harness, "TITLE");
                    capture(harness, visit, "title");
                    List<String> performers = strings(harness, "availablePerformers");
                    List<String> expected = ROSTER.stream().filter(id -> available(id, games)).toList();
                    require(performers.equals(expected), "Roster " + performers + " differs from " + expected);
                    verifyCatalogue(harness, games, visit);
                    verifyStage(harness);
                    // Real mouse selection of Quick play, followed by keyboard menu navigation.
                    click(harness, 50, 87);
                    requireScreen(harness, "CHARACTERS");
                    require(invoke(harness, "mode").equals("QUICK_PLAY"), "Mouse did not select Quick play");
                    for (String performer : performers) {
                        require((Integer) field(harness.scene(), "selected") == performers.indexOf(performer),
                                "Wrong visible performer for " + performer);
                        capture(harness, visit, "character-" + performer);
                        verifyActor(harness, performer);
                        press(harness, GLFW.GLFW_KEY_ENTER);
                        requireScreen(harness, "ROLES");
                        for (String role : ROLES) {
                            select(harness, ROLES.indexOf(role));
                            capture(harness, visit, "role-" + performer + "-" + role.toLowerCase(Locale.ROOT));
                            verifyActor(harness, performer);
                        }
                        press(harness, GLFW.GLFW_KEY_ESCAPE);
                        requireScreen(harness, "CHARACTERS");
                        press(harness, GLFW.GLFW_KEY_DOWN);
                    }
                    press(harness, GLFW.GLFW_KEY_ENTER);
                    requireScreen(harness, "ROLES");
                    select(harness, 0);
                    press(harness, GLFW.GLFW_KEY_ENTER);
                    requireScreen(harness, "DIFFICULTY");
                    for (String difficulty : DIFFICULTIES) {
                        select(harness, DIFFICULTIES.indexOf(difficulty));
                        capture(harness, visit, "difficulty-" + difficulty.toLowerCase(Locale.ROOT));
                        press(harness, GLFW.GLFW_KEY_ENTER);
                        requireScreen(harness, "SONGS");
                        require(field(harness.scene(), "difficulty").toString().equals(difficulty), "Difficulty not applied");
                        capture(harness, visit, "songs-" + difficulty.toLowerCase(Locale.ROOT));
                        // Traverse the full public list, including later pages, without playing every song.
                        for (int row = 0; row < strings(harness, "availableSongs").size(); row++)
                            press(harness, GLFW.GLFW_KEY_DOWN);
                        select(harness, strings(harness, "availableSongs").size() - 1);
                        capture(harness, visit, "songs-last-page-" + difficulty.toLowerCase(Locale.ROOT));
                        press(harness, GLFW.GLFW_KEY_ESCAPE);
                        requireScreen(harness, "DIFFICULTY");
                    }
                    jump(harness, "title");
                    titleScenes(harness, visit);
                    if (subset.equals("all")) {
                        performances(harness, visit);
                        localPerformance(harness, visit);
                    }
                    settings(harness, visit);
                    require(harness.findings().isEmpty(), "Mod fault findings: " + harness.findings());
                    require(STAGE_FAILURES.isEmpty(), "Production-ROM stage failures: " + STAGE_FAILURES);
                    Files.writeString(visit.resolve("acceptance.txt"), "PASS subset=" + subset
                            + " performers=" + performers + " publicSongs=" + strings(harness, "availableSongs").size()
                            + "\nOffline synthesis: no latency or real-time performance claim.\n");
                    System.out.println("VISUAL ACCEPTANCE: " + subset + " " + performers + "; captures " + visit);
                } catch (Exception failure) {
                    try { capture(harness, visit, "failure-" + screen(harness).toLowerCase(Locale.ROOT)); }
                    catch (Exception captureFailure) { failure.addSuppressed(captureFailure); }
                    Files.writeString(visit.resolve("failure.txt"), failure + "\nfindings=" + harness.findings());
                    throw failure;
                } finally {
                    harness.host().cleanup();
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> publicSongs(ExampleModHarness harness) throws Exception {
        return (List<Object>) harness.loader().loadClass("sitarhero.model.SongCatalog").getMethod("all").invoke(null);
    }

    private static void verifyCatalogue(ExampleModHarness harness, List<String> games, Path visit) throws Exception {
        List<Object> publicSongs = publicSongs(harness);
        List<String> expected = new ArrayList<>();
        StringBuilder inventory = new StringBuilder("id,game,musicId,durationFrames,publicForSubset\n");
        Set<String> unique = new HashSet<>();
        for (Object song : publicSongs) {
            String id = (String) method(song, "id");
            String game = (String) method(song, "game");
            require(unique.add(id), "Duplicate public song ID: " + id);
            require(ALL.contains(game), "Unknown public song ROM: " + game);
            require((Integer) method(song, "durationFrames") > 0, "No finite public song duration: " + id);
            if (games.contains(game)) expected.add(id);
            inventory.append(id).append(',').append(game).append(',').append(method(song, "musicId"))
                    .append(',').append(method(song, "durationFrames")).append(',').append(games.contains(game)).append('\n');
        }
        require(!expected.isEmpty(), "Public registry is empty for installed ROMs");
        require(strings(harness, "availableSongs").equals(expected), "Public catalogue/filter mismatch: expected="
                + expected + " actual=" + strings(harness, "availableSongs"));
        Files.writeString(visit.resolve("public-catalogue.csv"), inventory);
        System.out.println("PUBLIC CATALOGUE " + games + ": " + expected.size() + " selected / " + publicSongs.size()
                + " supplied registry entries; IDs=" + expected);
    }

    private static String recordSources(Path mod, Path romRoot, List<String> games, Path visit) throws Exception {
        StringBuilder sources = new StringBuilder("Captured at " + java.time.Instant.now() + "\nmod=" + mod
                + "\nengineClass=" + HeadlessGameBoot.class.getProtectionDomain().getCodeSource().getLocation()
                + "\ncaptureClass=" + SitarHeroCapture.class.getProtectionDomain().getCodeSource().getLocation()
                + "\nJava=" + System.getProperty("java.version") + "\n");
        // Hash exactly the source compiled by ExampleModHarness, including uncommitted parent edits.
        String identity = sourceIdentity(mod);
        sources.append("Source files (path / SHA256):\n").append(identity);
        sources.append("SOURCE MANIFEST SHA256 ").append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).append('\n');
        Path captureLocation = Path.of(SitarHeroCapture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path captureFile = Files.isDirectory(captureLocation)
                ? captureLocation.resolve("com/openggf/tools/SitarHeroCapture.class") : captureLocation;
        sources.append("CAPTURE SHA256 ").append(hash(captureFile, "SHA-256")).append(' ').append(captureFile).append('\n');
        for (String name : List.of("com/openggf/tools/HeadlessGameBoot.class",
                "com/openggf/mods/code/ExampleModHarness.class",
                "com/openggf/mods/scene/host/music/ManagedSceneMusic.class")) {
            try (var resource = SitarHeroCapture.class.getClassLoader().getResourceAsStream(name)) {
                require(resource != null, "Missing engine/harness bytecode: " + name);
                sources.append("BYTECODE SHA256 ").append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(resource.readAllBytes()))).append(' ').append(name).append('\n');
            }
        }
        for (String game : games) {
            Path rom = romRoot.resolve(game + ".gen").toRealPath();
            String sha1 = hash(rom, "SHA-1");
            String expected = switch (game) {
                case "s1" -> "69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b";
                case "s2" -> "8bca5dcef1af3e00098666fd892dc1c2a76333f9";
                case "s3k" -> "cfbf98c36c776677290a872547ac47c53d2761d6";
                default -> throw new IllegalArgumentException(game);
            };
            require(sha1.equals(expected), "Unexpected production ROM identity: " + rom + " SHA1=" + sha1);
            sources.append("ROM SHA1 ").append(sha1).append(' ').append(rom).append('\n');
        }
        Files.writeString(visit.resolve("sources.txt"), sources);
        return identity;
    }

    private static String sourceIdentity(Path mod) throws Exception {
        StringBuilder identity = new StringBuilder();
        try (var paths = Files.walk(mod.resolve("src/main"))) {
            for (Path source : paths.filter(Files::isRegularFile).sorted().toList())
                identity.append(source).append(' ').append(hash(source, "SHA-256")).append('\n');
        }
        return identity.toString();
    }

    private static String hash(Path path, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void configureSubset(Path romRoot, Path visit, List<String> games) throws Exception {
        var configuration = GameServices.configuration();
        configuration.setConfigValue(SonicConfiguration.ROMS_DIRECTORY,
                Files.createDirectories(visit.resolve("empty-rom-directory")).toString());
        var keys = List.of(SonicConfiguration.SONIC_1_ROM, SonicConfiguration.SONIC_2_ROM,
                SonicConfiguration.SONIC_3K_ROM);
        for (int index = 0; index < ALL.size(); index++) {
            String game = ALL.get(index);
            // Explicit missing paths fail closed in the catalogue; no ROM files or links are created.
            Path path = games.contains(game) ? romRoot.resolve(game + ".gen") : visit.resolve("absent-" + game + ".gen");
            configuration.setConfigValue(keys.get(index), path.toString());
        }
        GameServices.rom().reloadCatalogue();
    }

    private static boolean available(String performer, List<String> games) {
        return switch (performer) {
            case "silver-sonic" -> games.contains("s2");
            case "knuckles", "mecha-sonic", "egg-robo" -> games.contains("s3k");
            case "tails" -> games.contains("s2") || games.contains("s3k");
            default -> true;
        };
    }

    private static void performances(ExampleModHarness harness, Path visit) throws Exception {
        List<String> ids = strings(harness, "availableSongs");
        require(ids.contains("green-hill"), "Public registry has no Green Hill acceptance song");
        for (int index = 0; index < ROLES.size(); index++) {
            String performer = List.of("knuckles", "egg-robo", "silver-sonic", "mecha-sonic").get(index);
            performance(harness, visit, "green-hill", ROLES.get(index), performer,
                    DIFFICULTIES.get(index), index == 0);
        }
        // Native S2/S3K stages with performers sourced from another installed ROM.
        for (String game : List.of("s2", "s3k")) {
            String representative = game.equals("s2") ? "chemical-plant" : "angel-island-1";
            Object source = publicSongs(harness).stream().filter(song -> uncheckedId(song).equals(representative))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing public stage acceptance song: " + representative));
            String song = (String) method(source, "id");
            @SuppressWarnings("unchecked")
            List<Object> roles = (List<Object>) method(source, "availableRoles");
            performance(harness, visit, song, roles.getFirst().toString(), "sonic", "MEDIUM", false);
        }
    }

    private static String uncheckedId(Object song) {
        // Stream predicate cannot propagate reflection exceptions; preserve their cause.
        try { return (String) method(song, "id"); }
        catch (Exception failure) { throw new IllegalStateException("Cannot read public song ID", failure); }
    }

    private static void titleScenes(ExampleModHarness harness, Path visit) throws Exception {
        for (var item : Map.of(5, "ONLINE", 6, "RECORDS", 7, "HELP").entrySet()) {
            jump(harness, "title");
            select(harness, item.getKey());
            press(harness, GLFW.GLFW_KEY_ENTER);
            requireScreen(harness, item.getValue());
            capture(harness, visit, item.getValue().toLowerCase(Locale.ROOT));
            press(harness, GLFW.GLFW_KEY_ESCAPE);
            requireScreen(harness, "TITLE");
        }
    }

    /** Remapping, both player settings, and calibration via visible controls. */
    private static void settings(ExampleModHarness harness, Path visit) throws Exception {
        jump(harness, "title");
        select(harness, 8);
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "SETTINGS");
        capture(harness, visit, "settings-keyboard");
        press(harness, GLFW.GLFW_KEY_ENTER);
        require((Boolean) field(harness.scene(), "capturingBinding"), "Binding capture not armed");
        capture(harness, visit, "settings-awaiting-binding");
        press(harness, GLFW.GLFW_KEY_F);
        require(!(Boolean) field(harness.scene(), "capturingBinding"), "Binding capture did not consume F");
        Object setting = field(harness.scene(), "settings");
        Object binding = setting.getClass().getMethod("binding", boolean.class, boolean.class, int.class)
                .invoke(setting, false, false, 0);
        require((Integer) method(binding, "code") == GLFW.GLFW_KEY_F, "Remapped binding not applied");
        press(harness, GLFW.GLFW_KEY_TAB);
        require((Boolean) field(harness.scene(), "settingsPad"), "Gamepad settings not selected");
        capture(harness, visit, "settings-gamepad");
        press(harness, GLFW.GLFW_KEY_TAB);
        int inputOffset = (Integer) method(setting, "inputOffsetMs");
        int displayOffset = (Integer) method(setting, "displayOffsetMs");
        select(harness, 10);
        press(harness, GLFW.GLFW_KEY_RIGHT);
        require((Integer) method(setting, "inputOffsetMs") == inputOffset + 5, "Input offset edit not applied");
        select(harness, 11);
        press(harness, GLFW.GLFW_KEY_RIGHT);
        require((Integer) method(setting, "displayOffsetMs") == displayOffset + 5, "Display offset edit not applied");
        capture(harness, visit, "settings-offsets");
        select(harness, 15);
        press(harness, GLFW.GLFW_KEY_ENTER);
        require((Boolean) field(harness.scene(), "settingsSecond"), "Player two settings not selected");
        capture(harness, visit, "settings-player-two");
        press(harness, GLFW.GLFW_KEY_ENTER);
        select(harness, 13);
        press(harness, GLFW.GLFW_KEY_ENTER);
        awaitLoading(harness, "CALIBRATION");
        capture(harness, visit, "calibration-start");
        ScenePreparedMusic prepared = (ScenePreparedMusic) field(harness.scene(), "prepared");
        SceneMusicPlayer player = (SceneMusicPlayer) field(harness.scene(), "player");
        long[] onsets = prepared.notes().stream()
                .filter(note -> note.kind() == com.openggf.mods.scene.SceneNoteEvent.Kind.DAC)
                .mapToLong(com.openggf.mods.scene.SceneNoteEvent::onsetSamples).distinct().sorted().toArray();
        Object kick = setting.getClass().getMethod("binding", boolean.class, boolean.class, int.class)
                .invoke(setting, true, false, 4);
        int tapKey = (Integer) method(kick, "code");
        long deadline = deadline(LOADING_TIMEOUT);
        int remaining = finishSteps(prepared);
        for (long onset : onsets) {
            while (player.samplePosition() < onset && screen(harness).equals("CALIBRATION")) {
                require(remaining-- > 0 && System.nanoTime() < deadline, "Calibration tap wait exceeded bound");
                tick(harness);
            }
            requireScreen(harness, "CALIBRATION");
            press(harness, tapKey);
            if (((List<?>) field(harness.scene(), "calibration")).size() >= 8) break;
        }
        require(((List<?>) field(harness.scene(), "calibration")).size() >= 8, "Calibration did not accept eight real tap inputs");
        capture(harness, visit, "calibration-ready");
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "SETTINGS");
        require(field(harness.scene(), "notice").equals("Calibration saved"), "Calibration save was not acknowledged");
        capture(harness, visit, "settings-calibrated");
        press(harness, GLFW.GLFW_KEY_ESCAPE);
        requireScreen(harness, "TITLE");
    }

    private static void performance(ExampleModHarness harness, Path visit, String song, String role,
            String performer, String difficulty, boolean finish) throws Exception {
        String name = song + "-" + role.toLowerCase(Locale.ROOT) + "-" + performer + "-" + difficulty.toLowerCase(Locale.ROOT);
        jump(harness, "perform:" + song + ":" + role + ":" + performer + ":" + difficulty);
        capture(harness, visit, "loading-" + name);
        awaitLoading(harness, "PLAY");
        require(((Integer) invoke(harness, "chartNotes")) > 0, "Empty production-ROM chart");
        verifyStage(harness);
        jump(harness, "autoplay");
        for (int step = 0; step < 420; step++) tick(harness);
        requireScreen(harness, "PLAY");
        require((Long) invoke(harness, "score") > 0, "No judged notes after count-in");
        capture(harness, visit, "play-" + name);
        verifyActor(harness, performer);
        SceneMusicPlayer player = (SceneMusicPlayer) field(harness.scene(), "player");
        press(harness, GLFW.GLFW_KEY_ESCAPE);
        requireScreen(harness, "PAUSED");
        long pausedAt = player.samplePosition();
        capture(harness, visit, "paused-" + name);
        for (int step = 0; step < 12; step++) tick(harness);
        require(player.samplePosition() == pausedAt, "Audio advanced while paused");
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "PLAY");
        require(player.samplePosition() >= pausedAt, "Resume moved audio backwards");
        if (finish) {
            ScenePreparedMusic prepared = (ScenePreparedMusic) field(harness.scene(), "prepared");
            int bound = finishSteps(prepared);
            long wallDeadline = deadline(Duration.ofSeconds(Math.max(120, bound / 30)));
            int steps = 0;
            while (!screen(harness).equals("RESULTS") && steps < bound && System.nanoTime() < wallDeadline) {
                tick(harness); steps++;
                if (((Long) field(harness.scene(), "eofNanos")) != Long.MIN_VALUE) Thread.sleep(10);
            }
            require(screen(harness).equals("RESULTS"), "Natural finish exceeded duration-derived bound: steps="
                    + steps + "/" + bound + " screen=" + screen(harness) + " sample=" + player.samplePosition());
            require(((Long) invoke(harness, "score")) > 0, "Autoplay score remained zero");
            require(!(Boolean) field(harness.scene(), "saved"), "Demo unexpectedly saved a record");
            capture(harness, visit, "results-" + name);
            press(harness, GLFW.GLFW_KEY_ENTER);
            awaitLoading(harness, "PLAY");
            capture(harness, visit, "retry-" + name);
            System.out.println("NATURAL FINISH " + name + ": bound=" + bound + " actual additional steps=" + steps
                    + " ROM samples=" + prepared.lengthSamples() + " rate=" + prepared.sampleRate());
        }
        jump(harness, "title");
        tick(harness);
        requireScreen(harness, "TITLE");
    }

    private static void localPerformance(ExampleModHarness harness, Path visit) throws Exception {
        for (int titleItem : List.of(3, 4)) localPerformance(harness, visit, titleItem);
    }

    private static void localPerformance(ExampleModHarness harness, Path visit, int titleItem) throws Exception {
        String mode = titleItem == 3 ? "LOCAL_COOP" : "LOCAL_VERSUS";
        String name = mode.toLowerCase(Locale.ROOT);
        jump(harness, "title");
        select(harness, titleItem);
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "CHARACTERS");
        select(harness, ROSTER.indexOf("knuckles"));
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "CHARACTERS");
        require((Boolean) field(harness.scene(), "choosingSecond"), "Local second performer step missing");
        select(harness, ROSTER.indexOf("silver-sonic"));
        capture(harness, visit, name + "-player-two-selection");
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "ROLES");
        select(harness, ROLES.indexOf("BONGOS"));
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "DIFFICULTY");
        select(harness, 1);
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "SONGS");
        select(harness, strings(harness, "availableSongs").indexOf("green-hill"));
        press(harness, GLFW.GLFW_KEY_ENTER);
        awaitLoading(harness, "PLAY");
        require(invoke(harness, "mode").equals(mode), "Local mode missing: " + mode);
        Object first = field(harness.scene(), "session"), second = field(harness.scene(), "session2");
        require(first != null && second != null && first != second, "Local players do not have independent sessions");
        require(field(harness.scene(), "highway") != field(harness.scene(), "highway2"), "Shared local highway");
        jump(harness, "autoplay");
        for (int step = 0; step < 420; step++) tick(harness);
        requireScreen(harness, "PLAY");
        require((Long) method(first, "score") > 0 && (Long) method(second, "score") > 0, "Local highways did not judge both players");
        capture(harness, visit, name + "-two-highways");
        verifyActor(harness, "knuckles");
        verifyActor(harness, "p2-silver-sonic");
        press(harness, GLFW.GLFW_KEY_ESCAPE);
        requireScreen(harness, "PAUSED");
        capture(harness, visit, name + "-paused");
        press(harness, GLFW.GLFW_KEY_ENTER);
        requireScreen(harness, "PLAY");
        jump(harness, "title");
    }

    /** Async preparation gets a wall deadline and yields CPU to its worker; never a tick-only cap. */
    private static void awaitLoading(ExampleModHarness harness, String expected) throws Exception {
        long limit = deadline(LOADING_TIMEOUT);
        while (screen(harness).equals("LOADING")) {
            require(System.nanoTime() < limit, "Async ROM preparation exceeded " + LOADING_TIMEOUT);
            tick(harness);
            if (screen(harness).equals("LOADING")) Thread.sleep(2);
        }
        requireScreen(harness, expected);
    }

    private static long deadline(Duration timeout) { return System.nanoTime() + timeout.toNanos(); }

    /** Actual prepared duration + three-second count-in + ten seconds for the finite judgment tail. */
    private static int finishSteps(ScenePreparedMusic prepared) {
        return Math.toIntExact((prepared.lengthSamples() * 60 + prepared.sampleRate() - 1) / prepared.sampleRate() + 180 + 600);
    }

    private static void select(ExampleModHarness harness, int target) throws Exception {
        require(target >= 0, "Missing menu target");
        int bound = Math.max(32, strings(harness, "availableSongs").size() + 1);
        for (int step = 0; (Integer) field(harness.scene(), "selected") != target && step < bound; step++)
            press(harness, GLFW.GLFW_KEY_DOWN);
        require((Integer) field(harness.scene(), "selected") == target, "Menu target not reachable: " + target);
    }

    private static void click(ExampleModHarness harness, int x, int y) throws Exception {
        harness.input().handleMouseMove(x, y);
        harness.input().handleMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS);
        tick(harness);
        harness.input().handleMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE);
        // Move outside menus before releasing: a stationary cursor must not retarget later keyboard steps.
        harness.input().handleMouseMove(399, 223);
        tick(harness);
    }

    private static void tick(ExampleModHarness harness) throws Exception {
        harness.tick();
        HeadlessGameBoot.presentHeadlessOuterAudioFrame();
        require(!screen(harness).equals("ERROR"), "Scene error: " + invoke(harness, "error"));
        require(harness.findings().isEmpty(), "Mod fault findings: " + harness.findings());
    }

    private static void press(ExampleModHarness harness, int key) throws Exception {
        harness.input().handleKeyEvent(key, GLFW.GLFW_PRESS);
        tick(harness);
        harness.input().handleKeyEvent(key, GLFW.GLFW_RELEASE);
        tick(harness);
    }

    private static void jump(ExampleModHarness harness, String command) {
        require(harness.debugJump(command), "Rejected debug capture command: " + command);
    }

    private static void capture(ExampleModHarness harness, Path visit, String name) throws Exception {
        GL11.glViewport(0, 0, WIDTH, HEIGHT);
        GL11.glClearColor(0, 0, 0, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        float[] projection = {2f / WIDTH, 0, 0, 0, 0, 2f / HEIGHT, 0, 0, 0, 0, -1, 0, -1, -1, 0, 1};
        harness.host().draw(projection, new int[] {0, 0, WIDTH, HEIGHT});
        GL11.glFinish();
        var image = ScreenshotCapture.captureFramebuffer(WIDTH, HEIGHT);
        ScreenshotCapture.savePNG(image, visit.resolve(name + ".png"));
        // Flat-colour help/settings panels need few colours. Native art is checked
        // separately, so a colourful HUD cannot certify a missing concert stage.
        long colors = java.util.Arrays.stream(image.pixels()).distinct().count();
        long visible = java.util.Arrays.stream(image.pixels()).filter(pixel -> (pixel & 0xFFFFFF) != 0).count();
        require(colors > 4 && visible > WIDTH * HEIGHT / 20, "Blank capture: " + name + " colors=" + colors + " visible=" + visible);
        require(harness.findings().isEmpty(), "Draw fault findings: " + harness.findings());
    }

    private static void verifyStage(ExampleModHarness harness) throws Exception {
        SceneBackdrop backdrop = (SceneBackdrop) field(harness.scene(), "backdrop");
        SceneImage foreground = (SceneImage) field(harness.scene(), "foreground");
        List<String> failures = new ArrayList<>();
        if (backdrop == null) failures.add("missing backdrop");
        else if (nonblackColors(backdrop.image()) <= 8) failures.add("empty backdrop palette/art");
        if (foreground == null || opaquePixels(foreground) <= 1000) failures.add("missing/empty foreground");
        if (!failures.isEmpty()) {
            String evidence = field(harness.scene(), "song") + ": " + failures;
            STAGE_FAILURES.add(evidence);
            // Keep independent art/menu checks running, but the visit exits unsuccessfully.
            // Never downgrade missing native scenery into an acceptance pass.
            System.err.println("STAGE ACCEPTANCE FAILURE " + evidence);
            return;
        }
        System.out.println("STAGE " + field(harness.scene(), "song") + ": backdrop "
                + backdrop.image().width() + "x" + backdrop.image().height() + ", foreground " + opaquePixels(foreground));
    }

    private static void verifyActor(ExampleModHarness harness, String performer) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> actors = (Map<String, Object>) field(harness.scene(), "actors");
        Object actor = actors.get(performer);
        require(actor != null, "Performer was not decoded: " + performer);
        String nativeId = performer.startsWith("p2-") ? performer.substring(3) : performer;
        require(field(actor, "performer").equals(nativeId), "Wrong native actor ID: " + performer);
        @SuppressWarnings("unchecked")
        List<String> games = (List<String>) field(harness.scene(), "games");
        String donor = switch (nativeId) {
            case "silver-sonic" -> "s2";
            case "knuckles", "mecha-sonic", "egg-robo" -> "s3k";
            default -> games.contains("s3k") ? "s3k" : games.contains("s2") ? "s2" : "s1";
        };
        require(field(actor, "game").equals(donor), "Wrong ROM donor for " + performer + ": " + field(actor, "game"));
        SceneSpriteSet character = (SceneSpriteSet) field(actor, "character");
        int pixels = 0;
        int colors = 0;
        if (character != null) {
            int[] frames = character.animationFrames(5);
            if (frames.length == 0) frames = new int[] {0};
            for (int frame : java.util.Arrays.stream(frames).distinct().toArray()) {
                SceneImage image = character.frame(frame).image();
                require(opaquePixels(image) > 50 && nonblackColors(image) >= 3,
                        performer + " animation frame " + frame + " is invisible or has no palette");
                pixels = Math.max(pixels, opaquePixels(image));
                colors = Math.max(colors, nonblackColors(image));
            }
        } else {
            for (Object layer : (List<?>) field(actor, "layers")) {
                SceneSpriteSet set = (SceneSpriteSet) method(layer, "set");
                int frame = (Integer) method(layer, "frame");
                SceneImage image = set.frame(frame).image();
                pixels += opaquePixels(image);
                colors = Math.max(colors, nonblackColors(image));
            }
        }
        require(pixels > 50 && colors >= 3, "Invisible or uncoloured performer: " + performer);
        Object rig = field(actor, "rig");
        require(rig != null, "Rendered performer did not build native cutouts: " + performer);
        Object split = field(rig, "pixels");
        SceneSprite body = (SceneSprite) method(split, "body");
        require(opaquePixels(body.image()) > 30 && nonblackColors(body.image()) >= 3,
                "Native cutout body is empty: " + performer);
        List<?> limbs = (List<?>) method(split, "limbs");
        int armCount = (Integer) field(rig, "armCount");
        require(armCount >= 1 && limbs.size() >= armCount, "Missing native articulated arms: " + performer);
        for (int index = 0; index < limbs.size(); index++) {
            SceneSprite limb = (SceneSprite) field(limbs.get(index), "nativePart");
            require(opaquePixels(limb.image()) > 0, "Blank native limb " + index + " for " + performer);
        }
        SceneSpriteSet tail = (SceneSpriteSet) field(actor, "tails");
        if (tail != null) for (int frame = 0x22; frame <= 0x26; frame++)
            require(opaquePixels(tail.frame(frame).image()) > 0, "Blank standing tail frame " + frame);
        System.out.println("ACTOR " + performer + ": ROM=" + donor + " opaque=" + pixels + " palette-colors=" + colors
                + (tail == null ? "" : " standing-tail=" + opaquePixels(tail.frame(0x22).image())));
    }

    private static int opaquePixels(SceneImage image) {
        int count = 0;
        for (int pixel : image.pixels()) if ((pixel >>> 24) != 0) count++;
        return count;
    }

    private static int nonblackColors(SceneImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int pixel : image.pixels()) if ((pixel >>> 24) != 0 && (pixel & 0xFFFFFF) != 0) colors.add(pixel);
        return colors.size();
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object method(Object owner, String name) throws Exception {
        var method = owner.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(owner);
    }

    private static Object invoke(ExampleModHarness harness, String name) throws Exception {
        return harness.scene().getClass().getMethod(name).invoke(harness.scene());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(ExampleModHarness harness, String name) throws Exception {
        return (List<String>) invoke(harness, name);
    }

    private static String screen(ExampleModHarness harness) throws Exception { return (String) invoke(harness, "screen"); }
    private static void requireScreen(ExampleModHarness harness, String expected) throws Exception {
        require(screen(harness).equals(expected), "Expected " + expected + ", got " + screen(harness));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
