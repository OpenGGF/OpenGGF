package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;

import static com.openggf.game.soniccd.objects.CdR4Boss.Table.BUBBLE_POP_DIRECTION;
import static org.junit.jupiter.api.Assertions.*;

/** Literal boss.asm loc_20CA72/loc_20CC32/loc_20CDA0 controls. Explicit component
 * ingress is constructed; the final test separately borrows actual USA C/D bytes.
 * No World, player, ordinary-route or pixel qualification is implied. */
class TestCdR4BubbleNativeContinuations {
    @Test void attachmentComparesOriginalSignedWordsAndKeepsWrappedRadius() {
        // X, parent X, signed-BGE angle, wrapped SUB.W radius, wrapped NEG.W radius.
        int[][] rows={{0x7FFF,0x8000,0x8000,0xFFFF,1},
                {0x8000,0x7FFF,0,1,0xFFFF}, {0x7FFF,0x7FFF,0x8000,0,0},
                {0xFFFE,0xFFFF,0,0xFFFF,1}};
        for (int[] row:rows) {
            var h=new TestCdR4BossComponents.H();
            h.put(70,0x4F,2);h.w(70,0x2E,h.raw(71));
            // Parent remains a dirty ID-zero slot: the native raw link has no ID gate.
            h.put(71,0,0xA4);h.w(71,8,row[1]);h.w(71,12,0x520);
            h.w(70,8,row[0]);h.w(70,10,0xBEEF);h.w(70,12,0x520);h.w(70,14,0x1234);
            h.w(70,0x3A,0xABCD);
            var before=h.placement.capture();var globals=Map.copyOf(h.values);
            byte[] expected=before.pool().slots().get(38).copyBytes();
            word(expected,8,row[1]);word(expected,12,0x520);word(expected,14,0x9234);
            word(expected,0x34,row[2]);word(expected,0x3C,row[3]);word(expected,0x10,row[3]);
            word(expected,0x38,row[4]);word(expected,0x3A,0);expected[0x24]=4;expected[0x1C]=2;
            h.body.run(70);
            assertPoolAndPlacement(h,before,expected,null);
            assertEquals(globals,h.values);assertEquals(java.util.List.of("draw"),h.events);
        }
    }

    @Test void poppedOrbitReadsTheFiniteOperandInsteadOfParentDirection() {
        for (int operand:new int[]{0,8}) {
            var h=orbitIngress();h.data.tables.put(BUBBLE_POP_DIRECTION,
                    CdAssetBytes.copyOf(new byte[]{(byte)operand}));
            var before=h.placement.capture();var globals=Map.copyOf(h.values);
            h.body.run(70);
            assertEquals(operand==0?0x10B4:0x13B4,h.w(70,0x34));
            assertEquals(12,h.b(70,0x24));assertEquals(4,h.b(71,0x2D));
            assertEquals(0x48,h.b(71,0x2C));assertEquals(4,h.b(70,0x1D));
            assertEquals(1,h.b(70,0x1B));assertEquals(7,h.b(70,0x1E));
            assertUnchangedOtherSlots(h,before,70,71);assertEquals(globals,h.values);
            assertEquals(java.util.List.of(operand==0?"sine:16":"sine:19","draw"),h.events);
        }
    }

    @Test void secondRedistributePopKeepsBothHelperCallsAndUsesTheAdjacentOperand() {
        var h=orbitIngress();h.b(70,0x24,16);h.b(71,0x24,20);
        h.l(70,0x3C,0x300000);h.l(70,0x10,0x300000);
        h.data.tables.put(BUBBLE_POP_DIRECTION,CdAssetBytes.copyOf(new byte[]{0}));
        var before=h.placement.capture();var globals=Map.copyOf(h.values);
        h.body.run(70);
        assertEquals(3,h.b(71,0x2D),"both original helper calls pop against parent routine$14");
        assertEquals(12,h.b(70,0x24));assertEquals(0x0DB4,h.w(70,0x34));
        assertEquals(0x2F,h.w(70,0x3C));assertEquals(0x2F,h.w(70,0x10));
        assertUnchangedOtherSlots(h,before,70,71);assertEquals(globals,h.values);
        assertEquals(java.util.List.of("sine:13","draw"),h.events);
    }

    @Test void missingPopOperandStopsAfterAnimationAndBeforeOrbitOrDraw() {
        var h=orbitIngress();h.data.tables.put(BUBBLE_POP_DIRECTION,CdAssetBytes.copyOf(new byte[0]));
        var before=h.placement.capture();var globals=Map.copyOf(h.values);
        assertThrows(IndexOutOfBoundsException.class,()->h.body.run(70));
        assertEquals(12,h.b(70,0x24));assertEquals(4,h.b(70,0x1C));assertEquals(4,h.b(70,0x1D));
        assertEquals(1,h.b(70,0x1B));assertEquals(7,h.b(70,0x1E));assertEquals(0,h.b(70,0x1A));
        assertEquals(0x1234,h.w(70,0x34));assertEquals(4,h.b(71,0x2D));
        assertEquals(0x48,h.b(71,0x2C));assertTrue(h.events.isEmpty());
        assertUnchangedOtherSlots(h,before,70,71);assertEquals(globals,h.values);
    }

    @Test void nonPoppingOrbitDoesNotReadAnUnavailableAdjacentOperand() {
        var h=orbitIngress();h.b(70,0x20,255);h.data.tables.remove(BUBBLE_POP_DIRECTION);
        h.body.run(70);
        assertEquals(8,h.b(70,0x24));assertEquals(0x13B4,h.w(70,0x34));
        assertEquals(5,h.b(71,0x2D));assertEquals(8,h.b(71,0x2C));
        assertEquals(java.util.List.of("sine:19","draw"),h.events);
    }

    @Test void actualUsaBossProgrammesSupplyTheReachedSeparateOneByteView() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);
        try (var media=CdProgramAssets.open(Path.of(root,"USA","Sonic CD (US).cue"))) {
            CdNativeFile[] files={CdNativeFile.R43C,CdNativeFile.R43D};
            int[] roots={0xD126,0xD13A},sites={0xCE2A,0xCE3E};
            String[] hashes={"c27a455859c4820182c8119d0de3618dde55dd47d34d32c396963e677ac1994c",
                    "a11d1d547841697d87d2a1b5fd5c3fe3ebab9aeaf5e8f1d3964f165a521b4789"};
            for (int n=0;n<files.length;n++) {
                var image=media.mmd(files[n]);byte[] bytes=image.readFile(0,image.fileBytes());
                assertEquals(hashes[n],HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
                var animation=CdAssetBytes.copyOf(image.readFile(roots[n],32));
                assertEquals(0x1C,animation.unsignedWord(8));
                assertArrayEquals(HexFormat.of().parseHex(n==0?"43f90020d1264ef9":"43f90020d13a4ef9"),image.readFile(sites[n],8));
                var operand=CdAssetBytes.copyOf(image.readFile(roots[n]+0x48,1));
                assertEquals(0xB1,operand.unsignedByte(0));
                var h=orbitIngress();h.data.bubbleAnimation=new CdObjectAnimation.AnimationTable(animation,0,5);
                h.data.tables.put(BUBBLE_POP_DIRECTION,operand);h.body.run(70);
                assertEquals(0x10B4,h.w(70,0x34));assertEquals(7,h.b(70,0x1A));
                assertEquals(19,h.b(70,0x1E));assertEquals(12,h.b(70,0x24));
            }
        }
    }

    private static TestCdR4BossComponents.H orbitIngress() {
        var h=new TestCdR4BossComponents.H();h.put(70,0x4F,8);h.put(71,0x4C,24);
        h.w(70,0x2E,h.raw(71));h.w(71,8,0xA00);h.w(71,12,0x520);
        h.b(71,0x2C,8);h.b(71,0x2D,5);h.w(70,0x34,0x1234);
        h.w(70,0x3C,0x20);h.w(70,0x10,0x20);return h;
    }
    private static void word(byte[] bytes,int at,int value) {bytes[at]=(byte)(value>>>8);bytes[at+1]=(byte)value;}
    private static void assertPoolAndPlacement(TestCdR4BossComponents.H h,
            CdObjectPlacementRuntime.Snapshot before,byte[] bubble,byte[] parent) {
        for (int n=32;n<128;n++) assertArrayEquals(n==70?bubble:n==71&&parent!=null?parent:
                before.pool().slots().get(n-32).copyBytes(),h.pool.state(n).copyBytes(),"whole"+n);
        assertEquals(before.status(),h.placement.status());
        assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }
    private static void assertUnchangedOtherSlots(TestCdR4BossComponents.H h,
            CdObjectPlacementRuntime.Snapshot before,int first,int second) {
        for (int n=32;n<128;n++) if(n!=first&&n!=second)
            assertArrayEquals(before.pool().slots().get(n-32).copyBytes(),h.pool.state(n).copyBytes(),"whole"+n);
        assertEquals(before.status(),h.placement.status());
        assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());
    }
}
