package com.openggf.game.sonic2.levelselect;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class TestSonic2LevelSelectSoundMailbox {
    @Test
    void soundTestSubmitsNativeMusicSfxAndSystemRequestsThroughMusicMailbox() throws Exception {
        var audio = mock(AudioManager.class);
        try (var services = mockStatic(GameServices.class)) {
            services.when(GameServices::audio).thenReturn(audio);
            var manager = new LevelSelectManager();
            var selected = LevelSelectManager.class.getDeclaredField("selectedIndex");
            selected.setAccessible(true);
            selected.setInt(manager, 21);
            var value = LevelSelectManager.class.getDeclaredField("soundTestValue");
            value.setAccessible(true);
            var select = LevelSelectManager.class.getDeclaredMethod("handleSelect");
            select.setAccessible(true);
            // Native 81=2P results; A0+ SFX; F9 fade; FF stop; unmapped values
            // are still requests for the driver's own range classification.
            for (int request : new int[] {0x81, 0xA0, 0xF9, 0xFF, 0x80}) {
                value.setInt(manager, request - 0x80);
                select.invoke(manager);
                verify(audio).playMusicMailboxNativeRequest(request);
            }
            verifyNoMoreInteractions(audio);
        }
    }
}
