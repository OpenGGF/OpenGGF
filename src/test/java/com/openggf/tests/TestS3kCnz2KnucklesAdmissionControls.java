package com.openggf.tests;

import com.openggf.LevelFrameResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Declared CNZ2 screen-event component controls for native044 Player_1 gates. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnz2KnucklesAdmissionControls {
    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @CsvSource({"knuckles,18544,2880,0", "knuckles,18624,2800,0", "sonic,18624,2880,8"})
    void roleAndBothCoordinateGatesAreIndependent(String main, int x, int y, int foreground) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var configuration = GameServices.configuration();
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        configuration.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        // Respectively $4870/$B40, $48C0/$AF0 and non-Knuckles $48C0/$B40.
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) x, (short) y).startPositionIsCentre().build();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
        assertNotNull(events);
        assertEquals(0, events.getForegroundRoutine());
        assertFalse(fixture.sprite().getDead());
        int before = GameServices.level().getFrameCounter();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
        assertEquals((before + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF);
        assertFalse(fixture.sprite().getDead());
        int actualX = fixture.sprite().getCentreX() & 0xFFFF;
        int actualY = fixture.sprite().getCentreY() & 0xFFFF;
        if (main.equals("knuckles")) {
            assertTrue(x < 0x4880 ? actualX < 0x4880 && actualY >= 0xB00
                    : actualX >= 0x4880 && actualY < 0xB00,
                    "ordinary frame retains the declared independent negative inlet");
        }
        var objects = GameServices.level().getObjectManager();
        assertAll(() -> assertEquals(foreground, events.getForegroundRoutine()),
                () -> assertTrue(objects.activeObjectsOfType(CnzTeleporterInstance.class).isEmpty()),
                () -> assertTrue(objects.activeObjectsOfType(CnzEggCapsuleInstance.class).isEmpty()),
                () -> assertFalse(fixture.sprite().isControlLocked()));
    }

    /**
     * PUBLIC COMPONENT INLET: native044 CNZ2_ScreenEvent reads unsigned P1 x_pos,
     * even when the exported object camera service deliberately follows registered P2.
     * These declared positions/bounds exercise the ordinary public event update only;
     * they are not cold traversal or a creator transaction/fault-boundary test.
     */
    @ParameterizedTest(name = "P1 x={0}, focused P2 x={1}, minY={2} -> {3}")
    @CsvSource({
            "0x093F, 0x0940, 0x0000, 0x0580",
            "0x0940, 0x093F, 0x0580, 0x0000",
            "0x4600, 0x093F, 0x0123, 0x0123",
            "0x45FF, 0x4600, 0x0580, 0x0000",
            "0x8000, 0x093F, 0x0123, 0x0123",
            "0xFFFF, 0x0940, 0x0123, 0x0123"
    })
    void act2YStartUsesNativePrimaryWithContradictoryRegisteredSidekickFocus(
            int primaryX, int focusedP2X, int initialMinY, int expectedMinY) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var configuration = GameServices.configuration();
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        configuration.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x4800, (short) 0x0B40).startPositionIsCentre().build();
        var sprites = GameServices.sprites();
        var primary = assertInstanceOf(com.openggf.sprites.playable.Knuckles.class, sprites.getMainPlayable());
        assertSame(fixture.sprite(), primary);
        assertEquals(1, sprites.getRegisteredSidekicks().size());
        var p2 = assertInstanceOf(com.openggf.sprites.playable.Tails.class,
                sprites.getRegisteredSidekicks().getFirst());
        assertNotSame(primary, p2);
        var level = GameServices.level();
        var objects = level.getObjectManager();
        var services = objects.getObjectServices();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
        assertNotNull(events);
        assertEquals(0, events.getForegroundRoutine());
        int levelFrame = level.getFrameCounter();
        assertTrue(level.consumePendingInitialProcessSpritesPass(),
                "finish production Player2 setup before declaring component positions");
        assertEquals(levelFrame, level.getFrameCounter());
        assertSame(primary, services.playerQuery().mainPlayerOrNull());
        assertSame(p2, services.playerQuery().nativeP2OrNull());
        assertSame(fixture.camera(), services.camera());
        assertSame(primary, services.camera().getFocusedSprite());

        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(primary, primaryX);
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(primary, 0x0900);
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(p2, focusedP2X);
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(p2, 0x0900);
        services.camera().setFocusedSprite(p2);
        services.camera().setMinY((short) initialMinY);

        // No object/CPU movement or private helper invocation: the real public
        // ScreenEvent entry consumes these openly declared component inputs.
        events.update(1, 0);

        assertAll("native Player_1 operand and independent public camera target",
                () -> assertEquals(expectedMinY, services.camera().getMinY() & 0xFFFF),
                () -> assertSame(primary, sprites.getMainPlayable()),
                () -> assertSame(primary, services.playerQuery().mainPlayerOrNull()),
                () -> assertSame(p2, sprites.getRegisteredSidekicks().getFirst()),
                () -> assertSame(p2, services.playerQuery().nativeP2OrNull()),
                () -> assertSame(p2, services.camera().getFocusedSprite()),
                () -> assertEquals(primaryX, primary.getCentreX() & 0xFFFF),
                () -> assertEquals(focusedP2X, p2.getCentreX() & 0xFFFF),
                () -> assertEquals(0x0900, primary.getCentreY() & 0xFFFF),
                () -> assertEquals(0x0900, p2.getCentreY() & 0xFFFF),
                () -> assertEquals(levelFrame, level.getFrameCounter()),
                () -> assertEquals(0, events.getForegroundRoutine()),
                () -> assertTrue(objects.activeObjectsOfType(CnzTeleporterInstance.class).isEmpty()),
                () -> assertTrue(objects.activeObjectsOfType(CnzEggCapsuleInstance.class).isEmpty()));
    }

    /** PUBLIC COMPONENT INLET: camera focus is deliberately a real registered P2. */
    @Test
    void actualPrimaryAdmitsWhileFocusedRegisteredSidekickIsOutsideTrigger() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var configuration = GameServices.configuration();
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        configuration.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x4800, (short) 0x0B40).startPositionIsCentre().build();
        var sprites = GameServices.sprites();
        var primary = assertInstanceOf(com.openggf.sprites.playable.Knuckles.class, sprites.getMainPlayable());
        assertSame(fixture.sprite(), primary);
        assertEquals(1, sprites.getRegisteredSidekicks().size(), "actual admitted P2 required");
        var p2 = assertInstanceOf(com.openggf.sprites.playable.Tails.class,
                sprites.getRegisteredSidekicks().getFirst());
        assertNotSame(primary, p2);
        // Explicit production setup before this component's position/control declarations.
        // Initial SST Player2 setup writes main-$20/+4 while preserving control flags;
        // this is a source-owned setup writer, not ordinary CPU movement attribution.
        int setupLevelFrame = GameServices.level().getFrameCounter();
        var setupEvents = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
        assertNotNull(setupEvents);
        assertEquals(0, setupEvents.getForegroundRoutine());
        assertSame(primary, fixture.camera().getFocusedSprite());
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass(),
                "declared component inlet requires the actual pending initial setup pass");
        assertEquals(setupLevelFrame, GameServices.level().getFrameCounter());
        assertSame(setupEvents, assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents());
        assertEquals(0, setupEvents.getForegroundRoutine());
        assertSame(primary, sprites.getMainPlayable());
        assertSame(fixture.sprite(), primary);
        assertEquals(1, sprites.getRegisteredSidekicks().size());
        assertSame(p2, sprites.getRegisteredSidekicks().getFirst());
        assertSame(primary, fixture.camera().getFocusedSprite());
        // One real ordinary INIT dispatch precedes component control: initial
        // ProcessSprites resets CPU globals to INIT; only this ordinary pass
        // reaches updateInit's placement and its transition to NORMAL.
        assertTrue((primary.getCentreX() & 0xFFFF) < 0x4880);
        int initializationFrame = GameServices.level().getFrameCounter();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
        assertEquals((initializationFrame + 1) & 0xFFFF,
                GameServices.level().getFrameCounter() & 0xFFFF);
        assertNotNull(p2.getCpuController());
        assertEquals(com.openggf.sprites.playable.SidekickCpuController.State.NORMAL,
                p2.getCpuController().getState(), "one ordinary INIT must establish NORMAL; no warmup retry");
        assertSame(primary, sprites.getMainPlayable());
        assertEquals(1, sprites.getRegisteredSidekicks().size());
        assertSame(p2, sprites.getRegisteredSidekicks().getFirst());
        assertSame(primary, fixture.camera().getFocusedSprite());
        assertFalse(primary.getDead());
        assertFalse(p2.getDead());
        assertTrue((primary.getCentreX() & 0xFFFF) < 0x4880);
        assertEquals(0, setupEvents.getForegroundRoutine());
        assertTrue(GameServices.level().getObjectManager()
                .activeObjectsOfType(CnzTeleporterInstance.class).isEmpty());
        assertTrue(GameServices.level().getObjectManager()
                .activeObjectsOfType(CnzEggCapsuleInstance.class).isEmpty());
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(primary, 0x48C0);
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(primary, 0x0B40);
        // Explicit component placement/control. No private route or event phase is seeded.
        com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(p2, 0x47F0);
        com.openggf.sprites.NativePositionOps.writeYPosPreserveSubpixel(p2, 0x0B40);
        p2.setObjectControlled(true);
        p2.setObjectControlAllowsCpu(false);
        p2.setObjectControlSuppressesMovement(true);
        p2.setControlLocked(true);
        fixture.camera().setFocusedSprite(p2);
        assertEquals(0x47F0, p2.getCentreX() & 0xFFFF);
        assertEquals(0x0B40, p2.getCentreY() & 0xFFFF);
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
        assertNotNull(events);
        assertEquals(0, events.getForegroundRoutine());
        assertSame(p2, fixture.camera().getFocusedSprite());
        assertTrue((primary.getCentreX() & 0xFFFF) >= 0x4880
                && (primary.getCentreY() & 0xFFFF) >= 0xB00);
        int before = GameServices.level().getFrameCounter();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
        assertEquals((before + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF);
        // A changed fixture ownership/focus/inlet is an honest prerequisite failure.
        assertSame(primary, sprites.getMainPlayable());
        assertSame(p2, sprites.getRegisteredSidekicks().getFirst());
        assertSame(p2, fixture.camera().getFocusedSprite());
        assertFalse(primary.getDead());
        assertFalse(p2.getDead());
        assertTrue((primary.getCentreX() & 0xFFFF) >= 0x4880
                && (primary.getCentreX() & 0xFFFF) < 0x4A38
                && (primary.getCentreY() & 0xFFFF) >= 0xB00);
        assertEquals(0x47F0, p2.getCentreX() & 0xFFFF);
        assertEquals(0x0B40, p2.getCentreY() & 0xFFFF);
        var objects = GameServices.level().getObjectManager();
        assertAll(() -> assertEquals(4, events.getForegroundRoutine()),
                () -> assertEquals(1, objects.activeObjectsOfType(CnzTeleporterInstance.class).size()),
                () -> assertEquals(1, objects.activeObjectsOfType(CnzEggCapsuleInstance.class).size()),
                () -> assertFalse(primary.isControlLocked()));
    }


    /** DECLARED RESOURCE EXHAUSTION: reserve real pool slots, never fake objects. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void allocationFailureStillAdvancesOnceWithoutEarlyArm(int freeSlots) throws Exception {
        var fixture = bootDeclaredAdmission();
        var objects = GameServices.level().getObjectManager();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
        objects.reserveAllButNFreeSlots(freeSlots);
        assertEquals(freeSlots, objects.getObjectSlotCapacity() - objects.getActiveObjectSlotCount());
        assertEquals(0, events.getForegroundRoutine());
        stepDeclaredAdmission(fixture);
        assertEquals(4, events.getForegroundRoutine());
        assertEquals(0x4750, fixture.camera().getMinX() & 0xFFFF);
        assertEquals(0x48E0, fixture.camera().getMaxX() & 0xFFFF);
        assertFalse(GameServices.gameState().isEndOfLevelFlag());
        var teleporters = objects.activeObjectsOfType(CnzTeleporterInstance.class);
        assertEquals(freeSlots, teleporters.size());
        assertTrue(objects.activeObjectsOfType(CnzEggCapsuleInstance.class).isEmpty());
        int allocated = objects.getActiveObjectSlotCount();
        CnzTeleporterInstance actual = freeSlots == 1 ? teleporters.getFirst() : null;
        int actualSlot = actual == null ? -1 : actual.getSlotIndex();
        if (actual != null) assertTrue(actualSlot >= 0 && actualSlot < objects.getLastDynamicSlotExclusive());
        stepDeclaredAdmission(fixture);
        assertEquals(4, events.getForegroundRoutine(), "pool failure is not a retry state");
        assertEquals(allocated, objects.getActiveObjectSlotCount());
        assertEquals(freeSlots, objects.activeObjectsOfType(CnzTeleporterInstance.class).size());
        if (actual != null) {
            assertSame(actual, objects.activeObjectsOfType(CnzTeleporterInstance.class).getFirst());
            assertEquals(actualSlot, actual.getSlotIndex());
        }
        assertTrue(objects.activeObjectsOfType(CnzEggCapsuleInstance.class).isEmpty());
        assertTrue(objects.activeObjectsOfType(com.openggf.game.sonic3k.objects.CnzTeleporterBeamInstance.class).isEmpty());
        assertFalse(fixture.sprite().isControlLocked());
    }

    /** FULL REGISTERED WORLD: fresh pre-admission A -> ordinary B, restored twice. */
    @Test
    void admissionRecreatesAndReplaysEveryRegisteredKeyTwice() throws Exception {
        var fixture = bootDeclaredAdmission();
        // Declared public presentation initialization, before the first captured world.
        GameServices.parallax().initZone(Sonic3kZoneIds.ZONE_CNZ, 1,
                fixture.camera().getX(), fixture.camera().getY());
        var level = GameServices.level();
        var worldLevel = level.getCurrentLevel();
        var primary = fixture.sprite();
        var registry = fixture.gameplayMode().getRewindRegistry();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
        assertEquals(0, events.getForegroundRoutine());
        int frameA = level.getFrameCounter();
        var a = registry.capture();
        stepDeclaredAdmission(fixture);
        var objects = level.getObjectManager();
        assertEquals(4, events.getForegroundRoutine());
        assertEquals(1, objects.activeObjectsOfType(CnzTeleporterInstance.class).size());
        assertEquals(1, objects.activeObjectsOfType(CnzEggCapsuleInstance.class).size());
        int teleporterSlot = objects.activeObjectsOfType(CnzTeleporterInstance.class).getFirst().getSlotIndex();
        int capsuleSlot = objects.activeObjectsOfType(CnzEggCapsuleInstance.class).getFirst().getSlotIndex();
        assertTrue(capsuleSlot > teleporterSlot, "CreateNewSprite4 continues after the actual first slot");
        var b = registry.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(a);
            assertCompleteWorld(a, registry.capture(), "restored A cycle " + cycle);
            assertSame(worldLevel, level.getCurrentLevel());
            assertSame(primary, GameServices.sprites().getMainPlayable());
            assertSame(primary, fixture.camera().getFocusedSprite());
            assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
            assertEquals(frameA, level.getFrameCounter());
            assertEquals(0, events.getForegroundRoutine());
            assertTrue(objects.activeObjectsOfType(CnzTeleporterInstance.class).isEmpty());
            assertTrue(objects.activeObjectsOfType(CnzEggCapsuleInstance.class).isEmpty());
            stepDeclaredAdmission(fixture);
            assertCompleteWorld(b, registry.capture(), "forward B cycle " + cycle);
            assertEquals(4, events.getForegroundRoutine());
            assertEquals(teleporterSlot, objects.activeObjectsOfType(CnzTeleporterInstance.class).getFirst().getSlotIndex());
            assertEquals(capsuleSlot, objects.activeObjectsOfType(CnzEggCapsuleInstance.class).getFirst().getSlotIndex());
        }
    }

    private static HeadlessTestFixture bootDeclaredAdmission() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var configuration = GameServices.configuration();
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        configuration.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x48C0, (short) 0x0B40).startPositionIsCentre().build();
        assertSame(fixture.sprite(), GameServices.sprites().getMainPlayable());
        assertSame(fixture.sprite(), fixture.camera().getFocusedSprite());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        assertFalse(fixture.sprite().isControlLocked());
        return fixture;
    }

    private static void stepDeclaredAdmission(HeadlessTestFixture fixture) {
        int before = GameServices.level().getFrameCounter();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
        assertEquals((before + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF);
        assertFalse(fixture.sprite().getDead());
        assertTrue((fixture.sprite().getCentreX() & 0xFFFF) >= 0x4880
                && (fixture.sprite().getCentreX() & 0xFFFF) < 0x4A38
                && (fixture.sprite().getCentreY() & 0xFFFF) >= 0xB00,
                "ordinary frame must retain the declared admission/below-arm inlet");
    }

    private static void assertCompleteWorld(com.openggf.game.rewind.CompositeSnapshot expected,
            com.openggf.game.rewind.CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context + " all registered keys");
        for (String key : expected.entries().keySet()) {
            assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.keyEquals(key,
                    expected.entries().get(key), actual.entries().get(key)),
                    () -> context + " " + com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,
                            expected.entries().get(key), actual.entries().get(key)));
        }
    }

}
