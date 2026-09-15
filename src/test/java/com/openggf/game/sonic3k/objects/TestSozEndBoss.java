package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.runtime.SozZoneRuntimeState;
import com.openggf.game.solid.*;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozEndBoss {
    private Rom rom;
    private ObjectManager manager;
    private SozZoneRuntimeState state;
    private TestablePlayableSprite player;
    private ObjectSolidExecutionContext contacts;
    private StubObjectServices services;
    private int requestedZone=-1,requestedAct=-1;
    private boolean deactivateRequested;
    @BeforeEach void setup() throws Exception {
        TestEnvironment.activeGameplayMode();rom=new Rom();assertTrue(rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()));
        state=new SozZoneRuntimeState(1,PlayerCharacter.SONIC_ALONE);
        player=new TestablePlayableSprite("sonic",(short)0,(short)0);player.setCentreX((short)0x5100);player.setCentreY((short)0x600);
        var gameState=new GameStateManager();var camera=new Camera();camera.setX((short)0x5100);camera.setY((short)0x600);
        contacts=mock(ObjectSolidExecutionContext.class);
        when(contacts.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(null,Map.of()));
        services=new StubObjectServices(){
            @Override public Rom rom(){return rom;}
            @Override public void requestZoneAndAct(int zone,int act,boolean deactivate){requestedZone=zone;requestedAct=act;deactivateRequested=deactivate;}
            @Override public com.openggf.level.resources.KosinskiModuleQueue kosinskiModuleQueue(){return null;}
            @Override public ObjectManager objectManager(){return manager;}
            @Override public SozZoneRuntimeState zoneRuntimeState(){return state;}
            @Override public GameStateManager gameState(){return gameState;}
            @Override public Camera camera(){return camera;}
            @Override public ObjectSolidExecutionContext solidExecution(){return contacts;}
        }.withPlayerQuery(new ObjectPlayerQuery(()->player,List::of));
        manager=new ObjectManager(List.of(),new Sonic3kObjectRegistry(),0,null,null,null,camera,services);manager.reset(0);
    }
    @AfterEach void close() throws Exception {rom.close();}
    private SozEndBossInstance root(){return manager.createDynamicObject(()->new SozEndBossInstance(new ObjectSpawn(0x5260,0x690,0x98,0,0,false,0)));}
    private SozEndBossChild child(int role){return manager.activeObjectsOfType(SozEndBossChild.class).stream().filter(c->integer(c,"role")==role).findFirst().orElseThrow();}
    @Test void nativeRegistryPreservesS3Half() {
        var skl=new Sonic3kObjectRegistry(){@Override protected int currentRomZoneId(){return 8;}};
        var s3=new Sonic3kObjectRegistry(){@Override protected int currentRomZoneId(){return 1;}};
        var spawn=new ObjectSpawn(0x5260,0x690,0x98,0,0,false,0);
        assertInstanceOf(SozEndBossInstance.class,skl.create(spawn));assertInstanceOf(com.openggf.game.sonic3k.objects.badniks.PoindexterBadnikInstance.class,s3.create(spawn));
    }
    @Test void steadyGraphUsesTwoSeparatePreviousLinkChainsAndRestoresOutOfPlace() {
        var boss=root();boss.update(0,player);assertEquals(13,manager.getActiveObjects().size());
        for(int role=0;role<12;role++){var c=child(role);assertSame(boss,get(c,"boss"));assertEquals(boss.getSlotIndex()+role+1,c.getSlotIndex());
            assertSame(role==7||role==8||role==10||role==11?child(role-1):null,get(c,"parent"));}
        var rewind=new RewindRegistry();rewind.register(manager.rewindSnapshottable());var snapshot=rewind.capture();
        manager.setRewindInPlaceRestoreEnabledForTest(false);manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
        rewind.restore(snapshot);var restored=manager.activeObjectsOfType(SozEndBossInstance.class).getFirst();assertNotSame(boss,restored);
        assertSame(restored,get(child(8),"boss"));assertSame(child(7),get(child(8),"parent"));
        assertTrue(RewindSnapshotDiff.diffKey("object-manager",snapshot.get("object-manager"),rewind.capture().get("object-manager")).isEmpty());
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10,11,12})
    void everySteadyAllocationPrefixIsRetainedWithoutRetry(int count) {
        var boss=root();manager.reserveAllButNFreeSlots(count);boss.update(0,player);
        assertEquals(count,manager.activeObjectsOfType(SozEndBossChild.class).size());
        var snapshot=boss.captureRewindState();boss.update(1,player);boss.restoreRewindState(snapshot);boss.update(1,player);
        assertEquals(count,manager.activeObjectsOfType(SozEndBossChild.class).size());
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8,9,10,11,12})
    void failedAllocationStopsOnlyItsOwnTableAndRecreationDoesNotHeal(int failedAttempt) {
        manager=spy(manager);var boss=root();var attempts=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call->{if(attempts.incrementAndGet()!=failedAttempt)call.callRealMethod();return null;})
                .when(manager).addDynamicObjectAfterSlot(any(),anyInt());
        boss.update(0,player);
        var roles=manager.activeObjectsOfType(SozEndBossChild.class).stream().map(c->integer(c,"role")).toList();
        var expected=new ArrayList<Integer>();
        int tableEnd=failedAttempt<=6?6:failedAttempt<=9?9:12;
        for(int role=0;role<12;role++)if(role<failedAttempt-1||role>=tableEnd)expected.add(role);
        assertEquals(expected,roles);
        var rewind=new RewindRegistry();rewind.register(manager.rewindSnapshottable());var snapshot=rewind.capture();
        manager.setRewindInPlaceRestoreEnabledForTest(false);manager.getActiveObjects().stream().toList().forEach(manager::removeDynamicObject);
        rewind.restore(snapshot);manager.activeObjectsOfType(SozEndBossInstance.class).getFirst().update(1,player);
        assertEquals(expected,manager.activeObjectsOfType(SozEndBossChild.class).stream().map(c->integer(c,"role")).toList());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void characterWalkParametersUseNativeWordsAndInitialHalfStride(boolean knuckles) {
        state=new SozZoneRuntimeState(1,knuckles?PlayerCharacter.KNUCKLES:PlayerCharacter.SONIC_ALONE);
        var boss=root();boss.update(0,player);player.setCentreX((short)0x5200);boss.update(1,player);
        for(int i=0;i<60;i++)boss.update(i,player);assertEquals(6,integer(boss,"routine"));
        assertEquals(knuckles?-128:-64,integer(boss,"xVelocity"));
        for(int i=0;i<(knuckles?32:64);i++)boss.update(i,player);
        assertEquals(0x5250,boss.getX());assertEquals(8,integer(boss,"routine"));
        assertEquals(0,integer(boss,"armAngle"));
    }
    @Test void shellContactAndPilotDamageAreSeparateOwnersWithNativeFlashAndClosure() {
        var boss=root();boss.update(0,player);boss.onPlayerAttack(player,null);assertEquals(8,boss.getCollisionProperty());
        player.setCentreX((short)0x5250);player.setCentreY((short)0x660);
        when(contacts.resolveSolidNowAll()).thenReturn(new SolidCheckpointBatch(null,Map.of(player,
                new PlayerSolidContactResult(ContactKind.SIDE,false,false,true,false,PreContactState.ZERO,PostContactState.ZERO,1))));
        child(0).update(0,player);assertEquals(0x660,state.events().bossWallHitY());assertEquals(-0x400,player.getXSpeed());
        assertEquals(8,boss.getCollisionProperty());assertEquals(0xF,boss.getCollisionFlags());
        boss.onPlayerAttack(player,null);assertEquals(7,boss.getCollisionProperty());assertEquals(0,boss.getCollisionFlags());
        boss.update(1,player);assertEquals(31,integer(boss,"flashTimer"));
        for(int i=0;i<31;i++)boss.update(i,player);assertEquals(0xF,boss.getCollisionFlags());
        state.events().bossWallHitY(0);boss.update(33,player);assertEquals(0,boss.getCollisionFlags());
        assertEquals(2,integer(boss,"routine"));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20})
    void everyParticlePrefixHasExactTerminalAndDoesNotHeal(int count) {
        var boss=root();boss.update(0,player);var charge=manager.createDynamicObject(()->new SozEndBossChild(boss,null,12));
        manager.reserveAllButNFreeSlots(count);charge.update(0,player);
        var particles=manager.activeObjectsOfType(SozEndBossChild.class).stream().filter(c->integer(c,"role")==13).toList();
        assertEquals(count,particles.size());assertSame(count<20?null:particles.getLast(),get(charge,"terminal"));
        assertEquals(count<20?manager.getObjectSlotCapacity()-1:particles.getLast().getSlotIndex(),integer(charge,"terminalSlot"));
        charge.update(1,player);assertEquals(count,manager.activeObjectsOfType(SozEndBossChild.class).stream().filter(c->integer(c,"role")==13).count());
    }
    @Test void failedParticleAllocationReadsKnownSozTerminalSlotFlag() {
        var boss=root();boss.update(0,player);
        var charge=manager.createDynamicObject(()->new SozEndBossChild(boss,null,12));
        manager.reserveAllButNFreeSlots(0);charge.update(0,player);
        assertNull(get(charge,"terminal"));
        // Model the failed allocator's terminal occupied SST when it belongs to
        // another SOZ owner; the foreign-object $38 byte remains unavailable.
        set(charge,"terminalSlot",boss.getSlotIndex());
        charge.update(1,player);assertEquals(0,integer(charge,"phase"));
        set(boss,"dismantling",true);charge.update(2,player);
        assertEquals(1,integer(charge,"phase"));
    }
    @Test void terminalAnimationPublishesBeforeDeletionAndChargeFiresOnlyAfterPublication() {
        var boss=root();boss.update(0,player);var charge=manager.createDynamicObject(()->new SozEndBossChild(boss,null,12));charge.update(0,player);
        var terminal=(SozEndBossChild)get(charge,"terminal");
        for(int i=0;i<160;i++){terminal.update(i,player);charge.update(i,player);assertEquals(0,integer(charge,"phase"));}
        for(int i=0;i<32;i++){terminal.update(i,player);charge.update(i,player);assertEquals(0,integer(charge,"phase"));}
        terminal.update(192,player);assertEquals(true,get(terminal,"retired"));assertFalse(terminal.isDestroyed());
        charge.update(192,player);assertEquals(7,integer(charge,"timer"));assertNotNull(child(14));assertNotNull(child(15));
        terminal.update(193,player);assertTrue(terminal.isDestroyed());
    }
    @Test void finalHitSignalsEventThenRetainsRootForExplosionEscapeAndCapsule() {
        var boss=root();boss.update(0,player);set(boss,"hits",1);set(boss,"timer",0);boss.openShell(player);boss.onPlayerAttack(player,null);
        boss.update(1,player);assertEquals(0x55,state.sandCorkBackgroundFlag());
        assertTrue(boss.ownsPostResultsTransition());assertEquals(1,manager.activeObjectsOfType(SozEndBossExplosion.class).size());
        for(int i=0;i<63;i++){boss.update(i,player);assertFalse(boss.dismantling());}
        boss.update(64,player);assertTrue(boss.dismantling());
        for(int i=0;i<40&&integer(boss,"escapePhase")<4;i++)boss.update(i,player);
        assertEquals(4,integer(boss,"escapePhase"));assertEquals(1,manager.activeObjectsOfType(SozEndBossEggCapsule.class).size());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void postResultsWalkRunsCharacterExitAndRequestsLrz1AtItsNativeEdge(boolean knuckles) {
        state=new SozZoneRuntimeState(1,knuckles?PlayerCharacter.KNUCKLES:PlayerCharacter.SONIC_ALONE);
        var boss=root();boss.update(0,player);set(boss,"escapePhase",6);
        player.setCentreX((short)0x5468);player.setCentreY((short)0x6E0);player.setXSpeed((short)0x600);
        boss.update(0,player);assertEquals(7,integer(boss,"escapePhase"));assertEquals(!knuckles,player.isObjectControlled());
        for(int i=0;i<60&&integer(boss,"escapePhase")==7&&!boss.isDestroyed();i++)boss.update(i,player);
        if(!knuckles){assertEquals(8,integer(boss,"escapePhase"));assertEquals(-1,requestedZone);
            for(int i=0;i<127;i++)boss.update(i,player);assertEquals(-1,requestedZone);
            var before=boss.captureRewindState();boss.update(127,player);assertTrue(boss.isDestroyed());
            boss.restoreRewindState(before);boss.update(127,player);assertTrue(boss.isDestroyed());}
        assertEquals(9,requestedZone);assertEquals(0,requestedAct);assertTrue(deactivateRequested);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void beamPrefixStopsOnFailureWithoutRetry(int count){
        var boss=root();boss.update(0,player);var charge=manager.createDynamicObject(()->new SozEndBossChild(boss,null,12));charge.update(0,player);
        set(get(charge,"terminal"),"retired",true);manager.reserveAllButNFreeSlots(count);charge.update(1,player);
        assertEquals(count,manager.activeObjectsOfType(SozEndBossChild.class).stream().filter(c->integer(c,"role")==14||integer(c,"role")==15).count());
        charge.update(2,player);assertEquals(count,manager.activeObjectsOfType(SozEndBossChild.class).stream().filter(c->integer(c,"role")==14||integer(c,"role")==15).count());
    }
    @Test void resultsAllocationFailureDoesNotDereferenceMissingSst() throws Exception {
        var capsule=manager.createDynamicObject(()->new SozEndBossEggCapsule(0x5360,0x720));
        var opened=AbstractS3kUprightEggCapsuleInstance.class.getDeclaredField("opened");
        opened.setAccessible(true);opened.setBoolean(capsule,true);
        player.setAir(false);manager.reserveAllButNFreeSlots(0);
        assertDoesNotThrow(()->capsule.update(0,player));
        assertTrue(capsule.isResultsStarted());
        assertTrue(manager.activeObjectsOfType(S3kResultsScreenObjectInstance.class).isEmpty());
    }
    static Object get(Object target,String name){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception e){throw new AssertionError(e);}}
    static int integer(Object target,String name){return (Integer)get(target,name);}
    static void set(Object target,String name,Object value){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
}
