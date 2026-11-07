package com.openggf.game.soniccd.objects.r4;

import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdObjectState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts.Kind;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed complete component cuts, not live TT producer or native execution evidence.
 * Native TagaTaga Routine6:225–237; Yago loc_20E036:166–174 / loc_20E2A6:396–404. */
class TestCdR4EcologySpeedPrefixes {
    @Test void tagaRetainsEachLiteralSpeedStoreBeforeSubtypeAndFacingContinuation() {
        // subtype, facing bit, the complete native LONG publication sequence at var_2c.
        int[][] rows={{0,1,0x40000},{1,1,0x40000,0x18000},
                {0,0,0x40000,-0x40000},{1,0,0x40000,0x18000,-0x18000}};
        for (int[] row:rows) for (int cut=0;cut<row.length-2;cut++) {
            var f=fixture(0x2D,6,row[0],row[1]);f.longAt(64,0x2C,0x11223344);
            var before=f.placement.capture();var globals=Map.copyOf(f.globals);
            RuntimeException fault=new RuntimeException("original Taga Guard-after "+cut);
            int failIndex=cut;int[] speedStores={0};
            f.after=op->{if(changes(op,0x2C,4)&&speedStores[0]++==failIndex)throw fault;};
            assertSame(fault,assertThrows(RuntimeException.class,()->CdR4TagaTaga.run(f.c,64)));
            byte[] expected=before.pool().slots().get(32).copyBytes();
            word(expected,0x2A,0xFFFF);expected[0x24]=8;longWord(expected,0x2C,row[cut+2]);
            assertCompletePrefix(f,before,expected,globals);
            assertEquals(cut+1,speedStores[0]);assertEquals(0,fault.getSuppressed().length);
        }
    }

    @Test void yagoKeepsNegativeStoreThenLiveNegationBeforeSeparateVerticalSpeed() {
        for (int subtype=0;subtype<2;subtype++) for (int facing=0;facing<2;facing++) {
            // 1=after initial negative store;2=after NEG;3=before vertical store;4=after it.
            for (int cut:new int[]{1,2,3,4}) {
                if(cut==2&&facing==0)continue;
                var f=fixture(0x2E,12,subtype,facing);f.longAt(64,12,0x12345678);
                f.word(64,wordField(0x32),1);f.longAt(64,0x38,0x11223344);f.longAt(64,0x34,0x55667788);
                var before=f.placement.capture();var globals=Map.copyOf(f.globals);
                RuntimeException fault=new RuntimeException("original Yago Guard cut "+cut);
                int[] speedStores={0};
                f.before=op->{if(cut==3&&changes(op,0x34,4))throw fault;};
                f.after=op->{
                    if(changes(op,0x38,4)&&++speedStores[0]==cut)throw fault;
                    if(cut==4&&changes(op,0x34,4))throw fault;
                };
                assertSame(fault,assertThrows(RuntimeException.class,()->CdR4Yago.run(f.c,64)));
                byte[] expected=before.pool().slots().get(32).copyBytes();
                longWord(expected,12,subtype==0?0x12347678:0x12347E78);
                word(expected,0x32,0);expected[0x24]=14;
                int negative=subtype==0?-0x48000:-0x10000;
                longWord(expected,0x38,cut==1||facing==0?negative:-negative);
                if(cut==4)longWord(expected,0x34,-0x18000);
                assertCompletePrefix(f,before,expected,globals);
                assertEquals(cut==1?1:facing==0?1:2,speedStores[0]);
                assertEquals(0,fault.getSuppressed().length);
            }
        }
    }

    private static TestCdR4EcologyComponents.Fixture fixture(int id,int routine,int subtype,int facing) {
        var f=new TestCdR4EcologyComponents.Fixture();f.fullExcept();f.seed(64,id,routine,subtype);
        f.bytes(64,CdObjectState.ByteField.SPRITE_FLAGS,0xA4|facing);
        f.word(64,CdObjectState.WordField.X,0x4567);f.word(64,CdObjectState.WordField.X_FRACTION,0xABCD);
        f.word(64,CdObjectState.WordField.Y,0x1234);f.word(64,CdObjectState.WordField.Y_FRACTION,0x5678);
        return f;
    }
    private static boolean changes(CdR4ObjectPorts.Operation op,int offset,int bytes) {
        if(op.kind()!=Kind.STORE||op.target()!=64||op.output()==null)return false;
        byte[] old=op.cut().pool().slots().get(32).copyBytes(),next=op.output().copyBytes();
        return !Arrays.equals(Arrays.copyOfRange(old,offset,offset+bytes),Arrays.copyOfRange(next,offset,offset+bytes));
    }
    private static CdObjectState.WordField wordField(int offset) {
        return Arrays.stream(CdObjectState.WordField.values()).filter(f->f.byteOffset()==offset).findFirst().orElseThrow();
    }
    private static void word(byte[] out,int offset,int value) {out[offset]=(byte)(value>>>8);out[offset+1]=(byte)value;}
    private static void longWord(byte[] out,int offset,int value) {word(out,offset,value>>>16);word(out,offset+2,value);}
    private static void assertCompletePrefix(TestCdR4EcologyComponents.Fixture f,
            CdObjectPlacementRuntime.Snapshot before,byte[] actor,Map<CdR4ObjectPorts.Read,Integer> globals) {
        for (int n=32;n<128;n++) assertArrayEquals(n==64?actor:before.pool().slots().get(n-32).copyBytes(),
                f.s(n).copyBytes(),"whole"+n);
        assertEquals(before.status(),f.placement.status());
        assertArrayEquals(before.savedFlags().copy(),f.placement.capture().savedFlags().copy());
        assertEquals(globals,f.globals);assertEquals(List.of(Kind.READ),f.calls);
        assertTrue(f.operations.stream().noneMatch(f.c::isCurrent));
    }
}
