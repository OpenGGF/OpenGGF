package com.openggf.game.sonic3k.objects;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.route.InputProgram;
import com.openggf.tests.route.RouteSteering;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FBZ completion task, September 2026: reproduce the placed $1DC0 underpass
 * using only ordinary inputs after local ROM-backed setup. The live controller
 * predicts a car interception; contact, clearance and safety are observed from
 * production physics, never inferred from that prediction. This is a bounded
 * local regression, not an act traversal or emulator-parity claim.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzSqueezeOrdinaryRoll {
    private static final int RIGHT = 0x08;
    private static final int DOWN = 0x02;
    private static final int MAX_FRAMES = 1400;
    // Obj_FBZ_Elevator's $60 spawn period: delay through every residue without
    // writing the controller timer, the car position, or the player's speed.
    private static final int CAR_PERIOD = 0x60;

    @ParameterizedTest(name = "S1 ordinary underpass: arrival delay {0}")
    @MethodSource("arrivalDelays")
    void everyElevatorArrivalPhaseClearsWithS1OrdinaryRoll(int delay) throws Exception {
        run("s1", 320, delay, null);
    }

    @ParameterizedTest(name = "ordinary underpass: {0}px donor={1}")
    @MethodSource("widthDonorCases")
    void requiredWidthsAndDonorsUseActualCarContactWithoutAssist(int width, String donor)
            throws Exception {
        run(donor, width, 0, null);
    }

    @ParameterizedTest(name = "ordinary underpass rewind: {0}")
    @EnumSource(RewindSpot.class)
    void productionRegistryRestoresAndReplaysTheLocalCrossing(RewindSpot spot) throws Exception {
        run("s1", 320, 0, spot);
    }

    private static IntStream arrivalDelays() {
        return IntStream.range(0, CAR_PERIOD);
    }

    private static Stream<Arguments> widthDonorCases() {
        return IntStream.of(320, 400, 512, 640, 800).boxed()
                .flatMap(width -> Stream.of("off", "s1", "s2")
                        .map(donor -> Arguments.of(width, donor)));
    }

    private static void run(String donor, int width, int delay, RewindSpot rewindSpot)
            throws Exception {
        try (ConfigurationScope ignored = new ConfigurationScope(donor, width)) {
            var builder = HeadlessTestFixture.builder()
                    .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 1)
                    .startPosition((short) 0x1CF0, (short) 0x076C).startPositionIsCentre();
            if (!donor.equals("off")) builder.withCrossGameDonation(donor);
            HeadlessTestFixture fixture = builder.build();
            GameServices.graphics().setViewport(0, 0, width, 224);
            AbstractPlayableSprite player = fixture.sprite();
            assertEquals(width, fixture.camera().getWidth() & 0xFFFF);
            assertEquals(width, GameServices.graphics().getViewportWidth());
            assertEquals(!donor.equals("off"), CrossGameFeatureProvider.isActive());
            assertEquals(!donor.equals("s1"), player.getGameRules().playerCapability().spindashEnabled());
            assertTrue(GameServices.sprites().getSidekicks().isEmpty());

            var objects = GameServices.level().getObjectManager();
            Sonic3kInvisibleBlockObjectInstance block = null;
            FbzElevatorObjectInstance.Car predictedCar = null;
            FbzElevatorObjectInstance.Car acquiredCar = null;
            Stage stage = Stage.OPEN_DOOR;
            int waited = 0;
            int acquisitions = 0;
            int releases = 0;
            int previousRings = player.getRingCount();
            boolean rolled = false;
            boolean onTrackedCar = false;
            for (int frame = 0; frame < MAX_FRAMES; frame++) {
                if (block == null) {
                    block = objects.activeObjectsOfType(Sonic3kInvisibleBlockObjectInstance.class)
                            .stream().filter(candidate -> candidate.getX() == 0x1DC0)
                            .findFirst().orElse(null);
                }
                int x = player.getCentreX() & 0xFFFF;
                int mask;
                switch (stage) {
                    case OPEN_DOOR -> {
                        // Real placed button opens the door. No trigger bit or
                        // door coordinate is supplied by the fixture/controller.
                        var door = objects.activeObjectsOfType(FbzScrewDoorObjectInstance.class)
                                .stream().filter(candidate -> candidate.getSpawn().x() == 0x1D68)
                                .findFirst().orElse(null);
                        mask = RIGHT;
                        if (door != null && door.getY() <= door.getSpawn().y() - 0x40) {
                            assertNotNull(block, "opened door must expose the authored block");
                            stage = Stage.RUN_UP;
                        }
                    }
                    case RUN_UP -> {
                        mask = RouteSteering.steerMask(player, FbzSqueezeRunUp.startX(block), 2);
                        if (Math.abs(x - FbzSqueezeRunUp.startX(block)) <= 2
                                && Math.abs(player.getGSpeed()) < 0x20) stage = Stage.WAIT;
                    }
                    case WAIT -> {
                        mask = 0;
                        if (waited++ >= delay) {
                            predictedCar = FbzSqueezeRunUp.intercept(objects, block, player).orElse(null);
                            if (predictedCar != null) stage = Stage.CROSS;
                        }
                    }
                    case CROSS -> mask = x >= FbzSqueezeRunUp.rollX(block) ? DOWN : RIGHT;
                    default -> throw new AssertionError(stage);
                }
                // No jump is ever submitted, and DOWN is never combined with
                // RIGHT: neither spindash nor the compatibility assist launches us.
                assertEquals(0, mask & 0x10);
                assertFalse((mask & RIGHT) != 0 && (mask & DOWN) != 0);
                InputProgram.step(fixture, mask);
                String state = "donor=" + donor + " width=" + width + " delay=" + delay
                        + " frame=" + frame + " stage=" + stage + " x="
                        + Integer.toHexString(player.getCentreX() & 0xFFFF) + " y="
                        + Integer.toHexString(player.getCentreY() & 0xFFFF);
                assertSafe(player, previousRings, state);
                previousRings = player.getRingCount();
                if (stage != Stage.CROSS) continue;

                rolled |= player.getRolling();
                boolean onCar = player.isOnObject()
                        && player.getLatchedSolidObjectInstance() instanceof FbzElevatorObjectInstance.Car;
                if (onCar) {
                    var actual = (FbzElevatorObjectInstance.Car) player.getLatchedSolidObjectInstance();
                    assertSame(predictedCar, actual, "must acquire the predicted live car: " + state);
                    if (!onTrackedCar) {
                        assertTrue(rolled, "ordinary roll must precede car contact: " + state);
                        acquisitions++;
                        assertEquals(1, acquisitions, "car must bind exactly once: " + state);
                        acquiredCar = actual;
                    }
                } else if (onTrackedCar) {
                    releases++;
                    assertEquals(1, releases, "tracked car must release exactly once: " + state);
                    // The cached stand-on-object reference may outlive contact.
                    // Production standing/riding authority owns release instead.
                    assertFalse(objects.isRidingObject(player, acquiredCar),
                            "release must relinquish exact car contact: " + state);
                }
                onTrackedCar = onCar;
                int blockRight = block.getX() + block.getSolidParams().offsetX()
                        + block.getSolidParams().halfWidth();
                boolean clear = (player.getCentreX() & 0xFFFF) - player.getXRadius() > blockRight
                        && releases == 1 && !player.getAir() && !player.isOnObject();
                if (rewindSpot != null && switch (rewindSpot) {
                    case BEFORE_ENTRY -> acquisitions == 0 && mask == RIGHT;
                    case ACTIVE_CAR -> onTrackedCar;
                    case AFTER_EXIT -> clear;
                }) {
                    assertRegistryReplay(fixture, onTrackedCar ? acquiredCar.getSlotIndex() : -1, mask);
                    return;
                }
                if (clear) {
                    assertTrue(rolled, "ordinary roll must actually occur: " + state);
                    assertEquals(1, acquisitions, state);
                    assertEquals(1, releases, state);
                    assertFalse(objects.isRidingObject(player, acquiredCar), state);
                    assertTrue(player.getGSpeed() > 0, "independent exit motion: " + state);
                    assertNull(rewindSpot, "requested rewind spot was not reached");
                    return;
                }
            }
            fail("ordinary underpass did not complete within " + MAX_FRAMES + " frames: donor="
                    + donor + " width=" + width + " delay=" + delay + " stage=" + stage
                    + " acquisitions=" + acquisitions + " releases=" + releases
                    + " x=" + Integer.toHexString(player.getCentreX() & 0xFFFF)
                    + " y=" + Integer.toHexString(player.getCentreY() & 0xFFFF));
        }
    }

    private static void assertSafe(AbstractPlayableSprite player, int previousRings, String state) {
        assertFalse(player.getDead(), "death: " + state);
        assertFalse(player.isHurt(), "damage: " + state);
        assertTrue(player.getRingCount() >= previousRings, "ring loss: " + state);
        assertFalse(player.getSpindash(), "spindash: " + state);
        FbzZoneRuntimeState runtime = assertInstanceOf(FbzZoneRuntimeState.class,
                GameServices.zoneRuntimeRegistry().current());
        assertNotEquals(FbzZoneRuntimeState.S1DonationSqueezeAssistState.CONSUMED,
                runtime.s1DonationSqueezeAssistState(), "assist consumption: " + state);
    }

    private static void assertRegistryReplay(HeadlessTestFixture fixture, int supportSlot, int mask) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        assertTrue(before.containsKey("object-manager"));
        assertTrue(before.containsKey("zone-runtime"));
        int rings = fixture.sprite().getRingCount();
        for (int frame = 0; frame < 8; frame++) InputProgram.step(fixture, mask);
        CompositeSnapshot expected = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            assertSnapshotsEqual(before, registry.capture(), "restore cycle " + cycle);
            if (supportSlot >= 0) {
                assertTrue(fixture.sprite().isOnObject());
                var support = assertInstanceOf(FbzElevatorObjectInstance.Car.class,
                        fixture.sprite().getLatchedSolidObjectInstance());
                assertEquals(supportSlot, support.getSlotIndex());
                assertTrue(GameServices.level().getObjectManager()
                        .activeObjectsOfType(FbzElevatorObjectInstance.Car.class).contains(support));
            }
            for (int frame = 0; frame < 8; frame++) InputProgram.step(fixture, mask);
            assertSafe(fixture.sprite(), rings, "rewind cycle " + cycle);
            assertSnapshotsEqual(expected, registry.capture(), "forward replay cycle " + cycle);
        }
    }

    static void assertSnapshotsEqual(CompositeSnapshot expected, CompositeSnapshot actual,
                                             String boundary) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), boundary);
        for (String key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, canonicalCollections(expected.get(key)),
                    canonicalCollections(actual.get(key)));
            assertTrue(differences.isEmpty(), () -> boundary + " " + key + ": " + differences);
        }
    }

    /**
     * Compare collection contents and all gameplay fields. Mirror only the
     * documented nonsemantic exclusions in RewindSnapshotDiff: level CoW epoch,
     * object-manager render-bucket dirtiness and peak-slot telemetry. Keep newer
     * gameplay fields (HUD/timers, collision/contact state, dynamic IDs) that the
     * shared comparator's older explicit field list does not yet enumerate.
     */
    private static Object canonicalCollections(Object value) {
        if (value == null) return null;
        if (value instanceof com.openggf.level.Pattern pattern) {
            // Title initialization may recreate equal ROM pixels in a new
            // Pattern identity. Compare its complete (and sole) instance state.
            byte[] pixels = new byte[com.openggf.level.Pattern.PATTERN_SIZE_IN_MEM];
            pattern.copyInto(pixels, 0);
            return canonicalCollections(pixels);
        }
        if (value instanceof java.util.List<?> list) {
            if (!list.isEmpty() && list.getFirst()
                    instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry) {
                var bySlot = new java.util.TreeMap<Integer, Object>();
                for (Object item : list) {
                    var entry = (com.openggf.game.rewind.snapshot.ObjectManagerSnapshot.PerSlotEntry) item;
                    assertNull(bySlot.put(entry.slotIndex(), canonicalCollections(entry)),
                            "duplicate captured slot identity");
                }
                return bySlot;
            }
            var result = new java.util.ArrayList<Object>(list.size());
            for (Object item : list) result.add(canonicalCollections(item));
            return result;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            var result = new java.util.LinkedHashMap<Object, Object>();
            map.forEach((key, item) -> result.put(canonicalCollections(key), canonicalCollections(item)));
            return result;
        }
        if (value instanceof java.util.Set<?> set) {
            var result = new java.util.LinkedHashSet<Object>();
            for (Object item : set) result.add(canonicalCollections(item));
            return result;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            var result = new java.util.ArrayList<Object>(length);
            for (int i = 0; i < length; i++) result.add(
                    canonicalCollections(java.lang.reflect.Array.get(value, i)));
            return result;
        }
        if (type.isRecord()) {
            try {
                var result = new java.util.LinkedHashMap<String, Object>();
                result.put("recordType", type.getName());
                for (var component : type.getRecordComponents()) {
                    if (value instanceof com.openggf.game.rewind.snapshot.LevelSnapshot
                            && component.getName().equals("epochAtCapture")) continue;
                    if (value instanceof com.openggf.game.rewind.snapshot.ObjectManagerSnapshot
                            && (component.getName().equals("bucketsDirty")
                                || component.getName().equals("peakSlotCount"))) continue;
                    var accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    result.put(component.getName(), canonicalCollections(accessor.invoke(value)));
                }
                return result;
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot compare snapshot record " + type.getName(), failure);
            }
        }
        return value;
    }

    private enum Stage { OPEN_DOOR, RUN_UP, WAIT, CROSS }
    private enum RewindSpot { BEFORE_ENTRY, ACTIVE_CAR, AFTER_EXIT }

    /** Session overlays are restored exactly; this test never writes preferences. */
    private static final class ConfigurationScope implements AutoCloseable {
        private final SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        private final Map<SonicConfiguration, Object> overrides = new EnumMap<>(SonicConfiguration.class);
        private final int viewportX = GameServices.graphics().getViewportX();
        private final int viewportY = GameServices.graphics().getViewportY();
        private final int viewportWidth = GameServices.graphics().getViewportWidth();
        private final int viewportHeight = GameServices.graphics().getViewportHeight();

        private ConfigurationScope(String donor, int width) {
            File donorRom = switch (donor) {
                case "s1" -> RomTestUtils.ensureSonic1RomAvailable();
                case "s2" -> RomTestUtils.ensureSonic2RomAvailable();
                default -> null;
            };
            if (!donor.equals("off")) {
                assertNotNull(donorRom, "mandatory " + donor + " donor ROM must exist");
                assertTrue(donorRom.isFile(), "mandatory donor must be a regular ROM file");
            }
            for (SonicConfiguration key : SonicConfiguration.values()) {
                if (configuration.hasSessionOverride(key)) overrides.put(key, configuration.getConfigValue(key));
            }
            configuration.clearSessionOverrides();
            configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
            configuration.resolveDisplayAspect();
            configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
            configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, !donor.equals("off"));
            configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, donor);
            if (donorRom != null) configuration.setSessionOverride(donor.equals("s1")
                    ? SonicConfiguration.SONIC_1_ROM : SonicConfiguration.SONIC_2_ROM,
                    donorRom.getAbsolutePath());
            CrossGameFeatureProvider.getInstance().resetState();
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }

        @Override public void close() {
            CrossGameFeatureProvider.getInstance().resetState();
            configuration.clearSessionOverrides();
            overrides.forEach(configuration::setSessionOverride);
            configuration.resolveDisplayAspect();
            GameServices.graphics().setViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }
}
