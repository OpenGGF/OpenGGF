package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

import com.openggf.game.GameServices;
import com.openggf.mods.scene.host.SceneHostTestAccess;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Plays the Flappy Tails scene against a real S3K session with no GL: every zone's pictures and
 * title card come out of the ROM, the title-card lettering spells the game's words, a real
 * flight crashes into the results and saves records, and the autopilot flies the whole tour
 * into Super Tails without the fault boundary catching anything.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestFlappyTailsScene {
    private static final Path PROJECT = Path.of("examples/flappy-tails");

    @TempDir
    Path work;

    private SharedLevel level;
    private ExampleModHarness harness;

    @AfterEach
    void close() throws Exception {
        if (harness != null) harness.close();
        if (level != null) level.dispose();
    }

    private void open() throws Exception {
        level = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        harness = ExampleModHarness.build(PROJECT, work.resolve("build"));
        assertEquals("WIDE_16_9", harness.plan().requiredDisplayAspect());
        harness.open(harness.apply(GameServices.module()), work.resolve("saves"), 400, 224);
        play(2);
    }

    private void play(int ticks) {
        for (int i = 0; i < ticks; i++) {
            harness.tick();
            harness.host().draw(null, null);
        }
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private Object call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    @Test
    void everyZoneIsPicturedFromTheRomAndTheCardLettersSpellTheGame() throws Exception {
        open();
        Object scene = harness.scene();
        Object zones = field(scene, "zones");
        Object cards = field(scene, "cards");
        assertEquals(5, Array.getLength(zones));
        for (int i = 0; i < 5; i++) {
            Object art = Array.get(zones, i);
            assertNotNull(field(art, "backdrop"), "backdrop " + i);
            assertNotNull(field(art, "pillar"), "pillar " + i);
            assertNotNull(field(art, "ground"), "ground " + i);
            assertNotNull(Array.get(cards, i), "title card " + i);
        }
        Object font = field(scene, "font");
        Method canSpell = font.getClass().getDeclaredMethod("canSpell", String.class);
        canSpell.setAccessible(true);
        for (String word : List.of("FLAPPY TAILS", "GET READY", "GAME OVER", "SUPER TAILS", "PAUSE", "RECORDS")) {
            assertEquals(true, canSpell.invoke(font, word), word);
        }
        int worldOnly = SceneHostTestAccess.lastFrameOps(harness.host());
        play(60);                                          // the menu fades in after the logo, from tick 30
        int withMenu = SceneHostTestAccess.lastFrameOps(harness.host());
        assertTrue(worldOnly > 0, "the title drew its world");
        assertTrue(withMenu > worldOnly, "the menu added to the title: " + worldOnly + " -> " + withMenu);
        assertEquals(Map.of(), harness.findings());
    }

    @Test
    void aFlightCrashesIntoTheResultsSavesRecordsAndFliesAgain() throws Exception {
        open();
        play(40);
        harness.press(GLFW_KEY_ENTER);                     // PLAY
        play(2);
        assertEquals("PLAY", field(harness.scene(), "screen").toString());
        harness.press(GLFW_KEY_SPACE);                     // the first flap leaves GET READY
        play(600);                                         // then nothing: Tails glides into the ground
        assertEquals("RESULTS", field(harness.scene(), "screen").toString());
        try (var files = Files.walk(work.resolve("saves"))) {
            Path records = files.filter(p -> p.getFileName().toString().equals("records.txt")).findFirst()
                    .orElseThrow(() -> new AssertionError("no records.txt saved"));
            assertTrue(Files.readString(records).contains("flights=1"), Files.readString(records));
        }
        play(80);
        harness.press(GLFW_KEY_SPACE);                     // finish the tally
        harness.press(GLFW_KEY_SPACE);                     // fly again
        play(2);
        assertEquals("PLAY", field(harness.scene(), "screen").toString());
        assertEquals(Map.of(), harness.findings());
    }

    @Test
    void theAutopilotFliesTheWholeTourIntoSuperTails() throws Exception {
        open();
        assertTrue(harness.debugJump("seed:0x5eed"));
        assertTrue(harness.debugJump("autopilot:on"));
        assertTrue(harness.debugJump("play:classic"));
        Object run = null;
        for (int i = 0; i < 20_000; i++) {
            harness.tick();
            if (i % 30 == 0) harness.host().draw(null, null);
            run = field(harness.scene(), "run");
            if (run != null && (int) call(run, "score") >= 52) break;
        }
        assertNotNull(run);
        assertTrue((int) call(run, "score") >= 52, "the autopilot reached lap two");
        assertEquals(Map.of(), harness.findings());
        assertEquals(List.of(), harness.exits());
    }

    @Test
    void menusMoveAndUnknownDebugCommandsAreRefused() throws Exception {
        open();
        play(40);
        harness.press(GLFW_KEY_DOWN);
        harness.press(GLFW_KEY_DOWN);
        harness.press(GLFW_KEY_ENTER);                     // RECORDS
        assertEquals("RECORDS", field(harness.scene(), "screen").toString());
        assertTrue(!harness.debugJump("play:hyper"));
        assertTrue(!harness.debugJump("play:classic:9"));
        assertTrue(!harness.debugJump("seed:pigeon"));
        assertTrue(harness.debugJump("title"));
        play(2);
        harness.press(GLFW_KEY_DOWN);                      // the cursor stayed on RECORDS: one down is EXIT
        harness.press(GLFW_KEY_ENTER);
        assertEquals(List.of("game"), harness.exits());
        assertEquals(Map.of(), harness.findings());
    }
}
