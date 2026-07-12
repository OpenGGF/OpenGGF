package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestFbzWireCages {
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
        for(TestSprite p:List.of(main,sidekick,extra)){p.setCentreX((short)(0x1000-0xB8));p.setCentreY((short)0x800);p.setXSpeed((short)1);}
        main.setGSpeed((short)0x400);sidekick.setGSpeed((short)0x500);extra.setGSpeed((short)0x600);
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
        p.setGSpeed((short)0x400);cage.update(1,null);assertTrue(p.isObjectControlled(),"-$B0..-$C0 grounded entry captures");assertFalse(p.getAir());
        assertTrue(p.isObjectControlAllowsCpu());assertTrue(p.isObjectControlSuppressesMovement());
        p.setGSpeed((short)0x200);cage.update(2,null);assertFalse(p.isObjectControlled(),"speed below $400 releases and restores control");assertFalse(p.isOnObject());
    }

    @Test void participantStateScalesPastSixteenWithoutAliasingDuplicateSidekicks() {
        java.util.ArrayList<TestSprite> all=new java.util.ArrayList<>();for(int i=0;i<20;i++){TestSprite p=new TestSprite("sonic");p.setCentreX((short)(0x1000-0x70));p.setCentreY((short)0x800);all.add(p);}
        var cage=new FbzWireCageObjectInstance(spawn(0x6F,0x98));cage.setServices(new PlayersServices(all.getFirst(),all.subList(1,all.size())));cage.update(0,null);
        for(int i=0;i<20;i++)assertTrue(cage.heldByParticipant(i),"identity slot "+i);
        all.get(17).setCentreY((short)0xA00);cage.update(1,null);assertFalse(cage.heldByParticipant(17));assertTrue(cage.heldByParticipant(18));
    }

    @Test void verticalCageRejectsBroadCentreAndHorizontalCageUsesLandingAndAirReleaseRules() {
        TestSprite centre=new TestSprite("sonic");centre.setCentreX((short)0x1000);centre.setCentreY((short)0x800);var vertical=new FbzWireCageObjectInstance(spawn(0x6F,0x98));vertical.setServices(new PlayersServices(centre,List.of()));vertical.update(0,null);assertFalse(vertical.heldByParticipant(0),"vertical capture is edge bands only");
        TestSprite rider=new TestSprite("sonic");rider.setCentreX((short)0x1000);rider.setCentreY((short)0x825);var horizontal=new FbzWireCageObjectInstance(spawn(0x6F,0x10));horizontal.setServices(new PlayersServices(rider,List.of()));horizontal.update(0,null);assertTrue(horizontal.heldByParticipant(0));assertTrue(rider.isOnObject());assertFalse(rider.getAir());assertEquals(0x828,rider.getCentreY());
        horizontal.update(1,null);assertEquals(0x828,rider.getCentreY());assertEquals(4,horizontal.angleForParticipant(0));rider.setAir(true);rider.setYSpeed((short)0x400);horizontal.update(2,null);assertFalse(horizontal.heldByParticipant(0));assertEquals(0x200,rider.getYSpeed());assertEquals(0,rider.getFlipType());
    }

    @Test void verticalCageWritesRomAngleMirrorsVerticalRenderFlipAndTransfersStandingOwner() {
        TestSprite rising=new TestSprite("sonic");rising.setCentreX((short)(0x1000-0x70));rising.setCentreY((short)(0x800-0x40));rising.setYSpeed((short)-0x200);
        var old=new FbzWireCageObjectInstance(spawn(0x6F,0x10));old.setSlotIndex(20);old.setServices(new PlayersServices(rising,List.of()));
        rising.setCentreY((short)0x825);old.update(0,null);assertTrue(old.heldByParticipant(0));rising.setLatchedSolidObject(0x6F,old);
        rising.setCentreY((short)(0x800-0x40));
        var vertical=new FbzWireCageObjectInstance(spawn(0x6F,0x98));vertical.setSlotIndex(21);vertical.setServices(new PlayersServices(rising,List.of()));vertical.update(1,null);
        assertEquals(0x40,rising.getAngle()&0xFF);assertFalse(rising.getRenderVFlip());assertFalse(old.heldByParticipant(0));assertSame(vertical,rising.getLatchedSolidObjectInstance());assertEquals(21,rising.getInteractSlotIndex());

        TestSprite falling=new TestSprite("tails");falling.setCentreX((short)(0x1000+0x70));falling.setCentreY((short)(0x800+0x40));falling.setYSpeed((short)0x200);
        vertical=new FbzWireCageObjectInstance(spawn(0x6F,0x98));vertical.setServices(new PlayersServices(falling,List.of()));vertical.update(0,null);
        assertEquals(0xC0,falling.getAngle()&0xFF);assertTrue(falling.getRenderVFlip());
    }

    @Test void stationaryCageClearsRollingBeforeBothReleasePathsRestoreStandingRadii() {
        TestSprite slow=new TestSprite("sonic");slow.setCentreX((short)(0x1000-0xB8));slow.setCentreY((short)0x800);slow.setGSpeed((short)0x400);
        var cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));cage.setServices(new PlayersServices(slow,List.of()));cage.update(0,null);slow.setRolling(true);slow.setGSpeed((short)0x200);cage.update(1,null);assertFalse(slow.getRolling());
        TestSprite out=new TestSprite("sonic");out.setCentreX((short)(0x1000-0xB8));out.setCentreY((short)0x800);out.setGSpeed((short)0x400);
        cage=new FbzWireCageStationaryObjectInstance(spawn(0x70,1));cage.setServices(new PlayersServices(out,List.of()));cage.update(0,null);out.setRolling(true);out.setCentreX((short)(0x1000+0xC1));cage.update(1,null);assertFalse(out.getRolling());
    }

    @Test void wireCageReleaseClearsOnlyItsOwnLatchedInteractionBeforeUnload() {
        TestSprite p=new TestSprite("sonic");p.setCentreX((short)(0x1000-0x70));p.setCentreY((short)0x800);var cage=new FbzWireCageObjectInstance(spawn(0x6F,0x98));cage.setSlotIndex(22);cage.setServices(new PlayersServices(p,List.of()));cage.update(0,null);p.setLatchedSolidObject(0x6F,cage);p.setCentreY((short)0xA00);cage.update(1,null);
        assertNull(p.getLatchedSolidObjectInstance());assertEquals(0,p.getInteractSlotIndex());assertFalse(p.isOnObject());
        var other=new FbzWireCageObjectInstance(spawn(0x6F,0x98));p.setLatchedSolidObject(0x6F,other);cage.update(2,null);assertSame(other,p.getLatchedSolidObjectInstance(),"stale cage cleanup must not erase a transferred owner");
    }

    private static final class PlayersServices extends TestObjectServices {
        private final ObjectPlayerQuery query;
        PlayersServices(PlayableEntity main,List<? extends PlayableEntity> sidekicks){query=new ObjectPlayerQuery(()->main,()->sidekicks);}
        @Override public ObjectPlayerQuery playerQuery(){return query;}
    }
    private static final class TestSprite extends AbstractPlayableSprite {
        TestSprite(String code){super(code,(short)0,(short)0);}
        @Override public void draw(){} @Override public void defineSpeeds(){} @Override protected void createSensorLines(){}
    }

    private static ObjectSpawn spawn(int id, int subtype) {
        return new ObjectSpawn(0x1000, 0x800, id, subtype, 0, true, 1);
    }
}
