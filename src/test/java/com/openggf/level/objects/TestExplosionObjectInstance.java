package com.openggf.level.objects;

import com.openggf.game.GameId;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.Sonic1GameModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestExplosionObjectInstance {

    /**
     * ROM Obj27 (S2/S3K): {@code anim_frame_duration} init 3, reload 7, delete
     * at mapping_frame 5 (docs/s2disasm/s2.asm:46672-46684,
     * docs/skdisasm/sonic3k.asm:42195-42205). The first {@code update} is the
     * same-frame Init-&gt;Main fall-through (the ROM-spawn frame), so counting
     * that first update as game-frame 1, the explosion self-deletes on the 36th
     * update — i.e. 35 game frames after spawn, matching the EHZ1/SCZ/WFZ trace
     * timelines (e.g. appeared f153, removed f188 = 35). Default (no game module
     * wired) uses the S2/S3K value (3).
     */
    @Test
    public void explosionSelfDeletesAtRomExactFrameForS2() {
        ExplosionObjectInstance explosion =
                new ExplosionObjectInstance(0x27, 100, 200, null);
        explosion.setServices(new TestObjectServices()); // gameModule() == null -> default 3
        for (int update = 1; update <= 35; update++) {
            explosion.update(update, (PlayableEntity) null);
            assertFalse(explosion.isDestroyed(),
                    "explosion must survive through update " + update + " (35 frames live)");
        }
        explosion.update(36, (PlayableEntity) null);
        assertTrue(explosion.isDestroyed(),
                "explosion must self-delete 35 game frames after spawn (ROM-exact)");
    }

    /**
     * ROM Obj27 (S1): {@code obTimeFrame} init 7 (frame 0 held 8 game frames)
     * vs S2/S3K's 3, so S1 lives 39 game frames — modelled via
     * {@link Sonic1GameModule#explosionInitialAnimDuration()} =&gt; 7
     * (docs/s1disasm/_incObj/24, 27 &amp; 3F Explosions.asm ExItem_Main). With
     * the first update as game-frame 1, S1 self-deletes on the 40th update.
     */
    @Test
    public void explosionSelfDeletesAtRomExactFrameForS1() {
        Sonic1GameModule s1 = new Sonic1GameModule();
        assertEquals(7, s1.explosionInitialAnimDuration());
        assertEquals(GameId.S1, s1.getGameId());

        ExplosionObjectInstance explosion =
                new ExplosionObjectInstance(0x27, 100, 200, null);
        explosion.setServices(new TestObjectServices().withGameModule(s1));
        for (int update = 1; update <= 39; update++) {
            explosion.update(update, (PlayableEntity) null);
            assertFalse(explosion.isDestroyed(),
                    "S1 explosion must survive through update " + update + " (39 frames live)");
        }
        explosion.update(40, (PlayableEntity) null);
        assertTrue(explosion.isDestroyed(), "S1 explosion must self-delete 39 game frames after spawn");
    }

    @Test
    public void deferredExplosionSoundPlaysWhenServicesAreInjectedAfterConstruction() {
        RecordingObjectServices services = new RecordingObjectServices();

        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, 100, 200, null, 77);
        assertEquals(0, services.playedSfxCount);

        explosion.setServices(services);

        assertEquals(1, services.playedSfxCount);
        assertEquals(77, services.lastSfxId);
    }

    @Test
    public void constructionContextPlaybackDoesNotReplayWhenServicesAreInjected() {
        RecordingObjectServices services = new RecordingObjectServices();

        AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(services);
        try {
            ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, 100, 200, null, 88);
            assertEquals(1, services.playedSfxCount);

            explosion.setServices(services);

            assertEquals(1, services.playedSfxCount);
            assertEquals(88, services.lastSfxId);
        } finally {
            AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
        }
    }

    private static final class RecordingObjectServices extends TestObjectServices {
        private int playedSfxCount;
        private int lastSfxId = -1;

        @Override
        public void playSfx(int soundId) {
            playedSfxCount++;
            lastSfxId = soundId;
        }
    }
}


