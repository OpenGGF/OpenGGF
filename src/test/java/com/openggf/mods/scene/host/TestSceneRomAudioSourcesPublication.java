package com.openggf.mods.scene.host;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioTestFixtures;
import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.data.Rom;
import com.openggf.game.GameModule;
import org.junit.jupiter.api.Test;

import static com.openggf.audio.AudioTestFixtures.EMPTY_DAC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** ROM-free lifecycle controls; source selection runs through the real manager and scope. */
class TestSceneRomAudioSourcesPublication {
    @Test
    void leasesShareWithinOneManagerAndProfileButNotAcrossManagerIdentity() {
        SceneRomLibrary library = library();
        AudioManager a = spy(AudioManager.class);
        AudioManager b = spy(AudioManager.class);
        GameAudioProfile profile = profile();
        a.setAudioProfile(profile);
        b.setAudioProfile(profile);
        SmpsLoader priorA = register(a, "prior-a");
        SmpsLoader priorB = register(b, "prior-b");
        try (var sources = new SceneRomAudioSources(library)) {
            var first = sources.acquire(a, "s1");
            var shared = sources.acquire(a, "s1");
            var independent = sources.acquire(b, "s1");
            assertTrue(first.active());
            assertTrue(shared.active());
            assertTrue(independent.active());
            verify(library, times(2)).module("s1");
            first.close();
            assertFalse(first.active());
            assertTrue(shared.active());
            a.playDonorMusic("s1", 0x81);
            verify(priorA, never()).loadMusic(0x81);
            shared.close();
            a.playDonorMusic("s1", 0x81);
            verify(priorA).loadMusic(0x81);
            assertTrue(independent.active());
            independent.close();
            b.playDonorMusic("s1", 0x81);
            verify(priorB).loadMusic(0x81);
        } finally {
            a.destroy(); b.destroy();
        }
    }

    @Test
    void equalButDifferentProfileStartsANewEpochAndOldCloseCannotRestoreItsRoute() {
        SceneRomLibrary library = library();
        AudioManager audio = spy(AudioManager.class);
        GameAudioProfile first = profile();
        GameAudioProfile second = profile();
        assertEquals(first, second, "positive equality control distinguishes identity from equals");
        assertNotSame(first, second);
        audio.setAudioProfile(first);
        SmpsLoader original = register(audio, "original");
        SmpsLoader scene = library.module("s1").getAudioProfile().createSmpsLoader(library.sourceRom("s1"));
        clearInvocations(library);
        try (var sources = new SceneRomAudioSources(library)) {
            var stale = sources.acquire(audio, "s1");
            audio.setAudioProfile(second);
            SmpsLoader current = register(audio, "current");
            assertFalse(stale.active());
            var fresh = sources.acquire(audio, "s1");
            assertTrue(fresh.active());
            stale.close();
            stale.close();
            audio.playDonorMusic("s1", 0x81);
            verify(scene).loadMusic(0x81);
            verify(current, never()).loadMusic(anyInt());
            verify(original, never()).loadMusic(anyInt());
            fresh.close();
            audio.playDonorMusic("s1", 0x81);
            verify(current).loadMusic(0x81);
            verify(original, never()).loadMusic(anyInt());
            verify(library, times(2)).module("s1");
        } finally {
            audio.destroy();
        }
    }

    @Test
    void abandonmentConsumesLeaseWithoutRestoringEvenWhenOwnerCloses() {
        SceneRomLibrary library = library();
        AudioManager audio = spy(AudioManager.class);
        audio.setAudioProfile(profile());
        SmpsLoader prior = register(audio, "prior");
        SmpsLoader scene = library.module("s1").getAudioProfile().createSmpsLoader(library.sourceRom("s1"));
        try (var sources = new SceneRomAudioSources(library)) {
            var abandoned = sources.acquire(audio, "s1");
            abandoned.abandon();
            abandoned.close();
            assertFalse(abandoned.active());
        }
        try {
            audio.playDonorMusic("s1", 0x81);
            verify(scene).loadMusic(0x81);
            verify(prior, never()).loadMusic(anyInt());
        } finally {
            audio.destroy();
        }
    }

    @Test
    void acquisitionObservesProfileBeforeModuleCallbacksAndLaterCloseAbandonsChangedEpoch() {
        SceneRomLibrary library = library();
        AudioManager audio = spy(AudioManager.class);
        GameAudioProfile before = profile();
        GameAudioProfile after = profile();
        audio.setAudioProfile(before);
        SmpsLoader original = register(audio, "original");
        GameModule module = library.module("s1");
        GameAudioProfile donor = module.getAudioProfile();
        when(module.getAudioProfile()).thenAnswer(invocation -> {
            audio.setAudioProfile(after);
            return donor;
        });
        try (var sources = new SceneRomAudioSources(library)) {
            var lease = sources.acquire(audio, "s1");
            assertSame(after, audio.getAudioProfile());
            assertFalse(lease.active(), "acquire does not resample the epoch after module preparation");
            SmpsLoader current = register(audio, "post-acquire");
            lease.close();
            sources.close();
            audio.playDonorMusic("s1", 0x81);
            verify(current).loadMusic(0x81);
            verify(original, never()).loadMusic(anyInt());
        } finally {
            audio.destroy();
        }
    }

    private static SceneRomLibrary library() {
        SceneRomLibrary library = mock(SceneRomLibrary.class);
        GameModule module = mock(GameModule.class);
        SmpsLoader loader = mock(SmpsLoader.class);
        when(loader.loadDacData()).thenReturn(EMPTY_DAC);
        when(module.getAudioProfile()).thenReturn(new AudioTestFixtures.StubAudioProfile(loader) {
            @Override public SmpsSequencerConfig getSequencerConfig() {
                return new SmpsSequencerConfig.Builder().build();
            }
        });
        when(library.module("s1")).thenReturn(module);
        when(library.sourceRom("s1")).thenReturn(new Rom());
        return library;
    }

    private static GameAudioProfile profile() {
        return new AudioTestFixtures.StubAudioProfile(new AudioTestFixtures.StubSmpsLoader()) {
            @Override public SmpsSequencerConfig getSequencerConfig() {
                return new SmpsSequencerConfig.Builder().build();
            }
            @Override public boolean equals(Object other) { return other instanceof GameAudioProfile; }
            @Override public int hashCode() { return 1; }
        };
    }

    private static SmpsLoader register(AudioManager audio, String name) {
        SmpsLoader loader = mock(SmpsLoader.class, name);
        audio.registerDonorLoader("s1", loader, EMPTY_DAC, new SmpsSequencerConfig.Builder().build());
        return loader;
    }
}
