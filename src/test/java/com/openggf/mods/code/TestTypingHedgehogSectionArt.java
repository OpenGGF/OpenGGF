package com.openggf.mods.code;

import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual section frame uses have visible ROM pixels without installing native boss PLCs. */
class TestTypingHedgehogSectionArt {
    private static final Path EXAMPLE = Path.of("examples/typing-of-the-hedgehog");
    private static final Path RESOURCES = EXAMPLE.resolve("src/main/resources");
    @TempDir static Path work;
    static ExampleModHarness harness;
    static Class<?> sectionArt;
    static Class<?> castType;
    static Class<?> propsType;
    static Method prepare;
    static Method problem;

    @BeforeAll static void build() throws Exception {
        harness = ExampleModHarness.build(EXAMPLE, work.resolve("mod"));
        sectionArt = harness.loader().loadClass("typinghog.play.SectionArt");
        castType = harness.loader().loadClass("typinghog.play.Cast");
        propsType = harness.loader().loadClass("typinghog.play.Props");
        prepare = sectionArt.getMethod("prepare", ObjectServices.class, String.class, int.class, int.class,
                castType, propsType);
        problem = sectionArt.getMethod("problem", ObjectSpriteSheet.class, int.class);
    }

    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }

    @Test void frameGateRejectsBlankAndWrongBankPositiveControls() throws Exception {
        Pattern visible = new Pattern();
        visible.setPixel(3, 4, (byte) 7);
        var mapping = new SpriteMappingFrame(List.of(new SpriteMappingPiece(0, 0, 1, 1, 0,
                false, false, 2, true)));
        var blank = new ObjectSpriteSheet(new Pattern[] {new Pattern()}, List.of(mapping), 0, 1);
        var real = new ObjectSpriteSheet(new Pattern[] {visible}, List.of(mapping), 0, 1);
        assertEquals("transparent mapped pixels", problem.invoke(null, blank, 0));
        assertNull(problem.invoke(null, real, 0));
        // A populated bank elsewhere is insufficient: only the tiles the mapping actually draws count.
        var unrelated = new ObjectSpriteSheet(new Pattern[] {new Pattern(), visible}, List.of(mapping), 0, 1);
        assertEquals("transparent mapped pixels", problem.invoke(null, unrelated, 0));
        var empty = new ObjectSpriteSheet(new Pattern[] {visible}, List.of(new SpriteMappingFrame(List.of())), 0, 1);
        assertEquals("empty mapping", problem.invoke(null, empty, 0));
        assertNotNull(problem.invoke(null, real, 1));
    }

    @Test @RequiresRom(SonicGame.SONIC_1)
    void allSevenSonic1StopsHaveVisibleArt() throws Exception { check("s1", SonicGame.SONIC_1, 7); }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void allElevenSonic2StopsHaveVisibleArt() throws Exception { check("s2", SonicGame.SONIC_2, 11); }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void allThirteenS3kStopsHaveVisibleArt() throws Exception { check("s3k", SonicGame.SONIC_3K, 13); }

    private static void check(String game, SonicGame sonicGame, int expected) throws Exception {
        Object cast = castType.getMethod("parse", String.class).invoke(null, text("cast/" + game + ".txt"));
        Class<?> rigType = harness.loader().loadClass("typinghog.play.BossRig");
        Object rigs = rigType.getMethod("parse", String.class).invoke(null, text("bosses/" + game + ".txt"));
        castType.getMethod("withBosses", Map.class).invoke(cast, rigs);
        Object props = propsType.getMethod("parse", String.class).invoke(null, text("props/" + game + ".txt"));
        int count = 0;
        List<org.junit.jupiter.api.function.Executable> stops = new ArrayList<>();
        for (String row : text("hub/story.txt").split("\n")) {
            String[] fields = row.strip().split("\\s+");
            if (!fields[0].equals(game)) continue;
            int zone = Integer.parseInt(fields[1]), act = Integer.parseInt(fields[2]);
            Object parsedCast = cast;
            stops.add(() -> checkStop(game, sonicGame, zone, act, parsedCast, props));
            count++;
        }
        assertEquals(expected, count);
        assertAll(game + " section art", stops);
        assertTrue(harness.findings().isEmpty(), harness.findings().toString());
    }

    private static void checkStop(String game, SonicGame sonicGame, int zone, int act, Object cast, Object props)
            throws Exception {
        var bootstrap = SharedLevel.load(sonicGame, zone, act);
        try {
            var module = GameServices.module();
            SessionManager.clear();
            GameModuleRegistry.setCurrent(module);
            TestEnvironment.activeGameplayMode();
            var builder = HeadlessTestFixture.builder().withZoneAndAct(zone, act);
            if (sonicGame == SonicGame.SONIC_3K
                    && zone == Sonic3kZoneIds.ZONE_DDZ && act == 0) {
                builder.withFreshLevelStartLifecycle();
            }
            var fixture = builder.build();
            fixture.stepIdleFrames(240);
            var services = GameServices.level().getObjectManager().getObjectServices();
            var provider = services.renderManager().getArtProvider();
            var keys = List.copyOf(provider.getRendererKeys());
            Map<String, ObjectSpriteSheet> originalSheets = new LinkedHashMap<>();
            for (String key : keys) originalSheets.put(key, provider.getSheet(key));
            var level = services.currentLevel();
            long levelPixels = fingerprint(level);
            try (var art = (AutoCloseable) prepare.invoke(null, services, game, zone, act, cast, props)) {
                assertSame(level, services.currentLevel(), "preparation retains the selected world");
                assertEquals(keys, provider.getRendererKeys(), "private art never registers native renderers");
                for (var entry : originalSheets.entrySet()) assertSame(entry.getValue(), provider.getSheet(entry.getKey()));
                assertEquals(levelPixels, fingerprint(level), "private art never writes native VRAM");
                @SuppressWarnings("unchecked") Map<String, ObjectSpriteSheet> sheets =
                        (Map<String, ObjectSpriteSheet>) sectionArt.getMethod("sheets").invoke(art);
                @SuppressWarnings("unchecked") List<Object> uses = (List<Object>) sectionArt.getMethod("uses").invoke(art);
                assertFalse(uses.isEmpty());
                for (Object use : uses) {
                    String key = (String) use.getClass().getMethod("artKey").invoke(use);
                    int frame = (int) use.getClass().getMethod("frame").invoke(use);
                    assertNull(problem.invoke(null, sheets.get(key), frame), game + ":" + zone + ":" + act + " " + use);
                    ObjectSpriteSheet nativeSheet = originalSheets.get(key);
                    if (nativeSheet != null) assertNotSame(nativeSheet, sheets.get(key), "CPU sheet is detached");
                }
                // Stand/retry clear must keep the same prepared art and its priority-bearing mappings.
                var handType = harness.loader().loadClass("typinghog.play.Stagehand");
                Object hand = handType.getConstructor().newInstance();
                handType.getMethod("sectionArt", sectionArt).invoke(hand, art);
                handType.getMethod("clear").invoke(hand);
                assertSame(art, handType.getMethod("sectionArt").invoke(hand));
                handType.getMethod("clearSectionArt").invoke(hand);
                assertNull(handType.getMethod("sectionArt").invoke(hand));
                System.out.println("[typing-section-art] " + game + ":" + zone + ":" + act
                        + " sheets=" + sheets.size() + " visible-frame-uses=" + uses.size());
            }
        } finally {
            bootstrap.dispose();
            GameServices.configuration().clearSessionOverrides();
        }
    }

    private static long fingerprint(com.openggf.level.Level level) {
        long hash = 1;
        for (int i = 0; i < level.getPatternCount(); i++) {
            Pattern pattern = level.getPattern(i);
            if (pattern == null) { hash *= 31; continue; }
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) hash = hash * 31 + pattern.getPixel(x, y);
        }
        return hash;
    }

    private static String text(String relative) throws Exception { return Files.readString(RESOURCES.resolve(relative)); }
}
