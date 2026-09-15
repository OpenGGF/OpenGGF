package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.OscillationManager;
import com.openggf.physics.TrigLookupTable;
import com.openggf.game.rewind.identity.*;
import com.openggf.game.rewind.schema.RewindCaptureContext;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.level.objects.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozSwingAndWire {
    private Rom rom;
    private StubObjectServices services;
    private TestablePlayableSprite player;
    @BeforeEach void setup() throws Exception {
        rom=new Rom();assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
        player=new TestablePlayableSprite("sonic",(short)0,(short)0);
        player.setCentreX((short)0x400);player.setCentreY((short)0x344);
        services=new StubObjectServices(){ @Override public Rom rom(){return rom;} };
        services.withPlayerQuery(new ObjectPlayerQuery(()->player,List::of));
    }
    @AfterEach void close() throws Exception {rom.close();}
    private SozRapelWireObjectInstance wire(int subtype) {
        var w=new SozRapelWireObjectInstance(new ObjectSpawn(0x400,0x300,0x48,subtype,0,false,0));w.setServices(services);return w;
    }
    private void step(SozRapelWireObjectInstance w) {
        w.update(0,player);
        var s=(SozRapelWireObjectInstance.Segment)get(w,"first");
        while(s!=null){s.setServices(services);s.update(0,player);s=(SozRapelWireObjectInstance.Segment)get(s,"next");}
    }
    private static Object get(Object o,String name) {try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static RewindCaptureContext context(List<AbstractObjectInstance> objects) {
        var table=new RewindIdentityTable();
        for(int i=0;i<objects.size();i++)table.registerObject(objects.get(i),ObjectRefId.dynamic(i,0,i));
        return RewindCaptureContext.withIdentityTable(table);
    }
    private static int value(Object o,String name){return (Integer)get(o,name);}
    private void press(boolean held){player.setLogicalInputState(false,false,false,false,held,held);}
    @Test void lockedOnPointersAndMappingsAreBoundToTheirOwningRoutine() throws Exception {
        assertArrayEquals(new byte[]{0x21,0x7C,0,4,0x16,(byte)0xC6},rom.readBytes(0x4143C,6));
        assertArrayEquals(new byte[]{0x23,0x7C,0,4,(byte)0xB1,(byte)0xD0},rom.readBytes(0x4AA5E,6));
        assertEquals(3,S3kSpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom),0x416C6,3).size());
        assertEquals(39,S3kSpriteDataLoader.loadMappingFrames(RomByteReader.fromRom(rom),0x4B1D0,39).size());
    }
    @ParameterizedTest @ValueSource(ints={4,5,6,0x13,0x14,0x15,0x16,0x17})
    void everyPlacedPlatformSubtypeUsesItsLengthAndStandingTrigger(int subtype) {
        var w=new SozSwingingPlatformObjectInstance(new ObjectSpawn(0x400,0x300,0x43,subtype,0,false,0));w.setServices(services);
        w.update(0,player);
        assertNotNull(get(w,"display"));
        if((subtype&0xF0)!=0) {
            assertEquals(0x400-0x18,w.getX());assertEquals(0x300+16*((subtype&15)+1),w.getY());
            w.onSolidContact(player,new SolidContact(true,false,false,true,false),0);
            w.update(1,player);assertEquals(8,value(w,"velocity"));
            assertEquals(0x4008,value(w,"angle"));
            var context=context(List.of(w,(AbstractObjectInstance)get(w,"display")));
            var state=w.captureRewindState(context);
            for(int i=0;i<80;i++)w.update(i,player);
            int angle=value(w,"angle"),x=w.getX(),y=w.getY();
            w.restoreRewindState(state,context);
            for(int i=0;i<80;i++)w.update(i,player);
            assertEquals(angle,value(w,"angle"));assertEquals(x,w.getX());assertEquals(y,w.getY());
        }
    }
    @Test void sharedOscillatorAndTriggeredPauseFollowSeparateRomClocks() {
        OscillationManager.reset();
        var continuous=new SozSwingingPlatformObjectInstance(new ObjectSpawn(0x400,0x300,0x43,5,0,false,0));
        continuous.setServices(services);
        for(int i=0;i<80;i++) {
            OscillationManager.update(i);continuous.update(i,player);
            int a=(OscillationManager.getByte(0x18)+0x80)&255;
            assertEquals(0x400+((TrigLookupTable.cosHex(a)*6)>>4),continuous.getX());
            assertEquals(0x300+((TrigLookupTable.sinHex(a)*6)>>4),continuous.getY());
        }
        var triggered=new SozSwingingPlatformObjectInstance(new ObjectSpawn(0x400,0x300,0x43,0x13,0,false,0));
        triggered.setServices(services);triggered.onSolidContact(player,new SolidContact(true,false,false,true,false),0);
        for(int i=0;i<500 && value(triggered,"pause")==0;i++)triggered.update(i,player);
        assertEquals(30,value(triggered,"pause"));int a=value(triggered,"angle");
        for(int i=0;i<100;i++)triggered.update(i,player);
        assertEquals(30,value(triggered,"pause"));assertEquals(a,value(triggered,"angle"));
        triggered.onSolidContactCleared(player,0);
        for(int i=0;i<30;i++)triggered.update(i,player);
        assertEquals(0,value(triggered,"pause"));assertEquals(a,value(triggered,"angle"));
        triggered.update(0,player);assertEquals((a-8)&0xFFFF,value(triggered,"angle"));
    }
    @ParameterizedTest @ValueSource(ints={4,5,6,7,8,0x42,0x46,0x81})
    void everyPlacedWireSubtypeCapturesRatchetsAndReturnsAfterFinalStep(int subtype) {
        var w=wire(subtype);step(w);
        assertTrue(w.isPlayerHeld(player));assertEquals(0x330,w.handleY());assertEquals(0x344,player.getCentreY());
        assertEquals(0x14,player.getAnimationId());
        var child=(SozRapelWireObjectInstance.Segment)get(w,"first");int count=0;
        while(child!=null){count++;child=(SozRapelWireObjectInstance.Segment)get(child,"next");}assertEquals(17,count);
        for(int i=0;i<73;i++)step(w);
        assertEquals(0xC0,value(w,"length"));assertEquals(2,value(w,"routine"));
        int originalX=w.getX();
        for(int i=0;i<(subtype&15);i++) {
            press(true);step(w);press(false);
            for(int n=0;n<300 && value(w,"routine")!=2 && value(w,"routine")!=8;n++)step(w);
        }
        assertEquals(8,value(w,"routine"));assertEquals(2,value(w,"mode"));
        if((subtype&0xF0)!=0 && (subtype&15)%2!=0) assertNotEquals(originalX,w.getX());
        int finalLength=value(w,"length");
        int firstTail=value(w,"tailAngle");
        for(int i=0;i<128;i++) {
            step(w);
            assertEquals(8,value(w,"routine"),"loc_4AC98 retains final swing through parent3/$46");
            assertEquals(finalLength,value(w,"length"));
            assertTrue(w.isPlayerHeld(player));
        }
        assertEquals(firstTail,value(w,"tailAngle"),"byte angle completes one full cycle");
        press(true);step(w);press(false);
        assertFalse(w.isPlayerHeld(player));assertFalse(player.isObjectControlled());assertTrue(player.getAir());
        assertTrue(player.isJumping());assertTrue(player.getRolling());assertEquals(2,player.getAnimationId());
        player.setCentreX((short)0);
        for(int i=0;i<500 && value(w,"routine")!=0;i++)step(w);
        assertEquals(0,value(w,"routine"));assertEquals(0x30,value(w,"length"));
    }
    @Test void capturePreservesIncomingMappingUntilFirstHeldPass() {
        var w=wire(4);
        player.setMappingFrame(0x96);
        step(w);
        assertTrue(w.isPlayerHeld(player));
        assertEquals(0x14,player.getAnimationId());
        assertEquals(0x96,player.getMappingFrame(),"loc_4B13E does not write mapping_frame");
        step(w);
        assertEquals(0x92,player.getMappingFrame(),"loc_4B04A selects the held frame next pass");
    }
    @Test void horizontalFlipClearsAirButPreservesHigherStatusBits() {
        var w=wire(0x42);step(w);for(int i=0;i<73;i++)step(w);
        player.setAir(true);player.setRolling(true);player.setOnObject(true);
        press(true);step(w);press(false);
        assertEquals(3,value(w,"mode"));
        assertFalse(player.getAir(),"loc_4B0DE masks status with FC before writing facing");
        assertTrue(player.getRolling());assertTrue(player.isOnObject());
    }
    @Test void playerTwoCanGrabButCannotStartTheMainPlayerRatchet() {
        var p2=new TestablePlayableSprite("tails",(short)0,(short)0);
        p2.setCentreX((short)0x400);p2.setCentreY((short)0x344);player.setCentreX((short)0);
        services.withPlayerQuery(new ObjectPlayerQuery(()->player,()->List.of(p2)));
        var w=wire(5);step(w);assertTrue(w.isPlayerHeld(p2));assertFalse(w.isPlayerHeld(player));
        for(int i=0;i<80;i++)step(w);assertEquals(0x30,value(w,"length"));
    }
    @Test void cooldownExpiresOnSixtiethPassAndGrabWindowsAreUnsignedHalfOpen() {
        var w=wire(4);player.setCentreX((short)0x410);step(w);assertFalse(w.isPlayerHeld(player));
        player.setCentreX((short)0x3F0);player.setCentreY((short)0x348);step(w);assertFalse(w.isPlayerHeld(player));
        player.setCentreY((short)0x347);step(w);assertTrue(w.isPlayerHeld(player));
        player.setHurt(true);step(w);assertFalse(w.isPlayerHeld(player));player.setHurt(false);
        player.setCentreX((short)w.handleX());player.setCentreY((short)(w.handleY()+20));
        for(int i=0;i<59;i++)step(w);assertFalse(w.isPlayerHeld(player));
        step(w);assertTrue(w.isPlayerHeld(player));
    }
    @Test void graphRewindRestoresRatchetCaptureAndForwardPositions() {
        var w=wire(0x42);step(w);for(int i=0;i<73;i++)step(w);press(true);step(w);press(false);
        for(int i=0;i<20;i++)step(w);
        List<AbstractObjectInstance> objects=new ArrayList<>();objects.add(w);
        var s=(SozRapelWireObjectInstance.Segment)get(w,"first");while(s!=null){objects.add(s);s=(SozRapelWireObjectInstance.Segment)get(s,"next");}
        var context=context(objects);
        var snapshots=objects.stream().map(o->o.captureRewindState(context)).toList();
        for(int i=0;i<30;i++)step(w);
        int x=w.handleX(),y=w.handleY(),a=value(w,"angle"),len=value(w,"length");
        for(int i=0;i<objects.size();i++)objects.get(i).restoreRewindState(snapshots.get(i),context);
        for(int i=0;i<30;i++)step(w);
        assertEquals(x,w.handleX());assertEquals(y,w.handleY());assertEquals(a,value(w,"angle"));assertEquals(len,value(w,"length"));assertTrue(w.isPlayerHeld(player));
    }
}
