package com.openggf.game.sonic3k.audio.smps;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.smps.DacData;
import com.openggf.audio.smps.SmpsNoteListener;
import com.openggf.audio.smps.SmpsSequencer;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Earlier-header bank calls must execute without relocating the header or voice blob. */
class TestSonic3kBankTrackAddressSpace {
    @Test
    void bankTrackCallsEarlierBytesAndReturnsToTheOriginalTrack() {
        byte[] raw = song(); byte[] bank = new byte[0x200];
        System.arraycopy(raw, 0, bank, 0x100, raw.length);
        bank[0x80] = (byte) 0xEF; bank[0x81] = 0; // real instrument selection
        bank[0x82] = (byte) 0xA0; bank[0x83] = 2; bank[0x84] = (byte) 0xF9;
        var data = new Sonic3kSmpsData(raw, 0x8100);
        data.setBankData(bank, 0x8000);
        var sequencer = new SmpsSequencer(data, new DacData(Map.of(), Map.of(), 297),
                new SmpsDriver(), () -> { }, Sonic3kSmpsSequencerConfig.CONFIG);
        var notes = new ArrayList<Integer>();
        sequencer.setNoteListener(new SmpsNoteListener() {
            public void release(SmpsSequencer.Track track) { }
            public void attack(SmpsSequencer.Track track) { notes.add(track.note); }
        });
        for (int service = 0; service < 6; service++) sequencer.serviceOuterFrame();
        assertEquals(java.util.List.of(0xA0, 0xA1), notes,
                "the earlier bank phrase and local continuation both sound");
        var fm = sequencer.getTracks().stream().filter(t -> t.type == SmpsSequencer.TrackType.FM).findFirst().orElseThrow();
        assertFalse(fm.active); assertEquals(0, fm.returnSp); assertEquals(0x147, fm.pos);
        assertArrayEquals(Arrays.copyOfRange(raw, 0x70, 0x89), fm.voiceData);
    }

    @Test
    void programCoordinatesUseTheBankButHeaderAndLocalVoicesKeepTheRawContract() {
        byte[] raw = song(); byte[] bank = new byte[0x200];
        Arrays.fill(bank, (byte) 0x55);
        System.arraycopy(raw, 0, bank, 0x100, raw.length);
        var data = new Sonic3kSmpsData(raw, 0x8100);
        assertEquals(0x8100, data.getZ80StartAddress()); assertEquals(raw.length, data.dataLength());
        data.setBankData(bank, 0x8000);
        assertSame(raw, data.getData(), "legacy raw bytes remain the parsed header blob");
        assertSame(bank, data.getBankData(), "program reads select the loaded bank without copying it");
        assertEquals(0x8000, data.getZ80StartAddress()); assertEquals(bank.length, data.dataLength());
        assertEquals(0x8080, data.read16(0x142)); assertEquals((byte) 0x55, data.dataByteAt(0x80));
        assertEquals(0, data.read16(bank.length - 1));
        assertEquals(0x8170, data.getVoicePtr()); assertEquals(0x8141, data.getFmPointers()[1]);
        assertEquals(1, data.getDividingTiming()); assertEquals(0, data.getTempo());
        assertArrayEquals(Arrays.copyOfRange(raw, 0x70, 0x89), data.getVoice(0),
                "local voices still resolve against the original header address");
    }

    @Test
    void earlierSharedBankVoicesAndUnbankedWordReadsKeepTheirNativeLookup() {
        byte[] raw = song(); word(raw, 0, 0x8030);
        byte[] bank = new byte[0x200]; Arrays.fill(bank, 0x30, 0x49, (byte) 0x6B);
        var data = new Sonic3kSmpsData(raw, 0x8100);
        assertEquals(0x8080, data.read16(0x42));
        data.setBankData(bank, 0x8000);
        byte[] expected = new byte[25]; Arrays.fill(expected, (byte) 0x6B);
        assertArrayEquals(expected, data.getVoice(0));
        data.setBankData(null, 0);
        assertEquals(0x8100, data.getZ80StartAddress()); assertEquals(raw.length, data.dataLength());
        assertEquals(0x8080, data.read16(0x42));
    }

    private static byte[] song() {
        byte[] raw = new byte[0xA0];
        word(raw, 0, 0x8170); raw[2] = 2; raw[4] = 1;
        word(raw, 6, 0x8140); word(raw, 10, 0x8141);
        raw[0x40] = (byte) 0xF2; raw[0x41] = (byte) 0xF8; word(raw, 0x42, 0x8080);
        raw[0x44] = (byte) 0xA1; raw[0x45] = 3; raw[0x46] = (byte) 0xF2;
        Arrays.fill(raw, 0x70, 0x89, (byte) 0x3A);
        return raw;
    }
    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value; bytes[offset + 1] = (byte) (value >>> 8);
    }
}
