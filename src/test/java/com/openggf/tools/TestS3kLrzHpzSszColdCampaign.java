package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.dataselect.AbstractDataSelectProvider;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.dataselect.DataSelectDestination;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SavePaths;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.save.SessionSaveRequests;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.S3kFullSaveGame;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.dataselect.S3kDataSelectProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cold Lava Reef → Hidden Palace → Sky Sanctuary campaign products beyond the joined
 * arrival chains ({@link TestS3kHpzJoinedColdRouteCapture}).
 *
 * <p>The short, independent checks come first: the ordinary title → data select →
 * LOAD_SLOT inlet into LRZ1, and the native SaveGame next-level semantics at the
 * LRZ/HPZ save sites. Native owners: {@code SaveGame} and {@code SaveGame_NextLevel}
 * (sonic3k.asm:15824-15926), data select {@code sub_DA4E}/{@code LevelList_DA6E}
 * (:17480-17513), {@code Obj_StartNewLevel} (:181375-181394), {@code loc_45B94}
 * (:91514-91526) and {@code loc_45D8A} (:91712-91722).
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzHpzSszColdCampaign {
    @TempDir Path saves;
    private final String previousSaveRoot = System.getProperty(SavePaths.ROOT_PROPERTY);

    @AfterEach
    void reset() {
        SessionSaveRequests.flushPendingSaves();
        if (previousSaveRoot == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
        else System.setProperty(SavePaths.ROOT_PROPERTY, previousSaveRoot);
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    // ------------------------------------------------------------------ ordinary load inlet

    /**
     * Ordinary production entry with a declared memory-card slot (code 9, {@code $900}).
     * The slot payload is the only declared input; title, data select and level are driven
     * by controller presses through the native menu owners.
     *
     * <p>The 600-frame comparison with the declared {@code boot(9,0)} inlet is a measurement
     * and decides which inlet the long chains may use. It is printed, not asserted: menu
     * VBlanks advance {@code V_int_run_count}, which the first level adopts.
     */
    @ParameterizedTest(name = "LOAD_SLOT LRZ1 {0}+{1}")
    @CsvSource({"sonic,tails,lrz-sonic-tails-cold-hpz-ssz1-320.bk2",
            "knuckles,,lrz-knuckles-cold-hpz-ssz2-320.bk2"})
    void ordinaryLoadSlotLaunchesLrzOneWithTheActiveSaveSession(String main, String sidekick, String routeName)
            throws Exception {
        sidekick = sidekick == null ? "" : sidekick;
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var team = new SelectedTeam(main, sidekick.isEmpty() ? List.of() : List.of(sidekick));
        var payload = lrzSlot(team);
        new SaveManager(saves).writeSlot("s3k", 1, payload);
        var profile = new S3kDataSelectProfile();
        assertTrue(profile.isPayloadValid(payload), "declared slot payload is a valid S3K slot");
        assertEquals(new DataSelectDestination(Sonic3kZoneIds.ZONE_LRZ, 0), profile.resolveLoadDestination(payload));

        var movie = new Bk2MovieLoader().loadMovieOrInputLog(route(routeName));
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, false, false);
        List<String> titleRows = new ArrayList<>();
        int titleVint;
        int menuFrames;
        try (var session = new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            assertEquals(GameMode.TITLE_SCREEN, session.loop().getCurrentGameMode());
            int presentation = 0;
            try (var input = MutatorGameplayCaptureTool.PresentationInput.install(session.loop())) {
                // Real title owner: Start, never loop.setGameMode(DATA_SELECT).
                for (; presentation < 1800 && session.loop().getCurrentGameMode() != GameMode.DATA_SELECT; presentation++) {
                    session.stepPresentation(presentation % 20 == 10 ? start() : null, input);
                }
                assertEquals(GameMode.DATA_SELECT, session.loop().getCurrentGameMode(), "title Start reaches data select");
                for (int f = 0; f < 60; f++, presentation++) session.stepPresentation(null, input);
                session.stepPresentation(press(AbstractPlayableSprite.INPUT_RIGHT, 0), input);
                session.stepPresentation(null, input);
                presentation += 2;
                var menu = assertInstanceOf(AbstractDataSelectProvider.class, session.loop().getDataSelectProvider());
                var choice = menu.getSessionController().confirmSelection();
                assertEquals(DataSelectActionType.LOAD_SLOT, choice.type());
                assertEquals(1, choice.slot());
                assertEquals(Sonic3kZoneIds.ZONE_LRZ, choice.zone());
                assertEquals(0, choice.act());
                assertEquals(team, choice.team());
                session.stepPresentation(press(0, InputActionMasks.ACTION_A), input);
                presentation++;
                for (int f = 0; f < 600 && session.loop().getCurrentGameMode() != GameMode.LEVEL; f++, presentation++) {
                    session.stepPresentation(null, input);
                }
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), "LOAD_SLOT reaches the level");
            }
            menuFrames = presentation;
            var world = SessionManager.getCurrentWorldSession();
            assertEquals(1, world.getSaveSessionContext().activeSlot().orElseThrow(), "active save session");
            assertEquals(team, world.getSaveSessionContext().selectedTeam());
            assertEquals(Sonic3kZoneIds.ZONE_LRZ, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            assertEquals(main, GameServices.sprites().getMainPlayable().getCode());
            assertEquals(sidekick.isEmpty() ? 0 : 1, GameServices.sprites().getRegisteredSidekicks().size());
            assertEquals(5, GameServices.gameState().getLives(), "restored slot lives");
            assertEquals(List.of(1, 1, 1, 0, 0, 0, 0), GameServices.gameState().getS3kEmeraldStates(),
                    "restored slot emerald states");
            titleVint = GameServices.level().getObjectManager().getVblaCounter();
            var player = session.player();
            // SpawnLevelMainSprites loc_68A6: the LRZ1 falling state for non-Knuckles only.
            assertEquals(!main.equals("knuckles"), player.getAir(), "LRZ1 bootstrap air state");
            assertEquals(main.equals("knuckles") ? -1 : Sonic3kAnimationIds.HURT_FALL.id(),
                    player.getForcedAnimationId(), "LRZ1 bootstrap animation");
            // Equivalence measurement with the declared boot inlet (decision input, not asserted).
            for (int f = 0; f < 600; f++) {
                session.step(movie.getFrame(f));
                titleRows.add(session.stateLine(f, movie.getFrame(f)));
                assertFalse(session.player().getDead(), "title-inlet LRZ1 death " + f);
            }
            // Two 45-frame whole-world replay cycles after control release.
            assertFalse(session.player().isObjectControlled() || session.player().isControlLocked());
            replayTwice(session, movie, 599, 45, "LOAD_SLOT LRZ1 released");
            SessionSaveRequests.flushPendingSaves();
            var slot = new SaveManager(saves).readSlotSummary("s3k", 1);
            assertTrue(slot.isLoadable(), "EXISTING_SLOT_LOAD keeps the slot loadable");
            assertEquals(Sonic3kZoneIds.ZONE_LRZ, ((Number) slot.payload().get("zone")).intValue());
        }
        List<String> bootRows = new ArrayList<>();
        int bootVint;
        var bootSettings = new GameplayCaptureSession.Settings(320, main, sidekick, "off", null, null, null);
        try (var session = new GameplayCaptureSession(bootSettings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, bootSettings);
            bootVint = GameServices.level().getObjectManager().getVblaCounter();
            for (int f = 0; f < 600; f++) {
                session.step(movie.getFrame(f));
                bootRows.add(session.stateLine(f, movie.getFrame(f)));
            }
        }
        int firstDifference = -1;
        for (int f = 0; f < 600 && firstDifference < 0; f++) {
            if (!titleRows.get(f).equals(bootRows.get(f))) firstDifference = f;
        }
        System.out.println("COLD-INLET " + main + (sidekick.isEmpty() ? "" : "+" + sidekick)
                + " menuPresentations=" + menuFrames + " vint title=" + titleVint + " boot=" + bootVint
                + " firstStateDifference=" + firstDifference
                + (firstDifference >= 0 ? "\n  title " + titleRows.get(firstDifference)
                + "\n  boot  " + bootRows.get(firstDifference) : ""));
    }

    // ------------------------------------------------------------------ SaveGame next level

    /**
     * {@code loc_45B94}: Knuckles' upper HPZ exit calls SaveGame before
     * {@code StartNewLevel #$A01}. SaveGame stores {@code SaveGame_NextLevel[$1601] = $B};
     * data select {@code sub_DA4E} loads code {@code $B} as {@code $A01} for Player_mode 3.
     * Drives the production object (no direct SaveGame call).
     */
    @Test
    void knucklesHpzExitSaveResumesTheNativeNextLevel() throws Exception {
        var fixture = slotFixture(new SelectedTeam("knuckles", List.of()),
                Sonic3kZoneIds.ZONE_HPZ, 1, 0xAF0, 0x400);
        var manager = GameServices.level().getObjectManager();
        var loop = new GameLoop(new InputHandler());
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        try {
            // Same positioned controller flow as TestS3kHpzLifecycleProduction#checkKnucklesExit.
            for (int i = 0; i < 60; i++) fixture.stepFrame(false, false, false, false, false);
            for (int i = 0; i < 18; i++) fixture.stepFrame(false, false, false, true, true);
            for (int i = 0; i < 6; i++) fixture.stepFrame(false, false, false, true, false);
            int lives = GameServices.gameState().getLives();
            boolean loaded = false;
            for (int i = 0; i < 900 && !loaded; i++) {
                if (GameServices.level().getObjectManager() == manager
                        && GameServices.level().getCurrentZone() == Sonic3kZoneIds.ZONE_HPZ) {
                    if (i > 400) {
                        fixture.gameplayMode().getFadeManager().update();
                        loop.step();
                    } else {
                        fixture.stepFrame(false, false, false, false, false);
                    }
                } else {
                    loaded = true;
                }
            }
            assertTrue(loaded, "loc_45B94 must start a new level");
            assertEquals(Sonic3kZoneIds.ZONE_SSZ, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct(), "StartNewLevel #$A01");
            assertSavedNextLevel(new DataSelectDestination(Sonic3kZoneIds.ZONE_SSZ, 1), "knuckles", lives);
        } finally {
            loop.closePresence();
        }
    }

    /**
     * {@code loc_45D8A}: the Sonic/Tails HPZ exit SaveGame at {@code $1601}; code {@code $B}
     * loads {@code LevelList_DA6E[$B] = $A00} for every non-Knuckles Player_mode. The
     * altar route itself is covered by the joined pair chain; this is the SaveGame call at
     * the same zone state ({@code HpzTeleporterRouteHelperObjectInstance} stage timeout).
     */
    @ParameterizedTest(name = "HPZ loc_45D8A SaveGame {0}+{1}")
    @CsvSource({"sonic,tails", "sonic,", "tails,"})
    void sonicAndTailsHpzExitSaveResumesSkySanctuaryActOne(String main, String sidekick) throws Exception {
        sidekick = sidekick == null ? "" : sidekick;
        slotFixture(new SelectedTeam(main, sidekick.isEmpty() ? List.of() : List.of(sidekick)),
                Sonic3kZoneIds.ZONE_HPZ, 1, null, null);
        int lives = GameServices.gameState().getLives();
        S3kFullSaveGame.complete(GameServices.level().getObjectManager().getObjectServices());
        assertSavedNextLevel(new DataSelectDestination(Sonic3kZoneIds.ZONE_SSZ, 0), main, lives);
    }

    /**
     * {@code Obj_StartNewLevel} (SKL $B3) in LRZ2 at ({@code $3FE0,$E0}) subtype {@code $2D}:
     * Knuckles alone saves ({@code Player_mode 3}, zone 9) before {@code StartNewLevel $1601}.
     * {@code SaveGame_NextLevel[$901] = $A} loads {@code LevelList_DA6E[$A] = $1601}.
     */
    @Test
    void knucklesLrzStartNewLevelSaveResumesHiddenPalace() throws Exception {
        var fixture = slotFixture(new SelectedTeam("knuckles", List.of()),
                Sonic3kZoneIds.ZONE_LRZ, 1, 0x3FE0 - 0x60, 0xE0);
        int lives = GameServices.gameState().getLives();
        boolean requested = false;
        for (int i = 0; i < 240 && !requested; i++) {
            fixture.stepFrame(false, false, false, true, false);
            requested = GameServices.level().getCurrentZone() != Sonic3kZoneIds.ZONE_LRZ
                    || GameServices.level().getTransitions().hasPendingLevelExit();
        }
        assertTrue(requested, "Obj_StartNewLevel's Check_InMyRange must request $1601");
        assertSavedNextLevel(new DataSelectDestination(Sonic3kZoneIds.ZONE_HPZ, 1), "knuckles", lives);
    }

    // ------------------------------------------------------------------ long cold continuations

    /**
     * K: one uninterrupted controller-only Knuckles run from the declared {@code boot(9,0)}
     * inlet through LRZ2's {@code Obj_StartNewLevel}, HPZ's {@code loc_45B94}, the SSZ2 crane,
     * both Mecha fights, {@code loc_7BCB0}'s final defeat, the flight, both credit parts and the
     * post-credits owner to the title. Cold Knuckles has earned no emeralds (ending progress -2).
     * Inputs through the crane release are the qualified joined chain; the fight was authored
     * from that cold prefix with {@link SszBossInputAuthorTool} (MECHA2), and the tail is
     * neutral then Start once the post-credits owner reaches phase 4 after both credit parts.
     */
    @Test
    void knucklesColdCampaignClearsSkySanctuaryTwoThroughTheEndingAndTitle() throws Exception {
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(route("lrz-knuckles-cold-hpz-ssz2-ending-320.bk2"));
        var joined = loader.loadMovieOrInputLog(route("lrz-knuckles-cold-hpz-ssz2-320.bk2"));
        final int craneRelease = 51586; // TestS3kHpzJoinedColdRouteCapture's qualified release
        for (int f = 0; f <= craneRelease; f++) {
            assertEquals(joined.getFrame(f), movie.getFrame(f), "qualified joined input " + f);
        }
        var settings = new GameplayCaptureSession.Settings(320, "knuckles", "", "off", null, null, null);
        var checked = new java.util.LinkedHashSet<String>();
        int hpzLoad = -1, sszLoad = -1, released = -1, finalDefeat = -1, flight = -1, title = -1;
        int health = 8, phaseHits = 0, phase = 1;
        boolean creditsComplete = false, postSeen = false;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            int lives = GameServices.gameState().getLives();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                // Draw every input, as the joined chain and the fight author did.
                session.step(movie.getFrame(frame)); session.renderFrame();
                if (session.loop().getCurrentGameMode() == GameMode.TITLE_SCREEN) {
                    title = frame;
                    break;
                }
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), "input " + frame);
                assertFalse(session.player().getDead(), "cold death " + frame);
                assertTrue(GameServices.gameState().getLives() >= lives, "hidden respawn " + frame);
                lives = GameServices.gameState().getLives();
                assertInstanceOf(com.openggf.sprites.playable.Knuckles.class, session.player());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
                assertEquals(320, GameServices.camera().getWidth());
                var level = GameServices.level();
                int zone = level.getCurrentZone(), act = level.getCurrentAct();
                if (zone == 22 && act == 1 && hpzLoad < 0) hpzLoad = frame;
                if (zone == 10 && sszLoad < 0) {
                    sszLoad = frame;
                    assertEquals(1, act, "loc_45B94 StartNewLevel #$A01");
                }
                if (zone != 10) continue;
                var ssz = (com.openggf.game.sonic3k.runtime.SszZoneRuntimeState) GameServices.zoneRuntimeState();
                if (released < 0 && ssz.cutsceneFlag(5) && !session.player().isObjectControlled()
                        && !session.player().isControlLocked()) released = frame;
                String spot = null;
                var bosses = level.getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance.class);
                if (finalDefeat < 0 && !bosses.isEmpty()) {
                    var mecha = bosses.getFirst();
                    int hp = mecha.getCollisionProperty();
                    if (hp < health) {
                        assertEquals(health - 1, hp, "each collision deals one hit at " + frame);
                        phaseHits++;
                        if (phaseHits == 1) spot = "phase" + phase + "-first-hit";
                        if (hp == 2) spot = "phase" + phase + "-low-health";
                        if (hp == 0) spot = phase == 1 ? "first-defeat" : null;
                    } else if (hp > health) {
                        assertEquals(1, phase, "only loc_7BBE0 restores Mecha's health");
                        assertEquals(8, phaseHits, "first fight takes eight hits");
                        assertEquals(8, hp, "loc_7BBE0 returns with eight Super-phase hits");
                        phase = 2;
                        phaseHits = 0;
                    }
                    if (phase == 2 && checked.add("super-entry")) {
                        assertEquals(2, mecha.routineForTest(), "Super phase routine");
                        spot = "super-entry";
                    }
                    health = hp;
                }
                if (finalDefeat < 0 && ssz.act2EndingActive()) {
                    finalDefeat = frame;
                    assertEquals(2, phase, "loc_7BCB0 follows the Super phase");
                    assertEquals(8, phaseHits, "Super phase takes eight hits");
                    spot = "final-defeat";
                }
                if (ssz.ending().initialized()) {
                    creditsComplete |= ssz.ending().credits().complete();
                    assertEquals(-2, ssz.ending().progress(), "cold Knuckles earned no emeralds");
                }
                var flightOwner = level.getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.ending.EndingKnucklesFlightObjectInstance.class).stream()
                        .findFirst().orElse(null);
                if (flightOwner != null) {
                    if (flight < 0) flight = frame;
                    if (flightOwner.routine() == 4) spot = "flight-jump";
                    if (flightOwner.routine() == 10) spot = "plane-turn";
                    if (flightOwner.routine() == 12) spot = "small-plane";
                }
                var credits = ssz.ending().credits();
                if (credits.routine() == 4 && credits.rowIndex() > 0) {
                    spot = credits.partIndex() == 0 ? "credits-one" : "credits-two";
                }
                var post = level.getObjectManager().activeObjectsOfType(
                        com.openggf.game.sonic3k.ending.S3kPostCreditsObjectInstance.class).stream()
                        .findFirst().orElse(null);
                if (post != null) {
                    postSeen = true;
                    if (post.phase() == 1) spot = "banner";
                    if (post.phase() == 7 && !level.getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.ending.EndingMechaChargeObjectInstance.class).isEmpty()) {
                        spot = "mecha-charge";
                    }
                }
                if (spot != null && (spot.equals("super-entry") || checked.add(spot))) {
                    replayWindow(session, movie, frame, finalDefeat < 0 ? 45 : 30, spot);
                }
            }
        }
        System.out.println("K cold campaign: HPZ " + hpzLoad + ", SSZ2 " + sszLoad + ", release " + released
                + ", final defeat " + finalDefeat + ", flight " + flight + ", title " + title + ", spots " + checked);
        assertEquals(49297, hpzLoad);
        assertEquals(50160, sszLoad);
        assertEquals(craneRelease, released);
        assertTrue(finalDefeat > released && flight > finalDefeat, "fight, then the actual flight");
        assertTrue(creditsComplete, "both native credit parts");
        assertTrue(postSeen, "actual post-credits owner");
        assertTrue(title > flight, "controller Start reaches the title");
        assertEquals(java.util.Set.of("phase1-first-hit", "phase1-low-health", "first-defeat", "super-entry",
                "phase2-first-hit", "phase2-low-health", "final-defeat", "flight-jump", "plane-turn",
                "small-plane", "credits-one", "credits-two", "banner", "mecha-charge"), checked);
    }

    /** Two-cycle whole-world replay of {@code window} inputs inside the current world. */
    private static void replayWindow(GameplayCaptureSession session, Bk2Movie movie, int frame, int window,
                                     String label) {
        var level = GameServices.level();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var world = level.getCurrentLevel();
        assertTrue(frame + window < movie.getFrameCount(), label);
        var saved = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(saved, level.hasPendingFreshLevelTransitionBoundary());
        for (int n = 1; n <= window; n++) {
            session.step(movie.getFrame(frame + n)); session.renderFrame();
            assertSame(world, level.getCurrentLevel(), label);
        }
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            same(saved, registry.capture(), label + " restore " + cycle);
            session.restoreInputHistory(movie.getFrame(frame));
            for (int n = 1; n <= window; n++) {
                session.step(movie.getFrame(frame + n)); session.renderFrame();
                assertSame(world, level.getCurrentLevel(), label);
                assertFalse(session.player().getDead(), label + " replay death " + (frame + n));
            }
            same(expected, registry.capture(), label + " replay " + cycle);
        }
        registry.restore(saved);
        session.restoreInputHistory(movie.getFrame(frame));
    }

    // ------------------------------------------------------------------ helpers

    private HeadlessTestFixture slotFixture(SelectedTeam team, int zone, int act, Integer x, Integer y) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, team.mainCharacter());
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                team.sidekicks().isEmpty() ? "" : team.sidekicks().getFirst());
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
        config.resolveDisplayAspect();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        SessionSaveRequests.flushPendingSaves();
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var module = new Sonic3kGameModule();
        GameModuleRegistry.setCurrent(module);
        // Active slot context as a LOAD_SLOT launch opens it (TestS3kSszAct2FinalFight pattern).
        SessionManager.openGameplaySession(module, SaveSessionContext.forSlot("s3k", 1, team, zone, act));
        TestEnvironment.activeGameplayMode();
        var builder = HeadlessTestFixture.builder().withZoneAndAct(zone, act).withFreshLevelStartLifecycle();
        if (x != null) builder.startPosition((short) (int) x, (short) (int) y).startPositionIsCentre();
        var fixture = builder.build();
        assertEquals(1, SessionManager.getCurrentWorldSession().getSaveSessionContext().activeSlot().orElseThrow(),
                "fixture boot keeps the opened slot session");
        return fixture;
    }

    private void assertSavedNextLevel(DataSelectDestination expected, String main, int lives) throws Exception {
        SessionSaveRequests.flushPendingSaves();
        var slot = new SaveManager(saves).readSlotSummary("s3k", 1);
        assertTrue(slot.isLoadable(), "SaveGame wrote a loadable slot");
        var payload = slot.payload();
        var destination = new S3kDataSelectProfile().resolveLoadDestination(payload);
        assertEquals(expected, destination, "SaveGame stores SaveGame_NextLevel; payload=" + payload);
        assertEquals(main, payload.get("mainCharacter"));
        assertEquals(lives, ((Number) payload.get("lives")).intValue(), "SaveGame writes Life_count");
        assertEquals(0, GameServices.gameState().capture().collectedSpecialRings(),
                "loc_C4CC clears Collected_special_ring_array");
    }

    private static Map<String, Object> lrzSlot(SelectedTeam team) {
        // Declared memory-card inlet mirroring S3kSaveSnapshotProvider.capture keys.
        var payload = new LinkedHashMap<String, Object>();
        payload.put("zone", Sonic3kZoneIds.ZONE_LRZ);
        payload.put("act", 0);
        payload.put("mainCharacter", team.mainCharacter());
        payload.put("sidekicks", team.sidekicks());
        payload.put("lives", 5);
        payload.put("continues", 0);
        payload.put("chaosEmeralds", List.of(0, 1, 2));
        payload.put("superEmeralds", List.of());
        payload.put("emeraldsConverted", false);
        payload.put("emeraldStates", List.of(1, 1, 1, 0, 0, 0, 0));
        payload.put("clear", false);
        payload.put("progressCode", 10);
        payload.put("clearState", 0);
        return payload;
    }

    private static void replayTwice(GameplayCaptureSession session, Bk2Movie movie, int frame, int window,
                                    String label) {
        var level = GameServices.level();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var world = level.getCurrentLevel();
        var saved = registry.capture();
        GameplayInputBranchTool.requireRestorableCheckpoint(saved, level.hasPendingFreshLevelTransitionBoundary());
        for (int n = 1; n <= window; n++) {
            session.step(movie.getFrame(frame + n));
            assertSame(world, level.getCurrentLevel());
        }
        var expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(saved);
            same(saved, registry.capture(), label + " restore " + cycle);
            session.restoreInputHistory(movie.getFrame(frame));
            for (int n = 1; n <= window; n++) {
                session.step(movie.getFrame(frame + n));
                assertSame(world, level.getCurrentLevel());
                assertFalse(session.player().getDead(), label + " replay death " + (frame + n));
            }
            same(expected, registry.capture(), label + " replay " + cycle);
        }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), where + " " + key + ": " + differences);
        }
    }

    private static Bk2FrameInput start() {
        return new Bk2FrameInput(0, 0, 0, true, "cold campaign title Start");
    }

    private static Bk2FrameInput press(int direction, int action) {
        return new Bk2FrameInput(0, direction, action, false, "cold campaign data select");
    }

    private static Path route(String name) { return Path.of("src/test/resources/routes/s3k", name); }
}
