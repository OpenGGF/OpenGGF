package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.objects.bosses.IczEndBossEggCapsuleInstance;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kPostResultsMusic {
    @AfterEach
    void reset() {
        Aiz2BossEndSequenceState.reset();
    }

    @Test
    void iczResultsRestoreCorridorMusicExactlyOnce() throws Exception {
        RecordingServices services = new RecordingServices(Sonic3kMusic.ICZ2.id);
        Class<?> owner = Class.forName(IczEndBossEggCapsuleInstance.class.getName()
                + "$IczEndBossResultsScreenObjectInstance");
        var constructor = owner.getDeclaredConstructor();
        constructor.setAccessible(true);
        S3kResultsScreenObjectInstance results =
                (S3kResultsScreenObjectInstance) constructor.newInstance();
        var act = S3kResultsScreenObjectInstance.class.getDeclaredField("act");
        act.setAccessible(true);
        act.setInt(results, 1);
        results.setServices(services);
        Method exit = results.getClass().getDeclaredMethod("onExitReady");
        exit.setAccessible(true);
        assertEquals(List.of(), services.music);
        exit.invoke(results);
        assertEquals(List.of(Sonic3kMusic.ICZ2.id), services.music,
                "loc_71DE2 restores ICZ2 for the corridor before StartNewLevel LBZ1");
        exit.invoke(results);
        assertEquals(List.of(Sonic3kMusic.ICZ2.id), services.music);
    }

    @Test
    void aizWaitsForResultsThenRestoresWalkMusicExactlyOnce() {
        RecordingServices services = new RecordingServices(Sonic3kMusic.AIZ2.id);
        Aiz2BossEndSequenceController controller = new Aiz2BossEndSequenceController(0x4880, 0);
        controller.setServices(services);
        controller.update(0, services.player);
        assertEquals(List.of(), services.music);
        Aiz2BossEndSequenceState.releaseEggCapsule();
        for (int frame = 1; frame < 5; frame++) controller.update(frame, services.player);
        assertEquals(List.of(Sonic3kMusic.AIZ2.id), services.music,
                "AIZEndBoss_StartPostDefeatCutscene restores the song once before walking");
    }

    private static final class RecordingServices extends TestObjectServices {
        private final int levelMusic;
        private final List<Integer> music = new ArrayList<>();
        private final TestablePlayableSprite player =
                new TestablePlayableSprite("sonic", (short) 0x4900, (short) 0);

        RecordingServices(int levelMusic) {
            this.levelMusic = levelMusic;
            withGameState(new GameStateManager());
            withCamera(new com.openggf.camera.Camera());
            withIsolatedObjectManager();
        }

        @Override public ObjectPlayerQuery playerQuery() {
            return new ObjectPlayerQuery(() -> player, List::of);
        }
        @Override public int getCurrentLevelMusicId() { return levelMusic; }
        @Override public void playMusic(int musicId) { music.add(musicId); }
    }
}
