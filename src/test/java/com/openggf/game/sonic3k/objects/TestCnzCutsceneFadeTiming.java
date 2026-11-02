package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TestCnzCutsceneFadeTiming {
    @Test
    void nativeThemeTransitionIncludesInitializationInNinetyOneEntries() {
        assertTransitionFiresOnOwnEntry(SongFadeTransitionInstance.transitionTo(0x1F), 91, 0x1F);
    }

    @Test
    void nativeLevelRestoreIncludesInitializationInOneHundredTwentyOneEntries() {
        assertTransitionFiresOnOwnEntry(SongFadeTransitionInstance.toLevelMusic(0x08), 121, 0x08);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void actualCutsceneExitSubmitsRestoreWithOneHundredTwentyOneOwnEntries(boolean secondCutscene) throws Exception {
        var camera = new com.openggf.camera.Camera();
        camera.setX((short) (secondCutscene ? 0x45C0 : 0x1C00));
        camera.setY((short) (secondCutscene ? 0x0720 : 0x0176));
        var objects = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        List<com.openggf.level.objects.ObjectInstance> spawned = new ArrayList<>();
        org.mockito.Mockito.doAnswer(call -> { spawned.add(call.getArgument(0)); return null; })
                .when(objects).addDynamicObject(org.mockito.ArgumentMatchers.any());
        List<Integer> played = new ArrayList<>();
        TestObjectServices services = new TestObjectServices() {
            @Override public com.openggf.level.objects.ObjectManager objectManager() { return objects; }
            @Override public void playMusic(int id) { played.add(id); }
        }.withCamera(camera).withAudioManager(mock(AudioManager.class));
        com.openggf.level.objects.AbstractObjectInstance owner = secondCutscene
                ? new CutsceneKnucklesCnz2BInstance(new com.openggf.level.objects.ObjectSpawn(0x4800, 0x800, 0x82, 0x10, 0, false, 0))
                : new CutsceneKnucklesCnz2AInstance(new com.openggf.level.objects.ObjectSpawn(0x1F00, 0x300, 0x82, 0x0C, 0, false, 0));
        owner.setServices(services);
        var phase = owner.getClass().getDeclaredField("phase");
        phase.setAccessible(true);
        Object exitPhase = java.util.Arrays.stream(phase.getType().getEnumConstants())
                .filter(value -> value.toString().equals(secondCutscene ? "EXIT_RIGHT" : "FINAL_JUMP"))
                .findFirst().orElseThrow();
        phase.set(owner, exitPhase);
        owner.update(0, null);
        SongFadeTransitionInstance restore = spawned.stream().filter(SongFadeTransitionInstance.class::isInstance)
                .map(SongFadeTransitionInstance.class::cast).findFirst().orElseThrow();
        restore.setServices(services);
        for (int entry = 0; entry < 120; entry++) restore.update(entry, null);
        assertTrue(played.isEmpty());
        restore.update(120, null);
        assertEquals(List.of(com.openggf.game.sonic3k.audio.Sonic3kMusic.CNZ2.id), played);
    }

    private static void assertTransitionFiresOnOwnEntry(SongFadeTransitionInstance transition,
                                                       int entries, int music) {
        List<Integer> played = new ArrayList<>();
        TestObjectServices services = new TestObjectServices() {
            @Override public void playMusic(int musicId) { played.add(musicId); }
        }.withAudioManager(mock(AudioManager.class));
        transition.setServices(services);
        for (int entry = 1; entry < entries; entry++) transition.update(entry, null);
        assertTrue(played.isEmpty());
        assertFalse(transition.isDestroyed());
        transition.update(entries, null);
        assertEquals(List.of(music), played);
        assertTrue(transition.isDestroyed());
    }
}
