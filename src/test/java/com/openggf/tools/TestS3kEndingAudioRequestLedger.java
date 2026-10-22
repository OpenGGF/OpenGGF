package com.openggf.tools;

import com.openggf.audio.AudioRequestObserver;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.ending.EndingRescueDirectorObjectInstance;
import com.openggf.game.sonic3k.runtime.EndingZoneRuntimeState;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Request-tier ending audio for the declared no-menu $D01 entry, one Sonic product per
 * {@code _unkFA88} progress class. Rendered PCM is not compared here and remains open.
 *
 * <ul>
 * <li>A1 {@code loc_5DE36}/{@code loc_5DE46} (skdisasm 044fa46 sonic3k.asm:124151-124162): {@code cmd_FadeOut}
 * on the first frame that {@code _unkFA88<0} or {@code Camera_Y_pos_copy>=$78}.</li>
 * <li>A2 {@code Obj_5DFEE} (:124319-124333): {@code mus_CreditsK}; it is allocated only when
 * {@code Super_emerald_count>=7} (loc_5AC04, :121072-121076).</li>
 * <li>A3 {@code Obj_Ending} {@code loc_5E1C2} (:124462-124472): {@code mus_CreditsK}, skipped
 * when {@code _unkFA88==2}.</li>
 * </ul>
 *
 * <p>Expected Level_frame_counter values are native-anchored routine dates: the
 * {@code Events_routine_fg} 8-to-12 transition (Events_fg_4 from loc_5DE46, consumed by
 * loc_5AB96 in the same frame) and the first {@code Queue_Kos_Module} calls of Obj_5DFEE and
 * Obj_Ending, read from the declared native ledgers of the same setup. They are not native
 * audio observations; the native probes do not observe mailbox writes.
 *
 * <p>The request must also reach the driver: {@code zPlaySoundByIndex} routes {@code mus_CreditsK}
 * to {@code zPlayMusicCredits}, which loads the last music-table entry, {@code $32}
 * (Sound/Z80 Sound Driver.asm:1643-1644, 1709-1710). The driver services observed after the
 * request must show that song's sequencer loaded and its track positions advancing.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingAudioRequestLedger {
    private record Request(int levelFrame, AudioRequestObserver.RequestClass type, int id, String owner) { }
    private record MusicTick(int songId, List<Integer> trackPositions) { }
    /** Engine music ids are one-based, so table entry $32 is id $33 (Sonic3kMusic.CREDITS_SK). */
    private static final int CREDITS_SONG = Sonic3kMusic.CREDITS_SK.id;
    private static final int SSZ_SONG = Sonic3kMusic.SSZ.id;
    private static final int TITLE_SONG = Sonic3kMusic.TITLE.id;
    /** Frames of driver progress inspected after the credits request. */
    private static final int CREDITS_WINDOW = 120;

    @ParameterizedTest
    @CsvSource({
            "0000000,-2,3281,-1,3303,3303",
            "1111111,0,3692,-1,3714,3714",
            "3333333,2,3692,3714,-1,4403"})
    void declaredEndingIssuesRomGatedCreditsRequestsOnNativeRoutineFrames(
            String emeralds, int progress, int fadeFrame, int aizReturnCreditsFrame,
            int reunionCreditsFrame, int reunionFirstRunFrame) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, emeralds, false, false, null, null);
        var requests = new ArrayList<Request>();
        var fades = new ArrayList<Integer>();
        int gateFirstTrue = -1;
        int reunionFirstRun = -1;
        int creditsFrame = aizReturnCreditsFrame >= 0 ? aizReturnCreditsFrame : reunionCreditsFrame;
        int lastFrame = Math.max(reunionFirstRunFrame, creditsFrame + CREDITS_WINDOW);
        var pendingTicks = new ConcurrentLinkedQueue<MusicTick>();
        var musicTicks = new ArrayList<List<MusicTick>>();
        try (var session = new GameplayCaptureSession(settings)) {
            session.enableAudio();
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 13, 1, settings);
            var audio = GameServices.audio();
            // GameplayCaptureTool --audio's pull: the driver only services frames that are presented, so every
            // title frame is presented and drained too, in the tool's order (step, outer presentation, drain).
            try (var capture = audio.beginLiveCaptureAudio(60)) {
                short[] samples = new short[capture.maxStereoFramesPerPacket() * 2];
                enterDeclaredScene(session, emeralds, () -> {
                    session.loop().presentOuterFrame(false, false);
                    capture.drainPresentationFrame(samples);
                });
                audio.setRequestObserver((type, id) -> requests.add(new Request(
                        GameServices.level().getFrameCounter(), type, id, owner())));
                audio.setDriverServiceObserver(new SmpsDriverServiceObserver() {
                    @Override public void onServiceEnd(ServiceEvent event, SmpsDriverSnapshot snapshot) {
                        if (event.kind() != ServiceKind.SEQUENCER_TICK || event.sequencer().sfx()) return;
                        SmpsSourceDescriptor source = event.sequencer().source();
                        for (var entry : snapshot.sequencers()) {
                            if (!entry.sfx() && entry.source().equals(source)) {
                                pendingTicks.add(new MusicTick(source.id(), entry.snapshot().tracks().stream()
                                        .map(track -> track.pos()).toList()));
                            }
                        }
                    }
                });
                long fadeCount = audio.musicFadeOutCount();
                boolean rescueAtGate = false;
                for (int outer = 0; outer <= lastFrame; outer++) {
                    var zone = (EndingZoneRuntimeState) GameServices.zoneRuntimeState();
                    // loc_5DE36 reads the copy written by the previous frame's ScreenEvents.
                    boolean gate = zone.ending().progress() < 0
                            || (GameServices.camera().getYCopy() & 0xFFFF) >= 0x78;
                    if (rescueAtGate) {
                        assertEquals(zone.screen().foregroundY & 0xFFFF,
                                GameServices.camera().getYCopy() & 0xFFFF,
                                "the gate's foregroundY is the published Camera_Y_pos_copy");
                        if (gate && gateFirstTrue < 0) gateFirstTrue = outer;
                    }
                    session.step(new Bk2FrameInput(outer, 0, 0, false, 0, 0, false, "neutral"));
                    session.loop().presentOuterFrame(false, false);
                    capture.drainPresentationFrame(samples);
                    session.renderFrame();
                    assertEquals(outer, GameServices.level().getFrameCounter());
                    long now = audio.musicFadeOutCount();
                    for (long i = fadeCount; i < now; i++) fades.add(outer);
                    fadeCount = now;
                    var stepTicks = new ArrayList<MusicTick>();
                    for (MusicTick tick; (tick = pendingTicks.poll()) != null; ) stepTicks.add(tick);
                    musicTicks.add(stepTicks);
                    rescueAtGate = false;
                    for (var director : GameServices.level().getObjectManager()
                            .activeObjectsOfType(EndingRescueDirectorObjectInstance.class)) {
                        if (director.sequence() == EndingRescueDirectorObjectInstance.RESCUE
                                && director.routine() == 32) rescueAtGate = true;
                        if (director.sequence() == EndingRescueDirectorObjectInstance.REUNION
                                && director.routine() != 0 && reunionFirstRun < 0) reunionFirstRun = outer;
                    }
                }
            }
            audio.setRequestObserver(null);
            audio.setDriverServiceObserver(null);
        }
        assertEquals(List.of(fadeFrame), fades, "one cmd_FadeOut, on loc_5DE46's native frame");
        assertEquals(fadeFrame, gateFirstTrue, "the fade lands on the first gate-true frame");
        assertEquals(reunionFirstRunFrame, reunionFirstRun, "Obj_Ending first runs on its native frame");
        List<Integer> aizReturn = credits(requests, "aizReturn");
        List<Integer> reunion = credits(requests, "reunion");
        assertEquals(aizReturnCreditsFrame < 0 ? List.of() : List.of(aizReturnCreditsFrame), aizReturn,
                "Obj_5DFEE mus_CreditsK only after all super emeralds");
        assertEquals(reunionCreditsFrame < 0 ? List.of() : List.of(reunionCreditsFrame), reunion,
                "loc_5E1C2 skips mus_CreditsK when _unkFA88 == 2");
        assertEquals(aizReturn.size() + reunion.size(),
                requests.stream().filter(r -> r.id() == 0xDC).count(), "no other mus_CreditsK owner");
        assertTrue(requests.stream().noneMatch(r -> r.id() == CREDITS_SONG
                        && r.type() == AudioRequestObserver.RequestClass.MUSIC),
                "the request seam keeps the raw mus_CreditsK identity, not the table entry");
        assertTrue(progress != 2 || reunionFirstRun > 0, "absence is observed after Obj_Ending ran");

        // Positive control: the level song issued at the $D01 load is serviced before the fade.
        assertTrue(musicTicks.subList(0, fadeFrame).stream().flatMap(List::stream)
                .anyMatch(tick -> tick.songId() == SSZ_SONG), "LevelMusic_Playlist mus_SSZ ticks before the fade");
        var credits = new ArrayList<List<Integer>>();
        for (int frame = creditsFrame; frame <= creditsFrame + CREDITS_WINDOW; frame++) {
            musicTicks.get(frame).stream().filter(tick -> tick.songId() == CREDITS_SONG)
                    .forEach(tick -> credits.add(tick.trackPositions()));
        }
        assertFalse(credits.isEmpty(), "zPlayMusicCredits loads song $32 after mus_CreditsK");
        assertTrue(musicTicks.get(creditsFrame + CREDITS_WINDOW).stream()
                .anyMatch(tick -> tick.songId() == CREDITS_SONG), "the credits song is still being serviced");
        assertTrue(credits.stream().distinct().count() > 1, "the credits song's track positions advance");
    }

    private static List<Integer> credits(List<Request> requests, String method) {
        return requests.stream().filter(r -> r.id() == 0xDC && r.owner().equals(method))
                .map(Request::levelFrame).toList();
    }

    /** First ending-director frame on the request path, or the first non-audio caller. */
    private static String owner() {
        for (var frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().equals(EndingRescueDirectorObjectInstance.class.getName())) {
                return frame.getMethodName();
            }
        }
        return "";
    }

    /** The declared title-owner ingress used by the native declared products. */
    private static void enterDeclaredScene(GameplayCaptureSession session, String emeralds,
            Runnable presentAudio) throws Exception {
        var title = session.loop().getTitleScreenProvider();
        // Control: the title's audio is consumed on its own presented frames, so the title song is serviced
        // during the title at its own cadence up to the title's end, not carried as a backlog into the scene.
        var titleSongFrames = new ArrayList<Integer>();
        int[] presenting = {0};
        GameServices.audio().setDriverServiceObserver(new SmpsDriverServiceObserver() {
            @Override public void onServiceEnd(ServiceEvent event, SmpsDriverSnapshot snapshot) {
                if (event.kind() == ServiceKind.SEQUENCER_TICK && !event.sequencer().sfx()
                        && event.sequencer().source().id() == TITLE_SONG) titleSongFrames.add(presenting[0]);
            }
        });
        int startup = 0;
        for (; title.getState() != TitleScreenProvider.State.ACTIVE; startup++) {
            assertTrue(startup < 3000, "actual title becomes active");
            session.step(new Bk2FrameInput(startup, 0, 0, false, 0, 0, false, "ordinary startup neutral"));
            presenting[0] = startup;
            presentAudio.run();
            session.renderFrame();
        }
        GameServices.audio().setDriverServiceObserver(null);
        assertTrue(titleSongFrames.size() > 1, "mus_Title is serviced on the title's presented frames");
        int largestGap = 0;
        for (int i = 1; i < titleSongFrames.size(); i++) {
            largestGap = Math.max(largestGap, titleSongFrames.get(i) - titleSongFrames.get(i - 1));
        }
        assertTrue(startup - 1 - titleSongFrames.getLast() <= largestGap,
                "mus_Title is still serviced at its own cadence when the title ends");
        session.loop().getInputHandler().setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()));
        title.update(session.loop().getInputHandler());
        assertEquals(TitleScreenProvider.State.EXITING, title.getState());
        GameServices.gameState().restoreS3kEmeraldProgress(
                emeralds.chars().map(c -> Character.digit(c, 10)).boxed().toList(), false);
        session.loop().setGameMode(GameMode.LEVEL);
        GameServices.level().loadZoneAndActForFreshRuntime(13, 1);
        GameServices.level().getObjectManager().initVblaCounter(0);
        GameServices.rng().setSeed(0); // Declared setup: VInt/RNG zero before the initial dispatch only.
    }
}
