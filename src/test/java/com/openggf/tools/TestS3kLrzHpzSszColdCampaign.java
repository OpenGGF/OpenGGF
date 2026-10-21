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
 * (sonic3k.asm:15860-15962), data select {@code sub_DA4E}/{@code LevelList_DA6E}
 * (:17516-17549), {@code Obj_StartNewLevel} (:181466-181485), {@code loc_45B94}
 * (:91560-91572) and {@code loc_45D8A} (:91758-91768).
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

    /**
     * P: one uninterrupted controller-only Sonic and Tails run from the declared {@code boot(9,0)}
     * inlet through LRZ3, HPZ's {@code loc_45D8A} ({@code $A00}), Sky Sanctuary act 1's GHZ and MTZ
     * replicas and Mecha Sonic, the act-1 results SaveGame and the launch into DEZ1 ({@code $B00}).
     * Inputs through the SSZ1 control release are the qualified joined chain. The continuation
     * was authored from that cold prefix: the direct SSZ1 movie followed with time-shift resyncs,
     * a progress search along the direct SSZ1 path where the RNG-phased clouds and columns
     * differ ({@link ReferencePathInputAuthor}), {@link SszBossInputAuthorTool} for the three
     * encounters, then neutral input through the defeat, results, launch and DEZ intro.
     */
    @Test
    void pairColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory() throws Exception {
        verifyDeathEggCampaign(new DeathEggCampaign("sonic", "tails", "lrz-sonic-tails-cold-hpz-ssz1-dez1-320.bk2",
                "lrz-sonic-tails-cold-hpz-ssz1-320.bk2", 67211, 52947, 67037, 71823, 76536, 82834, 85143));
    }

    /**
     * T: the same campaign for Tails alone: the qualified cold Tails LRZ chain into HPZ, then
     * HPZ's Knuckles fight, theft, collapse and altar (the single-player helper timers) to
     * {@code loc_45D8A}'s {@code $A00}, Tails' SSZ1 arrival and act 1 through DEZ1's release.
     * HPZ was authored along the joined pair chain's HPZ path ({@link ReferencePathInputAuthor})
     * and with the HPZ fight policy; SSZ1 waits at the Knuckles-button bridge until
     * {@code Obj_SSZCutsceneBridge} has retracted and opened the act bounds, then follows the
     * direct SSZ1 path, with {@link SszBossInputAuthorTool} for the three encounters.
     */
    @Test
    void tailsColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory() throws Exception {
        verifyDeathEggCampaign(new DeathEggCampaign("tails", "", "lrz-tails-cold-hpz-ssz1-dez1-320.bk2",
                "lrz-tails-cold-hpz-320.bk2", 62545, 62424, 69861, 72972, 76304, 83380, 85689));
    }

    /**
     * S: the same campaign for Sonic alone, which no qualified prefix covers, so the whole movie is
     * the authored route (cold3's LRZ1/LRZ2 chain along the pair's P1 path to input 23900, then
     * round 2). Before LRZ3's arena Sonic breaks the fire-shield monitor ({@code Obj_Monitor}
     * subtype 5 at ({@code $AE8,$481})) that the pair also takes: {@code Obj_59FC4}'s
     * {@code loc_5A0AC} spares Player 1 with {@code Status_FireShield} from {@code sub_24280}'s lava
     * hurt, and the pair's path touches the lava. {@code Obj_LRZEndBoss} is player-independent
     * clockwork (native and engine both take the 14 mine hits at the same offsets from
     * {@code loc_797FA}), so the fight replays the pair's P1 pads aligned on the boss's activation
     * with per-input position correction, and the walk to {@code Obj_StartNewLevel} aligned on
     * {@code loc_799E0}'s {@code Restore_PlayerControl} (1017 inputs after the defeat here, 967 for
     * the pair). HPZ follows the pair's HPZ path and the HPZ fight policy; SSZ1 follows the direct
     * solo Sonic SSZ1 movie to the Knuckles-button bridge, waits for the act bounds, then searches
     * along that movie's path, with {@link SszBossInputAuthorTool} for the three encounters.
     */
    @Test
    void sonicColdCampaignLeavesSkySanctuaryForDeathEggWithIsolatedHistory() throws Exception {
        verifyDeathEggCampaign(new DeathEggCampaign("sonic", "", "lrz-sonic-cold-hpz-ssz1-dez1-320.bk2",
                null, -1, 30123, 39248, 43233, 45735, 49728, 52067));
    }

    /**
     * Inputs through {@code prefixEnd} equal the qualified prefix movie (none for solo Sonic); the
     * boundary inputs are measured on the authored movie and an uninterrupted replay must reproduce
     * each exactly.
     */
    private record DeathEggCampaign(String main, String sidekick, String movie, String prefixMovie, int prefixEnd,
                                    int hpzLoad, int sszLoad, int ghzDefeat, int mtzDefeat, int mechaDefeat,
                                    int dezLoad) { }

    /**
     * {@code Obj_LevelResults} saves at {@code loc_2DCA0} when the act is non-zero or the zone is
     * {@code $A} (sonic3k.asm:62708-62719); Sky Sanctuary act 1 takes the zone branch. Each real
     * load retires the outgoing live history: HPZ's tail is armed 120 inputs before SSZ1 (the
     * altar beam's long fade), SSZ's 36 before DEZ (14 live inputs and the 22-input fade-out).
     * SSZ1 assembles P1 at X {@code $100}, Camera Y + {@code $65} (the arrival owner); DEZ1 at
     * {@code loc_1BE7A}'s ({@code $30,$9AC}) start with Sonic_Init's feet (radius {@code $13}),
     * under {@code loc_44A26}'s control lock and the intro run.
     */
    private void verifyDeathEggCampaign(DeathEggCampaign c) throws Exception {
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(route(c.movie()));
        if (c.prefixMovie() != null) {
            var prefix = loader.loadMovieOrInputLog(route(c.prefixMovie()));
            for (int f = 0; f <= c.prefixEnd(); f++) {
                assertEquals(prefix.getFrame(f), movie.getFrame(f), "qualified prefix input " + f);
            }
        }
        boolean pair = !c.sidekick().isEmpty();
        var settings = new GameplayCaptureSession.Settings(320, c.main(), c.sidekick(), "off", null, null, null);
        var resultsState = com.openggf.level.objects.AbstractResultsScreen.class.getDeclaredField("state");
        resultsState.setAccessible(true);
        var checked = new java.util.LinkedHashSet<String>();
        int hpzLoad = -1, sszLoad = -1, sszAssembly = -1, ghzDefeat = -1, mtzDefeat = -1, mechaDefeat = -1;
        int resultsSave = -1, dezLoad = -1, dezAssembly = -1, dezReleased = -1;
        long hpzHistory = 0, sszHistory = 0;
        boolean sszIsolated = false, dezIsolated = false;
        Object sszManager = null, sszRuntime = null;
        var live = SonicConfiguration.LIVE_REWIND_ENABLED;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            int lives = GameServices.gameState().getLives();
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                if (frame == c.sszLoad() - 120 || frame == c.dezLoad() - 36) {
                    GameServices.configuration().setSessionOverride(live, true);
                }
                // Draw every input, as the chains and the continuation author did.
                session.step(movie.getFrame(frame)); session.renderFrame();
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), "input " + frame);
                assertFalse(session.player().getDead(), "cold death " + frame);
                assertTrue(GameServices.gameState().getLives() >= lives, "hidden respawn " + frame);
                lives = GameServices.gameState().getLives();
                assertEquals(c.main().equals("sonic") ? com.openggf.sprites.playable.Sonic.class
                        : com.openggf.sprites.playable.Tails.class, session.player().getClass());
                assertSame(GameServices.sprites().getMainPlayable(), session.player());
                var followers = GameServices.sprites().getRegisteredSidekicks();
                assertEquals(pair ? 1 : 0, followers.size(), "registered sidekicks at " + frame);
                if (pair) assertInstanceOf(com.openggf.sprites.playable.Tails.class, followers.getFirst());
                assertEquals(320, GameServices.camera().getWidth());
                var level = GameServices.level();
                var manager = level.getObjectManager();
                var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                boolean assembled = !level.hasPendingFreshLevelTransitionBoundary()
                        && !level.getTransitions().isTitleCardRequested();
                int zone = level.getCurrentZone(), act = level.getCurrentAct();
                if (zone == 22 && act == 1) {
                    if (hpzLoad < 0) hpzLoad = frame;
                    if (frame >= c.sszLoad() - 120 && rewind != null) hpzHistory = Math.max(hpzHistory, rewind.currentFrame());
                }
                String spot = null;
                if (zone == 10) {
                    assertEquals(0, act, "loc_45D8A StartNewLevel #$A00");
                    if (sszLoad < 0) {
                        sszLoad = frame;
                        assertTrue(hpzHistory > 10, "nonempty outgoing HPZ history");
                    }
                    if (sszAssembly < 0 && assembled) {
                        sszAssembly = frame;
                        assertEquals(0x100, session.player().getCentreX());
                        assertEquals((GameServices.camera().getY() & 0xFFFF) + 0x65, session.player().getCentreY());
                        assertEquals(0, session.player().getRingCount(), "fresh SSZ1 rings");
                    }
                    if (sszAssembly >= 0 && !sszIsolated && rewind != null) {
                        assertTrue(rewind.currentFrame() < hpzHistory, "SSZ1 retires the outgoing HPZ timeline");
                        sszIsolated = true;
                        GameServices.configuration().setSessionOverride(live, false);
                    }
                    sszManager = manager;
                    sszRuntime = GameServices.zoneRuntimeState();
                    if (frame >= c.dezLoad() - 36 && rewind != null) sszHistory = Math.max(sszHistory, rewind.currentFrame());
                    if (ghzDefeat < 0 && manager.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.bosses.SszGhzBossObjectInstance.class).stream()
                            .anyMatch(boss -> boss.hitsRemainingForTest() == 0)) {
                        ghzDefeat = frame; spot = "ghz-defeat";
                    }
                    if (mtzDefeat < 0 && manager.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.bosses.SszMtzBossObjectInstance.class).stream()
                            .anyMatch(boss -> boss.hitsRemainingForTest() == 0)) {
                        mtzDefeat = frame; spot = "mtz-defeat";
                    }
                    if (mechaDefeat < 0 && manager.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance.class).stream()
                            .anyMatch(boss -> boss.getCollisionProperty() == 0)) {
                        mechaDefeat = frame; spot = "mecha-defeat";
                    }
                    for (var results : manager.activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance.class)) {
                        // STATE_WAIT follows the tally dispatch that called S3kFullSaveGame.complete.
                        if (resultsSave < 0 && resultsState.getInt(results) == 3) {
                            resultsSave = frame; spot = "results-save";
                            assertTrue(mechaDefeat >= 0, "results follow the Mecha defeat");
                            assertEquals(0, GameServices.gameState().capture().collectedSpecialRings(),
                                    "loc_C4CC clears Collected_special_ring_array");
                        }
                    }
                }
                if (zone == 11) {
                    assertEquals(0, act, "SSZ1 launches into DEZ1 ($B00)");
                    if (dezLoad < 0) {
                        dezLoad = frame;
                        assertTrue(sszHistory > 10, "nonempty outgoing SSZ history");
                    }
                    if (dezAssembly < 0 && assembled) {
                        dezAssembly = frame;
                        assertNotSame(sszManager, manager, "DEZ is a real load");
                        assertNotSame(sszRuntime, GameServices.zoneRuntimeState());
                        assertSame(session.player(), GameServices.camera().getFocusedSprite());
                        assertFalse(session.player().isObjectControlled());
                        assertTrue(session.player().isControlLocked(), "loc_44A26 owns the DEZ entrance");
                        assertEquals(0, session.player().getRingCount(), "fresh DEZ rings");
                        assertEquals(0x30, session.player().getCentreX());
                        assertEquals(0x9AC + 0x13, session.player().getCentreY() + session.player().getYRadius());
                        assertFalse(manager.activeObjectsOfType(
                                com.openggf.game.sonic3k.objects.LevelIntroPlayerRunInstance.class).isEmpty(),
                                "the DEZ intro run owns P1");
                    }
                    if (dezAssembly >= 0 && !dezIsolated && rewind != null) {
                        assertTrue(rewind.currentFrame() < sszHistory, "DEZ retires the outgoing SSZ timeline");
                        dezIsolated = true;
                        GameServices.configuration().setSessionOverride(live, false);
                    }
                    if (dezAssembly >= 0 && dezReleased < 0 && !session.player().isControlLocked()
                            && manager.activeObjectsOfType(
                                    com.openggf.game.sonic3k.objects.LevelIntroPlayerRunInstance.class).isEmpty()) {
                        dezReleased = frame; spot = "dez-released";
                    }
                }
                if (spot != null && checked.add(spot)) replayWindow(session, movie, frame, 45, spot);
            }
        }
        System.out.println(c.main() + (pair ? "+" + c.sidekick() : "") + " cold campaign: HPZ " + hpzLoad + ", SSZ1 "
                + sszLoad + "/" + sszAssembly + ", GHZ " + ghzDefeat + ", MTZ " + mtzDefeat + ", Mecha " + mechaDefeat
                + ", results save " + resultsSave + ", DEZ " + dezLoad + "/" + dezAssembly + ", released "
                + dezReleased + ", spots " + checked);
        assertEquals(c.hpzLoad(), hpzLoad);
        assertEquals(c.sszLoad(), sszLoad);
        assertEquals(sszLoad + 1, sszAssembly);
        assertTrue(sszIsolated, "SSZ1 retires the HPZ timeline");
        assertEquals(c.ghzDefeat(), ghzDefeat);
        assertEquals(c.mtzDefeat(), mtzDefeat);
        assertEquals(c.mechaDefeat(), mechaDefeat);
        assertTrue(resultsSave > mechaDefeat && dezLoad > resultsSave, "results save, then the launch");
        assertEquals(c.dezLoad(), dezLoad);
        assertEquals(dezLoad + 1, dezAssembly);
        assertTrue(dezIsolated, "DEZ retires the SSZ timeline");
        assertTrue(dezReleased > dezAssembly, "the intro run releases control");
        assertEquals(java.util.Set.of("ghz-defeat", "mtz-defeat", "mecha-defeat", "results-save", "dez-released"),
                checked);
    }

    /**
     * G: the LRZ2 giant ring s9 at ($2640,$640), entered by cold Tails with the 12-input L+A hold
     * recorded in s3k-lrz-act2.md, then the return to LRZ2 at Saved2. No emeralds are declared:
     * {@code SSEntryFlash_GoSS} starts Blue Sphere, which neutral input fails. The seven-Chaos
     * $1701 branch is {@link #tailsSevenChaosGiantRingFailsAPedestalStageAndReturnsToLrzTwo}: the
     * sanctuary's exit band stays closed, but a failed pedestal stage still returns to LRZ2.
     */
    @ParameterizedTest(name = "giant ring s9 emeralds {0}")
    @CsvSource({"0000000,lrz-tails-cold-giant-ring-s9-320"})
    void tailsGiantRingGatewayReturnsToLrzTwo(String emeralds, String name) throws Exception {
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(route(name + ".bk2"));
        var source = loader.loadMovieOrInputLog(route("lrz-tails-cold-hpz-320.bk2"));
        for (int f = 0; f < 49073; f++) assertEquals(source.getFrame(f), movie.getFrame(f), "qualified Tails input " + f);
        boolean sanctuary = emeralds.equals("3333333");
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null, emeralds,
                false, false);
        int entry = -1, results = -1, returned = -1, ringsBefore = -1;
        Object outgoingObjects = null;
        com.openggf.level.BigRingReturnState saved = null;
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                var level = GameServices.level();
                if (entry < 0) {
                    ringsBefore = session.player().getRingCount();
                    outgoingObjects = level.getObjectManager();
                }
                session.step(movie.getFrame(frame)); session.renderFrame();
                var mode = session.loop().getCurrentGameMode();
                if (mode == GameMode.LEVEL) assertFalse(session.player().getDead(), "death " + frame);
                boolean inStage = mode == GameMode.SPECIAL_STAGE
                        || (mode == GameMode.LEVEL && level.getCurrentZone() == 0x17 && level.getCurrentAct() == 1);
                if (entry < 0 && inStage) {
                    entry = frame;
                    assertEquals(sanctuary ? GameMode.LEVEL : GameMode.SPECIAL_STAGE, mode,
                            "SSEntryFlash_GoSS: zone >= 7 with seven Chaos loads $1701, otherwise Blue Sphere");
                    assertEquals(0x200, GameServices.gameState().capture().collectedSpecialRings() & 0x200,
                            "Collected_special_ring_array bit = subtype 9");
                    assertTrue(level.hasBigRingReturn(), "Save_Level_Data2 recorded the origin");
                    saved = level.getBigRingReturn();
                }
                if (mode == GameMode.SPECIAL_STAGE_RESULTS && results < 0) results = frame;
                if (entry >= 0 && returned < 0 && mode == GameMode.LEVEL && level.getCurrentZone() == 9
                        && level.getCurrentAct() == 1 && !level.hasPendingFreshLevelTransitionBoundary()
                        && !level.getTransitions().isTitleCardRequested() && !session.player().isControlLocked()) {
                    returned = frame;
                    assertNotSame(outgoingObjects, level.getObjectManager(), "the return is a real LRZ2 load");
                    assertEquals(saved.playerX(), session.player().getCentreX(), "Saved2 X");
                    assertEquals(ringsBefore, session.player().getRingCount(), "Saved2 rings");
                    assertTrue(level.getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.Sonic3kSSEntryRingObjectInstance.class).stream()
                            .noneMatch(ring -> ring.getSpawn() != null && ring.getSpawn().subtype() == 9),
                            "the collected giant ring is not respawned");
                    assertEquals(sanctuary ? java.util.List.of(3, 3, 3, 3, 3, 3, 3) : java.util.List.of(0, 0, 0, 0, 0, 0, 0),
                            GameServices.gameState().getS3kEmeraldStates(), "no emerald changes on a failed/exit path");
                }
                if (returned >= 0 && (frame == returned + 60 || frame == returned + 200)) {
                    replayWindow(session, movie, frame, 45, "LRZ2 return +" + (frame - returned));
                }
            }
        }
        System.out.println("G " + emeralds + ": entry " + entry + ", results " + results + ", return " + returned
                + ", Saved2 " + saved);
        assertTrue(entry > 49073, "giant ring entered after the hold");
        assertEquals(!sanctuary, results > entry, "Blue Sphere results only on the stage path");
        assertTrue(returned > entry && returned + 245 < movie.getFrameCount(), "released LRZ2 return with replays");
    }

    /**
     * G7: the accepted s9 route under declared seven-Chaos progress ({@code 1111111}, the
     * only declared state). Inputs 0-50099 equal {@code lrz-tails-cold-giant-ring-s9-320.bk2};
     * the authored tail {@code 45 R;46 -;1 A;3000 -} walks off the arrival pad, coasts to a stop
     * under gray pedestal 2 and jumps straight up through its top-only solid, landing on the
     * descent near its centre (SolidObjectTop loc_1E42E, skdisasm 044fa46 sonic3k.asm:42062-42088), then stays neutral. {@code SSEntryFlash_GoSS} restarts into $1701
     * (sonic3k.asm:128492-128531), the sanctuary converts every Chaos Emerald to gray
     * (:198056-198094), the pedestal selects its Super stage by actual contact
     * (:197915-197948), neutral input fails it, and the results return to LRZ2 at Saved2
     * (:12745-12748, :7603-7614). The exit band is not crossed by this route. A declared native
     * twin of the hub-onward sequence lands on pedestal 2 at the same release-relative frame.
     */
    @Test
    void tailsSevenChaosGiantRingFailsAPedestalStageAndReturnsToLrzTwo() throws Exception {
        var loader = new Bk2MovieLoader();
        var movie = loader.loadMovieOrInputLog(route("lrz-tails-cold-seven-chaos-s9-320.bk2"));
        var source = loader.loadMovieOrInputLog(route("lrz-tails-cold-giant-ring-s9-320.bk2"));
        int authoredFrom = 50100;
        for (int f = 0; f < authoredFrom; f++) assertEquals(source.getFrame(f), movie.getFrame(f), "accepted s9 input " + f);
        var settings = new GameplayCaptureSession.Settings(320, "tails", "", "off", null, null, null, "1111111",
                false, false);
        int hub = -1, hubRelease = -1, pedestal = -1, selected = -1, stage = -1, results = -1, returned = -1, ringsBefore = -1;
        int markBefore = Integer.MIN_VALUE;
        Object outgoingObjects = null;
        com.openggf.level.BigRingReturnState saved = null;
        var gray = java.util.List.of(2, 2, 2, 2, 2, 2, 2);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 9, 0, settings);
            for (int frame = 0; frame < movie.getFrameCount(); frame++) {
                var level = GameServices.level();
                if (hub < 0) {
                    ringsBefore = session.player().getRingCount();
                    outgoingObjects = level.getObjectManager();
                    markBefore = level.getCheckpointState().getStarPostActivationMark();
                }
                session.step(movie.getFrame(frame)); session.renderFrame();
                var mode = session.loop().getCurrentGameMode();
                if (mode == GameMode.LEVEL) assertFalse(session.player().getDead(), "death " + frame);
                boolean inHub = mode == GameMode.LEVEL && level.getCurrentZone() == 0x17 && level.getCurrentAct() == 1;
                if (hub < 0 && (inHub || mode == GameMode.SPECIAL_STAGE)) {
                    hub = frame;
                    assertEquals(GameMode.LEVEL, mode, "seven Chaos on an S&K level restarts into $1701, not Blue Sphere");
                    assertEquals(0x200, GameServices.gameState().capture().collectedSpecialRings() & 0x200,
                            "Collected_special_ring_array bit = subtype 9");
                    saved = level.getBigRingReturn();
                    assertNotNull(saved, "Save_Level_Data2 recorded the origin");
                    assertEquals(9, saved.originZone());
                    assertEquals(1, saved.originAct());
                    assertFalse(level.getTransitions().isLastStarPostHitSet(), "loc_618AC clears Last_star_post_hit");
                }
                if (hub >= 0 && hubRelease < 0 && inHub && !session.player().isObjectControlled()
                        && !session.player().isControlLocked() && !level.hasPendingFreshLevelTransitionBoundary()
                        && !level.getTransitions().isTitleCardRequested()) {
                    hubRelease = frame;
                    assertEquals(gray, GameServices.gameState().getS3kEmeraldStates(), "every Chaos Emerald converted to gray");
                    assertTrue(GameServices.gameState().isEmeraldsConverted(), "Emeralds_converted_flag");
                }
                if (hubRelease >= 0 && frame == hubRelease + 10) replayWindow(session, movie, frame, 45, "$1701 hub +10");
                if (inHub && hubRelease >= 0 && pedestal < 0
                        && level.getObjectManager().getRidingObject(session.player())
                                instanceof com.openggf.game.sonic3k.objects.HPZSuperEmeraldObjectInstance ridden) {
                    pedestal = frame;
                    assertEquals(2, ridden.getSpawn().subtype(), "ordinary input lands on gray pedestal 2");
                }
                if (inHub && pedestal >= 0 && selected < 0 && session.player().isObjectControlled()) selected = frame;
                if (mode == GameMode.SPECIAL_STAGE && stage < 0) {
                    stage = frame;
                    var provider = assertInstanceOf(com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider.class,
                            session.loop().getActiveSpecialStageProvider());
                    assertEquals(2, provider.getCurrentStage(), "loc_90926: Current_special_stage = pedestal subtype");
                }
                if (mode == GameMode.SPECIAL_STAGE_RESULTS && results < 0) results = frame;
                if (results >= 0 && returned < 0 && mode == GameMode.LEVEL && level.getCurrentZone() == 9
                        && level.getCurrentAct() == 1 && !level.hasPendingFreshLevelTransitionBoundary()
                        && !level.getTransitions().isTitleCardRequested() && !session.player().isControlLocked()) {
                    returned = frame;
                    assertNotSame(outgoingObjects, level.getObjectManager(), "the return is a real LRZ2 load");
                    assertEquals(saved.playerX(), session.player().getCentreX(), "Saved2 X");
                    assertEquals(ringsBefore, session.player().getRingCount(), "Saved2 rings");
                    assertTrue(level.getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.Sonic3kSSEntryRingObjectInstance.class).stream()
                            .noneMatch(ring -> ring.getSpawn() != null && ring.getSpawn().subtype() == 9),
                            "the collected giant ring is not respawned");
                    assertEquals(gray, GameServices.gameState().getS3kEmeraldStates(), "a failed stage keeps the pedestal gray");
                    assertTrue(GameServices.gameState().isEmeraldsConverted());
                    // Level masks the stage exit's ori.b #$80 off with andi.b #$7F,(Last_star_post_hit) just
                    // before LevelLoop (loc_64DC, skdisasm 044fa46 sonic3k.asm:7945; native RAM $FE2A reads 0 on the first $0C frame), so
                    // only the restored low-bit mark below is ROM state here. After load the engine boolean
                    // plays Tails_CPU_star_post_flag (loc_137A4 copy, still $80 natively); not asserted here.
                    assertEquals(markBefore, level.getCheckpointState().getStarPostActivationMark(),
                            "loc_90926 restored Saved2_last_star_post_hit across the sanctuary detour");
                    assertFalse(level.hasBigRingReturn(), "Saved2 consumed by the destination restore");
                }
                if (returned >= 0 && (frame == returned + 60 || frame == returned + 200)) {
                    replayWindow(session, movie, frame, 45, "LRZ2 return +" + (frame - returned));
                }
            }
        }
        System.out.println("G7 1111111: hub " + hub + ", release " + hubRelease + ", pedestal " + pedestal
                + ", stage " + stage + ", results " + results + ", return " + returned + ", Saved2 " + saved);
        assertTrue(hub > 49073, "giant ring entered after the hold");
        assertTrue(hubRelease > hub && hubRelease + 10 + 45 < pedestal, "hub replay window precedes the pedestal");
        assertTrue(pedestal >= authoredFrom && stage > pedestal && results > stage && returned > results,
                "pedestal, stage, results and LRZ2 return in order");
        // loc_908DE (skdisasm 044fa46 sonic3k.asm:197914-197928) calls SolidObjectTop and tests p1_standing_bit in the
        // same routine, so object_control $81 lands with the player (native twin: same frame).
        assertEquals(pedestal, selected, "the landing frame selects the pedestal");
        assertTrue(returned + 245 < movie.getFrameCount(), "released LRZ2 return with replays");
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
