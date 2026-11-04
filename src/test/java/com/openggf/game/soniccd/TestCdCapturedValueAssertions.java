package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.resources.CdPlayerArtRuntime;
import com.openggf.game.soniccd.resources.CdR11bCameraArtResidency;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.openggf.game.soniccd.CdCapturedValueAssertions.assertCapturedValueEquals;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed ROM-free value assertions; no production adoption authority. */
class TestCdCapturedValueAssertions {
    private record ArtCapture(Object owner,CdPlayerArtRuntime.State art) { }
    private record NumberCapture(int value) { }
    private record OtherNumberCapture(int value) { }

    @Test void distinctRealArtRecordsWithEqualBytesCompareAsCapturedValues() {
        var first=art(0,0xFF);var second=art(0,0xFF);var owner=new Object();
        assertNotSame(first,second);assertNotEquals(first,second,"Record equality exposes the original CdAssetBytes identity gap");
        assertNotSame(first.cpuBuffer(),second.cpuBuffer());assertCapturedValueEquals(new ArtCapture(owner,first),new ArtCapture(owner,second));
        assertSame(owner,new ArtCapture(owner,second).owner(),"Ownership remains a separate identity assertion");
    }

    @Test void firstByteAndRecordFieldDifferencesIncludeTheirExactPaths() {
        var byteFailure=assertThrows(AssertionError.class,()->assertCapturedValueEquals(art(0,12),art(1,12),"capture.art"));
        assertTrue(byteFailure.getMessage().contains("capture.art.cpuBuffer[7]"));
        var fieldFailure=assertThrows(AssertionError.class,()->assertCapturedValueEquals(art(0,12),art(0,13),"capture.art"));
        assertTrue(fieldFailure.getMessage().contains("capture.art.lastFrame"));
    }

    @Test void nestedMapsListsOptionalsAndPrimitiveOrObjectArraysCompareRecursively() {
        var expected=Map.of("rows",Optional.of(List.of(new int[]{1,2},new Object[]{bytes(3,4),new long[]{5,6}})));
        Map<String,Object> actual=new LinkedHashMap<>();actual.put("rows",Optional.of(new ArrayList<>(List.of(new int[]{1,2},new Object[]{bytes(3,4),new long[]{5,6}}))));
        assertCapturedValueEquals(expected,actual,"capture");
        var changed=Map.of("rows",Optional.of(List.of(new int[]{1,9},new Object[]{bytes(3,4),new long[]{5,6}})));
        var failure=assertThrows(AssertionError.class,()->assertCapturedValueEquals(expected,changed,"capture"));
        assertTrue(failure.getMessage().contains("capture[rows].value[0][1]"));
    }

    @Test void opaqueOwnerIdentityIsNeverReplacedByReflectingItsFields() {
        var owner=new Object();assertCapturedValueEquals(new ArtCapture(owner,art(0,1)),new ArtCapture(owner,art(0,1)),"capture");
        var failure=assertThrows(AssertionError.class,()->assertCapturedValueEquals(new ArtCapture(owner,art(0,1)),new ArtCapture(new Object(),art(0,1)),"capture"));
        assertTrue(failure.getMessage().contains("capture.owner"));
    }

    @Test void opaqueScopedEqualityRetainsUnknownVersusKnownZeroKnowledge() {
        var unknown=CdR11bCameraArtResidency.unknown();var known=CdR11bCameraArtResidency.clear(unknown);
        assertTrue(unknown.region(CdR11bCameraArtResidency.Region.AT_0000).knownByte(0).isEmpty());
        assertEquals(0,known.region(CdR11bCameraArtResidency.Region.AT_0000).knownByte(0).orElseThrow());
        assertCapturedValueEquals(known,CdR11bCameraArtResidency.clear(CdR11bCameraArtResidency.unknown()),"current");
        var failure=assertThrows(AssertionError.class,()->assertCapturedValueEquals(unknown,known,"current"));
        assertTrue(failure.getMessage().contains("current"));
    }

    @Test void cpuSatEqualityIncludesBytesActiveCountAndPhysicalProvenance() {
        var first=cpu(32,128);var equal=cpu(32,128);assertNotSame(first,equal);assertCapturedValueEquals(first,equal,"sat");
        var bytesFailure=assertThrows(AssertionError.class,()->assertCapturedValueEquals(first,cpu(32,144),"sat"));assertTrue(bytesFailure.getMessage().contains("sat.bytes["));
        var countFailure=assertThrows(AssertionError.class,()->assertCapturedValueEquals(first,emptyCount(first.bytes()),"sat"));assertTrue(countFailure.getMessage().contains("sat.spriteCount"));
        var provenanceFailure=assertThrows(AssertionError.class,()->assertCapturedValueEquals(first,cpu(33,128),"sat"));assertTrue(provenanceFailure.getMessage().contains("sat.provenance[0].slotRef.wholeIndex"));
    }

    @Test void optionalPrimitiveKnowledgeUsesItsRealEquality() {
        assertCapturedValueEquals(OptionalInt.empty(),OptionalInt.empty());assertCapturedValueEquals(OptionalInt.of(255),OptionalInt.of(255));
        assertCapturedValueEquals(OptionalLong.of(0x1_0000_0000L),OptionalLong.of(0x1_0000_0000L));
        assertThrows(AssertionError.class,()->assertCapturedValueEquals(OptionalInt.empty(),OptionalInt.of(0),"flags"));
        assertThrows(AssertionError.class,()->assertCapturedValueEquals(OptionalLong.of(1),OptionalLong.of(2),"clock"));
    }

    @Test void nullAndTypeMismatchesFailAtTheRequestedPath() {
        assertCapturedValueEquals(null,null);
        assertTrue(assertThrows(AssertionError.class,()->assertCapturedValueEquals(null,"present","capture")).getMessage().contains("capture"));
        assertTrue(assertThrows(AssertionError.class,()->assertCapturedValueEquals(new int[]{1},new long[]{1},"capture")).getMessage().contains("capture.type"));
        assertTrue(assertThrows(AssertionError.class,()->assertCapturedValueEquals(new NumberCapture(1),new OtherNumberCapture(1),"capture")).getMessage().contains("capture.type"));
        assertTrue(assertThrows(AssertionError.class,()->assertCapturedValueEquals(bytes(1),new byte[]{1},"capture")).getMessage().contains("capture.type"));
    }

    @Test void sharedAcyclicAliasesAreAcceptedButDistinctCyclesAndExcessiveDepthFailConservatively() {
        var reused=bytes(1,2);assertCapturedValueEquals(List.of(reused,reused),List.of(bytes(1,2),bytes(1,2)));
        var a=new ArrayList<Object>();a.add(a);var b=new ArrayList<Object>();b.add(b);
        var cycle=assertThrows(AssertionError.class,()->assertCapturedValueEquals(a,b,"capture"));assertTrue(cycle.getMessage().contains("capture[0]"));assertTrue(cycle.getMessage().contains("cycle"));
        Object first=1,second=1;for(int i=0;i<80;i++){first=List.of(first);second=List.of(second);}final Object left=first,right=second;
        assertTrue(assertThrows(AssertionError.class,()->assertCapturedValueEquals(left,right,"deep")).getMessage().contains("depth"));
    }

    @Test void comparisonLeavesCallerArraysContainersAndValueReferencesUntouched() {
        byte[] left={1,2,3},right={1,2,3};var owner=new Object();var a=art(0,1);var b=art(0,1);
        var first=new ArrayList<>(List.of(left,new ArtCapture(owner,a)));var second=new ArrayList<>(List.of(right,new ArtCapture(owner,b)));
        assertCapturedValueEquals(first,second);assertArrayEquals(new byte[]{1,2,3},left);assertArrayEquals(new byte[]{1,2,3},right);
        assertSame(left,first.getFirst());assertSame(right,second.getFirst());assertSame(a,((ArtCapture)first.get(1)).art());assertSame(b,((ArtCapture)second.get(1)).art());
    }

    private static CdAssetBytes bytes(int... values){byte[] raw=new byte[values.length];for(int i=0;i<raw.length;i++)raw[i]=(byte)values[i];return CdAssetBytes.copyOf(raw);}
    private static CdPlayerArtRuntime.State art(int byteSeven,int lastFrame){byte[] cpu=new byte[CdPlayerArtRuntime.CPU_BUFFER_BYTES];cpu[7]=(byte)byteSeven;return new CdPlayerArtRuntime.State(cpu,new byte[CdPlayerArtRuntime.RESIDENT_WINDOW_BYTES],lastFrame,true);}
    /** Existing bounded component produces real CpuSat values; no private constructor/World ingress. */
    private static CdObjectDrawQueue.CpuSat cpu(int whole,int x) {
        var slots=new CdObjectDrawQueue.CdDrawSlots(){public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef ref){return new CdObjectDrawQueue.LiveDrawSlot(1,0,x,0,128,0,0,0);}public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef ref,boolean value){}};
        try(var q=new CdObjectDrawQueue(slots,(address,staticPiece,frame)->new CdObjectDrawQueue.FrameMapping(1,List.of(new CdObjectDrawQueue.RawPiece(0,0,0x123,0))),new byte[CdObjectDrawQueue.SAT_BYTE_SIZE])) {
            q.beginPass();var camera=new CdObjectDrawQueue.CameraWords(0,0);q.drawObject(new CdObjectDrawQueue.DrawCallCut(new CdObjectDrawQueue.CdSlotRef(whole),0,x,0,0,0,0),camera);
            return q.finishAfterCamera(new CdObjectDrawQueue.DrawCameras(camera,camera,camera));
        }
    }
    private static CdObjectDrawQueue.CpuSat emptyCount(byte[] retained) {
        try(var q=new CdObjectDrawQueue(new CdObjectDrawQueue.CdDrawSlots(){public CdObjectDrawQueue.LiveDrawSlot read(CdObjectDrawQueue.CdSlotRef r){throw new AssertionError("No dispatch");}public void writeOnScreenBit(CdObjectDrawQueue.CdSlotRef r,boolean v){throw new AssertionError("No dispatch");}},(a,s,f)->{throw new AssertionError("No mappings");},retained)){return q.cpuSat();}
    }
}
