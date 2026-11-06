package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.objects.GumballItemObjectInstance;
import com.openggf.game.sonic3k.objects.GumballMachineObjectInstance;
import com.openggf.game.sonic3k.objects.GumballTriangleBumperObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.game.sonic3k.objects.PachinkoItemOrbObjectInstance;
import com.openggf.game.sonic3k.objects.PachinkoBumperObjectInstance;
import com.openggf.game.sonic3k.objects.Sonic3kPointsObjectInstance;
import org.junit.jupiter.api.Test;
import com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production bonus reward contacts and natural lifetimes from the S3K parity task.
 * Logical entry and local free-player contacts are declared fixtures; no reward,
 * timer, RNG, delegate or exit flags are written. This is not a cold portal route.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kGumballPachinkoRewardMechanics {
    enum Mechanism { GUMBALL_EJECTION, GUMBALL_SPRING_CHAIN, GUMBALL_BUMPER, PACHINKO_REWARD, PACHINKO_TRAP }
    private final EnumMap<SonicConfiguration, Object> saved = new EnumMap<>(SonicConfiguration.class);
    private static final Bk2FrameInput NEUTRAL = new Bk2FrameInput(0, 0, 0, false, "bonus contact fixture");

    @BeforeEach void preserveConfiguration() {
        var config = GameServices.configuration();
        for (var key : SonicConfiguration.values()) if (config.hasSessionOverride(key))
            saved.put(key, config.getConfigValue(key));
    }
    @AfterEach void restoreConfiguration() {
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        var config = GameServices.configuration();config.clearSessionOverrides();
        saved.forEach(config::setSessionOverride);config.resolveDisplayAspect();
        SessionManager.clear();TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest(name = "actual native bonus mechanism {0}")
    @EnumSource(Mechanism.class)
    void actualNativeContactAndNaturalLifecycleReplaysItsCompleteWorldTwice(Mechanism mechanism)
            throws Exception {
        check(new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", ""), mechanism);
    }

    static java.util.stream.Stream<Arguments> representativeProducts() {
        return java.util.stream.Stream.of(
                new CanonicalEntryMatrixSupport.Row("knuckles", WidescreenAspect.WIDE_16_10, "off", "tails,sonic"),
                new CanonicalEntryMatrixSupport.Row("tails", WidescreenAspect.SUPER_32_9, "s2", "sonic,sonic"),
                new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.ULTRA_21_9, "s1", "sonic,sonic,sonic,sonic,sonic,sonic,sonic"))
                .flatMap(row -> java.util.Arrays.stream(Mechanism.values()).map(mechanism -> Arguments.of(row, mechanism)));
    }

    @ParameterizedTest(name = "representative actual bonus mechanism {0}/{1}")
    @MethodSource("representativeProducts")
    void actualRepresentativeProductsReplayEveryMechanism(CanonicalEntryMatrixSupport.Row row,
            Mechanism mechanism) throws Exception {
        check(row, mechanism);
    }

    static java.util.stream.Stream<Arguments> finiteProducts() {
        return CanonicalEntryMatrixSupport.configurations().flatMap(row ->
                java.util.Arrays.stream(Mechanism.values()).map(mechanism -> Arguments.of(row, mechanism)));
    }

    @ParameterizedTest(name = "finite actual bonus mechanism {0}/{1}")
    @MethodSource("finiteProducts")
    @org.junit.jupiter.api.Tag("exhaustive-audit")
    void actualFiniteSupportedProductsReplayEveryMechanism(CanonicalEntryMatrixSupport.Row row,
            Mechanism mechanism) throws Exception {
        check(row, mechanism);
    }

    static java.util.stream.Stream<CanonicalEntryMatrixSupport.Row> player2ExitRows() {
        return java.util.stream.Stream.of(
                new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", "tails"),
                new CanonicalEntryMatrixSupport.Row("knuckles", WidescreenAspect.WIDE_16_10, "off", "tails,sonic"),
                new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.ULTRA_21_9, "s1", "sonic,sonic,sonic"));
    }

    /**
     * loc_61050: with Player_1 outside word_610AE, a Player_2 inside it is
     * deleted (Delete_Referenced_Sprite) while the stage continues; the ordinary
     * exit later reloads the origin, recreating the configured companion.
     */
    @ParameterizedTest(name = "actual exit trigger deletes native Player_2 {0}")
    @MethodSource("player2ExitRows")
    void actualExitTriggerDeletesOnlyNativePlayer2AndReturnRestoresCompanions(
            CanonicalEntryMatrixSupport.Row row) throws Exception {
        try (var session = enter(row, BonusStageType.GUMBALL)) {
            var objects = GameServices.level().getObjectManager();
            // Let the entry's leader-locked CPU start state hand over to ordinary
            // follow control before the declared local placement.
            for (int frame = 0; frame < 90; frame++) step(session, GameMode.BONUS_STAGE, objects);
            var exit = children(objects, "ExitTriggerChild").getFirst();
            var sprites = GameServices.sprites();
            var player2 = sprites.getRegisteredSidekicks().getFirst();
            var later = new java.util.ArrayList<>(sprites.getRegisteredSidekicks().subList(1,
                    sprites.getRegisteredSidekicks().size()));
            String code = player2.getCode();
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var before = registry.capture();
            Runnable deleteAndContinue = () -> {
                var p2 = sprites.getRegisteredSidekicks().getFirst();
                assertEquals(code, p2.getCode());
                NativePositionOps.writeXPosResetSubpixel(p2, exit.getX());
                NativePositionOps.writeYPosResetSubpixel(p2, exit.getY() + 8);
                p2.setXSpeed((short) 0);p2.setYSpeed((short) 0);p2.setGSpeed((short) 0);
                for (int frame = 0; frame < 3 && sprites.getSprite(code) != null; frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertNull(sprites.getSprite(code), () -> "loc_61050 Delete_Referenced_Sprite clears Player_2; p2="
                        + Integer.toHexString(p2.getCentreX()) + "," + Integer.toHexString(p2.getCentreY())
                        + " exit=" + Integer.toHexString(exit.getX()) + "," + Integer.toHexString(exit.getY())
                        + " spawn=" + Integer.toHexString(exit.getSpawn().x()) + "," + Integer.toHexString(exit.getSpawn().y())
                        + " p1=" + Integer.toHexString(sprites.getMainPlayable().getCentreX()) + ","
                        + Integer.toHexString(sprites.getMainPlayable().getCentreY())
                        + " destroyed=" + exit.isDestroyed() + " slot=" + exit.getSlotIndex());
                assertFalse(sprites.getRegisteredSidekicks().contains(p2));
                assertEquals(later, sprites.getRegisteredSidekicks(), "later followers have no Player_2 slot");
                int x = p2.getCentreX(), y = p2.getCentreY(), frameIndex = p2.getMappingFrame();
                for (int frame = 0; frame < 12; frame++) step(session, GameMode.BONUS_STAGE, objects);
                assertEquals(x, p2.getCentreX(), "deleted Player_2 no longer updates");
                assertEquals(y, p2.getCentreY());
                assertEquals(frameIndex, p2.getMappingFrame());
                assertNull(sprites.getSprite(code), "deleted Player_2 is neither drawn nor collided");
                assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode(), "P2 alone does not exit");
            };
            replayTwice(before, deleteAndContinue, "actual exit-trigger Player_2 deletion");
            assertNull(sprites.getSprite(code));
            var main = sprites.getMainPlayable();
            NativePositionOps.writeXPosResetSubpixel(main, exit.getX());
            NativePositionOps.writeYPosResetSubpixel(main, exit.getY() + 8);
            main.setXSpeed((short) 0);main.setYSpeed((short) 0);main.setGSpeed((short) 0);
            for (int frame = 0; frame < 900; frame++) {
                session.step(NEUTRAL);session.renderFrame();
                if (session.loop().getCurrentGameMode() == GameMode.LEVEL
                        && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion()) break;
            }
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode(), "ordinary exit returns to the origin");
            assertNotEquals(BonusStageType.GUMBALL, GameServices.bonusStage().getActiveType());
            assertRoster(row);
            assertSame(player2, GameServices.sprites().getRegisteredSidekicks().getFirst(),
                    "the origin reload recreates the configured Player_2 first");
        }
    }

    /** sub_32F56 / loc_32FC4: real Touch_Loop contact adds 10, then AllocateObject owns the popup. */
    @Test
    void actualPachinkoBumperContactAllocatesScorePopupAndReplaysItsWorldTwice() throws Exception {
        var row = new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", "");
        try (var session = enter(row, BonusStageType.GLOWING_SPHERE)) {
            var objects = GameServices.level().getObjectManager();
            objects.setRewindInPlaceRestoreEnabledForTest(false);
            var bumper = objects.activeObjectsOfType(PachinkoBumperObjectInstance.class).stream()
                    .min(java.util.Comparator.comparingInt(object -> Math.abs(object.getY()
                            - GameServices.sprites().getMainPlayable().getCentreY()))).orElseThrow();
            int x = bumper.getX(), y = bumper.getY(), bumperSlot = bumper.getSlotIndex();
            place(x, y - 16);
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var before = registry.capture();
            Runnable contact = () -> {
                int score = GameServices.gameState().getScore();
                for (int frame = 0; frame < 8 && GameServices.gameState().getScore() == score; frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertEquals(score + 10, GameServices.gameState().getScore(), "actual bumper bounce owns the credit");
                var popups = objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class);
                assertEquals(1, popups.size(), "loc_32FC4 AllocateObject must create Obj_EnemyScore on the bounce");
                var popup = popups.getFirst();
                assertEquals(x, popup.getX());
                assertEquals(y, popup.getSpawn().y(), "the producer writes the bumper centre, before popup movement");
                assertEquals(4, pointsFrame(popup), "Map_EnemyScore frame 4 is the 10-point sprite");
                assertTrue(popup.getSlotIndex() >= 0, "ordinary AllocateObject supplies the SST identity");
                int updates = popup.getSlotIndex() > bumperSlot ? 1 : 0;
                assertEquals(y - 3 * updates, popup.getY(), "first update follows the ordinary SST execution order");
                assertEquals(-0x300 + 0x18 * updates, pointsVelocity(popup));
            };
            replayTwice(before, contact, "actual Pachinko bumper contact / popup allocation");
            var livePopup = registry.capture();
            var oldPopup = objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class).getFirst();
            Runnable naturalDeletion = () -> {
                var popup = objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class).getFirst();
                int score = GameServices.gameState().getScore();
                place(x + 0x40, y - 0x40);
                int remaining = (-pointsVelocity(popup) + 0x17) / 0x18;
                for (int frame = 0; frame < remaining; frame++) step(session, GameMode.BONUS_STAGE, objects);
                assertFalse(popup.isDestroyed(), "loc_2CD0C tests velocity before moving");
                assertEquals(0, pointsVelocity(popup));
                step(session, GameMode.BONUS_STAGE, objects);
                assertTrue(popup.isDestroyed(), "nonnegative velocity naturally deletes Obj_EnemyScore");
                assertTrue(objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class).isEmpty());
                assertEquals(score, GameServices.gameState().getScore(), "popup lifetime never adds score");
            };
            replayTwice(livePopup, naturalDeletion, "natural Pachinko popup deletion / recreation");
            registry.restore(livePopup);
            same(livePopup, registry.capture(), "live popup restores after natural deletion");
            var restoredPopup = objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class).getFirst();
            assertNotSame(oldPopup, restoredPopup, "restore must recreate the naturally deleted popup");
            assertEquals(4, pointsFrame(restoredPopup), "recreation restores the non-default score frame");
            var restoredBumper = objects.activeObjectsOfType(PachinkoBumperObjectInstance.class).stream()
                    .filter(object -> object.getSlotIndex() == bumperSlot).findFirst().orElseThrow();
            assertNotSame(bumper, restoredBumper, "whole-world restore recreates the producer too");
        }
    }

    @Test
    void actualPachinkoBumperKeepsScoreWhenOrdinarySstAllocationFails() throws Exception {
        var row = new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", "");
        try (var session = enter(row, BonusStageType.GLOWING_SPHERE)) {
            var objects = GameServices.level().getObjectManager();
            var bumper = objects.activeObjectsOfType(PachinkoBumperObjectInstance.class).getFirst();
            // Fill the pool through AllocateObject; no slot, allocator or private state writes.
            int allocated = 0;
            while (objects.createDynamicObject(() -> new Sonic3kPointsObjectInstance(
                    new com.openggf.level.objects.ObjectSpawn(bumper.getX(), bumper.getY(), 0x29, 0, 0, false, 0),
                    objects.getObjectServices(), 100)) != null) {
                assertTrue(++allocated <= 128, "the native dynamic SST pool is bounded");
            }
            int before = objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class).size();
            assertTrue(before > 0);
            int score = GameServices.gameState().getScore();
            place(bumper.getX(), bumper.getY() - 16);
            for (int frame = 0; frame < 8 && GameServices.gameState().getScore() == score; frame++)
                step(session, GameMode.BONUS_STAGE, objects);
            assertEquals(score + 10, GameServices.gameState().getScore(), "HUD_AddToScore precedes allocation failure");
            var popups = objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class);
            assertEquals(before, popups.size(), "allocation failure creates no extra popup");
            for (var popup : popups) assertEquals(0, pointsFrame(popup), "existing 100-point consumers remain unchanged");
        }
    }

    @Test
    void actualPachinkoBumperDoesNotProduceScoreWithoutEligibleContact() throws Exception {
        var row = new CanonicalEntryMatrixSupport.Row("sonic", WidescreenAspect.NATIVE_4_3, "off", "");
        try (var session = enter(row, BonusStageType.GLOWING_SPHERE)) {
            var objects = GameServices.level().getObjectManager();
            var bumper = objects.activeObjectsOfType(PachinkoBumperObjectInstance.class).getFirst();
            int score = GameServices.gameState().getScore();
            place(bumper.getX(), bumper.getY() - 64);
            step(session, GameMode.BONUS_STAGE, objects);
            assertEquals(score, GameServices.gameState().getScore());
            assertTrue(objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class).isEmpty());
            place(bumper.getX(), bumper.getY() - 16);
            GameServices.sprites().getMainPlayable().setHurt(true);
            for (int frame = 0; frame < 3; frame++) step(session, GameMode.BONUS_STAGE, objects);
            assertEquals(score, GameServices.gameState().getScore(), "hurt contact is rejected by the ordinary touch owner");
            assertTrue(objects.activeObjectsOfType(Sonic3kPointsObjectInstance.class).isEmpty());
        }
    }

    private static int pointsFrame(Sonic3kPointsObjectInstance popup) {
        return pointsScalar(popup, "scoreFrame");
    }

    private static int pointsVelocity(Sonic3kPointsObjectInstance popup) {
        return pointsScalar(popup, "yVel");
    }

    private static int pointsScalar(Sonic3kPointsObjectInstance popup, String name) {
        try {
            var field = com.openggf.level.objects.AbstractPointsObjectInstance.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(popup);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private GameplayCaptureSession enter(CanonicalEntryMatrixSupport.Row row, BonusStageType type) throws Exception {
        var donorRom = row.donor().equals("off") ? null : (row.donor().equals("s1")
                ? RomTestUtils.ensureSonic1RomAvailable() : RomTestUtils.ensureSonic2RomAvailable()).toPath();
        var settings = new GameplayCaptureSession.Settings(row.aspect().pixelWidth(), row.main(),
                row.followers(), row.donor(), donorRom, null, null);
        var session = new GameplayCaptureSession(settings);
        try {
            enterStage(session, settings, row, type);
            return session;
        } catch (Exception | Error e) {
            session.close();
            throw e;
        }
    }

    private static void enterStage(GameplayCaptureSession session, GameplayCaptureSession.Settings settings,
            CanonicalEntryMatrixSupport.Row row, BonusStageType type) throws Exception {
        GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
        for (int frame = 0; frame < 60; frame++) step(session, GameMode.LEVEL, null);
        GameServices.level().requestBonusStageEntry(type);
        for (int frame = 0; frame < 600; frame++) {
            session.step(NEUTRAL);session.renderFrame();
            if (session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE
                    && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion()) break;
        }
        assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
        assertFalse(GameServices.fade().isActive());assertFalse(GameServices.fade().hasPendingCompletion());
        var coordinator = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
        assertEquals(type, coordinator.getActiveType());assertTrue(coordinator.supportsRewind());
        assertEquals(row.aspect().pixelWidth(), GameServices.camera().getWidth() & 0xFFFF);
        assertRoster(row);
    }

    private void check(CanonicalEntryMatrixSupport.Row row, Mechanism mechanism) throws Exception {
        var donorRom = row.donor().equals("off") ? null : (row.donor().equals("s1")
                ? RomTestUtils.ensureSonic1RomAvailable() : RomTestUtils.ensureSonic2RomAvailable()).toPath();
        var settings = new GameplayCaptureSession.Settings(row.aspect().pixelWidth(), row.main(),
                row.followers(), row.donor(), donorRom, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            for (int frame = 0; frame < 60; frame++) step(session, GameMode.LEVEL, null);
            BonusStageType type = mechanism.name().startsWith("GUMBALL_")
                    ? BonusStageType.GUMBALL : BonusStageType.GLOWING_SPHERE;
            GameServices.level().requestBonusStageEntry(type);
            for (int frame = 0; frame < 600; frame++) {
                session.step(NEUTRAL);session.renderFrame();
                if (session.loop().getCurrentGameMode() == GameMode.BONUS_STAGE
                        && !GameServices.fade().isActive() && !GameServices.fade().hasPendingCompletion()) break;
            }
            assertEquals(GameMode.BONUS_STAGE, session.loop().getCurrentGameMode());
            assertFalse(GameServices.fade().isActive());assertFalse(GameServices.fade().hasPendingCompletion());
            var coordinator = assertInstanceOf(Sonic3kBonusStageCoordinator.class, GameServices.bonusStage());
            assertEquals(type, coordinator.getActiveType());assertTrue(coordinator.supportsRewind());
            var objects = GameServices.level().getObjectManager();
            assertEquals(row.aspect().pixelWidth(), GameServices.camera().getWidth() & 0xFFFF);
            assertEquals(row.main(), GameServices.sprites().getMainPlayable().characterKey().persisted());
            assertRoster(row);
            switch (mechanism) {
                case GUMBALL_EJECTION -> gumballEjection(session, objects, coordinator);
                case GUMBALL_SPRING_CHAIN -> gumballSpringChain(session, objects);
                case GUMBALL_BUMPER -> gumballBumper(session, objects);
                case PACHINKO_REWARD -> pachinkoReward(session, objects, coordinator);
                case PACHINKO_TRAP -> pachinkoTrap(session, objects);
            }
            assertRoster(row);
            SozAcceptanceConfigurations.assertDisplay(row.aspect());
        }
    }

    private static void assertRoster(CanonicalEntryMatrixSupport.Row row) {
        var main = GameServices.sprites().getMainPlayable();
        assertEquals(row.main(), main.characterKey().persisted());
        assertSame(main, GameServices.camera().getFocusedSprite());
        String[] expected = row.followers().isEmpty() ? new String[0] : row.followers().split(",");
        var actual = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(expected.length, actual.size(), "all configured bonus participants remain admitted");
        var identities = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<com.openggf.sprites.playable.AbstractPlayableSprite, Boolean>());
        identities.add(main);
        var previous = main;
        for (int index = 0; index < expected.length; index++) {
            var follower = actual.get(index);
            assertEquals(expected[index], follower.characterKey().persisted());
            assertTrue(identities.add(follower), "duplicate keys keep independent actors");
            assertTrue(follower.isCpuControlled());assertNotNull(follower.getCpuController());
            assertSame(previous, follower.getCpuController().getLeader(), "configured CPU leader order");
            previous = follower;
        }
        SozAcceptanceConfigurations.assertUsableTeam(row.donor());
    }

    private static void gumballEjection(GameplayCaptureSession session, ObjectManager objects,
            Sonic3kBonusStageCoordinator coordinator) throws Exception {
        var machine = objects.activeObjectsOfType(GumballMachineObjectInstance.class).getFirst();
        assertFalse(machine.isBit1Set(), "actual placed machine is idle before contact");
        place(machine.getX(), machine.getCurrentY());
        for (int frame = 0; frame < 4 && !machine.isBit1Set(); frame++) step(session, GameMode.BONUS_STAGE, objects);
        assertTrue(machine.isBit1Set(), "actual activation range owns spin");
        place(machine.getX() + 0x70, machine.getCurrentY());
        for (int frame = 0; frame < 120 && ejectedItem(objects) == null; frame++)
            step(session, GameMode.BONUS_STAGE, objects);
        var item = ejectedItem(objects);assertNotNull(item, "actual container created its native reward ball");
        var container = children(objects, "ContainerDisplayChild").getFirst();
        assertTrue(item.getSlotIndex() > container.getSlotIndex(),
                "loc_60E5C CreateChild6_Simple allocates after the container's SST");
        assertEquals(0x10, (int) field(field(item, "motionState"), "yVel"),
                "loc_60EBA falls through to loc_60ECE's first fall step in its birth pass");
        assertFalse((boolean) field(item, "dockEligible"), "fresh ball remains on its live parent3 dock");
        int subtype = item.getSpawn().subtype();
        assertTrue(subtype >= 0 && subtype <= 7);
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var live = registry.capture();
        if (subtype == 4) {
            // SYNTHETIC restore isolation, outside the native mechanic action:
            // public removal must recreate the live ball from the whole world.
            int ballSlot = item.getSlotIndex();
            var parent = assertInstanceOf(AbstractObjectInstance.class, field(item, "parentRef"));
            int parentSlot = parent.getSlotIndex();
            objects.removeDynamicObject(item);
            registry.restore(live);same(live, registry.capture(), "synthetic push-ball recreation");
            assertSame(objects, GameServices.level().getObjectManager());
            var recreated = ejectedItem(objects);assertNotNull(recreated);
            assertNotSame(item, recreated, "public removal requires a new actual ball reference");
            assertEquals(ballSlot, recreated.getSlotIndex());
            var recreatedParent = assertInstanceOf(AbstractObjectInstance.class, field(recreated, "parentRef"));
            assertEquals(parentSlot, recreatedParent.getSlotIndex());
            var rawRoster = (java.util.List<?>) field(objects, "dynamicObjects");
            assertTrue(rawRoster.stream().anyMatch(actor -> actor == recreated));
            assertTrue(rawRoster.stream().anyMatch(actor -> actor == recreatedParent));
            assertFalse(rawRoster.stream().anyMatch(actor -> actor == item));
        }
        // BEGIN GUMBALL_EXIT_OBSERVATION_CONTEXT
        var exitObservation = new GumballExitObservation(session, objects, coordinator);
        // END GUMBALL_EXIT_OBSERVATION_CONTEXT
        Runnable collectAndExpire = () -> {
            // BEGIN GUMBALL_EXIT_OBSERVATION_SCOPE
            var previousObservation = GUMBALL_EXIT_OBSERVATION.get();
            GUMBALL_EXIT_OBSERVATION.set(exitObservation);
            // END GUMBALL_EXIT_OBSERVATION_SCOPE
            try {
                var current = ejectedItem(objects);assertNotNull(current);
                // BEGIN GUMBALL_EXIT_OBSERVATION_BIND
                exitObservation.bindBall(current);
                // END GUMBALL_EXIT_OBSERVATION_BIND
                int rings = GameServices.sprites().getMainPlayable().getRingCount();
                int lives = GameServices.gameState().getLives();
                int rewardRings = coordinator.getRewards().rings();
                for (int frame = 0; frame < 64 && !(boolean) field(current, "dockEligible"); frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertTrue((boolean) field(current, "dockEligible"), "live parent/dock authority naturally releases");
                if (subtype == 4) assertFalse((boolean) field(current, "pushedPlayer"),
                        "declared contact must precede the actual push callback");
                place(current.getX() + 8, current.getY());
                step(session, GameMode.BONUS_STAGE, objects);
                if (subtype == 0) assertEquals(lives + 1, GameServices.gameState().getLives(), "loc_61120 life");
                if (subtype == 2) {
                    assertEquals(rings + 10, GameServices.sprites().getMainPlayable().getRingCount());
                    assertEquals(rewardRings + 20, coordinator.getRewards().rings());
                }
                if (subtype >= 5 && subtype <= 7) {
                    var type = subtype == 5 ? com.openggf.game.ShieldType.FIRE
                            : subtype == 6 ? com.openggf.game.ShieldType.BUBBLE : com.openggf.game.ShieldType.LIGHTNING;
                    assertEquals(type, GameServices.sprites().getMainPlayable().getShieldType(), "native shield pickup");
                }
                if (subtype == 4) {
                    assertTrue((boolean) field(current, "pushedPlayer"), "the actual native push callback ran");
                    assertFalse(current.isDestroyed(), "native push retains the ball");
                    assertTrue(GameServices.sprites().getMainPlayable().getXSpeed() != 0
                            || GameServices.sprites().getMainPlayable().getYSpeed() != 0);
                    assertFalse((boolean) field(current, "collected"), "loc_6116E clears d2 and keeps the push ball");
                    assertSame(current, ejectedItem(objects), "the pushed ball remains in the same live roster");
                    // This same-world boundary is push-and-survive. Independent
                    // loc_61050 exit may precede bottom expiry; that is not covered here.
                } else {
                    assertTrue((boolean) field(current, "collected"), "actual proximity dispatch collected the reward");
                    for (int frame = 0; frame < 4 && !current.isDestroyed(); frame++)
                        step(session, GameMode.BONUS_STAGE, objects);
                    assertTrue(current.isDestroyed(), "natural native collect/bottom expiry retires the reward");
                    step(session, GameMode.BONUS_STAGE, objects);
                }
            } catch (Exception e) { throw new AssertionError(e); }
            // BEGIN GUMBALL_EXIT_OBSERVATION_RESTORE
            finally {
                if (previousObservation == null) GUMBALL_EXIT_OBSERVATION.remove();
                else GUMBALL_EXIT_OBSERVATION.set(previousObservation);
            }
            // END GUMBALL_EXIT_OBSERVATION_RESTORE
        };
        replayTwice(live, collectAndExpire, subtype == 4
                ? "actual ejected push survival/recreation" : "actual ejected reward expiry/recreation");
    }

    private static java.util.List<AbstractObjectInstance> children(ObjectManager objects, String name) {
        return objects.getActiveObjects().stream().filter(object -> object.getClass().getSimpleName().equals(name))
                .map(AbstractObjectInstance.class::cast).toList();
    }

    private static void gumballSpringChain(GameplayCaptureSession session, ObjectManager objects) throws Exception {
        var springs = children(objects, "GumballSpringChild");
        assertEquals(4, springs.size(), "actual native dispenser owns four placed springs");
        assertEquals(1, children(objects, "DispenserChild").size());
        int firstSlot = springs.getFirst().getSlotIndex();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var idle = registry.capture();
        Runnable contactAndExpire = () -> {
            try {
                var current = children(objects, "GumballSpringChild");
                var spring = current.stream().filter(object -> object.getSlotIndex() == firstSlot).findFirst().orElseThrow();
                place(spring.getX(), spring.getY() - 40);
                for (int frame = 0; frame < 30 && !(boolean) field(spring, "triggered"); frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertTrue((boolean) field(spring, "triggered"), "actual SolidObjectFull2 contact triggers P1 crumble");
                assertTrue(GameServices.sprites().getMainPlayable().getYSpeed() < 0, "native spring bounce is upward");
                place(0x180, 0x260);
                for (int frame = 0; frame < 24 && !children(objects, "DispenserChild").isEmpty(); frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertTrue(children(objects, "DispenserChild").isEmpty(), "native spring bit retires the dispenser");
                for (var sibling : current) assertTrue((boolean) field(sibling, "falling"),
                        "native dispenser deletion starts every sibling's natural fall");
                assertFalse(children(objects, "EjectionEffectChild").isEmpty(), "actual dispenser delete owns its debris graph");
                step(session, GameMode.BONUS_STAGE, objects);
            } catch (Exception e) { throw new AssertionError(e); }
        };
        replayTwice(idle, contactAndExpire, "actual spring/dispenser deletion and sibling-fall recreation");
    }

    private static void gumballBumper(GameplayCaptureSession session, ObjectManager objects) throws Exception {
        var bumper = objects.activeObjectsOfType(GumballTriangleBumperObjectInstance.class).getFirst();
        assertFalse((boolean) field(bumper, "consumed"));
        int slot = bumper.getSlotIndex();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var idle = registry.capture();
        Runnable contactAndRetire = () -> {
            try {
                var current = objects.activeObjectsOfType(GumballTriangleBumperObjectInstance.class).stream()
                        .filter(object -> object.getSlotIndex() == slot).findFirst().orElseThrow();
                place(current.getX(), current.getY() - 40);
                for (int frame = 0; frame < 30 && !(boolean) field(current, "consumed"); frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertTrue((boolean) field(current, "consumed"), "actual solid contact owns native bumper dispatch");
                assertTrue(GameServices.sprites().getMainPlayable().getYSpeed() < 0);
                for (int frame = 0; frame < 3 && !current.isDestroyed(); frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertTrue(current.isDestroyed(), "loc_60F64/loc_60F80 clears the contacted native bumper SST");
                step(session, GameMode.BONUS_STAGE, objects);
            } catch (Exception e) { throw new AssertionError(e); }
        };
        replayTwice(idle, contactAndRetire, "actual triangular bumper contact/deletion/recreation");
    }

    private static GumballItemObjectInstance ejectedItem(ObjectManager objects) throws Exception {
        for (var item : objects.activeObjectsOfType(GumballItemObjectInstance.class))
            if (field(item, "motionMode").toString().equals("GUMBALL_EJECT")) return item;
        return null;
    }

    private static void pachinkoReward(GameplayCaptureSession session, ObjectManager objects,
            Sonic3kBonusStageCoordinator coordinator) throws Exception {
        var orb = objects.activeObjectsOfType(PachinkoItemOrbObjectInstance.class).stream()
                .min(java.util.Comparator.comparingInt(object -> Math.abs(object.getY()
                        - GameServices.sprites().getMainPlayable().getCentreY()))).orElseThrow();
        assertNull(field(orb, "rewardItem"));
        place(orb.getX(), orb.getY());
        for (int frame = 0; frame < 8 && !(boolean) field(orb, "armed"); frame++)
            step(session, GameMode.BONUS_STAGE, objects);
        assertTrue((boolean) field(orb, "armed"), "actual special touch arms the placed orb");
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var armed = registry.capture();
        int slot = orb.getSlotIndex();
        Runnable releaseAndConvert = () -> {
            try {
                var current = orbAt(objects, slot);
                assertNull(field(current, "rewardItem"), "reverse restore retires the later same-SST reward");
                assertEquals(0xED, current.getSpawn().objectId(), "original orb owns pre-conversion state");
                place(current.getX() + 0x50, current.getY());
                for (int frame = 0; frame < 8 && field(current, "rewardItem") == null; frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                var reward = assertInstanceOf(GumballItemObjectInstance.class, field(current, "rewardItem"));
                assertEquals(0xEB, reward.getSpawn().objectId());
            } catch (Exception e) { throw new AssertionError(e); }
        };
        replayTwice(armed, releaseAndConvert, "actual armed orb to same-SST reward conversion");
        var converted = registry.capture();
        Runnable collectAndExpire = () -> {
            try {
                var current = orbAt(objects, slot);
                var item = assertInstanceOf(GumballItemObjectInstance.class, field(current, "rewardItem"),
                        "native same-SST converted reward survives complete restoration");
                place(item.getX(), item.getY());
                for (int frame = 0; frame < 12 && !current.isDestroyed(); frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertTrue(current.isDestroyed(), "actual reward touch/next dispatch clears the native SST");
                step(session, GameMode.BONUS_STAGE, objects);
            } catch (Exception e) { throw new AssertionError(e); }
        };
        replayTwice(converted, collectAndExpire, "actual converted reward expiry/recreation");
    }

    private static PachinkoItemOrbObjectInstance orbAt(ObjectManager objects, int slot) {
        return objects.activeObjectsOfType(PachinkoItemOrbObjectInstance.class).stream()
                .filter(object -> object.getSlotIndex() == slot).findFirst().orElseThrow();
    }

    private static void pachinkoTrap(GameplayCaptureSession session, ObjectManager objects) throws Exception {
        var trap = objects.activeObjectsOfType(PachinkoEnergyTrapObjectInstance.class).getFirst();
        assertFalse(trap.isExitArmed());
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var free = registry.capture();
        Runnable capture = () -> {
            place(0x100, trap.getY());
            for (int frame = 0; frame < 18; frame++) step(session, GameMode.BONUS_STAGE, objects);
            assertTrue(trap.isExitArmed());assertTrue(trap.hasCapturedPlayer());
            assertFalse(trap.isExitRequested(), "window is bounded before actual exit/callback");
            assertTrue(GameServices.sprites().getMainPlayable().isObjectControlled());
            assertEquals(trap.getY(), GameServices.sprites().getMainPlayable().getCentreY());
        };
        replayTwice(free, capture, "actual trap control/countdown capture");
    }

    private static void replayTwice(CompositeSnapshot before, Runnable action, String label) {
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        action.run();var after = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);same(before, registry.capture(), label + " before restore");
            action.run();same(after, registry.capture(), label + " forward replay");
            registry.restore(before);same(before, registry.capture(), label + " reverse restore");
            registry.restore(after);same(after, registry.capture(), label + " after restore");
        }
    }
    private static void same(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        TestS3kCanonicalEntryLifecycle.assertSameWorld(expected, actual, label, 18);
    }
    private static void place(int x, int y) {
        var main = GameServices.sprites().getMainPlayable();
        assertFalse(main.isObjectControlled(), "declared local contact starts with free native control");
        NativePositionOps.writeXPosResetSubpixel(main, x);NativePositionOps.writeYPosResetSubpixel(main, y);
        main.setXSpeed((short) 0);main.setYSpeed((short) 0);main.setGSpeed((short) 0);
        main.setAir(true);main.setJumping(false);
    }
    private static void step(GameplayCaptureSession session, GameMode mode, ObjectManager objects) {
        int lives = GameServices.gameState().getLives();
        // BEGIN GUMBALL_EXIT_OBSERVATION_PRE
        var exitObservation = GUMBALL_EXIT_OBSERVATION.get();
        if (exitObservation != null) exitObservation.observe("PRE");
        // END GUMBALL_EXIT_OBSERVATION_PRE
        session.step(NEUTRAL);session.renderFrame();
        // BEGIN GUMBALL_EXIT_OBSERVATION_POST
        if (exitObservation != null) exitObservation.observe("POST");
        // END GUMBALL_EXIT_OBSERVATION_POST
        assertEquals(mode, session.loop().getCurrentGameMode(), exitObservation == null
                ? "bounded same-world mechanic window" : exitObservation.message());
        if (objects != null) assertSame(objects, GameServices.level().getObjectManager());
        assertFalse(GameServices.sprites().getMainPlayable().getDead());
        assertTrue(GameServices.gameState().getLives() >= lives, "no hidden life-loss reload");
    }
    // BEGIN GUMBALL_EXIT_OBSERVATION: test-local retained state, no exit predicates or gameplay calls.
    private static final ThreadLocal<GumballExitObservation> GUMBALL_EXIT_OBSERVATION = new ThreadLocal<>();
    private static final class GumballExitObservation {
        private static final String PREFIX = "bounded same-world mechanic window; ";
        private final GameplayCaptureSession session;
        private final ObjectManager retainedObjects;
        private final Sonic3kBonusStageCoordinator coordinator;
        private final java.util.IdentityHashMap<Object, Integer> identities = new java.util.IdentityHashMap<>();
        private GumballItemObjectInstance ball;
        private Object retainedMode, retainedWorld, retainedManager, retainedLevel, retainedCamera, trigger;
        private com.openggf.sprites.playable.AbstractPlayableSprite player;
        private Class<?> triggerType;
        private boolean ballNeedsBinding;
        private long action, samples;
        private String firstInvalid, firstExitChange, lastPre, lastPost;
        private ExitState previousExit;
        private GumballExitObservation(GameplayCaptureSession session, ObjectManager objects,
                Sonic3kBonusStageCoordinator coordinator) {
            this.session = session;this.retainedObjects = objects;this.coordinator = coordinator;
        }
        private void bindBall(GumballItemObjectInstance actual) {
            ball = actual;ballNeedsBinding = true;trigger = null;previousExit = null;action++;
        }
        private void observe(String phase) {
            if (firstInvalid != null) return;
            try {
                samples++;
                var snapshot = sample(phase);
                if (firstExitChange == null && previousExit != null && !previousExit.same(snapshot.exit()))
                    firstExitChange = "first observed boundary change from{" + exitText(previousExit) + "} to{"
                            + exitText(snapshot.exit()) + "}; " + snapshot.text();
                previousExit = snapshot.exit();
                if (phase.equals("PRE")) lastPre = snapshot.text();else lastPost = snapshot.text();
                if (retainedMessage().length() > 8192) throw new IllegalStateException("8192-character diagnostic budget");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                invalidate(failure);
            }
        }
        private void invalidate(Throwable failure) {
            if (firstInvalid != null) return;
            String detail = failure.getMessage();
            firstInvalid = failure.getClass().getSimpleName() + ": "
                    + (detail == null ? "no detail" : detail.substring(0, Math.min(256, detail.length())));
        }
        /** This assertion-message path reads retained text only, including after an original failure. */
        private String message() {
            return firstInvalid == null ? retainedMessage() : PREFIX + "DIAG_INVALID " + firstInvalid;
        }
        private String retainedMessage() {
            return PREFIX + "firstExitChange=" + (firstExitChange == null ? "NOT_OBSERVED" : firstExitChange)
                    + "\nlastPRE=" + (lastPre == null ? "NOT_OBSERVED" : lastPre)
                    + "\nlastPOST=" + (lastPost == null ? "NOT_OBSERVED" : lastPost);
        }
        private Snapshot sample(String phase) throws ReflectiveOperationException {
            Object mode = SessionManager.getCurrentGameplayMode();
            Object world = SessionManager.getCurrentWorldSession();
            Object manager = raw(mode, "levelManager");
            Object level = raw(manager, "level");
            Object objects = raw(manager, "objectManager");
            Object cameraObject = raw(mode, "camera");
            if (!(cameraObject instanceof com.openggf.camera.Camera camera))
                throw new IllegalStateException("current camera binding");
            Object focus = raw(camera, "focusedSprite");
            var dynamic = rawList(raw(retainedObjects, "dynamicObjects"));
            var currentDynamic = rawList(raw(objects, "dynamicObjects"));
            if (retainedMode == null) {
                retainedMode = mode;retainedWorld = world;retainedManager = manager;retainedLevel = level;retainedCamera = camera;
                if (!(focus instanceof com.openggf.sprites.playable.AbstractPlayableSprite actual)
                        || !Boolean.FALSE.equals(raw(actual, "cpuControlled")))
                    throw new IllegalStateException("original camera-focused P1 binding");
                player = actual;
            }
            if (ball == null || (ballNeedsBinding && identityIndex(dynamic, ball) < 0))
                throw new IllegalStateException("existing ejectedItem result is not a raw dynamic member");
            ballNeedsBinding = false;
            if (triggerType == null) {
                for (Class<?> nested : GumballMachineObjectInstance.class.getDeclaredClasses())
                    if (nested.getName().equals("com.openggf.game.sonic3k.objects.GumballMachineObjectInstance$ExitTriggerChild"))
                        triggerType = nested;
                if (triggerType == null) throw new IllegalStateException("exact ExitTriggerChild type missing");
            }
            Object memberTrigger = null;int triggerCount = 0;
            for (Object actor : dynamic) if (actor != null && actor.getClass() == triggerType) {
                memberTrigger = actor;triggerCount++;
            }
            if (triggerCount > 1 || (triggerCount == 0 && trigger == null))
                throw new IllegalStateException("raw ExitTriggerChild binding count=" + triggerCount);
            if (memberTrigger != null) trigger = memberTrigger; // Recreated actors bind only through actual raw membership.
            var motion = raw(ball, "motionState");
            if (!(motion instanceof com.openggf.level.objects.SubpixelMotion.State coordinates))
                throw new IllegalStateException("actual ball motion state type");
            Object loop = raw(session, "loop");
            Object provider = raw(loop, "activeBonusStageProvider");
            var exit = new ExitState(trigger, (boolean) raw(trigger, "exitFired"),
                    (boolean) raw(trigger, "player2Deleted"), (boolean) raw(loop, "bonusStageTransitionPending"),
                    provider, (boolean) raw(coordinator, "exitRequested"), raw(coordinator, "activeType"),
                    raw(loop, "currentGameMode"));
            Object spawnValue = raw(trigger, "spawn");
            if (!(spawnValue instanceof com.openggf.level.objects.ObjectSpawn spawn))
                throw new IllegalStateException("actual exit placement type");
            var text = new ObservationText();
            text.add(phase + " action=" + action + " sample=" + samples + " retainedWorld=" + ref(retainedWorld)
                    + " currentWorld=" + ref(world) + " retainedMode=" + ref(retainedMode) + " currentMode=" + ref(mode)
                    + " modeWorld=" + ref(raw(mode, "worldSession")) + " loopMode=" + ref(raw(loop, "gameplayMode"))
                    + " retainedLevelManager=" + ref(retainedManager) + " currentLevelManager=" + ref(manager)
                    + " retainedLevel=" + ref(retainedLevel) + " currentLevel=" + ref(level)
                    + " worldLevel=" + ref(raw(world, "currentLevel")) + " retainedOM=" + ref(retainedObjects)
                    + " currentOM=" + ref(objects) + " rawDynamicCounts=" + dynamic.size() + "/" + currentDynamic.size());
            text.add("; ball=" + ref(ball) + " slot=" + scalar(raw(ball, "slotIndex")) + " subtype=" + scalar(raw(ball, "subtype"))
                    + " motion=" + ref(motion) + " mode=" + scalar(raw(ball, "motionMode"))
                    + " motion[x,y,xSub,ySub,xVel,yVel]=[" + coordinates.x + "," + coordinates.y + "," + coordinates.xSub + ","
                    + coordinates.ySub + "," + coordinates.xVel + "," + coordinates.yVel + "] dockEligible="
                    + scalar(raw(ball, "dockEligible")) + " collected=" + scalar(raw(ball, "collected"))
                    + " destroyed=" + scalar(raw(ball, "destroyed")) + " parent=" + ref(raw(ball, "parentRef"))
                    + " rawDynamicIndexes=" + identityIndex(dynamic, ball) + "/" + identityIndex(currentDynamic, ball));
            text.add("; retainedP1=" + ref(player) + " centre=[" + player.getCentreX() + "," + player.getCentreY() + "] vel=[" + player.getXSpeed()
                    + "," + player.getYSpeed() + "," + player.getGSpeed() + "] Air=" + player.getAir()
                    + " dead=" + player.getDead() + " objectControlled=" + player.isObjectControlled()
                    + " controlLocked=" + player.isControlLocked() + " routine=" + scalar(raw(player, "objectRoutineOverride"))
                    + " allowsCpu=" + scalar(raw(player, "objectControlAllowsCpu")) + " suppressesMovement="
                    + scalar(raw(player, "objectControlSuppressesMovement")) + " retainedCamera=" + ref(retainedCamera)
                    + " currentCamera=" + ref(camera) + " cameraXY=[" + camera.getX() + "," + camera.getY()
                    + "] focus=" + ref(focus) + " focusIsRetainedP1=" + (focus == player));
            text.add("; exit=" + ref(trigger) + " rawMemberCount=" + triggerCount + " rawDynamicIndexes="
                    + identityIndex(dynamic, trigger) + "/" + identityIndex(currentDynamic, trigger)
                    + " slot=" + scalar(raw(trigger, "slotIndex")) + " destroyed=" + scalar(raw(trigger, "destroyed"))
                    + " spawn=[" + spawn.x() + "," + spawn.y() + "," + spawn.objectId() + "," + spawn.subtype()
                    + "," + spawn.renderFlags() + "," + spawn.rawYWord() + "," + spawn.layoutIndex() + "] "
                    + exitText(exit) + " providerIsCoordinator=" + (provider == coordinator));
            return new Snapshot(text.text.toString(), exit);
        }
        private String exitText(ExitState exit) {
            return "trigger=" + ref(exit.trigger()) + " exitFired=" + exit.fired() + " player2Deleted=" + exit.player2Deleted()
                    + " transitionPending=" + exit.pending() + " provider=" + ref(exit.provider())
                    + " coordinator=" + ref(coordinator) + " exitRequested=" + exit.requested()
                    + " activeType=" + scalar(exit.activeType()) + " loopMode=" + scalar(exit.mode());
        }
        private String ref(Object value) {
            if (value == null) return "null";
            Integer id = identities.get(value);
            if (id == null) {
                if (identities.size() >= 512) throw new IllegalStateException("reference budget");
                id = identities.size() + 1;identities.put(value, id);
            }
            return "r" + id;
        }
        private static Object raw(Object target, String name) throws ReflectiveOperationException {
            if (target == null) throw new IllegalStateException("missing owner for " + name);
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    var field = type.getDeclaredField(name);field.setAccessible(true);return field.get(target);
                } catch (NoSuchFieldException absent) { /* Continue only through the actual inheritance chain. */ }
            }
            throw new NoSuchFieldException(name);
        }
        private static String scalar(Object value) {
            if (value == null) return "null";
            if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                    || value instanceof Short || value instanceof Byte) return value.toString();
            if (value instanceof Enum<?> choice) return choice.name();
            throw new IllegalStateException("non-scalar diagnostic field");
        }
        private static java.util.List<?> rawList(Object value) {
            if (!(value instanceof java.util.List<?> result) || result.size() > 512)
                throw new IllegalStateException("raw dynamic list type/size");
            return result;
        }
        private static int identityIndex(java.util.List<?> values, Object target) {
            for (int index = 0; index < values.size(); index++) if (values.get(index) == target) return index;
            return -1;
        }
        private record Snapshot(String text, ExitState exit) { }
        private record ExitState(Object trigger, boolean fired, boolean player2Deleted, boolean pending,
                Object provider, boolean requested, Object activeType, Object mode) {
            private boolean same(ExitState other) {
                return trigger == other.trigger && fired == other.fired && player2Deleted == other.player2Deleted
                        && pending == other.pending && provider == other.provider && requested == other.requested
                        && activeType == other.activeType && mode == other.mode;
            }
        }
        private static final class ObservationText {
            private final StringBuilder text = new StringBuilder();
            private void add(String value) {
                if (value.length() > 2048 - text.length()) throw new IllegalStateException("retained snapshot budget");
                text.append(value);
            }
        }
    }
    // END GUMBALL_EXIT_OBSERVATION
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
}
