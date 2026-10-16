package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.*;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic mapping geometry exercises native allocation/ride contracts, not ROM route geometry. */
class TestWestCampaignBridgeLifecycle {
    @AfterEach void clearGraphics() {
        GraphicsManager.getInstance().resetState();
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }
    private record World(ObjectManager manager, TestObjectServices services) { }
    private static World world() {
        var config = SonicConfigurationService.createStandalone();
        var camera = new Camera(config);
        GraphicsManager.getInstance().initHeadless();
        var render = mock(ObjectRenderManager.class);
        var pieces = IntStream.range(0, 16).mapToObj(i -> new SpriteMappingPiece(
                i * 8 - 64, 0, 1, 1, 0, false, false, 0)).toList();
        var frame = new SpriteMappingFrame(pieces);
        var sheet = new ObjectSpriteSheet(new Pattern[0], java.util.Collections.nCopies(9, frame), 0, 0);
        when(render.getSheet(anyString())).thenReturn(sheet);
        var services = new TestObjectServices() {
            @Override public int romZoneId() { return 2; }
            @Override public ObjectRenderManager renderManager() { return render; }
        }.withConfiguration(config).withCamera(camera);
        var manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        services.withDirectObjectManager(manager);
        manager.reset(0);
        return new World(manager, services);
    }
    private static CollapsingBridgeObjectInstance bridge(World world) {
        return world.manager.createDynamicObject(() -> new CollapsingBridgeObjectInstance(
                new ObjectSpawn(1000, 500, 15, 0x12, 0, true, 500, 7), true));
    }
    @Test void nativeRiderWaveReleasesBothPlayersAfterTheWarnedBrittleCountdown() throws Exception {
        var world = world(); var bridge = bridge(world);
        var sonic = new Sonic("sonic", (short) 0, (short) 0);
        var tails = new Tails("tails", (short) 0, (short) 0);
        world.services.camera().setFocusedSprite(sonic);
        world.services.withSidekicks(List.of(tails));
        for (var player : List.of(sonic, tails)) {
            player.setCentreX((short) 1000); player.setCentreY((short) 460);
            player.setAir(false); player.setOnObject(true);
            world.manager.forceRidingObjectForBootstrap(player, bridge);
            bridge.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);
        }
        for (int tick = 0; tick < 17; tick++) bridge.update(tick, sonic);
        assertEquals(1, integer(bridge, "state"));
        assertEquals(1, integer(bridge, "collapseTimer"));
        bridge.update(17, sonic);
        assertEquals(2, integer(bridge, "state"));
        assertTrue(bridge.isSolidFor(sonic)); assertTrue(bridge.isSolidFor(tails));
        assertTrue(world.manager.getActiveObjects().stream().anyMatch(CollapsingBridgeObjectInstance.BridgeFragment.class::isInstance));
        for (int tick = 18; tick < 80; tick++) bridge.update(tick, sonic);
        for (var player : List.of(sonic, tails)) {
            assertTrue(player.getAir()); assertFalse(player.isOnObject());
            assertFalse(world.manager.isRidingObject(player, bridge));
        }
    }
    @Test void fragmentAllocationExhaustionCannotTrapTheParentInAnInfiniteSolidWait() throws Exception {
        var world = world(); var bridge = bridge(world);
        for (int i = 0; i < 89; i++) world.manager.createDynamicObject(Dummy::new);
        assertEquals(90, world.manager.getActiveObjects().size());
        set(bridge, "state", 1); set(bridge, "collapseTimer", 1);
        bridge.update(0, null);
        assertEquals(2, integer(bridge, "state"));
        assertEquals(90, world.manager.getActiveObjects().size());
        for (int tick = 1; tick < 64; tick++) bridge.update(tick, null);
        assertEquals(3, integer(bridge, "state"));
        assertTrue(world.manager.getActiveObjects().size() <= 90);
    }
    @Test void productionObjectManagerRestoreReplaysTheSameCountdownAndFragmentGraph() throws Exception {
        var world = world(); var source = bridge(world);
        set(source, "state", 1); set(source, "collapseTimer", 5);
        var rewind = new RewindRegistry(); rewind.register(world.manager.rewindSnapshottable());
        var snapshot = rewind.capture();
        for (int tick = 0; tick < 5; tick++) source.update(tick, null);
        int expectedFragments = world.manager.getActiveObjects().size();
        assertTrue(expectedFragments > 1, "the native fragment frame must actually allocate children");
        int expectedFrame = integer(source, "fragmentFrameIndex");
        rewind.restore(snapshot);
        var restored = world.manager.getActiveObjects().stream().filter(CollapsingBridgeObjectInstance.class::isInstance)
                .map(CollapsingBridgeObjectInstance.class::cast).findFirst().orElseThrow();
        assertNotSame(source, restored); assertEquals(5, integer(restored, "collapseTimer"));
        assertEquals(true, field(restored, "brittleSupport"));
        for (int tick = 0; tick < 5; tick++) restored.update(tick, null);
        assertEquals(expectedFragments, world.manager.getActiveObjects().size());
        assertEquals(expectedFrame, integer(restored, "fragmentFrameIndex"));
        assertEquals(2, integer(restored, "state"));
    }
    private static final class Dummy extends AbstractObjectInstance {
        Dummy() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "capacity"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static int integer(Object object, String name) throws Exception { return (int) field(object, name); }
    private static void set(Object object, String name, int value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.setInt(object, value);
    }
}
