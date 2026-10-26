package com.openggf.game.sonic1.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.DamageCause;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameRng;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Short REV01 ROM-sourced GBall allocation, native-clock, contact and rewind checks. */
@RequiresRom(SonicGame.SONIC_1)
class TestS1GhzWreckingBallNative {
    private static final int POSITION_TABLE = 0x1835E;
    private static final int SINE_TABLE = 0x29EA;
    private RomByteReader rom;

    @BeforeEach
    void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        rom = RomByteReader.fromRom(TestEnvironment.currentRom());
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void romPinsAllocationAndHurtBranchesRatherThanAnAssumedLastLinkFallback() {
        assertBytes(0x1809E, 0x4E, 0xB9, 0, 0, 0xE1, 0x30, 0x66, 0x16); // ship also uses FindNext
        assertBytes(0x182CE, 0x72, 5, 0x22, 0x48, 0x60, 0x38); // six total, root first
        assertBytes(0x182D4, 0x4E, 0xB9, 0, 0, 0xE1, 0x30, 0x66, 0x5A); // FindNext, fail -> MakeBall
        assertBytes(0xE130, 0x22, 0x48, 0x30, 0x3C, 0xF0, 0, 0x90, 0x48);
        assertBytes(0xE142, 0x43, 0xE9, 0, 0x40, 0x51, 0xC8, 0xFF, 0xF6, 0x4E, 0x75);
        assertBytes(0x18336, 0x13, 0x7C, 0, 8, 0, 0x24);
        assertBytes(0x18356, 0x13, 0x7C, 0, 0x81, 0, 0x20, 0x4E, 0x75);
        assertBytes(POSITION_TABLE, 0, 0x10, 0x20, 0x30, 0x40, 0x60);
    }

    @Test
    void allSixRealObjectsInitializeInOrderAndUseTheirOwnClocks() throws Exception {
        Harness h = harness(0x60, 0x40);
        h.step();
        assertEquals(List.of(0x41, 0x42, 0x43, 0x44, 0x45),
                parts(h.manager).stream().map(AbstractObjectInstance::getSlotIndex).toList());
        assertEquals(6, 1 + parts(h.manager).size());
        assertEquals(0, integer(h.root, "verticalOffset"), "routine 0 returns before extension");
        var ball = ball(h.manager);
        assertEquals(0, integer(ball, "distance"));
        assertEquals(0, integer(ball, "ballFrame"), "later ball slot toggles frame1 to0 in allocation pass");
        assertEquals(0x81, ball.getCollisionFlags(), "native ball hurts as soon as it exists");
        assertEquals(6, h.root.getPriorityBucket());
        for (int n = 1; n <= 96; n++) {
            int parentXAtAnchorSlot = h.boss.getX();
            int parentYAtAnchorSlot = h.boss.getY();
            h.step();
            assertEquals(parentXAtAnchorSlot, h.root.getX());
            assertEquals((parentYAtAnchorSlot + Math.min(n, 32)) & 0xFFFF, h.root.getY());
            assertEquals((n / 8) & 1, integer(h.root, "anchorFrame"));
            assertEquals(n & 1, integer(ball, "ballFrame"));
            for (var part : parts(h.manager)) {
                int radius = Math.min(n, rom.readU8(POSITION_TABLE + integer(part, "chainIndex")));
                assertEquals(radius, integer(part, "distance"));
                assertEquals(h.root.getX(), part.getX());
                assertEquals((h.root.getY() + radius) & 0xFFFF, part.getY());
                assertEquals(part == ball ? 5 : 6, part.getPriorityBucket());
            }
            assertEquals(0x4080, integer(h.root, "angle"), "swing dispatch starts only after routine2 finishes");
        }
        assertTrue(bool(h.root, "chainFullyExtended"));
        int parentX = h.boss.getX();
        int parentY = h.boss.getY();
        h.step();
        assertEquals(0x3E88, integer(h.root, "angle"));
        int angle = 0x3E;
        int sine = rom.readS16BE(SINE_TABLE + angle * 2);
        int cosine = rom.readS16BE(SINE_TABLE + (angle + 0x40) * 2);
        assertEquals((parentX + ((cosine * 0x60) >> 8)) & 0xFFFF, ball.getX());
        assertEquals((parentY + 32 + ((sine * 0x60) >> 8)) & 0xFFFF, ball.getY());
    }

    @Test
    void partialAndFullRamKeepOnlyTheSuccessfulHarmlessPrefixAndNeverUseLowerHoles() throws Exception {
        for (int successfulChildren = 0; successfulChildren < 5; successfulChildren++) {
            Harness h = harness(0x30, 0x7F - successfulChildren);
            h.step();
            assertEquals(successfulChildren, parts(h.manager).size());
            assertEquals(1 + successfulChildren, integer(h.root, "elementCount"));
            assertEquals(4 + successfulChildren, h.manager.getAllocatedSlotCount(),
                    "ship/face/flame plus root and successful links; failed ghost write owns no SST");
            assertTrue(parts(h.manager).stream().allMatch(part -> part.getCollisionFlags() == 0),
                    "GBall failure writes beyond gameplay RAM instead of converting a real link");
            for (int n = 0; n < 100; n++) h.step();
            for (var part : parts(h.manager)) {
                assertEquals(rom.readU8(POSITION_TABLE + integer(part, "chainIndex")), integer(part, "distance"));
            }
            assertTrue(h.manager.allocateSlotAfter(0x20) >= 0, "lower holes stayed unused by FindNextFreeObj");
        }
        Harness full = harness(0x60, 0x40);
        full.manager.reserveAllButNFreeSlots(0);
        full.step();
        assertEquals(96, full.manager.getAllocatedSlotCount());
        assertEquals(1, integer(full.root, "elementCount"));
        assertTrue(parts(full.manager).isEmpty(), "completely full initial RAM cannot create any links or real ball");
    }

    @Test
    void productionShipAllocatesTheRootAfterItsFaceAndFlameAndDoesNotUseLowerHoles() throws Exception {
        Harness h = harness(0x60, 0x40);
        h.manager.removeDynamicObject(h.root);
        enterApproach(h);
        h.step();
        GHZBossWreckingBall root = h.manager.getActiveObjects().stream()
                .filter(GHZBossWreckingBall.class::isInstance).map(GHZBossWreckingBall.class::cast)
                .findFirst().orElseThrow();
        assertEquals(0x63, root.getSlotIndex(), "ship96, face97, flame98, anchor99");
        assertEquals(List.of(100, 101, 102, 103, 104),
                parts(h.manager).stream().map(AbstractObjectInstance::getSlotIndex).toList());
        assertEquals(0x2A00, root.getX());
        assertEquals(0x337, root.getY(), "initial root/links copy scratch BossY before sine display update");
        assertTrue(parts(h.manager).stream().allMatch(part -> part.getX() == root.getX() && part.getY() == root.getY()));
        assertEquals(9, h.manager.getAllocatedSlotCount());
    }

    @Test
    void productionShipFailureAdvancesItsPhaseWithoutRetainingAnUnmanagedAnchor() throws Exception {
        Harness h = harness(0x60, 0x40);
        h.manager.removeDynamicObject(h.root);
        h.step(); // reserve the native face and flame first
        h.manager.reserveAllButNFreeSlots(0);
        enterApproach(h);
        h.step();
        assertEquals(4, h.boss.getState().routineSecondary);
        assertEquals(119, integer(h.boss, "timer"));
        assertNull(field(h.boss, "wreckingBall"));
        assertTrue(parts(h.manager).isEmpty());
        assertEquals(96, h.manager.getAllocatedSlotCount());
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(h.manager.rewindSnapshottable());
        assertDoesNotThrow(rewind::capture, "failed allocation must retain no unregistered component edge");
    }

    private static void enterApproach(Harness h) {
        h.boss.getState().routineSecondary = 2;
        h.boss.getState().xFixed = 0x2A01 << 16;
        h.boss.getState().yFixed = 0x338 << 16;
    }

    @Test
    void parentRoutineMustEqualSixAndThresholdFrameStillUsesDeploymentGeometry() throws Exception {
        Harness h = harness(0x60, 0x40);
        h.boss.getState().routineSecondary = 8;
        // Keep the parent's defeat wait from expiring; its defeated status bit is still clear.
        write(h.boss, "timer", 10000);
        h.step();
        for (int n = 0; n < 100; n++) h.step();
        assertFalse(bool(h.root, "chainFullyExtended"), "routine8 must not satisfy native cmpi.b #6");
        assertEquals(0x4080, integer(h.root, "angle"));
        h.boss.getState().routineSecondary = 6;
        h.step();
        assertTrue(bool(h.root, "chainFullyExtended"));
        assertEquals(0x4080, integer(h.root, "angle"));
        h.step();
        assertEquals(0x3E88, integer(h.root, "angle"));
    }

    @Test
    void deploymentContactRunsTheRealHurtPathBeforeTheChainCanSwing() throws Exception {
        Harness h = harness(0x60, 0x40);
        h.step();
        for (int n = 0; n < 24; n++) h.step();
        var ball = ball(h.manager);
        assertFalse(bool(h.root, "chainFullyExtended"));
        assertEquals(24, integer(ball, "distance"));
        // Isolate weapon contact from the ship's boss collision without altering the weapon.
        h.boss.getState().invulnerable = true;
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getCentreX()).thenReturn((short) ball.getX());
        when(player.getCentreY()).thenReturn((short) (ball.getY() + 10));
        when(player.getYRadius()).thenReturn((short) 19);
        when(player.getGameRules()).thenReturn(GameRules.SONIC_1);
        h.manager.snapshotTouchResponseState();
        h.manager.runTouchResponsesForPlayer(player, 25);
        verify(player).applyHurtOrDeath(ball.getX(), DamageCause.NORMAL, false);
    }

    @Test
    void defeatRetainsEachOriginalSlotThroughItsOwnNativeExplosionAndFullRamDrawsNoRng() throws Exception {
        Harness h = harness(0x60, 0x40);
        h.step();
        for (int n = 0; n < 96; n++) h.step();
        var ball = ball(h.manager);
        int rootSlot = h.root.getSlotIndex();
        int ballSlot = ball.getSlotIndex();
        List<Integer> linkSlots = parts(h.manager).stream().filter(part -> part != ball)
                .map(AbstractObjectInstance::getSlotIndex).toList();
        h.manager.reserveAllButNFreeSlots(0);
        long rngBefore = h.rng.getSeed();
        killBoss(h);
        h.step();
        assertEquals(0x3F, h.manager.objectIdInSlot(rootSlot));
        linkSlots.forEach(slot -> assertEquals(0x3F, h.manager.objectIdInSlot(slot)));
        assertEquals(0x48, h.manager.objectIdInSlot(ballSlot));
        assertEquals(0, ball.getCollisionFlags());
        assertEquals(95, integer(ball, "distance"));
        for (int n = 2; n <= 41; n++) h.step();
        assertEquals(0x3F, h.manager.objectIdInSlot(rootSlot), "no slot is freed at conversion/init");
        linkSlots.forEach(slot -> assertEquals(0x3F, h.manager.objectIdInSlot(slot)));
        assertEquals(rngBefore, h.rng.getSeed(), "failed BossDefeated allocation must not call RandomNumber");
        h.step(); // conversion + next-pass init + forty animation passes
        assertEquals(-1, h.manager.objectIdInSlot(rootSlot));
        linkSlots.forEach(slot -> assertEquals(-1, h.manager.objectIdInSlot(slot)));
        assertEquals(0x48, h.manager.objectIdInSlot(ballSlot), "the delayed ball still owns its original slot");
        assertNull(field(h.boss, "wreckingBall"), "anchor retirement must clear the dedicated graph edge");
        for (int n = 43; n <= 97; n++) h.step();
        assertEquals(0x3F, h.manager.objectIdInSlot(ballSlot), "$60+1 decrements convert the ball in place");
        for (int n = 98; n <= 137; n++) h.step();
        assertEquals(0x3F, h.manager.objectIdInSlot(ballSlot));
        h.step();
        assertEquals(-1, h.manager.objectIdInSlot(ballSlot));
    }

    @Test
    void conversionDisplaysTheOldPartButFieryInitReturnsWithoutDisplaying() throws Exception {
        Harness h = harness(0x60, 0x40);
        ObjectServices services = (ObjectServices) field(h.root, "services");
        PatternSpriteRenderer renderer = services.renderManager().getBossExplosionRenderer();
        h.step();
        h.root.appendRenderCommands(List.of());
        verifyNoInteractions(renderer); // GBall_Main returns without DisplaySprite.
        killBoss(h);
        h.step();
        var link = parts(h.manager).getFirst();
        h.root.appendRenderCommands(List.of());
        link.appendRenderCommands(List.of());
        verify(renderer).drawFrameIndex(0, h.root.getX(), h.root.getY(), false, false);
        verify(renderer).drawFrameIndex(1, link.getX(), link.getY(), false, false, 0);
        clearInvocations(renderer);
        h.step();
        assertEquals(12, h.root.getOnScreenHalfWidth());
        assertEquals(12, link.getOnScreenHalfWidth());
        h.root.appendRenderCommands(List.of());
        link.appendRenderCommands(List.of());
        verifyNoInteractions(renderer); // Expl_Main tail-jumps to sound, no display call.
        h.step();
        h.root.appendRenderCommands(List.of());
        link.appendRenderCommands(List.of());
        verify(renderer, times(2)).drawFrameIndex(eq(0), anyInt(), anyInt(), eq(false), eq(false));

        clearInvocations(renderer);
        var explosion = new S1BossExplosion(
                new ObjectSpawn(100, 100, Sonic1ObjectIds.EXPLOSION, 0, 0, false, 0), services);
        explosion.setServices(services);
        explosion.appendRenderCommands(List.of());
        explosion.update(1, null);
        explosion.appendRenderCommands(List.of());
        verifyNoInteractions(renderer);
        explosion.update(2, null);
        explosion.appendRenderCommands(List.of());
        verify(renderer).drawFrameIndex(0, 100, 100, false, false);
    }

    @Test
    void captureDuringDeploymentRestoresEveryNativeComponentAndReplaysIntoSwing() throws Exception {
        Harness h = harness(0x60, 0x40);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        h.step();
        for (int n = 0; n < 23; n++) h.step();
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(h.manager.rewindSnapshottable());
        rewind.register(h.rng);
        CompositeSnapshot snapshot = rewind.capture();
        List<List<String>> expected = forward(h, 120);
        rewind.restore(snapshot);
        GHZBossWreckingBall restored = h.manager.getActiveObjects().stream()
                .filter(GHZBossWreckingBall.class::isInstance).map(GHZBossWreckingBall.class::cast)
                .findFirst().orElseThrow();
        assertNotSame(h.root, restored);
        assertEquals(5, parts(h.manager).size());
        assertForwardEquals(expected, forward(h, 120));
    }

    @Test
    void earlyDefeatUsesCurrentRadiusAndRestoreReplaysAfterTheAnchorHasRetired() throws Exception {
        Harness h = harness(0x60, 0x40);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        h.step();
        for (int n = 0; n < 64; n++) h.step();
        killBoss(h);
        for (int n = 0; n < 43; n++) h.step();
        var ball = ball(h.manager);
        assertNull(field(h.boss, "wreckingBall"));
        assertEquals(22, integer(ball, "distance"), "first defeat anchor pass increments64 to65, then43 decrements");
        RewindRegistry rewind = new RewindRegistry();
        rewind.register(h.manager.rewindSnapshottable());
        rewind.register(h.rng);
        CompositeSnapshot snapshot = rewind.capture();
        List<List<String>> expected = forward(h, 90);
        rewind.restore(snapshot);
        var restoredBall = ball(h.manager);
        Sonic1GHZBossInstance restoredBoss = h.manager.getActiveObjects().stream()
                .filter(Sonic1GHZBossInstance.class::isInstance).map(Sonic1GHZBossInstance.class::cast).findFirst().orElseThrow();
        assertNotSame(ball, restoredBall);
        assertSame(restoredBoss, field(restoredBall, "parent"));
        assertNull(field(restoredBoss, "wreckingBall"));
        assertForwardEquals(expected, forward(h, 90));
    }

    private static void assertForwardEquals(List<List<String>> expected, List<List<String>> actual) {
        for (int pass = 0; pass < expected.size(); pass++) {
            assertEquals(expected.get(pass), actual.get(pass), "restored native state at forward pass " + pass);
        }
    }

    private List<List<String>> forward(Harness h, int passes) {
        List<List<String>> result = new ArrayList<>();
        for (int n = 0; n < passes; n++) {
            h.step();
            List<String> state = h.manager.getActiveObjects().stream()
                    .filter(AbstractObjectInstance.class::isInstance).map(AbstractObjectInstance.class::cast)
                    .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                    .map(part -> part.getClass().getName() + ":" + part.getSlotIndex() + ":"
                            + part.getSpawn().objectId() + ":" + part.getX() + ":" + part.getY() + ":"
                            + (part instanceof TouchResponseProvider touch ? touch.getCollisionFlags() : 0)
                            + nativeState(part))
                    .toList();
            List<String> withRng = new ArrayList<>(state);
            withRng.add("rng:" + h.rng.getSeed());
            result.add(withRng);
        }
        return result;
    }

    private static String nativeState(AbstractObjectInstance part) {
        try {
            if (part instanceof GHZBossWreckingBall) {
                return ":" + integer(part, "angle") + ":" + integer(part, "swingParam")
                        + ":" + integer(part, "anchorFrame") + ":" + integer(part, "anchorFrameCounter")
                        + ":" + integer(part, "verticalOffset") + ":" + bool(part, "chainFullyExtended")
                        + ":" + integer(part, "explosionFrame") + ":" + integer(part, "explosionDuration")
                        + ":" + bool(part, "displayThisPass");
            }
            if (part instanceof GHZBossWreckingBall.NativeComponent) {
                return ":" + integer(part, "distance") + ":" + integer(part, "ballFrame")
                        + ":" + bool(part, "parentDefeated") + ":" + bool(part, "convertedToExplosion")
                        + ":" + integer(part, "explosionFrame") + ":" + integer(part, "explosionDuration")
                        + ":" + bool(part, "displayThisPass");
            }
            return "";
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Harness harness(int bossSlot, int rootSlot) throws Exception {
        ObjectManager[] owner = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        GameRng rng = new GameRng(GameRng.Flavour.S1_S2);
        DebugOverlayManager debug = mock(DebugOverlayManager.class);
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
        when(renderer.isReady()).thenReturn(true);
        when(renderManager.getRenderer(anyString())).thenReturn(renderer);
        when(renderManager.getBossExplosionRenderer()).thenReturn(renderer);
        GameModule module = GameModuleRegistry.getCurrent();
        GameStateManager gameState = mock(GameStateManager.class);
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return owner[0]; }
            @Override public Camera camera() { return camera; }
            @Override public RomByteReader romReader() { return rom; }
            @Override public DebugOverlayManager debugOverlay() { return debug; }
            @Override public GameRng rng() { return rng; }
            @Override public GameModule gameModule() { return module; }
            @Override public GameStateManager gameState() { return gameState; }
            @Override public ObjectRenderManager renderManager() { return renderManager; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(), 0, null,
                module.createTouchResponseTable(GameServices.level().getWorldSession().getDataSource()),
                GraphicsManager.getInstance(), camera, services);
        owner[0] = manager;
        Sonic1GHZBossInstance boss = manager.createDynamicObjectAtSlot(
                () -> new Sonic1GHZBossInstance(new ObjectSpawn(128, 80, Sonic1ObjectIds.GHZ_BOSS, 0, 0, false, 0)), bossSlot);
        boss.getState().routineSecondary = 6;
        boss.getState().xVel = boss.getState().yVel = 0;
        write(boss, "timer", 10000);
        GHZBossWreckingBall root = manager.createDynamicObjectAtSlot(() -> new GHZBossWreckingBall(boss), rootSlot);
        boss.adoptWreckingBallForRewind(root);
        return new Harness(manager, boss, root, rng);
    }

    private static void killBoss(Harness h) throws Exception {
        h.boss.getState().hitCount = 1;
        h.boss.onPlayerAttack(null, null);
        assertTrue(h.boss.getState().defeated);
        assertNotEquals(0, h.boss.getState().renderFlags & 0x80,
                "the real killing-hit transition publishes the native defeated status bit");
        write(h.boss, "timer", 10000); // keep the ship in its native defeat wait during the bounded check
    }

    private record Harness(ObjectManager manager, Sonic1GHZBossInstance boss, GHZBossWreckingBall root, GameRng rng) {
        void step() { manager.update(0, null, List.of(), 0, false); }
    }

    private static List<GHZBossWreckingBall.NativeComponent> parts(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(GHZBossWreckingBall.NativeComponent.class::isInstance)
                .map(GHZBossWreckingBall.NativeComponent.class::cast)
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }

    private static GHZBossWreckingBall.NativeComponent ball(ObjectManager manager) {
        return parts(manager).stream().filter(part -> {
            try { return bool(part, "ball"); } catch (Exception e) { throw new AssertionError(e); }
        }).findFirst().orElseThrow();
    }

    private void assertBytes(int address, int... expected) {
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], rom.readU8(address + index), "ROM byte at " + Integer.toHexString(address + index));
        }
    }

    private static Object field(Object object, String name) throws Exception { return declared(object, name).get(object); }
    private static int integer(Object object, String name) throws Exception { return declared(object, name).getInt(object); }
    private static boolean bool(Object object, String name) throws Exception { return declared(object, name).getBoolean(object); }
    private static void write(Object object, String name, int value) throws Exception { declared(object, name).setInt(object, value); }
    private static Field declared(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field result = type.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
