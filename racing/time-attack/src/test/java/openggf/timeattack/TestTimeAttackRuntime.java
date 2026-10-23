package openggf.timeattack;

import com.openggf.net.ghost.GhostFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestTimeAttackRuntime {
    @Test
    void finishDeltaComparesTheFinishRatherThanTheLastCheckpoint(@TempDir Path root) throws Exception {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.beginAttemptForTest("fp");
        runtime.tickForTest(0x08, false, false, -1, frame(10));
        runtime.tickForTest(0x08, false, false, 1, frame(11));
        runtime.tickForTest(0x08, false, false, 1, frame(12));
        runtime.tickForTest(0x08, false, false, 1, frame(13));
        runtime.tickForTest(0x08, false, true, 1, frame(14));

        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, false));
        runtime.tickForTest(0x08, false, false, -1, frame(10));
        runtime.tickForTest(0x08, false, false, -1, frame(11));
        runtime.tickForTest(0x08, false, false, 1, frame(12));
        assertEquals(1, runtime.hudState().lastSplitDelta(), "the new attempt reaches the checkpoint one frame later");
        runtime.tickForTest(0x08, false, true, 1, frame(14));

        assertTrue(runtime.hudState().finished());
        assertEquals(3, runtime.hudState().elapsedDisplayFrames());
        assertEquals(4, runtime.hudState().bestTimeFrames());
        assertEquals(-1, runtime.hudState().lastSplitDelta(), "the finish is one frame faster despite the slower checkpoint");
    }
    @Test
    void failedAndRejectedBestWritesHaveVisibleHonestOutcomes(@TempDir Path root) throws Exception {
        Path rejectedRoot=root.resolve("rejected");
        java.nio.file.Files.createDirectories(rejectedRoot.resolve("s3k"));
        Path primary=rejectedRoot.resolve("s3k/0-0-sonic.ggfghost");
        byte[] rejected="future-or-corrupt-ghost".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(primary,rejected);
        TimeAttackRuntime preserved=new TimeAttackRuntime(new GhostStore(rejectedRoot),root.resolve("identity-a"),()->false);
        preserved.armForLaunch(new TimeAttackLaunchRequest("s3k",0,0,"sonic",java.util.List.of()));
        preserved.beginAttemptForTest("fp");
        preserved.tickForTest(0x08,false,true,-1,frame(10));
        assertTrue(preserved.hudState().finished());assertFalse(preserved.hudState().newBest());
        assertEquals("EXISTING RECORD PRESERVED",preserved.hudState().saveNotice());
        assertArrayEquals(rejected,java.nio.file.Files.readAllBytes(primary));

        Path failedRoot=root.resolve("failed");java.nio.file.Files.createDirectories(failedRoot);
        java.nio.file.Files.write(failedRoot.resolve("s3k"),new byte[]{0});
        TimeAttackRuntime failed=new TimeAttackRuntime(new GhostStore(failedRoot),root.resolve("identity-b"),()->false);
        failed.armForLaunch(new TimeAttackLaunchRequest("s3k",0,0,"sonic",java.util.List.of()));
        failed.beginAttemptForTest("fp");failed.tickForTest(0x08,false,true,-1,frame(10));
        assertTrue(failed.hudState().finished());assertFalse(failed.hudState().newBest());
        assertEquals("SAVE NOT CONFIRMED",failed.hudState().saveNotice());
    }
    @Test
    void aDeathRestartVoidsTheRunningAttemptAndHoldsForTheCountdown(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s1", 0, 0, "sonic", java.util.List.of()));
        java.util.List<String> events = new java.util.ArrayList<>();
        runtime.setAttemptListener(new TimeAttackRuntime.AttemptListener() {
            @Override public void onAttemptBegan(int attempt) { events.add("began " + attempt); }
            @Override public void onFrameSampled(int attempt, GhostFrame frame) {
                events.add("frame " + attempt + " x" + frame.x());
            }
            @Override public void onAttemptFinished(int attempt, int time, int first, int finish,
                                                    byte[] hash, AttemptInputRecording recording) {
                events.add("finished " + attempt + " in " + time);
            }
            @Override public void onAttemptVoided(int attempt) { events.add("voided " + attempt); }
        });
        var spec = runtime.runSpec();
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(spec, "fp", false, 0, 0, false));
        assertTrue(runtime.admitStep(NO_KEYS), "a launch starts without a countdown");
        assertEquals(0, runtime.restartCountdownFrames());
        runtime.afterStep(step(0x08, 10, false));
        assertTrue(runtime.isAttemptRunning());

        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(spec, "fp", false, 0, 0, true));
        assertEquals(java.util.List.of("began 1", "frame 1 x10", "voided 1", "began 2"), events,
                "the death ends the running attempt and the reload begins a fresh one");
        assertTrue(runtime.inRestartLeadIn());
        assertEquals(0, runtime.countdownDigit(), "no countdown is drawn over the black reload");
        assertTrue(runtime.admitStep(NO_KEYS), "the spawn step runs so the act is drawn as it starts");
        assertTrue(runtime.holdsPlayerInput(), "lead-in steps run with the player's input held");
        runtime.afterStep(step(0, 80, false));
        assertEquals(0, runtime.countdownDigit(), "no countdown is drawn during the fade-in lead-in");
        assertTrue(runtime.admitStep(FADING), "a level still fading in keeps the lead-in going");
        assertTrue(runtime.holdsPlayerInput());
        runtime.afterStep(step(0, 81, false));
        assertEquals(0, runtime.countdownDigit());
        assertEquals(4, events.size(), "lead-in frames wait for control before reaching the room");
        java.util.List<Integer> digits = new java.util.ArrayList<>();
        for (int frame = 0; frame < TimeAttackRuntime.RESTART_COUNTDOWN_FRAMES; frame++) {
            assertFalse(runtime.admitStep(NO_KEYS), "the countdown holds frame " + frame);
            assertFalse(runtime.holdsPlayerInput());
            assertFalse(runtime.inRestartLeadIn());
            digits.add(runtime.countdownDigit());
        }
        assertEquals(3, digits.get(0), "3 appears once the level is visible and frozen");
        assertEquals(java.util.List.of(3, 2, 1), digits.subList(0, digits.size() - 1).stream().distinct().toList());
        assertEquals(0, digits.get(digits.size() - 1), "the frame that releases control draws no digit");
        assertEquals(0, runtime.countdownDigit(), "no digit once control returns");
        assertEquals(java.util.List.of("frame 2 x80", "frame 2 x81"), events.subList(4, 6),
                "the lead-in frames are released when control returns");
        assertEquals(0, runtime.restartCountdownFrames());
        assertTrue(runtime.admitStep(NO_KEYS), "control returns when the countdown ends");
        assertFalse(runtime.holdsPlayerInput());
        runtime.afterStep(step(0x08, 82, false));
        runtime.afterStep(step(0x08, 83, true));
        assertEquals(java.util.List.of("frame 2 x82", "frame 2 x83", "finished 2 in 1"),
                events.subList(6, events.size()),
                "lead-in steps carry no input and held countdown frames execute nothing: untimed");
    }

    /** Drives a death restart through its lead-in and countdown, logging each sound by step. */
    private static java.util.List<String> restartCountdownSounds(Path root, String gameId) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest(gameId, 0, 0, "sonic", java.util.List.of()));
        java.util.List<String> sounds = new java.util.ArrayList<>();
        int[] stepIndex = {0};
        runtime.setSfxPlayer(id -> sounds.add(String.format("%02X@%d", id, stepIndex[0])));
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, true));
        assertTrue(runtime.admitStep(NO_KEYS), "the spawn step");
        runtime.afterStep(step(0, 80, false));
        stepIndex[0] = 1;
        while (!runtime.admitStep(NO_KEYS)) {
            stepIndex[0]++;
        }
        return sounds;
    }

    @Test
    void anS3kRestartCountdownSoundsLikeTheDeathEggTunnelLauncher(@TempDir Path root) {
        // Obj_DEZTunnelLauncher: LaunchReady ($A7) as the count starts and on each of the next
        // two 60-frame ticks, LaunchGo ($AD) as it releases the player.
        assertEquals(java.util.List.of("A7@1", "A7@60", "A7@120", "AD@181"),
                restartCountdownSounds(root, "s3k"),
                "one ready per digit as it appears, go on the first step with control");
    }

    @Test
    void otherGamesCountDownSilently(@TempDir Path root) {
        assertEquals(java.util.List.of(), restartCountdownSounds(root.resolve("s1"), "s1"));
        assertEquals(java.util.List.of(), restartCountdownSounds(root.resolve("s2"), "s2"));
    }

    @Test
    void aRoundStartCountdownSoundsEachDigitFromThreeAndGoOnlyWhenReleased(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        java.util.List<Integer> sounds = new java.util.ArrayList<>();
        runtime.setSfxPlayer(sounds::add);
        int[] digit = {5};
        boolean[] hold = {true};
        runtime.setFrameCompanion(new TimeAttackRuntime.FrameCompanion() {
            @Override public boolean admit(com.openggf.mods.run.RunInput input) { return !hold[0]; }
            @Override public void afterStep() { }
            @Override public int countdownDigit() { return digit[0]; }
        });
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, false));
        for (int d : new int[] {5, 5, 4, 3, 3, 2, 1, 1}) {
            digit[0] = d;
            assertFalse(runtime.admitStep(NO_KEYS), "the room holds the run at " + d);
        }
        assertEquals(java.util.List.of(0xA7, 0xA7, 0xA7), sounds, "only 3, 2 and 1 sound, once each");
        digit[0] = 0;
        assertFalse(runtime.admitStep(NO_KEYS), "the digit has gone but the room still holds the run");
        assertEquals(3, sounds.size(), "no go before the run is released");
        hold[0] = false;
        assertTrue(runtime.admitStep(NO_KEYS));
        assertTrue(runtime.admitStep(NO_KEYS));
        assertEquals(java.util.List.of(0xA7, 0xA7, 0xA7, 0xAD), sounds, "one go as the run is released");

        // Over the title card the run takes no steps, but the digits are still drawn and sound.
        sounds.clear();
        hold[0] = true;
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, false));
        com.openggf.mods.ui.LevelOverlayCanvas canvas =
                org.mockito.Mockito.mock(com.openggf.mods.ui.LevelOverlayCanvas.class);
        for (int d : new int[] {3, 2, 2, 1}) {
            digit[0] = d;
            runtime.drawOverlay(canvas);
        }
        digit[0] = 0;
        runtime.drawOverlay(canvas);
        assertEquals(java.util.List.of(0xA7, 0xA7, 0xA7), sounds, "drawn digits sound; drawing never says go");
        hold[0] = false;
        assertTrue(runtime.admitStep(NO_KEYS));
        assertEquals(java.util.List.of(0xA7, 0xA7, 0xA7, 0xAD), sounds, "go on the first released step");

        // A countdown left part-way through (the run is never released) never says go.
        sounds.clear();
        hold[0] = true;
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, false));
        digit[0] = 2;
        assertFalse(runtime.admitStep(NO_KEYS));
        digit[0] = 0;
        for (int i = 0; i < 5; i++) {
            assertFalse(runtime.admitStep(NO_KEYS));
        }
        assertEquals(java.util.List.of(0xA7), sounds);
    }

    @Test
    void theGamesOwnHudShowsTheAttemptClockInHundredths(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s1", 0, 0, "sonic", java.util.List.of()));
        java.util.List<Integer> times = new java.util.ArrayList<>();
        boolean[] hundredths = {false};
        runtime.attachHandle(new com.openggf.mods.run.RunHandle() {
            @Override public void retry() { }
            @Override public void leave() { }
            @Override public void spectate(boolean active, int dx, int dy) { }
            @Override public boolean isActive() { return true; }
            @Override public void setHudTime(int frames) { times.add(frames); }
            @Override public void setHudTimeHundredths(boolean shown) { hundredths[0] = shown; }
        });
        assertTrue(hundredths[0], "Time Attack asks for 0:00.00");
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, false));
        assertEquals(0, times.getLast(), "a fresh attempt shows 0:00.00");
        assertTrue(runtime.admitStep(NO_KEYS));
        runtime.afterStep(step(0, 10, false));
        assertEquals(0, times.getLast(), "the clock waits for the first input");
        for (int frame = 0; frame < 3; frame++) {
            assertTrue(runtime.admitStep(NO_KEYS));
            runtime.afterStep(step(0x08, 11 + frame, false));
        }
        assertEquals(runtime.hudState().elapsedDisplayFrames(), times.getLast(),
                "the HUD shows the attempt clock the room times, after every step");
        assertTrue(times.getLast() > 0);
    }

    @Test
    void theRunPanelNoLongerRepeatsTheTime() {
        assertEquals(null, TimeAttackHud.layout(320, 224, new TimeAttackHudState(true, 3210, -1, Integer.MIN_VALUE, false, false)),
                "no best and no split: the game's own TIME says it all");
        var panel = TimeAttackHud.layout(320, 224, new TimeAttackHudState(true, 3210, 3500, -120, false, false));
        assertTrue(panel.lines().stream().noneMatch(line -> line.text().startsWith("TIME")), panel.lines().toString());
        assertTrue(panel.lines().stream().anyMatch(line -> line.text().startsWith("BEST")));
    }

    @Test
    void aLongLeadInDrainsIntoTheRoomInsideTheHostStreamLimits(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        java.util.List<Integer> delivered = new java.util.ArrayList<>();
        runtime.setAttemptListener(new TimeAttackRuntime.AttemptListener() {
            @Override public void onAttemptBegan(int attempt) { }
            @Override public void onFrameSampled(int attempt, GhostFrame frame) { delivered.add(frame.x()); }
            @Override public void onAttemptFinished(int attempt, int time, int first, int finish,
                                                    byte[] hash, AttemptInputRecording recording) { }
            @Override public void onAttemptVoided(int attempt) { }
        });
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, true));
        int x = 0;
        assertTrue(runtime.admitStep(NO_KEYS));
        runtime.afterStep(step(0, x++, false));
        for (int i = 0; i < 22; i++) {
            assertTrue(runtime.admitStep(FADING));
            runtime.afterStep(step(0, x++, false));
        }
        while (runtime.restartCountdownFrames() > 0 || runtime.inRestartLeadIn()) {
            assertFalse(runtime.admitStep(NO_KEYS));
        }
        assertEquals(TimeAttackRuntime.STREAM_RELEASE_BURST, delivered.size(),
                "control returns with one burst below the host's frame lead");
        int steps = 0;
        while (delivered.size() < x + steps) {
            assertTrue(runtime.admitStep(NO_KEYS));
            runtime.afterStep(step(0x08, x + steps, false));
            steps++;
            int expectedMax = TimeAttackRuntime.STREAM_RELEASE_BURST + steps
                    + steps / TimeAttackRuntime.STREAM_CATCH_UP_INTERVAL;
            assertTrue(delivered.size() <= expectedMax, "at most 5 frames per second above play");
            assertTrue(steps < 600, "the backlog drains");
        }
        for (int i = 0; i < delivered.size(); i++) {
            assertEquals(i, delivered.get(i), "frames reach the room in order");
        }
    }

    private static final com.openggf.mods.run.RunInput FADING = new com.openggf.mods.run.RunInput() {
        @Override public com.openggf.control.LogicalInputSnapshot input() { return null; }
        @Override public boolean keyDown(int key) { return false; }
        @Override public boolean keyPressed(int key) { return false; }
        @Override public boolean levelFadingIn() { return true; }
    };

    private static com.openggf.mods.run.RunStep step(int heldMask, int x, boolean actComplete) {
        return new com.openggf.mods.run.RunStep(0, heldMask, false,
                new com.openggf.mods.run.PlayerPose(x, 100, 1, false, false, 2, false), actComplete, -1, false);
    }

    private static final com.openggf.mods.run.RunInput NO_KEYS = new com.openggf.mods.run.RunInput() {
        @Override public com.openggf.control.LogicalInputSnapshot input() { return null; }
        @Override public boolean keyDown(int key) { return false; }
        @Override public boolean keyPressed(int key) { return false; }
    };

    private static GhostFrame frame(int x) {
        return new GhostFrame(x, 100, 1, false, false, false, 2, false);
    }

    @Test
    void armTickFinishSavesBestGhostAndInputs(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        Path identityDir = root.resolve("identity");
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, identityDir, () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        assertTrue(java.nio.file.Files.exists(identityDir.resolve("player-identity.key"))); // identity wired at arm
        runtime.beginAttemptForTest("0.6:cafe");   // package-visible spawn hook used by onLevelReady
        runtime.tickForTest(0, false, false, -1, frame(10));      // spawn idle
        runtime.tickForTest(0x08, false, false, -1, frame(11));   // first input
        runtime.tickForTest(0x08, false, false, 1, frame(12));    // checkpoint 1
        runtime.tickForTest(0x08, false, true, 1, frame(13));     // signpost
        assertTrue(runtime.hudState().finished());
        assertTrue(runtime.hudState().newBest());
        var best = store.loadBest("s3k", 0, 0, "sonic").orElseThrow();
        assertEquals(4, best.frameCount());
        assertEquals(1, best.header().firstInputFrame());
        assertEquals(3, best.header().finishFrame());
        assertEquals(2, best.header().finalTimeFrames());
        assertArrayEquals(new int[] {2}, best.header().splitFrames());
        assertEquals(32, best.header().inputRecordingHash().length);
        assertEquals(8, best.header().displayName().length()); // fingerprint prefix from PlayerIdentity
        assertEquals(com.openggf.net.identity.PlayerIdentity.loadOrCreate(identityDir)
                .fingerprint().substring(0, 8), best.header().displayName());
    }

    @Test
    void taintedAttemptIsNeverSaved(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.markTainted();
        runtime.tickForTest(0x08, false, true, -1, frame(10));
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty());
        assertFalse(runtime.hudState().newBest());
    }

    @Test
    void incompatibleImportsAreSkipped(@TempDir Path root, @TempDir Path importDir) throws Exception {
        // A ghost recorded for a DIFFERENT track must never race in this one.
        byte[] frames = new byte[com.openggf.net.ghost.GhostFrameCodec.BYTES];
        var wrongTrack = new openggf.timeattack.ghost.GhostRecording(
                new openggf.timeattack.ghost.GhostHeader(1, "s3k", 1, 0, "sonic", "x", 0, 100,
                        new int[0], new byte[32]), frames);
        var rightTrack = new openggf.timeattack.ghost.GhostRecording(
                new openggf.timeattack.ghost.GhostHeader(1, "s3k", 0, 0, "tails", "y", 0, 100,
                        new int[0], new byte[32]), frames);
        Path wrong = importDir.resolve("wrong.ggfghost");
        Path right = importDir.resolve("right.ggfghost");
        openggf.timeattack.ghost.GhostFileCodec.write(wrongTrack, wrong);
        openggf.timeattack.ghost.GhostFileCodec.write(rightTrack, right);

        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic",
                java.util.List.of(wrong, right)));
        runtime.beginAttemptForTest("0.6:cafe");
        assertEquals(1, runtime.opponents().size()); // only the matching-track import raced
    }

    @Test
    void refusesToArmWhenTraceOrTestModeActive(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> true); // guard says blocked
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        assertFalse(runtime.isActive());
    }

    @Test
    void attemptVoidsAtMaxFramesAndNeverSaves(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.beginAttemptForTest("0.6:cafe");
        for (int i = 0; i < openggf.timeattack.ghost.GhostFileCodec.MAX_FRAMES + 10; i++) {
            runtime.tickForTest(0x08, false, false, -1, frame(10));
        }
        runtime.tickForTest(0x08, false, true, -1, frame(10)); // signpost after cap — ignored
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty());
        assertFalse(runtime.hudState().finished());
    }

    @Test
    void deactivateClearsOpponentGhosts(@TempDir Path root) throws Exception {
        // A frozen ghost must not keep rendering after a level-ended deactivate.
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        // Bank a best ghost, then re-arm so it loads as an opponent to race.
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0x08, false, false, -1, frame(10));
        runtime.tickForTest(0x08, false, true, -1, frame(11));
        runtime.beginAttemptForTest("0.6:cafe");
        assertEquals(1, runtime.opponents().size());
        runtime.deactivate();
        assertTrue(runtime.opponents().isEmpty());
    }

    @Test
    void retryVoidsAttemptWithoutSaving(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.beginAttemptForTest("0.6:cafe");
        runtime.tickForTest(0x08, false, false, -1, frame(10));
        runtime.requestRetry();
        assertTrue(runtime.consumeRetryRequested());
        assertFalse(runtime.consumeRetryRequested());
        runtime.tickForTest(0x08, false, true, -1, frame(11)); // finish after void — ignored
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty());
    }

    @Test
    void runSpecIsAnIsolatedActForTheArmedLaunch(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 1, 1, "knuckles", java.util.List.of()));
        var spec = runtime.runSpec();
        assertEquals("s3k", spec.gameId());
        assertEquals(1, spec.zone());
        assertEquals(1, spec.act());
        assertEquals("knuckles", spec.character());
        assertEquals(com.openggf.game.session.GameplayRunPolicy.isolatedAct().withFastRestarts().withoutZoneIntros(), spec.policy());
    }

    @Test
    void retryKeyVoidsTheAttemptAndAsksTheRunToRetry(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        boolean[] retried = new boolean[1];
        runtime.attachHandle(new com.openggf.mods.run.RunHandle() {
            @Override public void retry() { retried[0] = true; }
            @Override public void leave() { }
            @Override public void spectate(boolean active, int dx, int dy) { }
            @Override public boolean isActive() { return true; }
        });
        runtime.setRetryKey(() -> 82);
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, false));
        assertTrue(runtime.isAttemptActive());

        assertTrue(runtime.admitStep(keys(82)));

        assertFalse(runtime.isAttemptActive(), "retry voids the running attempt");
        assertTrue(retried[0], "retry is a run command, applied by the engine at the next boundary");
    }

    @Test
    void retryKeyIsIgnoredWhileTheCompanionRefusesRetries(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        int[] retries = new int[1];
        runtime.attachHandle(handle(retries, new int[1]));
        runtime.setRetryKey(() -> 82);
        boolean[] mayRetry = {false};
        runtime.setFrameCompanion(new TimeAttackRuntime.FrameCompanion() {
            @Override public boolean admit(com.openggf.mods.run.RunInput input) { return true; }
            @Override public void afterStep() { }
            @Override public boolean mayRetry() { return mayRetry[0]; }
        });
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", false, 0, 0, false));

        assertTrue(runtime.admitStep(keys(82)));
        assertTrue(runtime.isAttemptActive(), "a refused retry keeps the attempt");
        assertEquals(0, retries[0]);

        mayRetry[0] = true;
        runtime.admitStep(keys(82));
        assertFalse(runtime.isAttemptActive());
        assertEquals(1, retries[0]);
    }

    @Test
    void onlyACompanionThatContinuesRetriesOnActCompletion(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        assertFalse(runtime.retryOnActCompletion(), "solo runs return to their host");

        TimeAttackRuntime.FrameCompanion stock = new TimeAttackRuntime.FrameCompanion() {
            @Override public boolean admit(com.openggf.mods.run.RunInput input) { return true; }
            @Override public void afterStep() { }
        };
        runtime.setFrameCompanion(stock);
        assertFalse(runtime.retryOnActCompletion());
        assertTrue(stock.mayRetry());

        runtime.setFrameCompanion(new TimeAttackRuntime.FrameCompanion() {
            @Override public boolean admit(com.openggf.mods.run.RunInput input) { return true; }
            @Override public void afterStep() { }
            @Override public boolean continueAfterActCompletion() { return true; }
        });
        assertTrue(runtime.retryOnActCompletion());
        runtime.onRunEnded(com.openggf.mods.run.RunEndReason.ACT_COMPLETED);
        assertFalse(runtime.retryOnActCompletion(), "an ended run drops its companion");
    }

    @Test
    void leaveRunAsksTheAttachedHandleToEndTheRun(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        assertDoesNotThrow(runtime::leaveRun, "no run is hosted yet");
        int[] leaves = new int[1];
        runtime.attachHandle(handle(new int[1], leaves));
        runtime.leaveRun();
        assertEquals(1, leaves[0]);
    }

    private static com.openggf.mods.run.RunHandle handle(int[] retries, int[] leaves) {
        return new com.openggf.mods.run.RunHandle() {
            @Override public void retry() { retries[0]++; }
            @Override public void leave() { leaves[0]++; }
            @Override public void spectate(boolean active, int dx, int dy) { }
            @Override public boolean isActive() { return true; }
        };
    }

    @Test
    void debugAssistedLevelStartTaintsTheAttempt(@TempDir Path root) throws Exception {
        GhostStore store = new GhostStore(root);
        TimeAttackRuntime runtime = new TimeAttackRuntime(store, root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        runtime.onLevelReady(new com.openggf.mods.run.RunLevelStart(runtime.runSpec(), "fp", true, 0, 0, false));
        com.openggf.mods.run.PlayerPose pose = new com.openggf.mods.run.PlayerPose(10, 20, 0, false, false, 2, false);
        runtime.afterStep(new com.openggf.mods.run.RunStep(0, 0x08, false, pose, false, -1, false));
        runtime.afterStep(new com.openggf.mods.run.RunStep(1, 0x08, false, pose, true, -1, false));
        assertTrue(runtime.hudState().finished());
        assertTrue(store.loadBest("s3k", 0, 0, "sonic").isEmpty(), "a tainted finish is never saved");
    }

    @Test
    void endingTheRunDeactivatesTheRuntime(@TempDir Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root),
                root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "sonic", java.util.List.of()));
        assertTrue(runtime.isActive());
        runtime.onRunEnded(com.openggf.mods.run.RunEndReason.ACT_COMPLETED);
        assertFalse(runtime.isActive());
    }

    private static com.openggf.mods.run.RunInput keys(int pressedKey) {
        return new com.openggf.mods.run.RunInput() {
            @Override public com.openggf.control.LogicalInputSnapshot input() { return null; }
            @Override public boolean keyDown(int key) { return key == pressedKey; }
            @Override public boolean keyPressed(int key) { return key == pressedKey; }
        };
    }

    @org.junit.jupiter.api.Test
    void attachedRunRecoloursTheLocalCharacterWithTheSavedLook(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) {
        TimeAttackRuntime runtime = new TimeAttackRuntime(new GhostStore(root), root.resolve("identity"), () -> false);
        runtime.armForLaunch(new TimeAttackLaunchRequest("s3k", 0, 0, "knuckles", java.util.List.of()));
        java.util.List<String> asked = new java.util.ArrayList<>();
        runtime.setLookSource((game, character) -> {
            asked.add(game + ":" + character);
            return com.openggf.mods.run.CharacterLooks.withBaseColour(game, character, 0x0E0);
        });
        java.util.List<com.openggf.net.identity.PlayerAppearance> applied = new java.util.ArrayList<>();
        runtime.attachHandle(new com.openggf.mods.run.RunHandle() {
            @Override public void retry() { }
            @Override public void leave() { }
            @Override public void spectate(boolean active, int dx, int dy) { }
            @Override public boolean isActive() { return true; }
            @Override public void setPlayerAppearance(com.openggf.net.identity.PlayerAppearance look) { applied.add(look); }
        });
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("s3k:knuckles"), asked);
        org.junit.jupiter.api.Assertions.assertEquals(1, applied.size());
        org.junit.jupiter.api.Assertions.assertEquals(0x0E0, applied.getFirst().baseColour());
    }
}
