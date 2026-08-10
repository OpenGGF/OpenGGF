package com.openggf.audio.presentation;

import com.openggf.audio.AudioManager;
import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.presentation.AudioPresentationCommand.AddSmpsSfx;
import com.openggf.audio.presentation.AudioPresentationCommand.MusicVoiceEntry;
import com.openggf.audio.presentation.AudioPresentationCommand.PushMusicOverride;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceMusic;
import com.openggf.audio.presentation.AudioPresentationCommand.ReplaceRawPcm;
import com.openggf.audio.presentation.AudioPresentationCommand.StartSampleSfx;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import com.openggf.audio.rewind.SmpsDriverSnapshot;
import com.openggf.audio.smps.AbstractSmpsData;
import com.openggf.audio.smps.CoordFlagContext;
import com.openggf.audio.smps.CoordFlagHandler;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.smps.SmpsSfxData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kCoordFlagHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAudioPresentationCommandResolver {
    private static final DacData EMPTY_DAC =
            new DacData(Collections.emptyMap(), Collections.emptyMap(), 297);

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
    }

    @Test
    void resolvesBaseDonorAndFallbackMusic() {
        Fixture fixture = fixture();
        fixture.sources.baseMusic = music(0x81);
        fixture.sources.donorMusic = music(0x91);

        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x91, AudioCommand.MusicRoute.DONOR_SMPS, true, "s3k"));
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x71, AudioCommand.MusicRoute.FALLBACK_WAV, false, null));

        List<AudioPresentationCommand> commands = drain(fixture.queue);
        assertInstanceOf(ReplaceMusic.class, commands.get(0));
        assertInstanceOf(PushMusicOverride.class, commands.get(1));
        assertInstanceOf(ReplaceMusic.class, commands.get(2));
        assertEquals(AudioSourceDescriptor.baseMusic(0x81),
                ((ReplaceMusic) commands.get(0)).music().sourceDescriptor());
        assertEquals(AudioSourceDescriptor.donorMusic("s3k", 0x91),
                ((PushMusicOverride) commands.get(1)).music().sourceDescriptor());
        assertEquals(AudioSourceDescriptor.fallbackMusic(0x71),
                ((ReplaceMusic) commands.get(2)).music().sourceDescriptor());
    }

    @Test
    void resolvesBaseNameBaseIdDonorFallbackAndAlternatingRingSfx() {
        Fixture fixture = fixture();
        fixture.sources.baseSfx = sfx(0xA0, (byte) 0xF2);
        fixture.sources.namedSfx = sfx(0xA1, (byte) 0xF2);
        fixture.sources.donorSfx = sfx(0xB0, (byte) 0xF2);

        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                -1, "JUMP", AudioCommand.SfxRoute.BASE_SMPS_NAME, 0.5f, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xB0, null, AudioCommand.SfxRoute.DONOR_SMPS, 1.25f, "s3k"));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                -1, "SKID", AudioCommand.SfxRoute.FALLBACK_NAME, 0.75f, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                -1, "RING_LEFT", AudioCommand.SfxRoute.RING_RESOLVED, 1.0f, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                -1, "RING_RIGHT", AudioCommand.SfxRoute.RING_RESOLVED, 1.0f, null));

        List<AudioPresentationCommand> commands = drain(fixture.queue);
        assertEquals(8, commands.size());
        assertEquals(SmpsAssetKey.Route.BASE_ID,
                ((AddSmpsSfx) commands.get(0)).source().assetKey().route());
        assertEquals(SmpsAssetKey.Route.BASE_NAME,
                ((AddSmpsSfx) commands.get(1)).source().assetKey().route());
        assertEquals(SmpsAssetKey.Route.DONOR_ID,
                ((AddSmpsSfx) commands.get(2)).source().assetKey().route());
        assertInstanceOf(StartSampleSfx.class, commands.get(3));
        assertEquals(new AudioPresentationCommand.ResetRingAlternation(false),
                commands.get(4));
        assertInstanceOf(StartSampleSfx.class, commands.get(5));
        assertEquals(new AudioPresentationCommand.ResetRingAlternation(true),
                commands.get(6));
        assertInstanceOf(StartSampleSfx.class, commands.get(7));
    }

    @Test
    void musicOwnedSmpsSfxAttachesOnlyWhenRegistryAppliesCommand() {
        Fixture fixture = fixture();
        fixture.sources.baseMusic = music(0x81);
        fixture.sources.baseSfx = sfx(0xA0, (byte) 0xF2);
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
        List<AudioPresentationCommand> commands = drain(fixture.queue);

        AudioVoiceRegistry registry = fixture.registry();
        registry.apply(commands.get(0));
        SmpsCompositeVoice music =
                (SmpsCompositeVoice) registry.orderedVoiceAt(0);
        assertEquals(1, music.driver().captureSnapshot().sequencers().size());

        registry.apply(commands.get(1));

        assertEquals(2, music.driver().captureSnapshot().sequencers().size());
    }

    @Test
    void noMusicSmpsSfxCreatesOneStandaloneComposite() {
        Fixture fixture = fixture();
        fixture.sources.baseSfx = sfx(0xA0, (byte) 0xF2);
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));

        AudioVoiceRegistry registry = fixture.registry();
        drain(fixture.queue).forEach(registry::apply);

        assertEquals(1, registry.orderedVoiceCount());
        assertEquals(1, ((SmpsCompositeVoice) registry.orderedVoiceAt(0))
                .driver().captureSnapshot().sequencers().size());
    }

    @Test
    void resolvesFadeStopRestoreTempoSpeedAndOverrideCommands() {
        Fixture fixture = fixture();
        fixture.resolver.submit(new AudioCommand.FadeOutMusic(7, 3));
        fixture.resolver.submit(new AudioCommand.StopMusic());
        fixture.resolver.submit(new AudioCommand.StopAllSfx());
        fixture.resolver.submit(new AudioCommand.RestoreMusic(
                AudioCommand.RestoreCause.EXPLICIT));
        fixture.resolver.submit(new AudioCommand.SetSpeedShoes(true));
        fixture.resolver.submit(new AudioCommand.SetSpeedMultiplier(8));
        fixture.resolver.submit(new AudioCommand.ChangeMusicTempo(5));

        List<AudioPresentationCommand> commands = drain(fixture.queue);
        assertEquals(List.of(
                        AudioPresentationCommand.FadeMusic.class,
                        AudioPresentationCommand.StopMusic.class,
                        AudioPresentationCommand.StopAllSfx.class,
                        AudioPresentationCommand.RestoreMusicOverride.class,
                        AudioPresentationCommand.SetSpeedShoes.class,
                        AudioPresentationCommand.SetSpeedMultiplier.class,
                        AudioPresentationCommand.ChangeMusicTempo.class),
                commands.stream().map(Object::getClass).toList());
    }

    @Test
    void resolvesEndOverrideTempoAndRingResetToExplicitImmutableRecords() {
        Fixture fixture = fixture();
        fixture.resolver.submit(new AudioCommand.EndMusicOverride(0x91));
        fixture.resolver.submit(new AudioCommand.ChangeMusicTempo(3));
        fixture.resolver.submit(new AudioCommand.ResetRingAlternation(false));

        List<AudioPresentationCommand> commands = drain(fixture.queue);
        assertEquals(new AudioPresentationCommand.EndMusicOverride(0x91),
                commands.get(0));
        assertEquals(new AudioPresentationCommand.ChangeMusicTempo(3),
                commands.get(1));
        assertEquals(new AudioPresentationCommand.ResetRingAlternation(false),
                commands.get(2));
    }

    @Test
    void resolvesRawSegaPcmReplaceAndStop() {
        Fixture fixture = fixture();
        byte[] pcm = {0, (byte) 0x80, (byte) 0xFF};

        fixture.resolver.submitRawPcm(pcm, 16_500);
        fixture.resolver.stopRawPcm();

        List<AudioPresentationCommand> commands = drain(fixture.queue);
        ReplaceRawPcm replace = assertInstanceOf(
                ReplaceRawPcm.class, commands.get(0));
        assertTrue(replace.voice().snapshot().assetId().startsWith("sega-pcm:"));
        assertInstanceOf(AudioPresentationCommand.StopRawPcm.class,
                commands.get(1));
        assertNotNull(fixture.factory.resolvePcm(
                replace.voice().snapshot().assetId()));
    }

    @Test
    void rawPcmIdentityDoesNotAliasArraysWithTheSameArraysHashCode() {
        Fixture fixture = fixture();

        fixture.resolver.submitRawPcm(new byte[] {0, 31}, 16_500);
        fixture.resolver.submitRawPcm(new byte[] {1, 0}, 16_500);

        List<AudioPresentationCommand> commands = drain(fixture.queue);
        String first = ((ReplaceRawPcm) commands.get(0))
                .voice().snapshot().assetId();
        String second = ((ReplaceRawPcm) commands.get(1))
                .voice().snapshot().assetId();
        assertNotSame(first, second);
        assertFalse(first.equals(second));
    }

    @Test
    void systemMusicCommandIsExplicitlyRejectedWithoutQueueMutation() {
        Fixture fixture = fixture();

        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0xFE, AudioCommand.MusicRoute.SYSTEM_COMMAND, false, null));

        assertEquals(0, fixture.queue.size());
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("system music command"));
    }

    @Test
    void structuralOverflowDrainsAtInjectedOwnerBoundary() {
        Fixture fixture = fixture();

        for (int index = 0;
             index < AudioPresentationCommandQueue.CAPACITY + 17;
             index++) {
            fixture.resolver.submit(
                    new AudioCommand.EndMusicOverride(index));
        }

        assertEquals(AudioPresentationCommandQueue.CAPACITY,
                fixture.synchronouslyApplied.size());
        assertEquals(17, fixture.queue.size());
        assertEquals(new AudioPresentationCommand.EndMusicOverride(0),
                fixture.synchronouslyApplied.get(0));
    }

    @Test
    void queueCapacityFailureIsNotMisreportedAsAssetResolutionFailure() {
        Fixture fixture = fixture();
        fixture.sources.baseMusic = music(0x81);
        for (int index = 0;
             index < AudioPresentationCommandQueue.CAPACITY;
             index++) {
            fixture.resolver.submit(
                    new AudioCommand.EndMusicOverride(index));
        }
        fixture.ownerThreadBoundary.set(false);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> fixture.resolver.submit(new AudioCommand.PlayMusic(
                        0x81, AudioCommand.MusicRoute.BASE_SMPS,
                        false, null)));

        assertTrue(failure.getMessage().contains("owner boundary"));
        assertTrue(fixture.warnings.isEmpty());
    }

    @Test
    void malformedFallbackAssetWarnsAndRejectsOnlyThatVoice() {
        Fixture fixture = fixture();
        fixture.assets.malformed.add("sfx/skid.wav");

        fixture.resolver.submit(new AudioCommand.PlaySfx(
                -1, "SKID", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                -1, "JUMP", AudioCommand.SfxRoute.FALLBACK_NAME, 1.0f, null));

        List<AudioPresentationCommand> commands = drain(fixture.queue);
        assertEquals(1, commands.size());
        assertInstanceOf(StartSampleSfx.class, commands.get(0));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void resolvedCommandsNeverContainConsumerRunnableOrCallbackFields() {
        for (Class<?> commandType :
                AudioPresentationCommand.class.getPermittedSubclasses()) {
            for (Field field : commandType.getDeclaredFields()) {
                assertFalse(Runnable.class.isAssignableFrom(field.getType()));
                assertFalse(Consumer.class.isAssignableFrom(field.getType()));
                assertFalse(java.util.function.Function.class
                        .isAssignableFrom(field.getType()));
            }
        }
    }

    @Test
    void resolvingSmpsSfxDoesNotMutateTheOwnerDriverBeforeQueueApply() {
        Fixture fixture = fixture();
        fixture.sources.baseMusic = music(0x81);
        fixture.sources.baseSfx = sfx(0xA0, (byte) 0xF2);
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));
        AudioVoiceRegistry registry = fixture.registry();
        registry.apply(drain(fixture.queue).get(0));
        SmpsDriver owner =
                ((SmpsCompositeVoice) registry.orderedVoiceAt(0)).driver();

        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));

        assertEquals(1, owner.captureSnapshot().sequencers().size());
    }

    @Test
    void queuedSfxContainsOnlyAssetKeyAndPrimitiveMetadata() {
        for (Field field : ResolvedSmpsSfxSource.class.getDeclaredFields()) {
            assertTrue(field.getType().isPrimitive()
                            || field.getType() == SmpsAssetKey.class,
                    field.toString());
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
    }

    @Test
    void mutatingOriginalLoadedObjectsAfterWarmAndQueueDoesNotChangeAppliedSfx() {
        Fixture fixture = fixture();
        MutableSfxData original = sfx(0xA0, (byte) 0xF2);
        HashSet<Integer> mutableEndFlags = new HashSet<>(List.of(0xEE));
        fixture.sources.baseConfig = new SmpsSequencerConfig.Builder()
                .extraTrkEndFlags(mutableEndFlags)
                .fmSfxTakeoverMode(
                        SmpsSequencerConfig.FmSfxTakeoverMode.REGISTER_SEQUENCE)
                .build();
        fixture.sources.baseSfx = original;
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
        AudioPresentationCommand command = drain(fixture.queue).get(0);

        original.getData()[0x40] = (byte) 0xE9;
        mutableEndFlags.clear();
        fixture.sources.baseDac.samples.getOrDefault(1, new byte[0]);

        AudioVoiceRegistry registry = fixture.registry();
        assertDoesNotThrow(() -> registry.apply(command));
        SmpsDriverSnapshot snapshot =
                ((SmpsCompositeVoice) registry.orderedVoiceAt(0))
                        .driver().captureSnapshot();
        assertEquals((byte) 0xF2,
                snapshot.sequencers().get(0).smpsData().getData()[0x40]);
        assertNotSame(original, snapshot.sequencers().get(0).smpsData());
        assertEquals(Set.of(0xEE), snapshot.sequencers().get(0)
                .config().getExtraTrkEndFlags());
        assertEquals(SmpsSequencerConfig.FmSfxTakeoverMode.REGISTER_SEQUENCE,
                snapshot.sequencers().get(0).config().getFmSfxTakeoverMode());
    }

    @Test
    void reconstructedConfigsShareTheSessionCoordFlagHandlerOwner() {
        Fixture fixture = fixtureWithS3kOwner();
        MutableSfxData first = sfx(0xA0, (byte) 0xF2);
        MutableSfxData second = sfx(0xA1, (byte) 0xF2);
        fixture.factory.warmSmpsSfxAsset(
                new SmpsAssetKey("s3k", SmpsAssetKey.Route.BASE_ID,
                        0xA0, null),
                first, EMPTY_DAC, s3kConfig(new ArbitraryHandler()));
        fixture.factory.warmSmpsSfxAsset(
                new SmpsAssetKey("s3k", SmpsAssetKey.Route.BASE_ID,
                        0xA1, null),
                second, EMPTY_DAC, s3kConfig(new ArbitraryHandler()));
        SmpsDriver owner = new SmpsDriver(48_000);

        SmpsSequencer firstSeq = fixture.factory.instantiateCached(
                fixture.factory.resolveSmpsSfx(1,
                        new SmpsAssetKey("s3k", SmpsAssetKey.Route.BASE_ID,
                                0xA0, null),
                        1 << 16, 0x70, 0, 1, 64), owner);
        SmpsSequencer secondSeq = fixture.factory.instantiateCached(
                fixture.factory.resolveSmpsSfx(2,
                        new SmpsAssetKey("s3k", SmpsAssetKey.Route.BASE_ID,
                                0xA1, null),
                        1 << 16, 0x70, 0, 1, 64), owner);

        assertSame(firstSeq.getConfig().getCoordFlagHandler(),
                secondSeq.getConfig().getCoordFlagHandler());
        assertSame(fixture.handlers.handlerFor("s3k"),
                firstSeq.getConfig().getCoordFlagHandler());
    }

    @Test
    void offOwnerThreadInstantiationIsRejectedBeforeCacheLookup() throws Exception {
        Fixture fixture = fixture();
        AtomicBoolean owner = fixture.ownerThreadBoundary;
        owner.set(false);
        AtomicInteger assetLookup = fixture.factory.cacheLookupCountForTesting();
        int before = assetLookup.get();
        ResolvedSmpsSfxSource missing = fixture.factory.resolveSmpsSfx(
                1, new SmpsAssetKey("base", SmpsAssetKey.Route.BASE_ID,
                        0xEE, null),
                1 << 16, 0x70, 0, 1, 64);

        assertThrows(IllegalStateException.class,
                () -> fixture.factory.instantiateCached(
                        missing, new SmpsDriver(48_000)));
        assertEquals(before, assetLookup.get());
    }

    @Test
    void cacheMissAtApplyRejectsWithoutLoaderRomDecodeOrFallbackCalls() {
        Fixture fixture = fixture();
        AtomicInteger calls = fixture.sources.calls;
        ResolvedSmpsSfxSource missing = fixture.factory.resolveSmpsSfx(
                99, new SmpsAssetKey("base", SmpsAssetKey.Route.BASE_ID,
                        0xEE, null),
                1 << 16, 0x70, 0, 1, 64);
        AudioVoiceRegistry registry = fixture.registry();
        int before = calls.get() + fixture.assets.opens.get();

        registry.apply(new AddSmpsSfx(missing));

        assertEquals(0, registry.orderedVoiceCount());
        assertEquals(before, calls.get() + fixture.assets.opens.get());
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void replacementAndOverrideCommandsBeforeSfxSelectTheFinalCurrentDriver() {
        Fixture fixture = fixture();
        fixture.sources.baseMusic = music(0x81);
        fixture.sources.donorMusic = music(0x91);
        fixture.sources.baseSfx = sfx(0xA0, (byte) 0xF2);
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x91, AudioCommand.MusicRoute.DONOR_SMPS, true, "s3k"));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
        AudioVoiceRegistry registry = fixture.registry();

        drain(fixture.queue).forEach(registry::apply);

        SmpsCompositeVoice current =
                (SmpsCompositeVoice) registry.orderedVoiceAt(0);
        assertEquals(2, current.driver().captureSnapshot().sequencers().size());
    }

    @Test
    void overlappingNoMusicSfxUseOneStandaloneDriverAndArbitrate() {
        Fixture fixture = fixture();
        fixture.sources.baseSfx = sfx(0xA0, (byte) 0xF2);
        fixture.sources.namedSfx = sfx(0xA1, (byte) 0xF2);
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                -1, "JUMP", AudioCommand.SfxRoute.BASE_SMPS_NAME, 1.0f, null));
        AudioVoiceRegistry registry = fixture.registry();

        drain(fixture.queue).forEach(registry::apply);

        assertEquals(1, registry.orderedVoiceCount());
        assertEquals(1, ((SmpsCompositeVoice) registry.orderedVoiceAt(0))
                .driver().captureSnapshot().sequencers().size());
    }

    @Test
    void continuousRetriggerExtendsMusicAndStandaloneWithoutDuplicateSequencer() {
        for (boolean withMusic : List.of(false, true)) {
            Fixture fixture = fixture();
            fixture.sources.continuous = true;
            fixture.sources.baseSfx = sfx(0xBC, (byte) 0xFC, (byte) 0x40, (byte) 0,
                    (byte) 0xF2);
            if (withMusic) {
                fixture.sources.baseMusic = music(0x81);
                fixture.resolver.submit(new AudioCommand.PlayMusic(
                        0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));
            }
            fixture.resolver.submit(new AudioCommand.PlaySfx(
                    0xBC, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
            fixture.resolver.submit(new AudioCommand.PlaySfx(
                    0xBC, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
            AudioVoiceRegistry registry = fixture.registry();

            drain(fixture.queue).forEach(registry::apply);

            SmpsDriver driver =
                    ((SmpsCompositeVoice) registry.orderedVoiceAt(0)).driver();
            assertEquals(withMusic ? 2 : 1,
                    driver.captureSnapshot().sequencers().size());
            assertTrue(driver.isContinuousSfxFlagSet());
        }
    }

    @Test
    void musicMutationAndResetThenSfxObservesTheSamePresentationCounter() {
        Fixture fixture = fixtureWithS3kOwner();
        fixture.sources.baseConfig = s3kConfig(new ArbitraryHandler());
        fixture.sources.baseMusic = musicWithTrack(0x81,
                (byte) 0xE9, (byte) 0xE9, (byte) 0xF2);
        fixture.sources.baseSfx = sfx(0xA0, (byte) 0xE9, (byte) 0xF2);
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
        AudioVoiceRegistry registry = fixture.registry();
        drain(fixture.queue).forEach(registry::apply);

        mix(registry, 1024);

        assertTrue(fixture.handlers.state().spindashRevCounter() > 0);
    }

    @Test
    void sfxMutationThenMusicObservesTheSamePresentationCounter() {
        Fixture fixture = fixtureWithS3kOwner();
        fixture.sources.baseConfig = s3kConfig(new ArbitraryHandler());
        fixture.sources.baseSfx = sfx(0xA0,
                (byte) 0xE9, (byte) 0xE9, (byte) 0xF2);
        fixture.resolver.submit(new AudioCommand.PlaySfx(
                0xA0, null, AudioCommand.SfxRoute.BASE_SMPS_ID, 1.0f, null));
        AudioVoiceRegistry registry = fixture.registry();
        drain(fixture.queue).forEach(registry::apply);
        mix(registry, 1024);
        int afterSfx = fixture.handlers.state().spindashRevCounter();
        fixture.sources.baseMusic = musicWithTrack(0x81,
                (byte) 0xE9, (byte) 0xF2);
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));
        drain(fixture.queue).forEach(registry::apply);

        mix(registry, 1024);

        assertTrue(fixture.handlers.state().spindashRevCounter() >= afterSfx);
    }

    @Test
    void overrideAndSnapshotRecreationUseTheSamePresentationHandlerOwner() {
        Fixture fixture = fixtureWithS3kOwner();
        fixture.sources.baseConfig = s3kConfig(new ArbitraryHandler());
        fixture.sources.baseMusic = music(0x81);
        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, true, null));
        MusicVoiceEntry music =
                ((PushMusicOverride) drain(fixture.queue).get(0)).music();
        SmpsCompositeVoice original = fixture.factory.recreateSmps(
                (AudioPresentationCommand.SmpsVoiceDescriptor)
                        music.voiceDescriptor());
        PresentationVoiceSnapshot.Smps snapshot =
                (PresentationVoiceSnapshot.Smps) original.snapshot();

        SmpsCompositeVoice restored = fixture.factory.recreateSmps(
                snapshot, SmpsDriverSnapshot.liveReferences());

        assertSame(fixture.handlers.handlerFor("s3k"),
                restored.driver().captureSnapshot().sequencers().get(0)
                        .config().getCoordFlagHandler());
    }

    @Test
    void arbitraryHandlerEmbeddedInProfileConfigIsNeverUsedForPresentation() {
        Fixture fixture = fixtureWithS3kOwner();
        ArbitraryHandler arbitrary = new ArbitraryHandler();
        fixture.sources.baseConfig = s3kConfig(arbitrary);
        fixture.sources.baseMusic = music(0x81);

        fixture.resolver.submit(new AudioCommand.PlayMusic(
                0x81, AudioCommand.MusicRoute.BASE_SMPS, false, null));
        ReplaceMusic command =
                (ReplaceMusic) drain(fixture.queue).get(0);
        SmpsCompositeVoice voice = fixture.factory.recreateSmps(
                (AudioPresentationCommand.SmpsVoiceDescriptor)
                        command.music().voiceDescriptor());

        CoordFlagHandler actual = voice.driver().captureSnapshot()
                .sequencers().get(0).config().getCoordFlagHandler();
        assertNotSame(arbitrary, actual);
        assertSame(fixture.handlers.handlerFor("s3k"), actual);
    }

    private static Fixture fixture() {
        return fixture(new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState()), "base");
    }

    private static Fixture fixtureWithS3kOwner() {
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        handlers.register("s3k", Sonic3kCoordFlagHandler::new);
        return fixture(handlers, "s3k");
    }

    private static Fixture fixture(
            SmpsCoordFlagHandlerOwner handlers, String baseGameId) {
        AtomicBoolean owner = new AtomicBoolean(true);
        FakeAssets assets = new FakeAssets();
        DecodedPcmCache pcm = new DecodedPcmCache();
        AudioPresentationSourceFactory.Settings settings =
                new AudioPresentationSourceFactory.Settings(
                        48_000, SmpsSequencer.Region.NTSC,
                        false, false, false, false, 1,
                        AudioManager.getInstance(), pcm, assets);
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(owner::get, handlers,
                        settings);
        FakeSources sources = new FakeSources(baseGameId);
        AudioPresentationCommandQueue queue =
                new AudioPresentationCommandQueue();
        List<String> warnings = new ArrayList<>();
        List<AudioPresentationCommand> synchronouslyApplied =
                new ArrayList<>();
        AudioPresentationCommandResolver resolver =
                new AudioPresentationCommandResolver(
                        queue, factory, sources, warnings::add,
                        owner::get, synchronouslyApplied::add);
        return new Fixture(queue, factory, resolver, sources, assets, handlers,
                warnings, owner, synchronouslyApplied);
    }

    private record Fixture(
            AudioPresentationCommandQueue queue,
            AudioPresentationSourceFactory factory,
            AudioPresentationCommandResolver resolver,
            FakeSources sources,
            FakeAssets assets,
            SmpsCoordFlagHandlerOwner handlers,
            List<String> warnings,
            AtomicBoolean ownerThreadBoundary,
            List<AudioPresentationCommand> synchronouslyApplied) {
        AudioVoiceRegistry registry() {
            return new AudioVoiceRegistry(
                    factory, factory, handlers, warnings::add);
        }
    }

    private static final class FakeSources
            implements AudioPresentationCommandResolver.Sources {
        final AtomicInteger calls = new AtomicInteger();
        final String baseGameId;
        AbstractSmpsData baseMusic;
        AbstractSmpsData donorMusic;
        AbstractSmpsData baseSfx;
        AbstractSmpsData namedSfx;
        AbstractSmpsData donorSfx;
        DacData baseDac = EMPTY_DAC;
        SmpsSequencerConfig baseConfig =
                new SmpsSequencerConfig.Builder().build();
        boolean continuous;

        FakeSources(String baseGameId) {
            this.baseGameId = baseGameId;
        }

        @Override
        public String baseGameId() {
            return baseGameId;
        }

        @Override
        public AbstractSmpsData loadBaseMusic(int musicId) {
            calls.incrementAndGet();
            return baseMusic;
        }

        @Override
        public AbstractSmpsData loadDonorMusic(
                String donorGameId, int musicId) {
            calls.incrementAndGet();
            return donorMusic;
        }

        @Override
        public AbstractSmpsData loadBaseSfx(int sfxId) {
            calls.incrementAndGet();
            return baseSfx;
        }

        @Override
        public AbstractSmpsData loadBaseSfx(String name) {
            calls.incrementAndGet();
            return namedSfx;
        }

        @Override
        public AbstractSmpsData loadDonorSfx(
                String donorGameId, int sfxId) {
            calls.incrementAndGet();
            return donorSfx;
        }

        @Override
        public DacData dacFor(String gameId) {
            return baseDac;
        }

        @Override
        public SmpsSequencerConfig configFor(String gameId) {
            return baseConfig;
        }

        @Override
        public int sfxPriority(String gameId, int sfxId) {
            return 0x70;
        }

        @Override
        public boolean specialSfx(String gameId, int sfxId) {
            return false;
        }

        @Override
        public boolean continuousSfx(String gameId, int sfxId) {
            return continuous;
        }

        @Override
        public int maxStereoFrames() {
            return 2_048;
        }
    }

    private static final class FakeAssets
            implements AudioPresentationSourceFactory.WavAssets {
        final AtomicInteger opens = new AtomicInteger();
        final List<String> malformed = new ArrayList<>();

        @Override
        public InputStream open(String assetId) {
            opens.incrementAndGet();
            if (malformed.contains(assetId)) {
                return new ByteArrayInputStream(new byte[] {1, 2, 3});
            }
            return new ByteArrayInputStream(wav(
                    new short[] {100, 200, 300, 400}, 1, 48_000));
        }
    }

    private static final class ArbitraryHandler implements CoordFlagHandler {
        @Override
        public boolean handleFlag(
                CoordFlagContext ctx, SmpsSequencer.Track track, int command) {
            return false;
        }

        @Override
        public int flagParamLength(int command) {
            return -1;
        }
    }

    private static MutableMusicData music(int id) {
        return musicWithTrack(id, (byte) 0xF2);
    }

    private static MutableMusicData musicWithTrack(int id, byte... track) {
        byte[] data = new byte[0x100];
        data[2] = 1;
        data[4] = 1;
        data[5] = (byte) 0x80;
        data[6] = 0x40;
        System.arraycopy(track, 0, data, 0x40, track.length);
        MutableMusicData result = new MutableMusicData(data);
        result.setId(id);
        return result;
    }

    private static MutableSfxData sfx(int id, byte... track) {
        byte[] data = new byte[0x100];
        data[2] = 1;
        data[3] = 1;
        data[4] = (byte) 0x80;
        data[5] = 2;
        data[6] = 0x40;
        System.arraycopy(track, 0, data, 0x40, track.length);
        MutableSfxData result = new MutableSfxData(data);
        result.setId(id);
        return result;
    }

    private static SmpsSequencerConfig s3kConfig(CoordFlagHandler handler) {
        return new SmpsSequencerConfig.Builder()
                .tempoMode(SmpsSequencerConfig.TempoMode.OVERFLOW)
                .volMode(SmpsSequencerConfig.VolMode.BIT7)
                .psgEnvCmd80(SmpsSequencerConfig.PsgEnvCmd80.RESET)
                .noteOnPrevent(SmpsSequencerConfig.NoteOnPrevent.HOLD)
                .delayFreq(SmpsSequencerConfig.DelayFreq.KEEP)
                .coordFlagHandler(handler)
                .modAlgo(SmpsSequencerConfig.ModAlgo.MOD_Z80)
                .build();
    }

    private static void mix(AudioVoiceRegistry registry, int frames) {
        new AudioPresentationMixer(frames).mix(registry, frames);
    }

    private static List<AudioPresentationCommand> drain(
            AudioPresentationCommandQueue queue) {
        List<AudioPresentationCommand> commands = new ArrayList<>();
        queue.applyPending(commands::add);
        return commands;
    }

    private static byte[] wav(
            short[] samples, int channels, int sampleRate) {
        int dataBytes = samples.length * Short.BYTES;
        ByteBuffer out = ByteBuffer.allocate(44 + dataBytes)
                .order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.putInt(36 + dataBytes);
        out.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        out.putInt(16);
        out.putShort((short) 1);
        out.putShort((short) channels);
        out.putInt(sampleRate);
        out.putInt(sampleRate * channels * Short.BYTES);
        out.putShort((short) (channels * Short.BYTES));
        out.putShort((short) 16);
        out.put("data".getBytes(StandardCharsets.US_ASCII));
        out.putInt(dataBytes);
        for (short sample : samples) {
            out.putShort(sample);
        }
        return out.array();
    }

    private static class MutableMusicData extends AbstractSmpsData {
        MutableMusicData(byte[] data) {
            super(data, 0);
        }

        @Override
        protected void parseHeader() {
            voicePtr = 0;
            channels = data[2] & 0xFF;
            psgChannels = data[3] & 0xFF;
            dividingTiming = Math.max(1, data[4] & 0xFF);
            tempo = data[5] & 0xFF;
            fmPointers = channels == 0
                    ? new int[0] : new int[] {read16(6)};
            fmKeyOffsets = new int[channels];
            fmVolumeOffsets = new int[channels];
            psgPointers = new int[0];
            psgKeyOffsets = new int[0];
            psgVolumeOffsets = new int[0];
            psgModEnvs = new int[0];
            psgInstruments = new int[0];
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return new byte[25];
        }

        @Override
        public byte[] getPsgEnvelope(int id) {
            return new byte[] {(byte) 0x81};
        }

        @Override
        public int read16(int offset) {
            return (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8);
        }

        @Override
        public int getBaseNoteOffset() {
            return 0;
        }
    }

    private static final class MutableSfxData extends MutableMusicData
            implements SmpsSfxData {
        private final List<SmpsSfxTrack> tracks;

        MutableSfxData(byte[] data) {
            super(data);
            tracks = List.of(new FixtureTrack(
                    data[5] & 0xFF, read16(6), data[8], data[9]));
            channels = 1;
            psgChannels = 0;
        }

        @Override
        public int getTickMultiplier() {
            return 1;
        }

        @Override
        public List<? extends SmpsSfxTrack> getTrackEntries() {
            return tracks;
        }
    }

    private record FixtureTrack(
            int channelMask, int pointer, int transpose, int volume)
            implements SmpsSfxData.SmpsSfxTrack {
    }
}
