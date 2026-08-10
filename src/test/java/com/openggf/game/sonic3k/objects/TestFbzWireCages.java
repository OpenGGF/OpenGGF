package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.ShieldType;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestFbzWireCages {
    @AfterEach void resetCameraBounds() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

    @Test void stationaryCageUsesViewportAwareRomCoarseXRangeWithoutVerticalCulling() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);

        var verticallyDistant = new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x0200,0x7000,0x70,0,0,true,0,1));
        verticallyDistant.setServices(new PlayersServices(new TestSprite("sonic"),List.of()));
        verticallyDistant.update(0,null);
        assertFalse(verticallyDistant.isDestroyed(),
                "Delete_Sprite_If_Not_In_Range tests coarse X only, never object Y");

        var exactNativeBoundary = new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x0200,0x7000,0x70,0,0,true,0,2));
        exactNativeBoundary.setServices(new PlayersServices(new TestSprite("sonic"),List.of()));
        exactNativeBoundary.update(0,null);
        assertFalse(exactNativeBoundary.isDestroyed(),
                "native 320px width retains unsigned coarse distance exactly $280");

        var nextNativeChunk = new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x0280,0x0080,0x70,0,0,true,0,3));
        nextNativeChunk.setServices(new PlayersServices(new TestSprite("sonic"),List.of()));
        nextNativeChunk.update(0,null);
        assertTrue(nextNativeChunk.isDestroyed(),
                "the next $80 chunk exceeds the native $280 range");
        assertTrue(nextNativeChunk.isDestroyedRespawnable(),
                "offscreen Obj70 deletion must clear its placement live bit for later reload");

        AbstractObjectInstance.updateCameraBounds(0, 0, 528, 224, 0);
        var widescreenVisibleChunk = new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x0280,0x7000,0x70,0,0,true,0,4));
        widescreenVisibleChunk.setServices(new PlayersServices(new TestSprite("sonic"),List.of()));
        widescreenVisibleChunk.update(0,null);
        assertFalse(widescreenVisibleChunk.isDestroyed(),
                "widescreen extends only the viewport-width term of the coarse range");

        var beyondWidescreenChunk = new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x0300,0x0080,0x70,0,0,true,0,5));
        beyondWidescreenChunk.setServices(new PlayersServices(new TestSprite("sonic"),List.of()));
        beyondWidescreenChunk.update(0,null);
        assertTrue(beyondWidescreenChunk.isDestroyed(),
                "the first coarse chunk beyond the 528px viewport-aware limit must cull");
    }

    @Test void stationaryCageHeldParticipantDoesNotExemptRomOutOfRangeDeletion() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        TestSprite player = new TestSprite("sonic");
        player.setCentreX((short)(0x0280-0xB8));
        player.setCentreY((short)0x0080);
        player.setXSpeed((short)0x400);
        player.setGSpeed((short)0x400);
        var cage = new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x0280,0x0080,0x70,0,0,true,0,6));
        cage.setServices(new PlayersServices(player,List.of()));

        cage.update(0,null);

        assertTrue(cage.heldByParticipant(0),"test precondition: Obj70 captured the participant first");
        assertTrue(cage.isDestroyed(),
                "native Delete_Sprite_If_Not_In_Range runs even with a standing bit set");
        assertTrue(cage.isDestroyedRespawnable(),
                "the held-path deletion still routes through offscreen respawn cleanup");
    }

    @Test void countedWireSubtypesDecodeTheRomRanges() {
        for (int subtype : new int[]{0x10, 0x18, 0x98, 0xA4, 0xA6}) {
            var cage = new FbzWireCageObjectInstance(spawn(0x6F, subtype));
            assertEquals((subtype & 0x7F) << 3, cage.rangePixels());
            assertEquals((subtype & 0x80) != 0, cage.verticalMode());
            assertEquals(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED,
                    cage.participationPolicy());
        }
    }

    @Test void orbitTablesAndStationaryTravelAreExact() {
        assertEquals(0x6D, FbzWireCageObjectInstance.verticalPlayerFrame(0));
        assertEquals(0x77, FbzWireCageObjectInstance.verticalPlayerFrame(21));
        assertEquals(0x6C, FbzWireCageObjectInstance.verticalPlayerFrame(22));
        assertEquals(4, FbzWireCageStationaryObjectInstance.trackHeight(0));
        assertEquals(0, FbzWireCageStationaryObjectInstance.trackHeight(5));
        assertEquals(0x49, FbzWireCageStationaryObjectInstance.entryFrame(0));
        assertEquals(0x54, FbzWireCageStationaryObjectInstance.entryFrame(1));
        assertEquals(0x100, new FbzWireCageStationaryObjectInstance(spawn(0x70, 1)).travelAngle());
        assertEquals(0x800, new FbzWireCageStationaryObjectInstance(spawn(0x70, 2)).travelExtent());
        try{var field=FbzWireCageStationaryObjectInstance.class.getDeclaredField("LOOP_FRAMES");assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));assertTrue(java.lang.reflect.Modifier.isStatic(field.getModifiers()));assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));field.setAccessible(true);assertEquals(36,((int[])field.get(null)).length);}catch(ReflectiveOperationException e){fail(e);}
    }

    @Test void threeEligiblePlayersHaveIsolatedCarrierStateAndNativeWritesPreserveFractions() {
        TestSprite main=new TestSprite("sonic"),sidekick=new TestSprite("tails"),extra=new TestSprite("sidekick_3");
        for(TestSprite p:List.of(main,sidekick,extra)){p.setCentreX((short)(0x1000-0x70));p.setCentreY((short)0x800);p.setSubpixelRaw(0x1234,0x5678);}
        var cage=new FbzWireCageObjectInstance(spawn(0x6F,0x98));
        cage.setServices(new PlayersServices(main,List.of(sidekick,extra)));
        cage.update(0,null);
        for(int i=0;i<3;i++)assertTrue(cage.heldByParticipant(i));
        assertEquals(0x84,cage.angleForParticipant(0));assertEquals(0x84,cage.angleForParticipant(1));assertEquals(0x84,cage.angleForParticipant(2));
        for(TestSprite p:List.of(main,sidekick,extra)){assertEquals(0x1234,p.getXSubpixelRaw());assertEquals(0x5678,p.getYSubpixelRaw());assertTrue(p.isObjectControlled());}
        extra.setCentreY((short)0xA00);cage.update(1,null);
        assertFalse(cage.heldByParticipant(2));assertFalse(extra.isObjectControlled());
        assertTrue(cage.heldByParticipant(0));assertTrue(cage.heldByParticipant(1));

        main=new TestSprite("sonic");sidekick=new TestSprite("tails");extra=new TestSprite("sidekick_3");
        for(TestSprite p:List.of(main,sidekick,extra)){p.setCentreX((short)(0x1000-0xB8));p.setCentreY((short)0x800);}
        main.setXSpeed((short)0x400);main.setGSpeed((short)0x400);sidekick.setXSpeed((short)0x500);sidekick.setGSpeed((short)0x500);extra.setXSpeed((short)0x600);extra.setGSpeed((short)0x600);
        var stationary=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));
        stationary.setServices(new PlayersServices(main,List.of(sidekick,extra)));stationary.update(2,null);
        for(int i=0;i<3;i++)assertTrue(stationary.heldByParticipant(i));
        for(TestSprite p:List.of(main,sidekick,extra))p.setCentreX((short)(0x1000+8));
        stationary.update(3,null);
        assertEquals(0x0C0000,stationary.trackPositionForParticipant(0));
        assertEquals(0x0D0000,stationary.trackPositionForParticipant(1));
        assertEquals(0x0E0000,stationary.trackPositionForParticipant(2));
    }

    @Test void stationaryCageRequiresExactGroundedEntryWindowAndReleasesWhenSpeedDrops() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)(0x1000-0xB8));p.setCentreY((short)0x800);p.setGSpeed((short)0x3FF);
        var cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));cage.setServices(new PlayersServices(p,List.of()));
        cage.update(0,null);assertEquals(0,cage.trackPositionForParticipant(0),"below $400 never enters");assertFalse(p.isObjectControlled());
        p.setXSpeed((short)0x400);p.setGSpeed((short)0x400);cage.update(1,null);assertTrue(p.isObjectControlled(),"-$B0..-$C0 grounded entry captures");assertFalse(p.getAir());
        assertTrue(p.isObjectControlAllowsCpu());
        assertFalse(p.isObjectControlSuppressesMovement(),
                "object_control $42 leaves bit 0 clear, so normal movement remains active");
        assertTrue(p.isSuppressGroundWallCollision(),
                "object_control bit 6 bypasses CalcRoomInFront while the cage owns Sonic");
        p.setGSpeed((short)0x200);cage.update(2,null);assertFalse(p.isObjectControlled(),"speed below $400 releases and restores control");assertFalse(p.isOnObject());
    }

    @Test void stationaryCageAdvancesTheCurvedTrackOnAnExactZeroPhaseEntryForEveryParticipant() {
        // At FBZ complete-run f16878, loc_3A3B4 stores an exactly-zero track
        // position, then branches directly to loc_3A480. The native routine
        // still adds ground_vel<<8 in that same object call before mapping the
        // player onto the curve (sonic3k.asm:78027-78135).
        TestSprite main=new TestSprite("sonic"),extra=new TestSprite("sidekick_3");
        for(TestSprite player:List.of(main,extra)){
            player.setCentreX((short)(0x1000-0xB8));player.setCentreY((short)0x1000);
            player.setXSpeed((short)0x400);player.setGSpeed((short)0x400);
        }
        var cage=new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x1000,0x1000,0x70,1,0,true,1));
        cage.setServices(new PlayersServices(main,List.of(extra)));cage.update(0,null);

        for(TestSprite player:List.of(main,extra)){
            player.setCentreX((short)0x1000);player.setCentreY((short)0x1000);
            player.setXSpeed((short)0x0E01);player.setGSpeed((short)0x0E01);
        }
        cage.update(1,null);

        for(int i=0;i<2;i++)assertEquals(0x000E0100,cage.trackPositionForParticipant(i),
                "a zero-valued curved entry is still an active loc_3A480 path for participant "+i);
        for(TestSprite player:List.of(main,extra)){
            assertEquals(0x1011,player.getCentreX()&0xFFFF);
            assertEquals(0x1003,player.getCentreY()&0xFFFF);
            assertEquals(0x1100,player.getXSpeed());
            assertEquals(0x0300,player.getYSpeed());
            assertEquals(0x70,player.getMappingFrame());
        }

        TestSprite reverse=new TestSprite("sonic");
        reverse.setCentreX((short)0x10B8);reverse.setCentreY((short)0x1000);
        reverse.setXSpeed((short)-0x400);reverse.setGSpeed((short)-0x400);
        var reverseCage=new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x1000,0x1000,0x70,1,1,true,1));
        reverseCage.setServices(new PlayersServices(reverse,List.of()));reverseCage.update(0,null);
        reverse.setCentreX((short)0x0FF0);reverse.setCentreY((short)0x1000);
        reverse.setXSpeed((short)-0x0E01);reverse.setGSpeed((short)-0x0E01);

        reverseCage.update(1,null);

        assertEquals(0x03E1FF00,reverseCage.trackPositionForParticipant(0),
                "loc_3A3F2 also advances its newly written track in the same object call");
    }

    @Test void stationaryCageNativeP2SkipsOnlyTheDirtyP1DplcFrameWhileExtraSidekicksAdvance() {
        // FixBugs-disabled Obj_FBZWireCageStationary lets P1's
        // Perform_Player_DPLC clobber d6. A changed cage mapping loads a
        // non-empty DPLC and leaves d6=$00100000; addq.b #1 makes native P2
        // test object-status bit 1 instead of its standing bit 4. The next
        // unchanged mapping returns before clobbering d6, restoring bit 4.
        TestSprite main=new TestSprite("sonic"),nativeP2=new TestSprite("tails"),extra=new TestSprite("sidekick_3");
        for(TestSprite player:List.of(main,nativeP2,extra)){
            player.setCentreX((short)0x0F48);player.setCentreY((short)0x1000);
            player.setXSpeed((short)0x400);player.setGSpeed((short)0x400);
            player.setMappingFrame(0x49); // clean P1 DPLC comparison on capture
        }
        var cage=new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x1000,0x1000,0x70,1,0,true,1));
        cage.setServices(new PlayersServices(main,
                java.util.Arrays.asList(null,nativeP2,nativeP2,extra)));
        cage.update(0,null);
        assertTrue(cage.heldByParticipant(1));
        assertTrue(cage.normalStandingForParticipant(1));assertFalse(cage.nativeP2AliasStanding());

        for(TestSprite player:List.of(main,nativeP2,extra)){
            player.setCentreX((short)0x1000);player.setCentreY((short)0x1000);
            player.setXSpeed((short)0x0E01);player.setGSpeed((short)0x0E01);
        }
        cage.update(1,null);

        assertEquals(0x1011,main.getCentreX()&0xFFFF);
        assertEquals(0x1000,nativeP2.getCentreX()&0xFFFF,"dirty d6 misses native P2 standing bit 4");
        assertEquals(0x1000,nativeP2.getCentreY()&0xFFFF);
        assertEquals(0x0E01,nativeP2.getXSpeed());assertEquals(0,nativeP2.getYSpeed());
        assertEquals(0x49,nativeP2.getMappingFrame());assertTrue(nativeP2.isOnObject());assertTrue(nativeP2.isObjectControlled());
        assertEquals(0x1011,extra.getCentreX()&0xFFFF,"extra sidekicks do not inherit the native two-player register bug");
        assertEquals(0x70,extra.getMappingFrame());

        cage.update(2,null);
        assertEquals(0x1011,nativeP2.getCentreX()&0xFFFF,"unchanged P1 mapping restores native P2 standing bit 4");
        assertEquals(0x1003,nativeP2.getCentreY()&0xFFFF);
        assertEquals(0x1100,nativeP2.getXSpeed());assertEquals(0x0300,nativeP2.getYSpeed());
        assertEquals(0x70,nativeP2.getMappingFrame());

        nativeP2.setGSpeed((short)0x200);main.setMappingFrame(0);
        cage.update(3,null);
        assertTrue(nativeP2.isOnObject(),"dirty-bit miss also delays native P2 release");
        cage.update(4,null);
        assertTrue(nativeP2.isOnObject(),"a second changed P1 curve frame still selects the alias bit");
        cage.update(5,null);
        assertFalse(nativeP2.isOnObject());assertFalse(nativeP2.isObjectControlled());
    }

    @Test void stationaryCageDirtyNativeP2CaptureUsesTheAliasedObjectStatusBit() {
        for(String mainCode:List.of("sonic","tails","knuckles")){
            TestSprite main=new TestSprite(mainCode),nativeP2=new TestSprite("native_p2");
            for(TestSprite player:List.of(main,nativeP2)){
                player.setCentreX((short)0x0F48);player.setCentreY((short)0x1000);
                player.setXSpeed((short)0x400);player.setGSpeed((short)0x400);
            }
            var cage=new FbzWireCageStationaryObjectInstance(
                    new ObjectSpawn(0x1000,0x1000,0x70,1,0,true,1));
            cage.setServices(new PlayersServices(main,List.of(nativeP2)));

            cage.update(0,null); // P1 mapping $00->$49 dirties d6 before P2 capture

            assertTrue(nativeP2.isOnObject(),mainCode+" P1 still lets an uncontrolled P2 enter");
            assertTrue(nativeP2.isObjectControlled());
            assertTrue(cage.heldByParticipant(1),
                    mainCode+" aggregate ownership includes the dirty object-status alias bit 1");
            assertFalse(cage.normalStandingForParticipant(1));assertTrue(cage.nativeP2AliasStanding());

            nativeP2.setCentreX((short)0x0F68);nativeP2.setCentreY((short)0x1010);
            cage.update(1,null); // unchanged P1 mapping selects unset bit 4
            assertEquals(0x1010,nativeP2.getCentreY()&0xFFFF,
                    mainCode+" clean frame must not consume the dirty-capture alias");
            assertEquals(0x49,nativeP2.getMappingFrame());

            main.setMappingFrame(0);nativeP2.setCentreX((short)0x1000);nativeP2.setCentreY((short)0x1000);
            nativeP2.setXSpeed((short)0x0E01);nativeP2.setGSpeed((short)0x0E01);
            cage.update(2,null); // changed P1 mapping selects the still-set alias bit 1
            assertEquals(0x1011,nativeP2.getCentreX()&0xFFFF);
            assertEquals(0x1003,nativeP2.getCentreY()&0xFFFF);
            assertEquals(0x70,nativeP2.getMappingFrame());

            main.setMappingFrame(0);nativeP2.setGSpeed((short)0x200);
            cage.update(3,null); // dirty selected-bit release clears alias bit 1
            assertFalse(nativeP2.isOnObject());assertFalse(nativeP2.isObjectControlled());
            assertFalse(cage.nativeP2AliasStanding());assertFalse(cage.heldByParticipant(1));
        }
    }

    @Test void stationaryCageRewindAndSelectedReleasePreserveIndependentNativeP2StandingBits() {
        TestSprite main=new TestSprite("sonic"),nativeP2=new TestSprite("tails");
        for(TestSprite player:List.of(main,nativeP2)){
            player.setCentreX((short)0x0F48);player.setCentreY((short)0x1000);
            player.setXSpeed((short)0x400);player.setGSpeed((short)0x400);
        }
        var cage=new FbzWireCageStationaryObjectInstance(
                new ObjectSpawn(0x1000,0x1000,0x70,1,0,true,1));
        cage.setServices(new PlayersServices(main,List.of(nativeP2)));

        cage.update(0,null); // dirty P1 mapping: capture through alias bit 1
        assertTrue(cage.nativeP2AliasStanding());assertFalse(cage.normalStandingForParticipant(1));

        ObjectControlState.none().applyTo(nativeP2);nativeP2.setOnObject(false);
        nativeP2.setCentreX((short)0x0F48);nativeP2.setCentreY((short)0x1000);
        nativeP2.setXSpeed((short)0x400);nativeP2.setGSpeed((short)0x400);nativeP2.setAir(false);
        cage.update(1,null); // unchanged P1 mapping: capture through normal bit 4
        assertTrue(cage.nativeP2AliasStanding());assertTrue(cage.normalStandingForParticipant(1));
        PerObjectRewindSnapshot bothBits=cage.captureRewindState();

        nativeP2.setGSpeed((short)0x200);
        cage.update(2,null); // clean selection clears bit 4 only
        assertTrue(cage.nativeP2AliasStanding());assertFalse(cage.normalStandingForParticipant(1));
        assertTrue(cage.heldByParticipant(1),"alias bit keeps aggregate native P2 ownership alive");

        cage.restoreRewindState(bothBits);
        assertTrue(cage.nativeP2AliasStanding());assertTrue(cage.normalStandingForParticipant(1));
        cage.clearStandingOwner(nativeP2);
        assertFalse(cage.nativeP2AliasStanding());assertFalse(cage.normalStandingForParticipant(1));
        assertFalse(cage.heldByParticipant(1),"full ownership transfer clears both native object-status bits");
    }

    @Test void stationaryCagePublishesAndPreservesItsPersistentInteractOnRelease() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)(0x1000-0xB8));p.setCentreY((short)0x800);p.setXSpeed((short)0x400);p.setGSpeed((short)0x400);
        var cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));cage.setSlotIndex(23);cage.setServices(new PlayersServices(p,List.of()));

        cage.update(0,null);

        assertSame(cage,p.getLatchedSolidObjectInstance(),
                "RideObject_SetRide publishes the stationary cage as the on-object owner");
        assertEquals(23,p.getInteractSlotIndex());

        p.setGSpeed((short)0x200);cage.update(1,null);

        assertSame(cage,p.getLatchedSolidObjectInstance(),
                "loc_3A36E clears Status_OnObj and control, not persistent interact(a0)");
        assertEquals(23,p.getInteractSlotIndex());
    }

    @Test void stationaryCageForcesTheNativeRideObjectTouchFloorReset() {
        // loc_3A2F0 sets Status_InAir immediately before RideObject_SetRide,
        // deliberately forcing Player_TouchFloor even though the entry gate
        // accepted only a grounded player (sonic3k.asm:77949-77955).
        TestSprite p=new TestSprite("sonic");
        p.setCentreX((short)(0x1000-0xB8));p.setCentreY((short)0x800);
        p.setXSpeed((short)0x600);p.setYSpeed((short)0x500);p.setGSpeed((short)0x400);
        p.setAngle((byte)0x40);p.setRolling(true);p.applyRollingRadii(false);
        p.setPushing(true);p.setRollingJump(true);p.setJumping(true);p.setDoubleJumpFlag(1);
        p.setFlipAngle(0x55);p.setFlipType(0x80);p.setFlipsRemaining(3);
        var cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));
        cage.setServices(new PlayersServices(p,List.of()));

        cage.update(0,null);

        assertTrue(cage.heldByParticipant(0));assertTrue(p.isOnObject());assertFalse(p.getAir());
        // loc_3A426 follows the reset in the same call and seats the player on
        // the first stationary-cage height sample ($800+$04).
        assertEquals(0,p.getAngle()&0xFF);assertEquals(0,p.getYSpeed());assertEquals(0x600,p.getGSpeed());assertEquals(0x804,p.getCentreY());
        assertFalse(p.getRolling());assertEquals(p.getStandXRadius(),p.getXRadius());assertEquals(p.getStandYRadius(),p.getYRadius());
        assertFalse(p.getPushing());assertFalse(p.getRollingJump());assertFalse(p.isJumping());assertEquals(0,p.getDoubleJumpFlag());
        assertEquals(0,p.getFlipAngle());assertEquals(0,p.getFlipType());assertEquals(0,p.getFlipsRemaining());
    }

    @Test void stationaryCageImmediatelyReleasesAnAirborneBubbleBounceEvenWhileOnObjectWasSet() {
        // Player_TouchFloor can call BubbleShield_Bounce before loc_3A314.
        // Native loc_3A31C then branches on Status_InAir alone and releases
        // through loc_3A36E (sonic3k.asm:77963-78006).
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        Sonic p=new Sonic("sonic",(short)0,(short)0);
        p.setCentreX((short)(0x1000-0xB8));p.setCentreY((short)0x800);
        p.setXSpeed((short)0x600);p.setGSpeed((short)0x400);p.setDoubleJumpFlag(1);p.giveShield(ShieldType.BUBBLE);
        var cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));cage.setServices(new PlayersServices(p,List.of()));

        cage.update(0,null);

        assertFalse(cage.heldByParticipant(0));assertTrue(p.getAir());assertFalse(p.isOnObject());assertFalse(p.isObjectControlled());
        assertEquals(-0x780,p.getYSpeed(),"BubbleShield_Bounce survives the cage's native slow-release path");
    }

    @Test void stationaryCageFloorResetPreservesHurtRoutineUntilTheNextPlayerUpdate() {
        // Player_TouchFloor does not clear routine 4. The engine's ordinary
        // setAir(false) does, so object landings while hurt use the dedicated
        // delayed HurtStop transition.
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)(0x1000-0xB8));p.setCentreY((short)0x800);
        p.setXSpeed((short)0x600);p.setGSpeed((short)0x400);p.setHurt(true);
        var cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));cage.setServices(new PlayersServices(p,List.of()));

        cage.update(0,null);

        assertTrue(cage.heldByParticipant(0));assertFalse(p.getAir());assertTrue(p.isHurt(),
                "RideObject_SetRide/Player_TouchFloor leaves HurtStop ownership to the next player slot");
    }

    @Test void participantStateScalesPastSixteenWithoutAliasingDuplicateSidekicks() {
        java.util.ArrayList<TestSprite> all=new java.util.ArrayList<>();for(int i=0;i<20;i++){TestSprite p=new TestSprite("sonic");p.setCentreX((short)(0x1000-0x70));p.setCentreY((short)0x800);all.add(p);}
        var cage=new FbzWireCageObjectInstance(spawn(0x6F,0x98));cage.setServices(new PlayersServices(all.getFirst(),all.subList(1,all.size())));cage.update(0,null);
        for(int i=0;i<20;i++)assertTrue(cage.heldByParticipant(i),"identity slot "+i);
        all.get(17).setCentreY((short)0xA00);cage.update(1,null);assertFalse(cage.heldByParticipant(17));assertTrue(cage.heldByParticipant(18));
    }

    @Test void verticalCageRejectsBroadCentreAndHorizontalCageUsesLandingAndAirReleaseRules() {
        TestSprite centre=new TestSprite("sonic");centre.setCentreX((short)0x1000);centre.setCentreY((short)0x800);var vertical=new FbzWireCageObjectInstance(spawn(0x6F,0x98));vertical.setServices(new PlayersServices(centre,List.of()));vertical.update(0,null);assertFalse(vertical.heldByParticipant(0),"vertical capture is edge bands only");
        TestSprite rider=new TestSprite("sonic");rider.setCentreX((short)0x1000);rider.setCentreY((short)0x826);var horizontal=new FbzWireCageObjectInstance(spawn(0x6F,0x10));horizontal.setServices(new PlayersServices(rider,List.of()));horizontal.update(0,null);assertTrue(horizontal.heldByParticipant(0));assertTrue(rider.isOnObject());assertFalse(rider.getAir());assertEquals(0x828,rider.getCentreY());
        horizontal.update(1,null);assertEquals(0x828,rider.getCentreY());assertEquals(4,horizontal.angleForParticipant(0));rider.setAir(true);rider.setYSpeed((short)0x400);horizontal.update(2,null);assertFalse(horizontal.heldByParticipant(0));assertEquals(0x200,rider.getYSpeed());assertEquals(0,rider.getFlipType());
    }

    @Test void horizontalCageLandingAppliesNativeRideObjectReset() {
        // Obj_FBZWireCage runs after the player slot. At this crossing the
        // player has already reached the cage surface and RideObject_SetRide
        // clears y_vel while copying x_vel to ground_vel
        // (sonic3k.asm:77634-77655).
        TestSprite rider=new TestSprite("sonic");
        rider.setCentreX((short)0x1068);
        rider.setCentreY((short)0x826);
        rider.setXSpeed((short)-0x600);
        rider.setYSpeed((short)0x500);
        rider.setGSpeed((short)-0x4DD);
        rider.setAir(true);
        var horizontal=new FbzWireCageObjectInstance(spawn(0x6F,0x20));
        horizontal.setServices(new PlayersServices(rider,List.of()));

        horizontal.update(0,null);

        assertTrue(horizontal.heldByParticipant(0));
        assertTrue(rider.isOnObject());
        assertFalse(rider.getAir());
        assertEquals(0,rider.getYSpeed());
        assertEquals(-0x600,rider.getGSpeed());
    }

    @Test void horizontalCageLandingAppliesNativeRollingRadiusDelta() {
        // At f10499 of the complete-run reference, Sonic crosses the moving
        // cage surface while rolling.  RideObject_SetRide reaches
        // Sonic_ResetOnFloor, which moves y_pos by the current-to-standing
        // y_radius delta before installing the standing radii.  The sprite's
        // top-left-backed dimensions must not invert that native adjustment.
        TestSprite rider=new TestSprite("sonic");
        rider.setRolling(true);
        rider.setCentreX((short)0x1068);
        rider.setCentreY((short)0x82E); // surface gap -4 with rolling y_radius $0E
        rider.setSubpixelRaw(0,0x4900);
        rider.setAir(true);
        var horizontal=new FbzWireCageObjectInstance(spawn(0x6F,0x20));
        horizontal.setServices(new PlayersServices(rider,List.of()));

        horizontal.update(0,null);

        assertTrue(horizontal.heldByParticipant(0));
        assertFalse(rider.getRolling());
        assertEquals(rider.getStandYRadius(),rider.getYRadius());
        assertEquals(0x828,rider.getCentreY(),
                "native y_pos = post-gap $82D plus rolling-to-standing radius delta -5");
        assertEquals(0x4900,rider.getYSubpixelRaw());
    }

    @Test void horizontalCageRequiresNegativeSurfaceGapLikeNativeUnsignedBounds() {
        // sub_39F7E first rejects positive d0 with BHI, then compares d0 to
        // -$10 with BLO.  Together those unsigned branches accept only
        // -$10..-$01: an exactly flush d0=0 crossing waits one more frame.
        TestSprite rider=new TestSprite("sonic");
        rider.setCentreX((short)0x1068);
        rider.setCentreY((short)0x825); // $83C-($13+$04): exact d0=0
        rider.setAir(true);
        var horizontal=new FbzWireCageObjectInstance(spawn(0x6F,0x20));
        horizontal.setServices(new PlayersServices(rider,List.of()));

        horizontal.update(0,null);
        assertFalse(horizontal.heldByParticipant(0),"native BLO rejects a zero surface gap");

        rider.setCentreY((short)0x826); // d0=-1, inside the native landing band
        horizontal.update(1,null);
        assertTrue(horizontal.heldByParticipant(0));
        assertEquals(0x828,rider.getCentreY());
    }

    @Test void movingCagePublishesNativeCodePointerWordForS3kSidekickCpuLatch() {
        var cage=new FbzWireCageObjectInstance(spawn(0x6F,0x20));
        RomObjectCodePointerProvider pointer=assertInstanceOf(
                RomObjectCodePointerProvider.class,cage);
        assertEquals(0x0003,pointer.romObjectCodePointerHighWord(),
                "loc_39F58/loc_3A0B0 both occupy ROM bank $0003");
        pointer=assertInstanceOf(RomObjectCodePointerProvider.class,
                new FbzWireCageStationaryObjectInstance(spawn(0x70,0x20)));
        assertEquals(0x0003,pointer.romObjectCodePointerHighWord(),
                "Obj_FBZWireCageStationary also occupies ROM bank $0003");
    }

    @Test void verticalCageWritesRomAngleMirrorsVerticalRenderFlipAndTransfersStandingOwner() {
        TestSprite rising=new TestSprite("sonic");rising.setCentreX((short)(0x1000-0x70));rising.setCentreY((short)(0x800-0x40));rising.setYSpeed((short)-0x200);
        var old=new FbzWireCageObjectInstance(spawn(0x6F,0x10));old.setSlotIndex(20);old.setServices(new PlayersServices(rising,List.of()));
        rising.setCentreY((short)0x826);old.update(0,null);assertTrue(old.heldByParticipant(0));rising.setLatchedSolidObject(0x6F,old);
        rising.setCentreY((short)(0x800-0x40));
        var vertical=new FbzWireCageObjectInstance(spawn(0x6F,0x98));vertical.setSlotIndex(21);vertical.setServices(new PlayersServices(rising,List.of()));vertical.update(1,null);
        assertEquals(0x40,rising.getAngle()&0xFF);assertTrue(rising.getRenderVFlip(),"RideObject_SetRide zeroed y_vel before the vertical cage mirrors it");assertFalse(old.heldByParticipant(0));assertSame(vertical,rising.getLatchedSolidObjectInstance());assertEquals(21,rising.getInteractSlotIndex());

        TestSprite falling=new TestSprite("tails");falling.setCentreX((short)(0x1000+0x70));falling.setCentreY((short)(0x800+0x40));falling.setYSpeed((short)0x200);
        vertical=new FbzWireCageObjectInstance(spawn(0x6F,0x98));vertical.setServices(new PlayersServices(falling,List.of()));vertical.update(0,null);
        assertEquals(0xC0,falling.getAngle()&0xFF);assertTrue(falling.getRenderVFlip());
    }

    @Test void movingCageTransferClearsOrdinarySolidStandingOwnershipForMainPlayer() {
        TestSprite main=new TestSprite("sonic");
        OrdinarySolid ordinary=new OrdinarySolid(0x0F00,0x0800);
        ObjectManager manager=managerWith(ordinary);
        manager.forceRidingObjectForBootstrap(main,ordinary);
        main.setCentreX((short)(0x1000-0x70));main.setCentreY((short)0x800);
        FbzWireCageObjectInstance cage=new FbzWireCageObjectInstance(spawn(0x6F,0x98));
        cage.setSlotIndex(21);cage.setServices(new PlayersServices(main,List.of(),manager));

        cage.update(0,null);

        assertTrue(cage.heldByParticipant(0));
        assertFalse(manager.isRidingObject(main,ordinary),
                "sub_33C34 clears the previous interact object's standing ownership before installing the cage");
        assertFalse(manager.hasObjectStandingBit(main,ordinary));
        assertSame(cage,main.getLatchedSolidObjectInstance());assertEquals(21,main.getInteractSlotIndex());
    }

    @Test void stationaryCageTransferClearsOrdinarySolidStandingOwnershipForExtraSidekick() {
        TestSprite main=new TestSprite("sonic"),extra=new TestSprite("sidekick_3");
        OrdinarySolid ordinary=new OrdinarySolid(0x0F00,0x0800);
        ObjectManager manager=managerWith(ordinary);
        manager.forceRidingObjectForBootstrap(extra,ordinary);
        main.setCentreX((short)0);main.setCentreY((short)0);
        extra.setCentreX((short)(0x1000-0xB8));extra.setCentreY((short)0x800);
        extra.setXSpeed((short)0x400);extra.setGSpeed((short)0x400);
        FbzWireCageStationaryObjectInstance cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));
        cage.setSlotIndex(24);cage.setServices(new PlayersServices(main,List.of(extra),manager));

        cage.update(0,null);

        assertTrue(cage.heldByParticipant(1));
        assertFalse(manager.isRidingObject(extra,ordinary),
                "RideObject_SetRide clears native P2 standing ownership before assigning the stationary cage");
        assertFalse(manager.hasObjectStandingBit(extra,ordinary));
        assertSame(cage,extra.getLatchedSolidObjectInstance());assertEquals(24,extra.getInteractSlotIndex());
    }

    @Test void stationaryCageClearsRollingBeforeBothReleasePathsRestoreStandingRadii() {
        TestSprite slow=new TestSprite("sonic");slow.setCentreX((short)(0x1000-0xB8));slow.setCentreY((short)0x800);slow.setXSpeed((short)0x400);slow.setGSpeed((short)0x400);
        var cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));cage.setServices(new PlayersServices(slow,List.of()));cage.update(0,null);slow.setRolling(true);slow.setGSpeed((short)0x200);cage.update(1,null);assertFalse(slow.getRolling());
        TestSprite out=new TestSprite("sonic");out.setCentreX((short)(0x1000-0xB8));out.setCentreY((short)0x800);out.setXSpeed((short)0x400);out.setGSpeed((short)0x400);
        cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));cage.setServices(new PlayersServices(out,List.of()));cage.update(0,null);out.setRolling(true);out.setCentreX((short)(0x1000+0xC1));cage.update(1,null);assertFalse(out.getRolling());
    }

    @Test void wireCageReleasePreservesPersistentInteractAndDoesNotEraseALaterTransfer() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)(0x1000-0x70));p.setCentreY((short)0x800);var cage=new FbzWireCageObjectInstance(spawn(0x6F,0x98));cage.setSlotIndex(22);cage.setServices(new PlayersServices(p,List.of()));cage.update(0,null);p.setLatchedSolidObject(0x6F,cage);p.setCentreY((short)0xA00);cage.update(1,null);
        assertSame(cage,p.getLatchedSolidObjectInstance());assertEquals(22,p.getInteractSlotIndex());assertFalse(p.isOnObject());
        var other=new FbzWireCageObjectInstance(spawn(0x6F,0x98));p.setLatchedSolidObject(0x6F,other);cage.update(2,null);assertSame(other,p.getLatchedSolidObjectInstance(),"stale cage cleanup must not erase a transferred owner");
    }

    @Test void openingFbz2VerticalCageCapturesAtNativeRightEdgeAndReleasesPastNativeRange() {
        ObjectSpawn openingSpawn = new ObjectSpawn(0x0480, 0x04C0, 0x6F, 0x98, 0, true, 1);
        TestSprite player = new TestSprite("sonic");
        player.setCentreX((short) 0x04F0);
        player.setCentreY((short) 0x0550);
        player.setXSpeed((short) 0x300);
        player.setYSpeed((short) -0x200);
        player.setAir(true);
        FbzWireCageObjectInstance cage = new FbzWireCageObjectInstance(openingSpawn);
        cage.setSlotIndex(41);
        cage.setServices(new PlayersServices(player, List.of()));

        cage.update(0, null);

        assertTrue(cage.heldByParticipant(0));
        assertTrue(player.isOnObject());
        assertFalse(player.getAir());
        assertTrue(player.isObjectControlled());
        assertTrue(player.isObjectControlAllowsCpu());
        assertFalse(player.isObjectControlSuppressesMovement(),
                "Obj_FBZWireCage writes object_control bits 6+1 ($42), not movement-suppression bit 0");
        assertTrue(player.isSuppressGroundWallCollision(),
                "Obj_FBZWireCage object_control bit 6 bypasses Sonic_Move CalcRoomInFront");
        assertEquals(0x04, cage.angleForParticipant(0));
        assertEquals(0x04E8, player.getCentreX() & 0xFFFF);

        player.setCentreY((short) 0x0590);
        cage.update(1, null);

        assertFalse(cage.heldByParticipant(0));
        assertFalse(player.isOnObject());
        assertTrue(player.getAir());
        assertFalse(player.isObjectControlled());
        assertFalse(player.isSuppressGroundWallCollision(),
                "wire-cage release restores normal ground-wall collision");
    }

    private static final class PlayersServices extends TestObjectServices {
        private final ObjectPlayerQuery query;
        private final ObjectManager objectManager;
        PlayersServices(PlayableEntity main,List<? extends PlayableEntity> sidekicks){this(main,sidekicks,null);}
        PlayersServices(PlayableEntity main,List<? extends PlayableEntity> sidekicks,ObjectManager objectManager){query=new ObjectPlayerQuery(()->main,()->sidekicks);this.objectManager=objectManager;}
        @Override public ObjectPlayerQuery playerQuery(){return query;}
        @Override public ObjectManager objectManager(){return objectManager;}
    }
    private static final class TestSprite extends AbstractPlayableSprite {
        TestSprite(String code){super(code,(short)0,(short)0);}
        @Override public void draw(){} @Override public void defineSpeeds(){} @Override protected void createSensorLines(){}
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 1);
    }

    private static ObjectManager managerWith(ObjectInstance object) {
        ObjectRegistry registry=new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn ignored){return object;}
            @Override public void reportCoverage(List<ObjectSpawn> ignored){}
            @Override public String getPrimaryName(int objectId){return "OrdinarySolid";}
        };
        ObjectManager manager=new ObjectManager(List.of(),registry,0,null,null);
        manager.reset(0);manager.addDynamicObject(object);return manager;
    }

    private static final class OrdinarySolid extends AbstractObjectInstance implements SolidObjectProvider {
        OrdinarySolid(int x,int y){super(new ObjectSpawn(x,y,0x71,0,0,true,1),"OrdinarySolid");}
        @Override public SolidObjectParams getSolidParams(){return new SolidObjectParams(0x20,8,8);}
        @Override public void update(int vIntRunCount,PlayableEntity player){}
        @Override public void appendRenderCommands(List<GLCommand> commands){}
    }
}
