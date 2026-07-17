package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kFBZEvents;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolated production-pipeline evidence for optional FBZ2 interactions that
 * the authoritative mandatory completion path legitimately bypasses.
 *
 * <p>Every object comes from an exact placement record in the loaded act and
 * is materialized by {@link ObjectManager#reset(int)}, which uses the normal
 * production placement/registry path. No invented spawn or direct object
 * constructor participates in this compatibility evidence.</p>
 */
public final class FbzCompatibilityInteractionProbe {
    private FbzCompatibilityInteractionProbe() { }

    public static Evidence run(int viewportWidth) {
        boolean prison = probePrison(viewportWidth);
        FlameEvidence flame = probeFlamethrower(viewportWidth);
        MagneticEvidence magnetic = probeMagneticPlatforms(viewportWidth);
        SpiderEvidence spider = probeSpiderCrane(viewportWidth);
        return new Evidence(prison, flame.hazardActive(), flame.standingSuppressed(),
                flame.allEligibleSolid(), magnetic.bothSubtypesMoved(),
                magnetic.allEligibleCoherent(), spider.mainCapturedMovedReleased(),
                spider.sidekickAuthorityPreserved());
    }

    private static boolean probePrison(int viewportWidth) {
        ProbeContext context = contextAt(viewportWidth, 0xCF, spawn -> true);
        FbzEggPrisonInstance prison = exactObject(
                context, context.spawn(), FbzEggPrisonInstance.class);
        context.fixture().stepIdleFrames(2);
        FbzEggPrisonButtonInstance button = context.objects()
                .activeObjectsOfType(FbzEggPrisonButtonInstance.class).stream()
                .filter(candidate -> candidate.parentForTest() == prison)
                .findFirst().orElseThrow();
        AbstractPlayableSprite trigger = lastEligiblePlayer(context.players());
        placeForLanding(trigger, button.getX(), button.getY(), 4);
        context.fixture().stepIdleFrames(3);
        assertTrue(prison.releaseAttemptedForTest(),
                "exact placed $CF button contact did not open the prison");
        assertFalse(context.objects().activeObjectsOfType(
                        FbzEggPrisonExplosionController.class).isEmpty(),
                "placed $CF destruction graph did not allocate its explosion controller");
        assertFalse(context.objects().activeObjectsOfType(
                        FbzEggPrisonFragmentInstance.class).isEmpty(),
                "placed $CF destruction graph did not allocate fragments");
        assertFalse(trigger.getDead(), "prison trigger participant died during the probe");
        return true;
    }

    private static FlameEvidence probeFlamethrower(int viewportWidth) {
        ProbeContext context = contextAt(viewportWidth, 0xE4,
                spawn -> (spawn.subtype() & 0xC0) == 0);
        FbzFlamethrowerObjectInstance flame = exactObject(
                context, context.spawn(), FbzFlamethrowerObjectInstance.class);
        context.fixture().stepIdleFrames(8);
        int liveHazards = context.objects().activeObjectsOfType(FbzFlameObjectInstance.class).size();
        assertTrue(liveHazards > 0,
                "exact placed $E4 did not publish an active flame hazard");

        for (int index = 0; index < context.players().size(); index++) {
            AbstractPlayableSprite player = context.players().get(index);
            placeForLanding(player, flame.getX() - 6 + index * 4, flame.getY(), 9);
        }
        context.fixture().stepIdleFrames(2);
        for (AbstractPlayableSprite player : context.players()) {
            assertSame(flame, context.objects().getRidingObject(player),
                    "eligible participant did not establish an independent $E4 solid contact");
        }
        assertEquals(2, flame.mappingFrame(),
                "standing contact did not enter the ROM flame-suppression frame");
        assertTrue(flame.standingTimer() > 0,
                "standing suppression did not start the exact $3C timer");
        context.fixture().stepIdleFrames(8);
        assertEquals(2, flame.mappingFrame(),
                "standing riders did not keep the active flame suppressed");
        assertTrue(context.objects().activeObjectsOfType(FbzFlameObjectInstance.class).size()
                        <= liveHazards,
                "suppressed $E4 continued allocating flame hazards");
        assertAllAlive(context.players(), "$E4 standing probe");
        return new FlameEvidence(true, true, true);
    }

    private static MagneticEvidence probeMagneticPlatforms(int viewportWidth) {
        boolean bothSubtypesMoved = true;
        boolean allEligibleCoherent = true;
        for (int subtype : List.of(0x0E, 0x0F)) {
            ProbeContext context = contextAt(viewportWidth, 0x74,
                    spawn -> (spawn.subtype() & 0xFF) == subtype);
            FbzMagneticPlatformObjectInstance platform = exactObject(
                    context, context.spawn(), FbzMagneticPlatformObjectInstance.class);
            if (subtype == 0x0F) {
                Sonic3kInvisibleHurtBlockVObjectInstance crusher = context.objects()
                        .activeObjectsOfType(Sonic3kInvisibleHurtBlockVObjectInstance.class).stream()
                        .filter(candidate -> candidate.getSpawn().x() == platform.getSpawn().x()
                                && candidate.getSpawn().y() == platform.getSpawn().y() - 0x58
                                && candidate.getSpawn().subtype() == 0x61
                                && candidate.getSpawn().renderFlags() == 0x02)
                        .findFirst().orElseThrow();
                assertTrue(crusher.getSlotIndex() < platform.getSlotIndex(),
                        "authored $6B/$61 crusher must execute before its $74/$0F platform; "
                                + "crusherSlot=" + crusher.getSlotIndex()
                                + " platformSlot=" + platform.getSlotIndex());
                assertTrue(crusher.getSpawn().layoutIndex() < platform.getSpawn().layoutIndex(),
                        "production placement order no longer matches the authored crusher corridor");
            }
            fbzEvents().setMagneticState(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, 1);

            AbstractPlayableSprite main = context.fixture().sprite();
            placeForMagneticPlatformLanding(main, platform.getX(), platform.getY());
            context.fixture().stepIdleFrames(2);
            assertSame(platform, context.objects().getRidingObject(main),
                    "P1 did not establish the production inactive-platform history seed");
            int seededMainX = main.getCentreX() & 0xFFFF;
            List<AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
            int historySeedFrames = 17 * Math.max(1, sidekicks.size());
            for (int historyFrame = 1; historyFrame <= historySeedFrames; historyFrame++) {
                context.fixture().stepIdleFrames(1);
                assertSame(platform, context.objects().getRidingObject(main),
                        "P1 left the inactive $74 while seeding native follower history");
                assertEquals(seededMainX, main.getCentreX() & 0xFFFF,
                        "idle P1 moved horizontally during $74 history seeding");
                assertAllAlive(context.players(), "$74 follower-history seed");
            }

            for (int index = 0; index < sidekicks.size(); index++) {
                AbstractPlayableSprite player = sidekicks.get(index);
                placeForMagneticPlatformLanding(player,
                        platform.getX() - 4 + index * 4, platform.getY());
            }
            context.fixture().stepIdleFrames(2);
            for (int index = 0; index < context.players().size(); index++) {
                AbstractPlayableSprite player = context.players().get(index);
                assertSame(platform, context.objects().getRidingObject(player),
                        "eligible participant " + index
                                + " did not land on exact placed $74/$"
                                + Integer.toHexString(subtype)
                                + "; p=(0x" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                                + ",0x" + Integer.toHexString(player.getCentreY() & 0xFFFF)
                                + ") platform=(0x" + Integer.toHexString(platform.getX())
                                + ",0x" + Integer.toHexString(platform.getY()) + ')');
            }
            for (int index = 0; index < sidekicks.size(); index++) {
                int participantIndex = index + 1;
                AbstractPlayableSprite sidekick = sidekicks.get(index);
                assertTrue(Math.abs(sidekick.getCentreX() - platform.getX()) < 0x23,
                        () -> "participant " + participantIndex
                                + " began the active phase outside the exact $23 ride window; "
                                + "generated=0x" + Integer.toHexString(
                                sidekick.getCpuController().getDiagnosticGeneratedHeldInput()));
            }
            int inactiveY = platform.getY();
            boolean[] attached = new boolean[context.players().size()];
            boolean[] receivedVerticalCarry = new boolean[context.players().size()];
            boolean[] voluntaryExit = new boolean[context.players().size()];
            boolean[] authoredCrusherDeath = new boolean[context.players().size()];
            boolean[] leaderDeathRecovery = new boolean[context.players().size()];
            for (int index = 0; index < attached.length; index++) attached[index] = true;
            fbzEvents().setMagneticState(Sonic3kFBZEvents.MagneticPolarity.ACTIVE, 0);
            for (int riseFrame = 1; riseFrame <= 40; riseFrame++) {
                int previousPlatformY = platform.getY();
                context.fixture().stepIdleFrames(1);
                int platformDeltaY = platform.getY() - previousPlatformY;
                for (int index = 0; index < context.players().size(); index++) {
                    int observedRiseFrame = riseFrame;
                    int participantIndex = index;
                    AbstractPlayableSprite player = context.players().get(index);
                    ObjectInstance riding = context.objects().getRidingObject(player);
                    if (attached[index] && riding == platform) {
                        assertEquals(platform.getY() - 9 - 8 - player.getYRadius(),
                                player.getCentreY() & 0xFFFF,
                                () -> "continued $74 seat drifted: "
                                        + magneticDropDiagnostic(context, platform, subtype,
                                        observedRiseFrame, participantIndex, player, inactiveY));
                        receivedVerticalCarry[index] |= platformDeltaY != 0;
                        continue;
                    }
                    if (attached[index]) {
                        if (subtype == 0x0F && player.getDead()) {
                            assertAuthoredMagneticCrusherDeath(context, platform,
                                    observedRiseFrame, participantIndex, player, inactiveY);
                            authoredCrusherDeath[index] = true;
                        } else if (subtype == 0x0F
                                && isNativeLeaderDeathRecovery(player)) {
                            assertNativeLeaderDeathRecovery(context, platform,
                                    observedRiseFrame, participantIndex, player, inactiveY);
                            leaderDeathRecovery[index] = true;
                        } else {
                            assertVoluntaryMagneticExit(context, platform, subtype,
                                    observedRiseFrame, participantIndex, player, inactiveY);
                            voluntaryExit[index] = true;
                        }
                        attached[index] = false;
                    }
                }
                for (int index = 0; index < context.players().size(); index++) {
                    if (attached[index]) {
                        assertSame(platform,
                                context.objects().getRidingObject(context.players().get(index)),
                                "one participant's exit corrupted another participant's ride map");
                    }
                }
            }
            assertTrue(platform.lastMagneticActive(),
                    "active polarity was not consumed by $74/$"
                            + Integer.toHexString(subtype));
            assertTrue(platform.getY() < inactiveY,
                    "active polarity did not raise $74/$" + Integer.toHexString(subtype));
            for (int index = 0; index < context.players().size(); index++) {
                assertTrue(receivedVerticalCarry[index],
                        "participant " + index
                                + " never received a nonzero $74 vertical carry step");
                assertTrue(attached[index] || voluntaryExit[index]
                                || authoredCrusherDeath[index] || leaderDeathRecovery[index],
                        "participant " + index + " lost its ride without a classified exit");
            }
            if (subtype == 0x0E) {
                assertTrue(attached[0], "idle P1 may not voluntarily leave the $74 anchor ride");
                assertSame(platform, context.objects().getRidingObject(main),
                        "P1 did not retain independent $74 ride ownership");
                assertAllAlive(context.players(), "$74/$0E active-rise probe");
            } else {
                assertTrue(authoredCrusherDeath[0],
                        "idle P1 did not reach the authored $6B/$61 ceiling crusher");
                for (int index = 0; index < context.players().size(); index++) {
                    assertTrue(authoredCrusherDeath[index]
                                    || voluntaryExit[index] || leaderDeathRecovery[index],
                            "participant " + index
                                    + " neither reached the authored crusher, made a voluntary CPU exit, "
                                    + "nor entered native leader-death recovery");
                }
            }
            fbzEvents().setMagneticState(Sonic3kFBZEvents.MagneticPolarity.INACTIVE, 1);
            context.fixture().stepIdleFrames(2);
            assertFalse(platform.lastMagneticActive(),
                    "inactive phase was not consumed by $74/$"
                            + Integer.toHexString(subtype));
            if (subtype == 0x0E) {
                assertAllAlive(context.players(), "$74/$0E magnetic probe");
            }
        }
        return new MagneticEvidence(bothSubtypesMoved, allEligibleCoherent);
    }

    private static void assertVoluntaryMagneticExit(
            ProbeContext context,
            FbzMagneticPlatformObjectInstance platform,
            int subtype,
            int riseFrame,
            int participantIndex,
            AbstractPlayableSprite player,
            int initialPlatformY) {
        String diagnostic = magneticDropDiagnostic(context, platform, subtype,
                riseFrame, participantIndex, player, initialPlatformY);
        assertTrue(participantIndex > 0 && player.isCpuControlled(),
                "P1/non-CPU rider left without input: " + diagnostic);
        SidekickCpuController cpu = player.getCpuController();
        assertTrue(cpu != null, "departing CPU participant has no controller: " + diagnostic);
        int relativeX = player.getCentreX() - platform.getX();
        int generatedHeld = cpu.getDiagnosticGeneratedHeldInput();
        int generatedPressed = cpu.getDiagnosticGeneratedPressedInput();
        boolean walkedOutLeft = relativeX < -0x23
                && (generatedHeld & AbstractPlayableSprite.INPUT_LEFT) != 0;
        boolean walkedOutRight = relativeX >= 0x23
                && (generatedHeld & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        boolean jumped = (generatedPressed & AbstractPlayableSprite.INPUT_JUMP) != 0;
        assertTrue(walkedOutLeft || walkedOutRight || jumped,
                "rider loss was neither an exact-bound walk-off nor generated jump: "
                        + diagnostic);
        assertFalse(player.getDead(), "voluntary $74 exit killed participant: " + diagnostic);
        assertTrue(player.getAir(), "voluntary $74 exit did not set air: " + diagnostic);
        assertFalse(player.isOnObject(), "voluntary $74 exit kept OnObj: " + diagnostic);
        assertTrue(context.objects().getRidingObject(player) == null,
                "voluntary $74 exit retained a ride-map owner: " + diagnostic);
    }

    private static void assertAuthoredMagneticCrusherDeath(
            ProbeContext context,
            FbzMagneticPlatformObjectInstance platform,
            int riseFrame,
            int participantIndex,
            AbstractPlayableSprite player,
            int initialPlatformY) {
        String diagnostic = magneticDropDiagnostic(context, platform, 0x0F,
                riseFrame, participantIndex, player, initialPlatformY);
        assertTrue(player.getDead(), "authored $6B/$61 contact did not kill: " + diagnostic);
        assertTrue(player.getAir(), "Kill_Character did not set Status_InAir: " + diagnostic);
        assertEquals(0, player.getXSpeed(), "Kill_Character did not clear x_vel: " + diagnostic);
        assertEquals(0, player.getGSpeed(), "Kill_Character did not clear ground_vel: " + diagnostic);
        assertEquals((short) -0x700, player.getYSpeed(),
                "Kill_Character did not publish y_vel=-$700: " + diagnostic);
        assertFalse(player.isObjectControlled(),
                "authored crusher corrupted object-control ownership: " + diagnostic);
        assertTrue(context.objects().getRidingObject(player) == null,
                "authored crusher retained a live ride-map owner: " + diagnostic);
        assertFalse(player.isOnObject(),
                "later-slot SolidObjectFull_Offset did not clear Status_OnObj: " + diagnostic
                        + "; standingBit="
                        + context.objects().hasObjectStandingBit(player, platform));
        assertFalse(context.objects().hasObjectStandingBit(player, platform),
                "later-slot loc_1DC98 equivalent retained the platform standing bit: " + diagnostic);
    }

    private static boolean isNativeLeaderDeathRecovery(AbstractPlayableSprite player) {
        if (!player.isCpuControlled() || player.getCpuController() == null) {
            return false;
        }
        SidekickCpuController cpu = player.getCpuController();
        AbstractPlayableSprite directLeader = cpu.getLeader();
        AbstractPlayableSprite effectiveLeader = cpu.getEffectiveLeader();
        boolean leaderDead = directLeader != null && directLeader.getDead()
                || effectiveLeader != null && effectiveLeader.getDead();
        return leaderDead && cpu.getState() == SidekickCpuController.State.FLIGHT_AUTO_RECOVERY;
    }

    private static void assertNativeLeaderDeathRecovery(
            ProbeContext context,
            FbzMagneticPlatformObjectInstance platform,
            int riseFrame,
            int participantIndex,
            AbstractPlayableSprite player,
            int initialPlatformY) {
        String diagnostic = magneticDropDiagnostic(context, platform, 0x0F,
                riseFrame, participantIndex, player, initialPlatformY);
        assertTrue(player.isCpuControlled(), "leader recovery reached non-CPU identity: " + diagnostic);
        SidekickCpuController cpu = player.getCpuController();
        assertTrue(cpu != null, "leader recovery has no CPU controller: " + diagnostic);
        AbstractPlayableSprite directLeader = cpu.getLeader();
        AbstractPlayableSprite effectiveLeader = cpu.getEffectiveLeader();
        assertTrue(directLeader != null && directLeader.getDead()
                        || effectiveLeader != null && effectiveLeader.getDead(),
                "leader recovery has no dead direct/effective leader: " + diagnostic);
        assertEquals(SidekickCpuController.State.FLIGHT_AUTO_RECOVERY, cpu.getState(),
                "leader death did not select native flight recovery: " + diagnostic);
        assertFalse(player.getDead(), "leader recovery killed the sidekick: " + diagnostic);
        assertTrue(player.getAir(), "leader recovery did not set airborne state: " + diagnostic);
        assertFalse(player.isOnObject(), "leader recovery retained Status_OnObj: " + diagnostic);
        assertTrue(context.objects().getRidingObject(player) == null,
                "leader recovery retained a ride-map owner: " + diagnostic);
    }

    private static String magneticDropDiagnostic(
            ProbeContext context,
            FbzMagneticPlatformObjectInstance platform,
            int subtype,
            int riseFrame,
            int participantIndex,
            AbstractPlayableSprite player,
            int initialPlatformY) {
        SidekickCpuController cpu = player.getCpuController();
        return "exact placed $74/$" + Integer.toHexString(subtype)
                + " dropped participant " + participantIndex + '/' + player.getCode()
                + " on active-rise frame " + riseFrame
                + "; platformY=0x" + Integer.toHexString(platform.getY())
                + " delta=" + (platform.getY() - initialPlatformY)
                + "; p=(0x" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                + ",0x" + Integer.toHexString(player.getCentreY() & 0xFFFF) + ')'
                + " v=(0x" + Integer.toHexString(player.getXSpeed() & 0xFFFF)
                + ",0x" + Integer.toHexString(player.getYSpeed() & 0xFFFF)
                + ") g=0x" + Integer.toHexString(player.getGSpeed() & 0xFFFF)
                + " air=" + player.getAir() + " onObject=" + player.isOnObject()
                + " dead=" + player.getDead()
                + " ride=" + context.objects().getRidingObject(player)
                + (cpu == null ? " cpu=none" : " cpu=" + cpu.getState()
                + " generated=0x" + Integer.toHexString(cpu.getDiagnosticGeneratedHeldInput())
                + " normal=" + cpu.formatLatestNormalStepDiagnostics());
    }

    private static SpiderEvidence probeSpiderCrane(int viewportWidth) {
        ProbeContext context = contextAt(viewportWidth, 0xE5,
                spawn -> (spawn.subtype() & 0xFF) == 0x2C);
        FbzSpiderCraneObjectInstance crane = exactObject(
                context, context.spawn(), FbzSpiderCraneObjectInstance.class);
        AbstractPlayableSprite main = context.fixture().sprite();
        main.setCentreX((short) crane.getX());
        main.setCentreY((short) (context.spawn().y() + 0x45));
        main.setAir(false);
        main.setYSpeed((short) 0);
        List<AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
        for (int index = 0; index < sidekicks.size(); index++) {
            AbstractPlayableSprite sidekick = sidekicks.get(index);
            sidekick.setCentreX((short) (crane.getX() + 4 + index * 3));
            sidekick.setCentreY((short) (context.spawn().y() + 0x45));
            sidekick.setAir(false);
            sidekick.setYSpeed((short) 0);
        }

        boolean captured = false;
        boolean moved = false;
        int captureX = main.getCentreX() & 0xFFFF;
        for (int frame = 0; frame < 1000 && !"INERT".equals(crane.stateName()); frame++) {
            context.fixture().stepFrame(false, false, false, false, false);
            captured |= main.isObjectControlled();
            if (captured) moved |= (main.getCentreX() & 0xFFFF) != captureX;
            for (AbstractPlayableSprite sidekick : sidekicks) {
                assertFalse(sidekick.isObjectControlled(),
                        "extra sidekick stole strict-P1 spider-crane authority");
                assertFalse(sidekick.getDead(),
                        "extra sidekick became stuck/dead during spider-crane motion");
            }
        }
        assertTrue(captured, "exact placed $E5 never captured P1");
        assertTrue(moved, "exact placed $E5 never transported captured P1");
        assertEquals("INERT", crane.stateName(),
                "exact placed $E5 did not complete retract/travel/release");
        assertFalse(main.isObjectControlled(), "$E5 release left P1 object-controlled");
        assertTrue(main.getAir(), "$E5 release did not restore P1 airborne state");
        assertAllAlive(context.players(), "$E5 spider-crane probe");
        return new SpiderEvidence(true, true);
    }

    private static ProbeContext contextAt(
            int viewportWidth, int objectId, Predicate<ObjectSpawn> selector) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1)
                .build();
        GameServices.graphics().setViewport(0, 0, viewportWidth, 224);
        ObjectSpawn spawn = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(candidate -> candidate.objectId() == objectId)
                .filter(selector)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "FBZ2 has no exact placement for object $"
                                + Integer.toHexString(objectId)));
        List<AbstractPlayableSprite> players = new ArrayList<>();
        players.add(fixture.sprite());
        players.addAll(GameServices.sprites().getSidekicks());
        int cameraX = Math.max(0, spawn.x() - viewportWidth / 2);
        int cameraY = Math.max(0, spawn.y() - 112);
        fixture.sprite().setCentreX((short) (spawn.x() - 0x60));
        fixture.sprite().setCentreY((short) (spawn.y() - 0x60));
        fixture.sprite().setXSpeed((short) 0);
        fixture.sprite().setYSpeed((short) 0);
        fixture.sprite().setGSpeed((short) 0);
        fixture.sprite().setAir(true);
        fixture.camera().setX((short) cameraX);
        fixture.camera().setY((short) cameraY);
        GameServices.level().getObjectManager().reset(cameraX);
        settleProductionSidekickControllers(fixture, players, objectId);

        for (int index = 0; index < players.size(); index++) {
            AbstractPlayableSprite player = players.get(index);
            player.setCentreX((short) (spawn.x() - 0x60 - index * 8));
            player.setCentreY((short) spawn.y());
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            player.setAir(false);
            player.setOnObject(false);
        }
        fixture.camera().setX((short) cameraX);
        fixture.camera().setY((short) cameraY);
        GameServices.level().getObjectManager().reset(cameraX);
        return new ProbeContext(fixture, GameServices.level().getObjectManager(),
                spawn, List.copyOf(players));
    }

    private static void settleProductionSidekickControllers(
            HeadlessTestFixture fixture, List<AbstractPlayableSprite> players, int objectId) {
        List<AbstractPlayableSprite> sidekicks = GameServices.sprites().getSidekicks();
        int frames = 0;
        while (frames < 360 && sidekicks.stream().anyMatch(sidekick ->
                sidekick.getCpuController() == null
                        || sidekick.getCpuController().getState()
                        != SidekickCpuController.State.NORMAL)) {
            fixture.stepIdleFrames(1);
            frames++;
            assertAllAlive(players, "$" + Integer.toHexString(objectId)
                    + " sidekick bootstrap");
        }
        int settledFrames = frames;
        assertTrue(sidekicks.stream().allMatch(sidekick ->
                        sidekick.getCpuController() != null
                                && sidekick.getCpuController().getState()
                                == SidekickCpuController.State.NORMAL),
                () -> "$" + Integer.toHexString(objectId)
                        + " sidekicks did not settle through production CPU bootstrap in "
                        + settledFrames + " frames: " + sidekicks.stream()
                        .map(sidekick -> sidekick.getCode() + "="
                                + (sidekick.getCpuController() == null
                                ? "no-controller" : sidekick.getCpuController().getState())
                                + "@(" + Integer.toHexString(sidekick.getCentreX() & 0xFFFF)
                                + ',' + Integer.toHexString(sidekick.getCentreY() & 0xFFFF) + ')')
                        .toList());
    }

    private static <T extends ObjectInstance> T exactObject(
            ProbeContext context, ObjectSpawn spawn, Class<T> type) {
        T object = context.objects().activeObjectsOfType(type).stream()
                .filter(candidate -> candidate.getSpawn() == spawn
                        || spawn.equals(candidate.getSpawn()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "production placement/registry did not materialize " + type.getSimpleName()
                                + " from " + spawn));
        assertSame(spawn, object.getSpawn(),
                "production registry must preserve the loaded placement record identity");
        return assertInstanceOf(type, object);
    }

    private static void placeForLanding(
            AbstractPlayableSprite player, int objectX, int objectY, int halfHeight) {
        player.setCentreX((short) objectX);
        player.setCentreY((short) (objectY - halfHeight - player.getYRadius() - 2));
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setYSpeed((short) 0x100);
        player.setAir(true);
        player.setOnObject(false);
    }

    private static void placeForMagneticPlatformLanding(
            AbstractPlayableSprite player, int objectX, int objectY) {
        player.setCentreX((short) objectX);
        // SolidObjectFull_Offset uses d3=-9 and d2=$8.  Seven pixels into
        // loc_1E154's unsigned 0..$F landing band matches the production
        // integration fixture without pre-establishing an on-object flag.
        player.setCentreY((short) (objectY - 9 - 8 - player.getYRadius() + 7));
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setYSpeed((short) 0x100);
        player.setAir(true);
        player.setOnObject(false);
    }

    private static AbstractPlayableSprite lastEligiblePlayer(
            List<AbstractPlayableSprite> players) {
        return players.get(players.size() - 1);
    }

    private static Sonic3kFBZEvents fbzEvents() {
        return assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getFbzEvents();
    }

    private static void assertAllAlive(
            List<AbstractPlayableSprite> players, String probe) {
        for (AbstractPlayableSprite player : players) {
            assertFalse(player.getDead(), probe + " killed an eligible participant");
        }
    }

    private record ProbeContext(
            HeadlessTestFixture fixture,
            ObjectManager objects,
            ObjectSpawn spawn,
            List<AbstractPlayableSprite> players) { }

    private record FlameEvidence(
            boolean hazardActive,
            boolean standingSuppressed,
            boolean allEligibleSolid) { }

    private record MagneticEvidence(
            boolean bothSubtypesMoved,
            boolean allEligibleCoherent) { }

    private record SpiderEvidence(
            boolean mainCapturedMovedReleased,
            boolean sidekickAuthorityPreserved) { }

    public record Evidence(
            boolean prisonOpened,
            boolean flamethrowerHazardActive,
            boolean flamethrowerStandingSuppressed,
            boolean flamethrowerAllEligibleSolid,
            boolean magneticBothSubtypesMoved,
            boolean magneticAllEligibleCoherent,
            boolean spiderMainCapturedMovedReleased,
            boolean spiderSidekickAuthorityPreserved) { }
}
