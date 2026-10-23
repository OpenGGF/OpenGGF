package com.openggf.game.sonic3k.competition;

import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.control.InputHandler;
import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.competition.objects.CompetitionHudObjectInstance;
import com.openggf.level.LevelArtInternalAccess;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static com.openggf.game.sonic3k.competition.S3kCompetitionPlayer.Character.*;
import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena.AZURE_LAKE;
import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The canonical registry owns missing-actor reconstruction and RAM overlay.
 * Native body, animation, tail and dust are exercised; campaign shield/stars
 * controllers are not supported miniature effects and are not invented here.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionRosterRestore {
    private S3kCompetitionNativeHost host;
    private final InputHandler input = new InputHandler();

    @BeforeEach void createProductionHost() throws Exception {
        TestEnvironment.configureRomFixture(GameServices.rom().getRom());
        var engine = EngineServices.current();
        engine.graphics().initHeadless();
        var audio = new HeadlessSmpsAudioBackend(engine.configuration(),engine.profiler());
        engine.audio().setBackend(audio);
        assertTrue(engine.audio().hasInstalledBackend(audio));
        var bytes = ByteBuffer.allocate(80);
        for (int arena=0;arena<5;arena++) {
            for (int rank=0;rank<3;rank++) bytes.putInt((int)S3kCompetitionRecords.packTime(9,59,98));
            bytes.put((byte)0).put((byte)1).put((byte)2).put((byte)0);
        }
        host = new S3kCompetitionNativeHost(engine,new Sonic3kGameModule(),input,ignored -> {},
                new S3kCompetitionRecords(bytes.array()),ignored -> {},new Presentation(),new CanonicalBinding());
        host.screen().initialize();
    }

    @AfterEach void closeOwnedWorld() {
        if (host != null) host.closeArena();
        SessionManager.clear();
    }

    static Stream<Arguments> missingHumanCases() {
        return Stream.of(new S3kCompetitionPlayer.Character[]{SONIC,TAILS},
                        new S3kCompetitionPlayer.Character[]{TAILS,KNUCKLES},
                        new S3kCompetitionPlayer.Character[]{SONIC,SONIC},
                        new S3kCompetitionPlayer.Character[]{TAILS,TAILS})
                .flatMap(pair -> Stream.of(false,true).map(both -> Arguments.of(pair[0],pair[1],both)));
    }

    @ParameterizedTest(name="{0}/{1}, removeBoth={2}")
    @MethodSource("missingHumanCases")
    void canonicalRestoreRecreatesOnlyMissingHumansAndReplaysAllOwnersSeventeenFrames(
            S3kCompetitionPlayer.Character first,S3kCompetitionPlayer.Character second,boolean removeBoth)
            throws Exception {
        open(List.of(first,second));
        host.session().signalGo();
        host.levelState(0).setRings(7); host.levelState(1).setRings(19);
        host.players().get(0).setFractions(0x1234,0x5678);
        host.players().get(1).setFractions(0x9ABC,0xDEF0);
        host.session().applyImmediateItem(1,0);
        var moving = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),
                PlayerInputState.of(4,0,0,0,false,false));
        for (int frame=0;frame<17;frame++) step(moving);
        var context=host.world().context();
        var before=context.getRewindRegistry().capture();
        var retired0=host.players().get(0); var retired1=host.players().get(1);
        assertNativeResources(first,second);
        for (int frame=0;frame<17;frame++) step(moving);
        var expected=context.getRewindRegistry().capture();

        // No manual pre-add, native-state hydration, or extra bindRoster call.
        var sprites=context.getSpriteManager();
        if (removeBoth) sprites.removeSprite(retired0.sprite().getCode());
        sprites.removeSprite(retired1.sprite().getCode());
        context.getRewindRegistry().restore(before);

        if (removeBoth) assertNotSame(retired0,host.players().get(0));
        else assertSame(retired0,host.players().get(0));
        assertNotSame(retired1,host.players().get(1));
        assertNativeResources(first,second);
        assertFullRegistry(before,context.getRewindRegistry().capture());
        for (int frame=0;frame<17;frame++) step(moving);
        assertNativeResources(first,second);
        assertFullRegistry(expected,context.getRewindRegistry().capture());
        // Fresh models must write through their current a6 dust bindings too;
        // merely finding the independently restored fixed SST is insufficient.
        for (var player:host.players()) {
            player.setAnimation(8); player.setSpinDashFlag(0); player.setStatus(0); player.setAirLeft(30);
        }
        var downA=PlayerInputState.of(2,2,InputActionMasks.ACTION_A,InputActionMasks.ACTION_A,false,false);
        step(LogicalInputSnapshot.ofPlayers(downA,downA));
        for (int slot=0;slot<2;slot++) {
            assertEquals(1,host.players().get(slot).spinDashFlag());
            assertEquals(2,assertInstanceOf(S3kMiniatureDust.class,at(98+slot)).animation());
        }
    }

    @Test void prepareRestoreCreatesNeutralIdentityWithoutCopyingRetiredRamOrBindingEarly() {
        open(List.of(SONIC,TAILS));
        var context=host.world().context(); var sprites=context.getSpriteManager();
        var retained=host.players().get(0); var retired=host.players().get(1);
        retired.setCentre(777,888); retired.setFractions(0xABCD,0x1234);
        retired.setSpeeds(0x123,0x45,0x67); retired.setSpeedShoesTimer(57); retired.setStatusSecondary(4);
        var sessionBefore=host.session().capture();
        sprites.removeSprite(retired.sprite().getCode());
        var codes=Set.of(retained.sprite().getCode(),retired.sprite().getCode());
        host.prepareRestore(context,codes);
        var fresh=assertInstanceOf(S3kMiniatureSprite.class,sprites.getSprite(retired.sprite().getCode()));
        var nativePlayer=fresh.nativePlayer();
        assertNotSame(retired.sprite(),fresh); assertEquals(TAILS,nativePlayer.character());
        assertEquals(1,nativePlayer.slot()); assertEquals(0,nativePlayer.centreX()); assertEquals(0,nativePlayer.centreY());
        assertEquals(0,nativePlayer.xFraction()); assertEquals(0,nativePlayer.yFraction());
        assertEquals(new S3kCompetitionPlayer.Speeds(0x4C0,0x1C,0x70),nativePlayer.speeds());
        assertEquals(nativePlayer.speeds(),nativePlayer.capture().baseSpeeds());
        assertEquals(0,nativePlayer.speedShoesTimer()); assertEquals(0,nativePlayer.statusSecondary());
        assertFalse(nativePlayer.nativeInitialized());
        assertSame(retained.sprite(),sprites.getSprite(retained.sprite().getCode()));
        assertSame(retired,host.players().get(1)); assertSame(retired,host.session().player(1));
        assertEquals(sessionBefore,host.session().capture(),"identity preparation never overlays captured RAM");
        host.prepareRestore(context,codes);
        assertSame(fresh,sprites.getSprite(retired.sprite().getCode()),"an existing identity is not replaced");
    }

    @Test void nativeShoesRetainTheirOwnClockThroughReconstructionAndExpireAtTheEighthTick() {
        open(List.of(SONIC,KNUCKLES));
        var context=host.world().context();
        var initialScroll=host.scroll().capture();
        var parallax=context.getParallaxManager();
        var initialParallax=parallax.capture();
        assertEquals(3,initialScroll.waveCounter()); assertEquals(0x1000,initialScroll.cloudDrift());
        assertEquals(initialScroll,parallax.getHandler(context.getLevelManager().getCurrentZone()).captureRewindState(),
                "the initialized native handler is already published at LF0 without another scroll pass");
        host.session().applyImmediateItem(0,0);
        host.session().applyImmediateItem(1,1);
        for (var player:host.players()) {
            player.setSpeedShoesTimer(1);
            assertTrue(player.sprite().hasSpeedShoes());
            assertFalse(player.sprite().captureRewindState().playerExtra().speedShoes(),
                    "native shoes never activate the campaign timer owner");
            assertNull(player.sprite().currentTimerManager().getTimerForCode("SpeedShoes-"+player.sprite().getCode()));
        }
        var before=context.getRewindRegistry().capture();
        host.scroll().advance(new S3kCompetitionScroll.CameraPair(
                host.scroll().view(0).camera(),host.scroll().view(1).camera()));
        assertNotEquals(initialScroll,host.scroll().capture(),"the native scroll restore control must change state");
        assertNotEquals(initialParallax,parallax.capture());
        context.getSpriteManager().removeSprite(host.player(0).getCode());
        context.getSpriteManager().removeSprite(host.player(1).getCode());
        context.getRewindRegistry().restore(before);
        assertEquals(initialScroll,host.scroll().capture());
        assertEquals(initialParallax,parallax.capture(),"registry restore retains the complete parallax snapshot");
        assertFullRegistry(before,context.getRewindRegistry().capture());
        host.player(0).currentTimerManager().update();
        for (var player:host.players()) {
            assertTrue(player.sprite().hasSpeedShoes());
            assertEquals(1,player.speedShoesTimer(),"the ordinary timer phase cannot tick the native byte");
        }
        for (int frame=1;frame<=8;frame++) {
            step(LogicalInputSnapshot.neutral());
            for (var player:host.players()) {
                assertEquals(frame<8,player.sprite().hasSpeedShoes());
                assertEquals(frame<8?1:0,player.speedShoesTimer());
                assertNull(player.sprite().currentTimerManager().getTimerForCode("SpeedShoes-"+player.sprite().getCode()));
                if (frame==8) assertEquals(player.capture().baseSpeeds(),player.speeds());
            }
        }
    }

    @Test void hudReconstructionPreservesEveryCapturedSpawnFieldBeforeScalarOverlay() {
        open(List.of(SONIC,TAILS));
        var source=new CompetitionHudObjectInstance(CompetitionHudObjectInstance.Kind.TIMER,1);
        var state=source.captureRewindState();
        var capturedSpawn=new ObjectSpawn(0x321,0x456,0x65,1,3,true,0xA678,23);
        var probe=new CompetitionHudObjectInstance(new ObjectSpawn(0,0,0,0,0,false,0));
        var recreated=assertInstanceOf(CompetitionHudObjectInstance.class,
                probe.recreateForRewind(new RewindRecreateContext(capturedSpawn,state,null)));
        assertEquals(capturedSpawn,recreated.getSpawn());
        recreated.restoreRewindState(state);
        assertEquals(capturedSpawn,recreated.getSpawn());
        assertEquals(CompetitionHudObjectInstance.Kind.TIMER,recreated.kind());
        assertEquals(1,recreated.playerSlot());
    }

    @Test void uncapturedCodesAndSingleHumanRosterNeverInventASecondParticipant() {
        open(List.of(KNUCKLES));
        var context=host.world().context(); var sprites=context.getSpriteManager();
        var retired=host.players().getFirst(); String code=retired.sprite().getCode();
        sprites.removeSprite(code);
        host.prepareRestore(context,Set.of("s3k-competition-p2","foreign"));
        assertNull(sprites.getSprite(code)); assertTrue(sprites.getAllSprites().isEmpty());
        host.prepareRestore(context,Set.of(code,"s3k-competition-p2"));
        var fresh=assertInstanceOf(S3kMiniatureSprite.class,sprites.getSprite(code));
        assertEquals(KNUCKLES,fresh.nativePlayer().character()); assertEquals(0,fresh.nativePlayer().slot());
        assertEquals(new S3kCompetitionPlayer.Speeds(0x580,0x10,0x200),fresh.nativePlayer().speeds());
        assertEquals(1,sprites.getAllSprites().size()); assertNull(sprites.getSprite("s3k-competition-p2"));
        assertSame(retired,host.players().getFirst()); assertSame(retired,host.session().player(0));
    }

    @Test void foreignRestoreContextIsRejectedBeforeChangingAnyActiveIdentity() {
        open(List.of(SONIC,TAILS));
        var context=host.world().context(); var before=context.getRewindRegistry().capture();
        var first=host.players().get(0); var second=host.players().get(1);
        var foreign=new GameplayModeContext(new WorldSession(new Sonic3kGameModule()));
        assertThrows(IllegalArgumentException.class,() -> host.prepareRestore(foreign,
                Set.of(first.sprite().getCode(),second.sprite().getCode())));
        assertSame(context,host.world().context()); assertSame(first,host.players().get(0));
        assertSame(second,host.players().get(1));
        assertFullRegistry(before,context.getRewindRegistry().capture());
    }

    private void assertNativeResources(S3kCompetitionPlayer.Character... characters) throws Exception {
        var context=host.world().context();
        assertSame(host,NativeCompetitionRoster.from(context.getWorldSession()));
        assertNotNull(host.terrain());
        assertEquals(characters.length,context.getSpriteManager().getAllSprites().size());
        for (int slot=0;slot<characters.length;slot++) {
            var player=host.players().get(slot); var actor=player.sprite();
            assertEquals(characters[slot],player.character()); assertSame(player,host.session().player(slot));
            assertSame(actor,host.player(slot)); assertSame(actor,context.getSpriteManager().getSprite(actor.getCode()));
            assertSame(actor,host.camera(slot).getFocusedSprite()); assertSame(host.camera(slot),actor.currentCamera());
            assertSame(host.levelState(slot),actor.currentLevelState()); assertSame(host.gameState(slot),actor.currentGameState());
            assertFalse(actor.isCpuControlled()); assertNull(actor.getCpuController()); assertNull(actor.getAnimationProfile());
            assertNull(actor.getTailsTailsController()); assertNull(actor.getSpindashDustController());
            String key="competition_"+player.character().name().toLowerCase(Locale.ROOT);
            var sheet=host.assets().miniature(player.character());
            assertSame(sheet,host.artProvider().getSheet(key));
            int index=player.character().ordinal();
            assertEquals(new int[]{44,46,50}[index],sheet.getFrameCount());
            assertEquals(new int[]{390,365,420}[index],sheet.getPatterns().length);
            byte[] raw=GameServices.rom().getRom().readBytes(new int[]{0x337780,0x33A840,0x33DB60}[index],32);
            for (int y=0;y<8;y++) for (int x=0;x<8;x++) {
                int packed=raw[y*4+x/2]&255;
                assertEquals((x&1)==0?packed>>>4:packed&15,sheet.getPatterns()[0].getPixel(x,y)&15);
            }
            assertTrue(player.mappingFrame()<sheet.getFrameCount());
            assertEquals(slot,assertInstanceOf(S3kMiniatureDust.class,at(98+slot)).parentSlot());
            if (player.character()==TAILS) {
                assertEquals(slot,assertInstanceOf(S3kMiniatureTail.class,at(96+slot)).parentSlot());
                assertSame(host.assets().tailsTail(),host.artProvider().getSheet("competition_tails_tail"));
            } else assertNull(at(96+slot));
        }
    }

    private static void assertFullRegistry(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for (String key:List.of("sprites","object-manager","s3k.native-competition",
                "native_competition_camera_2","native_competition_collision_pointers",
                "native_competition_upfront_placement",NativeSpriteSubmissionAccess.REWIND_KEY,
                S3kCompetitionSpritePages.REWIND_KEY,"sonic3k.competition.planes",
                "sonic3k.competition.animated-tiles","sonic3k.competition.animated-palette",
                "sonic3k.competition.pause")) assertTrue(expected.containsKey(key),"mandatory owner: "+key);
        for (String key:expected.entries().keySet())
            assertEquals(List.of(),RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)),key);
        var before=assertInstanceOf(ObjectManagerSnapshot.class,expected.get("object-manager"));
        var after=assertInstanceOf(ObjectManagerSnapshot.class,actual.get("object-manager"));
        assertArrayEquals(before.usedSlotsBits(),after.usedSlotsBits());
        assertEquals(before.dynamicObjectIdCounter(),after.dynamicObjectIdCounter());
        assertEquals(before.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId).toList(),
                after.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::objectId).toList());
        assertEquals(before.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::slotIndex).toList(),
                after.dynamicObjects().stream().map(ObjectManagerSnapshot.DynamicObjectEntry::slotIndex).toList());
        var order=Comparator.comparingInt(ObjectManagerSnapshot.PerSlotEntry::slotIndex);
        assertEquals(before.slots().stream().sorted(order).map(ObjectManagerSnapshot.PerSlotEntry::objectId).toList(),
                after.slots().stream().sorted(order).map(ObjectManagerSnapshot.PerSlotEntry::objectId).toList());
    }

    private ObjectInstance at(int slot) {
        return host.world().context().getLevelManager().getObjectManager().getActiveObjects().stream()
                .filter(object -> object instanceof AbstractObjectInstance nativeObject && nativeObject.getSlotIndex()==slot)
                .findFirst().orElse(null);
    }

    private void open(List<S3kCompetitionPlayer.Character> characters) {
        host.openArena(new S3kCompetitionScreen.Launch(new S3kCompetitionSession.Config(
                characters.size()==1?TIME_ATTACK:MATCH_RACE,AZURE_LAKE,true,characters.size()),characters));
    }

    private void step(LogicalInputSnapshot logical) {
        input.setLogicalOverride(logical);
        var frame=host.world().context().plcFrameLifecycle().latchBeforeFadeUpdate();
        try { NativeCompetitionIterationAccess.runAdmitted(frame,() -> host.stepArena(logical,host.session())); }
        finally { frame.finish(); }
    }

    private static final class CanonicalBinding implements S3kCompetitionNativeHost.Binding {
        @Override public void initializeObjectArt(LevelManager level) { LevelArtInternalAccess.initializeObjectArt(level); }
    }

    private static final class Presentation implements S3kCompetitionNativeHost.Views {
        @Override public void drawArena(GameplayModeContext context,NativeCompetitionRoster roster,S3kCompetitionScroll scroll) { }
        @Override public void drawMenu(S3kCompetitionMenuScene.Scene scene) { }
        @Override public void prepareNativeSpritePages(GameplayModeContext context,NativeCompetitionRoster roster,S3kCompetitionScroll scroll) { }
        @Override public void publishNativeSpritePages(PlcLifecyclePhase phase,GameplayModeContext context) { }
        @Override public void closeArena() { }
        @Override public void setClearColor() { }
    }
}
