package com.openggf.game.sonic2.objects.bosses;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.driver.SmpsDriverTestAccess;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.audio.Sonic2SmpsSequencerConfig;
import com.openggf.game.sonic2.audio.smps.Sonic2SmpsLoader;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
@ExtendWith(SingletonResetExtension.class)
class TestSonic2OOZBossLaserSound {
    @Test
    void shooterFireSubmitsTheRomLaserBurstAndProductionAudioAdmitsIt() throws Exception {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        audio.setRom(TestEnvironment.currentRom());
        var profile = new Sonic2AudioProfile();
        audio.setAudioProfile(profile);
        audio.setSoundMap(profile.getSoundMap());
        List<Integer> requests = new ArrayList<>();
        var services = new TestObjectServices() {
            @Override public void playSfx(int soundId) {
                requests.add(soundId);
                audio.playSfx(soundId);
            }
        }.withIsolatedObjectManager();
        var boss = new Sonic2OOZBossInstance(new ObjectSpawn(
                0x2940, 0x0238, Sonic2ObjectIds.OOZ_BOSS, 0, 0, false, 0));
        boss.setServices(services);
        setIntField(boss, "bossSubtype", 0x04);
        setIntField(boss, "bossCountdown", 0x0238);
        boss.getState().routineSecondary = 0x06;
        boss.getState().y = 0x0238;
        boss.getState().yFixed = 0x0238_0000;
        boss.getState().yVel = -0x80;

        // Obj55_LaserShooter_Fire allocates subtype 8 then submits SndID_LaserBurst.
        boss.update(0, null);

        assertEquals(List.of(Sonic2Sfx.LASER_BURST.id), requests);
        assertTrue(services.objectManager().getActiveObjects().stream()
                .anyMatch(object -> object.getSpawn().subtype() == 0x08));
        audio.presentFrame(PresentationMode.FORWARD);
        assertTrue(audio.commandTimeline().entries().stream()
                .map(entry -> entry.command())
                .anyMatch(command -> command instanceof AudioCommand.PlaySfx sfx
                        && sfx.sfxId() == Sonic2Sfx.LASER_BURST.id),
                "the production S2 sound mailbox must admit raw $EA");
        boss.update(1, null);
        assertEquals(1, requests.size(), "waiting for the next target does not replay the firing cue");
    }

    @Test
    void laserBurstRomProgramProducesAudiblePcm() throws Exception {
        var loader = new Sonic2SmpsLoader(TestEnvironment.currentRom());
        var data = loader.loadSfx(Sonic2Sfx.LASER_BURST.id);
        assertNotNull(data);
        assertNotNull(data.getVoice(0), "EA uses Sound6A_6F_Laser_Voices");
        var driver = SmpsDriverTestAccess.create(44100);
        try {
            var sequencer = new SmpsSequencer(data, loader.loadDacData(), driver,
                    AudioManager.getInstance(), Sonic2SmpsSequencerConfig.CONFIG);
            driver.addSequencer(sequencer, true);
            short[] pcm = new short[44100];
            SmpsDriverTestAccess.read(driver, pcm);
            int peak = 0;
            for (short sample : pcm) peak = Math.max(peak, Math.abs((int) sample));
            assertTrue(peak > 100, "Laser Burst PCM peak=" + peak);
        } finally {
            SmpsDriverTestAccess.close(driver);
        }
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }
}
