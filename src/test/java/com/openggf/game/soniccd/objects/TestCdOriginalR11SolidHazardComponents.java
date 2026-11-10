package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;
/** Constructed raw source vectors, not original/media execution. Tests FIRST. ALL Java UNRUN. */
class TestCdOriginalR11SolidHazardComponents {
    static final int W=43,MAP=0xFB2A00;
    private static final byte[] RAMP_SCRIPTS = bytes(
            0x00, 0x06, 0x00, 0x12, 0x00, 0x18,
            0x00, 0x04, 0x04, 0x03, 0x03, 0x03, 0x05, 0x05, 0x02, 0x02, 0x02, 0xFF,
            0x02, 0x04, 0x07, 0x05, 0x06, 0xFF,
            0x02, 0x08, 0x00, 0x09, 0x00, 0xFF);
    private static final byte[] MAPPINGS = tenSinglePieceFrames();

    @Test void rampNativeStoreMustBeLiveBeforeDrawAndReturnedMetadataIsInert(){
        var h=new H();h.run(0x8000);assertEquals(List.of("primary:F400","draw"),h.order);
        assertEquals(-0xC00,h.velocity);assertEquals(60,h.slot().unsignedByte(RAMP_TIMER));
        assertFalse(h.ramp.isCurrentPrimaryCall(h.call));assertEquals(0x1234,h.slot().word(X_FRACTION));
        assertEquals(0xFEDC,h.slot().word(Y_FRACTION));
    }
    @Test void rampCopiedCallAndReentryNeverGrantInvocationAuthority(){
        var h=new H();h.before=()->{var c=h.call;assertTrue(h.ramp.isCurrentPrimaryCall(c));
            assertFalse(h.ramp.isCurrentPrimaryCall(new Cd3dRamp.PrimaryCall(c.cut(),c.source(),c.expectedPlayer(),c.signedWord())));
            assertThrows(IllegalStateException.class,()->h.run(0));};h.run(0);
        assertEquals(List.of("primary:0C00","draw"),h.order);
    }
    @Test void rampDrawErrorKeepsAlreadyPublishedVelocityAndObjectPrefix(){
        var h=new H();var error=new AssertionError("draw");h.drawError=error;
        assertSame(error,assertThrows(AssertionError.class,()->h.run(0)));assertEquals(0xC00,h.velocity);
        assertEquals(60,h.slot().unsignedByte(RAMP_TIMER));assertEquals(2,h.slot().routine());
    }
    @Test void rampPublicationErrorKeepsNativePrefixAndDoesNotDraw(){
        var h=new H();var error=new IllegalStateException("primary");h.before=()->{throw error;};
        assertSame(error,assertThrows(IllegalStateException.class,()->h.run(0)));assertEquals(List.of(),h.order);
        assertEquals(60,h.slot().unsignedByte(RAMP_TIMER));assertEquals(2,h.slot().routine());
    }
    @Test void rampBoredAndAirborneConditionalReadsDoNotInventPrimaryStore(){
        var h=new H();h.player=new Cd3dRamp.Player(0x100,0x80,0,0,0x2B,0);h.run(0);
        assertEquals(List.of("draw"),h.order);assertNull(h.call);
        h=new H();h.player=new Cd3dRamp.Player(0x100,0x80,0,2,0,0);h.run(0);assertNull(h.call);
    }
    @Test void rampRawTimerOneWrapAndNonzeroSubtypeKeepFractionsAndNonwrites(){
        var h=new H();h.edit(s->s.moveByte(ROUTINE,2).moveByte(WIDTH,32).moveByte(Y_RADIUS,32)
                .moveWord(RAMP_ANCHOR_X,0x100).moveByte(RAMP_TIMER,1).moveByte(SUBTYPE,0x80));
        h.player=new Cd3dRamp.Player(0x100,0x80,0,2,0x2B,2);h.run(0);
        assertEquals(0,h.slot().unsignedByte(RAMP_TIMER));assertEquals(0x100,h.slot().centreX());
        assertEquals(0x1234,h.slot().word(X_FRACTION));assertEquals(0xFEDC,h.slot().word(Y_FRACTION));assertNull(h.call);
    }
    @Test void rampAlternateOwnerAndWrongIdStopBeforeEveryStore(){
        var h=new H();h.edit(s->s.moveByte(SUBTYPE2,255));var before=h.runtime.capture();
        assertEquals(Cd3dRamp.UnsupportedBoundary.ALTERNATE_OWNER_3D_FALL,assertThrows(Cd3dRamp.Unsupported3dRamp.class,()->h.run(0)).boundary());
        assertEquals(before.pool().slots(),h.runtime.capture().pool().slots());assertTrue(h.order.isEmpty());
        h.edit(s->s.moveByte(ID,0x1B));assertThrows(IllegalArgumentException.class,()->h.run(0));
    }
    @Test void increasingPhysicalWalkerReloadsFreshIdAfterEarlierNativeWrite(){
        var h=new H();var seen=new ArrayList<Integer>();CdPhysicalObjectWalk.run(32,128,w->{int id=h.runtime.pool().state(w).id();
            if(w==32)h.runtime.pool().write(h.runtime.pool().state(44).moveByte(ID,0x1B));if(id!=0)seen.add(id);});
        assertEquals(List.of(0x2A,0x1B),seen);
    }
    @Test void boulderPrefixDynamicCounterpartChangesOnlyGenuineWrittenSlot(){
        var h=new B();h.player=new P().player(8,44);var old=h.pool().state(44).moveByte(FLAGS,0xAB).moveWord(X_FRACTION,0xBEEF);h.pool().write(old);
        h.run();assertEquals(old.moveByte(FLAGS,0xA3),h.pool().state(44));assertEquals(W,h.player.standObject());
        assertEquals(List.of(),h.fm);assertEquals(1,h.calls);
    }
    @Test void boulderReservedReleasePublishesStoppedPrefixAndIdenticalThrowable(){
        var h=new B();h.player=new P().player(8,1);
        var thrown=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::run);
        var stop=assertInstanceOf(CdSolidObject.NativeStopped.class,h.outcome);assertSame(stop.stop().originalException(),thrown);
        assertEquals(stop.prefix().object(),h.pool().state(W));assertEquals(stop.prefix().player(),h.player);assertFalse(h.drawn);
    }
    @Test void boulderFullPoolKeepsAllNinetyFiveNonwrites(){
        var h=new B();for(int w=32;w<128;w++)if(w!=W)h.pool().write(h.pool().state(w).moveByte(ID,0x55).moveWord(Y_FRACTION,w));
        var old=h.pool().capture();h.run();for(int w=32;w<128;w++)if(w!=W)assertEquals(old.slots().get(w-32),h.pool().state(w));
        assertEquals(List.of(),h.fm);
    }
    @Test void boulderOrderedChargeStopSoundIsAppendedExactlyOnce(){
        var h=new B();var p=new P();p.charge=1;p.x=0x10808000;h.player=p.player(8,W);
        // Boulder only calls Solid while onscreen. An out-of-range standing player then
        // reaches native GetOffObject with both standing bits and this physical stand byte.
        h.pool().write(h.pool().state(W).moveByte(FLAGS,8));
        h.run();assertEquals(List.of(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP),h.fm);
        assertEquals(0,h.player.standObject());assertEquals(0,h.player.actor().motion().flags()&8);
        assertEquals(0,h.pool().state(W).flags()&8);assertEquals(1,h.calls);
    }
    private static final class H {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(layout(row(0x100,0x80,0x2A,0,0x40,0)));
        final List<String> order=new ArrayList<>();Cd3dRamp.PrimaryCall call;Cd3dRamp.Player player;int velocity;Runnable before=()->{};Error drawError;
        final Cd3dRamp ramp;
        H(){runtime.pool().write(runtime.pool().state(W).moveByte(ID,0x2A).moveWord(X,0x100).moveWord(Y,0x80).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0xFEDC));
            ramp=Cd3dRamp.withPrimaryPublication(runtime,new Cd3dRamp.Assets(CdObjectVisualAssets.decode(MAPPINGS,10,RAMP_SCRIPTS,3,63),MAP),c->{
                call=c;assertEquals(runtime.pool().capture().slots(),c.cut().slots());assertEquals(runtime.pool().state(W),c.source());before.run();velocity=c.signedWord();order.add(String.format("primary:%04X",velocity&65535));
            },(p,id)->{assertTrue(rampCurrentDraw());order.add("draw");if(drawError!=null)throw drawError;});}
        boolean rampCurrentDraw(){return ramp.isCurrentDrawCall(runtime.pool(),slot().identity());}
        CdObjectState slot(){return runtime.pool().state(W);}void edit(java.util.function.UnaryOperator<CdObjectState> f){runtime.pool().write(f.apply(slot()));}
        Cd3dRamp.Result run(int v){return ramp.run(W,player!=null?player:new Cd3dRamp.Player(0x100,0x80,v,0,0,0),new Cd3dRamp.Globals(1,0,0x80));}
    }
    private static final class B {
        final CdObjectPlacementRuntime runtime=new CdObjectPlacementRuntime(layout());CdSolidObject.Player player=new P().player(2,0);
        final CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);CdSolidObject.Outcome outcome;int calls;boolean drawn;final List<CdPlayerTouchResponse.FmSound> fm=new ArrayList<>();final CdBoulder body;
        B(){pool().write(pool().state(W).moveByte(ID,0x1B).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,16).moveByte(Y_RADIUS,16).moveWord(X,0x1000).moveWord(Y,0x200));
            var tile=ByteBuffer.allocate(74);for(int i=0;i<18;i++)tile.putShort((short)(36+2*i));for(int i=0;i<19;i++)tile.putShort((short)i);
            body=CdBoulder.withPrefixPublication(runtime,new CdBoulder.Assets(CdAssetBytes.copyOf(tile.array()),CdObjectVisualAssets.decodeStatic(HexFormat.of().parseHex("000202f8020000ecf00f0003f400"),1,19),MAP),new Terrain().owner(),c->{
                assertTrue(bodyCurrent(c));calls++;outcome=c.outcome();CdObjectState own;List<CdObjectState> others;List<CdPlayerTouchResponse.FmSound> sounds;
                if(outcome instanceof CdSolidObject.Completed d){var r=d.completedResult();own=r.object();others=r.otherSlots();player=r.player();sounds=r.fmRequests();}
                else{var r=((CdSolidObject.NativeStopped)outcome).prefix();own=r.object();others=r.otherSlots();player=r.player();sounds=r.fmRequests();}
                var images=new ArrayList<CdObjectState>();images.add(own);images.addAll(others);pool().restore(CdObjectPool.overlay(c.callCut(),images));fm.addAll(sounds);
            },(p,id)->drawn=true);}
        boolean bodyCurrent(CdBoulder.PrefixSolidCall c){return body.isCurrentPrefixSolidCall(c);}
        CdObjectPool pool(){return runtime.pool();}void run(){body.run(W,new CdBoulder.Globals(1,0,0x1080),new CdBoulder.SolidInputs(player,new CdSolidObject.Gates(0,0),globals));}
    }
    private static final class P {int charge;int x=0x10008000;
        CdSolidObject.Player player(int flags,int stand){return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x,0x01DD1234,0x123,0x100,0,2,0x10,flags,4,9,19,0,0,1,charge,0,0,0,0x56,0x78),0x780,2,0,0),1,0,stand);}
    }
    private static byte[] nSinglePieceFrames(int n) {
        var out = ByteBuffer.allocate(2 * n + 6 * n);
        for (int i = 0; i < n; i++) { out.putShort((short) (2 * n + 6 * i)); }
        for (int i = 0; i < n; i++) { out.put((byte) 1).put(new byte[5]); }
        return out.array();
    }
    private static byte[] tenSinglePieceFrames() { return nSinglePieceFrames(10); }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) { out[i] = (byte) values[i]; }
        return out;
    }

    private static int[] row(int x, int y, int id, int subtype, int periods, int subtype2) {
        return new int[]{x, y, id, subtype, periods, subtype2};
    }

    private static CdObjectPlacements layout(int[]... records) {
        int nullAt = 12 + 8 * (records.length + 1);
        var bytes = ByteBuffer.allocate(nullAt + 6);
        bytes.putShort((short) 12).putShort((short) nullAt).putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        for (int[] r : records) {
            bytes.putShort((short) r[0]).putShort((short) r[1]).put((byte) r[2]).put((byte) r[3])
                    .put((byte) r[4]).put((byte) r[5]);
        }
        bytes.putShort((short) 0xFFFF).putInt(0).putShort((short) 0);
        bytes.putShort((short) 0xFFFF).putInt(0);
        return CdObjectPlacements.decodeLayout(bytes.array());
    }

    private static final class Terrain {
        final byte[] chunks = new byte[61952], fg = new byte[338], bg = new byte[50];
        final byte[] ids = new byte[900], angles = new byte[256], heights = new byte[4096], widths = new byte[4096];
        Terrain() {
            fg[0] = 41; fg[1] = 7; bg[0] = 5; bg[1] = 7; Arrays.fill(fg, 2, fg.length, (byte) 2);
            ids[1] = 1; ids[2] = 2; Arrays.fill(heights, 16, 32, (byte) 16); Arrays.fill(widths, 32, 48, (byte) 16); angles[2] = (byte) 0xC0;
        }
        void row(int y, int value) { for (int x = 0; x < 16; x++) word(chunks, 512 + (y * 16 + x) * 2, value); }
        CdSolidObject owner() {
            var terrain = CdR11aTerrain.decode(new byte[6432], new byte[25600], chunks, fg, bg, ids, angles, heights, widths);
            byte[] animations = new byte[578]; for (int i = 0; i < 60; i++) word(animations, i * 2, 120); animations[122] = (byte) 0xFF;
            byte[] maps = new byte[398], dplcs = new byte[398];
            for (int i = 0; i < 198; i++) { word(maps, i * 2, 396); word(dplcs, i * 2, 396); }
            var visual = CdSonicVisualAssets.decode(maps, dplcs, new byte[69472]); byte[] atan = new byte[257];
            for (int i = 0; i <= 256; i++) atan[i] = (byte) (i / 8);
            var assets = CdR11aPlayerAssets.decode(animations, new byte[60], new byte[]{6, 7, 8, 0x49, 0x4C, (byte) 0xFF},
                    new byte[4], new byte[640], atan, visual);
            return new CdSolidObject(new CdPlayerMovement(terrain, assets));
        }
    }
    private static void word(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}
}
