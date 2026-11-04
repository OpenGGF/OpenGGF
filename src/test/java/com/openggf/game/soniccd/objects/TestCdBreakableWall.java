package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal native vectors authored before the component. Java UNRUN. Native1ecc5f3a:
 * PP Breakable Wall/Main.asm:7-159; Object Spawner.asm:329-340,385-446.
 * Constructed data/terrain are component oracles, never media or World admission. */
class TestCdBreakableWall {
    private static final int WHOLE = 46, MAP = 0x123456;
    private static final CdObjectState.LongField DX = TAMABBOH_MISSILE_X_DELTA, DY = TAMABBOH_MISSILE_Y_DELTA;
    private static final int[] X_SPEED = {0xFFFD097C, 0xFFFC25EE, 0xFFFD097C, 0xFFFD097C, 0xFFFC97B5, 0xFFFD097C};
    private static final int[] Y_SPEED = {0xFFFEB750, 0, 0x000148B0, 0xFFFE4445, 0, 0x0001BBBB};
    private static final int[][] FRAMES = {{8,9,8,12,13,12},{8,9,8,10,11,10},{10,11,10,10,11,10},
            {10,11,10,12,13,12},{9,8,9,13,12,13},{9,8,9,11,10,11},{11,10,11,11,10,11},{11,10,11,13,12,13}};
    private static final int[][] DELTAS = {{-8,-16},{0,16},{0,32},{16,0},{16,16},{16,32}};

    @Test void initLiteralFullImageFallsThroughOneSharedPlainSolidEvenWithSpriteBit7Clear() {
        var h = new H(); h.edit(s -> dirty(s).moveByte(ID,0x36).moveByte(ROUTINE,0).moveByte(SUBTYPE,5)
                .moveByte(COLLISION_STATUS,0).moveByte(SPRITE_FLAGS,0x63).moveByte(SAVED_FLAGS_ID,0)
                .moveByte(FLAGS,0).moveWord(X,0x1000).moveWord(Y,0x200));
        var expected = h.image(); init(expected); expected[0x25]=0; expected[1]&=0x7F;
        var r=h.run(); assertArrayEquals(expected,r.state().copyBytes());
        assertEquals(List.of("solid","draw"),h.order); assertEquals(1,h.solids.size());
        var done=assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst().outcome()).completedResult();
        assertEquals(Optional.of(CdSolidObject.GetOffReason.OBJECT_OFFSCREEN),done.getOffReason());
        assertEquals(CdSolidObject.Entry.SOLID_OBJECT,done.entry()); assertEquals(0x44BE,r.state().word(TILE));
    }

    @Test void brokenWallUsesParentThenFiveFreshSlotsCopiesOffsetParentAndMovesParentNow() {
        for(int subtype=0;subtype<8;subtype++) {
            var h=new H(); h.breaking(subtype,0x601); var before=h.images(); var player=h.player;
            var expected=expectedBreak(before,WHOLE,List.of(32,33,34,35,36),subtype,false,true);
            var r=h.run(); h.assertImages(expected); assertEquals(List.of("fm","halve","draw"),h.order);
            assertEquals(0x300,h.player.actor().motion().xVelocity()); assertSame(player.actor().motion(),h.halves.getFirst().expectedPlayer().actor().motion());
            assertEquals(6,r.effects().stream().filter(CdBreakableWall.PieceInitialized.class::isInstance).count());
            assertEquals(5,r.effects().stream().filter(CdBreakableWall.SlotSearchReturned.class::isInstance).count());
        }
    }

    @Test void firstFreeSearchPreservesDirtyFullImagesAndMayAllocateOnBothSidesOfParent() {
        var h=new H(); h.breaking(7,-3); int[] holes={33,45,47,70,127};
        for(int i=32;i<128;i++) if(i!=WHOLE) h.pool().write(dirty(h.pool().state(i)).moveByte(ID,0x55));
        for(int i:holes) h.pool().write(h.pool().state(i).moveByte(ID,0));
        var before=h.images(); var expected=expectedBreak(before,WHOLE,Arrays.stream(holes).boxed().toList(),7,true,true);
        h.run(); h.assertImages(expected); assertEquals(-2,h.player.actor().motion().xVelocity());
        assertEquals(0xF8,h.pool().state(33).centreX()&255); // parent integerX1000-8, then child row1 X delta0
        assertEquals(word(before.get(33),10),h.pool().state(33).word(X_FRACTION));
        assertEquals(before.get(33)[0x20]&255,h.pool().state(33).unsignedByte(COLLISION_TYPE));
    }

    @Test void everyAllocationFailureCutFallsParentWithoutRollingBackCompletedChildren() {
        for(int capacity=0;capacity<5;capacity++) {
            var h=new H(); h.breaking(1,0); for(int i=32;i<128;i++) if(i!=WHOLE) h.pool().write(h.pool().state(i).moveByte(ID,0x55));
            var free=new ArrayList<Integer>(); for(int i=0;i<capacity;i++){int whole=60+i;free.add(whole);h.pool().write(dirty(h.pool().state(whole)).moveByte(ID,0));}
            var expected=expectedBreak(h.images(),WHOLE,free,1,false,true); var r=h.run();h.assertImages(expected);
            var searches=r.effects().stream().filter(CdBreakableWall.SlotSearchReturned.class::isInstance).map(CdBreakableWall.SlotSearchReturned.class::cast).toList();
            assertEquals(capacity+1,searches.size());assertTrue(searches.getLast().wholeIndex().isEmpty());
            assertEquals(1+capacity,r.effects().stream().filter(CdBreakableWall.PieceInitialized.class::isInstance).count());
            assertEquals(List.of("fm","halve","draw"),h.order);
        }
    }

    @Test void explicitLaterPhysicalVisitFallsChildWhileEarlierHoleWaitsForItsOwnVisit() {
        var h=new H();h.breaking(5,0);for(int i=32;i<128;i++)if(i!=WHOLE)h.pool().write(h.pool().state(i).moveByte(ID,0x55));
        for(int i:new int[]{32,47})h.pool().write(h.pool().state(i).moveByte(ID,0));
        h.run();var earlier=h.pool().state(32);var later=h.pool().state(47);var expected=later.copyBytes();fall(expected);expected[1]&=0x7F;
        h.body.run(47,h.globals,h.inputs());assertArrayEquals(expected,h.pool().state(47).copyBytes());assertEquals(earlier,h.pool().state(32));
    }

    @Test void sourceArithmeticShiftPreservesNegativeOddRoundingAndAllOtherPlayerValues() {
        for(int velocity:new int[]{-32768,-3,-1,0,1,3,32767}) {
            var h=new H();h.breaking(0,velocity);var before=h.player;h.run();
            assertEquals(velocity>>1,h.halves.getFirst().finalXVelocity());assertEquals(velocity>>1,h.player.actor().motion().xVelocity());
            assertEquals(withXVelocity(before,velocity>>1),h.player);assertEquals(1,h.halves.size());
            assertEquals(velocity<0?-X_SPEED[0]:X_SPEED[0],h.slot().longBits(DX));
        }
    }

    @Test void fmFailureRetainsClearWordRoutineAndOriginalVelocityWordsButNoHalveOrAllocation() {
        var h=new H();h.breaking(3,-0x123);var before=h.images();var expected=before.get(WHOLE).clone();breakPrefix(expected,-0x123,0x234);
        var boom=new IllegalStateException("fm");h.fmAfter=c->{throw boom;};
        assertSame(boom,assertThrows(IllegalStateException.class,h::run));assertArrayEquals(expected,h.image());
        assertEquals(-0x123,h.player.actor().motion().xVelocity());assertTrue(h.halves.isEmpty());
        assertUnrelated(before,h.images(),Set.of(WHOLE));assertEquals(List.of("fm"),h.order);
    }

    @Test void halveFailurePreservesTheSoundAndIdenticalObjectPrefixAndCallerCommittedPlayer() {
        var h=new H();h.breaking(2,-3);var expected=h.image();breakPrefix(expected,-3,0x234);
        var boom=new IllegalArgumentException("halve");h.halveAfter=c->{throw boom;};
        assertSame(boom,assertThrows(IllegalArgumentException.class,h::run));assertArrayEquals(expected,h.image());
        assertEquals(-2,h.player.actor().motion().xVelocity());assertEquals(List.of("fm","halve"),h.order);
    }

    @Test void invalidSubtypeStopsAtReachedFrameReadAfterHalveAndPieceDimensions() {
        var h=new H();h.breaking(8,0x123);var expected=h.image();breakPrefix(expected,0x123,0x234);
        expected[0x17]=8;expected[0x19]=8;expected[0x16]=8;
        var e=assertThrows(CdBreakableWall.UnsupportedWall.class,h::run);assertEquals(CdBreakableWall.Boundary.PIECE_FRAME_READ,e.boundary());
        assertArrayEquals(expected,h.image());assertEquals(0x91,h.player.actor().motion().xVelocity());assertEquals(List.of("fm","halve"),h.order);
    }

    @Test void fallLongGravityWrapAndSignedPlayerRelativeDeletionUseNoSavedUnloadOrCameraTail() {
        for(int difference:new int[]{-225,-224,-223,0,32767,-32768}) {
            var h=new H();h.edit(s->dirty(s).moveByte(ID,0x36).moveByte(ROUTINE,4).moveByte(SPRITE_FLAGS,0x84)
                    .moveByte(SAVED_FLAGS_ID,9).moveLong(X_POSITION,0x7FFFF000).moveLong(Y_POSITION,0x02000000)
                    .moveLong(DX,0x4000).moveLong(DY,-0x4000));
            h.player=player(0x600,0x234,(0x200+difference)&65535);h.placement.bsetSavedFlag(9,1,CdObjectPlacementRuntime.SavedBit.LOADED);
            var bank=h.placement.capture().savedFlags();var expected=h.image();fall(expected);expected[1]&=0x7F;
            var r=h.run();assertEquals(difference<=-224,r.deleted());
            assertArrayEquals(difference<=-224?new byte[64]:expected,h.image());
            assertArrayEquals(bank.copy(),h.placement.capture().savedFlags().copy());assertEquals(difference<=-224?List.of():List.of("draw"),h.order);
        }
    }

    @Test void drawFailureAfterBitClearRetainsMovedFallPrefixAndOriginalException() {
        var h=new H();h.edit(s->s.moveByte(ID,0x36).moveByte(ROUTINE,4).moveByte(SPRITE_FLAGS,0x84)
                .moveLong(DX,0xFFFF8000).moveLong(DY,0x2000));var expected=h.image();fall(expected);expected[1]&=0x7F;
        var boom=new IllegalStateException("draw");h.drawAfter=s->{throw boom;};
        assertSame(boom,assertThrows(IllegalStateException.class,h::run));assertArrayEquals(expected,h.image());
    }

    @Test void drawBeforeMaskedDespawnRetainsEqualityAndUnloadsOnlyMappedSavedPeriod() {
        for(int x:new int[]{0xD80,0xDFF,0xE00}) {
            var h=new H();h.main();h.edit(s->s.moveWord(X,x).moveByte(SAVED_FLAGS_ID,9));
            h.placement.bsetSavedFlag(9,1,CdObjectPlacementRuntime.SavedBit.LOADED);h.globals=new CdBreakableWall.Globals(1,0,0xB80);
            var r=h.run();boolean deleted=x>=0xE00;assertEquals(deleted,r.deleted());
            assertEquals(List.of("solid","draw"),h.order);assertEquals(deleted?0:0x80,h.placement.savedFlag(9,1));
        }
        var h=new H();h.main();h.edit(s->s.moveByte(SAVED_FLAGS_ID,9));
        for(int p=0;p<3;p++)h.placement.bsetSavedFlag(9,p,CdObjectPlacementRuntime.SavedBit.LOADED);
        h.globals=new CdBreakableWall.Globals(0x81,1,0x1000);h.run();
        assertEquals(0,h.placement.savedFlag(9,0));assertEquals(0x80,h.placement.savedFlag(9,1));assertEquals(0x80,h.placement.savedFlag(9,2));
    }

    @Test void sharedSolidSideContactPublishesActualPlayerAndObjectBeforeDraw() {
        var h=new H();h.main();h.edit(s->s.moveByte(SPRITE_FLAGS,0x84));h.player=player(0x600,0,0x200,0x0FE78000,0);
        h.run();var done=assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst().outcome()).completedResult();
        assertEquals(CdSolidObject.Path.HALT_ON_X,done.path());assertEquals(0,h.player.actor().motion().xVelocity());
        assertEquals(0x0FE68000,h.player.actor().motion().centreX16_16());assertEquals(WHOLE,h.player.pushObject());
        assertEquals(0x20,h.slot().flags());assertEquals(List.of("solid","draw"),h.order);
    }

    @Test void stoppedSharedSolidPublishesItsRealPrefixAndRethrowsTheExactNativeStop() {
        var h=new H();h.main();h.edit(s->s.moveByte(SPRITE_FLAGS,0x84).moveWord(X,0xF8).moveWord(Y,0x100));
        Arrays.fill(h.terrain.fg,2,h.terrain.fg.length,(byte)0xF9);h.player=player(0x123,0x100,0xE0,0x00F88000,2);
        // As in the genuine plain-Solid prefix fixture, bit6 selects the unavailable122
        // chunk arm. Flags4 alone selects resident121 and legitimately returns.
        var actor=h.player.actor();var m=actor.motion();
        var motion=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),
                m.routine(),m.angle(),m.flags(),0x44,m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),
                m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        h.player=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion,actor.tile(),actor.priority(),actor.hurt(),actor.rotationDistance()),
                h.player.slotId(),h.player.pushObject(),h.player.standObject());
        h.build(h::solid,h::halve,h::draw,h::fm);
        RuntimeException thrown=assertThrows(RuntimeException.class,h::run);
        var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.solids.getFirst().outcome());
        assertSame(stopped.stop().originalException(),thrown);assertEquals(stopped.prefix().object(),h.slot());
        assertEquals(stopped.prefix().player(),h.player);assertEquals(List.of("solid"),h.order);
    }

    @Test void callbackNonwritesCheckFullPoolStatusBankAndSuppressVerificationOnIdenticalFault() {
        var h=new H();h.breaking(0,0x600);var boom=new IllegalStateException("callback");
        h.fmAfter=c->{h.pool().write(h.pool().state(90).moveByte(ID,0x55));throw boom;};
        assertSame(boom,assertThrows(IllegalStateException.class,h::run));assertEquals(1,boom.getSuppressed().length);
        assertEquals(0x55,h.pool().state(90).id());assertTrue(h.halves.isEmpty());
    }

    @Test void missingOwnersAndReentryStopAtTheirReachedNativeSitesWithPrefixIntact() {
        var h=new H();h.breaking(0,0x600);h.build(null,h::halve,h::draw,h::fm);
        // Solid is not reached on the broken branch.
        h.run();assertEquals(List.of("fm","halve","draw"),h.order);
        var absent=new H();absent.breaking(0,0x600);absent.build(absent::solid,null,absent::draw,absent::fm);
        assertEquals(CdBreakableWall.Boundary.HALVE_OWNER,assertThrows(CdBreakableWall.UnsupportedWall.class,absent::run).boundary());
        assertEquals(List.of("fm"),absent.order);
        var nested=new H();nested.breaking(0,0x600);nested.fmAfter=c->assertThrows(IllegalStateException.class,nested::run);
        nested.run();assertEquals(List.of("fm","halve","draw"),nested.order);
        var bad=new H();for(int routine:new int[]{1,3,5,255}){bad.edit(s->s.moveByte(ROUTINE,routine));var before=bad.images();assertEquals(CdBreakableWall.Boundary.ROUTINE,assertThrows(CdBreakableWall.UnsupportedWall.class,bad::run).boundary());bad.assertImages(before);}
    }

    @Test void pureCallbackValuesRejectWrongSourceAndNonNativeVelocityOrSoundWithoutWrites() {
        var h=new H();var before=h.images();var cut=new CdBreakableWall.SourceCut(h.slot(),h.placement.capture());
        var foreign=new H();assertThrows(IllegalArgumentException.class,()->new CdBreakableWall.SourceCut(foreign.slot(),h.placement.capture()));
        assertThrows(IllegalArgumentException.class,()->new CdBreakableWall.HalveXVelocityCall(cut,h.player,0));
        assertThrows(IllegalArgumentException.class,()->new CdBreakableWall.FmCall(cut,0x93));
        var valid=new CdBreakableWall.HalveXVelocityCall(cut,h.player,0x300);assertSame(cut,valid.cut());assertSame(h.player,valid.expectedPlayer());
        var data=assets();assertThrows(IllegalArgumentException.class,()->new CdBreakableWall.Assets(data.pieceFrames().slice(0,63),data.pieceDeltas(),data.pieceSpeeds(),MAP));
        assertThrows(NullPointerException.class,()->new CdBreakableWall.Assets(null,data.pieceDeltas(),data.pieceSpeeds(),MAP));
        h.assertImages(before);assertTrue(h.order.isEmpty());
    }

    @Test void failedSolidPublicationPreservesTheActualCommittedFullPrefixAndOriginalFailure() {
        var h=new H();h.main();h.edit(s->s.moveByte(SPRITE_FLAGS,0x84));var boom=new IllegalStateException("solid publication");
        h.build(c->{h.solid(c);throw boom;},h::halve,h::draw,h::fm);
        assertSame(boom,assertThrows(IllegalStateException.class,h::run));
        var done=assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst().outcome()).completedResult();
        assertEquals(done.object(),h.slot());assertEquals(done.player(),h.player);assertEquals(List.of("solid"),h.order);
        assertEquals(0,boom.getSuppressed().length);
    }

    @Test void rawPeriodsRemainUnreadUntilDespawnAndUseTheExactLinearSavedAddressOrWarpClamp() {
        var untouched=new H();untouched.main();untouched.globals=new CdBreakableWall.Globals(127,0xFE,0x1000);
        var bank=untouched.placement.capture().savedFlags();untouched.run();
        assertArrayEquals(bank.copy(),untouched.placement.capture().savedFlags().copy());

        var linear=new H();linear.main();linear.edit(s->s.moveByte(SAVED_FLAGS_ID,1));
        linear.globals=new CdBreakableWall.Globals(126,0,0);
        for(int p=0;p<3;p++)linear.placement.bsetSavedFlag(1,p,CdObjectPlacementRuntime.SavedBit.LOADED);
        linear.placement.bsetSavedFlag(43,0,CdObjectPlacementRuntime.SavedBit.LOADED); // exact byte2+3*1+126=131
        var expected=linear.placement.capture().savedFlags().copy();expected[131]&=0x7F;
        assertTrue(linear.run().deleted());assertArrayEquals(expected,linear.placement.capture().savedFlags().copy());

        var warp=new H();warp.main();warp.edit(s->s.moveByte(SAVED_FLAGS_ID,1));
        warp.globals=new CdBreakableWall.Globals(0xFE,0,0x1000);
        warp.placement.bsetSavedFlag(1,2,CdObjectPlacementRuntime.SavedBit.LOADED);
        warp.placement.bsetSavedFlag(43,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        var warpExpected=warp.placement.capture().savedFlags().copy();warpExpected[7]&=0x7F;
        assertTrue(warp.run().deleted());assertArrayEquals(warpExpected,warp.placement.capture().savedFlags().copy());
    }

    @Test void outOfBankRawSavedAddressStopsAfterDrawWithoutDeletingOrChangingTheBank() {
        var h=new H();h.main();h.edit(s->s.moveByte(SAVED_FLAGS_ID,255));
        h.globals=new CdBreakableWall.Globals(127,0,0);var bank=h.placement.capture().savedFlags();
        var stopped=assertThrows(CdBreakableWall.UnsupportedWall.class,h::run);
        assertEquals(CdBreakableWall.Boundary.SAVED_FLAGS_ADDRESS,stopped.boundary());
        assertEquals(List.of("solid","draw"),h.order);assertEquals(0x36,h.slot().id());
        assertEquals(0,h.slot().spriteFlags()&0x80);assertArrayEquals(bank.copy(),h.placement.capture().savedFlags().copy());
    }

    @Test void failedSolidPublicationReportsCorruptionOfItsOwnDeclaredOutputWithoutReplacingTheOriginalFailure() {
        var h=new H();h.main();h.edit(s->s.moveByte(SPRITE_FLAGS,0x84));var boom=new IllegalStateException("corrupt declared output");
        h.build(c->{h.solid(c);h.pool().write(h.slot().moveWord(TITLE_CARD_START_Y,0x7654));throw boom;},h::halve,h::draw,h::fm);
        assertSame(boom,assertThrows(IllegalStateException.class,h::run));
        assertEquals(1,boom.getSuppressed().length);assertEquals(0x7654,h.slot().word(TITLE_CARD_START_Y));
        assertEquals(List.of("solid"),h.order);
    }

    private static Map<Integer,byte[]> expectedBreak(Map<Integer,byte[]> before,int parent,List<Integer> holes,int subtype,boolean flip,boolean move) {
        var expected=copy(before);var p=expected.get(parent); // WORD saves are overwritten by complete piece stores.
        p[0x20]=p[0x21]=0;p[0x24]=(byte)(p[0x24]+2);
        var selected=new ArrayList<Integer>();selected.add(parent);selected.addAll(holes);
        for(int row=0;row<selected.size();row++) {
            var target=expected.get(selected.get(row));
            if(row!=0){target[0]=p[0];target[0x24]=p[0x24];w(target,8,word(p,8));w(target,12,word(p,12));target[1]=p[1];target[0x18]=p[0x18];l(target,4,longword(p,4));w(target,2,word(p,2));}
            target[0x17]=target[0x19]=target[0x16]=8;target[0x1A]=(byte)FRAMES[subtype][row];
            w(target,8,word(target,8)+DELTAS[row][0]);w(target,12,word(target,12)+DELTAS[row][1]);
            l(target,0x2E,Y_SPEED[row]);l(target,0x2A,flip?-X_SPEED[row]:X_SPEED[row]);
        }
        if(move){fall(p);p[1]&=0x7F;}return expected;
    }
    private static void init(byte[] b){b[0x24]+=2;b[1]|=4;b[0x18]=1;b[0x17]=b[0x19]=16;b[0x16]=24;b[0x20]=(byte)0xEF;w(b,2,0x44BE);l(b,4,MAP);b[0x1A]=b[0x28];}
    private static void breakPrefix(byte[] b,int x,int y){b[0x20]=b[0x21]=0;b[0x24]+=2;w(b,0x2A,x);w(b,0x2E,y);}
    private static void fall(byte[] b){l(b,0x2E,longword(b,0x2E)+0x4000);l(b,8,longword(b,8)+longword(b,0x2A));l(b,12,longword(b,12)+longword(b,0x2E));}
    private static int word(byte[] b,int i){return (b[i]&255)<<8|(b[i+1]&255);}
    private static int longword(byte[] b,int i){return word(b,i)<<16|word(b,i+2);}
    private static void w(byte[] b,int i,int v){b[i]=(byte)(v>>>8);b[i+1]=(byte)v;}
    private static void l(byte[] b,int i,int v){w(b,i,v>>>16);w(b,i+2,v);}
    private static CdObjectState dirty(CdObjectState s){for(var f:CdObjectState.ByteField.values())s=s.moveByte(f,0x51+7*f.byteOffset());for(var f:CdObjectState.WordField.values())s=s.moveWord(f,0xA157+0x103*f.byteOffset());for(var f:CdObjectState.LongField.values())s=s.moveLong(f,0xA1536729+0x1030507*f.byteOffset());return s;}
    private static Map<Integer,byte[]> copy(Map<Integer,byte[]> source){var r=new LinkedHashMap<Integer,byte[]>();source.forEach((k,v)->r.put(k,v.clone()));return r;}
    private static void assertUnrelated(Map<Integer,byte[]> before,Map<Integer,byte[]> after,Set<Integer> changed){for(int i:before.keySet())if(!changed.contains(i))assertArrayEquals(before.get(i),after.get(i));}
    private static CdBreakableWall.Assets assets(){byte[] f=new byte[64],d=new byte[24],v=new byte[48];for(int i=0;i<8;i++)for(int j=0;j<6;j++)f[i*8+j]=(byte)FRAMES[i][j];for(int i=0;i<6;i++){w(d,i*4,DELTAS[i][0]);w(d,i*4+2,DELTAS[i][1]);l(v,i*8,X_SPEED[i]);l(v,i*8+4,Y_SPEED[i]);}return new CdBreakableWall.Assets(CdAssetBytes.copyOf(f),CdAssetBytes.copyOf(d),CdAssetBytes.copyOf(v),MAP);}
    private static CdObjectPlacements layout(){var b=ByteBuffer.allocate(34);b.putShort((short)12).putShort((short)28).putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0).putShort((short)0).put((byte)0x36).put((byte)0).put((byte)0x40).put((byte)0);b.putShort((short)0xFFFF).putInt(0).putShort((short)0);b.putShort((short)0xFFFF).putInt(0);return CdObjectPlacements.decodeLayout(b.array());}
    private static CdSolidObject.Player player(int xv,int yv,int y){return player(xv,yv,y,0x10001234,2);}
    private static CdSolidObject.Player player(int xv,int yv,int y,int x,int flags){return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x,(y<<16)|0x5678,xv,yv,0x345,2,0x10,flags,4,9,19,2,3,1,0,0,0,0,0x12,0x34),0x780,2,7,0),1,0,0);}
    private static CdSolidObject.Player withXVelocity(CdSolidObject.Player p,int xv){var a=p.actor();var m=a.motion();var n=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),xv,m.yVelocity(),m.groundVelocity(),m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(n,a.tile(),a.priority(),a.hurt(),a.rotationDistance()),p.slotId(),p.pushObject(),p.standObject());}

    private static final class H {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(layout());final Terrain terrain=new Terrain();
        CdSolidObject.Player player=player(0x601,0x234,0x200);CdSolidObject.Globals shared=new CdSolidObject.Globals(0,4,0x12,0x34);
        CdBreakableWall.Globals globals=new CdBreakableWall.Globals(1,0,0x1000);CdBreakableWall body;
        final List<String> order=new ArrayList<>();final List<CdBreakableWall.SolidCall> solids=new ArrayList<>();final List<CdBreakableWall.HalveXVelocityCall> halves=new ArrayList<>();
        Consumer<CdBreakableWall.FmCall> fmAfter=c->{};Consumer<CdBreakableWall.HalveXVelocityCall> halveAfter=c->{};Consumer<CdObjectState> drawAfter=s->{};
        H(){edit(s->s.moveByte(ID,0x36).moveWord(X,0x1000).moveWord(Y,0x200));build(this::solid,this::halve,this::draw,this::fm);}
        void build(CdBreakableWall.SolidCommit solid,CdBreakableWall.HalveXVelocity halve,CdBreakableWall.DrawObject draw,CdBreakableWall.PlayFm fm){body=new CdBreakableWall(placement,assets(),terrain.owner(),solid,halve,draw,fm);}
        CdObjectPool pool(){return placement.pool();}CdObjectState slot(){return pool().state(WHOLE);}byte[] image(){return slot().copyBytes();}
        void edit(UnaryOperator<CdObjectState> op){pool().write(op.apply(slot()));}void main(){edit(s->s.moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,16).moveByte(Y_RADIUS,24).moveByte(COLLISION_STATUS,0));}
        void breaking(int subtype,int xv){main();edit(s->s.moveByte(SUBTYPE,subtype).moveByte(COLLISION_TYPE,0xEF).moveByte(COLLISION_STATUS,1).moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678).moveLong(MAPPINGS,MAP));player=player(xv,0x234,0x200);}
        CdBreakableWall.Inputs inputs(){return new CdBreakableWall.Inputs(player,new CdSolidObject.Gates(0,0),shared);}CdBreakableWall.Result run(){return body.run(WHOLE,globals,inputs());}
        void solid(CdBreakableWall.SolidCall c){order.add("solid");solids.add(c);assertEquals(player,c.expectedPlayer());CdObjectState object;List<CdObjectState> other;
            if(c.outcome() instanceof CdSolidObject.Completed d){object=d.completedResult().object();other=d.completedResult().otherSlots();player=d.completedResult().player();shared=d.completedResult().globals();}
            else{var p=((CdSolidObject.NativeStopped)c.outcome()).prefix();object=p.object();other=p.otherSlots();player=p.player();shared=p.globals();}
            pool().write(object);other.forEach(pool()::write);}
        void halve(CdBreakableWall.HalveXVelocityCall c){order.add("halve");halves.add(c);assertEquals(player,c.expectedPlayer());player=withXVelocity(player,c.finalXVelocity());halveAfter.accept(c);}
        void fm(CdBreakableWall.FmCall c){order.add("fm");assertEquals(0xB0,c.nativeId());fmAfter.accept(c);}
        void draw(CdObjectPool p,CdObjectState.Identity i){order.add("draw");var s=p.state(i.wholeIndex());p.write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()&0x7F));drawAfter.accept(p.state(i.wholeIndex()));}
        Map<Integer,byte[]> images(){var r=new LinkedHashMap<Integer,byte[]>();for(var s:pool().capture().slots())r.put(s.identity().wholeIndex(),s.copyBytes());return r;}
        void assertImages(Map<Integer,byte[]> expected){expected.forEach((i,b)->assertArrayEquals(b,pool().state(i).copyBytes(),"whole "+i));}
    }
    /** Same real decoded format/probe fixture as TestCdSolidObject, not a mocked Solid owner. */
    private static final class Terrain {
        final byte[] chunks=new byte[61952],fg=new byte[338],bg=new byte[50],ids=new byte[900],angles=new byte[256],heights=new byte[4096],widths=new byte[4096];
        Terrain(){fg[0]=41;fg[1]=7;bg[0]=5;bg[1]=7;Arrays.fill(fg,2,fg.length,(byte)2);ids[1]=1;ids[2]=2;Arrays.fill(heights,16,32,(byte)16);Arrays.fill(widths,32,48,(byte)16);angles[2]=(byte)0xC0;}
        CdSolidObject owner(){var terrain=CdR11aTerrain.decode(new byte[6432],new byte[25600],chunks,fg,bg,ids,angles,heights,widths);byte[] ani=new byte[578];for(int i=0;i<60;i++)w(ani,i*2,120);ani[122]=(byte)0xFF;
            byte[] maps=new byte[398],dplcs=new byte[398];for(int i=0;i<198;i++){w(maps,i*2,396);w(dplcs,i*2,396);}var visual=CdSonicVisualAssets.decode(maps,dplcs,new byte[69472]);byte[] atan=new byte[257];for(int i=0;i<=256;i++)atan[i]=(byte)(i/8);
            var assets=CdR11aPlayerAssets.decode(ani,new byte[60],new byte[]{6,7,8,0x49,0x4C,(byte)0xFF},new byte[4],new byte[640],atan,visual);return new CdSolidObject(new CdPlayerMovement(terrain,assets));}
    }
}
