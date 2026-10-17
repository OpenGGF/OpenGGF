package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.dataselect.AbstractDataSelectProvider;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SavePaths;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title → data select → LOAD_SLOT into Hidden Palace, the real HPZ exit SaveGame, the
 * persisted file, a disposed and recreated session, and title → data select → LOAD_SLOT
 * into the saved Sky Sanctuary act.
 *
 * <p>Native owners (sonic3k.asm): HPZ's Knuckles upper exit {@code loc_45B94} and the
 * Sonic/Tails altar exit {@code loc_45D8A} call {@code SaveGame} (:15824-15926), which stores
 * {@code SaveGame_NextLevel[$1601] = $B}; data select {@code sub_DA4E}/{@code LevelList_DA6E}
 * (:17480-17513) loads code {@code $B} as {@code $A01} for Player_mode 3 and {@code $A00}
 * otherwise. The only declared input is a valid HPZ slot payload in a {@code @TempDir} save
 * root (mirroring the keys {@code S3kSaveSnapshotProvider.capture} writes). Every later step is
 * controller input through the native title, data select and level owners; no gameplay RAM,
 * position, clock or progress is written and the save is never invoked directly.
 *
 * <p>HPZ inputs come from accepted controller movies, replayed from HPZ control release (an
 * authoring start, not a success value): the joined cold Knuckles chain
 * ({@code lrz-knuckles-cold-hpz-ssz2-320}: HPZ load 49297, release 49380, SSZ2 load 50160) and
 * the cold Tails campaign ({@code lrz-tails-cold-hpz-ssz1-dez1-320}: 62424, 62507, SSZ1 69861),
 * stopping when the level owner leaves HPZ.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestS3kSaveRouteRoundtrip {
    @TempDir Path saves;
    private final String previousSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);

    private static final List<Integer> EMERALD_STATES = List.of(1, 1, 1, 0, 0, 0, 0);
    private static final int LIVES = 5;

    private record Route(SelectedTeam team, String movie, int hpzRelease, int sszLoad, int sszAct) { }

    private static Route route(String main) {
        return switch (main) {
            case "knuckles" -> new Route(new SelectedTeam(main, List.of()),
                    "lrz-knuckles-cold-hpz-ssz2-320.bk2", 49380, 50160, 1);
            case "tails" -> new Route(new SelectedTeam(main, List.of()),
                    "lrz-tails-cold-hpz-ssz1-dez1-320.bk2", 62507, 69861, 0);
            // HPZ load 52947; its release is the same 83-input HPZ intro measured on the other two.
            case "sonic+tails" -> new Route(new SelectedTeam("sonic", List.of("tails")),
                    "lrz-sonic-tails-cold-hpz-ssz1-dez1-320.bk2", 52947 + 83, 67037, 0);
            default -> throw new IllegalArgumentException(main);
        };
    }

    @AfterEach
    void reset() {
        SessionSaveRequests.flushPendingSaves();
        if (previousSaveRoot == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
        else System.setProperty(SavePaths.ROOT_PROPERTY, previousSaveRoot);
        clearSession();
    }

    private static void clearSession() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    /**
     * Short independent check: the declared HPZ slot launches through the title and data select
     * with its team, lives and emeralds, the released world replays twice, and leaving without
     * reaching an exit does not advance the saved destination (HPZ again, never SSZ).
     */
    @Order(1)
    @ParameterizedTest(name = "HPZ slot launch and no-exit relaunch {0}")
    @ValueSource(strings = {"knuckles", "tails"})
    void hpzSlotLaunchesThroughTitleAndALeaveWithoutTheExitKeepsHiddenPalace(String main) throws Exception {
        var team = new SelectedTeam(main, List.of());
        writeHpzSlot(team);
        try (var session = launch(team, new DataSelectDestination(Sonic3kZoneIds.ZONE_HPZ, 1))) {
            assertLaunched(session, team, Sonic3kZoneIds.ZONE_HPZ, 1);
            release(session, 600);
            replayReleasedTwice(session, main + " HPZ released");
            for (int frame = 0; frame < 120; frame++) {
                session.step(null);
                assertEquals(Sonic3kZoneIds.ZONE_HPZ, GameServices.level().getCurrentZone());
            }
        }
        assertSlot(team, Sonic3kZoneIds.ZONE_HPZ, 1, "no SaveGame boundary was crossed");
        clearSession();
        try (var session = launch(team, new DataSelectDestination(Sonic3kZoneIds.ZONE_HPZ, 1))) {
            assertLaunched(session, team, Sonic3kZoneIds.ZONE_HPZ, 1);
        }
    }

    /** Knuckles: {@code loc_45B94} SaveGame then {@code StartNewLevel #$A01}; the file resumes at SSZ2. */
    @Order(2)
    @Test
    void knucklesUpperExitSaveRoundTripsThroughTheTitleToSkySanctuaryTwo() throws Exception {
        roundTrip(route("knuckles"));
    }

    /** Tails alone: the altar exit {@code loc_45D8A} SaveGame then {@code $A00}; the file resumes at SSZ1. */
    @Order(3)
    @Test
    void tailsAltarExitSaveRoundTripsThroughTheTitleToSkySanctuaryOne() throws Exception {
        roundTrip(route("tails"));
    }

    /** Sonic and Tails: the same {@code loc_45D8A} SaveGame; the file keeps the sidekick. */
    @Order(4)
    @Test
    void sonicAndTailsAltarExitSaveRoundTripsThroughTheTitleToSkySanctuaryOne() throws Exception {
        roundTrip(route("sonic+tails"));
    }

    private void roundTrip(Route route) throws Exception {
        var team = route.team();
        writeHpzSlot(team);
        var movie = new Bk2MovieLoader().loadMovieOrInputLog(Path.of("src/test/resources/routes/s3k", route.movie()));
        try (var session = launch(team, new DataSelectDestination(Sonic3kZoneIds.ZONE_HPZ, 1))) {
            assertLaunched(session, team, Sonic3kZoneIds.ZONE_HPZ, 1);
            release(session, 600);
            replayReleasedTwice(session, team.mainCharacter() + " HPZ released");
            assertSlot(team, Sonic3kZoneIds.ZONE_HPZ, 1, "EXISTING_SLOT_LOAD keeps HPZ until the exit");
            var hpzObjects = GameServices.level().getObjectManager();
            GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
            long hpzHistory = 0;
            int bound = route.sszLoad() - route.hpzRelease() + 400, n = 0;
            for (; n < bound && GameServices.level().getCurrentZone() == Sonic3kZoneIds.ZONE_HPZ; n++) {
                // The movie's release index is the input whose step released P1; continue after it.
                session.step(movie.getFrame(route.hpzRelease() + 1 + n));
                session.renderFrame();
                assertFalse(session.player().getDead(), () -> team.mainCharacter() + " HPZ death " + state(session));
                assertEquals(LIVES, GameServices.gameState().getLives(), "no hidden HPZ restart");
                var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                if (rewind != null && GameServices.level().getCurrentZone() == Sonic3kZoneIds.ZONE_HPZ)
                    hpzHistory = Math.max(hpzHistory, rewind.currentFrame());
            }
            int exit = n;
            assertEquals(Sonic3kZoneIds.ZONE_SSZ, GameServices.level().getCurrentZone(),
                    () -> team.mainCharacter() + " HPZ exit not reached after " + exit + ": " + state(session));
            assertEquals(route.sszAct(), GameServices.level().getCurrentAct(), "StartNewLevel destination");
            assertNotSame(hpzObjects, GameServices.level().getObjectManager(), "SSZ is a real load");
            assertTrue(hpzHistory > 10, "the outgoing HPZ timeline recorded live history");
            for (int frame = 0; frame < 30 && GameServices.level().hasPendingFreshLevelTransitionBoundary(); frame++) {
                session.step(null);
            }
            var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(rewind == null || rewind.currentFrame() < hpzHistory, "the SSZ load retires HPZ history");
            System.out.println("SAVE-ROUNDTRIP " + team.mainCharacter() + " HPZ exit after " + exit
                    + " inputs from release (movie " + (route.sszLoad() - route.hpzRelease()) + ")");
        }
        assertSlot(team, Sonic3kZoneIds.ZONE_SSZ, route.sszAct(), "SaveGame stores SaveGame_NextLevel");
        // Dispose the session; a new one reads only the persisted file.
        clearSession();
        try (var session = launch(team, new DataSelectDestination(Sonic3kZoneIds.ZONE_SSZ, route.sszAct()))) {
            assertLaunched(session, team, Sonic3kZoneIds.ZONE_SSZ, route.sszAct());
            int frames = release(session, 2400);
            System.out.println("SAVE-ROUNDTRIP " + team.mainCharacter() + " reloaded SSZ" + (route.sszAct() + 1)
                    + " released after " + frames + ": " + state(session));
            assertFalse(session.player().getDead());
            replayReleasedTwice(session, team.mainCharacter() + " SSZ released");
        }
    }

    private void writeHpzSlot(SelectedTeam team) throws Exception {
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var payload = new LinkedHashMap<String, Object>();
        payload.put("zone", Sonic3kZoneIds.ZONE_HPZ);
        payload.put("act", 1);
        payload.put("mainCharacter", team.mainCharacter());
        payload.put("sidekicks", team.sidekicks());
        payload.put("lives", LIVES);
        payload.put("continues", 0);
        payload.put("chaosEmeralds", List.of(0, 1, 2));
        payload.put("superEmeralds", List.of());
        payload.put("emeraldsConverted", false);
        payload.put("emeraldStates", EMERALD_STATES);
        payload.put("clear", false);
        payload.put("progressCode", 11);
        payload.put("clearState", 0);
        var profile = new S3kDataSelectProfile();
        assertTrue(profile.isPayloadValid(payload), "declared HPZ slot is a valid S3K slot");
        assertEquals(new DataSelectDestination(Sonic3kZoneIds.ZONE_HPZ, 1), profile.resolveLoadDestination(payload));
        new SaveManager(saves).writeSlot("s3k", 1, payload);
    }

    private void assertSlot(SelectedTeam team, int zone, int act, String why) throws Exception {
        SessionSaveRequests.flushPendingSaves();
        var slot = new SaveManager(saves).readSlotSummary("s3k", 1);
        assertTrue(slot.isLoadable(), why);
        var payload = slot.payload();
        assertEquals(new DataSelectDestination(zone, act), new S3kDataSelectProfile().resolveLoadDestination(payload),
                why + "; payload=" + payload);
        assertEquals(team.mainCharacter(), payload.get("mainCharacter"));
        assertEquals(team.sidekicks(), payload.get("sidekicks"));
        assertEquals(LIVES, ((Number) payload.get("lives")).intValue(), "Life_count");
        assertEquals(EMERALD_STATES, ((List<?>) payload.get("emeraldStates")).stream()
                .map(value -> ((Number) value).intValue()).toList(), "Emerald states");
    }

    /** Ordinary title Start, data select Right to slot 1 and A, through the production owners. */
    private GameplayCaptureSession launch(SelectedTeam team, DataSelectDestination expected) throws Exception {
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, false, false);
        var session = new GameplayCaptureSession(settings);
        try {
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            assertEquals(GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode());
            try (var input = MutatorGameplayCaptureTool.PresentationInput.install(session.loop())) {
                int presentation = 0;
                for (; presentation < 1800 && session.loop().getCurrentGameMode() != GameMode.DATA_SELECT; presentation++) {
                    session.stepPresentation(presentation % 20 == 10 ? new Bk2FrameInput(0, 0, 0, true, "title Start") : null,
                            input);
                }
                assertEquals(GameMode.DATA_SELECT, session.loop().getCurrentGameMode(), "title Start reaches data select");
                for (int frame = 0; frame < 60; frame++) session.stepPresentation(null, input);
                session.stepPresentation(new Bk2FrameInput(0, AbstractPlayableSprite.INPUT_RIGHT, 0, false, "slot 1"), input);
                session.stepPresentation(null, input);
                var menu = assertInstanceOf(AbstractDataSelectProvider.class, session.loop().getDataSelectProvider());
                var choice = menu.getSessionController().confirmSelection();
                assertEquals(DataSelectActionType.LOAD_SLOT, choice.type());
                assertEquals(1, choice.slot());
                assertEquals(expected.zone(), choice.zone(), "data select reads the persisted zone");
                assertEquals(expected.act(), choice.act(), "data select reads the persisted act");
                assertEquals(team, choice.team());
                session.stepPresentation(new Bk2FrameInput(0, 0, InputActionMasks.ACTION_A, false, "load"), input);
                for (int frame = 0; frame < 600 && session.loop().getCurrentGameMode() != GameMode.LEVEL; frame++) {
                    session.stepPresentation(null, input);
                }
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), "LOAD_SLOT reaches the level");
            }
            return session;
        } catch (Throwable failure) {
            session.close();
            throw failure;
        }
    }

    private static void assertLaunched(GameplayCaptureSession session, SelectedTeam team, int zone, int act) {
        var world = SessionManager.getCurrentWorldSession();
        assertEquals(1, world.getSaveSessionContext().activeSlot().orElseThrow(), "active save session");
        assertEquals(team, world.getSaveSessionContext().selectedTeam());
        assertEquals(zone, GameServices.level().getCurrentZone());
        assertEquals(act, GameServices.level().getCurrentAct());
        assertEquals(team.mainCharacter(), GameServices.sprites().getMainPlayable().getCode());
        assertEquals(team.sidekicks(), GameServices.sprites().getRegisteredSidekicks().stream()
                .map(sidekick -> sidekick.characterKey().persisted()).toList(), "actual restored follower identities");
        assertEquals(LIVES, GameServices.gameState().getLives(), "restored slot lives");
        assertEquals(EMERALD_STATES, GameServices.gameState().getS3kEmeraldStates(), "restored emerald states");
    }

    /** Neutral input until the level owner releases P1 (title card, fade and fresh assembly done). */
    private static int release(GameplayCaptureSession session, int bound) {
        int frames = 0;
        while (!released(session) && frames++ < bound) {
            session.step(null);
            session.renderFrame();
        }
        assertTrue(released(session), () -> "release: " + state(session));
        return frames;
    }

    private static boolean released(GameplayCaptureSession session) {
        var p1 = session.player();
        return session.loop().getCurrentGameMode() == GameMode.LEVEL && !p1.isControlLocked()
                && !p1.isObjectControlled() && !GameServices.level().hasPendingFreshLevelTransitionBoundary()
                && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion();
    }

    /** Full registered world: capture, 45 neutral frames, restore and replay twice, then restore the capture. */
    private static void replayReleasedTwice(GameplayCaptureSession session, String label) {
        var level = GameServices.level();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var world = level.getCurrentLevel();
        int lives = GameServices.gameState().getLives();
        var saved = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(saved, level.hasPendingFreshLevelTransitionBoundary());
        var neutral = new Bk2FrameInput(0, 0, 0, false, "neutral");
        Runnable forward = () -> {
            for (int frame = 0; frame < 45; frame++) {
                session.step(neutral);
                session.renderFrame();
                assertSame(world, level.getCurrentLevel(), label + " stays in one world");
                assertFalse(session.player().getDead(), label);
                assertEquals(lives, GameServices.gameState().getLives(), label + " no hidden restart");
            }
        };
        session.restoreInputHistory(neutral);
        forward.run();
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            same(saved, registry.capture(), label + " restore " + cycle);
            session.restoreInputHistory(neutral);
            forward.run();
            same(expected, registry.capture(), label + " replay " + cycle);
        }
        registry.restore(saved);
        same(saved, registry.capture(), label + " final restore");
        session.restoreInputHistory(neutral);
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " " + key + ": " + differences);
        }
    }

    private static String state(GameplayCaptureSession session) {
        var p1 = session.player();
        var level = GameServices.level();
        return "mode=" + session.loop().getCurrentGameMode() + " zone=" + level.getCurrentZone() + "/"
                + level.getCurrentAct() + " p1=" + p1.getCentreX() + "," + p1.getCentreY()
                + " lock=" + p1.isControlLocked() + " objectControl=" + p1.isObjectControlled()
                + " air=" + p1.getAir() + " rings=" + p1.getRingCount() + " dead=" + p1.getDead()
                + " lives=" + GameServices.gameState().getLives()
                + " vint=" + level.getObjectManager().getVblaCounter();
    }
}
