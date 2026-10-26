package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectPlacements;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.UnaryOperator;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdTunnelPath.TunnelPlayerField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests FIRST, independently drafted in memory; author compilation/discovery/RED/execution UNRUN.
 * J 558dd7d00a6c9ba447c83f1bb98effd7de4675c7; N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Tunnel Path/Main.asm:119-478; Object Spawner.asm:385-446; Object Functions.asm:228-237.
 * Literal operands and real public immutable component/pool/placement APIs, no effect replay.
 * Constructed assets/player pair confer no Runtime/Frame authentication. No media/native/GL work.
 * All 64 bytes are compared, but opaque gaps remain their public fresh-pool zero ingress; no reflection.
 * Private invocation, exact-once same-value acceptance, Frame atomic preflight, natural route and
 * controlled Body ownership remain Root obligations, not capabilities manufactured by this harness.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdTunnelPath {
    static final int WHOLE=63, GV=0x208D90, PATH=0x208D98, MAP=0x23EE9C;
    static final String RAW_PATHS =
            "0006009000d60088144000f01478010814900140149001e0144001f8140001e013f001c0"
          + "13f00180140001701420016814400170146801a81660021816a0021016c001f816d001c8"
          + "16c001a816800198165801a0164001c8165001f01680020016c0020016d0021016d00288"
          + "16c002c0168002d8165002c0165002a01680029017000290172802a0172802e0170002f0"
          + "00440f0801a00f9001a00fc801b80fe001f00fe0026010000290103002a01068028810800250"
          + "10680218103002000ff002200fe0026010000290103002a010680288113001c8"
          + "004416300290163003181638033816d003d0170003e0173803c8175803901738035816f80340"
          + "16c0036016a8039016d003d0170003e0173803c817b8034817d0032017d00268";

    @Test void aliasOffsetsAndBigEndianOverlap() {
        assertEquals(4,CdTunnelPath.NativeRoutine.values().length);
        assertEquals(22,CdTunnelPath.ObjectField.values().length);
        assertEquals(24,CdTunnelPath.TunnelPlayerField.values().length);
        assertEquals(18,CdTunnelPath.NativeCut.values().length);
        assertEquals(10,CdTunnelPath.StoreKind.values().length);
        assertEquals(16,CdTunnelPath.DeleteLong.values().length);
        var s=new CdObjectPool().state(WHOLE).moveWord(TUNNEL_PATH_STEPS,0x1234)
                .moveWord(TUNNEL_PATH_INDEX_END,0x5688).moveWord(TUNNEL_PATH_ROUTINE_WORD,2);
        assertEquals(0x12,s.unsignedByte(TUNNEL_PATH_STEPS_HIGH));
        assertEquals(0x56,s.unsignedByte(TUNNEL_PATH_INDEX)); assertEquals(0x88,s.unsignedByte(TUNNEL_PATH_END));
        assertEquals(0,s.routine()); assertEquals(2,s.unsignedByte(ROUTINE_2));
        assertEquals(0x1134,s.addByte(TUNNEL_PATH_STEPS_HIGH,-1).word(TUNNEL_PATH_STEPS));
        assertEquals(32,MOVE_X_LONG.widthBits()); assertEquals(16,RELEASE_Y_MASK.widthBits());
        assertThrows(IllegalArgumentException.class,()->new CdTunnelPath.PlayerStore(ACQUIRE_CONTROL,0,0x80));
        assertThrows(IllegalArgumentException.class,()->new CdTunnelPath.PlayerStore(ACQUIRE_AIR,0,4));
        assertThrows(IllegalArgumentException.class,()->new CdTunnelPath.Globals(256,0,0));
    }
    @Test void foreignObjectOwnerRejected() {
        var h=ready(); var other=new CdObjectPool();
        assertThrows(IllegalArgumentException.class,()->h.pool().write(other.state(WHOLE)));
        h.edit(s->s.moveByte(ID,9)); assertThrows(IllegalArgumentException.class,h::run);
        assertEquals(0,h.reads);
    }
    @Test void initPath0DirtyNonwrites() { initLiteral(0,0x0088,0x208DA0,0x1440,0x00F0); }
    @Test void initPath1DirtyNonwrites() { initLiteral(1,0x0044,0x208E2A,0x0F08,0x01A0); }
    @Test void initPath2DirtyNonwrites() { initLiteral(2,0x0044,0x208E70,0x1630,0x0290); }
    @Test void initHighBitAliasesDirectoryAndSkipsGvel() {
        for(int subtype:new int[]{0x80,0x81,0x82}) {
            var h=new Harness(assets(new byte[0],hex(RAW_PATHS))); h.edit(s->s.moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype));
            h.run(); assertEquals(4,h.slot().routine()); assertEquals(0x81,h.live.state().motion().playerControl());
            assertEquals(0x1000,h.live.state().motion().groundVelocity()); assertEquals(1,h.fm.size());
        }
        var raw=new Harness(new CdTunnelPath.Assets(CdAssetBytes.copyOf(new byte[0]),0xCD208D90,
                CdAssetBytes.copyOf(hex(RAW_PATHS)),0xEF208D98,0xAB23EE9C));
        raw.seed.x=0; raw.install(); raw.edit(s->s.moveByte(ROUTINE,0)); raw.run();
        assertEquals(0xEF208DA0,raw.slot().longBits(TUNNEL_PATH_DATA));
        assertEquals(0xAB23EE9C,raw.slot().longBits(MAPPINGS));
    }
    @Test void initSelectedDirectoryFaultPrefix() {
        initFault(0,CdTunnelPath.UnsupportedBoundary.DIRECTORY_WORD,0);
        var overlap=new Harness(new CdTunnelPath.Assets(CdAssetBytes.copyOf(hex("10000c000c000800")),PATH,
                CdAssetBytes.copyOf(hex(RAW_PATHS)),PATH,MAP));
        overlap.edit(s->s.moveByte(ROUTINE,0));
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,overlap::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.DIRECTORY_WORD,e.boundary());
        assertEquals(CdTunnelPath.StopReason.AMBIGUOUS_ADDRESS,e.reason()); assertEquals(2,overlap.slot().routine());
    }
    @Test void initSelectedHeaderFaultPrefix() { initFault(6,CdTunnelPath.UnsupportedBoundary.HEADER_WORD,0); }
    @Test void initSelectedTargetXFaultPrefix() { initFault(8,CdTunnelPath.UnsupportedBoundary.INIT_X_WORD,2); }
    @Test void initSelectedTargetYFaultPrefix() { initFault(10,CdTunnelPath.UnsupportedBoundary.INIT_Y_WORD,3); }
    @Test void rawDirectoryAlias3InsideOwnedSpan() {
        var h=ready(); h.seed.x=0; h.install(); h.edit(s->s.moveByte(ROUTINE,0).moveByte(SUBTYPE,3));
        h.run(); assertEquals(0x1728,h.slot().word(TUNNEL_PATH_INDEX_END));
        assertEquals(0x208E22,h.slot().longBits(TUNNEL_PATH_DATA));
        assertEquals(0x02E0,h.slot().word(TUNNEL_PATH_TARGET_X));
        assertEquals(0x1700,h.slot().word(TUNNEL_PATH_TARGET_Y));
    }
    @Test void rawGvelAlias4InsidePaths() {
        var h=ready(); h.edit(s->s.moveByte(SUBTYPE,4)); h.run();
        assertEquals(0x1000,h.live.state().motion().groundVelocity()); assertEquals(1,h.fm.size());
        var padded=new Harness(assets(new byte[16],new byte[0])); padded.edit(s->s.moveByte(SUBTYPE,4));
        var fault=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,padded::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.GVEL_WORD,fault.boundary()); assertEquals(GV+8,fault.value());
    }
    @Test void rawGvelAlias15InsidePaths() {
        byte[] paths=hex(RAW_PATHS); w(paths,22,0x2000); var h=new Harness(assets(hex("10000c000c000800"),paths));
        h.edit(s->s.moveByte(SUBTYPE,15)); h.run();
        assertEquals(0x2000,h.live.state().motion().groundVelocity());
        assertEquals(List.of(ACQUIRE_GVEL_BASE,ACQUIRE_GVEL_RAISE),
                h.fields().stream().filter(f->f==ACQUIRE_GVEL_BASE||f==ACQUIRE_GVEL_RAISE).toList());
    }
    @Test void rawHighBytePointerAlias() {
        var h=completion(); h.edit(s->s.moveLong(TUNNEL_PATH_DATA,0xAB208DA0)); h.run();
        assertEquals(0xAB208DA0,h.slot().longBits(TUNNEL_PATH_DATA));
        assertEquals(0x1478,h.slot().word(TUNNEL_PATH_TARGET_X));
        assertEquals(0x0108,h.slot().word(TUNNEL_PATH_TARGET_Y));
    }
    @Test void wordAlignedPairAtOffset2Allowed() {
        var h=completion(); h.edit(s->s.moveLong(TUNNEL_PATH_DATA,PATH+10));
        // Both aligned WORD reads complete; this alias's later DIVS overflows, leaving a zero divisor.
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.DIVIDE_X_SPEED,e.boundary());
        assertEquals(CdTunnelPath.StopReason.ZERO_DIVISOR,e.reason());
        assertEquals(0x0108,h.slot().word(TUNNEL_PATH_TARGET_X));
        assertEquals(0x1490,h.slot().word(TUNNEL_PATH_TARGET_Y));
    }
    @Test void oddSelectedWordFault() {
        var h=completion(); h.edit(s->s.moveLong(TUNNEL_PATH_DATA,0xAB208D99));
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.StopReason.UNALIGNED_WORD,e.reason());
        assertEquals(CdTunnelPath.UnsupportedBoundary.NEXT_X_WORD,e.boundary()); assertEquals(0xAB208D9D,e.value());
        assertEquals(4,h.slot().unsignedByte(TUNNEL_PATH_INDEX));
    }
    @Test void unownedRawPointerFaultPrefix() {
        var h=completion(); h.edit(s->s.moveLong(TUNNEL_PATH_DATA,0xDE000000)); var before=h.live;
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.NEXT_X_WORD,e.boundary()); assertEquals(0xDE000004,e.value());
        assertEquals(0xFF7F,h.slot().word(TUNNEL_PATH_STEPS)); assertEquals(4,h.slot().unsignedByte(TUNNEL_PATH_INDEX));
        assertEquals(0x1440ABCD,h.live.state().motion().centreX16_16());
        assertEquals(0x00F0BEEF,h.live.state().motion().centreY16_16());
        assertEquals(before.environment(),h.live.environment()); assertTrue(h.fm.isEmpty());
        var padded=new Harness(assets(hex("10000c000c000800"),Arrays.copyOf(hex(RAW_PATHS),288)));
        padded.edit(s->s.moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0).moveLong(TUNNEL_PATH_DATA,PATH+280));
        var outside=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,padded::run);
        assertEquals(PATH+284,outside.value()); assertEquals(CdTunnelPath.UnsupportedBoundary.NEXT_X_WORD,outside.boundary());
    }
    @Test void indexByteWrapAndUnsignedEnd() {
        var h=completion(); h.edit(s->s.moveByte(TUNNEL_PATH_INDEX,0xFC).moveByte(TUNNEL_PATH_END,0xFF));
        h.run(); assertEquals(0,h.slot().unsignedByte(TUNNEL_PATH_INDEX)); assertEquals(6,h.slot().routine());
        var end=completion(); end.edit(s->s.moveByte(TUNNEL_PATH_INDEX,0xFC).moveByte(TUNNEL_PATH_END,0));
        end.run(); assertEquals(0,end.slot().routine()); assertEquals(2,end.slot().unsignedByte(ROUTINE_2));
    }
    @Test void animationGateSkipsBadRoutineAndTail() {
        var h=ready(); h.seed.anim=0x2B; h.install(); h.camera=0; h.edit(s->s.moveByte(ROUTINE,0xFF).moveByte(SPRITE_FLAGS,4));
        var before=h.pool().capture(); var r=h.run(); assertEquals(before.slots(),h.pool().capture().slots());
        assertTrue(r.effects().isEmpty()); assertTrue(h.fm.isEmpty()); assertTrue(h.players.isEmpty());
    }
    @Test void deadGateSkipsBadRoutineAndTail() {
        for(int routine:new int[]{6,0xFF}) {
            var h=ready(); h.seed.routine=routine; h.install(); h.camera=0; h.edit(s->s.moveByte(ROUTINE,1).moveByte(SPRITE_FLAGS,4));
            var before=h.slot(); assertEquals(before,h.run().state()); assertTrue(h.fm.isEmpty());
        }
    }
    @Test void travelPreludeBeforeAnimationGate() {
        var h=ready(); h.time=0x81; h.direction=1; h.seed.anim=0x2B; h.install();
        h.edit(s->s.moveByte(SAVED_FLAGS_ID,1).moveByte(ROUTINE,0xFF));
        h.placement.bsetSavedFlag(1,0,CdObjectPlacementRuntime.SavedBit.LOADED);
        var r=h.run(); assertEquals(0,h.placement.savedFlag(1,0)); assertEquals(0xFF,h.slot().routine());
        assertEquals(1,bankEffects(r).size()); assertTrue(h.players.isEmpty());
    }
    @Test void travelPreludeBeforeDeadGate() {
        var h=ready(); h.time=0x80; h.direction=0xFF; h.seed.routine=6; h.install();
        h.edit(s->s.moveByte(SAVED_FLAGS_ID,1));
        h.placement.bsetSavedFlag(1,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        h.run(); assertEquals(0,h.placement.savedFlag(1,1)); assertEquals(7,h.slot().id()); assertTrue(h.players.isEmpty());
    }
    @Test void travelSavedZeroSkipsAddressing() {
        var h=ready(); h.time=0xFF; h.direction=0x80; h.seed.anim=0x2B; h.install();
        var bank=h.placement.capture().savedFlags().copy(); h.run();
        assertArrayEquals(bank,h.placement.capture().savedFlags().copy());
    }
    @Test void travelClampsWordDirection() {
        for(int[] v:new int[][]{{0x80,1,0},{0x80,255,1},{0x81,255,2},{0xFF,128,2},{0x80,127,0}}) {
            var h=ready(); h.time=v[0]; h.direction=v[1]; h.seed.anim=0x2B; h.install();
            h.edit(s->s.moveByte(SAVED_FLAGS_ID,1)); h.placement.bsetSavedFlag(1,v[2],CdObjectPlacementRuntime.SavedBit.LOADED);
            var r=h.run(); assertEquals(2+3+v[2],bankEffects(r).getFirst().byteIndex());
            assertEquals(0,h.placement.savedFlag(1,v[2]));
        }
    }
    @Test void duplicatePreludeAndTailBankStores() {
        var h=ready(); h.time=0x81; h.seed.ctrl=1; h.install(); h.edit(s->s.moveByte(SAVED_FLAGS_ID,1));
        h.placement.bsetSavedFlag(1,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        var r=h.run(); var stores=bankEffects(r); assertTrue(r.deleted()); assertEquals(2,stores.size());
        assertEquals(0x80,stores.get(0).previousValue()); assertEquals(0,stores.get(1).previousValue());
        assertEquals(CdTunnelPath.BankSite.TRAVEL_PRELUDE,stores.get(0).site());
        assertEquals(CdTunnelPath.BankSite.DESPAWN,stores.get(1).site());
        assertArrayEquals(new byte[64],r.state().copyBytes());
    }
    @Test void rawNonwarpBankLinearAlias() {
        var h=ready(); h.time=127; h.camera=0; h.seed.x=0; h.install();
        h.edit(s->s.moveByte(SAVED_FLAGS_ID,1).moveByte(SPRITE_FLAGS,4));
        h.placement.bsetSavedFlag(43,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        var r=h.run(); assertTrue(r.deleted()); assertEquals(132,bankEffects(r).getFirst().byteIndex());
        assertEquals(0,h.placement.savedFlag(43,1));
        var last=ready(); last.time=1; last.camera=0; last.seed.x=0; last.install();
        last.edit(s->s.moveByte(SAVED_FLAGS_ID,254).moveByte(SPRITE_FLAGS,4));
        last.placement.bsetSavedFlag(254,1,CdObjectPlacementRuntime.SavedBit.LOADED);
        var finalByte=last.run(); assertTrue(finalByte.deleted()); assertEquals(765,bankEffects(finalByte).getFirst().byteIndex());
    }
    @Test void savedZeroSkipsNonwarpPeriod() {
        var h=ready(); h.time=127; h.camera=0; h.seed.x=0; h.install(); h.edit(s->s.moveByte(SPRITE_FLAGS,4));
        var bank=h.placement.capture().savedFlags().copy(); var r=h.run();
        assertTrue(r.deleted()); assertTrue(bankEffects(r).isEmpty()); assertArrayEquals(bank,h.placement.capture().savedFlags().copy());
    }
    @Test void bankOutOfBoundsStopsBeforeDeletion() {
        var h=ready(); h.time=2; h.camera=0; h.seed.x=0; h.install();
        h.edit(s->s.moveByte(SAVED_FLAGS_ID,254).moveByte(SPRITE_FLAGS,4)); var before=h.slot();
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.SAVED_FLAGS_ADDRESS,e.boundary()); assertEquals(766,e.value());
        assertEquals(before,h.slot()); assertTrue(h.fm.isEmpty());
    }
    @Test void rawRoutineOddOrUnownedStopsAfterPrelude() {
        for(int routine:new int[]{1,3,5,7,8,0xFE,0xFF}) {
            var h=ready(); h.time=0x81; h.edit(s->s.moveByte(ROUTINE,routine).moveByte(SAVED_FLAGS_ID,1));
            h.placement.bsetSavedFlag(1,1,CdObjectPlacementRuntime.SavedBit.LOADED);
            var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
            assertEquals(CdTunnelPath.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());
            assertEquals(routine,e.value()); assertEquals(0,h.placement.savedFlag(1,1)); assertEquals(routine,h.slot().routine());
        }
    }
    @Test void wrappedContactEdges() {
        for(int axis=0;axis<2;axis++) for(int delta:new int[]{-17,-16,15,16}) {
            var h=ready(); h.edit(s->s.moveWord(X,0xFFF8).moveWord(Y,0xFFF8));
            h.seed.x=((axis==0?(0xFFF8+delta)&0xFFFF:0xFFF8)<<16)|0xABCD;
            h.seed.y=((axis==1?(0xFFF8+delta)&0xFFFF:0xFFF8)<<16)|0xBEEF; h.install();
            h.run(); assertEquals(delta>=-16&&delta<=15?4:2,h.slot().routine());
        }
    }
    @Test void controlAnyNonzeroRejects() {
        for(int control:new int[]{1,4,8,0x80,0x81,0xC1,0xFF}) {
            var h=ready(); h.seed.ctrl=control; h.install(); var before=h.live; var source=h.slot();
            h.run(); assertEquals(source,h.slot()); assertEquals(before,h.live); assertTrue(h.players.isEmpty()); assertTrue(h.fm.isEmpty());
        }
    }
    @Test void suppliedX1226RejectsWithoutAttachment() {
        var h=ready(); h.seed.x=0x12264900; h.seed.y=0x026B8400; h.install(); h.edit(s->s.moveByte(ROUTINE,0).moveByte(SUBTYPE2,1));
        var before=h.live; h.run(); assertEquals(2,h.slot().routine()); assertEquals(before,h.live); assertTrue(h.players.isEmpty());
        assertEquals(0xFE46,(0x1226-0x13F0+16)&0xFFFF); assertTrue(h.fm.isEmpty());
    }
    @Test void acquire81ExactDirtyStateAndEnvironment() { acquireLiteral(0,0x81); }
    @Test void acquireC1ExactDirtyStateAndEnvironment() { acquireLiteral(1,0xC1); }
    @Test void hurt4OnlyCorrection() {
        for(int routine:new int[]{0,1,2,3,4,5}) {
            var h=ready(); h.seed.routine=routine; h.install(); h.run();
            assertEquals(routine==4?2:routine,h.live.state().motion().routine());
            assertEquals(routine==4?0x78:0xA1B2,h.live.state().timers().hurt());
            assertEquals(routine==4,h.fields().contains(HURT_ROUTINE));
        }
    }
    @Test void gvelBaseStoreBeforeReachedFault() {
        var h=new Harness(assets(new byte[0],new byte[0])); var before=h.seed.copy();
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.GVEL_WORD,e.boundary()); assertEquals(GV,e.value());
        before.ctrl=0x81; before.anim=2; before.gv=0x1000;
        assertEquals(before.cut(),h.live); assertEquals(4,h.slot().routine()); assertEquals(0xA5,h.slot().unsignedByte(TUNNEL_PATH_SCRATCH));
        assertTrue(h.fm.isEmpty()); assertFalse(h.fields().contains(ACQUIRE_X_VELOCITY));
        var hurt=new Harness(assets(new byte[0],new byte[0])); hurt.seed.routine=4; hurt.install();
        assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,hurt::run);
        assertEquals(2,hurt.live.state().motion().routine()); assertEquals(0x78,hurt.live.state().timers().hurt());
        assertEquals(0x1000,hurt.live.state().motion().groundVelocity()); assertEquals(4,hurt.slot().routine());
    }
    @Test void signedGvelCompareAndNegativeSubtypeSkip() {
        for(int selected:new int[]{0x0800,0x1000,0x2000,0x8000,0xFFFF}) {
            byte[] speeds=hex("10000c000c000800"); w(speeds,0,selected); var h=new Harness(assets(speeds,hex(RAW_PATHS))); h.run();
            assertEquals((short)selected>0x1000?selected:0x1000,h.live.state().motion().groundVelocity()&0xFFFF);
        }
        var negative=new Harness(assets(new byte[0],new byte[0])); negative.edit(s->s.moveByte(SUBTYPE,0xFF));
        negative.run(); assertEquals(0x1000,negative.live.state().motion().groundVelocity());
    }

    @Test void speedXBranchLiteral() {
        var h=speed(0x13F0,0xF0,0x1440,0xF0,0x1000); h.run();
        assertSpeed(h,0x1000,0,0x0500); assertEquals(List.of(SPEED_X_Y_VELOCITY,SPEED_X_X_VELOCITY),h.fields());
    }
    @Test void speedYBranchLiteral() {
        var h=speed(0x1630,0x290,0x1630,0x318,0x1000); h.run();
        assertSpeed(h,0,0x1000,0x0880); assertEquals(List.of(SPEED_Y_X_VELOCITY,SPEED_Y_Y_VELOCITY),h.fields());
    }
    @Test void speedTieSelectsY() {
        var h=speed(0x100,0x100,0x110,0x110,0x1000); h.run();
        assertSpeed(h,0x1000,0x1000,0x0100); assertEquals(SPEED_Y_X_VELOCITY,h.fields().getFirst());
    }
    @Test void speedNegativeAndTruncation() {
        var negative=speed(0x1500,0x100,0x14B0,0x100,0x1000); negative.run(); assertSpeed(negative,0xF000,0,0x0500);
        var fraction=speed(0x100,0x100,0x131,0x111,0x1000); fraction.run(); assertSpeed(fraction,0x1000,0x058D,0x0310);
    }
    @Test void subOverflowUsesNativeBge() {
        var h=speed(0x7FFF,0,0x8000,0,0x1000); h.run();
        assertSpeed(h,0xF000,0,0x0010); // SUB.W result0001, N=0,V=1 => BGE is false.
    }
    @Test void divOverflowRetainsOriginalBits() {
        var h=speed(0,0,0,0x8000,1); h.run(); assertSpeed(h,0,0xFFFF,0);
        var minimum=speed(0,0,0,0x8000,0xFFFF); minimum.run(); assertSpeed(minimum,0,1,0);
    }
    @Test void zeroTimeDivisorStopsBeforeSpeedStores() {
        var h=speed(0,0,0,0x10,0); var before=h.live; var source=h.slot();
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.DIVIDE_Y_TIME,e.boundary());
        assertEquals(before,h.live); assertEquals(source,h.slot()); assertTrue(h.fm.isEmpty());
        var x=speed(0,0,0x10,0,0);
        assertEquals(CdTunnelPath.UnsupportedBoundary.DIVIDE_X_TIME,assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,x::run).boundary());
    }
    @Test void zeroSecondaryDivisorStopsBeforeSpeedStores() {
        var h=speed(0,0,1,0x8000,1); var before=h.live;
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.DIVIDE_X_SPEED,e.boundary()); assertEquals(before,h.live);
        var x=speed(0,0,0x8000,1,1);
        assertEquals(CdTunnelPath.UnsupportedBoundary.DIVIDE_Y_SPEED,assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,x::run).boundary());
    }
    @Test void gotSpeedThenRoutineThenSecondFm() {
        var h=ready(); h.run(); assertEquals(1,h.fm.size()); assertEquals(CdTunnelPath.FmSite.MAIN_ACQUIRE,h.fm.getFirst().site());
        var position=h.live.state().motion(); h.run(); assertEquals(6,h.slot().routine()); assertSpeed(h,0x1000,0,0x0500);
        assertEquals(2,h.fm.size()); assertEquals(CdTunnelPath.FmSite.GOT_PLAYER,h.fm.get(1).site());
        assertEquals(position.centreX16_16(),h.live.state().motion().centreX16_16());
        assertEquals(6,h.fm.get(1).expectedSource().routine());
    }
    @Test void highByteCountdownAndFraction() {
        var h=ready(); h.seed.ctrl=0x81; h.seed.xv=0x1000; h.seed.yv=0; h.install();
        h.edit(s->s.moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0x0500));
        for(int i=0;i<5;i++) h.run();
        assertEquals(0,h.slot().word(TUNNEL_PATH_STEPS)); assertEquals(0x1440ABCD,h.live.state().motion().centreX16_16());
        h.run(); assertEquals(4,h.slot().unsignedByte(TUNNEL_PATH_INDEX)); assertEquals(0x1440ABCD,h.live.state().motion().centreX16_16());
        var dirty=ready(); dirty.seed.xv=0; dirty.seed.yv=0; dirty.install();
        dirty.edit(s->s.moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0x807F)); dirty.run();
        assertEquals(0x7F7F,dirty.slot().word(TUNNEL_PATH_STEPS));
    }
    @Test void integrationWrapAndOrder() {
        var h=ready(); h.seed.x=0xFFFFF000; h.seed.y=0x00000100; h.seed.xv=0x20; h.seed.yv=-2; h.install();
        h.edit(s->s.moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0x017F));
        var p=h.seed.copy(); p.x=0x00001000; p.y=0xFFFFFF00;
        h.run(); assertEquals(p.cut(),h.live); assertEquals(0x007F,h.slot().word(TUNNEL_PATH_STEPS));
        assertEquals(List.of(MOVE_X_LONG,MOVE_Y_LONG),h.fields());
    }
    @Test void completionSnapsBeforeNextReadFault() {
        var a=assets(); var h=new Harness(new CdTunnelPath.Assets(a.groundSpeeds(),GV,CdAssetBytes.copyOf(new byte[0]),PATH,MAP));
        h.edit(s->s.moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0x007F));
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.NEXT_X_WORD,e.boundary());
        assertEquals(0xFF7F,h.slot().word(TUNNEL_PATH_STEPS)); assertEquals(0x1440ABCD,h.live.state().motion().centreX16_16());
        assertEquals(0x00F0BEEF,h.live.state().motion().centreY16_16()); assertEquals(4,h.slot().unsignedByte(TUNNEL_PATH_INDEX));
    }
    @Test void nextTargetXPrefixBeforeYFault() {
        var a=assets(); var h=new Harness(new CdTunnelPath.Assets(a.groundSpeeds(),GV,CdAssetBytes.copyOf(Arrays.copyOf(hex(RAW_PATHS),14)),PATH,MAP));
        h.edit(s->s.moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0x007F));
        var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(CdTunnelPath.UnsupportedBoundary.NEXT_Y_WORD,e.boundary());
        assertEquals(0x1478,h.slot().word(TUNNEL_PATH_TARGET_X)); assertEquals(0xF0,h.slot().word(TUNNEL_PATH_TARGET_Y));
        assertTrue(h.fields().contains(COMPLETE_X_SNAP)); assertFalse(h.fields().contains(SPEED_X_X_VELOCITY));
    }
    @Test void allThreeFullPathsReachLastPairAndRelease() {
        int[][] starts={{0x13F0,0xF0},{0xF08,0x1A0},{0x1630,0x290}};
        int[] offsets={8,146,216}, lengths={34,17,17}; byte[] literal=hex(RAW_PATHS);
        assertEquals(284,literal.length);
        for(int path=0;path<3;path++) {
            var h=ready(); h.seed.x=(starts[path][0]<<16)|0xABCD; h.seed.y=(starts[path][1]<<16)|0xBEEF; h.install();
            int selectedPath=path;
            h.camera=starts[path][0]; h.edit(s->s.moveByte(ROUTINE,0).moveByte(SUBTYPE,selectedPath)
                    .moveWord(X,starts[selectedPath][0]).moveWord(Y,starts[selectedPath][1]));
            h.run(); h.run();
            for(int point=0;point<lengths[path];point++) {
                int ticks=h.slot().unsignedByte(TUNNEL_PATH_STEPS_HIGH); assertTrue(ticks<128);
                for(int tick=0;tick<ticks;tick++) h.run();
                int xFraction=h.live.state().motion().centreX16_16()&0xFFFF;
                int yFraction=h.live.state().motion().centreY16_16()&0xFFFF;
                h.run(); int at=offsets[path]+4*point;
                assertEquals((uword(literal,at)<<16)|xFraction,h.live.state().motion().centreX16_16());
                assertEquals((uword(literal,at+2)<<16)|yFraction,h.live.state().motion().centreY16_16());
                assertEquals(point+1==lengths[path]?0:6,h.slot().routine());
            }
            assertEquals(0,h.live.state().motion().playerControl()); assertEquals(2,h.slot().unsignedByte(ROUTINE_2));
            assertEquals(2,h.fm.size());
        }
    }
    @Test void releaseRoutineWordAndCurrentTail() {
        var h=completion(); h.seed.y=0xF923BEEF; h.seed.ctrl=0xC1; h.install();
        h.edit(s->s.moveByte(ROUTINE_2,0xA7).moveByte(TUNNEL_PATH_INDEX,0x84)
                .moveWord(TUNNEL_PATH_TARGET_Y,0xF923));
        var expected=h.seed.copy(); expected.x=0x1440ABCD; expected.y=0x0123BEEF; expected.ctrl=0;
        var r=h.run(); assertEquals(expected.cut(),h.live); assertEquals(0,h.slot().routine()); assertEquals(2,h.slot().unsignedByte(ROUTINE_2));
        assertEquals(1,calls(r,CdTunnelPath.NativeCall.CHECK_OBJ_DESPAWN)); assertFalse(r.deleted());
        var release=h.players.stream().filter(p->p.site()==CdTunnelPath.NativeCut.RELEASE).findFirst().orElseThrow();
        assertEquals(List.of(CdTunnelPath.TunnelPlayerField.RELEASE_Y_MASK,CdTunnelPath.ObjectField.RELEASE_ROUTINE_CLEAR,
                CdTunnelPath.TunnelPlayerField.RELEASE_CONTROL_CLEAR,CdTunnelPath.ObjectField.RELEASE_ROUTINE_WORD),storeFields(release.stores()));
    }
    @Test void despawnRangeAndSignedSprite() {
        for(int[] v:new int[][]{{0x280,4,0},{0x300,4,1},{0x300,0x84,0}}) {
            var h=ready(); h.camera=0x80; h.seed.x=0; h.install(); h.edit(s->s.moveWord(X,v[0]).moveByte(SPRITE_FLAGS,v[1]));
            var before=h.live; var r=h.run(); assertEquals(v[2]!=0,r.deleted()); assertEquals(before,h.live);
            if(r.deleted()) {
                var d=r.effects().stream().filter(CdTunnelPath.Deleted.class::isInstance).map(CdTunnelPath.Deleted.class::cast).findFirst().orElseThrow();
                assertEquals(16,d.stores().size()); assertArrayEquals(new byte[64],h.slot().copyBytes());
            }
        }
    }
    @Test void fmFirstFaultPreservesAcquire() {
        var h=ready(); h.failFm=1; assertSame(h.fault,assertThrows(Fault.class,h::run));
        assertEquals(4,h.slot().routine()); assertEquals(0x81,h.live.state().motion().playerControl());
        assertEquals(0x13F0ABCD,h.live.state().motion().centreX16_16()); assertEquals(1,h.fm.size()); assertEquals(0,h.acceptedFm);
    }
    @Test void fmSecondFaultPreservesGotSpeedRoutine() {
        var h=ready(); h.run(); h.failFm=2; assertSame(h.fault,assertThrows(Fault.class,h::run));
        assertEquals(6,h.slot().routine()); assertSpeed(h,0x1000,0,0x0500); assertEquals(1,h.acceptedFm);
        assertEquals(2,h.fm.size());
    }
    @Test void callbackPoolMutationOnReturnRejected() {
        for(String port:List.of("READ","COMMIT","FM")) {
            var h=ready(); h.attackPort=port; h.mutatePool=true;
            assertThrows(IllegalStateException.class,h::run); assertEquals(0xA6,h.pool().state(42).id());
        }
    }
    @Test void callbackPoolMutationOnThrowRejected() {
        for(String port:List.of("READ","COMMIT","FM")) {
            var h=ready(); h.attackPort=port; h.mutatePool=true; h.throwAttack=true;
            assertThrows(IllegalStateException.class,h::run); assertEquals(0xA6,h.pool().state(42).id());
        }
        var complete=ready(); complete.throwAfterCommit=true;
        assertSame(complete.fault,assertThrows(Fault.class,complete::run));
        assertEquals(4,complete.slot().routine()); assertEquals(0x81,complete.live.state().motion().playerControl());
        assertEquals(0x1000,complete.live.state().motion().groundVelocity()); assertTrue(complete.fm.isEmpty());
    }
    @Test void callbackPlayerNonwriteMutationRejected() {
        var h=ready(); h.attackPort="FM"; h.mutatePlayer=true;
        assertThrows(IllegalStateException.class,h::run); assertEquals(0xE7,h.live.state().rotationAngle());
        var thrown=ready(); thrown.attackPort="FM"; thrown.mutatePlayer=true; thrown.throwAttack=true;
        assertThrows(IllegalStateException.class,thrown::run);
    }
    @Test void stalePairBeforeCommitRejected() {
        var h=ready(); h.mutateReadAt=2; assertThrows(IllegalStateException.class,h::run);
        assertTrue(h.players.isEmpty());
    }
    @Test void noOpCommitWithRealChangeRejected() {
        var h=ready(); h.noOp=true; var before=h.live; var source=h.slot();
        assertThrows(IllegalStateException.class,h::run); assertEquals(before,h.live); assertEquals(source,h.slot());
        for(boolean sourceOnly:new boolean[]{false,true}) {
            var mixed=ready(); mixed.partialSourceOnly=sourceOnly; mixed.partialPlayerOnly=!sourceOnly;
            assertThrows(IllegalStateException.class,mixed::run);
        }
    }
    @Test void sameValueNoOpObservabilityLimit() {
        var h=ready(); var p=h.live;
        var result=new CdTunnelPath.PlayerResult(p.state(),p.environment(),List.of(new CdTunnelPath.PlayerStore(ACQUIRE_X_VELOCITY,h.seed.xv&0xFFFF,0)));
        assertThrows(IllegalArgumentException.class,()->CdTunnelPath.validatePlayerResult(p,result));
        h.seed.xv=0; h.install(); var samePair=h.live;
        var same=new CdTunnelPath.PlayerResult(samePair.state(),samePair.environment(),List.of(new CdTunnelPath.PlayerStore(ACQUIRE_X_VELOCITY,0,0)));
        CdTunnelPath.validatePlayerResult(samePair,same); // Value validity cannot prove a private invocation accepted this same-value store.
    }
    @Test void componentReentryRejected() {
        for(String port:List.of("READ","COMMIT","FM")) {
            var h=ready(); h.attackPort=port; h.reenter=true;
            assertThrows(IllegalStateException.class,h::run);
            h.attackPort=""; h.run(); // Invocation guard is retired even on host failure.
        }
    }
    @Test void publicResultValidationRejectsNonwritesAndBadOldValues() {
        var h=ready(); var p=h.live; var changed=h.seed.copy(); changed.rot=0xE7;
        var r=new CdTunnelPath.PlayerResult(changed.cut().state(),p.environment(),List.of());
        assertThrows(IllegalArgumentException.class,()->CdTunnelPath.validatePlayerResult(p,r));
        var badOld=new CdTunnelPath.PlayerResult(p.state(),p.environment(),List.of(new CdTunnelPath.PlayerStore(ACQUIRE_GVEL_BASE,1,0x1000)));
        assertThrows(IllegalArgumentException.class,()->CdTunnelPath.validatePlayerResult(p,badOld));
        var e=p.environment(); var different=new CdPlayerBody.Environment(e.movement(),new CdPlayerBody.Controls(1,2,3,4,5),e.globals(),e.dynamicSlots());
        assertThrows(IllegalArgumentException.class,()->CdTunnelPath.validatePlayerResult(p,new CdTunnelPath.PlayerResult(p.state(),different,List.of())));
    }
    @Test void immutableMetadataAndResultFinalSource() {
        var h=ready(); var r=h.run(); assertSame(h.slot(),r.state());
        assertThrows(UnsupportedOperationException.class,()->r.effects().clear());
        assertThrows(UnsupportedOperationException.class,()->h.players.getFirst().stores().clear());
        assertThrows(UnsupportedOperationException.class,()->h.players.getFirst().result().stores().clear());
        var call=h.players.getFirst(); var order=new ArrayList<>(call.stores()); java.util.Collections.reverse(order);
        assertThrows(IllegalArgumentException.class,()->new CdTunnelPath.PlayerCall(call.actualCut(),call.expectedSource(),
                call.expectedPlayer(),call.finalSource(),call.result(),call.site(),order));
        assertThrows(IllegalArgumentException.class,()->new CdTunnelPath.PlayerCall(new CdObjectPool().capture(),
                call.expectedSource(),call.expectedPlayer(),call.finalSource(),call.result(),call.site(),call.stores()));
        assertEquals(0x91,CdTunnelPath.FmSound.FM_91.nativeId()); assertFalse(r.deleted());
    }

    static Harness ready() { return new Harness(assets()); }
    static Harness completion() {
        var h=ready(); h.edit(s->s.moveByte(ROUTINE,6).moveWord(TUNNEL_PATH_STEPS,0x007F)); return h;
    }
    static Harness speed(int x,int y,int tx,int ty,int gv) {
        var h=ready(); h.seed.x=(x<<16)|0xABCD; h.seed.y=(y<<16)|0xBEEF; h.seed.gv=(short)gv; h.seed.ctrl=0x81; h.install();
        h.edit(s->s.moveByte(ROUTINE,4).moveWord(TUNNEL_PATH_TARGET_X,tx).moveWord(TUNNEL_PATH_TARGET_Y,ty)); return h;
    }
    static void assertSpeed(Harness h,int xv,int yv,int steps) {
        assertEquals(xv,h.live.state().motion().xVelocity()&0xFFFF); assertEquals(yv,h.live.state().motion().yVelocity()&0xFFFF);
        assertEquals(steps,h.slot().word(TUNNEL_PATH_STEPS));
    }
    static void initLiteral(int subtype,int header,int pointer,int tx,int ty) {
        var h=ready(); h.seed.x=0; h.install();
        h.edit(s->dirty(s).moveByte(ID,7).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype)
                .moveByte(SAVED_FLAGS_ID,0).moveWord(X,0x13F0).moveWord(Y,0xF0));
        var before=h.pool().capture(); byte[] expected=h.slot().copyBytes(); initHeader(expected);
        w(expected,0x3A,header); l(expected,0x3C,pointer); w(expected,0x36,tx); w(expected,0x38,ty);
        var player=h.live; var r=h.run(); assertArrayEquals(expected,r.state().copyBytes()); assertEquals(player,h.live);
        for(int whole=32;whole<128;whole++) if(whole!=WHOLE) assertEquals(before.slots().get(whole-32),h.pool().state(whole));
        assertTrue(h.players.isEmpty()); assertTrue(h.fm.isEmpty());
    }
    static void initFault(int length,CdTunnelPath.UnsupportedBoundary boundary,int completed) {
        var h=new Harness(assets(hex("10000c000c000800"),Arrays.copyOf(hex(RAW_PATHS),length)));
        h.edit(s->s.moveByte(ROUTINE,0).moveWord(TUNNEL_PATH_INDEX_END,0xA5B6)
                .moveLong(TUNNEL_PATH_DATA,0xABCD1234).moveWord(TUNNEL_PATH_TARGET_X,0xC7D8)
                .moveWord(TUNNEL_PATH_TARGET_Y,0xE9FA));
        byte[] expected=h.slot().copyBytes(); initHeader(expected);
        if(completed>=2) { w(expected,0x3A,0x0088); l(expected,0x3C,0x208DA0); }
        if(completed>=3) w(expected,0x36,0x1440);
        var player=h.live; var e=assertThrows(CdTunnelPath.UnsupportedTunnelPath.class,h::run);
        assertEquals(boundary,e.boundary()); assertArrayEquals(expected,h.slot().copyBytes()); assertEquals(player,h.live);
        assertTrue(h.fm.isEmpty()); assertTrue(h.players.isEmpty());
    }
    static void initHeader(byte[] expected) {
        l(expected,4,MAP); b(expected,1,4); b(expected,0x18,1); b(expected,0x19,0x10); w(expected,2,0x0541); b(expected,0x24,2);
    }
    static void acquireLiteral(int secondary,int control) {
        var h=ready(); h.edit(s->s.moveByte(SUBTYPE2,secondary)); var before=h.pool().capture();
        var expected=h.seed.copy(); expected.ctrl=control; expected.anim=2; expected.gv=0x1000;
        expected.xv=0; expected.yv=0; expected.flags=0x97; expected.jump=0;
        byte[] own=h.slot().copyBytes(); b(own,0x24,4); b(own,0x22,0x95); b(own,0x32,0);
        var r=h.run(); assertEquals(expected.cut(),h.live); assertArrayEquals(own,r.state().copyBytes());
        assertEquals(1,h.fm.size()); assertEquals(CdTunnelPath.FmSite.MAIN_ACQUIRE,h.fm.getFirst().site());
        for(int whole=32;whole<128;whole++) if(whole!=WHOLE) assertEquals(before.slots().get(whole-32),h.pool().state(whole));
        assertSame(h.players.getFirst().expectedPlayer().environment(),h.players.getFirst().result().finalEnvironment());
        assertEquals(ACQUIRE_GVEL_BASE,h.fields().get(secondary==0?2:3));
        assertEquals(List.of(CdTunnelPath.NativeCut.ACQUIRE_BASE,CdTunnelPath.NativeCut.ACQUIRE_FINISH),
                h.players.stream().map(CdTunnelPath.PlayerCall::site).toList());
    }
    static CdTunnelPath.Assets assets() { return assets(hex("10000c000c000800"),hex(RAW_PATHS)); }
    static CdTunnelPath.Assets assets(byte[] speeds,byte[] paths) {
        return new CdTunnelPath.Assets(CdAssetBytes.copyOf(speeds),GV,CdAssetBytes.copyOf(paths),PATH,MAP);
    }
    static List<Object> storeFields(List<CdTunnelPath.NativeStore> stores) {
        return stores.stream().map(s->s instanceof CdTunnelPath.PlayerStore p?(Object)p.field():((CdTunnelPath.ObjectStore)s).field()).toList();
    }
    static List<CdTunnelPath.SavedFlagChanged> bankEffects(CdTunnelPath.Result r) {
        return r.effects().stream().filter(CdTunnelPath.SavedFlagChanged.class::isInstance).map(CdTunnelPath.SavedFlagChanged.class::cast).toList();
    }
    static long calls(CdTunnelPath.Result r,CdTunnelPath.NativeCall call) {
        return r.effects().stream().filter(CdTunnelPath.CallReturned.class::isInstance).map(CdTunnelPath.CallReturned.class::cast).filter(e->e.call()==call).count();
    }
    static CdObjectState dirty(CdObjectState s) {
        for(var f:CdObjectState.ByteField.values()) s=s.moveByte(f,0xA5);
        for(var f:CdObjectState.WordField.values()) s=s.moveWord(f,0xB6C7);
        for(var f:CdObjectState.LongField.values()) s=s.moveLong(f,0xD8E9FA0B);
        return s;
    }
    static byte[] hex(String s) { return HexFormat.of().parseHex(s); }
    static int uword(byte[] a,int at) { return (Byte.toUnsignedInt(a[at])<<8)|Byte.toUnsignedInt(a[at+1]); }
    static void b(byte[] a,int at,int v) { a[at]=(byte)v; }
    static void w(byte[] a,int at,int v) { b(a,at,v>>>8); b(a,at+1,v); }
    static void l(byte[] a,int at,int v) { w(a,at,v>>>16); w(a,at+2,v); }

    /** Test ingress values only. No actor64, source capability, owner token or capture is synthesized. */
    static final class P {
        int x=0x13F0ABCD,y=0x00F0BEEF,xv=0x0123,yv=-0x0234,gv=0x0567,routine=2,angle=0xA4,flags=0xB5;
        int spr=0xD6,xr=0x19,yr=0x23,anim=0x11,prev=0x34,jump=0x56,charge=0x78,lock=-0x1234,stick=0x9A,ctrl;
        int hurt=0xA1B2,rot=0xC7,dist=0xD8;
        P copy() {
            var p=new P(); p.x=x;p.y=y;p.xv=xv;p.yv=yv;p.gv=gv;p.routine=routine;p.angle=angle;p.flags=flags;
            p.spr=spr;p.xr=xr;p.yr=yr;p.anim=anim;p.prev=prev;p.jump=jump;p.charge=charge;p.lock=lock;p.stick=stick;p.ctrl=ctrl;
            p.hurt=hurt;p.rot=rot;p.dist=dist; return p;
        }
        CdTunnelPath.PlayerCut cut() {
            var motion=new CdPlayerMotionState(x,y,xv,yv,gv,routine,angle,flags,spr,xr,yr,anim,prev,jump,charge,lock,stick,ctrl,0xDB,0xEC);
            byte[] history=new byte[256]; for(int i=0;i<history.length;i++) history[i]=(byte)(i*73+11);
            var state=new CdPlayerBody.State(motion,new CdPlayerBody.AnimationCursor(0x32,0x43,0x54),
                    new CdPlayerBody.Header(1,0xFE234567,0xABCD,0x65,0x76),new CdPlayerBody.Timers(hurt,0xB2C3,0xC3D4,0xD4E5),
                    new CdPlayerBody.History(CdAssetBytes.copyOf(history),0xE6F7),dist,new CdPlayerBody.ObjectLinks(0x87,0x98),rot);
            var movement=new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x678,0x19,0xA7),
                    new CdPlayerMovement.Bounds(0x123,0x4567,0x89AB,0xBC),
                    new CdPlayerMovement.Gates(0x1234,0xFD,0xAB,0xBC,0xCD,0xDE,0xEF),
                    new CdPlayerMovement.Globals(0x12,0x3456,0x78,0x89,0x9ABC,0xABCD,0xBC,0xCD,0xDE,0xEF));
            return new CdTunnelPath.PlayerCut(state,new CdPlayerBody.Environment(movement,
                    new CdPlayerBody.Controls(0xA1,0xB2,0xC3,0xD4,0xE5),
                    new CdPlayerBody.Globals(0x1234,0x5678,0x91,0xA2B3,0xB3C4,0xC4D5,0xD6,0xE7),
                    CdPlayerBody.DynamicSlots.UNIMPLEMENTED));
        }
    }
    static final class Fault extends RuntimeException { private static final long serialVersionUID=1L; }
    /** Value-only component port; the private production Runtime/Frame authentication is not mimicked. */
    static final class Harness implements CdTunnelPath.PlayerOwner {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(
                hex("000c0014ffff000000000000ffff000000000000ffff00000000")));
        final P seed=new P(); CdTunnelPath.PlayerCut live=seed.cut();
        final List<CdTunnelPath.PlayerCall> players=new ArrayList<>();
        final List<CdTunnelPath.FmCall> fm=new ArrayList<>();
        final Fault fault=new Fault(); final CdTunnelPath body;
        int time=1,direction,camera=0x13F0,reads,failFm,acceptedFm,mutateReadAt;
        String attackPort=""; boolean mutatePool,mutatePlayer,throwAttack,reenter,noOp,partialSourceOnly,partialPlayerOnly,throwAfterCommit;
        Harness(CdTunnelPath.Assets assets) {
            placement.resetSavedObjectFlags(); body=new CdTunnelPath(placement,assets,this,this::fm);
            edit(s->s.moveByte(ID,7).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0xB5)
                    .moveLong(X_POSITION,0x13F0FA01).moveLong(Y_POSITION,0x00F0BC02)
                    .moveByte(TUNNEL_PATH_SCRATCH,0xA5).moveWord(TUNNEL_PATH_INDEX_END,0x0088)
                    .moveLong(TUNNEL_PATH_DATA,0x208DA0).moveWord(TUNNEL_PATH_TARGET_X,0x1440)
                    .moveWord(TUNNEL_PATH_TARGET_Y,0xF0).moveWord(TUNNEL_PATH_STEPS,0x0500));
            pool().write(dirty(pool().state(42)).moveByte(ID,0));
        }
        CdObjectPool pool() { return placement.pool(); }
        CdObjectState slot() { return pool().state(WHOLE); }
        void edit(UnaryOperator<CdObjectState> f) { pool().write(f.apply(slot())); }
        void install() { live=seed.cut(); }
        CdTunnelPath.Result run() { return body.run(WHOLE,new CdTunnelPath.Globals(time,direction,camera)); }
        @Override public CdTunnelPath.PlayerCut read() {
            reads++; attack("READ");
            if(reads==mutateReadAt) changeRotation();
            return live;
        }
        @Override public void commit(CdTunnelPath.PlayerCall call) {
            check(call.actualCut(),call.expectedSource(),call.expectedPlayer()); players.add(call); attack("COMMIT");
            CdTunnelPath.validatePlayerResult(live,call.result());
            var readyPair=new CdTunnelPath.PlayerCut(call.result().finalState(),call.result().finalEnvironment());
            // Preserve an adversarial COMMIT mutation so the body can observe it after return.
            var readyPool=CdObjectPool.overlay(pool().capture(),List.of(call.finalSource()));
            if(noOp) return;
            if(partialSourceOnly) { pool().restore(readyPool); return; }
            if(partialPlayerOnly) { live=readyPair; return; }
            // All harness values and validations precede these two owner assignments.
            pool().restore(readyPool); live=readyPair;
            if(throwAfterCommit) throw fault;
        }
        void fm(CdTunnelPath.FmCall call) {
            check(call.actualCut(),call.expectedSource(),call.expectedPlayer()); fm.add(call); attack("FM");
            if(fm.size()==failFm) throw fault; acceptedFm++;
        }
        void check(CdObjectPool.Snapshot cut,CdObjectState source,CdTunnelPath.PlayerCut expected) {
            pool().validateRestore(cut); assertEquals(cut.slots(),pool().capture().slots());
            assertEquals(slot(),source); assertEquals(live,expected);
        }
        void attack(String port) {
            if(!port.equals(attackPort)) return;
            if(reenter) run();
            if(mutatePool) pool().write(pool().state(42).moveByte(ID,0xA6));
            if(mutatePlayer) changeRotation();
            if(throwAttack) throw fault;
        }
        void changeRotation() {
            var s=live.state();
            live=new CdTunnelPath.PlayerCut(new CdPlayerBody.State(s.motion(),s.animation(),s.header(),s.timers(),s.history(),
                    s.rotationDistance(),s.objectLinks(),0xE7),live.environment());
        }
        List<CdTunnelPath.TunnelPlayerField> fields() {
            return players.stream().flatMap(p->p.result().stores().stream()).map(CdTunnelPath.PlayerStore::field).toList();
        }
    }
}
