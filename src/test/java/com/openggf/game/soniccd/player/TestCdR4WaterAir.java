package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR4WaterEvents;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.Value.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal N-source vectors at 1ecc5f3. Constructed known RAM/port controls; no World/route/pixel claim.
 * Allocation controls use the REAL CdObjectPool full96 search and dirty immutable images.
 * Every absent value/RNG/callback fails explicitly. Zero initialization below is test input only.
 */
class TestCdR4WaterAir {
    @Test void underwaterRulesAreFiniteOperandsForSharedBody() {
        var wet = CdR4PlayerWater.rules(0x40);
        assertEquals(0x380, wet.jumpImpulse()); assertEquals(-0x200, wet.releaseCap());
        assertEquals(0x28, wet.afterFallSubtract()); assertEquals(-0x100, wet.hurtX());
        assertEquals(-0x200, wet.hurtY()); assertEquals(0x30, wet.hurtGravity());
        assertEquals(0x20, wet.hurtAfterGravitySubtract());
        assertEquals(0x680, CdR4PlayerWater.rules(0).jumpImpulse());
        assertEquals(-0x400, CdR4PlayerWater.rules(0).releaseCap());
    }
    @Test void swayUsesLogicalWordShiftThenByteSignExtension() {
        assertEquals(-4, CdR4WaterEvents.swayOffset(0xFF00));
        assertEquals(4, CdR4WaterEvents.swayOffset(0x0100));
        assertEquals(-1, CdR4WaterEvents.swayOffset(0xFFFF));
    }
    @Test void pausedUpdateDoesNotReadAnyOtherValue() {
        H h = new H(); h.g.clear(); h.g.put(PAUSED, 1);
        assertTrue(CdR4WaterEvents.update(h).isEmpty()); assertTrue(h.log.isEmpty());
    }
    @Test void deadPlayerSkipsEventsButAdvancesSwayByteAndProjectsFullWater() {
        H h = new H(); h.p.b(B.ROUTINE, 6); h.g.put(WATER_ANGLE, 255);
        h.g.put(STATIC_WATER_Y, 0x200); h.g.put(CAMERA_Y, 0x280); h.sine = 0xFF00;
        h.g.remove(ACT);
        assertEquals(new CdR4WaterEvents.Projection(0x1FC, 0xDF, true), CdR4WaterEvents.update(h).orElseThrow());
        assertEquals(1, h.g.get(WATER_ANGLE));
        assertEquals(List.of("WATER_FULL=0", "WATER_ANGLE=1", "sine:1", "WATER_Y=508", "SPLIT=223", "WATER_FULL=1", "SPLIT=223"), h.log);
    }
    @Test void noBorrowNegativeSplitIsClampedButNotFullWater() {
        H h = new H(); h.p.b(B.ROUTINE, 6); h.g.put(STATIC_WATER_Y, 0xF000);
        assertEquals(new CdR4WaterEvents.Projection(0xF000, 223, false), CdR4WaterEvents.update(h).orElseThrow());
    }
    @Test void projectionKeepsZeroAndVisibleSplit() {
        H h = new H(); h.p.b(B.ROUTINE, 6); h.g.put(STATIC_WATER_Y, 100); h.g.put(CAMERA_Y, 99);
        assertEquals(new CdR4WaterEvents.Projection(100, 1, false), CdR4WaterEvents.update(h).orElseThrow());
    }
    @Test void swayFaultRetainsAngleAndFullFlagPrefix() {
        H h = new H(); h.p.b(B.ROUTINE, 6); h.sine = null;
        assertThrows(IllegalStateException.class, () -> CdR4WaterEvents.update(h));
        assertEquals(List.of("WATER_FULL=0", "WATER_ANGLE=2", "sine:2"), h.log);
    }
    @Test void act1PastDoesNotOverwriteTargetAndSpeedIsUnsignedByte() {
        H h = new H(); h.g.put(TARGET_WATER_Y, 0x600); h.g.put(STATIC_WATER_Y, 0x500); h.g.put(WATER_SPEED, 255);
        CdR4WaterEvents.events(h);
        assertEquals(List.of("STATIC_WATER_Y=1535"), h.log);
    }
    @Test void targetMovementOvershootsAndWrapsWithoutClamp() {
        H h = new H(); h.g.put(TARGET_WATER_Y, 1); h.g.put(STATIC_WATER_Y, 2); h.g.put(WATER_SPEED, 255);
        CdR4WaterEvents.events(h); assertEquals(0xFF03, h.g.get(STATIC_WATER_Y));
    }
    @Test void act1ThresholdsAndMarkedEraAreLiteral() {
        int[][] vectors = {{1,0x5DF,0x280,1},{1,0x5E0,0x260,1},{0x82,0x91F,0x1D0,1},
                {2,0x920,0x110,1},{2,0x1980,0x240,3}};
        for (int[] v : vectors) {
            H h = new H(); h.g.put(TIME_ZONE,v[0]); h.p.w(W.X,v[1]); h.g.put(WATER_SPEED,1);
            CdR4WaterEvents.events(h); assertEquals(v[2],h.g.get(TARGET_WATER_Y)); assertEquals(v[3],h.g.get(WATER_SPEED));
        }
    }
    @Test void act2PresentOldTargetNonwriteStillMovesTowardOldTarget() {
        H h = new H(); h.g.put(ACT,1); h.g.put(TIME_ZONE,1); h.p.w(W.X,0x1200); h.p.w(W.Y,0x400);
        h.g.put(TARGET_WATER_Y,0x580); h.g.put(STATIC_WATER_Y,0x500); h.g.put(WATER_SPEED,3);
        CdR4WaterEvents.events(h); assertEquals(List.of("STATIC_WATER_Y=1283"),h.log);
    }
    @Test void act2EarlyGateUsesCameraAndAvoidsUnreachedUnknownPlayerY() {
        H h = new H(); h.g.put(ACT,1); h.p.w(W.X,0x1FF); h.p.unknownW.add(W.Y); h.g.put(CAMERA_Y,0xFF80);
        CdR4WaterEvents.events(h); assertEquals(0x68,h.g.get(STATIC_WATER_Y)); assertEquals(0x68,h.g.get(TARGET_WATER_Y));
    }
    @Test void act2AndAct3TargetThresholds() {
        int[][] vectors={{1,1,0xBFF,0x530},{1,1,0xC00,0x780},{1,1,0x1380,0x500},
                {1,1,0x1100,0x3C0},{1,2,0x400,0x390},{1,2,0x1600,0x410},{1,2,0x1900,0x500},
                {2,2,0x7C0,0x490},{2,2,0xAF0,0x5C0}};
        for(int[] v:vectors){H h=new H();h.g.put(ACT,v[0]);h.g.put(TIME_ZONE,v[1]);h.p.w(W.X,v[2]);
            CdR4WaterEvents.events(h);assertEquals(v[3],h.g.get(TARGET_WATER_Y));}
    }
    @Test void waterEntryHalvesNegativeXOnceAndYTwiceAndAllocatesDirtySplash() {
        H h=entry();h.p.w(W.VX,0xFFFD);h.p.w(W.VY,0xFFF9);
        h.pool.write(h.pool.state(32).moveByte(CdObjectState.ByteField.SUBTYPE,0xA7).moveWord(CdObjectState.WordField.Y,0x456));
        CdR4PlayerWater.check(h);
        assertEquals(0xFFFE,h.p.read(W.VX));assertEquals(0xFFFE,h.p.read(W.VY));
        assertEquals(0x21,h.r.read(B.ID));assertEquals(0x81,h.r.read(B.SUBTYPE));assertEquals(0x300,h.g.get(MAX_SPEED));
        assertEquals(0xB,h.pool.state(32).id());assertEquals(0xA7,h.pool.state(32).subtype());assertEquals(0x456,h.pool.state(32).centreY());
    }
    @Test void full96WaterEntryKeepsAllEarlierWritesAndNoChild() {
        H h=entry();h.p.w(W.VY,0x100);h.fillPool();var before=h.pool.capture();
        CdR4PlayerWater.check(h);assertEquals(before.slots(),h.pool.capture().slots());
        assertEquals(0x40,h.p.read(B.FLAGS));assertEquals(0x40,h.p.read(W.VY));assertEquals(0x1E,h.g.get(DROWN_TIMER));
    }
    @Test void alreadyUnderwaterStillExecutesSameValueBsetAndNoReset() {
        H h=entry();h.p.b(B.FLAGS,0x40);h.g.remove(DROWN_TIMER);CdR4PlayerWater.check(h);
        assertEquals(List.of("p.FLAGS=64"),h.log);
    }
    @Test void act2DryExceptionLeavesWaterAndFlagsUnread() {
        H h=entry();h.g.put(ACT,1);h.p.w(W.X,0xC7);h.g.remove(WATER_Y);h.p.unknownB.add(B.FLAGS);
        CdR4PlayerWater.check(h);assertTrue(h.log.isEmpty());
    }
    @Test void positiveYVelocityPreventsSurfaceExitAndBclr() {
        H h=entry();h.p.w(W.Y,50);h.p.w(W.VY,1);h.p.unknownB.add(B.FLAGS);
        CdR4PlayerWater.check(h);assertTrue(h.log.isEmpty());
    }
    @Test void sourceExitDoubleWrapThenSignedCapIsRetained() {
        H h=entry();h.p.w(W.Y,50);h.p.b(B.FLAGS,0x40);h.p.w(W.VY,0xF700);
        CdR4PlayerWater.check(h);assertEquals(0xF000,h.p.read(W.VY));assertEquals(0,h.p.read(B.FLAGS));
        assertEquals(0x600,h.g.get(MAX_SPEED));assertEquals(0xB,h.pool.state(32).id());
    }
    @Test void entryMusicFaultRetainsFlagButNotSpeedOrReservedSeed() {
        H h=entry();h.g.put(DROWN_TIMER,12);h.fail="music:130";
        assertSame(h.failure,assertThrows(IllegalStateException.class,()->CdR4PlayerWater.check(h)));
        assertEquals(0x40,h.p.read(B.FLAGS));assertEquals(0,h.r.read(B.ID));assertEquals(0,h.g.get(MAX_SPEED));
    }
    @Test void airResetUsesMusicPriorityAndClearsOnlyReservedByte() {
        H h=new H();h.g.put(DROWN_TIMER,12);h.g.put(ZONE_WORD,0x103);h.g.put(INVINCIBLE,1);h.g.put(BOSS_STARTED,1);
        h.r.w(W.DEATH_DELAY,120);h.r.b(B.COUNTDOWN,9);CdR4PlayerBubbles.resetAir(h);
        assertEquals(List.of("music:140","DROWN_TIMER=30","r.COUNTDOWN=0"),h.log);
        assertEquals(120,h.r.read(W.DEATH_DELAY));
    }
    @Test void airResetAbove12DoesNotReadMusicGates() {
        H h=new H();h.g.put(DROWN_TIMER,13);h.g.remove(ZONE_WORD);CdR4PlayerBubbles.resetAir(h);
        assertEquals(30,h.g.get(DROWN_TIMER));
    }
    @Test void currentFirstMatchUsesLowerInclusiveUpperExclusiveAndNoMode0SwitchRead() {
        H h=current();h.p.w(W.X,0x100);h.p.w(W.Y,0x200);
        CdR4WaterCurrents.check(h,rects(0),i->{throw new AssertionError("mode0 switch read");});
        assertEquals(0x100,h.p.read(W.VX));assertEquals(0xFF00,h.p.read(W.VY));assertEquals(0xF,h.p.read(B.ANIM));
        h=current();h.p.w(W.X,0x110);h.p.w(W.Y,0x200);h.g.put(WATER_CURRENT,1);
        CdR4WaterCurrents.check(h,rects(0),i->0);assertEquals(0,h.g.get(WATER_CURRENT));assertEquals(0,h.p.read(B.ANIM));
    }
    @Test void mode1InactiveSwitchReturnsWithoutClearingExistingCurrent() {
        H h=current();h.g.put(WATER_CURRENT,1);h.p.b(B.ANIM,7);
        CdR4WaterCurrents.check(h,rects(1),i->{assertEquals(9,i);return 0;});assertTrue(h.log.isEmpty());
        assertEquals(1,h.g.get(WATER_CURRENT));assertEquals(7,h.p.read(B.ANIM));
    }
    @Test void mode2SwitchFaultRetainsVelocityAnimationAndAirbornePrefix() {
        H h=current();
        assertSame(h.failure,assertThrows(IllegalStateException.class,()->CdR4WaterCurrents.check(h,rects(2),i->{throw h.failure;})));
        assertEquals(1,h.g.get(WATER_CURRENT));assertEquals(0x100,h.p.read(W.VX));assertEquals(2,h.p.read(B.FLAGS));
        assertEquals(0xF,h.p.read(B.ANIM));
    }
    @Test void mode2ReversesAndLogicalDirectionNudgesAreOrderedWordStores() {
        H h=current();h.g.put(HELD,15);CdR4WaterCurrents.check(h,rects(2),i->0x20);
        assertEquals(0xFF00,h.p.read(W.VX));assertEquals(0x100,h.p.read(W.VY));assertEquals(0x100,h.p.read(W.X));
        assertEquals(List.of("p.Y=511","p.Y=512","p.X=255","p.X=256"),h.log.subList(h.log.size()-4,h.log.size()));
    }
    @Test void hurtCurrentClearsFlagWithoutAnimationOrVelocityWrite() {
        H h=current();h.p.b(B.ROUTINE,4);CdR4WaterCurrents.check(h,rects(0),i->0);
        assertEquals(List.of("WATER_CURRENT=0"),h.log);
    }
    @Test void currentUnreachedClockAndSwitchAreNotRead() {
        H h=current();h.p.b(B.ANIM,0x2B);h.g.remove(VBLANK_LOW);
        CdR4WaterCurrents.check(h,null,null);assertTrue(h.log.isEmpty());
    }
    @Test void zeroCurrentMissHasNoSameValueFlagWrite() {
        H h=current();h.p.w(W.X,0x110);CdR4WaterCurrents.check(h,rects(0),i->0);assertTrue(h.log.isEmpty());
    }
    @Test void shortCurrentRecordFaultPrecedesAnyCurrentWrites() {
        H h=current();byte[] b=Arrays.copyOf(rects(0).copy(),10); // Missing mode/index faults before velocity writes.
        byte[] shortView=b;
        assertThrows(IndexOutOfBoundsException.class,()->CdR4WaterCurrents.check(h,CdAssetBytes.copyOf(shortView),i->0));
        assertTrue(h.log.isEmpty());
    }
    @Test void countdownKeeps60StepWordTimerAndFullPoolEmissionPrefix() {
        H h=air();h.g.put(DROWN_TIMER,25);h.r.w(W.TIMER,0);h.randoms.addAll(List.of(1,10));h.fillPool();
        CdR4PlayerBubbles.run(h.r,h,null);
        assertEquals(59,h.r.read(W.TIMER));assertEquals(24,h.g.get(DROWN_TIMER));assertEquals(10,h.r.read(W.EMIT_TIMER));
        assertEquals(1,h.r.read(B.REMAINING));assertFalse(h.log.stream().anyMatch(x->x.startsWith("sound:")));
    }
    @Test void countdownWarningNumberAndDirtyChildAreLiteral() {
        H h=air();h.g.put(DROWN_TIMER,12);h.r.w(W.TIMER,0);h.r.b(B.RELOAD,1);
        h.randoms.addAll(List.of(0,3,0));CdR4PlayerBubbles.run(h.r,h,null);
        assertEquals(11,h.g.get(DROWN_TIMER));assertTrue(h.log.contains("sound:223"));
        assertEquals(0x21,h.pool.state(32).id());assertEquals(5,h.pool.state(32).subtype());
        assertEquals(0x2000,h.pool.state(32).word(CdObjectState.WordField.TILE));
        assertEquals(0x1C,wordAt(h.pool.state(32),0x38));
    }
    @Test void drowningStartsItsOwn120DelayNotKillPlayerAndNoMusicOnWordUnderflow() {
        H h=air();h.g.put(DROWN_TIMER,0);h.r.w(W.TIMER,0);h.randoms.add(0);
        CdR4PlayerBubbles.run(h.r,h,null);
        assertEquals(120,h.r.read(W.DEATH_DELAY));assertEquals(0x17,h.p.read(B.ANIM));assertEquals(0x81,h.p.read(B.CONTROL));
        assertEquals(0,h.p.read(B.ROUTINE));assertEquals(1,h.g.get(SCROLL_LOCK));assertEquals(30,h.g.get(DROWN_TIMER));
        assertTrue(h.log.contains("ground"));assertTrue(h.log.contains("sound:182"));
        assertFalse(h.log.stream().anyMatch(x->x.startsWith("music:")));
    }
    @Test void drowningSoundFailureRetainsResetAndControlBeforeDeathSetup() {
        H h=air();h.g.put(DROWN_TIMER,0);h.r.w(W.TIMER,0);h.randoms.add(0);h.fail="sound:182";
        assertSame(h.failure,assertThrows(IllegalStateException.class,()->CdR4PlayerBubbles.run(h.r,h,null)));
        assertEquals(30,h.g.get(DROWN_TIMER));assertEquals(0x81,h.p.read(B.CONTROL));assertEquals(0,h.r.read(W.DEATH_DELAY));
    }
    @Test void deathDelayOneOnlyChangesPlayerRoutineAndReturns() {
        H h=air();h.r.w(W.DEATH_DELAY,1);CdR4PlayerBubbles.run(h.r,h,null);
        assertEquals(List.of("r.DEATH_DELAY=0","p.ROUTINE=6"),h.log);
    }
    @Test void deathDelayMovesBeforeGravityAndBeforeEmissionGate() {
        H h=air();h.r.w(W.DEATH_DELAY,2);h.p.w(W.VY,0xFFF0);CdR4PlayerBubbles.run(h.r,h,null);
        assertEquals(List.of("r.DEATH_DELAY=1","move:p","p.VY=0"),h.log);
    }
    @Test void reservedInitOrsDirtyTileAndKeepsDirtyTimer() {
        H h=air();h.r.b(B.ROUTINE,0);h.r.b(B.SUBTYPE,0x81);h.r.w(W.TILE,0x2000);h.r.w(W.TIMER,5);
        CdR4PlayerBubbles.run(h.r,h,new BubbleData(0xABCDEF00,null,null,0));
        assertEquals(10,h.r.read(B.ROUTINE));assertEquals(0xA48A,h.r.read(W.TILE));assertEquals(4,h.r.read(W.TIMER));
    }
    @Test void childWobbleFaultRetainsAngleAndCurrentAnchor() {
        H h=new H();S o=new S("o",h.log);o.b(B.ROUTINE,4);o.w(W.Y,0x200);o.w(W.ANCHOR_X,10);
        h.g.put(WATER_Y,0x100);h.g.put(WATER_CURRENT,1);
        assertThrows(IndexOutOfBoundsException.class,()->CdR4PlayerBubbles.run(o,h,new BubbleData(0,CdAssetBytes.copyOf(new byte[0]),null,0)));
        assertEquals(14,o.read(W.ANCHOR_X));assertEquals(1,o.read(B.ANGLE));
    }
    @Test void numberBecomesScreenCoordinatesUsingXPlus2AndStillCallsMove() {
        H h=new H();S o=new S("o",h.log);o.b(B.ROUTINE,4);o.b(B.SPRITE_FLAGS,0x80);
        o.w(W.Y,0x200);o.w(W.ANCHOR_X,0x100);o.w(W.TIMER,1);h.g.put(WATER_Y,0x100);
        CdR4PlayerBubbles.run(o,h,data());assertEquals(0x280,o.read(W.SCREEN_Y));assertEquals(0x200,o.read(W.Y));
        assertEquals(12,o.read(B.ROUTINE));assertTrue(h.log.contains("move:o"));
    }
    @Test void sourceBubbleContactIsStrictLowerInclusiveUpperAndUsaDrownGate() {
        H h=new H();S o=new S("o",h.log);o.w(W.X,100);o.w(W.Y,200);h.p.w(W.X,84);h.p.w(W.Y,201);
        assertFalse(CdR4BubbleSpawner.contact(o,h,CdR4BubbleSpawner.ContactProfile.USA));
        h.p.w(W.X,116);h.p.w(W.Y,216);assertTrue(CdR4BubbleSpawner.contact(o,h,CdR4BubbleSpawner.ContactProfile.USA));
        h.p.b(B.ANIM,0x17);assertFalse(CdR4BubbleSpawner.contact(o,h,CdR4BubbleSpawner.ContactProfile.USA));
        assertTrue(CdR4BubbleSpawner.contact(o,h,CdR4BubbleSpawner.ContactProfile.JP_EU));
    }
    @Test void eligibleLargeBubbleResetsAirAndRollingDimensionsInSourceOrder() {
        H h=new H();S o=pickup(h);h.p.b(B.FLAGS,0x34);CdR4BubbleSpawner.run(o,h,data(),CdR4BubbleSpawner.ContactProfile.USA);
        assertEquals(30,h.g.get(DROWN_TIMER));assertEquals(0x15,h.p.read(B.ANIM));assertEquals(35,h.p.read(W.AIR_TIMER));
        assertEquals(0,h.p.read(B.FLAGS));assertEquals(0x13,h.p.read(B.HEIGHT));assertEquals(9,h.p.read(B.WIDTH));
        assertEquals(196,h.p.read(W.Y));assertEquals(6,o.read(B.ROUTINE));
    }
    @Test void smallBubbleDoesNotReadPlayerOrResetAir() {
        H h=new H();S o=pickup(h);o.b(B.ELIGIBLE,0);h.p.unknownB.add(B.ANIM);h.g.remove(DROWN_TIMER);
        CdR4BubbleSpawner.run(o,h,data(),CdR4BubbleSpawner.ContactProfile.USA);
        assertFalse(h.log.contains("sound:173"));assertTrue(h.log.contains("move:o"));
    }
    @Test void airPickupSoundFaultKeepsAirResetWithoutLaterVelocityClear() {
        H h=new H();S o=pickup(h);h.p.w(W.VX,123);h.fail="sound:173";
        assertSame(h.failure,assertThrows(IllegalStateException.class,()->CdR4BubbleSpawner.run(o,h,data(),CdR4BubbleSpawner.ContactProfile.USA)));
        assertEquals(30,h.g.get(DROWN_TIMER));assertEquals(123,h.p.read(W.VX));assertEquals(4,o.read(B.ROUTINE));
    }
    @Test void spawnerRejectionRngThenFullPoolStillConsumesBurstAndCooldown() {
        H h=new H();S o=spawner(h);h.fillPool();h.randoms.addAll(List.of(7,6,0,5,1));
        CdR4BubbleSpawner.run(o,h,data(),CdR4BubbleSpawner.ContactProfile.USA);
        assertEquals(0,h.randoms.size());assertEquals(0,o.read(W.BURST));assertEquals(134,o.read(W.TIMER));
        assertEquals(255,o.read(B.REMAINING));
    }
    @Test void spawnerPatternFaultKeepsAdmittedChildPrefixAndRawPointer() {
        H h=new H();S o=spawner(h);o.w(W.BURST,1);o.l(L.PATTERN,0xDEADBEEF);h.randoms.addAll(List.of(0,8));
        assertThrows(UnsupportedOperationException.class,()->CdR4BubbleSpawner.run(o,h,data(),CdR4BubbleSpawner.ContactProfile.USA));
        assertEquals(0x20,h.pool.state(32).id());assertEquals(0x200,h.pool.state(32).centreY());
        assertEquals(0xDEADBEEF,o.read(L.PATTERN));assertEquals(0,o.read(B.REMAINING));
    }
    @Test void waterSurfaceAlternatesSignedOffsetsAndRetainsDirtyHighSpriteBits() {
        H h=new H();S o=new S("o",h.log);o.b(B.SPRITE_FLAGS,0xA1);h.g.put(CAMERA_X,0x100);h.g.put(WATER_Y,0x200);
        var offsets=CdAssetBytes.copyOf(new byte[]{(byte)0xA0,0x60});
        CdR4WaterSurface.run(o,h,0x12345678,offsets);assertEquals(0x200,o.read(W.X));assertEquals(0x202,o.read(W.Y));
        assertEquals(0xA5,o.read(B.SPRITE_FLAGS));assertEquals(0x12345678,o.read(L.MAPPINGS));
        CdR4WaterSurface.run(o,h,0x12345678,offsets);assertEquals(0x140,o.read(W.X));
    }
    @Test void surfaceOffsetFaultKeepsInitializationAndPhaseButNoCoordinates() {
        H h=new H();S o=new S("o",h.log);
        assertThrows(IndexOutOfBoundsException.class,()->CdR4WaterSurface.run(o,h,123,CdAssetBytes.copyOf(new byte[0])));
        assertEquals(2,o.read(B.ROUTINE));assertEquals(1,o.read(B.PHASE));assertEquals(0,o.read(W.Y));
    }
    @Test void splashSoundVariantFaultRetainsInitAndNoWaterYWrite() {
        H h=new H();S o=new S("o",h.log);o.b(B.SUBTYPE,2);h.fail="sound:161";
        assertSame(h.failure,assertThrows(IllegalStateException.class,()->CdR4Splash.run(o,h,123)));
        assertEquals(2,o.read(B.ROUTINE));assertEquals(0x298,o.read(W.TILE));assertEquals(0,o.read(W.Y));
    }
    @Test void splashDeleteDoesNotReadWaterOrPlaySoundAgain() {
        H h=new H();S o=new S("o",h.log);o.b(B.ROUTINE,4);h.g.clear();CdR4Splash.run(o,h,0);
        assertEquals(List.of("delete:o"),h.log);
    }
    @Test void unknownReachedClockStopsBeforeSwitchOrWrites() {
        H h=current();h.g.remove(VBLANK_LOW);
        assertThrows(IllegalStateException.class,()->CdR4WaterCurrents.check(h,rects(1),i->{throw new AssertionError();}));
        assertTrue(h.log.isEmpty());
    }
    @Test void dirtySpawnerPointerReadsExactBorrowedSourceBeforeNormalPatternTable() {
        H h=new H();S o=spawner(h);o.w(W.BURST,1);o.l(L.PATTERN,0x200FF0);
        h.randoms.addAll(List.of(0,8,0));
        // Constructed source window: first byte precedes the normal pattern table by16 bytes.
        byte[] known=new byte[34];known[0]=(byte)0xE7;var bytes=CdAssetBytes.copyOf(known);
        List<String> reads=new ArrayList<>();
        var data=new BubbleData(0,CdAssetBytes.copyOf(new byte[128]),(pointer,index)->{
            reads.add(Integer.toHexString(pointer)+":"+index);
            return bytes.unsignedByte(Math.toIntExact(Integer.toUnsignedLong(pointer)+index-0x200FF0L));
        },0x201000);
        CdR4BubbleSpawner.run(o,h,data,CdR4BubbleSpawner.ContactProfile.USA);
        assertEquals(List.of("200ff0:0"),reads);assertEquals(0xE7,h.pool.state(32).subtype());
        assertEquals(0x200FF0,o.read(L.PATTERN));
    }
    @Test void overlappingCurrentRectanglesStopAtFirstMatch() {
        H h=current();byte[] a=rects(0).copy(),both=new byte[29];
        System.arraycopy(a,0,both,0,14);System.arraycopy(a,0,both,14,14);
        both[22]=3;both[23]=0;both[28]=(byte)0xFF;
        CdR4WaterCurrents.check(h,CdAssetBytes.copyOf(both),i->{throw new AssertionError("no switch read");});
        assertEquals(0x100,h.p.read(W.VX)); // second matching record would publish$0300
    }
    @Test void warningReadsOnlyLowClockByteWhenHighByteIsUnknown() {
        for(int low:new int[]{0,0x20}) {
            H h=air();h.g.put(DROWN_TIMER,12);h.r.w(W.TIMER,0);h.g.remove(STAGE_FRAMES);
            h.g.put(STAGE_FRAMES_LOW,low);h.randoms.addAll(List.of(0,3,0));
            CdR4PlayerBubbles.run(h.r,h,null);
            assertEquals(low==0,h.log.contains("sound:223"));assertEquals(11,h.g.get(DROWN_TIMER));
        }
    }
    @Test void drowningEmissionRequiresFullClockWordEvenWhenLowByteIsKnown() {
        H h=air();h.r.w(W.DEATH_DELAY,2);h.r.w(W.BURST,1);h.g.remove(STAGE_FRAMES);
        h.g.put(STAGE_FRAMES_LOW,0);h.randoms.addAll(List.of(0,0x34));
        assertThrows(IllegalStateException.class,()->CdR4PlayerBubbles.run(h.r,h,null));
        assertEquals(0x21,h.pool.state(32).id());assertEquals(0x34,h.pool.state(32).unsignedByte(CdObjectState.ByteField.ANGLE));
        assertEquals(6,h.pool.state(32).subtype());assertEquals(0xFFF4,h.pool.state(32).centreY());
        assertEquals(List.of("r.EMIT_TIMER=65535","r.EMIT_TIMER=0","r.EMIT_TIMER=0","r.EMIT_TIMER=0"),
                h.log.stream().filter(x->x.startsWith("r.EMIT_TIMER=")).toList());
    }
    private static H entry(){H h=new H();h.g.put(ZONE,2);h.g.put(WATER_Y,100);h.g.put(DROWN_TIMER,30);h.p.w(W.Y,200);return h;}
    private static H current(){H h=new H();h.p.b(B.ID,1);h.p.w(W.X,0x100);h.p.w(W.Y,0x200);return h;}
    private static H air(){H h=new H();h.r.b(B.ROUTINE,10);h.p.b(B.FLAGS,0x40);return h;}
    private static S pickup(H h){S o=new S("o",h.log);o.b(B.ROUTINE,4);o.b(B.ELIGIBLE,1);o.b(B.SPRITE_FLAGS,0x80);
        o.w(W.ANCHOR_X,100);o.w(W.Y,200);h.g.put(WATER_Y,100);h.g.put(DROWN_TIMER,20);h.p.w(W.X,100);h.p.w(W.Y,201);return o;}
    private static S spawner(H h){S o=new S("o",h.log);o.b(B.ROUTINE,10);o.b(B.SPRITE_FLAGS,0x80);o.w(W.Y,0x200);h.g.put(WATER_Y,0x100);return o;}
    private static BubbleData data(){return new BubbleData(0xABCDEF00,CdAssetBytes.copyOf(new byte[128]),(pointer,index)->{
        long offset=Integer.toUnsignedLong(pointer)+index-0x201000L;
        if(offset<0||offset>=18)throw new UnsupportedOperationException("unowned constructed source byte");
        return 0;
    },0x201000);}
    private static CdAssetBytes rects(int mode){return CdAssetBytes.copyOf(new byte[]{1,0,2,0,1,16,2,16,1,0,(byte)255,0,(byte)mode,9,(byte)255});}
    private static int wordAt(CdObjectState s,int n){byte[] b=s.copyBytes();return (b[n]&255)*256+(b[n+1]&255);}
    private static class S implements Slot {
        final String name; final List<String> log; final byte[] bytes=new byte[64];
        final Set<B> unknownB=EnumSet.noneOf(B.class);final Set<W> unknownW=EnumSet.noneOf(W.class);
        S(String n,List<String> l){name=n;log=l;}
        void b(B f,int v){bytes[f.offset]=(byte)v;}void w(W f,int v){bytes[f.offset]=(byte)(v>>>8);bytes[f.offset+1]=(byte)v;}
        void l(L f,int v){for(int i=0;i<4;i++)bytes[f.offset+i]=(byte)(v>>>(24-i*8));}
        public int read(B f){if(unknownB.contains(f))throw new IllegalStateException("unknown "+f);return bytes[f.offset]&255;}
        public int read(W f){if(unknownW.contains(f))throw new IllegalStateException("unknown "+f);return (bytes[f.offset]&255)*256+(bytes[f.offset+1]&255);}
        public int read(L f){int v=0;for(int i=0;i<4;i++)v=v<<8|(bytes[f.offset+i]&255);return v;}
        public void write(B f,int v){b(f,v);log.add(name+"."+f+"="+(v&255));}
        public void write(W f,int v){w(f,v);log.add(name+"."+f+"="+(v&65535));}
        public void write(L f,int v){l(f,v);log.add(name+"."+f+"="+Integer.toUnsignedString(v));}
    }
    /** Actual dynamic pool adapter for these tests; uses existing typed aliases by matching native offset. */
    private static class P implements Slot {
        final CdObjectPool pool;final int n;P(CdObjectPool p,int n){pool=p;this.n=n;}
        public int read(B f){return pool.state(n).copyBytes()[f.offset]&255;}
        public int read(W f){return wordAt(pool.state(n),f.offset);}
        public int read(L f){byte[] b=pool.state(n).copyBytes();int v=0;for(int i=0;i<4;i++)v=v<<8|(b[f.offset+i]&255);return v;}
        public void write(B f,int v){var a=Arrays.stream(CdObjectState.ByteField.values()).filter(x->x.byteOffset()==f.offset).findFirst().orElseThrow();pool.write(pool.state(n).moveByte(a,v));}
        public void write(W f,int v){var a=Arrays.stream(CdObjectState.WordField.values()).filter(x->x.byteOffset()==f.offset).findFirst().orElseThrow();pool.write(pool.state(n).moveWord(a,v));}
        public void write(L f,int v){var a=Arrays.stream(CdObjectState.LongField.values()).filter(x->x.byteOffset()==f.offset).findFirst().orElseThrow();pool.write(pool.state(n).moveLong(a,v));}
    }
    private static class H implements Calls {
        final EnumMap<Value,Integer> g=new EnumMap<>(Value.class); final List<String> log=new ArrayList<>();
        final S p=new S("p",log),r=new S("r",log);final CdObjectPool pool=new CdObjectPool();
        final Deque<Integer> randoms=new ArrayDeque<>();Integer sine=0;String fail;
        final IllegalStateException failure=new IllegalStateException("injected call fault");
        H(){for(Value f:Value.values())g.put(f,0);}
        public int read(Value f){if(!g.containsKey(f))throw new IllegalStateException("unknown "+f);return g.get(f);}
        public void write(Value f,int v){g.put(f,v);log.add(f+"="+v);}
        public Slot player(){return p;}public Slot reservedBubbles(){return r;}
        public CdPlayerMath.SineCosine sineCosine(int a){log.add("sine:"+a);if(sine==null)throw failure;return new CdPlayerMath.SineCosine(sine,0);}
        public Optional<Slot> spawn(){log.add("spawn");var n=pool.findObjSlot();return n.isPresent()?Optional.of(new P(pool,n.getAsInt())):Optional.empty();}
        public int random(){log.add("random");if(randoms.isEmpty())throw new IllegalStateException("unknown RNG");return randoms.removeFirst();}
        void call(String s){log.add(s);if(s.equals(fail))throw failure;}
        public void sound(int id){call("sound:"+id);}public void music(int id){call("music:"+id);}public void setPlayerGround(){call("ground");}
        public void move(Slot o){call("move:"+((S)o).name);}public void animate(Slot o,Animation a){call("animate:"+a);}
        public void draw(Slot o){call("draw:"+((S)o).name);}public void delete(Slot o){call("delete:"+((S)o).name);}
        void fillPool(){for(int n=32;n<128;n++)pool.write(pool.state(n).moveByte(CdObjectState.ByteField.ID,0x7F));}
    }
}
