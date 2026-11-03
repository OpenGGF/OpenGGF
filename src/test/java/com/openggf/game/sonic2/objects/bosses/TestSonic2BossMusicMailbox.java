package com.openggf.game.sonic2.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TestSonic2BossMusicMailbox {
    @Test
    void mechaSonicFadeUsesSfxMailboxBeforeSixtyOneUpdateMusicWait() throws Exception {
        var services = new Services();
        when(services.camera.getX()).thenReturn((short) 0x224);
        var boss = ObjectConstructionContext.construct(services,
                () -> new Sonic2MechaSonicInstance(spawn(0xAF)));
        boss.setServices(services);
        invoke(boss, "updateWaitCamera");
        assertEquals(List.of(0xF9), services.sfx);
        assertDelayedMusic(boss, services, 60, Sonic2Music.BOSS.id);
    }

    @Test
    void wfzFadeUsesSfxMailboxBeforeNinetyOneUpdateMusicWait() throws Exception {
        var services = new Services();
        var boss = ObjectConstructionContext.construct(services,
                () -> new Sonic2WFZBossInstance(spawn(0xC5)));
        boss.setServices(services);
        invoke(boss, "updateSpawnChildren");
        assertEquals(List.of(0xF9), services.sfx);
        for (int n = 0; n < 90; n++) invoke(boss, "updateSpawnChildren");
        assertEquals(List.of(), services.music);
        invoke(boss, "updateSpawnChildren");
        assertEquals(List.of(Sonic2Music.BOSS.id), services.music);
    }

    @Test
    void deathEggRobotFadeUsesSfxMailboxAfterHeadSignal() throws Exception {
        var services = new Services();
        var boss = ObjectConstructionContext.construct(services,
                () -> new Sonic2DeathEggRobotInstance(spawn(0xC7)));
        boss.setServices(services);
        var head = mock(Sonic2DeathEggRobotInstance.HeadChild.class);
        when(head.isBodyMiscSignaled()).thenReturn(true);
        Field field = boss.getClass().getDeclaredField("head");
        field.setAccessible(true);
        field.set(boss, head);
        invoke(boss, "updateWaitEggman");
        assertEquals(List.of(0xF9), services.sfx);
        assertDelayedMusic(boss, services, 60, Sonic2Music.FINAL_BOSS.id);
    }

    private static void assertDelayedMusic(Object boss, Services services, int wait, int track) throws Exception {
        for (int n = 0; n < wait; n++) invoke(boss, "updateCountdown");
        assertEquals(List.of(), services.music);
        invoke(boss, "updateCountdown");
        assertEquals(List.of(track), services.music);
    }

    private static ObjectSpawn spawn(int id) {
        return new ObjectSpawn(0x300, 0x400, id, 0, 0, false, 0);
    }

    private static void invoke(Object owner, String method) throws Exception {
        var target = owner.getClass().getDeclaredMethod(method);
        target.setAccessible(true);
        target.invoke(owner);
    }

    private static final class Services extends StubObjectServices {
        final Camera camera = mock(Camera.class);
        final GameStateManager state = new GameStateManager();
        final List<Integer> sfx = new ArrayList<>();
        final List<Integer> music = new ArrayList<>();
        @Override public Camera camera() { return camera; }
        @Override public GameStateManager gameState() { return state; }
        @Override public void playSfx(int id) { sfx.add(id); }
        @Override public void playMusic(int id) { music.add(id); }
        @Override public void fadeOutMusic() { throw new AssertionError("Bypasses native SFX admission"); }
    }
}
