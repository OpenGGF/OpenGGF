package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.ShieldType;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Short encounter boundaries; the entry signal is explicit setup, not a route claim. */
@RequiresRom(SonicGame.SONIC_3K)
class TestLrzEndBossEncounterHeadless {
    private HeadlessTestFixture fixture;
    private LrzEndBossObjectInstance boss;
    private String donor;
    @AfterEach void reset() {
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        var config=com.openggf.configuration.SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE,"tails");
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
    }
    private void step() { fixture.stepFrame(false,false,false,false,false); }
    private void beforeBodyInitialization() {
        fixture=HeadlessTestFixture.builder().withZoneAndAct(22,0).withFreshLevelStartLifecycle()
                .startPosition((short)0x9C0,(short)0x368).startPositionIsCentre()
                .withCrossGameDonation(donor).build();
        boss=GameServices.level().getObjectManager().createDynamicObject(LrzEndBossObjectInstance::new);
        step();
        var runtime=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        runtime.setBackgroundRoutine(12);
        runtime.bossAct().setAutoscrollRoutine(-1);
        runtime.bossAct().publishEntryPlatformReady();
        var camera=GameServices.camera();
        camera.setX((short)0xA00); camera.setMinX((short)0xA00); camera.setMaxX((short)0xA00);
        camera.setY((short)0x560); camera.setMinY((short)0x560);
        camera.setMaxY((short)0x560); camera.setMaxYTarget((short)0x560);
        fixture.sprite().setCentreX((short)0xAA0); fixture.sprite().setCentreY((short)0x500);
        fixture.sprite().giveShield(ShieldType.FIRE);
        for(int i=0;i<122;i++) step();
        assertEquals(0,boss.hits(),"Obj_Wait callback changes code; body initializes next dispatch");
    }
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void persistentChildTableKeepsOnlyItsAllocatedPrefix(int capacity) {
        beforeBodyInitialization();
        var manager=GameServices.level().getObjectManager();
        manager.reserveAllButNFreeSlots(capacity);
        step();
        var children=manager.activeObjectsOfType(LrzEndBossChild.class);
        assertEquals(14,boss.hits()); assertEquals(capacity,children.size());
        if(capacity>0) assertEquals(0xE6,children.getFirst().getSpawn().subtype());
        if(capacity>1) assertEquals(0x0C,children.get(1).getSpawn().subtype());
        for(var child:children) {
            assertEquals(4,child.getPriorityBucket(),"forward children initialize in the same object sweep");
            manager.removeDynamicObject(child);
        }
        step();
        assertTrue(manager.activeObjectsOfType(LrzEndBossChild.class).isEmpty(),
                "the startup table does not reconstruct deleted children or retry its suffix");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void rollingPlayerTouchCannotPublishMineDamageOrShieldDeflection(boolean fireShield) {
        beforeBodyInitialization(); step(); step();
        var player=fixture.sprite(); player.setRingCount(20);
        if(fireShield) player.giveShield(ShieldType.FIRE); else player.removeShield();
        player.setInvulnerableFrames(0); player.setHurt(false);
        player.setCentreX((short)boss.getX()); player.setCentreY((short)boss.getY());
        player.setAir(true); player.setRolling(true); player.setYSpeed((short)0x100);
        GameServices.level().getObjectManager().snapshotTouchResponseState();
        GameServices.level().getObjectManager().runTouchResponsesForPlayer(player,200);
        assertEquals(14,boss.hits()); assertFalse(boss.hitPending());
        assertEquals(0,boss.getShieldReactionFlags());
        assertFalse(boss.onShieldDeflect(player));
        assertTrue(player.isHurt(),"$B8 is a hurt-only body even while the player is rolling");
    }
    @Test void floatingMineOwnsHitPublicationAndTheRootConsumesItOnItsNextDispatch() {
        beforeBodyInitialization(); step();
        var manager=GameServices.level().getObjectManager();
        var mine=manager.createDynamicObject(()->new LrzEndBossChild(boss,0x79AAE,0,-0x18));
        // Drive the real mine flight/fall/float graph, without writing the boss hit bit.
        for(int i=0;i<1000 && !boss.hitPending();i++) step();
        assertTrue(boss.hitPending(),"a floating mine must publish the scripted hit");
        assertEquals(14,boss.hits(),"the publisher runs after its parent slot");
        assertTrue(mine.isDestroyed());
        step(); assertEquals(13,boss.hits());
        assertTrue(boss.hitPending(),"flash window retains status bit 6");
    }
    private int field(Object object,String name) throws Exception {
        var field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.getInt(object);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void launchTableKeepsMineBeforePlumeAndDoesNotRetryAMissingSuffix(int capacity) throws Exception {
        beforeBodyInitialization(); step();
        for(int i=0;i<256 && !(field(boss,"routine")==4 && field(boss,"timer")==0);i++) step();
        assertEquals(4,field(boss,"routine")); assertEquals(0,field(boss,"timer"));
        var manager=GameServices.level().getObjectManager(); manager.reserveAllButNFreeSlots(capacity);
        // Isolate this allocation call from the independently recurring platform stream.
        boss.update(200,fixture.sprite());
        var launched=manager.activeObjectsOfType(LrzEndBossChild.class).stream()
                .filter(c->c.getSpawn().subtype()==0xAE || c.getSpawn().subtype()==0x7E).toList();
        assertEquals(capacity,launched.size());
        if(capacity>0) assertEquals(0xAE,launched.getFirst().getSpawn().subtype());
        if(capacity>1) assertEquals(0x7E,launched.get(1).getSpawn().subtype());
        launched.forEach(manager::removeDynamicObject);
        boss.update(201,fixture.sprite());
        assertEquals(0,manager.activeObjectsOfType(LrzEndBossChild.class).stream()
                .filter(c->c.getSpawn().subtype()==0xAE || c.getSpawn().subtype()==0x7E).count());
    }
    @ParameterizedTest @ValueSource(ints={0,1})
    void airborneMineAttemptsOneForwardTrailOnlyOnItsVintGate(int capacity) {
        beforeBodyInitialization(); step();
        var manager=GameServices.level().getObjectManager();
        var mine=manager.createDynamicObject(()->new LrzEndBossChild(boss,0x79AAE,0,-0x18));
        manager.reserveAllButNFreeSlots(capacity);
        mine.update(200,fixture.sprite());
        var trails=manager.activeObjectsOfType(LrzEndBossChild.class).stream()
                .filter(c->c.getSpawn().subtype()==0xAC).toList();
        assertEquals(capacity,trails.size());
        trails.forEach(manager::removeDynamicObject);
        mine.update(201,fixture.sprite());
        assertEquals(0,manager.activeObjectsOfType(LrzEndBossChild.class).stream()
                .filter(c->c.getSpawn().subtype()==0xAC).count());
    }

    @Test void capsuleAcceptsAGroundedMovingPlayerButNotAnAirborneOrDeadOne() {
        beforeBodyInitialization();
        var capsule=GameServices.level().getObjectManager().createDynamicObject(LrzEndBossEggCapsule::new);
        var player=fixture.sprite(); player.setAir(false); player.setXSpeed((short)0x100);
        player.setGSpeed((short)0x100);
        assertTrue(capsule.shouldStartResults(player));
        player.setAir(true); assertFalse(capsule.shouldStartResults(player));
        player.setAir(false); player.setDead(true); assertFalse(capsule.shouldStartResults(player));
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"6,3,0", "6,3,1", "0,31,0", "0,31,1"})
    void explosionConsumesBurstBudgetButRandomizesOnlyAllocatedChildren(int subtype,int bursts,int capacity) throws Exception {
        beforeBodyInitialization(); step();
        var manager=GameServices.level().getObjectManager();
        var emitter=manager.createDynamicObject(()->new LrzEndBossExplosion(boss,subtype));
        manager.reserveAllButNFreeSlots(capacity);
        var rng=GameServices.rng();
        long seed=rng.getSeed();
        emitter.update(200,fixture.sprite());
        assertEquals(bursts,field(emitter,"remaining"),"Obj_BossExpControl1 decrements before allocation");
        assertEquals(capacity,manager.activeObjectsOfType(S3kBossExplosionChild.class).size());
        if(capacity==0) assertEquals(seed,rng.getSeed(),"failed CreateChild6 does not call Random_Number");
        else assertNotEquals(seed,rng.getSeed());
        for(int clock=201;clock<=200+bursts*3;clock++) emitter.update(clock,fixture.sprite());
        assertEquals(0,field(emitter,"remaining"),"all attempted bursts, including allocation failures");
        assertFalse(emitter.isDestroyed(),"Go_Delete_Sprite changes the next dispatch");
        emitter.update(201+bursts*3,fixture.sprite());
        assertTrue(emitter.isDestroyed());
    }
    @Test void platformStreamRetriesFailedFirstAllocationAndStopsWhenDirectionClears() throws Exception {
        beforeBodyInitialization();
        var manager=GameServices.level().getObjectManager();
        var state=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
        state.setStreamDirection(0xFFFF);
        var stream=manager.createDynamicObject(LrzEndBossPlatformStream::new);
        var blocker=manager.createDynamicObject(LrzEndBossPaletteRestore::new);
        int freedSlot=blocker.getSlotIndex();
        manager.reserveAllButNFreeSlots(0);
        stream.update(200,fixture.sprite());
        assertEquals(-1,field(stream,"lastSlot"));
        manager.removeDynamicObject(blocker);
        stream.update(201,fixture.sprite());
        assertEquals(freedSlot,field(stream,"lastSlot"),"AllocateObject retries the first available slot");
        var platform=manager.activeObjectsOfType(LrzBossPlatformObjectInstance.class).stream()
                .filter(p->p.getSlotIndex()==freedSlot).findFirst().orElseThrow();
        assertEquals(0x9E0,platform.getX());
        state.setStreamDirection(0xFF00);
        stream.update(202,fixture.sprite());
        assertTrue(stream.isDestroyed()); assertFalse(platform.isDestroyed());
    }
    @Test void platformStreamReadsTheReusedSlotInsteadOfItsDeletedChildReference() throws Exception {
        beforeBodyInitialization();
        var manager=GameServices.level().getObjectManager();
        var state=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
        state.setStreamDirection(1);
        var stream=manager.createDynamicObject(LrzEndBossPlatformStream::new);
        stream.update(200,fixture.sprite());
        int originalSlot=field(stream,"lastSlot");
        var original=manager.activeObjectsOfType(LrzBossPlatformObjectInstance.class).stream()
                .filter(p->p.getSlotIndex()==originalSlot).findFirst().orElseThrow();
        manager.removeDynamicObject(original);
        var replacement=manager.createDynamicObject(()->LrzBossPlatformObjectInstance.floating(0xB60));
        assertEquals(originalSlot,replacement.getSlotIndex());
        int count=manager.activeObjectsOfType(LrzBossPlatformObjectInstance.class).size();
        stream.update(201,fixture.sprite());
        assertEquals(count,manager.activeObjectsOfType(LrzBossPlatformObjectInstance.class).size(),
                "replacement slot X is within $80 even though the original child has been removed");
        manager.removeDynamicObject(replacement);
        stream.update(202,fixture.sprite());
        assertEquals(count,manager.activeObjectsOfType(LrzBossPlatformObjectInstance.class).size(),
                "cleared slot has X=0, so the next update allocates a new platform");
    }
    @Test void capsulePaletteScriptWaitsForPublicationAndPausesDuringOtherFades() throws Exception {
        beforeBodyInitialization();
        var state=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
        var palette=GameServices.level().getObjectManager().createDynamicObject(LrzEndBossPaletteRestore::new);
        palette.update(200,fixture.sprite());
        assertEquals(0,field(palette,"cursor"));
        state.setCapsuleOpened(true);
        var registry=GameServices.paletteOwnershipRegistry(); registry.setPaletteRotationDisabled(true);
        palette.update(201,fixture.sprite());
        int first=field(palette,"cursor");
        assertEquals(0x78EB8,first,"word_78EAA starts after the four-byte script header");
        assertEquals(0,field(palette,"delay"));
        assertEquals(0x7FFF,state.consumePrimaryPaletteTimerWrite());
        registry.setPaletteRotationDisabled(false);
        palette.update(202,fixture.sprite());
        assertEquals(first+12,field(palette,"cursor"),"five colour words then one delay word");
        assertEquals(3,field(palette,"delay"),"palscriptdata 4 emits the delay word 4-1");
        registry.setPaletteRotationDisabled(true);
        for(int clock=203;clock<210;clock++) palette.update(clock,fixture.sprite());
        assertEquals(3,field(palette,"delay"));
        registry.setPaletteRotationDisabled(false);
        for(int clock=210;clock<213;clock++) palette.update(clock,fixture.sprite());
        assertEquals(first+12,field(palette,"cursor"));
        palette.update(213,fixture.sprite());
        assertEquals(first+24,field(palette,"cursor"),"encoded delay 3 writes on the fourth enabled dispatch");
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> encounterConfigurations() {
        return java.util.stream.IntStream.of(320,352,400,528,800).boxed().flatMap(width ->
                java.util.stream.Stream.of(new String[]{"off","sonic","tails"},
                        new String[]{"off","sonic",""}, new String[]{"off","tails",""},
                        new String[]{"s1","sonic",""}, new String[]{"s2","sonic","tails"})
                        .map(roster -> org.junit.jupiter.params.provider.Arguments.of(width,roster[0],roster[1],roster[2])));
    }
    @ParameterizedTest(name="boss completion {0}px {1} {2}+{3}")
    @org.junit.jupiter.params.provider.MethodSource("encounterConfigurations")
    void realMineFightCapsuleAndResultsPublishTheHiddenPalaceTransition(int width,String donorCode,
                                                                       String main,String side) throws Exception {
        var config=com.openggf.configuration.SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE,main);
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE,side);
        var aspect=java.util.Arrays.stream(com.openggf.configuration.WidescreenAspect.values())
                .filter(a->a.pixelWidth()==width).findFirst().orElseThrow();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT,aspect.name());
        config.resolveDisplayAspect(); donor=donorCode.equals("off")?null:donorCode;
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        beforeBodyInitialization(); step();
        assertEquals(width,GameServices.camera().getWidth());
        assertEquals(main,fixture.sprite().getCode());
        assertEquals(donor!=null,com.openggf.game.CrossGameFeatureProvider.isActive());
        var manager=GameServices.level().getObjectManager();
        boolean sawDefeat=false, sawCapsule=false, sawOpened=false, sawResults=false, sawRelease=false;
        boolean rewoundPeak=false, rewoundDefeat=false, rewoundOpened=false, rewoundResults=false;
        int peak=0;
        for(int frame=0;frame<8000 && GameServices.level().getCurrentAct()==0;frame++) {
            var player=fixture.sprite(); var runtime=S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().bossAct();
            var capsules=manager.activeObjectsOfType(LrzEndBossEggCapsule.class);
            boolean capsule=!capsules.isEmpty(); int target=capsule?capsules.getFirst().getX():0xAA0;
            int predicted=player.getCentreX()+player.getXSpeed()*8/256;
            boolean left=predicted>target+4, right=predicted<target-4, imminent=false;
            for(var child:manager.activeObjectsOfType(LrzEndBossChild.class))
                if(field(child,"code")==0x79B54 && Math.abs(child.getX()-player.getCentreX())<55) imminent=true;
            boolean jump=!runtime.capsuleOpened() && ((!player.getAir() && (imminent || capsule))
                    || (player.getAir() && player.getYSpeed()<0));
            if(GameServices.gameState().isEndOfLevelFlag()) { left=false; right=true; jump=!player.getAir() || player.getYSpeed()<0; }
            sawDefeat|=boss.defeated(); sawCapsule|=capsule; sawOpened|=runtime.capsuleOpened();
            sawResults|=!manager.activeObjectsOfType(LrzEndBossEggCapsule.Results.class).isEmpty();
            sawRelease|=field(boss,"entry")==6;
            int graphCount=manager.activeObjectsOfType(LrzEndBossChild.class).size()+1;
            peak=Math.max(peak,graphCount);
            boolean rewind=false;
            if(graphCount==13 && !rewoundPeak) { rewind=true; rewoundPeak=true; }
            else if(boss.defeated() && !rewoundDefeat) { rewind=true; rewoundDefeat=true; }
            else if(runtime.capsuleOpened() && !rewoundOpened) { rewind=true; rewoundOpened=true; }
            else if(sawResults && !rewoundResults) { rewind=true; rewoundResults=true; }
            if(rewind) roundTripEncounter(left,right,jump);
            else fixture.stepFrame(false,false,left,right,jump);
            assertFalse(player.getDead(),"authored checkpoint encounter died at step "+frame);
        }
        assertTrue(sawDefeat && sawCapsule && sawOpened && sawResults && sawRelease,
                "real publication chain: defeat="+sawDefeat+" capsule="+sawCapsule+" opened="+sawOpened+" results="+sawResults+" release="+sawRelease);
        assertEquals(13,peak,"root, crest/pilot, three mines, plume and six trails on this reachable path");
        assertTrue(rewoundPeak && rewoundDefeat && rewoundOpened && rewoundResults);
        assertEquals(22,GameServices.level().getCurrentZone()); assertEquals(1,GameServices.level().getCurrentAct());
    }
    private void roundTripEncounter(boolean left,boolean right,boolean jump) {
        var registry=fixture.gameplayMode().getRewindRegistry(); var before=registry.capture();
        fixture.stepFrame(false,false,left,right,jump); var after=registry.capture();
        var manager=GameServices.level().getObjectManager();
        manager.getActiveObjects().stream().filter(o->o.getClass().getSimpleName().startsWith("LrzEndBoss")
                || o instanceof LrzEndBossEggCapsule.Results).toList().forEach(manager::removeDynamicObject);
        registry.restore(before); assertSameWorld(before,registry.capture());
        fixture.stepFrame(false,false,left,right,jump); assertSameWorld(after,registry.capture());
        boss=manager.activeObjectsOfType(LrzEndBossObjectInstance.class).getFirst();
    }
    private void assertSameWorld(com.openggf.game.rewind.CompositeSnapshot expected,
                                 com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet()) {
            var diff=com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key));
            assertTrue(diff.isEmpty(),()->key+": "+diff);
        }
    }

}
