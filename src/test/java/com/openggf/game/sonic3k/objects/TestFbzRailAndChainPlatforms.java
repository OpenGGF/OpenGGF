package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;
import com.openggf.game.OscillationManager;
import com.openggf.game.OscillationSnapshot;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.LevelManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestFbzRailAndChainPlatforms {
    @Test void everyCountedFloatingSubtypeSelectsTheAsmMovementEntry() {
        int[] subtypes = {0x00,0x10,0x20,0x30,0x38,0x41,0x45,0x46,0x49,0x4F};
        for (int subtype : subtypes) {
            var platform = new FbzFloatingPlatformObjectInstance(spawn(0x71, subtype));
            assertEquals((subtype & 0x70) >>> 4, platform.movementMode());
            assertEquals((subtype & 0x0F) << 4, platform.phase());
            assertEquals(0x2B, platform.getSolidParams().halfWidth());
            assertEquals(0x0C, platform.getSolidParams().airHalfHeight());
            assertEquals(0x0C, platform.getSolidParams().groundHalfHeight(),
                    "SolidObjectFull_Offset keeps d2=$C as the surface radius");
            assertEquals(-0x0D, platform.getSolidParams().offsetY(),
                    "the d3=-$D argument shifts the collision anchor");
            assertEquals(-0x0D, platform.romGroundOffset());
            assertTrue(platform.fullSolidBottomOverlapUsesCurrentYRadiusOnly(null),
                    "SolidObjectFull_Offset_1P doubles d2 after adding the live y_radius; "
                            + "it does not use SolidObject_cont's default_y_radius lower half");
            assertEquals(0x20, platform.getBalanceWidthPixels(),
                    "Sonic_Balance reads Obj_FBZFloatingPlatform width_pixels, not the $2B solid width");
            assertTrue(platform.getSolidRoutineProfile().inclusiveRightEdge());
            assertTrue(platform.getSolidRoutineProfile().bypassesOffscreenSolidGate());
            assertTrue(platform.usesInstanceSolidStateLatchKey(),
                    "Obj71 rewrites its dynamic spawn while ROM keeps standing/pushing bits in the live SST slot");
            assertEquals(0x0003,assertInstanceOf(
                    com.openggf.level.objects.RomObjectCodePointerProvider.class,platform)
                    .romObjectCodePointerHighWord(),
                    "loc_3A5DA is the word latched by S3K Tails_CPU_interact");
            assertEquals(5,platform.getPriorityBucket());
            assertEquals(0x8C,assertInstanceOf(com.openggf.level.objects.TouchResponseProvider.class,platform).getCollisionFlags());
        }
    }

    @Test void chainSubtypesDecodeVerticalAndHorizontalRangesWithoutSharedPlayerState() {
        for (int subtype : new int[]{0x05,0x0F,0x12,0x14,0x16,0x1B,0x83,0x84,0x88,0xC3,0xC7,0xC8}) {
            var link = new FbzChainLinkObjectInstance(spawn(0x72, subtype));
            assertEquals((subtype & 0x80) != 0, link.horizontalMode());
            int expected = (subtype & 0x80) != 0 ? (subtype & 0x3F) << 4 : (subtype & 0x7F) << 3;
            assertEquals(expected, link.rangePixels());
            assertEquals(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
                    link.participationPolicy());
            assertNotSame(link.stateForParticipant(0), link.stateForParticipant(1));
            assertNotSame(link.stateForParticipant(1), link.stateForParticipant(2));
            assertEquals(1,link.getPriorityBucket());
        }
    }

    @Test void grabJumpUsesExactReleaseVelocityAndCooldown() {
        assertEquals(-0x380, FbzChainLinkObjectInstance.RELEASE_Y_VELOCITY);
        assertEquals(0x200, FbzChainLinkObjectInstance.RELEASE_X_VELOCITY);
        assertEquals(0x12, FbzChainLinkObjectInstance.JUMP_COOLDOWN);
        assertEquals(0x3C, FbzChainLinkObjectInstance.DIRECTIONAL_JUMP_COOLDOWN);
    }

    @Test void threePlayersGrabIndependentlyAndChainStaysAliveWhileAnyOwnerRemains() {
        TestSprite p1=new TestSprite("sonic"),p2=new TestSprite("tails"),p3=new TestSprite("sidekick_3");
        for(TestSprite p:List.of(p1,p2,p3)){p.setCentreX((short)0x1000);p.setCentreY((short)0x892);p.setSubpixelRaw(0x1234,0x5678);}
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));
        chain.setServices(new PlayersServices(p1,List.of(p2,p3)));chain.update(0,null);
        for(int i=0;i<3;i++)assertTrue(chain.stateForParticipant(i).grabbed());
        for(TestSprite p:List.of(p1,p2,p3)){assertTrue(p.isObjectControlled());assertEquals(0x1234,p.getXSubpixelRaw());assertEquals(0x5678,p.getYSubpixelRaw());}
        p3.setJumpInputPressed(true,true);p3.setLogicalInputState(false,false,false,false,true,true);chain.update(1,null);
        assertFalse(chain.stateForParticipant(2).grabbed());assertFalse(p3.isObjectControlled());assertEquals(-0x380,p3.getYSpeed());
        assertTrue(chain.stateForParticipant(0).grabbed());assertTrue(chain.stateForParticipant(1).grabbed());assertFalse(chain.isDestroyed());
    }

    @Test void horizontalDirectionalJumpUsesLongCooldownRightWinsAndFrame96() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        p.setDirectionalInputPressed(true,false,true,true);p.setJumpInputPressed(true,true);p.setLogicalInputState(true,false,true,true,true,true);chain.update(1,null);assertFalse(chain.stateForParticipant(0).grabbed());assertEquals(0x3C,chain.stateForParticipant(0).cooldown());assertEquals(0x200,p.getXSpeed());assertEquals(-0x380,p.getYSpeed());assertTrue(p.getRolling());assertEquals(0x96,p.getMappingFrame());assertFalse(p.isObjectControlled());
    }

    @Test void floatingModesReadRomOscillatingTableAndMode4RunsRiderTriggeredSquashCycle() {
        OscillationManager.reset();
        OscillationSnapshot base=OscillationManager.snapshot();
        int[] values=base.values(),deltas=base.deltas();
        values[2]=0x1D00;deltas[2]=0xFF00;
        values[7]=0x3500;deltas[7]=0x0100;
        OscillationManager.restore(new OscillationSnapshot(values,deltas,base.activeSpeeds(),base.activeLimits(),base.control(),base.lastFrame(),base.suppressedUpdates()));
        byte[] romTable=OscillationManager.snapshotRomFormatBytes();
        var mode1=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x10));mode1.update(123,null);
        assertEquals(0x800+Byte.toUnsignedInt(romTable[0x0A])-0x20,mode1.getY(),
                "loc_3A63A reads Oscillating_table+$0A after the two-byte control word");
        var mode2=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x20));mode2.update(123,null);
        assertEquals(0x800+Byte.toUnsignedInt(romTable[0x1E])-0x40,mode2.getY(),
                "loc_3A646 reads Oscillating_table+$1E after the two-byte control word");

        OscillationManager.reset();
        var mode4=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x41));
        SolidObjectListener listener=assertInstanceOf(SolidObjectListener.class,mode4);
        listener.onSolidContact(new TestSprite("sonic"),new SolidContact(true,false,false,false,false,0,false),0);
        int anchor=mode4.getY();
        for(int i=0;i<0x80;i++)mode4.update(i,null);
        assertNotEquals(anchor,mode4.getY(),"standing begins the ROM acceleration/deceleration squash/drop phase");
    }

    @Test void mode4NibbleOneMatchesEveryRomAccelerationRecurrenceStep() {
        var p=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x41));
        assertInstanceOf(SolidObjectListener.class,p).onSolidContact(new TestSprite("sonic"),new SolidContact(true,false,false,false,false,0,false),0);
        // cmp.b $32(a0),d2 observes the high byte of the big-endian $32 word.
        int[] acc={4,12,24,40,60,84,112,144,180,220};int[] vel={4,8,12,16,20,24,28,32,36,40};boolean[] dir={false,false,false,false,false,false,false,false,false,false};int[] off={0,0,0,0,0,1,1,2,2,3};
        for(int i=0;i<10;i++){p.update(i,null);assertEquals(acc[i],p.dropAccumulator(),"acc frame "+i);assertEquals(vel[i],p.dropVelocity(),"vel frame "+i);assertEquals(dir[i],p.dropDirection(),"dir frame "+i);assertEquals(0x800+off[i],p.getY(),"y frame "+i);}
    }

    @Test void mode4ReturnsPermanentlyToOrdinarySineAfterFirstDrop() {
        var p=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x41));
        SolidObjectListener listener=assertInstanceOf(SolidObjectListener.class,p);
        TestSprite rider=new TestSprite("sonic");
        listener.onSolidContact(rider,new SolidContact(true,false,false,false,false,0,false),0);
        int frame=0;
        while(!p.mode4Completed()&&frame<1000)p.update(frame++,null);
        assertTrue(p.mode4Completed(),"loc_3A724 replaces callback $40 with loc_3A620 when $34 reaches zero");
        int completedAccumulator=p.dropAccumulator();
        int completedAnchor=p.getY();
        for(int next=frame;next<frame+0x80;next++){
            listener.onSolidContact(rider,new SolidContact(true,false,false,false,false,0,false),next);
            p.update(next,null);
            assertEquals(0,p.dropVelocity(),"standing must not re-enter loc_3A6D0 after callback replacement");
            assertEquals(completedAccumulator,p.dropAccumulator(),"ordinary sine must leave the completed drop words untouched");
            assertTrue(Math.abs(p.getY()-completedAnchor)<=8,"loc_3A620 remains within its native +/-8px sine range");
        }
    }

    @Test void mode3UsesRadius64AndNegatesFrameCounterBeforeAddingPhaseWhenFlipped() {
        var normal=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x30));normal.update(0,null);assertEquals(0x1000,normal.getX());assertEquals(0x840,normal.getY());
        var flipped=new FbzFloatingPlatformObjectInstance(new ObjectSpawn(0x1000,0x800,0x71,0x38,1,true,2));flipped.update(1,null);
        int angle=(-1+0x80)&0xFF;assertEquals(0x1000+(com.openggf.physics.TrigLookupTable.sinHex(angle)>>2),flipped.getX());assertEquals(0x800+(com.openggf.physics.TrigLookupTable.cosHex(angle)>>2),flipped.getY());
    }

    @Test void floatingPlatformDeleteTouchUsesImmutableAnchorXAfterMovement() {
        var platform=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x38));
        platform.update(0x40,null);
        assertFalse(platform.isDestroyed(),
                "loc_3A616 delegates deletion to the shared loc_1B666 tail; the movement routine does not self-cull");
        assertNotEquals(0x1000,platform.getX(),"mode 3 must move before the delete-touch tail");
        assertEquals(0x1000,platform.getOutOfRangeReferenceX(),
                "loc_3A616 loads $44(a0) before jumping to loc_1B666; current x_pos is not the cull key");
    }

    @Test void mode3ReadsRomVisibleLevelFrameCounterInsteadOfObjectVblankClock() {
        LevelManager levelManager=mock(LevelManager.class);
        when(levelManager.getFrameCounter()).thenReturn(0x50);
        var platform=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x30));
        platform.setServices(new TestObjectServices().withLevelManager(levelManager));

        platform.update(0x91,null);

        int romVisibleCounter=0x51;
        assertEquals(0x1000+(com.openggf.physics.TrigLookupTable.sinHex(romVisibleCounter)>>2),platform.getX(),
                "loc_3A664 reads (Level_frame_counter+1).w, not ObjectManager's free-running VBla clock");
        assertEquals(0x800+(com.openggf.physics.TrigLookupTable.cosHex(romVisibleCounter)>>2),platform.getY());
    }

    @Test void horizontalChainWalksHandOverHandAndReleasesAtConfiguredEnd() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0xC3));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        assertTrue(chain.stateForParticipant(0).grabbed());int before=p.getCentreX();
        p.setDirectionalInputPressed(false,false,false,true);for(int i=1;i<=32;i++)chain.update(i,null);
        assertTrue(p.getCentreX()>before,"RawAni_3AC38/byte_3AC40 advance the player toward the right end");
        assertFalse(chain.stateForParticipant(0).grabbed(),"subtype bit6 releases at the configured endpoint");
        assertFalse(p.isObjectControlled());
    }

    @Test void horizontalHandCycleUsesExactFramesDeltasAndMidpointGrabEdge() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        PlayersServices services=new PlayersServices(p,List.of());var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(services);chain.update(0,null);
        p.setDirectionalInputPressed(false,false,false,true);int start=p.getCentreX();
        int[] frames={1,9,17,25},delta={6,16,28,32},maps={0x85,0x80,0x81,0x91};
        for(int frame=1;frame<=25;frame++){chain.update(frame,null);for(int i=0;i<frames.length;i++)if(frame==frames[i]){assertEquals(start+delta[i],p.getCentreX());assertEquals(maps[i],p.getMappingFrame());}}
        assertEquals(2,services.grabSfxCount,"capture plus remaining==2 cadence edge");
    }

    @Test void horizontalHandCycleDefersOppositeFacingUntilTheActiveStepCompletes() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        p.setDirectionalInputPressed(false,false,true,false);chain.update(1,null);
        assertEquals(com.openggf.physics.Direction.LEFT,p.getDirection());
        assertTrue(chain.stateForParticipant(0).handStep()>0);
        p.setDirectionalInputPressed(false,false,false,true);chain.update(2,null);
        assertEquals(com.openggf.physics.Direction.LEFT,p.getDirection(),
                "loc_3AB3E branches to loc_3ABBE while byte 4(a2) is active, before the facing writes");
    }

    @Test void horizontalEndpointReleaseStillSamplesDirectionAfterSub3ac48Returns() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(new ObjectSpawn(0x1000,0x800,0x72,0xC3,1,true,2));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        p.setDirectionalInputPressed(false,false,true,false);for(int frame=1;frame<25;frame++)chain.update(frame,null);
        assertEquals(com.openggf.physics.Direction.LEFT,p.getDirection());
        int before=p.getCentreX();p.setDirectionalInputPressed(false,false,false,true);chain.update(25,null);
        assertFalse(chain.stateForParticipant(0).grabbed());
        assertEquals(com.openggf.physics.Direction.RIGHT,p.getDirection(),
                "sub_3AC48 branches to loc_3AB24, whose rts returns to loc_3AC26 before the input recheck");
        assertEquals(before,p.getCentreX(),"the final left delta and first away-from-endpoint right delta cancel");
        assertEquals(3,chain.stateForParticipant(0).handStep());
        assertEquals(0,chain.stateForParticipant(0).cooldown(),
                "the post-release right start overwrites aliased byte 2(a2)'s $3C cooldown with direction 0");
    }

    @Test void horizontalEndpointReleaseDoesNotRecaptureThePlayerOnTheNextObjectPass() {
        TestSprite p=new TestSprite("tails");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(new ObjectSpawn(0x1000,0x800,0x72,0xC3,1,true,2));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        p.setDirectionalInputPressed(false,false,true,false);for(int frame=1;frame<25;frame++)chain.update(frame,null);
        p.setDirectionalInputPressed(false,false,false,true);
        chain.update(25,null);
        assertFalse(chain.stateForParticipant(0).grabbed());
        assertEquals(0,chain.stateForParticipant(0).cooldown(),
                "the post-release re-entry aliases byte 2(a2) back to the right-facing direction value");
        assertEquals(3,chain.stateForParticipant(0).handStep());
        chain.update(26,null);
        assertFalse(chain.stateForParticipant(0).grabbed(),
                "loc_3ACEA excludes the configured endpoint cell even after cooldown is overwritten");
    }

    @Test void horizontalHandCycleClearsItsStepTimerBeforeALaterDirectionStarts() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        p.setDirectionalInputPressed(false,false,false,true);chain.update(1,null);
        p.setDirectionalInputPressed(false,false,false,false);for(int frame=2;frame<=25;frame++)chain.update(frame,null);
        assertEquals(0,chain.stateForParticipant(0).handStep());
        assertEquals(0,chain.stateForParticipant(0).handTimer(),
                "loc_3AC26 clears byte 6(a2) when the four-step cycle completes");
        int before=p.getCentreX();p.setDirectionalInputPressed(false,false,true,false);chain.update(26,null);
        assertEquals(before-4,p.getCentreX(),"the next phase's first hand delta executes on its input frame");
    }

    @Test void invalidHeldPlayerDropsWithLongCooldownAndHorizontalHandAlwaysOwnsMapping() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        assertTrue(p.isObjectMappingFrameControl());assertEquals(0x91,p.getMappingFrame());chain.update(1,null);assertTrue(p.isObjectMappingFrameControl());
        p.setRenderFlagOnScreen(false);chain.update(2,null);assertFalse(chain.stateForParticipant(0).grabbed());assertEquals(0x3C,chain.stateForParticipant(0).cooldown());assertFalse(p.isObjectControlled());assertFalse(p.isOnObject());

        TestSprite debug=new TestSprite("tails");debug.setCentreX((short)0x1000);debug.setCentreY((short)0x892);var vertical=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));vertical.setServices(new PlayersServices(debug,List.of()));vertical.update(0,null);debug.setDebugMode(true);vertical.update(1,null);assertFalse(vertical.stateForParticipant(0).grabbed());assertEquals(0x3C,vertical.stateForParticipant(0).cooldown());
    }

    @Test void horizontalRecaptureClearsStaleHandStepAndTimer() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        p.setDirectionalInputPressed(false,false,true,false);chain.update(1,null);
        assertTrue(chain.stateForParticipant(0).handStep()>0);
        p.setRenderFlagOnScreen(false);chain.update(2,null);p.setRenderFlagOnScreen(true);
        p.setDirectionalInputPressed(false,false,false,false);
        for(int frame=3;frame<=62;frame++)chain.update(frame,null);
        assertTrue(chain.stateForParticipant(0).grabbed());
        assertEquals(0,chain.stateForParticipant(0).handStep(),"loc_3AD10 clears byte 4(a2) on capture");
        assertEquals(0,chain.stateForParticipant(0).handTimer(),"loc_3AD10 clears byte 6(a2) on capture");
        assertEquals(0x91,p.getMappingFrame());
    }

    @Test void chainTransfersHorizontalOwnerToVerticalButRejectsVerticalOwnerFromHorizontal() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var horizontal=new FbzChainLinkObjectInstance(spawn(0x72,0x83));horizontal.setSlotIndex(30);horizontal.setServices(new PlayersServices(p,List.of()));horizontal.update(0,null);p.setLatchedSolidObject(0x72,horizontal);
        p.setCentreX((short)0x1000);p.setCentreY((short)0x892);var vertical=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));vertical.setSlotIndex(31);vertical.setServices(new PlayersServices(p,List.of()));vertical.update(1,null);
        assertTrue(vertical.stateForParticipant(0).grabbed());assertFalse(horizontal.stateForParticipant(0).grabbed());assertEquals(0x3C,horizontal.stateForParticipant(0).cooldown());assertSame(vertical,p.getLatchedSolidObjectInstance());

        TestSprite q=new TestSprite("tails");q.setCentreX((short)0x1000);q.setCentreY((short)0x892);vertical=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));vertical.setSlotIndex(32);vertical.setServices(new PlayersServices(q,List.of()));vertical.update(0,null);q.setLatchedSolidObject(0x72,vertical);q.setCentreY((short)0x800);
        horizontal=new FbzChainLinkObjectInstance(spawn(0x72,0x83));horizontal.setServices(new PlayersServices(q,List.of()));horizontal.update(1,null);assertFalse(horizontal.stateForParticipant(0).grabbed());assertTrue(vertical.stateForParticipant(0).grabbed());
    }

    @Test void chainJumpReleasePreservesNativeCentreAndSubpixelWhileChangingRadii() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x892);p.setSubpixelRaw(0x1234,0x5678);var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));chain.setSlotIndex(33);chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        int x=p.getCentreX(),y=p.getCentreY();p.setJumpInputPressed(true,true);p.setLogicalInputState(false,false,false,false,true,true);chain.update(1,null);assertEquals(x,p.getCentreX());assertEquals(y,p.getCentreY());assertEquals(0x1234,p.getXSubpixelRaw());assertEquals(0x5678,p.getYSubpixelRaw());assertEquals(0xE,p.getYRadius());assertEquals(7,p.getXRadius());assertNull(p.getLatchedSolidObjectInstance());assertEquals(0,p.getInteractSlotIndex(),"sub_3AA7E explicitly clears interact(a0)");
    }

    @Test void chainJumpReleaseConsumesTheLogicalPressByteNotASynthesizedHeldEdge() {
        // sub_3AA7E receives Ctrl_1_logical/Ctrl_2_logical in d0 and masks
        // their low-byte A/B/C press bits (sonic3k.asm:78527-78535,78552-78559).
        TestSprite p=new TestSprite("tails");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        assertTrue(chain.stateForParticipant(0).grabbed());

        p.setJumpInputPressed(true,true);
        p.setLogicalInputState(false,false,false,false,true,false);
        chain.update(1,null);
        assertTrue(chain.stateForParticipant(0).grabbed(),"a raw held transition without Ctrl_*_logical press must not release");

        p.setJumpInputPressed(true,false);
        p.setLogicalInputState(false,false,false,false,true,true);
        chain.update(2,null);
        assertFalse(chain.stateForParticipant(0).grabbed(),"the logical low-byte jump press releases the chain");
    }

    @Test void bothChainCapturesSelectHangingAnimationAndHorizontalInputOwnsFacingAndRenderBits() {
        TestSprite verticalPlayer=new TestSprite("sonic");verticalPlayer.setCentreX((short)0x1000);verticalPlayer.setCentreY((short)0x892);var vertical=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));vertical.setServices(new PlayersServices(verticalPlayer,List.of()));vertical.update(0,null);assertEquals(0x14,verticalPlayer.getAnimationId());
        TestSprite horizontalPlayer=new TestSprite("tails");horizontalPlayer.setCentreX((short)0x1000);horizontalPlayer.setCentreY((short)0x800);horizontalPlayer.setRenderFlips(true,true);var horizontal=new FbzChainLinkObjectInstance(spawn(0x72,0x83));horizontal.setServices(new PlayersServices(horizontalPlayer,List.of()));horizontal.update(0,null);assertEquals(0x14,horizontalPlayer.getAnimationId());
        horizontalPlayer.setDirectionalInputPressed(false,false,true,false);horizontal.update(1,null);assertEquals(com.openggf.physics.Direction.LEFT,horizontalPlayer.getDirection());assertTrue(horizontalPlayer.getRenderHFlip());assertFalse(horizontalPlayer.getRenderVFlip());
        horizontalPlayer.setDirectionalInputPressed(false,false,false,false);for(int frame=2;frame<=25;frame++)horizontal.update(frame,null);
        horizontalPlayer.setDirectionalInputPressed(false,false,true,true);horizontal.update(26,null);assertEquals(com.openggf.physics.Direction.RIGHT,horizontalPlayer.getDirection(),"right input wins the ROM's sequential status writes when the hand cycle is idle");assertFalse(horizontalPlayer.getRenderHFlip());assertFalse(horizontalPlayer.getRenderVFlip());
    }

    @Test void endpointReleasePerformsFullCleanupForMainAndExtraSidekick() {
        TestSprite main=new TestSprite("sonic"),extra=new TestSprite("sidekick_3");for(TestSprite p:List.of(main,extra)){p.setCentreX((short)0x1000);p.setCentreY((short)0x800);}
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0xC3));chain.setSlotIndex(40);chain.setServices(new PlayersServices(main,List.of(extra)));chain.update(0,null);for(TestSprite p:List.of(main,extra)){p.setLatchedSolidObject(0x72,chain);p.setDirectionalInputPressed(false,false,false,true);}
        for(int frame=1;frame<=40;frame++){chain.update(frame,null);if(!chain.stateForParticipant(0).grabbed()&&!chain.stateForParticipant(1).grabbed())break;}
        for(int i=0;i<2;i++){TestSprite p=i==0?main:extra;assertFalse(chain.stateForParticipant(i).grabbed());assertEquals(0x3C,chain.stateForParticipant(i).cooldown());assertFalse(p.isOnObject());assertFalse(p.isObjectControlled());assertFalse(p.isObjectMappingFrameControl());assertNull(p.getLatchedSolidObjectInstance());assertEquals(0,p.getInteractSlotIndex(),"loc_3AB24 explicitly clears interact(a0)");assertEquals(com.openggf.physics.Direction.RIGHT,p.getDirection());assertFalse(p.getRenderHFlip());assertFalse(p.getRenderVFlip());}
    }

    private static final class PlayersServices extends TestObjectServices {private final ObjectPlayerQuery q;int grabSfxCount;PlayersServices(PlayableEntity p,List<? extends PlayableEntity>s){q=new ObjectPlayerQuery(()->p,()->s);}@Override public ObjectPlayerQuery playerQuery(){return q;}@Override public void playSfx(int id){if(id==com.openggf.game.sonic3k.audio.Sonic3kSfx.GRAB.id)grabSfxCount++;}}
    private static final class TestSprite extends AbstractPlayableSprite {
        TestSprite(String c){super(c,(short)0,(short)0);}
        @Override public void setDirectionalInputPressed(boolean up,boolean down,boolean left,boolean right){
            super.setDirectionalInputPressed(up,down,left,right);
            setLogicalInputState(up,down,left,right,false);
        }
        @Override public void draw(){}
        @Override public void defineSpeeds(){}
        @Override protected void createSensorLines(){}
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 2);
    }
}
