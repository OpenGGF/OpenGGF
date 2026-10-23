package com.openggf.game.sonic3k.competition;

import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionHudObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.level.LevelArtInternalAccess;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import static com.openggf.game.sonic3k.competition.S3kCompetitionSession.Mode;
import static org.junit.jupiter.api.Assertions.*;

/** The actual ROM decode/load/frame/rewind pipeline, without a GUI or campaign team bootstrap. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionNativeHost {
    private S3kCompetitionNativeHost host;
    private final InputHandler input = new InputHandler();
    private final AtomicReference<GameplayModeContext> published = new AtomicReference<>();
    private final List<byte[]> saves = new ArrayList<>();
    private final Presentation views = new Presentation();
    private S3kCompetitionRecords records;

    @BeforeEach void createNativeOwnerWithProductionNoDeviceAudio() throws Exception {
        var rom = GameServices.rom().getRom();
        TestEnvironment.configureRomFixture(rom);
        var engine = EngineServices.current(); engine.graphics().initHeadless();
        // Same production no-device backend selected by HeadlessGameBoot. No
        // GLFW initialization, physical input polling or audio-device request.
        var audio = new HeadlessSmpsAudioBackend(engine.configuration(), engine.profiler());
        engine.audio().setBackend(audio); assertTrue(engine.audio().hasInstalledBackend(audio));
        byte[] block = new byte[80]; ByteBuffer bytes = ByteBuffer.wrap(block);
        for (int arena = 0; arena < 5; arena++) {
            for (int rank = 0; rank < 3; rank++) bytes.putInt((int) S3kCompetitionRecords.packTime(9,59,98));
            bytes.put((byte)0).put((byte)1).put((byte)2).put((byte)0);
        }
        records = new S3kCompetitionRecords(block);
        host = new S3kCompetitionNativeHost(engine,new Sonic3kGameModule(),input,published::set,
                records,saves::add,views,new CanonicalBinding());
        host.screen().initialize();
    }
    @AfterEach void releaseOwnedWorld() { if (host != null) host.closeArena(); SessionManager.clear(); }

    @Test void foreignBaseModulesAreRejectedBeforeAnyHostPublicationOrViewBinding() {
        var rejectedViews = org.mockito.Mockito.mock(S3kCompetitionNativeHost.Views.class);
        var rejectedBinding = org.mockito.Mockito.mock(S3kCompetitionNativeHost.Binding.class);
        for (var id : List.of(com.openggf.game.GameId.S1, com.openggf.game.GameId.S2,
                com.openggf.game.GameId.STANDALONE)) {
            var module = org.mockito.Mockito.mock(com.openggf.game.GameModule.class);
            org.mockito.Mockito.when(module.getGameId()).thenReturn(id);
            var failure = assertThrows(IllegalArgumentException.class, () ->
                    new S3kCompetitionNativeHost(EngineServices.current(), module, input,
                            published::set, records, saves::add, rejectedViews, rejectedBinding));
            assertEquals("S3K module required", failure.getMessage());
        }
        assertNull(published.get());
        assertTrue(saves.isEmpty());
        org.mockito.Mockito.verifyNoInteractions(rejectedViews, rejectedBinding);
    }

    @Test void actualAlzLoadsEightSourcePlacementsBeforeGoalChildrenAndRunsOneUncountedPass() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        var context = host.world().context(); var level = context.getLevelManager();
        assertSame(context,published.get()); assertEquals(0xE,level.getCurrentLevel().getZoneIndex());
        assertSame(host,NativeCompetitionRoster.from(context.getWorldSession()));
        assertSame(host.session(),WorldSessionPolicyAccess.getService(context.getWorldSession(),S3kCompetitionSession.class));
        var descriptors=assertInstanceOf(com.openggf.game.internal.RetainedPlaneDescriptorSource.class,
                level.getZoneFeatureProvider());
        assertSame(host.planes().foregroundPlane(),descriptors.foregroundPlane());
        assertSame(host.planes().backgroundPlane(),descriptors.backgroundPlane());
        assertEquals(128,descriptors.foregroundPlane().widthTiles());
        assertEquals(32,descriptors.backgroundPlane().heightTiles());
        assertTrue(descriptors.foregroundPlane().revision()>0);
        assertEquals(2,host.spritePages().capture().physicalPages().size());
        assertEquals(0,level.getFrameCounter()); assertEquals(0,context.getSpriteManager().getFrameCounter());
        assertEquals(2,context.getSpriteManager().getAllSprites().size());
        // The shared native roster exposes P2 through this structural query;
        // its role remains a human, with no ordinary CPU or tail controllers.
        assertEquals(List.of(host.player(1)),context.getSpriteManager().getRegisteredSidekicks());
        for (int slot = 0; slot < 2; slot++) {
            var actor = host.player(slot);
            assertInstanceOf(S3kMiniatureSprite.class,actor); assertFalse(actor.isCpuControlled());
            assertNull(actor.getCpuController()); assertNull(actor.getAnimationProfile());
            assertNull(actor.getTailsTailsController()); assertNull(actor.getSpindashDustController());
        }
        // AllocateObject pre-increments Dynamic_object_RAM (global slot3), so
        // literal ALZ bytes $1FC560..$1FC58F occupy native slots4..11.
        int[] ids = {0x65,2,0x5F,2,0x64,0x64,0x4F,0x33};
        for (int i = 0; i < ids.length; i++) assertEquals(ids[i],at(i+4).getSpawn().objectId());
        var kinds = new CompetitionHudObjectInstance.Kind[]{CompetitionHudObjectInstance.Kind.ITEM_ICON,
                CompetitionHudObjectInstance.Kind.ITEM_ICON,CompetitionHudObjectInstance.Kind.LAP,
                CompetitionHudObjectInstance.Kind.LAP,CompetitionHudObjectInstance.Kind.NEON,
                CompetitionHudObjectInstance.Kind.TIMER,CompetitionHudObjectInstance.Kind.TIMER,
                CompetitionHudObjectInstance.Kind.RESERVED};
        for (int i = 0; i < kinds.length; i++) assertEquals(kinds[i],
                assertInstanceOf(CompetitionHudObjectInstance.class,at(i+12)).kind());
        assertInstanceOf(S3kMiniatureTail.class,at(97));
        assertInstanceOf(S3kCompetitionPatternAnimator.class,level.getAnimatedPatternManager());
        assertTrue(views.prepared > 0); assertFalse(EngineServices.current().graphics().isGlInitialized());
        assertActorPrefix(0,1);
        assertTrue(NativeSpriteSubmissionAccess.admissions(objects()).stream().anyMatch(a -> a.slot()==97 && a.bucket()==2));
    }

    @Test void ninetyCountedNativePassesKeepBothActorCallsBeforeDynamicDrawsAndUploadP1() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        for(int frame=1;frame<=90;frame++) {
            step(LogicalInputSnapshot.neutral()); assertActorPrefix(0,1);
            assertEquals(frame,level().getFrameCounter());
        }
        assertTrue(host.spritePages().uploaded().player1().pieces().stream().anyMatch(piece -> piece.nativeSlot()==0),
                "the uploaded native P1 SAT still contains Sonic after ninety ordinary passes");
        assertEquals(2,host.world().context().getSpriteManager().getAllSprites().size());
        for(var player:host.players()) {
            assertFalse(player.sprite().isCpuControlled()); assertNull(player.sprite().getCpuController());
            assertNull(player.sprite().getAnimationProfile()); assertNull(player.sprite().getTailsTailsController());
            assertNull(player.sprite().getSpindashDustController());
        }
    }

    @Test void actorDrawPrefixRetainsActualBlinkCallsRatherThanInferringFromEndTimers() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        var first=host.players().getFirst(); var second=host.players().get(1);
        first.setInvulnerabilityTimer(1); second.setInvulnerabilityTimer(4);
        step(LogicalInputSnapshot.neutral()); assertActorPrefix(1);
        assertEquals(0,first.invulnerabilityTimer()); assertEquals(3,second.invulnerabilityTimer());
        assertFalse(first.bodyVisible()); assertTrue(second.bodyVisible());
        assertTrue(NativeSpriteSubmissionAccess.admissions(objects()).contains(new NativeSpriteSubmissionAccess.Admission(97,2)),
                "Tails' fixed SST draws independently of its parent body's blink");
        step(LogicalInputSnapshot.neutral()); assertActorPrefix(0);
        first.setInvulnerabilityTimer(1); second.setInvulnerabilityTimer(3);
        step(LogicalInputSnapshot.neutral()); assertActorPrefix();
        assertTrue(NativeSpriteSubmissionAccess.nativePassPresent(objects()),"an empty actor prefix still belongs to a real native pass");
        assertTrue(NativeSpriteSubmissionAccess.admissions(objects()).contains(new NativeSpriteSubmissionAccess.Admission(97,2)));
    }

    @Test void identicalCharactersKeepBothDrawCallsWhenNativeViewSkipBitsToggle() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.SONIC);
        assertActorPrefix(0,1);
        for(int frame=1;frame<=4;frame++) {
            step(LogicalInputSnapshot.neutral()); assertActorPrefix(0,1);
            assertEquals((frame&1)==0?0:8,host.players().get(0).nativeRenderFlags()&0x18);
            assertEquals((frame&1)==0?0:0x10,host.players().get(1).nativeRenderFlags()&0x18);
        }
    }

    @Test void bothHumansUseNativeInputsRealRomTerrainAndExactlyOneWorldClock() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        int floorSamples = 0;
        var definition = loadDefinition(Arena.AZURE_LAKE);
        for (int x = definition.startX()-24; x <= definition.startX()+24; x+=4)
            for (int y = definition.startY()-16; y <= definition.startY()+64; y+=4)
                if (host.terrain().probe(Direction.DOWN,x,y,0xC,false).tileIndex()!=0) floorSamples++;
        assertTrue(floorSamples>10,"Native ALZ collision masks are decoded and probed");
        byte[] animationBefore = ((S3kCompetitionPatternAnimator)level().getAnimatedPatternManager()).capture();
        var right = PlayerInputState.of(8,0,0,0,false,false);
        var left = PlayerInputState.of(4,0,0,0,false,false);
        for (int i = 0; i < 8; i++) step(LogicalInputSnapshot.ofPlayers(right,left));
        assertEquals(8,level().getFrameCounter());
        assertEquals(8,host.world().context().getSpriteManager().getFrameCounter());
        assertEquals(8,host.session().levelFrameCounter());
        assertTrue(host.players().get(0).xVelocity()>0); assertTrue(host.players().get(1).xVelocity()<0);
        assertEquals(0,host.levelState(0).getTimerFrames()); assertEquals(0,host.levelState(1).getTimerFrames());
        assertFalse(java.util.Arrays.equals(animationBefore,((S3kCompetitionPatternAnimator)level().getAnimatedPatternManager()).capture()));
        host.session().signalGo();
        for (int i = 0; i < 60; i++) step(LogicalInputSnapshot.neutral());
        assertEquals(60,host.levelState(0).getTimerFrames()); assertEquals(60,host.levelState(1).getTimerFrames());
        assertEquals(100,host.session().timeCentiseconds(0)); assertEquals(100,host.session().timeCentiseconds(1));
    }

    @Test void nativeBanksWrapRingsWithoutExtraLivesAndPaletteWritesAreImmediatelyReadable() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        int lives0 = host.gameState(0).getLives(), lives1 = host.gameState(1).getLives();
        host.levelState(0).setRings(99); host.levelState(0).addRings(101);
        host.levelState(1).setRings(0xFFFF); host.levelState(1).addRings(2);
        assertEquals(200,host.session().rings(0)); assertEquals(1,host.session().rings(1));
        assertEquals(lives0,host.gameState(0).getLives()); assertEquals(lives1,host.gameState(1).getLives());
        host.levelState(0).resumeTimer(); assertFalse(host.levelState(0).isTimerPaused());
        host.levelState(0).pauseTimer(); assertTrue(host.levelState(0).isTimerPaused());
        host.levelState(0).setTimerFrames(36000); assertFalse(host.levelState(0).isTimeOver());
        host.setPaletteColor(2,14,0x2E); assertEquals(0x2E,host.paletteColor(2,14));
        host.setPaletteColor(2,14,0xC2E); assertEquals(0xC2E,host.paletteColor(2,14));
        host.setTrigger(2,0x101); assertEquals(1,host.trigger(2));
        assertEquals(com.openggf.game.OscillationManager.getByte(0x14),host.oscillationByte(0x16));
        short originalMin=host.camera(0).getMinY(),originalMax=host.camera(0).getMaxY();
        host.camera(0).setMaxY((short)0x1AB); assertEquals(0x1AB,host.bottomY());
        host.camera(0).setMinY((short)-0x100); assertEquals(0x1FF,host.wrapYMask());
        host.camera(0).setMinY((short)0); assertEquals(-1,host.wrapYMask());
        host.camera(0).setMinY(originalMin); host.camera(0).setMaxY(originalMax);
        assertThrows(IllegalArgumentException.class,() -> host.levelState(0).setTimerFrames(36001));
    }

    @Test void nativeAll64FadeRunsAfterEachCompletedSpritePageAndNeverBeforeActors() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        assertEquals(List.of(0xFFFF),views.paletteTimersAtRender);
        assertEquals(22,host.palettes().fadeTimer());
        for(int line=0;line<4;line++) for(int color=0;color<16;color++)
            assertEquals(0,host.paletteColor(line,color),"initial loc_665A clears every current word");
        step(LogicalInputSnapshot.neutral());
        assertEquals(List.of(0xFFFF,22),views.paletteTimersAtRender);
        assertEquals(21,host.palettes().fadeTimer());
        for(int pass=1;pass<22;pass++)step(LogicalInputSnapshot.neutral());
        assertEquals(0,host.palettes().fadeTimer());
        for(int line=0;line<4;line++)for(int color=0;color<16;color++)
            assertEquals(host.palettes().targetColor(line,color),host.paletteColor(line,color));
        assertEquals(23,views.prepared,"one initial page and one page per native ordinary body");
    }

    @Test void fixedDustUsesTheCurrentRecreatedOwnerForEachHumanAndSurvivesForwardReplay() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        for(int slot=0;slot<2;slot++) {
            var dust=assertInstanceOf(S3kMiniatureDust.class,at(98+slot));
            assertEquals(slot,dust.parentSlot());assertEquals(2,dust.routine());
            assertTrue(NativeSpriteSubmissionAccess.admissions(objects()).stream()
                    .anyMatch(admission->admission.slot()==dust.getSlotIndex()&&admission.bucket()==1));
        }
        var checkpoint=host.world().context().getRewindRegistry().capture();
        var retired=at(98);
        host.world().context().getRewindRegistry().restore(checkpoint);
        // Object snapshots retain exact native owner identity when recreation is unnecessary;
        // overwrite it explicitly, then prove actor writes resolve that current address.
        var replacement=com.openggf.level.objects.ObjectManagerInternalAccess.replaceNativeCompetitionFixedObject(
                objects(),98,()->new S3kMiniatureDust(0));
        assertNotSame(retired,replacement);
        host.players().getFirst().setSpinDashFlag(1);
        host.players().getFirst().setAnimation(0xD);
        var downA=PlayerInputState.of(2,2,InputActionMasks.ACTION_A,InputActionMasks.ACTION_A,false,false);
        step(LogicalInputSnapshot.ofPlayers(downA,PlayerInputState.neutral()));
        assertTrue(replacement.routine()!=0,"fixed replacement executes through the canonical SST pass");
        var saved=host.world().context().getRewindRegistry().capture();
        for(int tick=0;tick<17;tick++)step(LogicalInputSnapshot.neutral());
        var expected=host.session().capture();var expectedDust=assertInstanceOf(S3kMiniatureDust.class,at(98)).captureRewindState();
        host.world().context().getRewindRegistry().restore(saved);
        for(int tick=0;tick<17;tick++)step(LogicalInputSnapshot.neutral());
        assertEquals(expected,host.session().capture());
        assertEquals(expectedDust,assertInstanceOf(S3kMiniatureDust.class,at(98)).captureRewindState());
    }

    @Test void nativeAllocationFailureDoesNotConstructAndReservedProjectileStillReplacesAtFullRam() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        while (objects().allocateDynamicSlot() >= 0) { }
        AtomicInteger constructions = new AtomicInteger();
        assertNull(host.allocateAfter(assertInstanceOf(AbstractObjectInstance.class,at(4)),() -> {
            constructions.incrementAndGet(); return new ProbeObject();
        }));
        assertNull(host.allocateFree(() -> {constructions.incrementAndGet();return new ProbeObject();}));
        assertEquals(0,constructions.get());
        var first = host.overwriteProjectile(() -> { constructions.incrementAndGet(); return new ProbeObject(); });
        var second = host.overwriteProjectile(() -> { constructions.incrementAndGet(); return new ProbeObject(); });
        assertEquals(2,constructions.get()); assertNotSame(first,second); assertSame(second,at(94));
        host.levelState(1).setRings(3); host.hurt(host.players().get(1),0x600);
        assertEquals(4,host.players().get(1).routine()); assertEquals(3,host.session().rings(1),
                "failed native ring-owner allocation preserves the bank while still hurting");
        host.hurt(host.players().getFirst(),0x600);
        assertEquals(6,host.players().getFirst().routine()); assertTrue(host.players().getFirst().highPriority());
        step(LogicalInputSnapshot.neutral()); assertActorPrefix(0,1);
    }

    @Test void nativeDamageUsesRealSstRingOwnerSharedClockAndPreviousListCollection() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        host.levelState(0).setRings(7); host.levelState(1).setRings(1);
        host.hurt(host.players().get(1),0x600);
        assertEquals(4,host.players().get(1).routine()); assertEquals(7,host.session().rings(0));
        var ring=objects().getActiveObjects().stream().filter(S3kCompetitionRingLoss.class::isInstance)
                .map(S3kCompetitionRingLoss.class::cast).findFirst().orElseThrow();
        assertEquals(1,ring.lossOwnerSlot()); assertEquals(0,ring.routine());
        assertSame(level().getRingManager().getSpillAnimationState(),host.spillAnimation());
        step(LogicalInputSnapshot.neutral());
        assertEquals(0,host.session().rings(0),"FixBugs=0 P2 spill initialization clears P1");
        assertEquals(1,host.session().rings(1)); assertEquals(0xFF,host.spillAnimation().counter(),
                "competition does not dispatch ChangeRingFrame after native ring initialization");
        var collector=host.players().getFirst(); collector.setObjectControl(1);
        collector.setCentre(ring.getX(),ring.getY());
        step(LogicalInputSnapshot.neutral());
        assertEquals(1,host.session().rings(0)); assertEquals(1,host.session().rings(1));
        assertEquals(6,ring.routine(),"the actual previous collision list dispatches ring collection");
    }

    @Test void nativePauseUsesPriorP1SampleKeepsEveryWorldOwnerFrozenAndResumesAtASeparateWait() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        var start=PlayerInputState.of(0,0,0,0,true,true);
        step(LogicalInputSnapshot.ofPlayers(start,PlayerInputState.neutral()));
        assertEquals(1,level().getFrameCounter(),"current Start is latched by the ordinary wait");
        step(LogicalInputSnapshot.neutral());
        assertTrue(host.pause().gamePaused());assertTrue(host.gameState(0).isGamePaused());
        int prepared=views.prepared;var frozen=host.session().capture();var pages=host.spritePages().capture();
        var palette=host.palettes().capture();var scroll=host.scroll().capture();
        var checkpoint=host.world().context().getRewindRegistry().capture();
        for(int tick=0;tick<17;tick++)step(LogicalInputSnapshot.neutral());
        assertEquals(1,level().getFrameCounter());assertEquals(prepared,views.prepared);
        assertEquals(frozen,host.session().capture());assertEquals(palette,host.palettes().capture());
        assertEquals(scroll,host.scroll().capture());assertEquals(pages,host.spritePages().capture());
        host.world().context().getRewindRegistry().restore(checkpoint);
        assertTrue(host.gameState(0).isGamePaused(),"native pause adapter restores the global flag");
        for(int tick=0;tick<17;tick++)step(LogicalInputSnapshot.neutral());
        assertEquals(frozen,host.session().capture());assertEquals(palette,host.palettes().capture());
        step(LogicalInputSnapshot.ofPlayers(start,PlayerInputState.neutral()));
        assertFalse(host.pause().gamePaused());assertEquals(1,level().getFrameCounter(),"resume still owns a pause-only VBlank");
        step(LogicalInputSnapshot.neutral());
        assertEquals(2,level().getFrameCounter());assertEquals(prepared+1,views.prepared);
        step(LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),start));
        step(LogicalInputSnapshot.neutral());
        assertEquals(4,level().getFrameCounter());assertFalse(host.pause().gamePaused(),"P2 never enters Pause_Game");
    }

    @Test void nativeOpenRetiresInheritedBlackPresentationAndKeepsSharedAnimationRamUndispatched() {
        var graphics=EngineServices.current().graphics();
        graphics.setPaletteFadePresentation(com.openggf.graphics.PaletteFadePresentation.Mode.FROM_BLACK,0,
                com.openggf.graphics.PaletteFadePresentation.LINES_1_TO_3);
        assertTrue(graphics.getPaletteFadePresentation().isActive());
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        assertFalse(graphics.getPaletteFadePresentation().isActive());
        assertArrayEquals(new int[]{0,0,0},host.spillAnimation().snapshot());
        var oscillator=com.openggf.game.OscillationManager.snapshotRomFormatBytes();
        for(int tick=0;tick<17;tick++)step(LogicalInputSnapshot.neutral());
        assertArrayEquals(new int[]{0,0,0},host.spillAnimation().snapshot());
        assertArrayEquals(oscillator,com.openggf.game.OscillationManager.snapshotRomFormatBytes());
        assertEquals(0,((S3kCompetitionPatternAnimator)level().getAnimatedPatternManager()).stageRingAnimationFrame());
    }

    @Test void nativeGiveRingAwardsEachHumanAndRestoresThresholdFlagsAndLifeByte() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        int initial0=host.gameState(0).getLives(),initial1=host.gameState(1).getLives();
        host.levelState(1).setRings(99);
        var snapshot=host.world().context().getRewindRegistry().capture();
        host.session().giveRing(1);
        assertEquals(initial0,host.gameState(0).getLives()); assertEquals(initial1+1,host.gameState(1).getLives());
        assertEquals(2,host.levelState(1).getRingExtraLifeFlags());
        var expected=host.session().capture();
        host.world().context().getRewindRegistry().restore(snapshot);
        assertEquals(initial1,host.gameState(1).getLives()); assertEquals(0,host.levelState(1).getRingExtraLifeFlags());
        host.session().giveRing(1);
        assertEquals(expected,host.session().capture()); assertEquals(initial1+1,host.gameState(1).getLives());
        host.levelState(1).setRings(199); host.session().giveRing(1);
        assertEquals(initial1+2,host.gameState(1).getLives()); assertEquals(6,host.levelState(1).getRingExtraLifeFlags());
        host.levelState(1).setRings(199); host.session().giveRing(1);
        assertEquals(initial1+2,host.gameState(1).getLives());
        while(host.gameState(0).getLives()<0xFF) host.gameState(0).addLife();
        host.levelState(0).setRings(99); host.session().giveRing(0);
        assertEquals(0,host.gameState(0).getLives(),"native ADDQ.B wraps the life bank");
    }

    @Test void actualPreviousTouchListStopsAtFirstOverlapForEachHumanBeforeWheelConsumption() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        var parent = assertInstanceOf(AbstractObjectInstance.class,at(4));
        var first = assertInstanceOf(CompetitionItemObjectInstance.class,host.allocateAfter(parent,
                () -> new CompetitionItemObjectInstance(new ObjectSpawn(0x680,0x100,0x64,0,0,false,0x100))));
        var second = assertInstanceOf(CompetitionItemObjectInstance.class,host.allocateAfter(first,
                () -> new CompetitionItemObjectInstance(first.getSpawn())));
        first.replenish(3); second.replenish(3);
        step(LogicalInputSnapshot.neutral()); // Both real SST routines publish the canonical next list.
        for (var player : host.players()) {
            player.setObjectControl(1); player.setCentre(first.getX(),first.getY());
        }
        step(LogicalInputSnapshot.neutral());
        assertEquals(1,host.session().rings(0)); assertEquals(0,host.session().rings(1));
        assertEquals(7,first.effect(),"P1 then P2 touch bits are consumed by one wheel SST");
        assertEquals(3,second.effect(),"Touch_Process returns at the first overlap for each human");
        assertEquals(0xC7,second.getCollisionFlags());
    }

    @Test void absentPlayerTwoRetainsClearedCharacterByteForSingleSonicRenderFlags() {
        host.openArena(new S3kCompetitionScreen.Launch(new S3kCompetitionSession.Config(Mode.TIME_ATTACK,
                Arena.AZURE_LAKE,true,1),List.of(S3kCompetitionPlayer.Character.SONIC)));
        var player = host.players().getFirst();
        assertEquals(4,player.nativeRenderFlags() & 0x7F,"init overwrites the first entry toggle");
        step(LogicalInputSnapshot.neutral());
        assertEquals(0xC,player.nativeRenderFlags() & 0x7F,"absent P2 character_id remains zero");
        step(LogicalInputSnapshot.neutral());
        assertEquals(4,player.nativeRenderFlags() & 0x7F);
    }

    @Test void hurtLandingReturnsWithoutTouchUntilTheNextNormalDispatch() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        var player = host.players().getFirst();
        int[] floor = hurtLandingFloor();
        var wheel = ringWheelAt(floor[0],floor[1]-11+floor[2]);
        // The hurt integrator advances one pixel before these actual paired
        // floor probes, then resolves the native negative-distance landing.
        player.setCentre(floor[0],floor[1]-12); player.launch(0,0x100); player.setRoutine(4);
        step(LogicalInputSnapshot.neutral());
        assertEquals(2,player.routine(),"real ROM terrain resolves the hurt landing");
        assertEquals(0,host.session().rings(0)); assertEquals(3,wheel.effect());
        player.setObjectControl(1); step(LogicalInputSnapshot.neutral());
        assertEquals(1,host.session().rings(0)); assertEquals(7,wheel.effect());
    }

    @Test void deathRespawnReturnsWithoutTouchUntilTheNextNormalDispatch() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.KNUCKLES);
        var player = host.players().getFirst(); var definition = loadDefinition(Arena.AZURE_LAKE);
        var wheel = ringWheelAt(definition.startX(),definition.startY());
        player.setCentre(definition.startX(),host.scroll().view(0).camera().y()+0x91);
        player.setXVelocity(0); player.setYVelocity(0); player.setRoutine(6);
        step(LogicalInputSnapshot.neutral());
        assertEquals(2,player.routine()); assertEquals(definition.startY(),player.centreY());
        assertEquals(0,host.session().rings(0)); assertEquals(3,wheel.effect());
        player.setObjectControl(1); step(LogicalInputSnapshot.neutral());
        assertEquals(1,host.session().rings(0)); assertEquals(7,wheel.effect());
    }

    @Test void freshMiniatureRecreationRestoresNativeAnimationAndBothHumansForward() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        var moving = LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,0,0,false,false),
                PlayerInputState.of(4,0,0,0,false,false));
        for (int i = 0; i < 7; i++) step(moving);
        var context = host.world().context(); var snapshot = context.getRewindRegistry().capture();
        var before = host.session().capture(); var retired0 = host.players().get(0); var retired1 = host.players().get(1);
        var beforeCalls=NativeSpriteSubmissionAccess.admissions(objects()); var beforePages=host.spritePages().capture();
        for (int i = 0; i < 9; i++) step(moving);
        var expected = host.session().capture(); var expectedScroll = host.scroll().capture();
        var expectedCalls=NativeSpriteSubmissionAccess.admissions(objects()); var expectedPages=host.spritePages().capture();
        var sprites = context.getSpriteManager();
        sprites.removeSprite(retired0.sprite().getCode()); sprites.removeSprite(retired1.sprite().getCode());
        // Bare recreation deliberately has no ROM animator. Production bindRoster
        // must reattach the immutable bank while retaining captured animation phase.
        sprites.addSprite(S3kMiniatureSprite.recreate(before.players().get(0).body()));
        sprites.addSprite(S3kMiniatureSprite.recreate(before.players().get(1).body()));
        context.getRewindRegistry().restore(snapshot);
        assertNotSame(retired0,host.players().get(0)); assertNotSame(retired1,host.players().get(1));
        assertSame(host.players().get(0).sprite(),host.camera(0).getFocusedSprite());
        assertSame(host.players().get(1).sprite(),host.camera(1).getFocusedSprite());
        assertEquals(before,host.session().capture());
        assertEquals(beforeCalls,NativeSpriteSubmissionAccess.admissions(objects())); assertEquals(beforePages,host.spritePages().capture());
        for (int i = 0; i < 9; i++) step(moving);
        assertEquals(expected,host.session().capture()); assertEquals(expectedScroll,host.scroll().capture());
        assertEquals(expectedCalls,NativeSpriteSubmissionAccess.admissions(objects())); assertEquals(expectedPages,host.spritePages().capture());
        assertActorPrefix(0,1);
    }

    @Test void actorFailureBeforeObjectQueueResetRestoresThenRecoversWithoutStalePrefixCalls() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        var second=host.players().get(1);
        second.setAnimation(8); second.setSpinDashFlag(0); second.setStatus(0); second.setAirLeft(30);
        var context=host.world().context(); var checkpoint=context.getRewindRegistry().capture();
        var before=host.session().capture(); var beforeCalls=NativeSpriteSubmissionAccess.admissions(objects());
        var beforePages=host.spritePages().capture(); int prepared=views.prepared;
        var failure=new IllegalStateException("intentional P2 native dust write failure");
        var writes=new AtomicInteger();
        second.setNativeDustWrites(new S3kCompetitionPlayer.NativeDustWrites() {
            @Override public void setAnimation(int animation) {assertEquals(2,animation);writes.incrementAndGet();throw failure;}
            @Override public void startSkid() {fail("spin charge must write animation through a6");}
        });
        var downA=PlayerInputState.of(2,2,InputActionMasks.ACTION_A,InputActionMasks.ACTION_A,false,false);
        assertSame(failure,assertThrows(IllegalStateException.class,
                () -> step(LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),downA))));
        assertNoActiveFixtureIteration(context);
        assertEquals(1,failure.getSuppressed().length);
        assertEquals("missing PLC preparation for ORDINARY_LEVEL",failure.getSuppressed()[0].getMessage());
        assertEquals(1,writes.get()); assertNotEquals(before.players().getFirst().body(),host.players().getFirst().capture(),
                "P1 already dispatched before P2's callback failed");
        assertEquals(prepared,views.prepared);
        // The frame's earlier VBlank can upload the preceding page. Failure
        // must not build a new CPU page or mutate its retained input pointers.
        assertEquals(beforePages.physicalPages(),host.spritePages().capture().physicalPages());
        assertEquals(beforePages.inputPointers(),host.spritePages().capture().inputPointers());
        context.getRewindRegistry().restore(checkpoint);
        assertEquals(before,host.session().capture()); assertEquals(beforeCalls,NativeSpriteSubmissionAccess.admissions(objects()));
        assertEquals(beforePages,host.spritePages().capture());
        step(LogicalInputSnapshot.neutral()); assertActorPrefix(0,1);
        step(LogicalInputSnapshot.neutral()); assertActorPrefix(0,1);
        assertEquals(1,writes.get(),"roster restore reinstalls live native dust writes, not the retired callback");
    }

    @Test void thrownQueuePrefixLeavesNoNewPageAndRestoredWorldRecoversOnlyItsCurrentActorCalls() {
        open(Mode.MATCH_RACE,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        var context=host.world().context();var checkpoint=context.getRewindRegistry().capture();
        var beforePages=host.spritePages().capture();int prepared=views.prepared;
        NativeSpriteSubmissionAccess.bindBeforePassPrefix(objects(),()->{throw new IllegalStateException("queue prefix failed");});
        var failure=assertThrows(IllegalStateException.class,()->step(LogicalInputSnapshot.neutral()));
        assertEquals("queue prefix failed",failure.getMessage());
        assertNoActiveFixtureIteration(context);
        assertEquals(1,failure.getSuppressed().length);
        assertEquals("missing PLC preparation for ORDINARY_LEVEL",failure.getSuppressed()[0].getMessage());
        assertTrue(NativeSpriteSubmissionAccess.nativePassPresent(objects()));
        assertTrue(NativeSpriteSubmissionAccess.admissions(objects()).isEmpty(),"reset precedes the failing prefix");
        assertEquals(prepared,views.prepared);
        assertEquals(beforePages.physicalPages(),host.spritePages().capture().physicalPages());
        assertEquals(beforePages.inputPointers(),host.spritePages().capture().inputPointers());
        context.getRewindRegistry().restore(checkpoint); // Rebinds the current host callback, never the failed identity.
        host.players().getFirst().setInvulnerabilityTimer(1);host.players().get(1).setInvulnerabilityTimer(4);
        step(LogicalInputSnapshot.neutral());assertActorPrefix(1);
        step(LogicalInputSnapshot.neutral());assertActorPrefix(0);
        assertEquals(prepared+2,views.prepared);
    }

    @Test void grandPrixReusesWorldAndActorSlotsButResetsNativeArenaState() {
        open(Mode.GRAND_PRIX,Arena.AZURE_LAKE,S3kCompetitionPlayer.Character.SONIC,S3kCompetitionPlayer.Character.TAILS);
        var world = host.world(); var first = host.players().getFirst(); var second = host.players().get(1);
        first.setInvulnerabilityTimer(1); second.setInvulnerabilityTimer(4);
        step(LogicalInputSnapshot.neutral()); assertActorPrefix(1); host.levelState(0).setRings(12);
        host.openArena(new S3kCompetitionScreen.Launch(new S3kCompetitionSession.Config(Mode.GRAND_PRIX,Arena.BALLOON_PARK,true,2),
                List.of(first.character(),second.character())));
        assertSame(world,host.world()); assertSame(first,host.players().getFirst()); assertSame(second,host.players().get(1));
        assertEquals(Arena.BALLOON_PARK,host.session().arena()); assertEquals(0xF,level().getCurrentLevel().getZoneIndex());
        assertEquals(0,level().getFrameCounter()); assertEquals(0,host.session().rings(0));
        assertEquals(15,loadDefinition(Arena.BALLOON_PARK).placements().size());
        assertEquals(0x61,at(4).getSpawn().objectId()); assertInstanceOf(S3kMiniatureTail.class,at(97));
        assertActorPrefix(0,1);
        step(LogicalInputSnapshot.neutral()); assertActorPrefix(0,1);
    }

    @Test void timeAttackPauseBReturnsToSelectionOnlyAfterTheFollowingOrdinaryBody() {
        press(0,2,0);press(0,2,0);press(0,0,InputActionMasks.ACTION_A);
        press(0,0,InputActionMasks.ACTION_A);for(int i=0;i<10;i++)press(0,0,0);
        press(0,0,InputActionMasks.ACTION_A);
        assertEquals(S3kCompetitionScreen.Phase.RACE,host.screen().view().phase());
        var start=PlayerInputState.of(0,0,0,0,true,true);
        updateScreen(LogicalInputSnapshot.ofPlayers(start,PlayerInputState.neutral()));
        updateScreen(LogicalInputSnapshot.neutral());
        assertTrue(host.pause().gamePaused());int prepared=views.prepared;
        var back=PlayerInputState.of(0,0,InputActionMasks.ACTION_B,InputActionMasks.ACTION_B,false,false);
        updateScreen(LogicalInputSnapshot.ofPlayers(back,PlayerInputState.neutral()));
        assertEquals(S3kCompetitionScreen.Phase.RACE,host.screen().view().phase());
        assertEquals(1,level().getFrameCounter());assertEquals(prepared,views.prepared);
        assertFalse(host.pause().gamePaused());
        updateScreen(LogicalInputSnapshot.neutral());
        assertEquals(prepared+1,views.prepared,"the pending native ordinary body actually rendered its next page");
        assertEquals(S3kCompetitionScreen.Phase.ARENA_SELECT,host.screen().view().phase());
        assertEquals(Mode.TIME_ATTACK,host.screen().view().mode());assertNull(host.world());
        assertEquals(List.of(false,false),host.screen().view().confirmed());assertTrue(saves.isEmpty());
    }

    @Test void nativeGoalFinishPersistsTimeAttackBeforeTheResultWaitAndUsesFinishedTimerFlag() {
        press(0,2,0); press(0,2,0); press(0,0,InputActionMasks.ACTION_A);
        press(0,0,InputActionMasks.ACTION_A); for (int i = 0; i < 10; i++) press(0,0,0);
        press(0,0,InputActionMasks.ACTION_A);
        assertEquals(S3kCompetitionScreen.Phase.RACE,host.screen().view().phase());
        var goal = assertInstanceOf(CompetitionGoalObjectInstance.class,at(4));
        host.session().signalGo();
        var player = host.players().getFirst(); player.setObjectControl(1); player.setSolidBits(0xC,0xD);
        // Start cross, then five independent completed laps; side reset is far
        // outside the goal's native ±$20 X window, so it cannot decrement laps.
        for (int cross = 0; cross < 6; cross++) {
            player.setCentre(goal.getX()-0x40,goal.getY()); press(0,0,0);
            host.levelState(0).setTimerFrames(60);
            player.setCentre(goal.getX()+1,goal.getY()); press(0,0,0);
        }
        assertTrue(host.session().finished(0)); assertEquals(1,saves.size()); assertEquals(0,host.newRecordIndex());
        assertFalse(host.levelState(0).isTimerPaused(),"native finished Update_HUD_timer=$80 is nonzero");
        assertEquals(S3kCompetitionScreen.Phase.RACE,host.screen().view().phase());
        assertEquals(S3kCompetitionRecords.packCentiseconds(host.session().timeCentiseconds(0)),
                records.entries(Arena.AZURE_LAKE).getFirst().packedTime());
        assertArrayEquals(records.encode(),saves.getFirst());
    }

    private void open(Mode mode,Arena arena,S3kCompetitionPlayer.Character first,S3kCompetitionPlayer.Character second) {
        host.openArena(new S3kCompetitionScreen.Launch(new S3kCompetitionSession.Config(mode,arena,true,2),List.of(first,second)));
    }
    private void step(LogicalInputSnapshot logical) {
        input.setLogicalOverride(logical);
        runFixtureIteration(() -> host.stepArena(logical,host.session()));
    }
    private void runFixtureIteration(Runnable body) {
        var lifecycle = host.world().context().plcFrameLifecycle();
        try {
            lifecycle.runLogicalIteration(() -> {},frame -> {
                NativeCompetitionIterationAccess.runAdmitted(frame,body);
                return null;
            });
        } catch (RuntimeException | Error failure) {
            // This fixture owns the abandoned outer iteration. Preserve the
            // primary failure and its suppressed preparation guard, then let
            // the test restore the genuine registry before stepping again.
            lifecycle.reset();
            throw failure;
        }
    }
    private void assertNoActiveFixtureIteration(GameplayModeContext context) {
        var completed = new AtomicInteger();
        context.plcFrameLifecycle().afterConsumedIteration(completed::incrementAndGet);
        assertEquals(1,completed.get(),"the failed fixture token must no longer own completion callbacks");
    }
    private void press(int slot,int direction,int action) {
        var buttons = PlayerInputState.of(direction,direction,action,action,false,false);
        var logical = LogicalInputSnapshot.ofPlayers(slot==0?buttons:PlayerInputState.neutral(),slot==1?buttons:PlayerInputState.neutral());
        updateScreen(logical);
    }
    private void updateScreen(LogicalInputSnapshot logical) {
        input.setLogicalOverride(logical);
        if (host.screen().view().phase()==S3kCompetitionScreen.Phase.RACE)
            runFixtureIteration(() -> host.screen().update(logical));
        else host.screen().update(logical);
        host.screen().afterFrame();
    }
    private CompetitionItemObjectInstance ringWheelAt(int x,int y) {
        for (int slot=1;slot<host.players().size();slot++) {
            var other=host.players().get(slot); other.setObjectControl(1); other.setCentre(x+0x100,y);
        }
        var wheel = assertInstanceOf(CompetitionItemObjectInstance.class,host.allocateAfter(
                assertInstanceOf(AbstractObjectInstance.class,at(4)),
                () -> new CompetitionItemObjectInstance(new ObjectSpawn(x-56,y,0x64,0,0,false,y))));
        wheel.replenish(3); step(LogicalInputSnapshot.neutral());
        return wheel;
    }
    private int[] hurtLandingFloor() {
        var definition=loadDefinition(Arena.AZURE_LAKE);
        for(int x=definition.startX()-24;x<=definition.startX()+24;x+=4)
            for(int y=definition.startY()-16;y<=definition.startY()+64;y+=4) {
                var left=host.terrain().probe(Direction.DOWN,x-5,y,0xC,false);
                var right=host.terrain().probe(Direction.DOWN,x+5,y,0xC,false);
                var selected=left.distance()<=right.distance()?left:right;
                int angle=(selected.angle()&1)!=0?0:selected.angle();
                if(left.tileIndex()!=0 && right.tileIndex()!=0 && selected.distance()<0
                        && selected.distance()>=-8 && Math.abs((byte)angle)<=0x20)
                    return new int[]{x,y,selected.distance()};
            }
        throw new AssertionError("Actual ALZ start neighborhood supplies no native hurt landing");
    }
    private ObjectManager objects() { return level().getObjectManager(); }
    private List<NativeSpriteSubmissionAccess.Admission> actorCalls() {
        return NativeSpriteSubmissionAccess.admissions(objects()).stream().filter(call -> call.slot()==0||call.slot()==1).toList();
    }
    private void assertActorPrefix(int... nativeSlots) {
        var expected=java.util.Arrays.stream(nativeSlots)
                .mapToObj(slot -> new NativeSpriteSubmissionAccess.Admission(slot,2)).toList();
        var all=NativeSpriteSubmissionAccess.admissions(objects());
        assertTrue(NativeSpriteSubmissionAccess.nativePassPresent(objects()));
        assertEquals(expected,actorCalls(),"actual actor Draw_Sprite calls retain FIFO order and multiplicity");
        assertTrue(all.size()>=expected.size());
        assertEquals(expected,all.subList(0,expected.size()),"actor calls precede every dynamic/fixed object draw call");
    }
    private LevelManager level() { return host.world().context().getLevelManager(); }
    private ObjectInstance at(int slot) {
        return objects().getActiveObjects().stream().filter(o -> o instanceof AbstractObjectInstance a && a.getSlotIndex()==slot)
                .findFirst().orElseThrow(() -> new AssertionError("Missing native SST"+slot));
    }
    private S3kCompetitionArenaDefinition loadDefinition(Arena arena) {
        try { return S3kCompetitionArenaDefinition.load(GameServices.rom().getRom(),arena); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
    private static final class CanonicalBinding implements S3kCompetitionNativeHost.Binding {
        @Override public void initializeObjectArt(LevelManager level) { LevelArtInternalAccess.initializeObjectArt(level); }
    }
    private static final class Presentation implements S3kCompetitionNativeHost.Views {
        int prepared;
        final List<Integer> paletteTimersAtRender=new ArrayList<>();
        @Override public void drawArena(GameplayModeContext context,NativeCompetitionRoster roster,S3kCompetitionScroll scroll) { }
        @Override public void drawMenu(S3kCompetitionMenuScene.Scene scene) { }
        @Override public void prepareNativeSpritePages(GameplayModeContext context,NativeCompetitionRoster roster,S3kCompetitionScroll scroll) {
            prepared++;
            paletteTimersAtRender.add(((S3kCompetitionPaletteAnimator)context.getLevelManager().getAnimatedPaletteManager()).fadeTimer());
        }
        @Override public void publishNativeSpritePages(PlcLifecyclePhase phase,GameplayModeContext context) { }
        @Override public void closeArena() { }
        @Override public void setClearColor() { }
    }
    private static final class ProbeObject extends AbstractObjectInstance {
        ProbeObject() { super(new ObjectSpawn(0,0,0,0,0,false,0),"NativeHostAllocationProbe"); }
        @Override public void update(int vIntRunCount,com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
    }
}
