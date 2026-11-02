package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestCutsceneMusicSlotTiming {
    @BeforeEach void initGraphics() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void resetGraphics() { GraphicsManager.getInstance().resetState(); }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"AIZ2,false", "AIZ2,true", "CNZ2B,false", "CNZ2B,true"})
    void themeCountdownBeginsOnFirstReachableSlotPass(String scene, boolean lowerHole) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera();
        if (scene.equals("CNZ2B")) {
            camera.setX((short) 0x45C0);
            camera.setY((short) 0x0720);
        }
        AudioManager audio = mock(AudioManager.class);
        List<Integer> played = new ArrayList<>();
        TestObjectServices services = new TestObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public void playMusic(int id) { played.add(id); }
        }.withCamera(camera).withAudioManager(audio);
        var registry = new Sonic3kObjectRegistry();
        int firstSlot = registry.objectSlotLayout().firstDynamicSlot();
        int actorSlot = firstSlot + 6;
        ObjectManager manager = new ObjectManager(List.of(), registry, 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        manager.reset(camera.getX());
        if (!lowerHole) {
            for (int slot = firstSlot; slot < actorSlot; slot++) manager.addDynamicObjectAtSlot(new InertObject(), slot);
        }
        AbstractObjectInstance actor = scene.equals("AIZ2")
                ? new CutsceneKnucklesAiz2Instance(new ObjectSpawn(0, 0, 0x82, 4, 0, false, 0))
                : new CutsceneKnucklesCnz2BInstance(new ObjectSpawn(0x46C0, 0x0800, 0x82, 0x10, 0, false, 0));
        manager.addDynamicObjectAtSlot(actor, actorSlot);
        try (var art = mockStatic(AizIntroArtLoader.class)) {
            manager.update(camera.getX(), null, List.of(), 0, false);
            verify(audio, times(lowerHole ? 0 : 1)).fadeOutMusic();
            SongFadeTransitionInstance fade = manager.activeObjectsOfType(SongFadeTransitionInstance.class)
                    .stream().findFirst().orElseThrow();
            assertEquals(lowerHole ? firstSlot : actorSlot + 1, fade.getSlotIndex());
            for (int frame = 1; frame <= 89; frame++) manager.update(camera.getX(), null, List.of(), frame, false);
            assertEquals(List.of(), played);
            manager.update(camera.getX(), null, List.of(), 90, false);
            assertEquals(lowerHole ? List.of() : List.of(Sonic3kMusic.KNUCKLES.id), played);
            if (lowerHole) manager.update(camera.getX(), null, List.of(), 91, false);
            assertEquals(List.of(Sonic3kMusic.KNUCKLES.id), played);
            verify(audio, times(1)).fadeOutMusic();
        }
    }

    @org.junit.jupiter.api.Test
    void aiz1InitializationAllocatesMusicBeforeItsRock() throws Exception {
        var objects = mock(ObjectManager.class);
        List<com.openggf.level.objects.ObjectInstance> allocated = new ArrayList<>();
        doAnswer(call -> { allocated.add(call.getArgument(0)); return null; })
                .when(objects).addDynamicObject(any());
        doAnswer(call -> { allocated.add(call.getArgument(0)); return null; })
                .when(objects).addDynamicObjectAfterCurrent(any());
        var services = new TestObjectServices() {
            @Override public ObjectManager objectManager() { return objects; }
        };
        var actor = new CutsceneKnucklesAiz1Instance(new ObjectSpawn(0, 0, 0x82, 0, 0, false, 0));
        actor.setServices(services);
        var init = CutsceneKnucklesAiz1Instance.class.getDeclaredMethod("routine0Init");
        init.setAccessible(true);
        init.invoke(actor);
        assertEquals(2, allocated.size());
        org.junit.jupiter.api.Assertions.assertInstanceOf(SongFadeTransitionInstance.class, allocated.getFirst());
        org.junit.jupiter.api.Assertions.assertFalse(allocated.get(1) instanceof SongFadeTransitionInstance);
    }

    private static final class InertObject extends AbstractObjectInstance {
        InertObject() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "slot filler"); }
        @Override public boolean isPersistent() { return true; }
        @Override public void update(int frame, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
