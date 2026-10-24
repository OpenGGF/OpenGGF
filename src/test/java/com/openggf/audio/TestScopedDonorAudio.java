package com.openggf.audio;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** A scoped donor registration borrows a key and gives back exactly what it found. */
class TestScopedDonorAudio {
    @AfterEach
    void resetSharedAudio() {
        AudioManager.getInstance().resetState();
        AudioManager.getInstance().setBackend(new NullAudioBackend());
    }

    @Test
    void closingRestoresThePriorDonorRouteAndBindingsForThatKey() throws Exception {
        AudioManager audio = freshAudio();
        Sonic1AudioProfile profile = new Sonic1AudioProfile();
        SmpsLoader prior = mock(SmpsLoader.class);
        SmpsLoader scene = mock(SmpsLoader.class);
        // Stands in for CrossGameFeatureProvider's donor, its music map and its sound bindings.
        audio.registerDonorLoader("s1", prior, new DacData(Map.of(), Map.of()), profile.getSequencerConfig(), profile);
        audio.registerDonorMusicMap("s1", Map.of(GameMusic.INVINCIBILITY, 0x87));
        audio.registerDonorSound(GameSound.RING_SPILL, "s1", 0xC6);
        audio.registerDonorSound(GameSound.JUMP, "s3k", 0x62);
        Object priorSource = sources(audio).get("s1");
        Map<GameSound, Object> priorSounds = new EnumMap<>(sounds(audio));
        Object priorMusic = music(audio).get("s1");

        ScopedDonorAudio scope = ScopedDonorAudio.register(audio, "s1", scene, new DacData(Map.of(), Map.of()),
                profile.getSequencerConfig(), profile);
        audio.playDonorMusic("s1", 0x81);
        verify(scene).loadMusic(0x81);
        verify(prior, never()).loadMusic(anyInt());
        // Changes made under the scope do not outlive it either.
        audio.registerDonorMusicMap("s1", Map.of(GameMusic.SUPER, 0x8C));
        audio.registerDonorSound(GameSound.RING_SPILL, "s1", 0x01);

        scope.close();
        scope.close();
        audio.playDonorMusic("s1", 0x81);
        verify(prior).loadMusic(0x81);
        verify(scene, times(1)).loadMusic(0x81);
        assertTrue(audio.playDonorMusic("s1", GameMusic.INVINCIBILITY));
        verify(prior).loadMusic(0x87);
        assertFalse(audio.playDonorMusic("s1", GameMusic.SUPER), "the scope's music map is gone");

        Object restored = sources(audio).get("s1");
        for (String component : new String[] {"loader", "dac", "config", "profile", "sfxPolicyProfile"}) {
            assertSame(component(priorSource, component), component(restored, component),
                    "restored donor " + component);
        }
        assertTrue((long) component(restored, "generation") > (long) component(priorSource, "generation"),
                "the restored route gets a fresh generation, so no scoped asset can answer for it");
        assertEquals(priorSounds, sounds(audio), "sound bindings for every key are as before");
        assertSame(priorMusic, music(audio).get("s1"));
    }

    @Test
    void aScopeOverAnUnregisteredKeyLeavesNoRouteBehind() throws Exception {
        AudioManager audio = freshAudio();
        Sonic1AudioProfile profile = new Sonic1AudioProfile();
        SmpsLoader scene = mock(SmpsLoader.class);
        ScopedDonorAudio scope = ScopedDonorAudio.register(audio, "s1", scene, new DacData(Map.of(), Map.of()),
                profile.getSequencerConfig(), profile);
        audio.playDonorMusic("s1", 0x81);
        verify(scene).loadMusic(0x81);
        scope.close();
        assertFalse(sources(audio).containsKey("s1"));
        audio.playDonorMusic("s1", 0x81);
        verify(scene, times(1)).loadMusic(0x81);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedCloseKeepsPublishedRouteAndBothBindingMapsAndConsumesTheToken(boolean error) throws Exception {
        AudioManager audio = AudioManager.createSilent();
        var priorProfile = new Sonic1AudioProfile();
        var prior = mock(SmpsLoader.class);
        var scene = mock(SmpsLoader.class);
        var dac = new DacData(Map.of(), Map.of());
        audio.registerDonorLoader("s1", prior, dac, priorProfile.getSequencerConfig(), priorProfile);
        audio.registerDonorMusicMap("s1", Map.of(GameMusic.INVINCIBILITY, 0x87));
        audio.registerDonorSound(GameSound.JUMP, "s1", 0xA0);
        audio.registerDonorSound(GameSound.ROLLING, "unrelated", 0xA3);
        Object oldSource = sources(audio).get("s1");
        Map<GameSound, Object> oldSounds = Map.copyOf(sounds(audio));
        Object oldMusic = music(audio).get("s1");
        var scope = ScopedDonorAudio.register(audio, "s1", scene, dac,
                priorProfile.getSequencerConfig(), priorProfile);
        audio.registerDonorSound(GameSound.JUMP, "s1", 0xA1);
        audio.registerDonorSound(GameSound.RING_SPILL, "s1", 0xA2);
        audio.registerDonorMusicMap("s1", Map.of(GameMusic.SUPER, 0x8C));
        Throwable failure = error ? new AssertionError("restore backend identity")
                : new IllegalStateException("restore backend identity");
        int[] restores = {0};
        // Installing this backend does not itself call the donor registration seam.
        audio.setBackend(new NullAudioBackend() {
            @Override public void registerAudioProfileCoordHandlers(GameAudioProfile profile) {
                restores[0]++;
                assertSame(priorProfile, profile);
                assertTrue(Thread.holdsLock(audio));
                assertDoesNotThrow(() -> {
                    Object published = sources(audio).get("s1");
                    assertSame(prior, component(published, "loader"));
                    assertEquals((long) component(oldSource, "generation") + 2,
                            component(published, "generation"));
                    assertEquals(oldSounds, sounds(audio), "bindings precede backend restoration");
                    assertSame(oldMusic, music(audio).get("s1"));
                    assertEquals(Boolean.TRUE, field(audio, "sourceMutationInProgress"));
                });
                if (failure instanceof Error fatal) throw fatal;
                throw (RuntimeException) failure;
            }
        });
        try {
            Throwable actual = error ? assertThrows(AssertionError.class, scope::close)
                    : assertThrows(IllegalStateException.class, scope::close);
            assertSame(failure, actual);
            Object published = sources(audio).get("s1");
            assertSame(prior, component(published, "loader"));
            assertEquals(oldSounds, sounds(audio));
            assertSame(oldMusic, music(audio).get("s1"));
            assertEquals(Boolean.FALSE, field(audio, "sourceMutationInProgress"));
            scope.close();
            assertEquals(1, restores[0], "a failed first close consumes its token before restoration");
            assertSame(published, sources(audio).get("s1"));
        } finally {
            audio.destroy();
        }
    }

    @Test
    void laterRegistrationDoesNotMakeAnOlderScopeStaleOrPreventRestoration() throws Exception {
        AudioManager audio = spy(AudioManager.class);
        var config = new Sonic1AudioProfile().getSequencerConfig();
        SmpsLoader prior = mock(SmpsLoader.class);
        SmpsLoader scene = mock(SmpsLoader.class);
        SmpsLoader later = mock(SmpsLoader.class);
        DacData dac = new DacData(Map.of(), Map.of());
        audio.registerDonorLoader("s1", prior, dac, config);
        audio.registerDonorSound(GameSound.JUMP, "s1", 0xA0);
        try {
            var scope = ScopedDonorAudio.register(audio, "s1", scene, dac, config, null);
            audio.registerDonorLoader("s1", later, dac, config);
            audio.registerDonorSound(GameSound.JUMP, "s1", 0xA1);
            scope.close();
            assertSame(prior, component(sources(audio).get("s1"), "loader"));
            assertEquals(4L, component(sources(audio).get("s1"), "generation"));
            assertEquals(0xA0, component(sounds(audio).get(GameSound.JUMP), "sfxId"));
            audio.playDonorMusic("s1", 0x81);
            verify(prior).loadMusic(0x81);
            verify(later, never()).loadMusic(anyInt());
        } finally {
            audio.destroy();
        }
    }

    @Test
    void capturePrecedesVirtualRegistrationAndIsNotResampledAfterItsCallback() throws Exception {
        AudioManager audio = spy(AudioManager.class);
        var config = new Sonic1AudioProfile().getSequencerConfig();
        SmpsLoader prior = mock(SmpsLoader.class);
        SmpsLoader scene = mock(SmpsLoader.class);
        DacData dac = new DacData(Map.of(), Map.of());
        audio.registerDonorLoader("s1", prior, dac, config);
        audio.registerDonorSound(GameSound.JUMP, "s1", 0xA0);
        doAnswer(invocation -> {
            assertTrue(Thread.holdsLock(audio), "the synchronized registration wrapper holds the manager monitor at its virtual callback");
            audio.registerDonorSound(GameSound.JUMP, "s1", 0xA1);
            return invocation.callRealMethod();
        }).when(audio).registerDonorLoader("s1", scene, dac, config, null);
        try {
            var scope = ScopedDonorAudio.register(audio, "s1", scene, dac, config, null);
            assertEquals(0xA1, component(sounds(audio).get(GameSound.JUMP), "sfxId"));
            scope.close();
            assertSame(prior, component(sources(audio).get("s1"), "loader"));
            assertEquals(0xA0, component(sounds(audio).get(GameSound.JUMP), "sfxId"));
        } finally {
            audio.destroy();
        }
    }

    private static AudioManager freshAudio() {
        AudioManager audio = AudioManager.getInstance();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        return audio;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sources(AudioManager audio) throws ReflectiveOperationException {
        return (Map<String, Object>) field(audio, "donorAudioSources");
    }

    @SuppressWarnings("unchecked")
    private static Map<GameSound, Object> sounds(AudioManager audio) throws ReflectiveOperationException {
        return (Map<GameSound, Object>) field(audio, "donorSoundBindings");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> music(AudioManager audio) throws ReflectiveOperationException {
        return (Map<String, Object>) field(audio, "donorMusicBindings");
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Object component(Object record, String name) throws ReflectiveOperationException {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                return accessor.invoke(record);
            }
        }
        throw new NoSuchFieldException(name);
    }
}
