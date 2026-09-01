package com.openggf.audio.session;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.driver.SmpsDriverServiceObserver;
import com.openggf.audio.output.NoDeviceAudioSink;
import com.openggf.audio.presentation.AudioPresentationCommandQueue;
import com.openggf.audio.presentation.AudioPresentationMixer;
import com.openggf.audio.presentation.AudioPresentationProducer;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.AudioVoiceRegistry;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.SmpsCoordFlagHandlerOwner;
import com.openggf.audio.smps.SmpsCoordFlagRuntimeState;
import com.openggf.audio.smps.SmpsLoader;
import com.openggf.audio.smps.SmpsSequencerConfig;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.data.Rom;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSmpsDriverSession {
    @Test
    void composingCapturingAndClosingInertSessionEmitNoWrites() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);

        assertTrue(observer.events().isEmpty());
        assertFalse(session.installed());
        assertFalse(session.captureSnapshot().initialized());
        assertTrue(observer.events().isEmpty());

        session.close();
        assertTrue(observer.events().isEmpty());
        assertThrows(IllegalStateException.class, session::captureSnapshot);
    }

    @Test
    void legacyCompatibilityPolicyMatchesCurrentDefaultBehaviorAndIdentity() {
        SmpsPhysicalPolicy first =
                LegacyCompatibilitySmpsPhysicalPolicy.INSTANCE;
        SmpsPhysicalPolicy second =
                new LegacyCompatibilitySmpsPhysicalPolicy();
        GameAudioProfile profile = new MinimalProfile();
        SmpsSessionTestFixtures.RecordingObserver legacyObserver =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsSessionTestFixtures.RecordingObserver policyObserver =
                new SmpsSessionTestFixtures.RecordingObserver();

        new VirtualSynthesizer(44_100, legacyObserver);
        SmpsPhysicalDevice device = new SmpsPhysicalDevice(
                SmpsSessionTestFixtures.settings(), policyObserver);
        device.apply(first.boot());

        assertSame(first, profile.smpsPhysicalPolicy());
        assertEquals(first.identity(), second.identity());
        assertInstanceOf(SmpsPhysicalPolicy.Identity.class,
                first.identity());
        assertEquals(legacyObserver.events(), policyObserver.events());
        assertEquals(first.boot(), first.stopAll());
        assertEquals(List.of(new SmpsChipWrite.Ym2612(
                        0, 0x2B, 0x80)),
                first.activateMusic(new SmpsMusicActivation(
                        SmpsSessionTestFixtures.source(6), 1)).writes());
    }

    @Test
    void sessionLiveMutationTokensAreSingleUseAndSessionBound() {
        SmpsDriverSession session = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        SmpsDriverSession other = SmpsSessionTestFixtures.session(
                new SmpsSessionTestFixtures.RecordingObserver());
        var before = SmpsSessionTestFixtures.json(session.captureSnapshot());
        SmpsDriverSession.LiveMutationToken token =
                session.captureLiveMutation();

        session.applyChannelMasks(0x15, 0x05);
        session.rollbackLiveMutation(token);

        assertEquals(before, SmpsSessionTestFixtures.json(
                session.captureSnapshot()));
        assertThrows(IllegalStateException.class,
                () -> session.rollbackLiveMutation(token));

        SmpsDriverSession.LiveMutationToken crossSession =
                session.captureLiveMutation();
        assertThrows(IllegalArgumentException.class,
                () -> other.commitLiveMutation(crossSession));
        session.commitLiveMutation(crossSession);
        assertThrows(IllegalStateException.class,
                () -> session.commitLiveMutation(crossSession));
    }

    @Test
    void presentationCompositionSharesOneInertSessionAndClosesItWriteFree() {
        SmpsSessionTestFixtures.RecordingObserver observer =
                new SmpsSessionTestFixtures.RecordingObserver();
        SmpsDriverSession session = SmpsSessionTestFixtures.session(observer);
        SmpsCoordFlagHandlerOwner handlers = new SmpsCoordFlagHandlerOwner(
                new SmpsCoordFlagRuntimeState());
        AudioPresentationSourceFactory.Settings settings =
                new AudioPresentationSourceFactory.Settings(
                        44_100, com.openggf.audio.smps.SmpsSequencer.Region.NTSC,
                        false, false, false, false, 1,
                        () -> { }, new DecodedPcmCache(), ignored -> null);
        AudioPresentationSourceFactory factory =
                new AudioPresentationSourceFactory(
                        () -> true, handlers, settings, session);
        AudioVoiceRegistry registry = new AudioVoiceRegistry(
                factory, factory, handlers, ignored -> { }, session);
        AudioPresentationProducer producer = new AudioPresentationProducer(
                44_100, 60, 44_100, 32, registry,
                new AudioPresentationCommandQueue(),
                new AudioPresentationMixer(735),
                new NoDeviceAudioSink(44_100), session);

        assertTrue(observer.events().isEmpty());
        assertFalse(session.installed());

        producer.close();

        assertTrue(observer.events().isEmpty());
        assertThrows(IllegalStateException.class, session::installed);
    }

    private static final class MinimalProfile implements GameAudioProfile {
        @Override
        public SmpsLoader createSmpsLoader(Rom rom) {
            return null;
        }

        @Override
        public SmpsSequencerConfig getSequencerConfig() {
            return null;
        }

        @Override
        public int getSpeedShoesOnCommandId() {
            return 0;
        }

        @Override
        public int getSpeedShoesOffCommandId() {
            return 0;
        }

        @Override
        public int getInvincibilityMusicId() {
            return 0;
        }

        @Override
        public int getExtraLifeMusicId() {
            return 0;
        }

        @Override
        public int getDrowningMusicId() {
            return 0;
        }

        @Override
        public Map<com.openggf.audio.GameSound, Integer> getSoundMap() {
            return Map.of();
        }
    }
}
