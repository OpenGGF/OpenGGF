package com.openggf.audio.presentation;

import com.openggf.audio.driver.SmpsDriver;
import com.openggf.audio.rewind.SmpsSourceDescriptor;
import com.openggf.audio.smps.*;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsSequencerConfig;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsData;
import com.openggf.game.sonic3k.audio.smps.Sonic3kSmpsLoader;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.SitarHeroS3kSongProbe;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Production freezing must preserve the native bank, not substitute the raw header blob. */
class TestFrozenSmpsBankProgram {
    private static final DacData EMPTY_DAC = new DacData(Map.of(), Map.of(), 297);

    @Test
    void frozenEarlierHeaderCallReturnsAndKeepsHeaderRelativeVoice() {
        var source = syntheticSong();
        var frozen = SmpsAssetCatalog.freezeStandalone(source);
        assertArrayEquals(source.getData(), frozen.getData());
        assertEquals(source.dataLength(), frozen.dataLength());
        var sequence = new SmpsSequencer(frozen, EMPTY_DAC, new SmpsDriver(),
                () -> { }, Sonic3kSmpsSequencerConfig.CONFIG);
        var notes = new ArrayList<Integer>();
        sequence.setNoteListener(new SmpsNoteListener() {
            public void release(SmpsSequencer.Track track) { }
            public void attack(SmpsSequencer.Track track) { notes.add(track.note); }
        });
        for (int frame = 0; frame < 6; frame++) sequence.serviceOuterFrame();
        assertEquals(List.of(0xA0, 0xA1), notes);
        var fm = sequence.getTracks().stream()
                .filter(t -> t.type == SmpsSequencer.TrackType.FM).findFirst().orElseThrow();
        assertFalse(fm.active);
        assertEquals(0, fm.returnSp);
        assertEquals(0x147, fm.pos);
        assertArrayEquals(Arrays.copyOfRange(source.getData(), 0x70, 0x89), fm.voiceData);
    }

    @Test
    void frozenIndexedBytesWordsHeaderAndVoicesOwnTheirInputs() {
        var source = syntheticSong();
        byte[] raw = source.getData().clone();
        byte[] bank = source.getBankData().clone();
        byte[] voice = source.getVoice(0).clone();
        var frozen = SmpsAssetCatalog.freezeStandalone(source);
        var descriptor = SmpsSourceDescriptor.from(frozen);
        Arrays.fill(source.getData(), (byte) 0x66);
        Arrays.fill(source.getBankData(), (byte) 0x77);
        source.getFmPointers()[1] = 0;
        frozen.getData()[0] = 0;
        frozen.getVoice(0)[0] = 0;
        frozen.getFmPointers()[1] = 0;
        assertArrayEquals(raw, frozen.getData());
        assertArrayEquals(voice, frozen.getVoice(0));
        assertEquals(0x8141, frozen.getFmPointers()[1]);
        assertEquals(bank.length, frozen.dataLength());
        for (int index = 0; index < bank.length; index++) {
            assertEquals(bank[index], frozen.dataByteAt(index), "bank byte " + index);
            if (index + 1 < bank.length)
                assertEquals((bank[index] & 255) | ((bank[index + 1] & 255) << 8),
                        frozen.read16(index), "native little-endian word " + index);
        }
        assertEquals(0, frozen.read16(bank.length - 1), "source's incomplete final word");
        assertEquals(descriptor, SmpsSourceDescriptor.from(frozen));
        assertSame(frozen, SmpsAssetCatalog.freezeStandalone(frozen));
    }

    @Test
    void indexedWordsKeepBigEndianSourceOrderingAndIncompleteWordRejection() {
        byte[] bank = {0x12, 0x34, 0x56, 0x78};
        var source = new AbstractSmpsData(new byte[] {(byte) 0xF2}, 0x4000) {
            protected void parseHeader() { }
            public byte[] getVoice(int id) { return null; }
            public byte[] getPsgEnvelope(int id) { return null; }
            public int getBaseNoteOffset() { return 0; }
            public int dataLength() { return bank.length; }
            public byte dataByteAt(int index) { return bank[index]; }
            public int read16(int offset) { return ((bank[offset] & 255) << 8) | (bank[offset + 1] & 255); }
        };
        var frozen = SmpsAssetCatalog.freezeStandalone(source);
        Arrays.fill(bank, (byte) 0);
        assertArrayEquals(new byte[] {(byte) 0xF2}, frozen.getData());
        assertEquals(0x1234, frozen.read16(0));
        assertEquals(0x3456, frozen.read16(1));
        assertEquals(0x5678, frozen.read16(2));
        assertThrows(IndexOutOfBoundsException.class, () -> frozen.read16(3));
    }

    @Test
    void identicalRawHeadersCannotHideDifferentIndexedPrograms() {
        var catalog = catalog();
        var source = syntheticSong();
        var config = SmpsAssetCatalog.copyConfigWithoutHandler(Sonic3kSmpsSequencerConfig.CONFIG);
        var first = catalog.register(key(), source, EMPTY_DAC, config, false);
        var equal = syntheticSong();
        assertSame(first, catalog.register(key(), equal, EMPTY_DAC, config, false));
        var changed = syntheticSong();
        changed.getBankData()[0x82] = (byte) 0xA2; // earlier phrase, outside raw header blob
        assertArrayEquals(source.getData(), changed.getData());
        assertThrows(SmpsAssetCatalog.ProgramIdentityConflict.class,
                () -> catalog.register(key(), changed, EMPTY_DAC, config, false));
        assertSame(first, catalog.find(key()));
    }

    @Test
    void identicalBytesCannotHideDifferentWordOrderOrIncompleteWordSemantics() {
        var config = new SmpsSequencerConfig.Builder().build();
        var little = catalog();
        var first = little.register(key(), new WordProgram(false, true, 0), EMPTY_DAC, config, false);
        assertSame(first, little.register(key(), new WordProgram(false, true, 0), EMPTY_DAC, config, false));
        var rejectedTail = catalog();
        var rejected = rejectedTail.register(key(), new WordProgram(false, false, 0), EMPTY_DAC, config, false);
        assertSame(rejected, rejectedTail.register(key(), new WordProgram(false, false, 0), EMPTY_DAC, config, false));
        assertAll(
                () -> assertThrows(SmpsAssetCatalog.ProgramIdentityConflict.class,
                        () -> little.register(key(), new WordProgram(true, true, 0), EMPTY_DAC, config, false),
                        "equal bytes and metadata with different read16 byte order cannot share a frozen program"),
                () -> assertThrows(SmpsAssetCatalog.ProgramIdentityConflict.class,
                        () -> little.register(key(), new WordProgram(false, false, 0), EMPTY_DAC, config, false),
                        "captured incomplete-word value differs from rejection"),
                () -> assertThrows(SmpsAssetCatalog.ProgramIdentityConflict.class,
                        () -> little.register(key(), new WordProgram(false, true, 7), EMPTY_DAC, config, false),
                        "different captured incomplete-word values cannot share a frozen program"),
                () -> assertThrows(SmpsAssetCatalog.ProgramIdentityConflict.class,
                        () -> rejectedTail.register(key(), new WordProgram(false, true, 0), EMPTY_DAC, config, false),
                        "rejected incomplete word differs from a captured value in either registration order"));
        assertEquals(SmpsSourceDescriptor.from(new WordProgram(false, true, 0)),
                SmpsSourceDescriptor.from(new WordProgram(true, false, 0)),
                "descriptor fingerprint remains byte-oriented; registry validates semantic identity");
    }

    @Test
    void sharedPhysicalBankDoesNotEraseRawHeaderOrTrackMetadataIdentity() {
        var catalog = catalog();
        var source = syntheticSong();
        var config = SmpsAssetCatalog.copyConfigWithoutHandler(Sonic3kSmpsSequencerConfig.CONFIG);
        catalog.register(key(), source, EMPTY_DAC, config, false);
        var differentHeader = syntheticSong();
        differentHeader.getData()[0x20] = 1; // raw blob difference independent of executed bank
        differentHeader.setBankData(source.getBankData(), 0x8000);
        assertSame(source.getBankData(), differentHeader.getBankData());
        assertThrows(SmpsAssetCatalog.ProgramIdentityConflict.class,
                () -> catalog.register(key(), differentHeader, EMPTY_DAC, config, false));
        var differentTrack = syntheticSong();
        differentTrack.setBankData(source.getBankData(), 0x8000);
        differentTrack.getFmVolumeOffsets()[1] = 3;
        assertArrayEquals(source.getData(), differentTrack.getData());
        assertThrows(SmpsAssetCatalog.ProgramIdentityConflict.class,
                () -> catalog.register(key(), differentTrack, EMPTY_DAC, config, false));
    }

    @Test
    void liveFrozenAndRegisteredDescriptorsHashTheSameCompleteIndexedProgram() {
        var source = syntheticSong();
        var live = SmpsSourceDescriptor.from(source);
        assertEquals(source.dataLength(), live.dataLength());
        assertEquals(Arrays.hashCode(source.getBankData()), live.dataHash());
        var frozen = SmpsAssetCatalog.freezeStandalone(source);
        assertEquals(live, SmpsSourceDescriptor.from(frozen));
        var entry = catalog().register(key(), source, EMPTY_DAC,
                SmpsAssetCatalog.copyConfigWithoutHandler(Sonic3kSmpsSequencerConfig.CONFIG), false);
        assertEquals(live.dataLength(), entry.sourceDescriptor().dataLength());
        assertEquals(live.dataHash(), entry.sourceDescriptor().dataHash());
        assertTrue(entry.sourceDescriptor().matchesData(frozen));
        var changed = syntheticSong();
        changed.getBankData()[0x82] = (byte) 0xA2;
        assertNotEquals(live.dataHash(), SmpsSourceDescriptor.from(changed).dataHash());
    }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void all101NativeEntriesKeepCompleteDirectFrozenStreamsIncludingS3Ending() throws Exception {
        var loader = new Sonic3kSmpsLoader(GameServices.rom().getRom());
        var dac = loader.loadDacData();
        // Test-only access reuses the durable observer without adding a public tool/API method.
        Method inspect = SitarHeroS3kSongProbe.class.getDeclaredMethod("inspectData",
                Sonic3kSmpsLoader.class, DacData.class, int.class, int.class, AbstractSmpsData.class);
        inspect.setAccessible(true);
        var ids = new ArrayList<Integer>();
        for (int id = 1; id <= 0x33; id++) ids.add(id);
        for (int id = 1; id <= 0x32; id++) ids.add(0x100 | id);
        assertEquals(101, ids.size());
        for (int id : ids) {
            var source = loader.loadMusic(id);
            assertNotNull(source, "native table entry " + Integer.toHexString(id));
            var frozen = SmpsAssetCatalog.freezeStandalone(source);
            assertArrayEquals(source.getData(), frozen.getData());
            assertEquals(source.getZ80StartAddress(), frozen.getZ80StartAddress());
            assertEquals(source.dataLength(), frozen.dataLength());
            for (int offset = 0; offset < source.dataLength(); offset++) {
                assertEquals(source.dataByteAt(offset), frozen.dataByteAt(offset));
                assertEquals(source.read16(offset), frozen.read16(offset));
            }
            var direct = SitarHeroS3kSongProbe.inspect(loader, dac, id, 36_001);
            var snapshot = (SitarHeroS3kSongProbe.Result) inspect.invoke(null,
                    loader, dac, id, 36_001, frozen);
            assertEquals(direct, snapshot, "all sources, attacks, note/voice signatures, tempo/jumps/stops: "
                    + Integer.toHexString(id));
            if (id == 0x132) {
                assertEquals(609, snapshot.naturalEndFrame(), "complete ending, formerly truncated at 514");
                assertEquals(48, snapshot.parts().stream().filter(p -> p.kind().equals("FM") && p.channel() == 2)
                        .findFirst().orElseThrow().attacks());
                assertEquals(72, snapshot.parts().stream().filter(p -> p.kind().equals("FM") && p.channel() == 3)
                        .findFirst().orElseThrow().attacks());
            }
        }
    }

    private static SmpsAssetCatalog catalog() {
        return new SmpsAssetCatalog(new SmpsCoordFlagHandlerOwner(new SmpsCoordFlagRuntimeState()));
    }
    private static final class WordProgram extends AbstractSmpsData {
        private final byte[] indexed = {0x12, 0x34, 0x56, 0x78};
        private final boolean bigEndian;
        private final boolean acceptsTail;
        private final int tail;

        private WordProgram(boolean bigEndian, boolean acceptsTail, int tail) {
            super(new byte[] {(byte) 0xF2}, 0x4000);
            this.bigEndian = bigEndian; this.acceptsTail = acceptsTail; this.tail = tail;
            setId(1);
        }
        protected void parseHeader() { }
        public byte[] getVoice(int id) { return null; }
        public byte[] getPsgEnvelope(int id) { return null; }
        public int getBaseNoteOffset() { return 0; }
        public int dataLength() { return indexed.length; }
        public byte dataByteAt(int index) { return indexed[index]; }
        public int read16(int offset) {
            if (offset == indexed.length - 1 && acceptsTail) return tail;
            int left = indexed[offset] & 255, right = indexed[offset + 1] & 255;
            return bigEndian ? (left << 8) | right : left | (right << 8);
        }
    }
    private static SmpsAssetCatalog.ProgramKey key() {
        return new SmpsAssetCatalog.ProgramKey(
                new SmpsAssetKey("test", SmpsAssetKey.Route.BASE_MUSIC, 1, null), 7);
    }
    private static Sonic3kSmpsData syntheticSong() {
        byte[] raw = new byte[0xA0];
        word(raw, 0, 0x8170); raw[2] = 2; raw[4] = 1;
        word(raw, 6, 0x8140); word(raw, 10, 0x8141);
        raw[0x40] = (byte) 0xF2; raw[0x41] = (byte) 0xF8; word(raw, 0x42, 0x8080);
        raw[0x44] = (byte) 0xA1; raw[0x45] = 3; raw[0x46] = (byte) 0xF2;
        Arrays.fill(raw, 0x70, 0x89, (byte) 0x3A);
        byte[] bank = new byte[0x200];
        System.arraycopy(raw, 0, bank, 0x100, raw.length);
        bank[0x80] = (byte) 0xEF; bank[0x81] = 0;
        bank[0x82] = (byte) 0xA0; bank[0x83] = 2; bank[0x84] = (byte) 0xF9;
        var source = new Sonic3kSmpsData(raw, 0x8100);
        source.setBankData(bank, 0x8000);
        source.setId(1);
        return source;
    }
    private static void word(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value; bytes[offset + 1] = (byte) (value >>> 8);
    }
}
