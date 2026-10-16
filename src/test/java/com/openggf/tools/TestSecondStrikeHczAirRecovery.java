package com.openggf.tools;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.objects.BubblerObjectInstance;
import com.openggf.game.sonic3k.objects.HCZConveyorBeltObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.DrowningController;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Positioned HCZ1 oxygen answer through the actual packaged Second Strike mod.
 *
 * <p>Origin: 2026-10-16 maker114 R2–R6 investigation; root77730 qualified the
 * fixed ordinary policy on historical42171/cb2. This class must earn the same
 * local result on its own current build; historical frame numbers are NOT inputs
 * or assertions. It does not certify a cold route, HCZ2, all characters, viewport
 * breadth, save/revisit, or a whole-act air budget.
 *
 * <p>Supply an explicitly matched, strictly packaged creator jar with
 * {@code -Dsecondstrike.hcz.air.mod=/absolute/path/second-strike.jar}. No stale
 * target default, source compilation fallback, probe-only replacement mod or
 * synthetic ROM is selected. Missing property is an explicit artifact opt-in
 * skip; once supplied, wrong/missing artifacts and failed prerequisites fail.
 *
 * <p>Setup alone declares Sonic solo, donor off, native320, No Save, seed12A8/082C
 * and zero rings. Thereafter only ordinary P1 pads and the registered-world
 * restore below change gameplay. No queue servicing, actor removal, RNG/resource/
 * camera/control/phase write, input search, second-hop fallback or timer retuning.
 */
@RequiresRom(SonicGame.SONIC_3K)
@EnabledIfSystemProperty(named = "secondstrike.hcz.air.mod", matches = ".+")
class TestSecondStrikeHczAirRecovery {
    private static final int SEED_X = 0x12A8, SEED_Y = 0x082C, FLOOR = 0x083F;
    private static final int TARGET_X = 0x12C4, BAND_LO = 0x12C3, BAND_HI = 0x12CC;
    private static final int RETURN_LO = 0x12A0, MAX_POST_READY_CALLS = 600;
    private static final int RIGHT = AbstractPlayableSprite.INPUT_RIGHT;
    private static final int LEFT = AbstractPlayableSprite.INPUT_LEFT;
    private static final int A = AbstractPlayableSprite.INPUT_JUMP;
    // Original HCZ1 object bank, pinned original locked-on ROM; six bytes per record.
    private static final int HCZ1_OBJECTS = 0x1E6090;

    @BeforeEach void resetBefore() { TestEnvironment.resetAll(); }
    @AfterEach void resetAfter() { TestEnvironment.resetAll(); }

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SAME_THREAD)
    void zeroRingNativeBubbleRefillsReturnsAndRecreatesForWholeWorldReplay() throws Exception {
        assertEquals("surfaceless-egl", System.getProperty("openggf.headless.gl"),
                "this opt-in native-GL test must fail before boot without the isolated EGL backend");
        assertNull(System.getenv("DISPLAY"), "never use the shared X11 display");
        assertNull(System.getenv("WAYLAND_DISPLAY"), "never use the shared Wayland display");
        Path mod = Path.of(System.getProperty("secondstrike.hcz.air.mod")).toRealPath();
        assertTrue(Files.isRegularFile(mod), "actual packaged creator jar required");
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null,
                SEED_X, SEED_Y, null, false, false, null, null, false, 0, false);

        try (var session = new GameplayCaptureSession(settings)) {
            session.applyMod(mod);
            session.boot(rom, 1, 0, settings);
            var attempt = new Attempt(session, Files.readAllBytes(rom));
            attempt.ready();
            attempt.approach();
            attempt.selectFirstCommittedBubble();

            var rewind = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            assertNotNull(rewind, "the actual gameplay session must publish its whole-world registry");
            CompositeSnapshot committed = rewind.capture();
            assertTrue(committed.entries().containsKey("object-manager"));
            NativeFrame committedFrame = attempt.frame();
            Bk2FrameInput committedInput = attempt.lastInput;
            BubblerObjectInstance original = attempt.chosen;
            int slot = original.getSlotIndex();
            ObjectSpawn spawn = original.getSpawn();
            assertTrue(slot >= 0, "the naturally spawned chosen child has a managed native slot");
            int suffixStart = attempt.steps.size();

            attempt.hopBreatheAndReturn();
            attempt.assertEndpoint();
            assertEquals(1, attempt.issuedATaps, "one fresh A in the forward attempt, no retry");
            assertTrue(original.isDestroyed(), "the consumed native child must retire naturally");
            assertTrue(attempt.objects.getActiveObjects().stream().noneMatch(o -> o == original),
                    "destructive restore is earned by natural child retirement, never test removal");
            List<RecordedStep> suffix = List.copyOf(attempt.steps.subList(suffixStart, attempt.steps.size()));
            assertFalse(suffix.isEmpty());
            assertEquals(1L, suffix.stream().filter(s -> s.input().p1InputMask() == A).count());
            CompositeSnapshot forward = rewind.capture();

            rewind.restore(committed);
            // Package-supported external edge history; no native field/snapshot hydration.
            session.restoreInputHistory(committedInput);
            attempt.lastInput = committedInput;
            assertWorldEquals(committed, rewind.capture(), "immediate committed-world restore");
            var restored = attempt.objects.getActiveObjects().stream()
                    .filter(BubblerObjectInstance.class::isInstance).map(BubblerObjectInstance.class::cast)
                    .filter(o -> !o.isDestroyed() && o.getSlotIndex() == slot && spawn.equals(o.getSpawn()))
                    .toList();
            assertEquals(1, restored.size(), "exact chosen native slot/full-spawn identity recreates once");
            attempt.chosen = restored.getFirst();
            assertNotSame(original, attempt.chosen, "restore must recreate the naturally retired child");
            assertEquals(committedFrame, attempt.frame(), "native player/air/breath/child state before any replay update");
            attempt.assertStationary();

            // Replay only the actual admitted ordinary suffix. Expected state is comparison-only.
            // The attempt-wide post-ready call counter remains monotonic; restore never resets it.
            for (int i = 0; i < suffix.size(); i++) {
                RecordedStep expected = suffix.get(i);
                attempt.phase = expected.phase(); // observer label, never native state or input selection
                attempt.step(expected.input());
                assertEquals(expected.after(), attempt.frame(), "native suffix timeline at +" + (i + 1));
            }
            attempt.assertEndpoint();
            assertEquals(2, attempt.issuedATaps, "one original A and its exact replay, no second-hop search");
            assertWorldEquals(forward, rewind.capture(), "whole registered world after forward replay");
        }
    }

    private static void assertWorldEquals(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), label + " registered keys");
        var differences = new ArrayList<String>();
        for (String key : expected.entries().keySet())
            differences.addAll(RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        assertTrue(differences.isEmpty(), () -> label + "\n" + String.join("\n", differences));
    }

    /** Read-only native scalars; no reflection setter or private owner routine is invoked. */
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException next) { }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private static boolean flag(Object owner, String name) throws ReflectiveOperationException {
        return (boolean) field(owner, name);
    }

    private static int number(Object owner, String name) throws ReflectiveOperationException {
        return (int) field(owner, name);
    }

    private record ChildFrame(int slot, ObjectSpawn spawn, int x, int y, int mapping,
                              boolean inhalable, boolean consumed, boolean destroyed, boolean managed) { }

    private record NativeFrame(int levelClock, int vInt, int x, int y, int xFraction, int yFraction,
                               int xSpeed, int ySpeed, int gSpeed, boolean airborne, boolean rolling,
                               boolean jumping, int xRadius, int yRadius, int angle, GroundMode groundMode,
                               int animation, int mapping, int forcedAnimation, int moveLock,
                               int cameraX, int cameraY, DrowningController.RewindState breath, ChildFrame child) { }

    private record RecordedStep(Bk2FrameInput input, String phase, NativeFrame after) { }

    private static final class Attempt {
        final GameplayCaptureSession session;
        final AbstractPlayableSprite player;
        final LevelManager level;
        final ObjectManager objects;
        final Object nativeLevel;
        final long generation;
        final int lives;
        final byte[] rom;
        final List<RecordedStep> steps = new ArrayList<>();
        BubblerObjectInstance chosen;
        Bk2FrameInput lastInput;
        int calls, readyAt = -1, issuedATaps;
        String phase = "READINESS";

        Attempt(GameplayCaptureSession session, byte[] rom) {
            this.session = session;
            this.rom = rom;
            player = session.player();
            level = GameServices.level();
            objects = level.getObjectManager();
            nativeLevel = level.getCurrentLevel();
            generation = level.getCompletedProductionLoadGeneration();
            lives = GameServices.gameState().getLives();
        }

        int air() { return player.getDrowningController().getRemainingAir(); }

        int targetCameraY() {
            var camera = GameServices.camera();
            return Math.max(camera.getMinY(), Math.min(camera.getMaxY(), player.getCentreY() - camera.getYPosBias()));
        }

        ObjectSpawn original(int index) {
            int offset = HCZ1_OBJECTS + index * 6;
            int x = ((rom[offset] & 255) << 8) | (rom[offset + 1] & 255);
            int y = ((rom[offset + 2] & 255) << 8) | (rom[offset + 3] & 255);
            return new ObjectSpawn(x, y & 0xFFF, rom[offset + 4] & 255, rom[offset + 5] & 255,
                    (y >>> 13) & 3, (y & 0x8000) != 0, y, index);
        }

        ObjectSpawn placement(int index) {
            var matches = objects.getAllSpawns().stream().filter(o -> o.layoutIndex() == index).toList();
            assertEquals(1, matches.size(), "canonical record " + index);
            return matches.getFirst();
        }

        List<ObjectInstance> liveRecord(int index) {
            return objects.getActiveObjects().stream().filter(o -> !o.isDestroyed() && o.getSpawn() != null
                    && o.getSpawn().layoutIndex() == index).toList();
        }

        void assertNativeRecordsAndModEconomy() {
            for (int index : new int[]{99, 100, 114, 115, 116, 117})
                assertEquals(original(index), placement(index), "native maker/hazard/conveyor full fields " + index);
            for (int index : new int[]{109, 112, 113}) {
                var raw = original(index);
                var expected = new ObjectSpawn(raw.x(), raw.y(), raw.objectId(), raw.subtype(), raw.renderFlags(),
                        raw.respawnTracked(), raw.rawYWord(), raw.layoutIndex(), "second-strike", "second-strike:robotnik-monitor");
                assertEquals(expected, placement(index), "actual creator monitor economics " + index);
            }
        }

        boolean conveyorReady(int subtype) throws Exception {
            var owners = objects.getActiveObjects().stream().filter(HCZConveyorBeltObjectInstance.class::isInstance)
                    .map(HCZConveyorBeltObjectInstance.class::cast)
                    .filter(o -> !o.isDestroyed() && o.getSpawn().subtype() == subtype).toList();
            if (owners.size() != 1) return false;
            var belt = owners.getFirst();
            int index = belt.getSpawn().layoutIndex();
            if (subtype == 4 ? index != 99 && index != 116 : index != 100 && index != 117) return false;
            var raw = original(index);
            int y = subtype == 4 ? 0x078B : 0x07B5;
            var shifted = new ObjectSpawn(0x11A8, y, raw.objectId(), raw.subtype(), raw.renderFlags(),
                    raw.respawnTracked(), raw.rawYWord(), raw.layoutIndex(), null, null);
            // Full subtype owns one belt across a native duplicate-marker pair; not one body per record.
            // Native getSpawn() uses buildSpawnAt(leftBound, objY): only X/Y change,
            // so a body from record116/117 reports11A8 while retaining its original index/raw metadata.
            // loadArray is deliberately not treated as current ownership authority.
            return belt.getClass() == HCZConveyorBeltObjectInstance.class && flag(belt, "initialized")
                    && shifted.equals(belt.getSpawn()) && belt.getSlotIndex() >= 0
                    && number(belt, "rawSubtype") == subtype && number(belt, "subtypeIndex") == 4
                    && number(belt, "leftBound") == 0x11A8 && number(belt, "rightBound") == 0x12D8
                    && belt.getX() == 0x11A8 && belt.getY() == y && number(belt, "objY") == y
                    && flag(belt, "flipped") == (subtype == 0x14)
                    && number(belt, "activeLeftBound") == (subtype == 4 ? 0x11A0 : 0x11A8)
                    && number(belt, "activeRightBound") == (subtype == 4 ? 0x12D8 : 0x12E0);
        }

        boolean localArtReady() {
            var art = level.getObjectRenderManager();
            var sprite = player.getSpriteRenderer();
            if (art == null || !art.isReady() || sprite == null || sprite.patternBankBase() < 0
                    || sprite.patternBankCapacity() <= 0) return false;
            var bounds = sprite.getFrameBounds(player.getMappingFrame(), false, false); // pure compute
            if (bounds.width() <= 0 || bounds.height() <= 0) return false;
            for (String key : List.of(Sonic3kObjectArtKeys.BUBBLER, Sonic3kObjectArtKeys.HCZ_SPINNING_COLUMN,
                    Sonic3kObjectArtKeys.MONITOR, Sonic3kObjectArtKeys.SPIKES)) {
                var renderer = art.getRenderer(key);
                if (renderer == null || !renderer.isReady()) return false;
            }
            return true;
        }

        boolean currentTerrainSupport() {
            if (player.getAir() || player.getGroundMode() != GroundMode.GROUND || player.getAngle() != 0
                    || player.getYSpeed() != 0 || !noObjectSupport()) return false;
            var sensors = player.getGroundSensors();
            if (sensors == null || sensors.length != 2) return false;
            for (var sensor : sensors) {
                if (sensor == null || !sensor.isActive() || sensor.getCurrentResult() == null) return false;
                var result = sensor.getCurrentResult();
                if (result.direction() != Direction.DOWN || Math.abs((int) result.distance()) > 14) return false;
            }
            int foot = player.getCentreY() + player.getYRadius();
            var left = ObjectTerrainUtils.checkFloorDistOnLayer(level, player.getCentreX() - player.getXRadius(), foot, (byte) 0, false, true);
            var right = ObjectTerrainUtils.checkFloorDistOnLayer(level, player.getCentreX() + player.getXRadius(), foot, (byte) 0, false, true);
            return left.foundSurface() && right.foundSurface() && Math.abs(left.distance()) <= 1
                    && Math.abs(right.distance()) <= 1 && foot + left.distance() == FLOOR && foot + right.distance() == FLOOR;
        }

        boolean noObjectSupport() {
            return !player.isOnObject() && !objects.isRidingObject(player) && objects.getRidingObject(player) == null
                    && objects.getActiveObjects().stream().noneMatch(o -> !o.isDestroyed() && objects.hasObjectStandingBit(player, o));
        }

        boolean backgroundPlaneActive() {
            var state = GameServices.zoneRuntimeRegistry().current().backgroundPlaneCollisionStateOrNull();
            return state != null ? state.active() : GameServices.gameState().isBackgroundCollisionFlag();
        }

        void assertEpisode() {
            assertSame(level, GameServices.level());
            assertSame(objects, level.getObjectManager());
            assertSame(nativeLevel, level.getCurrentLevel());
            assertEquals(generation, level.getCompletedProductionLoadGeneration());
            assertEquals(1, level.getCurrentZone());
            assertEquals(0, level.getCurrentAct());
            assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
            assertSame(player, GameServices.sprites().getMainPlayable());
            assertEquals("sonic", player.getCode().toLowerCase(java.util.Locale.ROOT));
            assertTrue(GameServices.sprites().getSidekicks().isEmpty());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertFalse(CrossGameFeatureProvider.isActive(), "native donor-off team");
            var save = GameServices.worldSession().getSaveSessionContext();
            assertTrue(save == null || save.activeSlot().isEmpty(), "No Save must stay No Save");
            assertEquals(0, player.getRingCount());
            assertFalse(player.hasShield());
            assertFalse(player.hasSpeedShoes());
            assertFalse(player.isSuperSonic());
            assertFalse(player.isHurt(), "hurt during " + phase + " call=" + calls);
            assertFalse(player.getDead(), "death during " + phase + " call=" + calls);
            assertEquals(0, player.getInvulnerableFrames());
            assertEquals(0, player.getInvincibleFrames());
            assertEquals(lives, GameServices.gameState().getLives());
            assertFalse(player.isObjectControlled());
            assertFalse(player.isControlLocked());
            assertTrue(noObjectSupport(), "current manager support, not historical pointer");
            assertFalse(backgroundPlaneActive());
            assertEquals(12, player.getTopSolidBit() & 255);
            assertEquals(13, player.getLrbSolidBit() & 255);
            assertTrue(player.getCentreX() >= RETURN_LO && player.getCentreX() <= BAND_HI);
            assertTrue(player.getCentreY() >= 0x07EC && player.getCentreY() <= SEED_Y + 6);
            assertTrue(!player.getRolling() || phase.equals("HOP") || phase.equals("BREATH"));
            for (ObjectInstance object : objects.getActiveObjects()) {
                if (object.isDestroyed() || !(object instanceof SolidObjectProvider solid)) continue;
                var box = solid.getSolidParams();
                if (box == null) continue;
                boolean overlap = Math.abs(player.getCentreX() - (object.getX() + box.offsetX())) < player.getXRadius() + box.halfWidth()
                        && Math.abs(player.getCentreY() - (object.getY() + box.offsetY())) < player.getYRadius() + box.airHalfHeight();
                assertFalse(overlap, "body overlaps a current native solid");
            }
            if (readyAt >= 0) {
                assertEquals(0x0680, GameServices.water().getGameplayWaterLevelY(1, 0));
                assertTrue(player.isInWater());
                assertEquals(6, player.getRunAccel());
                assertEquals(64, player.getRunDecel());
                assertEquals(6, player.getFriction());
                assertEquals(768, player.getMax());
                assertEquals(896, player.getJump());
                assertTrue(GameServices.camera().isLevelStarted());
                assertTrue(player.getForcedAnimationId() < 0);
                assertTrue(localArtReady(), "continuing LOCAL renderer/bank/bounds readiness, not ledger-idle admission");
            }
        }

        boolean readyNow() throws Exception {
            if (liveRecord(109).size() != 1 || liveRecord(114).size() != 1 || liveRecord(115).size() != 1) return false;
            var maker = liveRecord(114).getFirst();
            if (maker.getClass() != BubblerObjectInstance.class || !flag(maker, "maker") || number(maker, "routine") != 10
                    || !conveyorReady(4) || !conveyorReady(0x14)) return false;
            var camera = GameServices.camera();
            boolean idleAtEntry = SessionManager.getCurrentGameplayMode().captureQueueDiagnostics().stream()
                    .noneMatch(q -> q.busy() || !q.queuedFingerprints().isEmpty());
            // The immutable latest DPLC ledger is an entry precondition, not a continuing air invariant.
            boolean playerLedgerIdleAtEntry = GameServices.captureDynamicArtDiagnostics().outstandingTransferIds().isEmpty();
            return currentTerrainSupport() && player.getGSpeed() == 0 && player.getXSpeed() == 0
                    && player.getMoveLockTimer() == 0 && player.getForcedAnimationId() < 0
                    && player.isInWater() && camera.isLevelStarted() && camera.getY() == targetCameraY()
                    && camera.getX() > 0x0900 && camera.getX() <= 0x2A00
                    && GameServices.water().getGameplayWaterLevelY(1, 0) == 0x0680
                    && idleAtEntry && playerLedgerIdleAtEntry && localArtReady();
        }

        void ready() throws Exception {
            assertNativeRecordsAndModEconomy();
            assertEpisode();
            var camera = GameServices.camera();
            int dy = Math.abs(targetCameraY() - camera.getY());
            int dx = Math.abs(Math.max(camera.getMinX(), Math.min(camera.getMaxX(), SEED_X - 160)) - camera.getX());
            // Same source-derived bound as R2–R6: native6px groundY/24px horizontal + explicit10-call overhead.
            int limit = Math.max((dy + 2 + 5) / 6, (dx + 23) / 24) + 10;
            assertTrue(limit <= 11, "declared entry must fit the original finite readiness bound; no extension");
            int stable = 0;
            for (int i = 0; i < limit && stable < 2; i++) {
                boolean ordinary = step(0);
                assertNativeRecordsAndModEconomy();
                stable = ordinary && readyNow() ? stable + 1 : 0;
            }
            assertEquals(2, stable, "finite native readiness incomplete; never force intro/control/streaming");
            readyAt = calls;
            assertEpisode();
            assertStationary();
            assertEquals(SEED_X, player.getCentreX());
            assertTrue(Math.abs(player.getCentreY() - SEED_Y) <= 1);
        }

        void assertStationary() {
            assertTrue(currentTerrainSupport(), "real current two-foot support on083F required");
            assertEquals(0, player.getXSpeed());
            assertEquals(0, player.getGSpeed());
            assertEquals(0, player.getMoveLockTimer());
        }

        void groundTravel() {
            assertTrue(currentTerrainSupport());
            assertEquals(0, player.getMoveLockTimer());
            assertTrue(player.getForcedAnimationId() < 0);
        }

        void approach() throws Exception {
            phase = "APPROACH";
            while (player.getCentreX() < TARGET_X) { groundTravel(); step(RIGHT); }
            while (player.getGSpeed() > player.getRunDecel()) { groundTravel(); step(LEFT); }
            assertTrue(player.getGSpeed() >= 0);
            while (player.getGSpeed() > 0) { groundTravel(); step(0); }
            assertStationary();
            assertTrue(player.getCentreX() >= BAND_LO && player.getCentreX() <= BAND_HI);
        }

        void selectFirstCommittedBubble() throws Exception {
            phase = "WAIT";
            while (chosen == null) {
                assertStationary();
                for (ObjectInstance object : objects.getActiveObjects()) {
                    if (object.getClass() != BubblerObjectInstance.class || object.isDestroyed()) continue;
                    var bubble = (BubblerObjectInstance) object;
                    var spawn = bubble.getSpawn();
                    int gap = player.getCentreY() - bubble.getY();
                    if (!flag(bubble, "maker") && spawn.objectId() == 0x54 && spawn.layoutIndex() == -1
                            && spawn.ownerModId() == null && spawn.objectKey() == null && spawn.y() == 0x0838
                            && spawn.x() >= 0x12C0 && spawn.x() <= 0x12CF && spawn.subtype() == 2
                            && number(bubble, "mappingFrame") == 6 && flag(bubble, "inhalable") && !flag(bubble, "consumed")
                            && player.getCentreX() > bubble.getX() - 16 && player.getCentreX() <= bubble.getX() + 16
                            && gap >= 17 && gap <= 24) {
                        chosen = bubble;
                        break;
                    }
                }
                if (chosen == null) step(0);
            }
            assertTrue(air() > 0 && air() < 30, "natural pre-refill air depletion, not a resource seed");
        }

        void hopBreatheAndReturn() throws Exception {
            assertStationary();
            assertFalse(player.isJumping());
            assertFalse(player.getRolling());
            assertEquals(19, player.getYRadius());
            assertFalse(chosen.isDestroyed());
            assertFalse(flag(chosen, "consumed"));
            phase = "HOP";
            int beforeInhale = air();
            step(A); // exactly one fresh A, followed only by neutral until native breath/landing
            assertTrue(player.getAir() && player.isJumping() && player.getRolling()
                    && player.getYRadius() == 14 && player.getYSpeed() < 0, "actual native jump acceptance");
            while (!flag(chosen, "consumed")) {
                assertFalse(chosen.isDestroyed(), "chosen child retired without native intake; no retry");
                beforeInhale = air();
                step(0);
                assertTrue(player.getAir() || flag(chosen, "consumed"), "one tap landed without intake; no second hop");
            }
            assertTrue(beforeInhale < air(), "genuine consumed child must increase naturally depleted air");
            int lock = player.getMoveLockTimer();
            assertTrue(lock >= 34 && lock <= 35, "native35 lock, allowing one already-dispatched owner decrement");
            assertFalse(player.isJumping());
            assertEquals(0, player.getXSpeed());
            assertEquals(0, player.getGSpeed());
            assertTrue(player.getYSpeed() >= 0 && player.getYSpeed() <= 56);
            assertEquals(player.resolveAnimationId(CanonicalAnimation.BUBBLE), player.getAnimationId());
            phase = "BREATH";
            // Remain neutral through every remaining native lock dispatch and natural landing.
            while (player.getMoveLockTimer() > 0 || !currentTerrainSupport()) {
                int before = player.getMoveLockTimer();
                boolean ordinary = step(0);
                int after = player.getMoveLockTimer();
                if (before > 0) {
                    assertTrue(after == before || after == before - 1, "only native breath-owner decrements");
                    if (after == before - 1) {
                        assertTrue(ordinary, "breath decrement requires a completed native level/V-int pass");
                    }
                }
            }
            assertStationary();
            phase = "RETURN";
            while (player.getCentreX() > SEED_X) { groundTravel(); step(LEFT); }
            while (player.getGSpeed() < -player.getRunDecel()) { groundTravel(); step(RIGHT); }
            assertTrue(player.getGSpeed() <= 0);
            while (player.getGSpeed() < 0) { groundTravel(); step(0); }
        }

        void assertEndpoint() throws Exception {
            assertEpisode();
            assertStationary();
            assertTrue(player.getCentreX() >= RETURN_LO && player.getCentreX() <= SEED_X);
            assertTrue(air() > 0);
            assertNativeRecordsAndModEconomy();
            assertTrue(conveyorReady(4) && conveyorReady(0x14));
            assertTrue(flag(chosen, "consumed"));
        }

        boolean step(int mask) throws Exception {
            return step(new Bk2FrameInput(calls, mask, mask == A ? 1 : 0, false, 0, 0, false, "hcz114-one-policy"));
        }

        boolean step(Bk2FrameInput input) throws Exception {
            if (readyAt >= 0) assertTrue(calls - readyAt < MAX_POST_READY_CALLS,
                    "original600-call post-ready budget includes replay; no reset/extension");
            int beforeLevel = level.getFrameCounter(), beforeVInt = objects.getVblaCounter();
            calls++;
            if (input.p1InputMask() == A) issuedATaps++; // actual issued boundary, even if the step fails
            lastInput = input;
            session.step(input);
            session.renderFrame(); // normal presentation owner only; no readback/queue servicing
            int levelDelta = level.getFrameCounter() - beforeLevel, vIntDelta = objects.getVblaCounter() - beforeVInt;
            assertTrue(levelDelta >= 0 && levelDelta <= 1 && vIntDelta >= 0 && vIntDelta <= 1,
                    "native counters must advance only through their ordinary owner");
            steps.add(new RecordedStep(input, phase, frame()));
            assertEpisode();
            return levelDelta == 1 && vIntDelta == 1;
        }

        NativeFrame frame() throws Exception {
            var camera = GameServices.camera();
            ChildFrame bubble = chosen == null ? null : new ChildFrame(chosen.getSlotIndex(), chosen.getSpawn(),
                    chosen.getX(), chosen.getY(), number(chosen, "mappingFrame"), flag(chosen, "inhalable"),
                    flag(chosen, "consumed"), chosen.isDestroyed(), objects.getActiveObjects().stream().anyMatch(o -> o == chosen));
            return new NativeFrame(level.getFrameCounter(), objects.getVblaCounter(), player.getCentreX(), player.getCentreY(),
                    player.getXSubpixelRaw(), player.getYSubpixelRaw(), player.getXSpeed(), player.getYSpeed(), player.getGSpeed(),
                    player.getAir(), player.getRolling(), player.isJumping(), player.getXRadius(), player.getYRadius(),
                    player.getAngle() & 255, player.getGroundMode(), player.getAnimationId(), player.getMappingFrame(),
                    player.getForcedAnimationId(), player.getMoveLockTimer(), camera.getX(), camera.getY(),
                    player.getDrowningController().captureRewindState(), bubble);
        }
    }
}
