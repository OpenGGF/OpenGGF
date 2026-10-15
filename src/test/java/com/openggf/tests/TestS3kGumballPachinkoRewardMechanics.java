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
    void actualFiniteSupportedProductsReplayEveryMechanism(CanonicalEntryMatrixSupport.Row row,
            Mechanism mechanism) throws Exception {
        check(row, mechanism);
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
        Runnable collectAndExpire = () -> {
            try {
                var current = ejectedItem(objects);assertNotNull(current);
                int rings = GameServices.sprites().getMainPlayable().getRingCount();
                int lives = GameServices.gameState().getLives();
                int rewardRings = coordinator.getRewards().rings();
                for (int frame = 0; frame < 64 && !(boolean) field(current, "dockEligible"); frame++)
                    step(session, GameMode.BONUS_STAGE, objects);
                assertTrue((boolean) field(current, "dockEligible"), "live parent/dock authority naturally releases");
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
                    assertFalse(current.isDestroyed(), "native push retains the ball");
                    assertTrue(GameServices.sprites().getMainPlayable().getXSpeed() != 0
                            || GameServices.sprites().getMainPlayable().getYSpeed() != 0);
                    var currentMachine = objects.activeObjectsOfType(GumballMachineObjectInstance.class).getFirst();
                    place(currentMachine.getX() + 0x70, currentMachine.getCurrentY());
                    for (int frame = 0; frame < 360 && !current.isDestroyed(); frame++)
                        step(session, GameMode.BONUS_STAGE, objects);
                } else {
                    assertTrue((boolean) field(current, "collected"), "actual proximity dispatch collected the reward");
                    for (int frame = 0; frame < 4 && !current.isDestroyed(); frame++)
                        step(session, GameMode.BONUS_STAGE, objects);
                }
                assertTrue(current.isDestroyed(), "natural native collect/bottom expiry retires the reward");
                step(session, GameMode.BONUS_STAGE, objects);
            } catch (Exception e) { throw new AssertionError(e); }
        };
        replayTwice(live, collectAndExpire, "actual ejected reward expiry/recreation");
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
        session.step(NEUTRAL);session.renderFrame();
        assertEquals(mode, session.loop().getCurrentGameMode(), "bounded same-world mechanic window");
        if (objects != null) assertSame(objects, GameServices.level().getObjectManager());
        assertFalse(GameServices.sprites().getMainPlayable().getDead());
        assertTrue(GameServices.gameState().getLives() >= lives, "no hidden life-loss reload");
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
}
