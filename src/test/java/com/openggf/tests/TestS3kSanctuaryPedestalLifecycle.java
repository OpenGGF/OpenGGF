package com.openggf.tests;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.sonic3k.objects.HPZSuperEmeraldObjectInstance;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Actual placed-pedestal contact and GameLoop entry; no direct selection/outcome writes. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSanctuaryPedestalLifecycle {
    private final CanonicalEntryMatrixSupport support = new CanonicalEntryMatrixSupport() {
        @Override TestS3kCanonicalEntryLifecycle.Act binding() {
            return new TestS3kCanonicalEntryLifecycle.Act("HPZ-sanctuary", 0x17, 1);
        }
    };
    @BeforeEach void saveConfiguration() { support.saveConfiguration(); }
    @AfterEach void restoreConfiguration() { support.restoreConfiguration(); }

    @ParameterizedTest(name = "physical gray pedestal{0} → actual Super stage")
    @ValueSource(ints = {0,1,2,3,4,5,6})
    void eachPlacedGrayPedestalEntersItsActualSuperEmeraldStageAndRetiresHubHistory(int stage)
            throws Exception {
        assertPhysicalEntry(new CanonicalEntryMatrixSupport.Row(
                "sonic", WidescreenAspect.NATIVE_4_3, "off", ""), stage);
    }

    static Stream<CanonicalEntryMatrixSupport.Row> representativeRows() {
        return TestS3kSanctuaryOriginReturnLifecycle.representativeRows();
    }

    @ParameterizedTest(name = "actual placed pedestal representative {0}")
    @MethodSource("representativeRows")
    void configuredRepresentativePhysicallySelectsAndLoadsItsSuperStage(
            CanonicalEntryMatrixSupport.Row row) throws Exception {
        assertPhysicalEntry(row, 3);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> supportedNativeSelectors() {
        return selectors(true);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> supportedWiderSelectors() {
        return selectors(false);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> selectors(boolean nativeWidth) {
        return CanonicalEntryMatrixSupport.configurations()
                .filter(row -> (row.aspect() == WidescreenAspect.NATIVE_4_3) == nativeWidth)
                .flatMap(row -> java.util.stream.IntStream.range(0, 7).mapToObj(stage ->
                        org.junit.jupiter.params.provider.Arguments.of(row, stage)));
    }

    @ParameterizedTest(name = "actual native-width selector {0}/stage{1}")
    @MethodSource("supportedNativeSelectors")
    void everyNativeWidthProductPhysicallySelectsAllSevenNativeStageOwners(
            CanonicalEntryMatrixSupport.Row row, int stage) throws Exception {
        assertPhysicalEntry(row, stage);
    }

    @ParameterizedTest(name = "actual wider selector {0}/stage{1}")
    @MethodSource("supportedWiderSelectors")
    void everyWiderProductPhysicallySelectsAllSevenNativeStageOwners(
            CanonicalEntryMatrixSupport.Row row, int stage) throws Exception {
        assertPhysicalEntry(row, stage);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> nativeOutcomes() {
        return TestS3kSanctuaryOriginReturnLifecycle.nativeOutcomes();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> supportedOutcomes() {
        return TestS3kSanctuaryOriginReturnLifecycle.supportedOutcomes();
    }

    @ParameterizedTest(name = "actual no-Saved2 developer-scene return {0}/{1}")
    @MethodSource("nativeOutcomes")
    void nativeOutcomeOwnersReturnADeclaredDirectSceneWithoutInventingSaved2(
            CanonicalEntryMatrixSupport.Row row, SanctuaryResultsLifecycleProbe.Outcome outcome)
            throws Exception {
        assertDirectSceneReturn(row, outcome);
    }

    @ParameterizedTest(name = "supported no-Saved2 developer-scene return {0}/{1}")
    @MethodSource("supportedOutcomes")
    void everySupportedDirectSceneOutcomeReturnsAndReplaysWithoutSaved2(
            CanonicalEntryMatrixSupport.Row row, SanctuaryResultsLifecycleProbe.Outcome outcome)
            throws Exception {
        assertDirectSceneReturn(row, outcome);
    }

    private void assertDirectSceneReturn(CanonicalEntryMatrixSupport.Row row,
            SanctuaryResultsLifecycleProbe.Outcome outcome) throws Exception {
        // The shipped-style level-select table offers no1701 hub. This is the
        // existing developer/capture direct-scene boundary, not a native menu route.
        var fixture = support.boot(row);
        GameServices.gameState().restoreS3kEmeraldProgress(outcome.initialProgress(), true);
        var neutral = new Bk2FrameInput(0, 0, 0, false, "");
        var input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(neutral, neutral));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        int lives = GameServices.gameState().getLives();
        try {
            assertFalse(GameServices.level().hasBigRingReturn(), "direct scene has no captured origin bank");
            advance(loop, support, row, 200);
            var provider = selectAndReplayIncomingStage(loop, input, fixture, support, row, 6, lives);
            SanctuaryResultsLifecycleProbe.completeAndReturn(loop, provider, fixture, row, outcome, lives, null);
            assertTrue(GameServices.gameState().getLives() >= lives);
            lives = GameServices.gameState().getLives();
            TestS3kSanctuaryOriginReturnLifecycle.waitForReleasedWorld(loop, lives, 0x17, 1, row);
            support.assertOwners(row);
            assertFalse(GameServices.level().hasBigRingReturn(), "fallback never fabricates Saved2");
            assertFalse(GameServices.sprites().getMainPlayable().isObjectControlled());
            assertTrue(GameServices.level().getLevelGamestate().isTimerPaused());
            var hudGate = com.openggf.level.LevelManager.class.getDeclaredMethod("isHudSuppressed");
            hudGate.setAccessible(true);
            assertTrue((boolean) hudGate.invoke(GameServices.level()), "actual semantic HUD suppression");
            var objects = GameServices.level().getObjectManager();
            var controller = objects.activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.HPZSSEntryControlObjectInstance.class).getFirst();
            assertFalse(controller.isHostingSpecialStageResults());
            assertFalse(controller.resultsReturnTransformationActive(), "success reveal already belonged to results");
            assertTrue(objects.activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.HPZSuperEmeraldReturnEffectObjectInstance.class).isEmpty());
            assertTrue(objects.activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.HPZSanctuaryFallingCrystalObjectInstance.class).isEmpty());
            var pedestal = objects.activeObjectsOfType(HPZSuperEmeraldObjectInstance.class).stream()
                    .filter(object -> object.getSpawn().subtype() == 6).findFirst().orElseThrow();
            assertEquals(outcome == SanctuaryResultsLifecycleProbe.Outcome.FAILURE
                    ? HPZSuperEmeraldObjectInstance.Display.GRAY : HPZSuperEmeraldObjectInstance.Display.COLORED,
                    pedestal.display());
            assertEquals(outcome == SanctuaryResultsLifecycleProbe.Outcome.FAILURE, pedestal.isSelectable());
            TestS3kSanctuaryOriginReturnLifecycle.replayTwice(loop, fixture, support, row, lives);
            assertFalse(GameServices.level().getObjectManager().activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.HPZSSEntryControlObjectInstance.class).getFirst()
                    .resultsReturnTransformationActive());
            assertTrue(GameServices.level().getObjectManager().activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.HPZSuperEmeraldReturnEffectObjectInstance.class).isEmpty());
        } finally {loop.closePresence();}
    }

    private void assertPhysicalEntry(CanonicalEntryMatrixSupport.Row row, int stage) throws Exception {
        var fixture = support.boot(row);
        // Declared persistent progress only, before the controller first dispatch.
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(2,2,2,2,2,2,2), true);
        var neutral = new Bk2FrameInput(0,0,0,false,"");
        var input = new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(neutral, neutral));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        int lives=GameServices.gameState().getLives();
        try {
            for (int frame = 0; frame < 200; frame++) advance(loop, support, row, 1);
            selectAndReplayIncomingStage(loop, input, fixture, support, row, stage, lives);
        } finally {loop.closePresence();}
    }

    /** Reuses actual placed geometry in an already released sanctuary; never boots or resets it. */
    static Sonic3kSpecialStageProvider selectAndReplayIncomingStage(GameLoop loop, InputHandler input,
            HeadlessTestFixture fixture, CanonicalEntryMatrixSupport support,
            CanonicalEntryMatrixSupport.Row row, int stage, int lives) throws Exception {
        var neutral = new Bk2FrameInput(0, 0, 0, false, "");
        var main = GameServices.sprites().getMainPlayable();
        assertFalse(main.isObjectControlled(), "actual non-conversion intro has released");
        var objects = GameServices.level().getObjectManager();
        var pedestal = objects.activeObjectsOfType(HPZSuperEmeraldObjectInstance.class).stream()
                .filter(object -> object.getSpawn().subtype() == stage).findFirst().orElseThrow();
        assertTrue(pedestal.isSelectable());
        assertEquals(HPZSuperEmeraldObjectInstance.Display.GRAY, pedestal.display());
        // A real fresh jump retires the arrival pad's standing owner before
        // moving this local contact fixture to a different placed platform.
        var jump = new Bk2FrameInput(0, 0x10, 1, false, "");
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(jump, neutral));
        advance(loop, support, row, 1);
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(neutral, jump));
        advance(loop, support, row, 1);
        assertTrue(main.getAir(), "ordinary jump released the arrival pad");
        assertNull(objects.getRidingObject(main), "jump owns prior ride retirement");
        // Declared local falling-contact stimulus on the actual placed native geometry.
        // The manager must establish standing; no contact, selection or timer is seeded.
        NativePositionOps.writeXPosPreserveSubpixel(main, pedestal.getX());
        NativePositionOps.writeYPosPreserveSubpixel(main,
                pedestal.getY() - 0x10 - main.getYRadius() - 4);
        main.setXSpeed((short)0);main.setGSpeed((short)0);main.setYSpeed((short)0x100);
        main.setAir(true);main.setJumping(false);
        GameServices.camera().updatePosition(true);
        for (int frame = 0; frame < 32 && !main.isObjectControlled(); frame++) advance(loop,support,row,1);
        // Selection itself is gated by this exact pedestal's real manager
        // ride in update(). Native control $81 then owns movement; a folded
        // end-frame ride record is not the contact/selection contract.
        assertTrue(main.isObjectControlled(), "loc908DE physical contact selected the pedestal");
        assertEquals(5, main.getAnimationId());
        assertEquals(0, main.getYSpeed(), "actual top landing stopped the fall");
        assertEquals(pedestal.getY() - 0x10 - main.getYRadius() - 1,
                main.getCentreY(), "SolidObjectTop's actual surface snap");
        // Native selection publishes after16 decrements. The8-tick window is
        // clamped to this real short phase; a45-tick raw restore would cross a fade/load.
        assertFalse(GameServices.fade().hasPendingCompletion());
        var registry=fixture.gameplayMode().getRewindRegistry();
        var before=registry.capture();advance(loop,support,row,8);var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(before,registry.capture(),"physical selection restore",8);
            advance(loop,support,row,8);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(after,registry.capture(),"physical selection replay",8);
        }
        assertTrue(fixture.gameplayMode().getRewindController().currentFrame()>10);
        int outgoing=fixture.gameplayMode().getRewindController().currentFrame();
        for(int frame=0;frame<300 && loop.getCurrentGameMode()==GameMode.LEVEL;frame++) {
            advance(loop,support,row,1);
            var history=fixture.gameplayMode().getRewindController();
            if(history!=null)outgoing=Math.max(outgoing,history.currentFrame());
        }
        assertEquals(GameMode.SPECIAL_STAGE,loop.getCurrentGameMode(),"actual stage load"+stage);
        var provider=assertInstanceOf(Sonic3kSpecialStageProvider.class,loop.getActiveSpecialStageProvider());
        assertEquals(stage,provider.getCurrentStage());
        // Scene drawing is omitted in this sparse lifecycle driver. Invoke
        // Engine's actual presentation binding with the resolved preset,
        // without constructing another engine/loop or changing gameplay RAM.
        bindIncomingPresentation(provider, support.config.getInt(
                com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS));
        assertTrue(provider.hasSpecialStages());assertTrue(provider.supportsRewind());
        var incoming=fixture.gameplayMode().getRewindController();
        assertTrue(incoming==null || incoming.currentFrame()<outgoing,"special world retires outgoing hub history");
        assertFalse(main.getDead());
        // The actual incoming provider/fade owns readiness. Raw restoration
        // begins only after that boundary releases, within this one special
        // world; no native return/results callback is crossed.
        for (int frame=0;frame<160 && (!provider.isEntryPresentationReady()
                ||GameServices.fade().isActive()||GameServices.fade().hasPendingCompletion());frame++)
            advanceSpecial(loop,provider,stage,lives,1);
        assertTrue(provider.isEntryPresentationReady());
        assertFalse(GameServices.fade().isActive());
        assertFalse(GameServices.fade().hasPendingCompletion());
        assertEquals(support.config.getInt(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS),
                provider.getSpecialStageViewport().logicalWidth(), "actual resolved incoming presentation width");
        var specialBefore=registry.capture();
        advanceSpecial(loop,provider,stage,lives,45);var specialAfter=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(specialBefore);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(specialBefore,registry.capture(),"incoming Super-stage restore",45);
            advanceSpecial(loop,provider,stage,lives,45);
            TestS3kCanonicalEntryLifecycle.assertSameWorld(specialAfter,registry.capture(),"incoming Super-stage replay",45);
        }
        return provider;
    }

    @ParameterizedTest(name = "actual full hub load / native target publication {0}")
    @ValueSource(strings = {"sonic","knuckles"})
    void actualFreshHubTransitionPublishesItsNativeTargetPalette(String character) throws Exception {
        var origin = new CanonicalEntryMatrixSupport() {
            @Override TestS3kCanonicalEntryLifecycle.Act binding() {
                return new TestS3kCanonicalEntryLifecycle.Act("LRZ1",9,0);
            }
        };
        var row = new CanonicalEntryMatrixSupport.Row(character,WidescreenAspect.NATIVE_4_3,"off","");
        origin.saveConfiguration();
        var fixture=origin.boot(row);
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(1,1,1,1,1,1,1),false);
        var neutral=new Bk2FrameInput(0,0,0,false,"");var input=new InputHandler();
        input.setLogicalOverride(RecordedInputSnapshots.fromBk2(neutral,neutral));
        var loop=new GameLoop(input);loop.setGameplayMode(fixture.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        var source=GameServices.level().getObjectManager();int lives=GameServices.gameState().getLives();
        try {
            // Declared transition stimulus to distinguish actual production fade/assembly
            // from direct fixture boot. This is not physical giant-ring route certification.
            GameServices.level().requestZoneAndAct(0x17,1,true);
            for(int frame=0;frame<400 && GameServices.level().getObjectManager()==source;frame++) {
                loop.step();GameServices.graphics().runPendingRenderThreadTasks();
            }
            assertNotSame(source,GameServices.level().getObjectManager());
            for(int frame=0;frame<400 && (GameServices.fade().isActive()
                    ||GameServices.fade().hasPendingCompletion()
                    ||GameServices.level().hasPendingFreshLevelTransitionBoundary());frame++) {
                loop.step();GameServices.graphics().runPendingRenderThreadTasks();
            }
            assertEquals(GameMode.LEVEL,loop.getCurrentGameMode());
            assertFalse(GameServices.fade().isActive());assertFalse(GameServices.fade().hasPendingCompletion());
            assertFalse(GameServices.level().hasPendingFreshLevelTransitionBoundary());
            support.assertOwners(row);assertEquals(lives,GameServices.gameState().getLives());
            assertTrue(GameServices.level().getLevelGamestate().isTimerPaused());
            var rom=GameServices.rom().getRom();
            byte[] nativeLine1=rom.readBytes(character.equals("knuckles")
                    ?Sonic3kConstants.PAL_SONIC_TAILS_ADDR:Sonic3kConstants.PAL_CUTSCENE_KNUX_ADDR,32);
            byte[] nativeLines23=rom.readBytes(Sonic3kConstants.HPZ_MAIN_PALETTE_ADDR+32,64);
            var errors=new java.util.ArrayList<String>();
            for(int line=1;line<4;line++) {
                byte[] expected=line==1?nativeLine1:java.util.Arrays.copyOfRange(nativeLines23,(line-2)*32,(line-1)*32);
                // Master Emerald cyclic colors have a separate owner. Compare static cells.
                for(int color=0;color<16;color++) {
                    if(line==3&&(color==1||color==2))continue;
                    int word=((expected[color*2]&255)<<8)|(expected[color*2+1]&255);
                    var value=GameServices.level().getCurrentLevel().getPalette(line).getColor(color);
                    int actual=((value.r&255)*7+127)/255*2|(((value.g&255)*7+127)/255*2<<4)
                            |(((value.b&255)*7+127)/255*2<<8);
                    var target=GameServices.paletteOwnershipRegistryOrNull().targetSegaData(line,color,1);
                    int targetWord=((target[0]&255)<<8)|(target[1]&255);
                    if((word&0xEEE)!=(actual&0xEEE)||(word&0xEEE)!=(targetWord&0xEEE))
                        errors.add("line"+line+" color"+color+" native="+Integer.toHexString(word)
                                +" current="+Integer.toHexString(actual)+" target="+Integer.toHexString(targetWord));
                }
            }
            assertTrue(errors.isEmpty(),()->"actual native B5 target/current publication: "+errors);
        } finally {loop.closePresence();}
    }

    private static void bindIncomingPresentation(SpecialStageProvider provider, int width)
            throws ReflectiveOperationException {
        Engine presentation = org.mockito.Mockito.mock(Engine.class,
                org.mockito.Mockito.CALLS_REAL_METHODS);
        var projection = Engine.class.getDeclaredField("projectionWidth");
        projection.setAccessible(true);
        projection.setDouble(presentation, width);
        var bind = Engine.class.getDeclaredMethod("applySpecialStageViewport", SpecialStageProvider.class);
        bind.setAccessible(true);
        bind.invoke(presentation, provider);
    }

    private static void advanceSpecial(GameLoop loop,Sonic3kSpecialStageProvider provider,
                                       int stage,int lives,int frames) {
        for(int frame=0;frame<frames;frame++) {
            loop.step();GameServices.graphics().runPendingRenderThreadTasks();
            assertEquals(GameMode.SPECIAL_STAGE,loop.getCurrentGameMode(),"bounded incoming special-world window");
            assertSame(provider,loop.getActiveSpecialStageProvider());
            assertEquals(stage,provider.getCurrentStage());
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(lives,GameServices.gameState().getLives(),"no hidden reload during incoming replay");
        }
    }

    private static void advance(GameLoop loop, CanonicalEntryMatrixSupport support,
                                CanonicalEntryMatrixSupport.Row row, int frames) {
        int lives=GameServices.gameState().getLives();
        for(int frame=0;frame<frames;frame++) {
            loop.step();GameServices.graphics().runPendingRenderThreadTasks();
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(lives,GameServices.gameState().getLives(),"no hidden life-loss load");
            support.assertOwners(row);
        }
    }
}
