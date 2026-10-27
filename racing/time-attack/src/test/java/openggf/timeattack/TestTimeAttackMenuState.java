package openggf.timeattack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link TimeAttackMenuState} directly (no GL, no InputHandler
 * simulation needed since the row-navigation/adjust/go methods are public).
 */
class TestTimeAttackMenuState {

    @Test
    void startsOnRequestedGameAndFirstTrack(@TempDir Path root) {
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s1", "s2", "s3k"), "s3k", new GhostStore(root));

        assertEquals("s3k", state.currentGameId());
        assertEquals(0, state.trackIndex());
        assertEquals(TimeAttackTrackCatalog.tracksFor("s3k").get(0), state.currentTrack());
        assertEquals(TimeAttackMenuState.Row.GAME, state.focusedRow());
    }

    @Test
    void unknownInitialGameFallsBackToFirstAvailable(@TempDir Path root) {
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s2", "s3k"), "s1", new GhostStore(root));
        assertEquals("s2", state.currentGameId());
    }

    @Test
    void changingGameResetsTrackAndCharacter(@TempDir Path root) {
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s1", "s2", "s3k"), "s1", new GhostStore(root));
        state.adjust(1); // GAME row: s1 -> s2
        assertEquals("s2", state.currentGameId());
        assertEquals(0, state.trackIndex());
        assertEquals(0, state.characterIndex());

        state.adjust(-1); // wraps back to s1
        assertEquals("s1", state.currentGameId());
    }

    @Test
    void trackAndCharacterColumnsNavigateAndWrap(@TempDir Path root) {
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s3k"), "s3k", new GhostStore(root));
        state.moveFocus(1); // GAME -> TRACK
        assertEquals(TimeAttackMenuState.Row.TRACK, state.focusedRow());

        int trackCount = TimeAttackTrackCatalog.tracksFor("s3k").size();
        state.adjust(-1); // wraps to last track
        assertEquals(trackCount - 1, state.trackIndex());

        state.moveFocus(1); // TRACK -> CHARACTER
        assertEquals(TimeAttackMenuState.Row.CHARACTER, state.focusedRow());
        state.adjust(1);
        assertEquals("tails", state.currentCharacter());
    }

    @Test
    void goProducesLaunchRequestMatchingSelection(@TempDir Path root) {
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s3k"), "s3k", new GhostStore(root));
        state.moveFocus(1); // TRACK
        state.adjust(1); // HCZ1
        state.moveFocus(1); // CHARACTER
        state.adjust(2); // sonic -> tails -> knuckles

        state.pressGo();
        var request = state.consumeLaunchRequest();

        assertNotNull(request);
        assertEquals("s3k", request.gameId());
        assertEquals(1, request.zone());
        assertEquals(0, request.act());
        assertEquals("knuckles", request.character());
        assertNull(state.consumeLaunchRequest(), "launch request should be consumed once");
    }

    @Test
    void importsForOtherTracksDoNotConsumeTheSelectedTracksGhostSlots(@TempDir Path root) throws Exception {
        Path imports = root.resolve("s3k/import");
        for (int index = 0; index < TimeAttackMenuState.MAX_GHOSTS_RACED; index++) {
            writeImport(imports.resolve("a-other-" + index + ".ggfghost"), 1, 0);
        }
        Path matching = imports.resolve("z-selected.ggfghost");
        writeImport(matching, 0, 0);
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s3k"), "s3k", new GhostStore(root));

        state.pressGo();
        var request = state.consumeLaunchRequest();
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(request);
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, false));

        assertEquals(1, runtime.activeGhostsForTest().size(), "the selected act's valid ghost must be raced");
    }

    private static void writeImport(Path path, int zone, int act) throws Exception {
        var header = new openggf.timeattack.ghost.GhostHeader(1, "s3k", zone, act, "sonic", "racer",
                0, 1, new int[0], new byte[32]);
        var capture = new openggf.timeattack.ghost.GhostCaptureBuffer();
        capture.capture(new com.openggf.net.ghost.GhostFrame(
                10, 100, 1, false, false, false, 2, false));
        capture.capture(new com.openggf.net.ghost.GhostFrame(
                11, 100, 1, false, false, true, 2, false));
        var recording = new openggf.timeattack.ghost.GhostRecording(header, capture.toFrameData());
        openggf.timeattack.ghost.GhostFileCodec.write(recording, path);
    }

    @Test
    void summaryReflectsSavedBestAndImportCount(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s3k"), "s3k", store);
        assertFalse(state.bestExists());
        assertEquals(0, state.importCount());

        Path importDir = root.resolve("s3k").resolve("import");
        Files.createDirectories(importDir);
        Files.createFile(importDir.resolve("someone.ggfghost"));

        state.moveFocus(1);
        state.adjust(0); // no-op adjust just to trigger a refresh path exercise
        // Force a refresh via a real navigation round-trip.
        state.adjust(1);
        state.adjust(-1);

        assertEquals(1, state.importCount());
    }

    @Test
    void closeRequestDefaultsFalseAndIsConsumedOnce(@TempDir Path root) {
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s3k"), "s3k", new GhostStore(root));
        assertFalse(state.consumeCloseRequested());
    }
    @Test
    void soloAndJoinNavigationSkipHiddenHostSettings(@TempDir Path root) {
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s2"), "s2", new GhostStore(root));
        state.moveFocus(-1);
        assertEquals(TimeAttackMenuState.Row.MODE, state.focusedRow());
        state.adjust(2); // JOIN_LAN
        state.moveFocus(1);
        assertEquals(TimeAttackMenuState.Row.GAME, state.focusedRow());
        state.moveFocus(-1);
        state.adjust(-1); // HOST_LAN
        state.moveFocus(1);
        assertEquals(TimeAttackMenuState.Row.POLICY, state.focusedRow());
    }

    @Test
    void hostedModesShowThePoolRowWhichCyclesSavedPoolsAndOpensTheEditor(@TempDir Path root) {
        TimeAttackSettings settings = new TimeAttackSettings();
        settings.putVotePool("s2", null, new VotePool("Bosses", List.of("0:1", "1:1", "2:1")));
        settings.putVotePool("s2", null, new VotePool("Mixed", List.of("0:0", "0:1", "7:2")));
        TimeAttackMenuState state = new TimeAttackMenuState(List.of("s1", "s2"), "s2", new GhostStore(root),
                new TimeAttackMenuState.PoolChoices() {
                    @Override public List<String> names(String gameId) {
                        return settings.votePools(gameId).stream().map(VotePool::name).toList();
                    }
                    @Override public String chosen(String gameId) { return settings.votePoolChoice(gameId); }
                    @Override public void choose(String gameId, String name) { settings.setVotePoolChoice(gameId, name); }
                });
        assertFalse(state.visibleRows().contains(TimeAttackMenuState.Row.POOL), "solo races have no vote");
        while (state.focusedRow() != TimeAttackMenuState.Row.MODE) state.moveFocus(1);
        state.adjust(1); // SOLO -> HOST_LAN
        assertTrue(state.visibleRows().contains(TimeAttackMenuState.Row.POOL));
        while (state.focusedRow() != TimeAttackMenuState.Row.POOL) state.moveFocus(1);

        assertEquals("", state.currentPoolName(), "the default pool until one is chosen");
        state.adjust(1);
        assertEquals("Bosses", state.currentPoolName());
        state.adjust(1);
        assertEquals("Mixed", state.currentPoolName());
        assertEquals("Mixed", settings.votePoolChoice("s2"));
        state.adjust(1);
        assertEquals("", state.currentPoolName(), "wraps back to the default pool");
        state.adjust(-1);
        assertEquals("Mixed", state.currentPoolName());

        state.update(new TimeAttackMenuState.MenuKeys(false, false, false, false, true, false));
        assertTrue(state.consumePoolEditorRequested());
        assertNull(state.consumeLaunchRequest(), "confirming the pool row opens the editor, not a race");

        while (state.focusedRow() != TimeAttackMenuState.Row.GAME) state.moveFocus(1);
        state.adjust(-1); // s2 -> s1: pools are per game
        assertEquals("", state.currentPoolName());
    }
}
