package com.openggf.game.sonic3k.audio.smps;

import com.openggf.audio.AudioManager;
import com.openggf.audio.AudioManagerTestDiagnostics;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.audio.synth.VirtualSynthesizer;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Syntax-only checks for the three shipped-unreachable FF meta formats.
 *
 * <p>These streams characterize defensive operand-width consumption; they do
 * not advertise custom-driver execution support.</p>
 */
@ExtendWith(SingletonResetExtension.class)
class TestSonic3kSmpsMetaCommandOperands {

    private static final DacData EMPTY_DAC = new DacData(new HashMap<>(), new HashMap<>(), 297);

    @Test
    void ff01IsSyntaxOnlyAndConsumesOneOperand() {
        assertMetaTrackEndsAfter(new byte[] {(byte) 0xFF, 0x01, 0x7A, (byte) 0xF2});
    }

    @Test
    void ff02IsSyntaxOnlyAndConsumesOneOperand() {
        assertMetaTrackEndsAfter(new byte[] {(byte) 0xFF, 0x02, 0x7A, (byte) 0xF2});
    }

    @Test
    void ff03IsSyntaxOnlyAndConsumesThreeOperands() {
        assertMetaTrackEndsAfter(new byte[] {(byte) 0xFF, 0x03, 0x12, 0x34, 0x56, (byte) 0xF2});
    }

    @Test
    void ff01DoesNotDispatchSoundCommand() {
        AudioManager audio = AudioManager.getInstance();
        int commandCountBefore = audio.commandTimeline().entryCount();
        int presentationVoicesBefore = AudioManagerTestDiagnostics
                .producerFingerprint(audio).voiceIdentities().size();
        SmpsSequencer sequencer = sequencerWithSingleFm(
                new byte[] {(byte) 0xFF, 0x01, (byte) 0xA4, (byte) 0xF2});

        sequencer.read(new short[0]);
        audio.presentFrame(PresentationMode.SILENT);

        SmpsSequencer.Track fm = fmTrack(sequencer, 0);
        assertFalse(fm.active);
        assertEquals(0x44, fm.pos);
        assertEquals(commandCountBefore, audio.commandTimeline().entryCount(),
                "syntax-only FF 01 must not submit an AudioManager command");
        assertEquals(presentationVoicesBefore, AudioManagerTestDiagnostics
                        .producerFingerprint(audio).voiceIdentities().size(),
                "syntax-only FF 01 must not admit a presentation voice");
    }

    @Test
    void ff02DoesNotHaltSiblingSongTrack() {
        byte[] data = new byte[0x100];
        data[0x02] = 3; // DAC + two FM tracks
        setLe16(data, 0x0A, 0x40);
        setLe16(data, 0x0E, 0x60);
        data[0x40] = (byte) 0xFF;
        data[0x41] = 0x02;
        data[0x42] = 0x01;
        data[0x43] = (byte) 0xF2;
        data[0x60] = (byte) 0x81;
        data[0x61] = 0x7F;
        SmpsSequencer sequencer = sequencer(data);

        sequencer.read(new short[0]);

        SmpsSequencer.Track commandTrack = fmTrack(sequencer, 0);
        assertFalse(commandTrack.active);
        assertEquals(0x44, commandTrack.pos);
        SmpsSequencer.Track sibling = fmTrack(sequencer, 1);
        assertTrue(sibling.active);
        assertEquals(0x62, sibling.pos);
        assertEquals(0x81, sibling.note);
        assertEquals(0x7F, sibling.duration);
    }

    @Test
    void ff03DoesNotMutateSequenceMemory() {
        byte[] data = new byte[0x100];
        data[0x02] = 2; // DAC + one FM track
        setLe16(data, 0x0A, 0x40);
        data[0x40] = (byte) 0xFF;
        data[0x41] = 0x03;
        data[0x42] = 0x70;
        data[0x43] = 0x00;
        data[0x44] = 0x01;
        data[0x45] = (byte) 0xF2;
        data[0x70] = (byte) 0x81;
        SmpsSequencer sequencer = sequencer(data);

        sequencer.read(new short[0]);

        assertEquals(0xF2, sequencer.getData()[0x45] & 0xFF,
                "syntax-only FF 03 must not copy into sequence memory");
        assertNotEquals(sequencer.getData()[0x70], sequencer.getData()[0x45],
                "the unchanged destination must remain distinct from the source byte");
        SmpsSequencer.Track fm = fmTrack(sequencer, 0);
        assertFalse(fm.active);
        assertEquals(0x46, fm.pos);
    }

    private static void assertMetaTrackEndsAfter(byte[] trackBytes) {
        byte[] data = new byte[0x100];
        data[0x02] = 2; // DAC + one FM track
        setLe16(data, 0x0A, 0x40); // second FM/DAC entry is the synthetic FM track
        System.arraycopy(trackBytes, 0, data, 0x40, trackBytes.length);
        SmpsSequencer sequencer = sequencer(data);

        sequencer.read(new short[20_000]);
        SmpsSequencer.Track fm = fmTrack(sequencer, 0);
        assertFalse(fm.active, "the aligned F2 terminator must stop the FM track");
        assertEquals(0x40 + trackBytes.length, fm.pos,
                "meta command must consume exactly its native operand width");
    }

    private static SmpsSequencer sequencerWithSingleFm(byte[] trackBytes) {
        byte[] data = new byte[0x100];
        data[0x02] = 2; // DAC + one FM track
        setLe16(data, 0x0A, 0x40);
        System.arraycopy(trackBytes, 0, data, 0x40, trackBytes.length);
        return sequencer(data);
    }

    private static SmpsSequencer sequencer(byte[] data) {
        Sonic3kSmpsData smps = new Sonic3kSmpsData(data, 0);
        return new SmpsSequencer(smps, EMPTY_DAC, new VirtualSynthesizer(),
                Sonic3kSmpsSequencerConfig.CONFIG);
    }

    private static SmpsSequencer.Track fmTrack(SmpsSequencer sequencer, int channelId) {
        return sequencer.getTracks().stream()
                .filter(track -> track.type == SmpsSequencer.TrackType.FM
                        && track.channelId == channelId)
                .findFirst().orElseThrow();
    }

    private static void setLe16(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }
}
