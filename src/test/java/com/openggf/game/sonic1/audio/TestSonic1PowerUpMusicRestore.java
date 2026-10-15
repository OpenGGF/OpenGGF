package com.openggf.game.sonic1.audio;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.game.GameServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@FullReset
@ExtendWith(SingletonResetExtension.class)
class TestSonic1PowerUpMusicRestore {
    private AudioManager audio;
    private Sonic player;

    @BeforeEach
    void setUp() throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic1GameModule());
        audio = GameServices.audio();
        audio.setBackend(new NullAudioBackend());
        audio.setAudioProfile(new Sonic1AudioProfile());
        LevelManager level = mock(LevelManager.class);
        when(level.getCurrentLevelMusicId()).thenReturn(Sonic1Music.GHZ.id);
        GameplayModeContext mode = SessionManager.getCurrentGameplayMode();
        Field field = GameplayModeContext.class.getDeclaredField("levelManager");
        field.setAccessible(true);
        field.set(mode, level);
        GameServices.gameState().setScreenLocked(false);
        player = new Sonic("test", (short) 0, (short) 0);
    }

    @Test
    void nativeBossScreenLockPreservesBossMusicOnInvincibilityExpiry() {
        // DLE_GHZ3 starts the boss song and sets f_lockscreen, represented by
        // currentBossId's lock latch rather than AbstractLevelEventManager's flag.
        GameServices.gameState().setCurrentBossId(Sonic1ObjectIds.GHZ_BOSS);
        expireInvincibility();
        assertTrue(musicRequests().isEmpty());
    }

    @Test
    void defeatedBossStillOwnsTheMusicUntilTheScreenLockIsReleased() {
        GameServices.gameState().setCurrentBossId(Sonic1ObjectIds.GHZ_BOSS);
        GameServices.gameState().setCurrentBossId(0);
        assertTrue(GameServices.gameState().isScreenLocked());
        expireInvincibility();
        assertTrue(musicRequests().isEmpty(), "Sonic_Display tests f_lockscreen even after defeat");
    }

    @Test
    void releasingTheNativeLockAllowsTheLevelMusicToResume() {
        GameServices.gameState().setCurrentBossId(Sonic1ObjectIds.GHZ_BOSS);
        GameServices.gameState().setCurrentBossId(0);
        GameServices.gameState().setScreenLocked(false);
        expireInvincibility();
        assertEquals(List.of(Sonic1Music.GHZ.id), musicRequests());
    }

    private void expireInvincibility() {
        player.setInvincibleFrames(1);
        player.tickStatus();
        assertEquals(0, player.getInvincibleFrames(), "the music lock does not freeze protection");
    }

    private List<Integer> musicRequests() {
        return audio.commandTimeline().entries().stream().map(entry -> entry.command())
                .filter(AudioCommand.PlayMusic.class::isInstance)
                .map(AudioCommand.PlayMusic.class::cast).map(AudioCommand.PlayMusic::musicId).toList();
    }
}
