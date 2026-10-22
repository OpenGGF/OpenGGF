package com.openggf.game.sonic3k.competition;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kCompetitionRecords {
    // Literal native long/character/padding rows. DPZ precedes CGZ in saved RAM.
    private static final byte[] BLOCK = HexFormat.of().parseHex(
            "000102030002030400030405000102a1"
            + "000203040003040500040506020001b2"
            + "000304050004050600050607010200c3"
            + "000405060005060700060708000201d4"
            + "000506070006070800070809020100e5");

    @Test void decodeEncodePreservesEverySuppliedByteWithoutInventingDefaults() {
        byte[] source = BLOCK.clone();
        var records = new S3kCompetitionRecords(source);
        source[0] = 0x7F;
        assertArrayEquals(BLOCK, records.encode());
        byte[] encoded = records.encode(); encoded[1] = 0x7F;
        assertArrayEquals(BLOCK, records.encode());
        assertEquals(List.of(0xE,0xF,0x10,0x11,0x12), records.capture().arenas().stream()
                .map(arena -> arena.arena().zoneId()).toList());
        assertEquals(0x00030405L, records.entries(S3kCompetitionSession.Arena.DESERT_PALACE).getFirst().packedTime());
        assertEquals(0x00040506L, records.entries(S3kCompetitionSession.Arena.CHROME_GADGET).getFirst().packedTime());
        assertThrows(IllegalArgumentException.class, () -> new S3kCompetitionRecords(new byte[79]));
    }

    @Test void firstPlaceShiftsBothTimesAndCharacterBytesAndPreservesPadding() {
        var records = new S3kCompetitionRecords(BLOCK);
        assertEquals(0, records.insert(S3kCompetitionSession.Arena.AZURE_LAKE, 0x00010063L, 2));
        assertEquals(List.of(new S3kCompetitionRecords.Entry(0x00010063L,2),
                new S3kCompetitionRecords.Entry(0x00010203L,0),
                new S3kCompetitionRecords.Entry(0x00020304L,1)),
                records.entries(S3kCompetitionSession.Arena.AZURE_LAKE));
        assertEquals(0xA1,records.padding(S3kCompetitionSession.Arena.AZURE_LAKE));
        assertArrayEquals(Arrays.copyOfRange(BLOCK,16,80),Arrays.copyOfRange(records.encode(),16,80));
    }

    @Test void secondAndThirdPlaceUseTheNativeIndependentShiftPaths() {
        var second = new S3kCompetitionRecords(BLOCK);
        assertEquals(1,second.insert(S3kCompetitionSession.Arena.BALLOON_PARK,0x00030000L,1));
        assertEquals(List.of(new S3kCompetitionRecords.Entry(0x00020304L,2),
                new S3kCompetitionRecords.Entry(0x00030000L,1),
                new S3kCompetitionRecords.Entry(0x00030405L,0)),
                second.entries(S3kCompetitionSession.Arena.BALLOON_PARK));
        var third = new S3kCompetitionRecords(BLOCK);
        assertEquals(2,third.insert(S3kCompetitionSession.Arena.ENDLESS_MINE,0x00070000L,0));
        assertEquals(List.of(new S3kCompetitionRecords.Entry(0x00050607L,2),
                new S3kCompetitionRecords.Entry(0x00060708L,1),
                new S3kCompetitionRecords.Entry(0x00070000L,0)),
                third.entries(S3kCompetitionSession.Arena.ENDLESS_MINE));
    }

    @Test void equalFirstCanInsertSecondAndEqualSecondCanInsertThird() {
        var records = new S3kCompetitionRecords(BLOCK);
        assertEquals(1,records.insert(S3kCompetitionSession.Arena.AZURE_LAKE,0x00010203L,2));
        assertEquals(List.of(0,2,1), records.entries(S3kCompetitionSession.Arena.AZURE_LAKE).stream()
                .map(S3kCompetitionRecords.Entry::characterId).toList());
        assertEquals(2,records.insert(S3kCompetitionSession.Arena.AZURE_LAKE,0x00010203L,1));
        byte[] filled = records.encode();
        assertEquals(-1,records.insert(S3kCompetitionSession.Arena.AZURE_LAKE,0x00010203L,0));
        assertArrayEquals(filled,records.encode(),"equal third place is not a qualifying save");
    }

    @Test void nonqualifyingTimesChangeNoByteInAnyArenaAndProduceNoSaveRank() {
        var records = new S3kCompetitionRecords(BLOCK);
        for (int i=0;i<5;i++) {
            var arena=S3kCompetitionSession.Arena.forZone(0xE+i);
            long third=records.entries(arena).get(2).packedTime();
            assertEquals(-1,records.insert(arena,third,0));
            assertEquals(-1,records.insert(arena,0xFFFF_FFFFL,2));
        }
        assertArrayEquals(BLOCK,records.encode());
    }

    @Test void comparisonsAreUnsignedLongsAndRawUnknownCharacterBytesRoundTrip() {
        byte[] block=BLOCK.clone();
        byte[] row=HexFormat.of().parseHex("7fffffff80000000fffffffffe80ffa5");
        System.arraycopy(row,0,block,32,16);
        var records=new S3kCompetitionRecords(block);
        var arena=S3kCompetitionSession.Arena.DESERT_PALACE;
        assertEquals(1,records.insert(arena,0x7FFF_FFFFL,0x42));
        assertEquals(List.of(0x7FFF_FFFFL,0x7FFF_FFFFL,0x8000_0000L),records.entries(arena).stream()
                .map(S3kCompetitionRecords.Entry::packedTime).toList());
        assertEquals(List.of(0xFE,0x42,0x80),records.entries(arena).stream()
                .map(S3kCompetitionRecords.Entry::characterId).toList());
        assertEquals(0xA5,records.padding(arena));
        assertThrows(IllegalArgumentException.class,()->records.insert(arena,0x1_0000_0000L,0));
        assertThrows(IllegalArgumentException.class,()->records.insert(arena,-1,0));
    }

    @Test void everyArenaUsesItsPhysicalSixteenByteRow() {
        for (int i=0;i<5;i++) {
            var records=new S3kCompetitionRecords(BLOCK);
            var arena=S3kCompetitionSession.Arena.forZone(0xE+i);
            assertEquals(0,records.insert(arena,0,S3kCompetitionPlayer.Character.KNUCKLES));
            byte[] after=records.encode();
            assertArrayEquals(Arrays.copyOfRange(BLOCK,0,i*16),Arrays.copyOfRange(after,0,i*16));
            assertArrayEquals(Arrays.copyOfRange(BLOCK,(i+1)*16,80),Arrays.copyOfRange(after,(i+1)*16,80));
            assertEquals(BLOCK[i*16+15]&255,after[i*16+15]&255);
            assertEquals(2,records.entries(arena).getFirst().characterId());
        }
    }

    @Test void immutableSnapshotRestoresBothOrdersAndForwardInsertionOutcome() {
        var records=new S3kCompetitionRecords(BLOCK);
        var before=records.capture();
        assertThrows(UnsupportedOperationException.class,()->before.arenas().clear());
        assertThrows(UnsupportedOperationException.class,()->before.arenas().getFirst().entries().clear());
        insertForward(records);
        var expected=records.capture(); byte[] expectedBytes=records.encode();
        records.restore(before); assertArrayEquals(BLOCK,records.encode());
        insertForward(records);
        assertEquals(expected,records.capture()); assertArrayEquals(expectedBytes,records.encode());
    }

    @Test void packedTimeHelperKeepsNativeMinuteSecondAndCentisecondBytes() {
        assertEquals(0x00093B63L,S3kCompetitionRecords.packTime(9,59,99));
        assertEquals(0x00093B63L,S3kCompetitionRecords.packCentiseconds(59999));
        assertEquals(0x00010001L,S3kCompetitionRecords.packCentiseconds(6001));
        assertEquals(0x00FF3B63L,S3kCompetitionRecords.packTime(255,59,99));
        assertThrows(IllegalArgumentException.class,()->S3kCompetitionRecords.packTime(0,60,0));
        assertThrows(IllegalArgumentException.class,()->S3kCompetitionRecords.packTime(0,0,100));
    }

    private static void insertForward(S3kCompetitionRecords records) {
        records.insert(S3kCompetitionSession.Arena.DESERT_PALACE,0x00030000L,0);
        records.insert(S3kCompetitionSession.Arena.CHROME_GADGET,0x00050000L,2);
        records.insert(S3kCompetitionSession.Arena.DESERT_PALACE,0x00030000L,1);
    }
}
