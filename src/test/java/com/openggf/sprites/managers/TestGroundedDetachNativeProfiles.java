package com.openggf.sprites.managers;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic2.kis2.Kis2GamePatch;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Short real-owner witnesses; component inlets, not complete routes or native pixel parity. */
@Isolated
class TestGroundedDetachNativeProfiles {
    @AfterEach void tearDown() { TestEnvironment.resetAll(); }

    @ParameterizedTest(name = "{0}/{1}/donor={2}")
    @CsvSource({"s1,sonic,off,false", "s2,sonic,off,false", "s2,tails,off,false", "s3k,sonic,off,true",
            "s3k,tails,off,true", "s3k,knuckles,off,true", "s2,knuckles,off,false", "s1,knuckles,s3k,false"})
    void nativeGroundSelectionsAndOwners(String host, String character, String donor, boolean downFirst) throws Exception {
        File rom = switch (host) {
            case "s1" -> RomTestUtils.ensureSonic1RomAvailable();
            case "s2" -> RomTestUtils.ensureSonic2RomAvailable();
            default -> RomTestUtils.ensureSonic3kRomAvailable();
        };
        assumeTrue(rom != null && rom.isFile(), "missing original " + host + " ROM");
        File sk = RomTestUtils.ensureSonic3kRomAvailable();
        boolean kis2 = host.equals("s2") && character.equals("knuckles");
        if (kis2 || !donor.equals("off")) {
            assumeTrue(sk != null && sk.isFile(), "missing original S3&K ROM for character assets");
        }
        if (kis2) {
            String lockOn = System.getProperty("kis2.rom.path");
            assumeTrue(lockOn != null && new File(lockOn).isFile(), "missing original KiS2 lock-on ROM");
            // The production catalogue resolves the built-in patch's physical chip window.
            GameServices.configuration().setConfigValue(SonicConfiguration.ROMS_DIRECTORY,
                    Path.of(lockOn).toAbsolutePath().getParent().toString());
            GameServices.configuration().setConfigValue(SonicConfiguration.SONIC_3K_ROM, sk.getAbsolutePath());
        }
        var settings = new GameplayCaptureSession.Settings(320, character, "", donor,
                donor.equals("off") ? null : sk.toPath(), null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(rom.toPath(), 0, 0, settings);
            if (kis2) {
                var module = assertInstanceOf(DelegatingGameModule.class, SessionManager.requireCurrentGameModule());
                assertEquals(Kis2GamePatch.ID, module.patchId());
            }
            if (!donor.equals("off")) assertTrue(CrossGameFeatureProvider.isActive());
            var player = session.player();
            assertEquals(character, player.getCode());
            var profile = assertInstanceOf(ScriptedVelocityAnimationProfile.class, player.getAnimationProfile());
            prepare(player, profile.getWalkAnimId());
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var inlet = registry.capture();
            // Independent native expectations above also check the resolved host provider.
            assertEquals(downFirst, com.openggf.game.internal.StandingInputPolicy.forHost(player.currentGameModule())
                    == com.openggf.game.internal.StandingInputPolicy.DOWN_FIRST);
            boolean lateDownWrite = player.getGameRules().playerMovement().groundPose().movingCrouchThreshold() > 0;
            assertAll(
                    () -> checkSelection(registry, inlet, false, false, profile.getIdleAnimId()),
                    () -> checkSelection(registry, inlet, false, true, profile.getDuckAnimId()),
                    () -> checkSelection(registry, inlet, true, false, profile.getLookUpAnimId()),
                    () -> checkSelection(registry, inlet, true, true,
                            downFirst ? profile.getDuckAnimId() : profile.getLookUpAnimId()),
                    () -> {
                        registry.restore(inlet);
                        var p = GameServices.sprites().getMainPlayable();
                        // Directly exercise the early production entry with a live override:
                        // ordinary grounded dispatch expires stale unlocked overrides before it.
                        p.setCrouching(true);
                        p.setAnimationId(profile.getRollAnimId());
                        p.setForcedAnimationId(profile.getRollAnimId());
                        p.getAnimationManager().publishGroundPoseAnimation(1);
                        assertEquals(profile.getRollAnimId(), p.getAnimationId(), "forced owner at early entry");
                        // A native lock keeps that override live through the real movement call.
                        p.setMoveLockTimer(1);
                        movement(p, false, true);
                        assertEquals(profile.getRollAnimId(), p.getForcedAnimationId(), "force reached early publication");
                        assertEquals(profile.getRollAnimId(), p.getAnimationId(), "forced owner after movement");
                        p.getAnimationManager().update(1);
                        assertEquals(profile.getRollAnimId(), p.getAnimationId(), "forced animation owns publication");
                    },
                    () -> {
                        registry.restore(inlet);
                        var p = GameServices.sprites().getMainPlayable();
                        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(p);
                        p.setAnimationId(profile.getLookUpAnimId());
                        tick(p, false, true);
                        assertFalse(p.getAir(), "object control skips terrain movement");
                        assertFalse(p.getAnimationManager().hasGroundMovementAnimSpeed());
                        assertEquals(profile.getLookUpAnimId(), p.getAnimationId(), "object-owned byte survives");
                    },
                    () -> {
                        registry.restore(inlet);
                        var p = GameServices.sprites().getMainPlayable();
                        // Independent vertical-wall inlet: Move's flat-angle gate
                        // rejects Wait, while SlopeRepel owns the later detach/lock.
                        p.setStickToConvex(false);
                        p.setAngle((byte) 0xC0);
                        p.setGroundMode(GroundMode.RIGHTWALL);
                        p.setAnimationId(profile.getWalkAnimId());
                        tick(p, false, false);
                        assertTrue(p.getAir());
                        assertTrue(p.getMoveLockTimer() > 0);
                        assertEquals(profile.getWalkAnimId(), p.getAnimationId(),
                                "slope detachment must retain the incoming byte when Move writes no standing pose");
                        checkContinuationAndRecreatedReplay(registry, p, profile.getWalkAnimId());
                    },
                    () -> checkMoveLock(registry, inlet, false, profile.getLookUpAnimId()),
                    () -> checkMoveLock(registry, inlet, true,
                            lateDownWrite ? profile.getDuckAnimId() : profile.getLookUpAnimId()),
                    () -> {
                        registry.restore(inlet);
                        var p = GameServices.sprites().getMainPlayable();
                        p.setMoveLockTimer(1);
                        p.setAnimationId(profile.getLookUpAnimId());
                        p.captureOnObjectAtFrameStart();
                        p.getMovementManager().handleMovement(false, true, true, false,
                                false, false, false, false);
                        p.getAnimationManager().update(1);
                        assertTrue(p.getAir());
                        assertEquals(profile.getLookUpAnimId(), p.getAnimationId(),
                                "held Left gates the later Roll Duck write despite move_lock filtering");
                        checkContinuationAndRecreatedReplay(registry, p, profile.getLookUpAnimId());
                    },
                    () -> checkPowerProfiles(registry, inlet, profile, downFirst));
        }
    }

    private static void checkPowerProfiles(RewindRegistry registry, CompositeSnapshot inlet,
            ScriptedVelocityAnimationProfile profile, boolean downFirst) {
        // These are installed-speed component controls, not transformation-route certification.
        // The host provider must survive shoes and replacement Super tuning without deriving
        // standing precedence from speed values or the later Roll Duck threshold.
        for (boolean shoes : new boolean[] {true, false}) {
            for (boolean[] input : new boolean[][] {{true, false}, {false, true}, {true, true}}) {
                registry.restore(inlet);
                var p = GameServices.sprites().getMainPlayable();
                if (shoes) {
                    p.giveSpeedShoes();
                    assertTrue(p.hasSpeedShoes());
                } else {
                    var tuning = switch (p.getCode()) {
                        case "tails" -> com.openggf.game.PhysicsProfile.SONIC_3K_SUPER_TAILS;
                        case "knuckles" -> com.openggf.game.PhysicsProfile.SONIC_3K_SUPER_KNUCKLES;
                        default -> com.openggf.game.PhysicsProfile.SONIC_3K_SUPER_SONIC;
                    };
                    p.applyExternalPhysicsProfile(tuning);
                    p.setSuperSonic(true);
                    assertEquals(tuning.max(), p.getMax());
                }
                int expected = input[0] && !(input[1] && downFirst)
                        ? profile.getLookUpAnimId() : profile.getDuckAnimId();
                movement(p, input[0], input[1]);
                assertEquals(expected, p.getAnimationId(), "power tuning at early publication");
                p.getAnimationManager().update(1);
                assertTrue(p.getAir());
                assertEquals(expected, p.getAnimationId());
                tick(p, false, false);
                assertAirContinuation(p, expected);
            }
        }
    }

    private static void checkMoveLock(RewindRegistry registry,
            com.openggf.game.rewind.CompositeSnapshot inlet, boolean down, int expected) {
        for (int lock : new int[] {1, 2}) {
            registry.restore(inlet);
            var p = GameServices.sprites().getMainPlayable();
            p.setMoveLockTimer(lock);
            p.setAnimationId(((ScriptedVelocityAnimationProfile) p.getAnimationProfile()).getLookUpAnimId());
            tick(p, false, down);
            assertTrue(p.getAir());
            assertEquals(lock - 1, p.getMoveLockTimer());
            assertTrue(p.getAnimationManager().isGroundMovementAnimationSuppressed());
            assertEquals(expected, p.getAnimationId(), "move_lock ground dispatch: down=" + down);
            checkContinuationAndRecreatedReplay(registry, p, expected);
        }
    }

    private static void checkSelection(com.openggf.game.rewind.RewindRegistry registry,
            com.openggf.game.rewind.CompositeSnapshot inlet, boolean up, boolean down, int expected) {
        registry.restore(inlet);
        var p = GameServices.sprites().getMainPlayable();
        movement(p, up, down);
        assertEquals(expected, p.getAnimationId(), "Move/Roll publish before the animation consumer");
        p.getAnimationManager().update(1);
        String input = "up=" + up + ",down=" + down;
        assertTrue(p.getAir(), "unsupported inlet must detach: " + input);
        assertFalse(p.getAirAtFrameStart());
        assertTrue(p.getAnimationManager().hasGroundMovementAnimSpeed());
        assertEquals(expected, p.getAnimationId(), "ground dispatch selection: " + input);
        checkContinuationAndRecreatedReplay(registry, p, expected);
    }

    private static void checkContinuationAndRecreatedReplay(RewindRegistry registry,
            AbstractPlayableSprite p, int expected) {
        var detached = registry.capture();
        tick(p, false, false);
        assertAirContinuation(p, expected);
        var airborne = registry.capture();
        // Configured players are not automatically recreated by SpriteManager.restore.
        // Recreate through the active character definition, wire real ROM art, then
        // restore every registered key onto that fresh player/controller.
        var replacement = SessionManager.requireCurrentGameModule().getPlayableCharacterRegistry()
                .find(p.characterKey()).orElseThrow().spriteFactory()
                .create(p.getCode(), p.getX(), p.getY());
        assertNotSame(p, replacement);
        assertNotSame(p.getAnimationManager(), replacement.getAnimationManager());
        GameServices.sprites().addSprite(replacement);
        PlayableSpriteInternalAccess.bindNativeSpeedOwner(replacement, GameServices.level().getWorldSession());
        replacement.setPowerUpSpawner(p.getPowerUpSpawner());
        GameServices.level().refreshPlayableSpriteArt();
        registry.restore(detached);
        var restored = GameServices.sprites().getMainPlayable();
        assertSame(replacement, restored);
        assertWorld(detached, registry.capture());
        tick(restored, false, false);
        assertAirContinuation(restored, expected);
        assertWorld(airborne, registry.capture());
    }

    private static void assertAirContinuation(AbstractPlayableSprite p, int expected) {
        assertTrue(p.getAir());
        assertFalse(p.getCrouching());
        assertFalse(p.getLookingUp());
        assertFalse(p.getAnimationManager().hasGroundMovementAnimSpeed());
        assertEquals(expected, p.getAnimationId(), "neutral airborne continuation");
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> key + " " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }

    private static void prepare(AbstractPlayableSprite p, int idle) {
        ObjectControlState.none().applyTo(p);
        p.setControlLocked(false);
        p.setCentreX((short) 0x400);
        p.setCentreY((short) 0x100);
        p.setXSpeed((short) 0);
        p.setYSpeed((short) 0);
        p.setGSpeed((short) 0);
        p.setAir(false);
        p.setRolling(false);
        p.setAnimationId(idle);
        var camera = GameServices.camera();
        camera.setMinX((short) 0);
        camera.setMaxX((short) 0x3000);
        camera.setMinY((short) 0);
        camera.setMaxY((short) 0x1000);
    }

    private static void movement(AbstractPlayableSprite p, boolean up, boolean down) {
        p.captureOnObjectAtFrameStart();
        p.getMovementManager().handleMovement(up, down, false, false, false, false, false, false);
    }

    private static void tick(AbstractPlayableSprite p, boolean up, boolean down) {
        movement(p, up, down);
        p.getAnimationManager().update(1);
    }
}
