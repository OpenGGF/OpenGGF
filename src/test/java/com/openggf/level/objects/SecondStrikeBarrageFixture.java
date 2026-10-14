package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Source-owned barrage interactions against one actual native S3K SST and ROM touch table. */
final class SecondStrikeBarrageFixture {
    final ObjectManager manager;
    final Sonic player;
    final List<Integer> sounds = new ArrayList<>();
    final GameStateManager score = new GameStateManager();
    final TestObjectServices services;
    private final URLClassLoader creator;
    boolean flatFloor;

    static URLClassLoader compile(Path temporary) throws Exception {
        return com.openggf.mods.code.SecondStrikeSourceFixture.compile(temporary.resolve("barrage"),
                com.openggf.mods.code.SecondStrikeSourceFixture.ENEMIES);
    }

    SecondStrikeBarrageFixture(URLClassLoader creator) throws Exception {
        this.creator = creator;
        HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro().build();
        player = new Sonic("sonic", (short) 500, (short) 300);
        var camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 1000);
        when(camera.getHeight()).thenReturn((short) 800);
        when(camera.getFocusedSprite()).thenReturn(player);
        var module = new Sonic3kGameModule();
        services = new TestObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public void playSfx(int sound) { sounds.add(sound); }
            @Override public TerrainSurface floorSurface(int x, int y, TerrainSurface.Layer layer, TerrainSurface.Path path) {
                return flatFloor ? new TerrainSurface(true, y, 0, 0) : super.floorSurface(x, y, layer, path);
            }
        }.withCamera(camera).withDebugOverlay(mock(DebugOverlayManager.class))
                .withGameState(score).withGameModule(module).withLevelManager(GameServices.level());
        var table = new TouchResponseTable(RomByteReader.fromRom(TestEnvironment.currentRom()),
                Sonic3kConstants.TOUCH_SIZES_ADDR, Sonic3kConstants.TOUCH_SIZES_COUNT);
        var keys = new com.openggf.mods.code.ModObjectKeyRegistry(List.of(
                new com.openggf.mods.code.ModObjectKeyRegistry.Registration("second-strike", "second-strike:guard", (spawn, registry) -> {
                    try {
                        return (ObjectInstance) creator.loadClass("secondstrike.enemies.GuardBadnik")
                                .getConstructor(ObjectSpawn.class).newInstance(spawn);
                    } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                })));
        var registry = new com.openggf.mods.code.ModDecoratedObjectRegistry(module.createObjectRegistry(), keys);
        manager = new ObjectManager(List.of(), registry, 0, null, table,
                GraphicsManager.getInstance(), camera, services);
        manager.setRewindClassResolver(com.openggf.mods.code.SecondStrikeSourceFixture.ownedResolver(creator));
        services.withDirectObjectManager(manager);
        bounds();
        place(player, 500, 300);
    }

    void bounds() { AbstractObjectInstance.updateCameraBounds(0, 0, 1000, 800, 0); }
    static ObjectSpawn spawn(String key, int x, int y) {
        return new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, "second-strike", "second-strike:" + key);
    }
    AbstractObjectInstance enemy(String name, String key, int x, int y) throws Exception {
        var object = (AbstractObjectInstance) creator.loadClass("secondstrike.enemies." + name)
                .getConstructor(ObjectSpawn.class).newInstance(spawn(key, x, y));
        manager.addDynamicObject(object);
        assertTrue(object.getSlotIndex() >= 0 && !object.isDestroyed());
        return object;
    }
    AbstractObjectInstance gunner() throws Exception { return enemy("ArtilleryBadnik", "artillery", 400, 300); }
    List<AbstractObjectInstance> shots() {
        return manager.getActiveObjects().stream().filter(o -> o.getClass().getSimpleName().equals("CommittedShot"))
                .map(AbstractObjectInstance.class::cast).filter(o -> !o.isDestroyed()).toList();
    }
    int velocity(AbstractObjectInstance shot, String method) throws Exception {
        return (int) shot.getClass().getMethod(method).invoke(shot);
    }
    static TouchResponseProvider touch(AbstractObjectInstance object) { return (TouchResponseProvider) object; }
    static void ticks(AbstractObjectInstance object, PlayableEntity player, int count) {
        for (int i = 0; i < count; i++) object.update(i, player);
    }
    void firstRelease(AbstractObjectInstance gunner) { ticks(gunner, player, 37); }
    long fireCues() { return sounds.stream().filter(s -> s == 0x4d).count(); }
    static void place(AbstractPlayableSprite sprite, int x, int y) {
        NativePositionOps.writeXPosPreserveSubpixel(sprite, x);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, y);
    }
    void attack(AbstractObjectInstance object, int frame) {
        place(player, object.getX(), object.getY());
        player.setAir(true); player.setRolling(true);
        player.setAnimationId(com.openggf.game.sonic3k.constants.Sonic3kAnimationIds.ROLL.id());
        player.setXSpeed((short) 0x280); player.setYSpeed((short) 0x400); player.setGSpeed((short) 0x280);
        manager.snapshotTouchResponseState();
        manager.runTouchResponsesForPlayer(player, frame);
    }
    void exhaustLeaving(int free) {
        while (manager.getActiveObjectSlotCount() < manager.getObjectSlotCapacity() - free) {
            var filler = new AbstractObjectInstance(new ObjectSpawn(800, 700, 0, 0, 0, false, 700), "slot filler") {
                @Override public void appendRenderCommands(List<GLCommand> commands) { }
            };
            manager.addDynamicObject(filler);
            assertFalse(filler.isDestroyed());
        }
    }
}
