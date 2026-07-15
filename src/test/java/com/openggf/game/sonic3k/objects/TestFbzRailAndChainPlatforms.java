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
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidContact;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzRailAndChainPlatforms {
    @Test void everyCountedFloatingSubtypeSelectsTheAsmMovementEntry() {
        int[] subtypes = {0x00,0x10,0x20,0x30,0x38,0x41,0x45,0x46,0x49,0x4F};
        for (int subtype : subtypes) {
            var platform = new FbzFloatingPlatformObjectInstance(spawn(0x71, subtype));
            assertEquals((subtype & 0x70) >>> 4, platform.movementMode());
            assertEquals((subtype & 0x0F) << 4, platform.phase());
            assertEquals(0x2B, platform.getSolidParams().halfWidth());
            assertEquals(0x0C, platform.getSolidParams().airHalfHeight());
            assertEquals(-0x0D, platform.romGroundOffset());
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
        p3.setJumpInputPressed(true,true);chain.update(1,null);
        assertFalse(chain.stateForParticipant(2).grabbed());assertFalse(p3.isObjectControlled());assertEquals(-0x380,p3.getYSpeed());
        assertTrue(chain.stateForParticipant(0).grabbed());assertTrue(chain.stateForParticipant(1).grabbed());assertFalse(chain.isDestroyed());
    }

    @Test void horizontalDirectionalJumpUsesLongCooldownRightWinsAndFrame96() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        p.setDirectionalInputPressed(true,false,true,true);p.setJumpInputPressed(true,true);chain.update(1,null);assertFalse(chain.stateForParticipant(0).grabbed());assertEquals(0x3C,chain.stateForParticipant(0).cooldown());assertEquals(0x200,p.getXSpeed());assertEquals(-0x380,p.getYSpeed());assertTrue(p.getRolling());assertEquals(0x96,p.getMappingFrame());assertFalse(p.isObjectControlled());
    }

    @Test void floatingModesReadRomOscillatingTableAndMode4RunsRiderTriggeredSquashCycle() {
        OscillationManager.reset();
        var mode1=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x10));mode1.update(123,null);
        assertEquals(0x800+OscillationManager.getByte(0x0A)-0x20,mode1.getY());
        var mode2=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x20));mode2.update(123,null);
        assertEquals(0x800+OscillationManager.getByte(0x1E)-0x40,mode2.getY());

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
        int[] acc={4,12,24,40,60,76,88,96,100,100};int[] vel={4,8,12,16,20,16,12,8,4,0};boolean[] dir={false,false,false,false,true,true,true,true,true,true};int[] off={0,0,0,0,0,1,1,1,1,1};
        for(int i=0;i<10;i++){p.update(i,null);assertEquals(acc[i],p.dropAccumulator(),"acc frame "+i);assertEquals(vel[i],p.dropVelocity(),"vel frame "+i);assertEquals(dir[i],p.dropDirection(),"dir frame "+i);assertEquals(0x800+off[i],p.getY(),"y frame "+i);}
    }

    @Test void mode4ReturnsPermanentlyToOrdinarySineAfterFirstDrop() {
        var p=new FbzFloatingPlatformObjectInstance(spawn(0x71,0x41));
        SolidObjectListener listener=assertInstanceOf(SolidObjectListener.class,p);
        TestSprite rider=new TestSprite("sonic");
        listener.onSolidContact(rider,new SolidContact(true,false,false,false,false,0,false),0);
        for(int frame=0;frame<10;frame++)p.update(frame,null);
        assertTrue(p.mode4Completed(),"loc_3A724 replaces callback $40 with loc_3A620 when $34 reaches zero");
        int completedAccumulator=p.dropAccumulator();
        int completedAnchor=p.getY();
        for(int frame=10;frame<0x90;frame++){
            listener.onSolidContact(rider,new SolidContact(true,false,false,false,false,0,false),frame);
            p.update(frame,null);
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
        int[] frames={1,9,17,25},delta={6,16,28,36},maps={0x85,0x80,0x81,0x82};
        for(int frame=1;frame<=25;frame++){chain.update(frame,null);for(int i=0;i<frames.length;i++)if(frame==frames[i]){assertEquals(start+delta[i],p.getCentreX());assertEquals(maps[i],p.getMappingFrame());}}
        assertEquals(2,services.grabSfxCount,"capture plus remaining==2 cadence edge");
    }

    @Test void invalidHeldPlayerDropsWithLongCooldownAndHorizontalHandAlwaysOwnsMapping() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x800);
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x83));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        assertTrue(p.isObjectMappingFrameControl());assertEquals(0x91,p.getMappingFrame());chain.update(1,null);assertTrue(p.isObjectMappingFrameControl());
        p.setRenderFlagOnScreen(false);chain.update(2,null);assertFalse(chain.stateForParticipant(0).grabbed());assertEquals(0x3C,chain.stateForParticipant(0).cooldown());assertFalse(p.isObjectControlled());assertFalse(p.isOnObject());

        TestSprite debug=new TestSprite("tails");debug.setCentreX((short)0x1000);debug.setCentreY((short)0x892);var vertical=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));vertical.setServices(new PlayersServices(debug,List.of()));vertical.update(0,null);debug.setDebugMode(true);vertical.update(1,null);assertFalse(vertical.stateForParticipant(0).grabbed());assertEquals(0x3C,vertical.stateForParticipant(0).cooldown());
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
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)0x1000);p.setCentreY((short)0x892);p.setSubpixelRaw(0x1234,0x5678);var chain=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));chain.setServices(new PlayersServices(p,List.of()));chain.update(0,null);
        int x=p.getCentreX(),y=p.getCentreY();p.setJumpInputPressed(true,true);chain.update(1,null);assertEquals(x,p.getCentreX());assertEquals(y,p.getCentreY());assertEquals(0x1234,p.getXSubpixelRaw());assertEquals(0x5678,p.getYSubpixelRaw());assertEquals(0xE,p.getYRadius());assertEquals(7,p.getXRadius());
    }

    @Test void bothChainCapturesSelectHangingAnimationAndHorizontalInputOwnsFacingAndRenderBits() {
        TestSprite verticalPlayer=new TestSprite("sonic");verticalPlayer.setCentreX((short)0x1000);verticalPlayer.setCentreY((short)0x892);var vertical=new FbzChainLinkObjectInstance(spawn(0x72,0x1B));vertical.setServices(new PlayersServices(verticalPlayer,List.of()));vertical.update(0,null);assertEquals(0x14,verticalPlayer.getAnimationId());
        TestSprite horizontalPlayer=new TestSprite("tails");horizontalPlayer.setCentreX((short)0x1000);horizontalPlayer.setCentreY((short)0x800);horizontalPlayer.setRenderFlips(true,true);var horizontal=new FbzChainLinkObjectInstance(spawn(0x72,0x83));horizontal.setServices(new PlayersServices(horizontalPlayer,List.of()));horizontal.update(0,null);assertEquals(0x14,horizontalPlayer.getAnimationId());
        horizontalPlayer.setDirectionalInputPressed(false,false,true,false);horizontal.update(1,null);assertEquals(com.openggf.physics.Direction.LEFT,horizontalPlayer.getDirection());assertTrue(horizontalPlayer.getRenderHFlip());assertFalse(horizontalPlayer.getRenderVFlip());
        horizontalPlayer.setDirectionalInputPressed(false,false,true,true);horizontal.update(2,null);assertEquals(com.openggf.physics.Direction.RIGHT,horizontalPlayer.getDirection(),"right input wins the ROM's sequential status writes");assertFalse(horizontalPlayer.getRenderHFlip());assertFalse(horizontalPlayer.getRenderVFlip());
    }

    @Test void endpointReleasePerformsFullCleanupForMainAndExtraSidekick() {
        TestSprite main=new TestSprite("sonic"),extra=new TestSprite("sidekick_3");for(TestSprite p:List.of(main,extra)){p.setCentreX((short)0x1000);p.setCentreY((short)0x800);}
        var chain=new FbzChainLinkObjectInstance(spawn(0x72,0xC3));chain.setSlotIndex(40);chain.setServices(new PlayersServices(main,List.of(extra)));chain.update(0,null);for(TestSprite p:List.of(main,extra)){p.setLatchedSolidObject(0x72,chain);p.setDirectionalInputPressed(false,false,false,true);}
        for(int frame=1;frame<=40;frame++){chain.update(frame,null);if(!chain.stateForParticipant(0).grabbed()&&!chain.stateForParticipant(1).grabbed())break;}
        for(int i=0;i<2;i++){TestSprite p=i==0?main:extra;assertFalse(chain.stateForParticipant(i).grabbed());assertEquals(0x3C,chain.stateForParticipant(i).cooldown());assertFalse(p.isOnObject());assertFalse(p.isObjectControlled());assertFalse(p.isObjectMappingFrameControl());assertNull(p.getLatchedSolidObjectInstance());assertEquals(0,p.getInteractSlotIndex());assertEquals(com.openggf.physics.Direction.RIGHT,p.getDirection());assertFalse(p.getRenderHFlip());assertFalse(p.getRenderVFlip());}
    }

    private static final class PlayersServices extends TestObjectServices {private final ObjectPlayerQuery q;int grabSfxCount;PlayersServices(PlayableEntity p,List<? extends PlayableEntity>s){q=new ObjectPlayerQuery(()->p,()->s);}@Override public ObjectPlayerQuery playerQuery(){return q;}@Override public void playSfx(int id){if(id==com.openggf.game.sonic3k.audio.Sonic3kSfx.GRAB.id)grabSfxCount++;}}
    private static final class TestSprite extends AbstractPlayableSprite {TestSprite(String c){super(c,(short)0,(short)0);}@Override public void draw(){}@Override public void defineSpeeds(){}@Override protected void createSensorLines(){}}

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 2);
    }
}
