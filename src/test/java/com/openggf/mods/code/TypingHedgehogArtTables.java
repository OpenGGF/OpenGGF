package com.openggf.mods.code;

import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Checks Typing of the Hedgehog's art tables against the ROM: for every shipped rail of a game,
 * loads the rail's act and requires that each badnik frame of the zone's cast, each prop frame
 * and each frame of the zone's boss rig names a sheet the act registered and a frame inside it.
 * A typo or a sheet the act never loads would otherwise draw nothing, silently.
 *
 * <p>The tables are read as text (the formats are {@code typinghog.play.Cast}, {@code Props} and
 * {@code BossRig}), independent of the mod's own parsers.
 */
final class TypingHedgehogArtTables {
    static final Path RESOURCES = Path.of("examples/typing-of-the-hedgehog/src/main/resources");
    /** Idle frames stepped before the art provider is read. */
    static final int SETTLE_FRAMES = 240;

    private TypingHedgehogArtTables() { }

    record Use(String table, String artKey, int frame) { }

    /** Loads each of {@code game}'s rail acts and fails listing every frame its act cannot draw. */
    static void check(SonicGame game, String code) throws Exception {
        Map<String, int[]> rails = rails(code);
        assertTrue(!rails.isEmpty() || !code.equals("s1"), "Sonic 1 ships rails");
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (var entry : rails.entrySet()) {
            int zone = entry.getValue()[0], act = entry.getValue()[1];
            SharedLevel level = SharedLevel.load(game, zone, act);
            try {
                // The same bootstrap the course test uses (TestTypingHedgehogCourse.launch), then a few
                // seconds of play, as the course reads art only after its first rows.
                GameModule module = GameServices.module();
                SessionManager.clear();
                GameModuleRegistry.setCurrent(module);
                TestEnvironment.activeGameplayMode();
                HeadlessTestFixture.builder().withZoneAndAct(zone, act).build().stepIdleFrames(SETTLE_FRAMES);
                // The level's render manager holds the provider the act loaded, which is what puppets
                // draw through; Sonic1GameModule.getObjectArtProvider() returns a fresh, empty provider
                // on every call, so asking the module would check nothing.
                var art = GameServices.level().getObjectRenderManager().getArtProvider();
                System.out.println("[typing-hedgehog] " + entry.getKey() + ": " + art.getRendererKeys().size()
                        + " art keys registered");
                for (Use use : uses(code, zone, act)) {
                    checked++;
                    var sheet = art.getSheet(use.artKey());
                    if (art.getRenderer(use.artKey()) == null || sheet == null) {
                        problems.add(entry.getKey() + ": " + use.table() + " names " + use.artKey()
                                + ", which the act does not register");
                    } else if (use.frame() < 0 || use.frame() >= sheet.getFrameCount()) {
                        problems.add(entry.getKey() + ": " + use.table() + " " + use.artKey() + " frame "
                                + use.frame() + " of " + sheet.getFrameCount());
                    }
                }
            } finally {
                level.dispose();
            }
        }
        System.out.println("[typing-hedgehog] " + code + " art tables: " + rails.size() + " rail acts, "
                + checked + " frame uses checked");
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    /** Rail name to {zone, act} for {@code code}'s shipped rails. */
    static Map<String, int[]> rails(String code) throws Exception {
        Map<String, int[]> out = new LinkedHashMap<>();
        for (String line : Files.readString(RESOURCES.resolve("rails/index.txt")).split("\n")) {
            String name = line.strip();
            if (name.isEmpty() || name.startsWith("#")) continue;
            String game = null;
            int zone = -1, act = -1;
            for (String raw : Files.readString(RESOURCES.resolve("rails/" + name)).split("\n")) {
                String[] f = raw.strip().split("\\s+");
                if (f.length < 2) continue;
                switch (f[0]) {
                    case "game" -> game = f[1];
                    case "zone" -> zone = Integer.parseInt(f[1]);
                    case "act" -> act = Integer.parseInt(f[1]);
                    default -> { }
                }
            }
            if (code.equals(game)) out.put(name, new int[] {zone, act});
        }
        return out;
    }

    /** Every (art key, frame) the zone's cast, the game's props and the zone's boss rig draw. */
    static Set<Use> uses(String code, int zone, int act) throws Exception {
        Set<Use> uses = new LinkedHashSet<>();
        for (String[] f : rows("cast/" + code + ".txt")) {
            String[] at = f[0].split("@");
            if (Integer.parseInt(at[0]) != zone || (at.length > 1 && Integer.parseInt(at[1]) != act)) continue;
            for (String frame : f[8].split(",")) uses.add(new Use("cast " + f[1], f[2], Integer.decode(frame)));
        }
        for (String[] f : rows("props/" + code + ".txt")) uses.add(new Use("prop " + f[0], f[1], Integer.decode(f[2])));
        for (String key : List.of("explosion", "animal", "points")) uses.add(new Use("effects", key, 0));
        // The rig the course uses for this act: its zone@act lines, else its zone's, else the * lines.
        List<String[]> rig = rows("bosses/" + code + ".txt");
        String key = "*";
        for (String candidate : List.of(zone + "@" + act, Integer.toString(zone))) {
            if (rig.stream().anyMatch(f -> f[0].equals(candidate))) {
                key = candidate;
                break;
            }
        }
        for (String[] f : rig) {
            if (!f[0].equals(key) || f[1].equals("hover")) continue;
            for (int i = 5; i < f.length; i++) {
                String token = f[i];
                if (token.startsWith("links=")) {
                    String[] l = token.substring(6).split(":");
                    uses.add(new Use("boss " + f[1] + " links", l[0], Integer.decode(l[1])));
                } else if (token.matches("[a-z*]+=\\d+:[0-9x,]+")) {
                    for (String frame : token.substring(token.indexOf(':') + 1).split(",")) {
                        uses.add(new Use("boss " + f[1], f[2], Integer.decode(frame)));
                    }
                }
            }
        }
        return uses;
    }

    private static List<String[]> rows(String path) throws Exception {
        Path file = RESOURCES.resolve(path);
        List<String[]> out = new ArrayList<>();
        if (!Files.exists(file)) return out;
        for (String raw : Files.readString(file).split("\n")) {
            String line = raw.strip();
            if (!line.isEmpty() && !line.startsWith("#")) out.add(line.split("\\s+"));
        }
        return out;
    }
}
