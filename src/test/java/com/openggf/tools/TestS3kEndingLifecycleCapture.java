package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance;
import com.openggf.game.sonic3k.ending.S3kPostCreditsObjectInstance;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** Declared D01 scene entry, distinct from incoming DEZ/DDZ campaign qualification. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingLifecycleCapture {
    @ParameterizedTest
    @CsvSource({"sonic,,0000000,-2", "sonic,,1111111,0", "sonic,,3333333,2",
            "tails,,0000000,-2", "tails,,1111111,0", "tails,,3333333,2",
            "sonic,tails,0000000,-2", "sonic,tails,1111111,0", "sonic,tails,3333333,2"})
    void nativeEndingCompletesBothCreditPartsAndReturnsToTitleWithPhaseReplay(
            String character, String sidekick, String emeralds, int progress) throws Exception {
        runEnding(character, sidekick, emeralds, progress, false);
    }

    @ParameterizedTest
    @CsvSource({"sonic,,0000000,-2", "sonic,,1111111,0", "sonic,,3333333,2",
            "tails,,0000000,-2", "tails,,1111111,0", "tails,,3333333,2",
            "sonic,tails,0000000,-2", "sonic,tails,1111111,0", "sonic,tails,3333333,2"})
    void palEndingCompletesBothCreditPartsAndReturnsToTitleWithPhaseReplay(
            String character, String sidekick, String emeralds, int progress) throws Exception {
        runEnding(character, sidekick, emeralds, progress, true);
    }

    private void runEnding(String character, String sidekick, String emeralds, int progress,
                           boolean pal) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, character, sidekick, "off",
                null, null, null, emeralds, false, false);
        boolean pair = "tails".equals(sidekick);
        var checked = new HashSet<String>();
        boolean creditsComplete = false;
        boolean terminalSeen = false;
        boolean startIssued = false;
        String lastPhase = "entry";
        try (var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(
                    com.openggf.configuration.SonicConfiguration.REGION, pal ? "PAL" : "NTSC");
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 13, 1, settings);
            var entryRegistry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var beforeEntry = pair ? entryRegistry.capture() : null;
            if (pair) assertEquals(1, GameServices.sprites().getSidekicks().size());
            for (int frame = 0; frame < 60_000; frame++) {
                var pad = input(frame, startIssued);
                session.step(pad);
                session.renderFrame();
                assertTrue(GameServices.sprites().getSidekicks().isEmpty(), "loc_5D8BC clears native P2");
                if (pair && frame == 0) {
                    verifyTeamRetirement(session, pad, beforeEntry);
                    checked.add("team-retirement");
                }
                if (session.loop().getCurrentGameMode() == GameMode.TITLE_SCREEN) {
                    assertTrue(creditsComplete, "both native credit parts completed");
                    assertTrue(terminalSeen, "the real terminal object ran");
                    assertTrue(startIssued, "Start, rather than the terminal timeout, returned to title");
                    var required = new HashSet<>(Set.of("rescue-flight", "reunion",
                            "credits-part-one", "credits-part-two", "reunion-launch", "post-banner"));
                    if (progress > 0) required.add("aiz-return");
                    if (pair) required.add("team-retirement");
                    assertEquals(required, checked);
                    // Use the production fresh-load boundary after returning to
                    // title. The native screen origin must not follow reused P1.
                    GameServices.level().loadZoneAndAct(0, 0);
                    var freshPlayer = GameServices.sprites().getMainPlayable();
                    assertEquals(freshPlayer.getCentreX(), freshPlayer.getRenderCentreX());
                    assertEquals(freshPlayer.getCentreY(), freshPlayer.getRenderCentreY());
                    System.out.println("S3K ending " + character + " progress=" + progress
                            + " title input=" + frame + " replay=" + checked);
                    return;
                }
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), "ending stays inside D01");
                assertFalse(session.player().getDead(), "ending death at input " + frame);
                assertFalse(session.loop().isPaused(), "the ending owns Start rather than pause");
                var state = (EndingZoneRuntimeState) GameServices.zoneRuntimeState();
                assertTrue(state.ending().initialized(), "first Process_Sprites initializes native progress");
                assertEquals(progress, state.ending().progress());
                creditsComplete |= state.ending().credits().complete();
                var manager = GameServices.level().getObjectManager();
                var director = manager.activeObjectsOfType(EndingRescueDirectorObjectInstance.class)
                        .stream().findFirst().orElse(null);
                var post = manager.activeObjectsOfType(S3kPostCreditsObjectInstance.class)
                        .stream().findFirst().orElse(null);
                terminalSeen |= post != null;
                String spot = null;
                if (director != null) {
                    lastPhase = "director=" + director.sequence() + "/" + director.routine();
                    if (director.sequence() == EndingRescueDirectorObjectInstance.RESCUE
                            && director.routine() == 6) spot = "rescue-flight";
                    if (director.sequence() == EndingRescueDirectorObjectInstance.AIZ_RETURN) spot = "aiz-return";
                    if (director.sequence() == EndingRescueDirectorObjectInstance.REUNION) {
                        if (director.routine() == 2) spot = "reunion";
                        if (director.routine() == 6) spot = "reunion-launch";
                    }
                }
                var credits = state.ending().credits();
                if (pal && credits.active()) {
                    int speed = progress < 2 ? 0xF5 : "tails".equals(character) ? 0xFB : 0xFD;
                    assertEquals(speed << 8, credits.speed(), "production beginCredits selects the PAL half");
                }
                if (credits.routine() == 4 && credits.rowIndex() > 0)
                    spot = credits.partIndex() == 0 ? "credits-part-one" : "credits-part-two";
                if (post != null) {
                    lastPhase = "post=" + post.phase();
                    if (post.phase() == 1) spot = "post-banner";
                    if (post.phase() >= 4 && creditsComplete) startIssued = true;
                }
                if (spot != null && checked.add(spot)) replay(session, pad, frame, spot);
                if (frame % 5_000 == 0) System.out.println("S3K ending " + character + "/" + progress
                        + " input=" + frame + " " + lastPhase + " fg=" + state.screen().foregroundRoutine
                        + " credits=" + credits.routine() + "/" + credits.partIndex() + "/" + credits.rowIndex());
            }
            fail("Ending did not return to title: " + character + "/" + progress + " " + lastPhase + " replay=" + checked);
        }
    }

    private static void verifyTeamRetirement(GameplayCaptureSession session, Bk2FrameInput firstInput,
                                             CompositeSnapshot beforeEntry) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var entered = registry.capture();
        registry.restore(beforeEntry);
        same(beforeEntry, registry.capture(), "team before first object pass");
        var followers = GameServices.sprites().getSidekicks();
        assertEquals(1, followers.size());
        assertEquals(com.openggf.game.CharacterKey.TAILS, followers.getFirst().characterKey());
        assertSame(GameServices.sprites().getMainPlayable(), GameServices.camera().getFocusedSprite());
        session.restoreInputHistory(null);
        session.step(firstInput);session.renderFrame();
        assertTrue(GameServices.sprites().getSidekicks().isEmpty(), "native P2 retirement repeats after restore");
        same(entered, registry.capture(), "team initial pass forward replay");
        session.restoreInputHistory(firstInput);
    }

    private static Bk2FrameInput input(int frame, boolean start) {
        return new Bk2FrameInput(frame, 0, 0, start, 0, 0, false, start ? "Start" : "neutral");
    }

    private static void replay(GameplayCaptureSession session, Bk2FrameInput pad, int frame, String label) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int n = 1; n <= 30; n++) { session.step(input(frame + n, false)); session.renderFrame(); }
        var expected = registry.capture();
        registry.restore(saved);
        same(saved, registry.capture(), label + " restore");
        session.restoreInputHistory(pad);
        for (int n = 1; n <= 30; n++) { session.step(input(frame + n, false)); session.renderFrame(); }
        same(expected, registry.capture(), label + " forward replay");
        registry.restore(saved);
        session.restoreInputHistory(pad);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> label + " " + key + ": " + differences);
        }
    }
}
