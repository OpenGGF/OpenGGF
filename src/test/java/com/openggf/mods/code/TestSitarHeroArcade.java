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

    @Test
    void judgedHitsAnimateIndependentNativePerformersAndPauseClearsGestures() throws Exception {
        var f = new Fixture(List.of("s1"));
        assertTrue(f.debug("local:green-hill:BONGOS:sonic:MEDIUM:coop"));
        var second = type.getDeclaredField("performer2"); second.setAccessible(true);
        second.set(f.scene, type.getDeclaredField("performer").getType().getEnumConstants()[0]);
        for (int step = 0; step < 3; step++) f.step();
        assertEquals("PLAY", f.screen());
        f.music.player.position = -50;
        f.key(SceneKeys.SPACE);
        var actorsField = type.getDeclaredField("actors"); actorsField.setAccessible(true);
        var actors = (Map<?, ?>) actorsField.get(f.scene);
        Object first = actors.get("sonic");
        assertNotNull(first);
        assertFalse(actors.containsKey("p2-sonic"), "P1 hit cannot start the other player's gesture");
        assertTrue(gestureAge(first, f.tick) < 18);
        f.key(SceneKeys.P);
        Object other = actors.get("p2-sonic");
        assertNotNull(other); assertNotSame(first, other);
        assertTrue(gestureAge(other, f.tick) < 18);
        assertTrue(f.debug("pause"));
        var canvas = mock(SceneCanvas.class);
        when(canvas.width()).thenReturn(400); when(canvas.height()).thenReturn(224);
        f.scene.draw(f, canvas);
        assertEquals(18, gestureAge(first, f.tick));
        assertEquals(18, gestureAge(other, f.tick));
    }

    private static int gestureAge(Object artist, long tick) throws Exception {
        var field = artist.getClass().getDeclaredField("motion"); field.setAccessible(true);
        Object motion = field.get(artist);
        Class<?> role = harness.loader().loadClass("sitarhero.model.Role");
        Object drums = java.util.Arrays.stream(role.getEnumConstants()).filter(v -> v.toString().equals("BONGOS")).findFirst().orElseThrow();
        var age = motion.getClass().getDeclaredMethod("age", role, int.class, long.class); age.setAccessible(true);
        return (int) age.invoke(motion, drums, 16, tick);
    }

    private static final class Music implements SceneMusic {
        final ScenePreparedMusic song;
        Player player;
        int lastPreparedId, lastPreparedFrames, rate = 1_000;
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
                public int sampleRate() { return rate; }
                public long lengthSamples() { return 20_000; }
                public List<SceneNoteEvent> notes() { return events; }
            };
        }
        public ScenePreparedMusic prepare(String game, int id, int frames) { lastPreparedId = id; lastPreparedFrames = frames; return song; }
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
        final Map<String, SceneRomArt> roms = new HashMap<>();
        final Set<Integer> pressed = new HashSet<>();
        final Set<Integer> held = new HashSet<>();
        PhysicalInput physical = PhysicalInput.neutral();
        SceneMouse pointer = SceneMouse.none();
        int logicalPress, logicalRepeat;
        long tick, sequence;
        Fixture(List<String> games) throws Exception { this(games, new HashMap<>()); }
        Fixture(List<String> games, Map<String, String> saves) throws Exception {
            this.games = games; this.saves = saves;
            scene = (ModScene) type.getConstructor().newInstance(); scene.enter(this);
        }
        void step() {
            if (music.player != null && !music.player.paused && !music.player.stopped) music.player.position += 50;
            scene.update(this); tick++;
            pressed.clear(); logicalPress = 0; pointer = SceneMouse.none();
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
        void pad(int button) { pad(button, 0); }
        void pad(int button, int device) {
            // Mirror the existing Genesis mapper while supplying the same raw pad edge.
            logicalPress = button == PhysicalGamepad.BUTTON_A ? SceneButtons.B
                    : button == PhysicalGamepad.BUTTON_B ? SceneButtons.C : 0;
            physical = new PhysicalInput(tick * 50_000_000L, List.of(), List.of(), List.of(
                    new PhysicalInputEvent(sequence++, tick * 50_000_000L, PhysicalInputEvent.Kind.BUTTON, device, button, 1)), 0);
            step(); step();
        }
        void mouse(int x, int y, boolean click, int wheel) {
            pointer = mock(SceneMouse.class);
            when(pointer.leftPressed()).thenReturn(click); when(pointer.moved()).thenReturn(true);
            when(pointer.wheel()).thenReturn(wheel);
            when(pointer.over(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
                int rx = call.getArgument(0), ry = call.getArgument(1), rw = call.getArgument(2), rh = call.getArgument(3);
                return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
            });
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
        public boolean buttonRepeated(int buttons) { return (logicalRepeat & buttons) != 0; }
        public LogicalInputSnapshot input() { return LogicalInputSnapshot.neutral(); }
        public PhysicalInput physicalInput() { return physical; }
        public boolean keyDown(int key) { return held.contains(key); }
        public boolean keyPressed(int key) { return pressed.contains(key); }
        public SceneMouse mouse() { return pointer; }
        public SceneArt art() {
            return new SceneArt() {
                public SceneImage png(byte[] bytes) { throw new UnsupportedOperationException(); }
                public List<String> availableGames() { return games; }
                public SceneRomArt rom() { return rom(games.getFirst()); }
                public SceneRomArt rom(String game) { return games.contains(game) ? roms.computeIfAbsent(game, TestSitarHeroArcade.this::fakeRom) : null; }
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
        when(rom.hasZonePictures(anyInt(), anyInt())).thenAnswer(call ->
                (int) call.getArgument(0) == (game.equals("s2") ? 1 : 0) && (int) call.getArgument(1) == 0);
        when(rom.character(anyString())).thenReturn(set);
        when(rom.characterAccessory(anyString())).thenReturn(set);
        when(rom.sprites(any(), any())).thenReturn(set);
        when(rom.read(anyInt(), anyInt())).thenAnswer(call -> new byte[(int) call.getArgument(1)]);
        when(rom.palette(anyInt(), anyInt())).thenAnswer(call -> { int[] p = new int[(int) call.getArgument(1)]; Arrays.fill(p, 0xFFFFFFFF); return p; });
        when(rom.levelStages(anyInt(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        return rom;
    }

    @Test void fullVersionTitleOffersCareerAndASeparatePracticeRoute() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        assertEquals("TITLE", f.screen());
        f.key(SceneKeys.ENTER);
        assertEquals("CHARACTERS", f.screen());
        f.key(SceneKeys.ESCAPE);
        assertEquals("TITLE", f.screen());
        f.key(SceneKeys.DOWN); f.key(SceneKeys.DOWN); f.key(SceneKeys.ENTER);
        assertEquals("CHARACTERS", f.screen());
        assertEquals("PRACTICE", type.getMethod("mode").invoke(f.scene));
        f.scene.exit(f);
    }

    @Test void careerOpensNativeWorldsAndKeepsLockedMainActsVisible() throws Exception {
        Fixture f = new Fixture(List.of("s3k"));
        assertTrue(f.debug("career"));
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        assertEquals("TOURS", f.screen());
        f.key(SceneKeys.ENTER);
        assertEquals("WORLDS", f.screen());
        f.key(SceneKeys.DOWN); f.key(SceneKeys.ENTER);
        assertEquals("WORLDS", f.screen(), "Hydrocity waits for both Angel Island acts");
        f.key(SceneKeys.UP); f.key(SceneKeys.ENTER);
        assertEquals("STORY", f.screen());
        f.key(SceneKeys.ESCAPE);
        assertEquals("SONGS", f.screen());
        var list = type.getDeclaredMethod("setlistSongs"); list.setAccessible(true);
        List<?> songs = (List<?>) list.invoke(f.scene);
        assertEquals(List.of("angel-island-1", "angel-island-2"), songs.subList(0, 2).stream().map(s -> {
            try { return s.getClass().getMethod("id").invoke(s); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }).toList());
        assertTrue(f.saves.containsKey("career.txt"), "skipping acknowledges the introduction");
        f.scene.exit(f);
    }

    @Test void seenIntermissionsCanReplayWithoutLosingRoleChangesOrSetlistReturn() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        f.debug("career");
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        assertEquals("STORY", f.screen());
        f.key(SceneKeys.ESCAPE); f.key(SceneKeys.ESCAPE);
        assertEquals("WORLDS", f.screen());
        f.key(SceneKeys.ENTER);
        assertEquals("SONGS", f.screen(), "revisits do not force the whole introduction");
        f.key(SceneKeys.I);
        assertEquals("ROLES", f.screen());
        f.key(SceneKeys.DOWN); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        assertEquals("SONGS", f.screen(), "role changes return to the same gig");
        f.key(SceneKeys.ESCAPE); f.key(SceneKeys.R);
        assertEquals("STORY", f.screen(), "a seen scene remains replayable");
        f.key(SceneKeys.ESCAPE);
        assertEquals("WORLDS", f.screen(), "replay returns to the board");
        f.scene.exit(f);
    }

    @Test void earnedCareerClearsUnlockWorldsAcrossRolesButDemosAndQuickPlayDoNot() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        f.debug("career");
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ESCAPE);
        f.key(SceneKeys.ENTER); f.step(); f.step(); f.step();
        assertEquals("PLAY", f.screen());
        f.debug("autoplay");
        for (int i = 0; i < 500 && !f.screen().equals("RESULTS"); i++) f.step();
        assertEquals("RESULTS", f.screen());
        assertFalse(careerClear(f, "green-hill"));
        f.debug("career");
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        f.step(); f.step(); f.step();
        strikeEveryNote(f);
        assertEquals("RESULTS", f.screen());
        assertTrue(careerClear(f, "green-hill"));
        assertTrue(f.saves.containsKey("career.txt"));
        f.key(SceneKeys.DOWN); f.key(SceneKeys.DOWN); f.key(SceneKeys.ENTER);
        assertEquals("WORLDS", f.screen());
        f.key(SceneKeys.ENTER);
        assertEquals("STORY", f.screen(), "Marble opens after the required Green Hill song, without requiring its optional gig");
        f.key(SceneKeys.ESCAPE); f.key(SceneKeys.I); f.key(SceneKeys.DOWN);
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        assertEquals("SONGS", f.screen());
        assertTrue(careerClear(f, "green-hill"), "instrument changes retain earned tour progress");
        f.debug("perform:marble:BONGOS:sonic:EXPERT");
        f.step(); f.step(); f.step(); strikeEveryNote(f);
        assertEquals("RESULTS", f.screen());
        assertFalse(careerClear(f, "marble"), "Quick Play records do not skip authored career stops");
        f.scene.exit(f);
        Fixture restored = new Fixture(List.of("s1"), f.saves);
        assertTrue(careerClear(restored, "green-hill"));
        assertFalse(careerClear(restored, "marble"));
        restored.scene.exit(restored);
    }

    private boolean careerClear(Fixture f, String songId) throws Exception {
        var field = type.getDeclaredField("career"); field.setAccessible(true);
        Object journal = field.get(f.scene);
        return (boolean) journal.getClass().getMethod("cleared", String.class).invoke(journal, songId);
    }

    @Test void requiredSongsWithAbsentNativePartsOfferAnHonestRoleChange() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        Object tour = ((List<?>) harness.loader().loadClass("sitarhero.model.CareerTours").getMethod("all").invoke(null)).getFirst();
        List<?> worlds = (List<?>) tour.getClass().getMethod("worlds").invoke(tour);
        var journalField = type.getDeclaredField("career"); journalField.setAccessible(true);
        Object journal = journalField.get(f.scene);
        Class<?> result = harness.loader().loadClass("sitarhero.model.PerformanceResult");
        var constructor = result.getConstructor(String.class, String.class, String.class, long.class, int.class, int.class, int.class, boolean.class, boolean.class);
        for (int i = 0; i < 6; i++) {
            List<?> ids = (List<?>) worlds.get(i).getClass().getMethod("requiredSongIds").invoke(worlds.get(i));
            for (Object id : ids) journal.getClass().getMethod("record", result, boolean.class)
                    .invoke(journal, constructor.newInstance(id, "BONGOS", "EASY", 100L, 1, 1, 1, false, true), true);
        }
        f.debug("career"); f.key(SceneKeys.ENTER);
        var role = type.getDeclaredField("role"); role.setAccessible(true);
        Class<?> roleClass = harness.loader().loadClass("sitarhero.model.Role");
        role.set(f.scene, Arrays.stream(roleClass.getEnumConstants()).filter(v -> v.toString().equals("SYNTH")).findFirst().orElseThrow());
        var selected = type.getDeclaredField("selected"); selected.setAccessible(true); selected.set(f.scene, 2);
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ESCAPE);
        assertEquals("SONGS", f.screen());
        f.key(SceneKeys.ENTER);
        assertEquals("ROLES", f.screen(), "Final Zone remains required even though the ROM has no PSG part");
        f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        assertEquals("SONGS", f.screen());
        assertEquals("SITAR", role.get(f.scene).toString());
        assertTrue(careerClear(f, "green-hill"));
        f.scene.exit(f);
    }

    @Test void careerReplayAndPartChangesAcceptMouseAndRawGamepadActions() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        f.debug("career"); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        f.key(SceneKeys.ENTER);
        f.pad(PhysicalGamepad.BUTTON_Y);
        assertEquals("STORY", f.screen());
        f.pad(PhysicalGamepad.BUTTON_B); assertEquals("WORLDS", f.screen());
        f.mouse(275, 176, true, 0); assertEquals("STORY", f.screen());
        f.key(SceneKeys.ESCAPE); f.key(SceneKeys.ENTER); assertEquals("SONGS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_X); assertEquals("ROLES", f.screen());
        f.key(SceneKeys.ESCAPE); assertEquals("SONGS", f.screen());
        f.mouse(275, 178, true, 0); assertEquals("ROLES", f.screen());
        f.scene.exit(f);
    }

    @Test void completedToursRecoverPendingFinalesAndKeepThemReplayable() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        Object tour = ((List<?>) harness.loader().loadClass("sitarhero.model.CareerTours").getMethod("all").invoke(null)).getFirst();
        var journalField = type.getDeclaredField("career"); journalField.setAccessible(true);
        Object journal = journalField.get(f.scene);
        Class<?> result = harness.loader().loadClass("sitarhero.model.PerformanceResult");
        var constructor = result.getConstructor(String.class, String.class, String.class, long.class, int.class, int.class, int.class, boolean.class, boolean.class);
        for (Object world : (List<?>) tour.getClass().getMethod("worlds").invoke(tour))
            for (Object id : (List<?>) world.getClass().getMethod("requiredSongIds").invoke(world))
                journal.getClass().getMethod("record", result, boolean.class)
                        .invoke(journal, constructor.newInstance(id, "SITAR", "EASY", 100L, 1, 1, 1, false, true), true);
        f.debug("career"); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER); f.key(SceneKeys.ENTER);
        assertEquals("STORY", f.screen(), "a completed but unseen finale is recovered on tour entry");
        var storyField = type.getDeclaredField("story"); storyField.setAccessible(true);
        Object story = storyField.get(f.scene);
        assertEquals("s1-outro", story.getClass().getMethod("id").invoke(story));
        f.key(SceneKeys.ESCAPE); assertEquals("WORLDS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_X); assertEquals("STORY", f.screen());
        f.key(SceneKeys.ESCAPE); f.mouse(275, 188, true, 0); assertEquals("STORY", f.screen());
        f.key(SceneKeys.ESCAPE); f.key(SceneKeys.ESCAPE); f.key(SceneKeys.ENTER);
        assertEquals("WORLDS", f.screen(), "a seen finale is available without being forced again");
        f.scene.exit(f);
        Fixture restored = new Fixture(List.of("s1"), f.saves);
        restored.debug("career"); restored.key(SceneKeys.ENTER); restored.key(SceneKeys.ENTER);
        restored.key(SceneKeys.ENTER); restored.key(SceneKeys.ENTER);
        assertEquals("WORLDS", restored.screen());
        restored.scene.exit(restored);
    }
    private void strikeEveryNote(Fixture f) throws Exception {
        Object session = f.session();
        Object chart = session.getClass().getMethod("chart").invoke(session);
        List<?> notes = (List<?>) chart.getClass().getMethod("notes").invoke(chart);
        var input = session.getClass().getMethod("input", long.class, int.class, int.class, boolean.class, boolean.class, boolean.class);
        var role = type.getDeclaredField("role"); role.setAccessible(true);
        boolean drums = (boolean) role.get(f.scene).getClass().getMethod("drums").invoke(role.get(f.scene));
        for (Object note : notes) {
            long onset = (long) note.getClass().getMethod("onset").invoke(note);
            int lanes = (int) note.getClass().getMethod("lanes").invoke(note);
            input.invoke(session, onset, lanes, drums ? lanes : 0, !drums, false, false);
        }
        f.music.player.position = 20_000;
        for (int i = 0; i < 20 && !f.screen().equals("RESULTS"); i++) f.step();
    }

    @Test void unsupportedSongSceneryUsesSupportedPicturesFromThatSameRom() throws Exception {
        for (String game : List.of("s2", "s3k")) {
            Fixture f = new Fixture(List.of(game));
            SceneRomArt rom = f.roms.get(game); clearInvocations(rom);
            Class<?> catalog = harness.loader().loadClass("sitarhero.model.SongCatalog");
            Class<?> songClass = harness.loader().loadClass("sitarhero.model.SongSpec");
            String id = game.equals("s2") ? "emerald-hill" : "marble-garden-1";
            Object source = ((List<?>) catalog.getMethod("all").invoke(null)).stream().filter(song -> {
                try { return songClass.getMethod("id").invoke(song).equals(id); }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            }).findFirst().orElseThrow();
            var selected = type.getDeclaredField("song"); selected.setAccessible(true); selected.set(f.scene, source);
            var stage = type.getDeclaredMethod("stage", songClass); stage.setAccessible(true); stage.invoke(f.scene, source);
            int picturedZone = game.equals("s2") ? 1 : 0;
            verify(rom).zoneBackdrop(picturedZone, 0);
            verify(rom).levelStages(picturedZone, 0, 400, 90, 24);
            verify(rom, never()).zoneBackdrop(game.equals("s2") ? 0 : 2, 0);
            assertSame(source, selected.get(f.scene), "concert scenery cannot change the selected song");
            assertEquals(Set.of(game), f.roms.keySet(), "no other ROM can supply fallback pictures");
            f.scene.exit(f);
        }
    }

    @Test void practiceRunsPastMissesWithoutSavingPlayerRecords() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        assertTrue(f.debug("practice:green-hill:BONGOS:sonic:MEDIUM"));
        f.step(); f.step(); f.step();
        for (int i = 0; i < 500 && !f.screen().equals("RESULTS"); i++) f.step();
        assertEquals("RESULTS", f.screen());
        assertEquals(0, f.score());
        assertFalse(f.saves.containsKey("profile.txt"), "practice cannot produce earned records");
        f.scene.exit(f);
    }

    @Test void physicalMenuDirectionsIgnoreConflictingGenesisMappingsAndRepeatHeldArrows() throws Exception {
        Fixture f = new Fixture(List.of("s1")); f.logicalRepeat = SceneButtons.UP;
        f.key(SceneKeys.DOWN);
        var selected = type.getDeclaredField("selected"); selected.setAccessible(true);
        assertEquals(1, selected.getInt(f.scene), "mapped UP cannot cancel physical DOWN");
        f.held.add(SceneKeys.DOWN);
        for (int i = 0; i < 20; i++) f.step();
        assertTrue(selected.getInt(f.scene) >= 2, "held raw arrows repeat independently of Genesis bindings");
        f.scene.exit(f);
    }

    @Test void calibrationFromResultsPreservesCompletedAttemptAndSafeReturn() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        assertTrue(f.debug("practice:green-hill:BONGOS:sonic:MEDIUM")); assertTrue(f.debug("autoplay"));
        for (int i = 0; i < 500 && !f.screen().equals("RESULTS"); i++) f.step();
        assertEquals("RESULTS", f.screen());
        Object original = f.session(); long score = f.score();
        var resultField = type.getDeclaredField("result"); resultField.setAccessible(true);
        Object result = resultField.get(f.scene);
        f.key(SceneKeys.TAB); for (int i = 0; i < 13; i++) f.key(SceneKeys.DOWN);
        f.key(SceneKeys.ENTER); f.step(); f.step(); assertEquals("CALIBRATION", f.screen());
        f.key(SceneKeys.ESCAPE); f.key(SceneKeys.ESCAPE); assertEquals("RESULTS", f.screen());
        assertSame(original, f.session()); assertSame(result, resultField.get(f.scene)); assertEquals(score, f.score());
        var canvas = mock(SceneCanvas.class); when(canvas.width()).thenReturn(400); when(canvas.height()).thenReturn(224);
        assertDoesNotThrow(() -> f.scene.draw(f, canvas)); f.scene.exit(f);
    }

    @Test void guestCanRetryAnOfferedRoundAfterCancellingLoading() throws Exception {
        Fixture f = new Fixture(List.of("s1")); f.music.rate = 8_000;
        ScenePeer peer = mock(ScenePeer.class); when(peer.state()).thenReturn(ScenePeer.State.CONNECTED);
        when(peer.send(anyString())).thenReturn(true);
        when(peer.poll()).thenReturn(List.of(new ScenePeer.Message("SH1 HELLO s1", 0),
                new ScenePeer.Message("SH1 OFFER 1 green-hill BONGOS MEDIUM coop", 0))).thenReturn(List.of());
        Class<?> onlineType = harness.loader().loadClass("sitarhero.net.OnlineMatch");
        Object match = onlineType.getConstructor(ScenePeer.class, boolean.class, List.class).newInstance(peer, false, List.of("s1"));
        var field = type.getDeclaredField("online"); field.setAccessible(true); field.set(f.scene, match);
        f.step(); assertEquals("LOBBY", f.screen());
        f.key(SceneKeys.ENTER); assertEquals("LOADING", f.screen());
        f.key(SceneKeys.ESCAPE); assertEquals("LOBBY", f.screen());
        f.key(SceneKeys.ENTER); f.step(); f.step();
        assertEquals("LOBBY", f.screen()); assertEquals(true, onlineType.getMethod("ready").invoke(match));
        verify(peer).send(matches("SH1 READY 1 8000 [0-9a-f]+")); f.scene.exit(f);
    }

    @Test void mouseActivatesVisibleRowsAndIgnoresOutsideClicks() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        f.mouse(395, 10, true, 0); assertEquals("TITLE", f.screen());
        f.mouse(35, 81, true, 0); assertEquals("CHARACTERS", f.screen(), "quick-play title row");
        f.mouse(35, 64, true, 0); assertEquals("ROLES", f.screen());
        f.mouse(35, 95, true, 0); assertEquals("DIFFICULTY", f.screen(), "Bongos role");
        f.mouse(35, 95, true, 0); assertEquals("SONGS", f.screen());
        f.mouse(395, 10, true, 0); assertEquals("SONGS", f.screen());
        f.mouse(35, 64, false, -6);
        var selected = type.getDeclaredField("selected"); selected.setAccessible(true);
        assertTrue(selected.getInt(f.scene) >= 4, "wheel reaches songs beyond first page");
        f.mouse(35, 64, true, 0); f.step(); f.step(); assertEquals("PLAY", f.screen());
        f.key(SceneKeys.ESCAPE); assertEquals("PAUSED", f.screen());
        f.mouse(395, 10, true, 0); assertEquals("PAUSED", f.screen());
        f.mouse(100, 90, true, 0); assertEquals("PLAY", f.screen(), "visible resume action");
        f.scene.exit(f);
    }

    @Test void playerTwoCalibrationUsesItsOwnKeyboardAndPad() throws Exception {
        Fixture f = new Fixture(List.of("s1")); assertTrue(f.debug("settings"));
        for (int i = 0; i < 15; i++) f.key(SceneKeys.DOWN);
        f.key(SceneKeys.ENTER); f.key(SceneKeys.UP); f.key(SceneKeys.UP); f.key(SceneKeys.ENTER);
        f.step(); f.step(); assertEquals("CALIBRATION", f.screen());
        var field = type.getDeclaredField("calibration"); field.setAccessible(true);
        f.music.player.position = 450; f.pad(PhysicalGamepad.BUTTON_A, 0);
        assertTrue(((List<?>) field.get(f.scene)).isEmpty());
        f.music.player.position = 450; f.key(SceneKeys.SPACE);
        assertTrue(((List<?>) field.get(f.scene)).isEmpty());
        f.music.player.position = 450; f.key(SceneKeys.P);
        assertEquals(1, ((List<?>) field.get(f.scene)).size());
        for (int i = 2; i <= 8; i++) {
            f.music.player.position = i * 500 - 50; f.pad(PhysicalGamepad.BUTTON_A, 1);
        }
        f.key(SceneKeys.ENTER); assertEquals("SETTINGS", f.screen());
        assertTrue(f.saves.containsKey("settings-p2.txt")); assertFalse(f.saves.containsKey("profile.txt"));
        f.scene.exit(f);
    }

    @Test void allSevenLibrariesReachSelectionWithCorrectRoster() throws Exception {
        for (int subset = 1; subset < 8; subset++) {
            List<String> games = new ArrayList<>(); if ((subset & 1) != 0) games.add("s1"); if ((subset & 2) != 0) games.add("s2"); if ((subset & 4) != 0) games.add("s3k");
            Fixture f = new Fixture(games);
            assertEquals("TITLE", f.screen());
            assertEquals(((List<?>) harness.loader().loadClass("sitarhero.model.SongCatalog").getMethod("available", List.class).invoke(null, games)).size(), ((List<?>) type.getMethod("availableSongs").invoke(f.scene)).size());
            int expected = 2 + ((subset & 6) != 0 ? 1 : 0) + ((subset & 2) != 0 ? 1 : 0) + ((subset & 4) != 0 ? 3 : 0);
            assertEquals(expected, ((List<?>) type.getMethod("availablePerformers").invoke(f.scene)).size());
            f.key(SceneKeys.DOWN); // Quick Play keeps the complete public library open.
            f.key(SceneKeys.ENTER); assertEquals("CHARACTERS", f.screen());
            f.key(SceneKeys.ENTER); assertEquals("ROLES", f.screen());
            f.step(); f.step(); assertEquals("ROLES", f.screen());
            f.key(SceneKeys.ENTER); assertEquals("DIFFICULTY", f.screen());
            f.key(SceneKeys.ENTER); assertEquals("SONGS", f.screen());
            f.scene.exit(f);
        }
    }
    @Test void ordinaryPadMenusDoNotDependOnGenesisMappings() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("CHARACTERS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("ROLES", f.screen());
        f.pad(PhysicalGamepad.BUTTON_DPAD_DOWN);
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("DIFFICULTY", f.screen());
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("TOURS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("WORLDS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_A); assertEquals("STORY", f.screen());
        f.pad(PhysicalGamepad.BUTTON_B); assertEquals("SONGS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_A); f.step(); f.step(); assertEquals("PLAY", f.screen());
        f.scene.exit(f);
    }
    @Test void rawPadBackAndSettingsAcceptDoNotCollideWithGenesisAliases() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        f.pad(PhysicalGamepad.BUTTON_DPAD_DOWN); // Exercise the flat Quick Play return route.
        for (int i = 0; i < 4; i++) f.pad(PhysicalGamepad.BUTTON_A);
        assertEquals("SONGS", f.screen());
        f.pad(PhysicalGamepad.BUTTON_B); assertEquals("DIFFICULTY", f.screen());
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

    @Test void localPlayersStrikeIndependentlyOnOneClockAndDoNotSaveSoloRecords() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        assertTrue(f.debug("local:green-hill:BONGOS:sonic:MEDIUM:coop"));
        f.step(); f.step(); f.step();
        Object p1 = f.session();
        var field = type.getDeclaredField("session2"); field.setAccessible(true);
        Object p2 = field.get(f.scene);
        f.music.player.position = -50;
        f.key(SceneKeys.SPACE);
        assertEquals(1, p1.getClass().getMethod("hits").invoke(p1));
        assertEquals(0, p2.getClass().getMethod("hits").invoke(p2));
        f.key(SceneKeys.P);
        assertEquals(1, p2.getClass().getMethod("hits").invoke(p2));
        f.key(SceneKeys.ESCAPE); assertEquals("PAUSED", f.screen());
        f.key(SceneKeys.ENTER); assertEquals("PLAY", f.screen());
        for (int i = 0; i < 500 && !f.screen().equals("RESULTS"); i++) f.step();
        assertEquals("RESULTS", f.screen());
        assertFalse(f.saves.containsKey("profile.txt"));
        f.scene.exit(f);
    }

    @Test void calibrationSelectsRealDrumsWhenTheChosenSongHasNone() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        assertTrue(f.debug("practice:s1-special-stage:SITAR:sonic:MEDIUM"));
        f.key(SceneKeys.ESCAPE); assertEquals("SONGS", f.screen());
        f.key(SceneKeys.TAB); assertEquals("SETTINGS", f.screen());
        for (int i = 0; i < 13; i++) f.key(SceneKeys.DOWN);
        f.key(SceneKeys.ENTER); f.step(); f.step(); f.step();
        assertEquals("CALIBRATION", f.screen());
        assertEquals(0x81, f.music.lastPreparedId, "same-game GHZ has real percussion");
        assertEquals(1800, f.music.lastPreparedFrames, "calibration needs only thirty seconds");
        var songField = type.getDeclaredField("song"); songField.setAccessible(true);
        assertEquals("s1-special-stage", songField.get(f.scene).getClass().getMethod("id").invoke(songField.get(f.scene)), "selection is preserved");
        f.scene.exit(f);
    }

    @Test void cancelledCalibrationDoesNotLeaveAPracticeAttemptEligibleForRecords() throws Exception {
        Fixture f = new Fixture(List.of("s1"));
        assertTrue(f.debug("settings"));
        for (int i = 0; i < 13; i++) f.key(SceneKeys.DOWN);
        f.key(SceneKeys.ENTER); f.step(); f.step(); f.step();
        assertEquals("CALIBRATION", f.screen());
        f.key(SceneKeys.ESCAPE); assertEquals("SETTINGS", f.screen());
        assertFalse(f.saves.containsKey("profile.txt"));
        f.scene.exit(f);
    }
}
