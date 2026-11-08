package openggf.timeattack;

import openggf.timeattack.mp.VoteTrackPools;
import openggf.timeattack.ui.FakeViewInput;
import openggf.timeattack.ui.MenuCue;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Custom vote pools in the mod settings and the room owner's pool editor. */
class TestTimeAttackSettingsVotePools {

    @Test
    void poolsAndChoicesRoundTripPerGame() {
        MemoryModStorage storage = new MemoryModStorage();
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.putVotePool("s1", null, new VotePool("Boss acts", List.of("0:2", "1:2", "2:2")));
        settings.putVotePool("s3k", null, new VotePool("Angel | Hydro", List.of("0:0", "1:0", "2:0")));
        settings.putVotePool("s3k", null, new VotePool("Ébauche", List.of("3:0", "5:0", "7:0")));
        settings.setVotePoolChoice("s3k", "Ébauche");
        assertTrue(settings.save(storage));
        String text = storage.read(TimeAttackSettings.FILE_NAME).orElseThrow();
        assertTrue(text.contains("votePool.s1.1=0:2,1:2,2:2|Boss acts\n"), text);
        assertTrue(text.contains("votePool.s3k.1=0:0,1:0,2:0|Angel | Hydro\n"), text);
        assertTrue(text.contains("votePoolChoice.s3k=Ébauche\n"), text);

        TimeAttackSettings loaded = TimeAttackSettings.load(storage);
        assertEquals(settings.votePools("s1"), loaded.votePools("s1"));
        assertEquals(List.of("Angel | Hydro", "Ébauche"),
                loaded.votePools("s3k").stream().map(VotePool::name).toList());
        assertEquals("Ébauche", loaded.votePoolChoice("s3k"));
        assertEquals("", loaded.votePoolChoice("s1"), "no choice means the default pool");
        assertTrue(loaded.votePools("s2").isEmpty());
    }

    @Test
    void unreadablePoolsAreDroppedAndAStaleChoiceFallsBackToDefault() {
        TimeAttackSettings settings = TimeAttackSettings.parse("""
                votePool.s2.2=0:0,1:0,2:0|Second
                votePool.s2.1=3:0,4:0|First
                votePool.s2.3=0:0,0:0,1:0|Duplicate act
                votePool.s2.4=zero:one|Bad act
                votePool.s2.5=0:0,1:0,2:0|
                votePool.s2.6=0:0,1:0,2:0|Second
                votePool.S2.1=0:0|Bad game
                votePool.s2.x=0:0|Bad number
                votePoolChoice.s2=Missing
                votePoolChoice.s1=
                """);
        assertEquals(List.of("First", "Second"), settings.votePools("s2").stream().map(VotePool::name).toList(),
                "pools keep their stored order; malformed entries and repeated names are dropped");
        assertEquals("", settings.votePoolChoice("s2"));
    }

    @Test
    void poolLimitsAndNamesAreEnforced() {
        TimeAttackSettings settings = new TimeAttackSettings();
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= VotePool.MAX_ACTS; i++) tooMany.add(i + ":0");
        assertThrows(IllegalArgumentException.class, () -> new VotePool("Big", tooMany));
        assertThrows(IllegalArgumentException.class, () -> new VotePool("  padded ", List.of("0:0")));
        assertThrows(IllegalArgumentException.class, () -> new VotePool("Dup", List.of("0:0", "0:0")));
        for (int i = 1; i <= TimeAttackSettings.MAX_VOTE_POOLS_PER_GAME; i++) {
            settings.putVotePool("s1", null, new VotePool("P" + i, List.of("0:0", "0:1", "0:2")));
        }
        assertThrows(IllegalStateException.class,
                () -> settings.putVotePool("s1", null, new VotePool("One more", List.of("0:0"))));
        assertThrows(IllegalArgumentException.class,
                () -> settings.putVotePool("s1", "P1", new VotePool("P2", List.of("0:0"))));
        assertThrows(IllegalArgumentException.class, () -> settings.setVotePoolChoice("s1", "Nope"));

        settings.setVotePoolChoice("s1", "P1");
        settings.putVotePool("s1", "P1", new VotePool("Renamed", List.of("1:0", "1:1", "1:2")));
        assertEquals("Renamed", settings.votePoolChoice("s1"), "a renamed pool stays chosen");
        settings.removeVotePool("s1", "Renamed");
        assertEquals("", settings.votePoolChoice("s1"), "deleting the chosen pool returns to the default");
    }

    @Test
    void editorStartsFromTheDefaultPoolAddsBossActsAndSaves() {
        TimeAttackSettings settings = new TimeAttackSettings();
        AtomicInteger changes = new AtomicInteger();
        List<MenuCue> cues = new ArrayList<>();
        VotePoolEditorView view = new VotePoolEditorView(settings, "s1", changes::incrementAndGet, cues::add);
        FakeViewInput input = new FakeViewInput();

        input.tapEnter(view::update); // DEFAULT row: a new pool seeded with the default acts
        assertTrue(view.isEditingPool());
        // Rows: NAME, then the eligible acts in game order; GREEN HILL 3 is the third act.
        input.tapDown(view::update);
        input.tapDown(view::update);
        input.tapDown(view::update);
        input.tapEnter(view::update); // add GREEN HILL 3 (a prison-capsule act)
        input.tapDown(view::update);
        input.tapEnter(view::update); // remove MARBLE 1
        for (int i = 0; i < TimeAttackTrackCatalog.eligibleFor("s1").size() - 4 + 1; i++) {
            input.tapDown(view::update);
        }
        input.tapEnter(view::update); // SAVE
        assertFalse(view.isEditingPool(), view.status());
        assertEquals(1, changes.get());

        VotePool saved = settings.votePool("s1", "POOL 1").orElseThrow();
        List<String> expected = new ArrayList<>();
        for (String key : VoteTrackPools.forGame("s1")) expected.add(key.substring(3));
        expected.add(2, "0:2");
        expected.remove("1:0");
        assertEquals(expected, saved.acts(), "saved in catalog order with the edits applied");
        assertTrue(VoteTrackPools.rejection("s1", VoteTrackPools.keysOf("s1", saved), null).isEmpty());
    }

    @Test
    void editorRefusesTooFewActsAndExplainsTheLimit() {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.putVotePool("s2", null, new VotePool("Mine", List.of("0:0", "0:1", "1:0")));
        VotePoolEditorView view = new VotePoolEditorView(settings, "s2", () -> { }, cue -> { });
        FakeViewInput input = new FakeViewInput();
        input.tapDown(view::update); // DEFAULT -> Mine
        input.tapEnter(view::update);
        input.tapDown(view::update); // NAME -> EMERALD HILL 1
        input.tapEnter(view::update); // remove it: two acts left
        for (int i = 0; i < TimeAttackTrackCatalog.eligibleFor("s2").size(); i++) input.tapDown(view::update);
        input.tapEnter(view::update); // SAVE
        assertTrue(view.isEditingPool());
        assertEquals("Choose at least 3 acts so a vote has a choice", view.status());
        assertEquals(List.of("0:0", "0:1", "1:0"), settings.votePool("s2", "Mine").orElseThrow().acts());

        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        assertTrue(canvas.shows("of 32 acts"), "the act limit is on the page");

        input.tapDown(view::update); // DELETE
        input.tapEnter(view::update);
        assertTrue(settings.votePools("s2").isEmpty());
    }

    @Test
    void competitionEditorListsTheArenasUnderTheirOwnCaptionAndSavesThem() {
        TimeAttackSettings settings = new TimeAttackSettings();
        VotePoolEditorView view = new VotePoolEditorView(settings, "s3k", CompetitionRoomSettings.Mode.COMPETITION,
                () -> { }, cue -> { });
        FakeViewInput input = new FakeViewInput();
        input.tapEnter(view::update); // a new pool from the default acts
        // NAME, the ACTS caption (skipped), the acts, the ARENAS caption (skipped), then the arenas.
        input.tapDown(view::update);
        assertEquals(2, view.focusIndex(), "the arrows step over the Acts caption");
        int acts = TimeAttackTrackCatalog.eligibleFor("s3k").size();
        for (int i = 0; i < acts; i++) input.tapDown(view::update);
        assertEquals(acts + 3, view.focusIndex(), "and over the Arenas caption, to Azure Lake");
        input.tapEnter(view::update); // add AZURE LAKE
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        assertTrue(canvas.shows("ARENAS"), canvas.joined());
        assertTrue(canvas.shows("5 LAPS") && canvas.shows("COMP ONLY"), "the focused arena's badges");
        assertTrue(canvas.shows("of 32 tracks"));
        input.tapUp(view::update);
        assertEquals(acts + 1, view.focusIndex(), "up from the first arena skips its caption");
        for (int i = 0; i < 6; i++) input.tapDown(view::update); // past the five arenas to SAVE
        input.tapEnter(view::update);
        VotePool saved = settings.votePools("s3k").getFirst();
        assertEquals("arena:0", saved.acts().getLast(), view.status());
        assertTrue(VoteTrackPools.rejection("s3k", VoteTrackPools.keysOf("s3k", saved), null,
                CompetitionRoomSettings.Mode.COMPETITION).isEmpty());
        assertTrue(VoteTrackPools.rejection("s3k", VoteTrackPools.keysOf("s3k", saved), null,
                CompetitionRoomSettings.Mode.TIME_ATTACK).isPresent(), "arenas are Competition only");
    }

    @Test
    void timeAttackEditorHidesArenasButKeepsThemOnSave() {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.putVotePool("s3k", null, new VotePool("Mixed", List.of("0:0", "0:1", "1:0", "arena:2")));
        VotePoolEditorView view = new VotePoolEditorView(settings, "s3k", () -> { }, cue -> { });
        FakeViewInput input = new FakeViewInput();
        input.tapDown(view::update); // DEFAULT -> Mixed
        input.tapEnter(view::update);
        RecordingSceneCanvas canvas = new RecordingSceneCanvas(320);
        view.draw(canvas);
        assertFalse(canvas.shows("ARENAS"));
        for (int i = 0; i <= TimeAttackTrackCatalog.eligibleFor("s3k").size(); i++) input.tapDown(view::update);
        input.tapEnter(view::update); // SAVE
        assertEquals(List.of("0:0", "0:1", "1:0", "arena:2"), settings.votePool("s3k", "Mixed").orElseThrow().acts());
    }
}
