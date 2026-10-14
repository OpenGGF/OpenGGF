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

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production bonus reward contacts and natural lifetimes from the S3K parity task.
 * Logical entry and local free-player contacts are declared fixtures; no reward,
 * timer, RNG, delegate or exit flags are written. This is not a cold portal route.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kGumballPachinkoRewardMechanics {
    enum Mechanism { GUMBALL_EJECTION, PACHINKO_REWARD, PACHINKO_TRAP }
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

    private void check(CanonicalEntryMatrixSupport.Row row, Mechanism mechanism) throws Exception {
        var donorRom = row.donor().equals("off") ? null : (row.donor().equals("s1")
                ? RomTestUtils.ensureSonic1RomAvailable() : RomTestUtils.ensureSonic2RomAvailable()).toPath();
        var settings = new GameplayCaptureSession.Settings(row.aspect().pixelWidth(), row.main(),
                row.followers(), row.donor(), donorRom, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 8, 0, settings);
            for (int frame = 0; frame < 60; frame++) step(session, GameMode.LEVEL, null);
            BonusStageType type = mechanism == Mechanism.GUMBALL_EJECTION
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
            switch (mechanism) {
                case GUMBALL_EJECTION -> gumballEjection(session, objects, coordinator);
                case PACHINKO_REWARD -> pachinkoReward(session, objects, coordinator);
                case PACHINKO_TRAP -> pachinkoTrap(session, objects);
            }
        }
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
