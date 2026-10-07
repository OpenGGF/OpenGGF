package com.openggf.mods.code;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import com.openggf.mods.scene.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Packages the actual external mod and exercises its arcade consumer with a controlled clock. */
class TestSitarHeroArcade {
    @TempDir static Path temp;
    static ExampleModHarness harness;
    static Class<?> type;
    @BeforeAll static void packageMod() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/sitar-hero"), temp.resolve("package"));
        type = harness.loader().loadClass("sitarhero.SitarScene");
        assertEquals("any", harness.manifest().baseGame());
    }
    @AfterAll static void close() throws Exception { if (harness != null) harness.close(); }

    private static final class Music implements SceneMusic {
        final ScenePreparedMusic song;
        Player player;
        Music() {
            var notes = new ArrayList<SceneNoteEvent>();
            for (int at = 0; at < 20_000; at += 250) {
                notes.add(new SceneNoteEvent(5, SceneNoteEvent.Kind.DAC, 5, at % 500 == 0 ? 0x81 : 0x82, at, at, 250));
                if (at % 500 == 0) {
                    for (int channel = 0; channel < 5; channel++) notes.add(new SceneNoteEvent(channel,
                            SceneNoteEvent.Kind.FM, channel, 0xA0 + channel + at / 500 % 5, at, at, 450));
                    for (int channel = 0; channel < 3; channel++) notes.add(new SceneNoteEvent(6 + channel,
                            SceneNoteEvent.Kind.PSG, channel, 0xA0 + channel + at / 500 % 5, at, at, 450));
                }
            }
            List<SceneNoteEvent> events = List.copyOf(notes);
            song = new ScenePreparedMusic() {
                public int sampleRate() { return 1_000; }
                public long lengthSamples() { return 20_000; }
                public List<SceneNoteEvent> notes() { return events; }
            };
        }
        public ScenePreparedMusic prepare(String game, int id, int frames) { return song; }
        public SceneMusicPlayer start(ScenePreparedMusic song, int fm, int psg, boolean dac, int lead) { return start(song, List.of(new SceneMusicPart(0, fm, psg, dac)), lead); }
        public SceneMusicPlayer start(ScenePreparedMusic song, List<SceneMusicPart> parts, int lead) { player = new Player(-lead); return player; }
    }
    private static final class Player implements SceneMusicPlayer {
        long position;
        boolean paused, stopped, speakerUnavailable;
        Player(long position) { this.position = position; }
        public long samplePosition() { return position; }
        public long samplePositionAt(long nanos) { return position; }
        public void pause() { paused = true; }
        public void resume() { if (!speakerUnavailable) paused = false; }
        public void setPartAudible(boolean value) { }
        public void setWhammy(double value) { }
        public boolean finished() { return stopped || position >= 20_000; }
        public boolean paused() { return paused; }
        public long underrunCount() { return speakerUnavailable ? 1 : 0; }
        public void stop() { stopped = true; }
    }
    private final class Fixture implements SceneContext {
        final ModScene scene;
        final Music music = new Music();
        final List<String> games;
        final Map<String, String> saves;
        final Set<Integer> pressed = new HashSet<>();
        final Set<Integer> held = new HashSet<>();
        PhysicalInput physical = PhysicalInput.neutral();
        int logicalPress;
        long tick, sequence;
        Fixture(List<String> games) throws Exception { this(games, new HashMap<>()); }
        Fixture(List<String> games, Map<String, String> saves) throws Exception {
            this.games = games; this.saves = saves;
            scene = (ModScene) type.getConstructor().newInstance(); scene.enter(this);
        }
        void step() {
            if (music.player != null && !music.player.paused && !music.player.stopped) music.player.position += 50;
            scene.update(this); tick++;
            pressed.clear(); logicalPress = 0;
            physical = new PhysicalInput(tick * 50_000_000L, List.copyOf(held), List.of(), List.of(), 0);
        }
        void key(int code) {
            held.add(code); pressed.add(code);
            physical = new PhysicalInput(tick * 50_000_000L, List.copyOf(held), List.of(), List.of(
                    new PhysicalInputEvent(sequence++, tick * 50_000_000L, PhysicalInputEvent.Kind.KEY, -1, code, 1)), 0);
            step(); held.remove(code);
            physical = new PhysicalInput(tick * 50_000_000L, List.copyOf(held), List.of(), List.of(
                    new PhysicalInputEvent(sequence++, tick * 50_000_000L, PhysicalInputEvent.Kind.KEY, -1, code, 0)), 0);
            step();
        }
        void pad(int button) {
            // Mirror the existing Genesis mapper while supplying the same raw pad edge.
            logicalPress = button == PhysicalGamepad.BUTTON_A ? SceneButtons.B
                    : button == PhysicalGamepad.BUTTON_B ? SceneButtons.C : 0;
            physical = new PhysicalInput(tick * 50_000_000L, List.of(), List.of(), List.of(
                    new PhysicalInputEvent(sequence++, tick * 50_000_000L, PhysicalInputEvent.Kind.BUTTON, 0, button, 1)), 0);
            step(); step();
        }
        boolean debug(String command) { return ((DebuggableScene) scene).debugJump(command); }
        String screen() throws Exception { return (String) type.getMethod("screen").invoke(scene); }
        long score() throws Exception { return (long) type.getMethod("score").invoke(scene); }
        int count(String method) throws Exception { return (int) type.getMethod(method).invoke(scene); }
        Object session() throws Exception { var field = type.getDeclaredField("session"); field.setAccessible(true); return field.get(scene); }
        public String ownerModId() { return "sitar-hero"; }
        public int width() { return 400; }
        public int height() { return 224; }
        public long ticks() { return tick; }
        public boolean buttonDown(int buttons) { return false; }
        public boolean buttonPressed(int buttons) { return (logicalPress & buttons) != 0; }
        public boolean buttonRepeated(int buttons) { return false; }
        public LogicalInputSnapshot input() { return LogicalInputSnapshot.neutral(); }
        public PhysicalInput physicalInput() { return physical; }
        public boolean keyDown(int key) { return held.contains(key); }
        public boolean keyPressed(int key) { return pressed.contains(key); }
        public SceneMouse mouse() { return SceneMouse.none(); }
        public SceneArt art() {
            return new SceneArt() {
                public SceneImage png(byte[] bytes) { throw new UnsupportedOperationException(); }
                public List<String> availableGames() { return games; }
                public SceneRomArt rom() { return rom(games.getFirst()); }
                public SceneRomArt rom(String game) { return games.contains(game) ? fakeRom(game) : null; }
            };
        }
        public SceneAudio audio() { return mock(SceneAudio.class); }
        public SceneMusic music() { return music; }
        public SceneStorage storage() {
            return new SceneStorage() {
                public Optional<String> read(String name) { return Optional.ofNullable(saves.get(name)); }
                public boolean write(String name, String text) { saves.put(name, text); return true; }
                public boolean delete(String name) { return saves.remove(name) != null; }
                public List<String> list() { return saves.keySet().stream().sorted().toList(); }
            };
        }
        public void exitToGameTitle() { }
        public void exitToMasterTitle() { }
    }
    private SceneRomArt fakeRom(String game) {
        SceneSpriteSet set = mock(SceneSpriteSet.class);
        when(set.animationFrames(anyInt())).thenReturn(new int[]{0});
        when(set.frameCount()).thenReturn(1);
        when(set.frame(anyInt())).thenReturn(SceneSprite.of(new SceneImage(1, 1, new int[]{0xFFFFFFFF})));
        SceneRomArt rom = mock(SceneRomArt.class);
        when(rom.gameId()).thenReturn(game);
        when(rom.character(anyString())).thenReturn(set);
        when(rom.characterAccessory(anyString())).thenReturn(set);
        when(rom.sprites(any(), any())).thenReturn(set);
        when(rom.read(anyInt(), anyInt())).thenAnswer(call -> new byte[(int) call.getArgument(1)]);
        when(rom.palette(anyInt(), anyInt())).thenAnswer(call -> { int[] p = new int[(int) call.getArgument(1)]; Arrays.fill(p, 0xFFFFFFFF); return p; });
        when(rom.levelStages(anyInt(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        return rom;
    }

    @Test void allSevenLibrariesReachSelectionWithCorrectRoster() throws Exception {
        for (int subset = 1; subset < 8; subset++) {
            List<String> games = new ArrayList<>(); if ((subset & 1) != 0) games.add("s1"); if ((subset & 2) != 0) games.add("s2"); if ((subset & 4) != 0) games.add("s3k");
            Fixture f = new Fixture(games);
            assertEquals("CHARACTERS", f.screen());
            assertEquals(games.size(), ((List<?>) type.getMethod("availableSongs").invoke(f.scene)).size());
            int expected = 2 + ((subset & 6) != 0 ? 1 : 0) + ((subset & 2) != 0 ? 1 : 0) + ((subset & 4) != 0 ? 3 : 0);
            assertEquals(expected, ((List<?>) type.getMethod("availablePerformers").invoke(f.scene)).size());
            f.key(SceneKeys.ENTER); assertEquals("ROLES", f.screen());
            f.step(); f.step(); assertEquals("ROLES", f.screen());
            f.key(SceneKeys.ENTER); assertEquals("SONGS", f.screen());
            f.scene.exit(f);
        }
    }
    @Test void ordinaryPadMenusDoNotDependOnGenesisMappings() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("ROLES", f.screen());
        f.pad(PhysicalGamepad.BUTTON_DPAD_DOWN);
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("SONGS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_A); f.step(); f.step(); assertEquals("PLAY", f.screen());
        f.scene.exit(f);
    }
    @Test void rawPadBackAndSettingsAcceptDoNotCollideWithGenesisAliases() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        f.pad(PhysicalGamepad.BUTTON_A); f.pad(PhysicalGamepad.BUTTON_A); assertEquals("SONGS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_B); assertEquals("ROLES", f.screen());
        assertTrue(f.debug("settings")); f.key(SceneKeys.TAB);
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("SETTINGS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_X); assertEquals("SETTINGS", f.screen());
        assertTrue(f.saves.get("settings.txt").contains("p0.0=B,0,2,1"));
        f.scene.exit(f);
    }
    @Test void padCalibrationTapsStayInCalibrationAndSave() throws Exception {
        Fixture f = new Fixture(List.of("s1")); assertTrue(f.debug("settings"));
        for (int i = 0; i < 13; i++) f.key(SceneKeys.DOWN);
        f.pad(PhysicalGamepad.BUTTON_A); f.step(); f.step(); assertEquals("CALIBRATION", f.screen());
        for (int i = 1; i <= 8; i++) {
            f.music.player.position = i * 500 - 50; f.pad(PhysicalGamepad.BUTTON_A);
            assertEquals("CALIBRATION", f.screen());
        }
        f.key(SceneKeys.ENTER); assertEquals("SETTINGS", f.screen());
        assertTrue(f.saves.containsKey("settings.txt")); f.scene.exit(f);
    }
    @Test void cosmeticCrossGamePerformanceEndsAndRetryResetsScore() throws Exception {
        Fixture f = new Fixture(List.of("s1", "s3k"));
        assertTrue(f.debug("perform:green-hill:SITAR:knuckles")); assertTrue(f.debug("autoplay"));
        for (int frame = 0; frame < 500 && !f.screen().equals("RESULTS"); frame++) f.step();
        assertEquals("RESULTS", f.screen(), (String) type.getMethod("error").invoke(f.scene));
        assertEquals(f.count("chartNotes"), f.count("hits")); assertTrue(f.score() > 0);
        assertFalse(f.saves.containsKey("green-hill.sitar.txt"), "tool demo must not overwrite a player's record");
        assertTrue(f.debug("retry")); f.step(); f.step(); f.step(); assertEquals("PLAY", f.screen()); assertEquals(0, f.score());
        f.scene.exit(f);
    }
    @Test void pauseFreezesAudioAndSessionUntilReleasedResume() throws Exception {
        Fixture f = new Fixture(List.of("s2")); assertTrue(f.debug("perform:chemical-plant:SYNTH:silver-sonic"));
        f.step(); f.step(); f.step(); assertEquals("PLAY", f.screen());
        f.key(SceneKeys.ESCAPE); assertEquals("PAUSED", f.screen());
        long position = f.music.player.position; for (int i = 0; i < 20; i++) f.step(); assertEquals(position, f.music.player.position);
        f.key(SceneKeys.ENTER); assertEquals("PLAY", f.screen()); assertTrue(f.music.player.position > position);
        f.scene.exit(f);
    }
    @Test void failedSpeakerKeepsTheVisiblePauseMenuAndFrozenScoreOnResume() throws Exception {
        Fixture f = new Fixture(List.of("s1")); assertTrue(f.debug("perform:green-hill:SITAR:sonic"));
        f.step(); f.step(); f.step(); assertEquals("PLAY", f.screen());
        f.music.player.speakerUnavailable = true;
        f.music.player.paused = true;
        f.step(); assertEquals("PAUSED", f.screen());
        long position = f.music.player.position, score = f.score();
        f.key(SceneKeys.ENTER);
        assertEquals("PAUSED", f.screen(), "failed output cannot become a silently advancing performance");
        assertEquals(position, f.music.player.position);
        assertEquals(score, f.score());
        f.scene.exit(f);
    }
    @Test void keyboardRemappingAndCalibrationSettingsPersist() throws Exception {
        Fixture f = new Fixture(List.of("s1")); assertTrue(f.debug("settings"));
        f.key(SceneKeys.ENTER); f.key(SceneKeys.J); f.key(SceneKeys.ESCAPE);
        assertTrue(f.saves.get("settings.txt").contains("k0.0=K,-1,74,1"));
        Fixture reopened = new Fixture(List.of("s1"), f.saves);
        var field = type.getDeclaredField("settings"); field.setAccessible(true);
        assertTrue(field.get(reopened.scene).getClass().getMethod("encode").invoke(field.get(reopened.scene)).toString().contains("k0.0=K,-1,74,1"));
        reopened.scene.exit(reopened);
    }
    @Test void bongosSettingsSkipUnusedStrumAndWhammyRows() throws Exception {
        Fixture f = new Fixture(List.of("s1")); assertTrue(f.debug("settings"));
        for (int i = 0; i < 14; i++) f.key(SceneKeys.DOWN);
        f.key(SceneKeys.ENTER); // Switch the visible instrument profile to Bongos.
        for (int i = 0; i < 7; i++) f.key(SceneKeys.UP);
        var selected = type.getDeclaredField("selected"); selected.setAccessible(true);
        assertEquals(4, selected.getInt(f.scene));
        f.key(SceneKeys.DOWN); assertEquals(8, selected.getInt(f.scene));
        f.key(SceneKeys.UP); assertEquals(4, selected.getInt(f.scene));
        f.key(SceneKeys.ENTER); f.key(SceneKeys.Z);
        assertTrue(f.saves.get("settings.txt").contains("k1.4=K,-1,90,1"));
        f.scene.exit(f);
    }
    @Test void directKeyboardDrumStrikesSaveACompletedPlayerRecord() throws Exception {
        Fixture f = new Fixture(List.of("s1")); assertTrue(f.debug("perform:green-hill:BONGOS:robotnik"));
        f.step(); f.step(); f.step();
        Object session = f.session(); var sessionType = session.getClass();
        Object chart = sessionType.getMethod("chart").invoke(session);
        List<?> notes = (List<?>) chart.getClass().getMethod("notes").invoke(chart);
        for (Object note : notes) {
            long onset = (long) note.getClass().getMethod("onset").invoke(note);
            int lane = (int) note.getClass().getMethod("lanes").invoke(note);
            f.music.player.position = onset - 50;
            int key = lane == 16 ? SceneKeys.SPACE : SceneKeys.A;
            f.key(key);
        }
        f.music.player.position = 19_950; f.step();
        assertEquals("RESULTS", f.screen()); assertEquals(notes.size(), f.count("hits"));
        assertTrue(Long.parseLong(f.saves.get("green-hill.bongos.txt")) > 0);
        f.scene.exit(f);
    }
    @Test void positiveCalibrationAtEofStillResolvesTheLastGem() throws Exception {
        Fixture f = new Fixture(List.of("s1")); assertTrue(f.debug("perform:green-hill:BONGOS:sonic"));
        f.step(); f.step(); f.step();
        var settingField = type.getDeclaredField("settings"); settingField.setAccessible(true);
        Object settings = settingField.get(f.scene);
        settings.getClass().getMethod("inputOffsetMs", int.class).invoke(settings, 250);
        Object session = f.session(); Object chart = session.getClass().getMethod("chart").invoke(session);
        List<?> notes = (List<?>) chart.getClass().getMethod("notes").invoke(chart);
        for (int i = 0; i < notes.size() - 1; i++) {
            Object note = notes.get(i); long onset = (long) note.getClass().getMethod("onset").invoke(note);
            int lane = (int) note.getClass().getMethod("lanes").invoke(note);
            f.music.player.position = onset + 250 - 50; f.key(lane == 16 ? SceneKeys.SPACE : SceneKeys.A);
        }
        f.music.player.position = 19_950; f.step(); assertEquals("PLAY", f.screen());
        f.key(SceneKeys.ESCAPE); assertEquals("PAUSED", f.screen());
        for (int i = 0; i < 20; i++) f.step();
        f.key(SceneKeys.ENTER); assertEquals("PLAY", f.screen(), "silent judgment tail must freeze during pause");
        for (int i = 0; i < 12 && f.screen().equals("PLAY"); i++) f.step();
        assertEquals("RESULTS", f.screen());
        assertEquals(notes.size(), f.count("hits") + (int) session.getClass().getMethod("misses").invoke(session));
        assertEquals(1, (int) session.getClass().getMethod("misses").invoke(session));
        f.scene.exit(f);
    }
}
