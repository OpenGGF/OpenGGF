package com.openggf.audio.smps;

import com.openggf.audio.AudioManager;
import com.openggf.audio.presentation.AudioPresentationCommand;
import com.openggf.audio.presentation.AudioPresentationSourceFactory;
import com.openggf.audio.presentation.DecodedPcmCache;
import com.openggf.audio.presentation.SmpsCompositeVoice;
import com.openggf.audio.rewind.AudioSourceDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestFrozenSmpsDataImmutability {
    private static final DacData EMPTY_DAC =
            new DacData(Map.of(), Map.of(), 288);

    @AfterEach
    void tearDown() {
        AudioManager.getInstance().resetState();
    }

    @Test
    void frozenProgramOwnsLoaderArraysAndDefendsEveryPublicArrayAccessor() {
        MutableSmpsData source = MutableSmpsData.complete();
        AudioPresentationSourceFactory factory = factory();
        AudioPresentationCommand.MusicVoiceEntry entry = factory.musicSmps(
                "base", 0x91, 1, source, EMPTY_DAC,
                new SmpsSequencerConfig.Builder().build(),
                AudioSourceDescriptor.baseMusic(0x91), 32);

        source.mutateOwnedInputs();
        AbstractSmpsData frozen = program(factory, entry);

        mutatePublicCopies(frozen);
        assertOriginalValues(frozen);
        assertThrows(UnsupportedOperationException.class,
                () -> frozen.setId(0x44));
        assertThrows(UnsupportedOperationException.class,
                () -> frozen.setPalSpeedupDisabled(false));
        assertEquals(0x91, frozen.getId());
        assertTrue(frozen.isPalSpeedupDisabled());
    }

    @Test
    void coordFlagContextExposesOnlyTheScalarIndexedProgramView()
            throws Exception {
        Class<?> view = assertDoesNotThrow(() -> Class.forName(
                "com.openggf.audio.smps.SmpsProgramView"));
        Set<String> expectedMethods = Set.of(
                "dataLength", "dataByteAt",
                "fmPointerCount", "fmPointerAt", "fmKeyOffsetAt",
                "fmVolumeOffsetAt", "psgPointerCount", "psgPointerAt",
                "psgKeyOffsetAt", "psgVolumeOffsetAt",
                "psgModEnvelopeAt", "psgInstrumentAt",
                "voiceLength", "voiceByteAt",
                "psgEnvelopeLength", "psgEnvelopeByteAt",
                "modEnvelopeLength", "modEnvelopeByteAt");
        Set<String> actualMethods = Set.of(view.getDeclaredMethods()).stream()
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertEquals(expectedMethods, actualMethods);
        for (Method method : view.getDeclaredMethods()) {
            assertFalse(method.getReturnType().isArray(), method::toString);
            assertFalse(Collection.class.isAssignableFrom(
                    method.getReturnType()), method::toString);
        }
        assertEquals(view,
                CoordFlagContext.class.getMethod("programView")
                        .getReturnType());
        for (Method method : CoordFlagContext.class.getMethods()) {
            assertFalse(method.getReturnType().isArray(), method::toString);
        }
    }

    private static AudioPresentationSourceFactory factory() {
        return new AudioPresentationSourceFactory(
                () -> true,
                new SmpsCoordFlagHandlerOwner(
                        new SmpsCoordFlagRuntimeState()),
                new AudioPresentationSourceFactory.Settings(
                        48_000, SmpsSequencer.Region.NTSC,
                        false, false, false, false, 1,
                        AudioManager.getInstance(),
                        new DecodedPcmCache(), ignored -> null));
    }

    private static AbstractSmpsData program(
            AudioPresentationSourceFactory factory,
            AudioPresentationCommand.MusicVoiceEntry entry) {
        SmpsCompositeVoice voice = factory.recreateSmps(
                (AudioPresentationCommand.SmpsVoiceDescriptor)
                        entry.voiceDescriptor());
        return voice.driver().firstMusicSequencer().getSmpsData();
    }

    private static void mutatePublicCopies(AbstractSmpsData frozen) {
        frozen.getData()[0] = 0x55;
        frozen.getFmPointers()[0] = 0x55;
        frozen.getFmKeyOffsets()[0] = 0x55;
        frozen.getFmVolumeOffsets()[0] = 0x55;
        frozen.getPsgPointers()[0] = 0x55;
        frozen.getPsgKeyOffsets()[0] = 0x55;
        frozen.getPsgVolumeOffsets()[0] = 0x55;
        frozen.getPsgModEnvs()[0] = 0x55;
        frozen.getPsgInstruments()[0] = 0x55;
        frozen.getVoice(7)[0] = 0x55;
        frozen.getPsgEnvelope(8)[0] = 0x55;
        frozen.getModEnvelope(9)[0] = 0x55;
    }

    private static void assertOriginalValues(AbstractSmpsData frozen) {
        assertArrayEquals(new byte[] {1, 2, 3}, frozen.getData());
        assertArrayEquals(new int[] {0}, frozen.getFmPointers());
        assertArrayEquals(new int[] {11}, frozen.getFmKeyOffsets());
        assertArrayEquals(new int[] {12}, frozen.getFmVolumeOffsets());
        assertArrayEquals(new int[] {0}, frozen.getPsgPointers());
        assertArrayEquals(new int[] {13}, frozen.getPsgKeyOffsets());
        assertArrayEquals(new int[] {14}, frozen.getPsgVolumeOffsets());
        assertArrayEquals(new int[] {15}, frozen.getPsgModEnvs());
        assertArrayEquals(new int[] {16}, frozen.getPsgInstruments());
        assertArrayEquals(new byte[] {17, 18}, frozen.getVoice(7));
        assertArrayEquals(new byte[] {19, 20}, frozen.getPsgEnvelope(8));
        assertArrayEquals(new byte[] {21, 22}, frozen.getModEnvelope(9));
    }

    private static final class MutableSmpsData extends AbstractSmpsData {
        private final byte[] voice;
        private final byte[] psgEnvelope;
        private final byte[] modEnvelope;

        private MutableSmpsData(
                byte[] data,
                int[] fmPointers,
                int[] fmKeyOffsets,
                int[] fmVolumeOffsets,
                int[] psgPointers,
                int[] psgKeyOffsets,
                int[] psgVolumeOffsets,
                int[] psgModEnvs,
                int[] psgInstruments,
                byte[] voice,
                byte[] psgEnvelope,
                byte[] modEnvelope) {
            super(data, 0);
            this.fmPointers = fmPointers;
            this.fmKeyOffsets = fmKeyOffsets;
            this.fmVolumeOffsets = fmVolumeOffsets;
            this.psgPointers = psgPointers;
            this.psgKeyOffsets = psgKeyOffsets;
            this.psgVolumeOffsets = psgVolumeOffsets;
            this.psgModEnvs = psgModEnvs;
            this.psgInstruments = psgInstruments;
            this.voice = voice;
            this.psgEnvelope = psgEnvelope;
            this.modEnvelope = modEnvelope;
            setId(0x91);
            setPalSpeedupDisabled(true);
        }

        static MutableSmpsData complete() {
            return new MutableSmpsData(
                    new byte[] {1, 2, 3},
                    new int[] {0}, new int[] {11}, new int[] {12},
                    new int[] {0}, new int[] {13}, new int[] {14},
                    new int[] {15}, new int[] {16},
                    new byte[] {17, 18}, new byte[] {19, 20},
                    new byte[] {21, 22});
        }

        void mutateOwnedInputs() {
            data[0] = 0x66;
            fmPointers[0] = 0x66;
            fmKeyOffsets[0] = 0x66;
            fmVolumeOffsets[0] = 0x66;
            psgPointers[0] = 0x66;
            psgKeyOffsets[0] = 0x66;
            psgVolumeOffsets[0] = 0x66;
            psgModEnvs[0] = 0x66;
            psgInstruments[0] = 0x66;
            voice[0] = 0x66;
            psgEnvelope[0] = 0x66;
            modEnvelope[0] = 0x66;
            setId(0x66);
            setPalSpeedupDisabled(false);
        }

        @Override
        protected void parseHeader() {
        }

        @Override
        public byte[] getVoice(int voiceId) {
            return voiceId == 7 ? voice : null;
        }

        @Override
        public byte[] getPsgEnvelope(int id) {
            return id == 8 ? psgEnvelope : null;
        }

        @Override
        public byte[] getModEnvelope(int id) {
            return id == 9 ? modEnvelope : null;
        }

        @Override
        public int read16(int offset) {
            return ((data[offset] & 0xFF) << 8)
                    | (data[offset + 1] & 0xFF);
        }

        @Override
        public int getBaseNoteOffset() {
            return 0;
        }
    }
}
