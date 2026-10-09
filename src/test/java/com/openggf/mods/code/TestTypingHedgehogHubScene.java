package com.openggf.mods.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.mods.scene.DebuggableScene;
import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneArt;
import com.openggf.mods.scene.SceneAudio;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneKeys;
import com.openggf.mods.scene.SceneLaunch;
import com.openggf.mods.scene.SceneMouse;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.SceneStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Packages the real Typing of the Hedgehog mod (the validator rejects mutable static state)
 * and drives its hub scene against a scripted scene context: typed menus, the story's
 * hand-offs into gameplay and into another supplied game, results merged on return, a Boss
 * Rush carried on to its next boss, a Typing School drill played to its grade, options saved,
 * and every screen drawn without faults at the mod's 320-pixel width and at 400. ROM art is a
 * stand-in; the ROM-backed smoke test is {@link TestTypingHedgehogHubRom}.
 */
class TestTypingHedgehogHubScene {
    private static final Path RESOURCES = Path.of("examples/typing-of-the-hedgehog/src/main/resources");
    @TempDir static Path temp;
    static ExampleModHarness harness;
    static ClassLoader loader;

    @BeforeAll static void packageMod() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/typing-of-the-hedgehog"), temp.resolve("package"));
        loader = harness.loader();
        assertEquals("any", harness.manifest().baseGame());
    }

    @AfterAll static void close() throws Exception {
        if (harness != null) harness.close();
    }

    // ---- tests -------------------------------------------------------------------------------

    @Test void typingStartAndStoryWalksFromTitleToTheChapterMap() throws Exception {
        Fixture f = new Fixture("s1", List.of("s1"), new HashMap<>(), Set.of("s1:0:2"));
        assertEquals("TITLE", f.screen());
        f.step(40);
        f.type("start");
        f.step(45);
        assertEquals("MENU", f.screen());
        f.type("stx");
        f.step(1);
        assertEquals("MENU", f.screen(), "a slip does not select");
        f.type("story");
        f.step(1);
        assertEquals("STORY", f.screen());
        f.drawAll();
    }

    @Test void aRailedStoryStopHandsOffWithAnIntent() throws Exception {
        Fixture f = new Fixture("s1", List.of("s1"), new HashMap<>(), Set.of("s1:0:2"));
        f.debug("story");
        f.step(2);
        f.type("go");
        f.step(1);
        assertEquals(List.of("0:2"), f.started);
        String intent = f.saves.get("intent.txt");
        assertNotNull(intent, "the course reads intent.txt");
        assertTrue(intent.contains("mode=STORY") && intent.contains("game=s1") && intent.contains("act=2"), intent);
        assertTrue(intent.contains("voice=MEGADRIVE"), "the voice choice travels with the act: " + intent);
        assertEquals(intent, f.saves.get("hub-launch.txt"), "the hub keeps its own copy");
    }

    @Test void stopsWithoutARailAreUnderConstructionAndLockedStopsWait() throws Exception {
        Fixture f = new Fixture("s1", List.of("s1"), new HashMap<>(), Set.of("s1:0:2", "s1:2:2"));
        f.debug("story");
        f.step(2);
        f.type("marble");
        f.step(1);
        f.type("go");
        f.step(1);
        assertTrue(f.started.isEmpty(), "Marble has no rail");
        f.type("spring yard");
        f.step(1);
        f.type("go");
        f.step(1);
        assertTrue(f.started.isEmpty(), "Spring Yard waits for Green Hill, skipping the unbuilt Marble");
        assertEquals("STORY", f.screen());
    }

    @Test void aStopInAnotherGameRelaunchesAndTheReopenedHubCarriesOn() throws Exception {
        Map<String, String> saves = new HashMap<>();
        Fixture f = new Fixture("s1", List.of("s1", "s2"), saves, Set.of("s2:0:1"));
        f.debug("story:s2");
        f.step(2);
        f.type("go");
        f.step(1);
        assertEquals(List.of(new SceneLaunch("s2", "sonic", List.of())), f.relaunched);
        assertTrue(saves.containsKey("pending.txt"));
        assertTrue(f.started.isEmpty());
        f.scene.exit(f);

        Fixture reopened = new Fixture("s2", List.of("s1", "s2"), saves, Set.of("s2:0:1"));
        reopened.step(60);
        assertEquals(List.of("0:1"), reopened.started, "the reopened hub starts the chosen act");
        assertFalse(saves.containsKey("pending.txt"));
        assertTrue(saves.get("intent.txt").contains("game=s2"));
    }

    @Test void aWrongTeamRelaunchesTheSameGame() throws Exception {
        Fixture f = new Fixture("s1", List.of("s1"), new HashMap<>(), Set.of("s1:0:2"));
        f.launch = new SceneLaunch("s1", "sonic", List.of("tails"));
        f.scene.enter(f);
        f.debug("story");
        f.step(2);
        f.type("go");
        f.step(1);
        assertEquals(List.of(new SceneLaunch("s1", "sonic", List.of())), f.relaunched);
    }

    @Test void aReturnedResultIsShownMergedAndDeleted() throws Exception {
        Map<String, String> saves = new HashMap<>();
        saves.put("result.txt", result("STORY", 0, 2, true, true, 23456));
        saves.put("hub-launch.txt", "mode=STORY\ngame=s1\nzone=0\nact=2\ndifficulty=HARD\n");
        Fixture f = new Fixture("s1", List.of("s1"), saves, Set.of("s1:0:2", "s1:1:2"));
        assertEquals("RESULTS", f.screen());
        assertFalse(saves.containsKey("result.txt"));
        String profile = saves.get("profile.txt");
        assertTrue(profile.contains("cleared.s1=0:2"), profile);
        assertTrue(profile.contains("emeralds.s1=0:2"), profile);
        assertTrue(profile.contains("record.act.s1.0.2.HARD=23456/A/"), profile);
        assertTrue(profile.contains("keys.hits=a:30"), profile);
        f.step(5);
        f.key(SceneKeys.ENTER);
        f.drawAll();
        f.key(SceneKeys.ENTER);
        assertEquals("STORY", f.screen());
    }

    @Test void aBossRushCarriesOnToItsNextBoss() throws Exception {
        Map<String, String> saves = new HashMap<>();
        saves.put("profile.txt", "version=1\ncleared.s1=0:2,2:2\n");
        saves.put("rush.txt", "game=s1\nremaining=0:2,2:2\nwon=0\nscore=0\n");
        saves.put("result.txt", result("BOSS_RUSH", 0, 2, true, false, 5000));
        Fixture f = new Fixture("s1", List.of("s1"), saves, Set.of("s1:0:2", "s1:2:2"));
        assertEquals("RESULTS", f.screen());
        assertTrue(saves.get("rush.txt").contains("remaining=2:2"));
        assertTrue(saves.get("profile.txt").contains("stars.s1:0:2=1"));
        f.key(SceneKeys.ENTER);
        f.key(SceneKeys.ENTER);
        assertEquals(List.of("2:2"), f.started);
        assertTrue(saves.get("intent.txt").contains("mode=BOSS_RUSH"));
    }

    @Test void aTypingSchoolDrillPlaysToItsGrade() throws Exception {
        Map<String, String> saves = new HashMap<>();
        Fixture f = new Fixture("s2", List.of("s2"), saves, Set.of());
        assertTrue(f.debug("drill:HOME_ROW"));
        assertEquals("DRILL", f.screen());
        f.step(95);
        Object session = session(f);
        for (int guard = 0; guard < 40 && f.screen().equals("DRILL"); guard++) {
            List<?> targets = (List<?>) call(session, "targets");
            if (targets.isEmpty()) {
                f.step(1);
                continue;
            }
            Object prompt = call(targets.getFirst(), "prompt");
            String typed = (String) call(prompt, "typed");
            int progress = (int) call(targets.getFirst(), "progress");
            for (char ch : typed.substring(progress).toCharArray()) {
                f.step(5);
                f.type(String.valueOf(ch));
            }
            f.step(1);
        }
        assertEquals("GRADE", f.screen());
        f.drawAll();
        assertTrue(saves.get("profile.txt").contains("grade.HOME_ROW="), saves.get("profile.txt"));
        f.key(SceneKeys.ENTER);
        f.step(25);
        f.key(SceneKeys.ENTER);
        assertEquals("SCHOOL", f.screen());
    }

    @Test void typedOptionsToggleAndSave() throws Exception {
        Map<String, String> saves = new HashMap<>();
        Fixture f = new Fixture("s3k", List.of("s3k"), saves, Set.of());
        f.debug("options");
        f.step(1);
        f.type("tricky");
        f.step(1);
        f.type("expert");
        f.step(1);
        f.type("voice");
        f.step(1);
        String profile = saves.get("profile.txt");
        assertTrue(profile.contains("dict.tricky=true"), profile);
        assertTrue(profile.contains("difficulty=EXPERT"), profile);
        assertTrue(profile.contains("voice=CLEAN"), "voice cycles from the Mega Drive render to clean: " + profile);
        f.type("voice");
        f.step(1);
        assertTrue(saves.get("profile.txt").contains("voice=OFF"));
        f.type("mega drive");
        f.step(1);
        assertTrue(saves.get("profile.txt").contains("voice=MEGADRIVE"));
        f.drawAll();
    }

    @Test void everyScreenDrawsInEveryGameWithoutFaults() throws Exception {
        for (String game : List.of("s1", "s2", "s3k")) {
            for (int width : List.of(320, 400)) {
                Fixture f = new Fixture(game, List.of(game), new HashMap<>(), Set.of(), width);
                f.debug("demo");
                for (String screen : List.of("title", "menu", "story", "arcade", "endless", "rush", "school",
                        "drill:REFLEX", "drill:BOSS_TRAINING", "options", "records:ACTS", "records:SCHOOL",
                        "records:KEYS", "results:sample", "results:fail")) {
                    assertTrue(f.debug(screen), screen);
                    f.step(120);
                    f.drawAll();
                }
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static String result(String mode, int zone, int act, boolean cleared, boolean emerald, long score) {
        return "mode=" + mode + "\ngame=s1\nzone=" + zone + "\nact=" + act + "\ncleared=" + cleared + "\nscore=" + score
                + "\nwpm=48.5\naccuracy=0.96\nbestChain=14\nkills=40\nhitsTaken=1\nrescues=2\nrings=12\ngrade=A\n"
                + "tallyTotal=9000\nemerald=" + emerald + "\nkeyHits=a:30,b:20\nkeyMisses=q:2\n";
    }

    private static Object session(Fixture f) throws Exception {
        var field = f.scene.getClass().getDeclaredField("school");
        field.setAccessible(true);
        Object school = field.get(f.scene);
        Method m = school.getClass().getDeclaredMethod("session");
        m.setAccessible(true);
        return m.invoke(school);
    }

    private static Object call(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static String read(String path) {
        Path file = RESOURCES.resolve(path);
        try {
            return Files.isRegularFile(file) ? Files.readString(file) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Builds the hub as the mod's registration does, with rails for {@code railed} acts only. */
    private static ModScene hub(Set<String> railed) throws Exception {
        Class<?> lexiconType = loader.loadClass("typinghog.core.Lexicon");
        Object lexicon = lexiconType.getConstructor().newInstance();
        Method pool = lexiconType.getMethod("pool", String.class, String.class);
        for (String name : List.of("common", "tricky", "phrases", "rescues", "lore-s1", "lore-s2", "lore-s3k")) {
            pool.invoke(lexicon, name, read("words/" + name + ".txt"));
        }
        lexiconType.getMethod("taunts", String.class).invoke(lexicon, read("words/taunts.txt"));
        lexiconType.getMethod("trivia", String.class).invoke(lexicon, read("words/trivia.txt"));
        Class<?> fontType = loader.loadClass("typinghog.art.PlateFont");
        Object font = fontType.getConstructor(String.class).newInstance(read("art/plate-font.txt"));
        Class<?> platesType = loader.loadClass("typinghog.art.Plates");
        Object plates = platesType.getConstructor(fontType).newInstance(font);
        Class<?> soundsType = loader.loadClass("typinghog.play.Sounds");
        Map<String, Object> sounds = new HashMap<>();
        for (String g : List.of("s1", "s2", "s3k")) {
            sounds.put(g, soundsType.getMethod("parse", String.class).invoke(null, read("sounds/" + g + ".txt")));
        }
        Class<?> railType = loader.loadClass("typinghog.rails.Rail");
        Map<String, Object> rails = new HashMap<>();
        for (String key : railed) {
            String[] p = key.split(":");
            rails.put(key, railType.getMethod("parse", String.class).invoke(null,
                    "game " + p[0] + "\nzone " + p[1] + "\nact " + p[2] + "\nprogram 600:8\n"));
        }
        Class<?> assetsType = loader.loadClass("typinghog.play.CourseAssets");
        // CourseAssets(lexicon, plates, casts, sounds, props, rails): the hub reads only the
        // lexicon, sounds and rails.
        Object assets = assetsType.getConstructors()[0].newInstance(lexicon, plates, Map.of(), sounds, Map.of(), rails);
        Class<?> hubAssetsType = loader.loadClass("typinghog.hub.HubAssets");
        UnaryOperator<String> reader = TestTypingHedgehogHubScene::read;
        Object hubAssets = hubAssetsType.getMethod("read", UnaryOperator.class).invoke(null, reader);
        return (ModScene) loader.loadClass("typinghog.hub.HubScene").getConstructor(assetsType, hubAssetsType)
                .newInstance(assets, hubAssets);
    }

    private static SceneRomArt fakeRom(String game) {
        SceneSpriteSet set = mock(SceneSpriteSet.class);
        when(set.animationFrames(anyInt())).thenReturn(new int[] {0, 1});
        when(set.animationDelay(anyInt())).thenReturn(8);
        when(set.frameCount()).thenReturn(40);
        when(set.frame(anyInt())).thenReturn(new SceneSprite(new SceneImage(16, 32, new int[16 * 32]), 8, 16));
        SceneRomArt rom = mock(SceneRomArt.class);
        when(rom.gameId()).thenReturn(game);
        when(rom.character(anyString())).thenReturn(set);
        when(rom.characterAccessory(anyString())).thenReturn(set);
        when(rom.sprites(any(), any())).thenReturn(set);
        when(rom.read(anyInt(), anyInt())).thenAnswer(call -> new byte[(int) call.getArgument(1)]);
        when(rom.palette(anyInt(), anyInt())).thenAnswer(call -> new int[(int) call.getArgument(1)]);
        when(rom.levelStages(anyInt(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        return rom;
    }

    /** A scripted scene context: typed text, keys, storage in a map, and recorded hand-offs. */
    private static final class Fixture implements SceneContext {
        final ModScene scene;
        final String game;
        final List<String> games;
        final Map<String, String> saves;
        final Map<String, SceneRomArt> roms = new HashMap<>();
        final List<String> started = new ArrayList<>();
        final List<SceneLaunch> relaunched = new ArrayList<>();
        final Set<Integer> pressed = new HashSet<>();
        final List<String> audio = new ArrayList<>();
        final int width;
        SceneLaunch launch;
        String typed = "";
        long tick;

        Fixture(String game, List<String> games, Map<String, String> saves, Set<String> railed) throws Exception {
            this(game, games, saves, railed, 320);
        }

        Fixture(String game, List<String> games, Map<String, String> saves, Set<String> railed, int width)
                throws Exception {
            this.width = width;
            this.game = game;
            this.games = games;
            this.saves = saves;
            this.launch = new SceneLaunch(game, "sonic", List.of());
            scene = hub(railed);
            scene.enter(this);
        }

        void step(int n) {
            for (int i = 0; i < n; i++) {
                scene.update(this);
                tick++;
                typed = "";
                pressed.clear();
            }
        }

        void type(String text) {
            typed = text;
            step(1);
        }

        void key(int code) {
            pressed.add(code);
            step(1);
        }

        boolean debug(String command) { return ((DebuggableScene) scene).debugJump(command); }

        String screen() throws Exception { return (String) scene.getClass().getMethod("screen").invoke(scene); }

        /** Draws a frame and checks that no image (text included) leaves the screen sideways. */
        void drawAll() {
            SceneCanvas canvas = mock(SceneCanvas.class);
            when(canvas.width()).thenReturn(width);
            when(canvas.height()).thenReturn(224);
            List<String> outside = new ArrayList<>();
            org.mockito.Mockito.doAnswer(call -> {
                float dx = call.getArgument(5), dw = call.getArgument(7);
                if (dx < -1 || dx + dw > width + 1) outside.add(dx + "+" + dw);
                return null;
            }).when(canvas).drawRegion(any(), anyInt(), anyInt(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.anyFloat(),
                    org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(),
                    org.mockito.ArgumentMatchers.anyFloat(), any());
            scene.draw(this, canvas);
            try {
                assertTrue(outside.isEmpty(), "drawn past the " + width + "-pixel screen on " + screen() + ": " + outside);
            } catch (AssertionError e) {
                throw e;
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }

        @Override public String ownerModId() { return "typing-of-the-hedgehog"; }
        @Override public int width() { return width; }
        @Override public int height() { return 224; }
        @Override public long ticks() { return tick; }
        @Override public boolean buttonDown(int buttons) { return false; }
        @Override public boolean buttonPressed(int buttons) { return false; }
        @Override public boolean buttonRepeated(int buttons) { return false; }
        @Override public LogicalInputSnapshot input() { return LogicalInputSnapshot.neutral(); }
        @Override public String typedText() { return typed; }
        @Override public boolean keyDown(int key) { return pressed.contains(key); }
        @Override public boolean keyPressed(int key) { return pressed.contains(key); }
        @Override public SceneMouse mouse() { return SceneMouse.none(); }
        @Override public SceneArt art() {
            return new SceneArt() {
                @Override public SceneImage png(byte[] bytes) { throw new UnsupportedOperationException(); }
                @Override public List<String> availableGames() { return games; }
                @Override public SceneRomArt rom() { return rom(game); }
                @Override public SceneRomArt rom(String g) {
                    return games.contains(g) ? roms.computeIfAbsent(g, TestTypingHedgehogHubScene::fakeRom) : null;
                }
            };
        }
        @Override public SceneAudio audio() {
            return new SceneAudio() {
                @Override public void playMusic(int id) { audio.add("music:" + Integer.toHexString(id)); }
                @Override public void playSfx(int id) { audio.add("sfx:" + Integer.toHexString(id)); }
                @Override public void fadeOutMusic() { audio.add("fade"); }
                @Override public void stopMusic() { audio.add("stop"); }
            };
        }
        @Override public SceneStorage storage() {
            return new SceneStorage() {
                @Override public Optional<String> read(String name) { return Optional.ofNullable(saves.get(name)); }
                @Override public boolean write(String name, String text) { saves.put(name, text); return true; }
                @Override public boolean delete(String name) { return saves.remove(name) != null; }
                @Override public List<String> list() { return saves.keySet().stream().sorted().toList(); }
            };
        }
        @Override public boolean startStockAct(int zone, int act) {
            started.add(zone + ":" + act);
            return true;
        }
        @Override public SceneLaunch currentLaunch() { return launch; }
        @Override public boolean relaunch(SceneLaunch next) {
            relaunched.add(next);
            return true;
        }
        @Override public void exitToGameTitle() { }
        @Override public void exitToMasterTitle() { }

        @SuppressWarnings("unused")
        List<String> sounds() { return Arrays.asList(audio.toArray(String[]::new)); }
    }
}
