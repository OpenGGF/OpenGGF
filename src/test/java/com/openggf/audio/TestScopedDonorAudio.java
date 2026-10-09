package com.openggf.audio;

import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
