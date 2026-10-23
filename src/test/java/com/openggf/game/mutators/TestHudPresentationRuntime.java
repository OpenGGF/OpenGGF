package com.openggf.game.mutators;

import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.level.Pattern;
import com.openggf.level.objects.*;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.mods.mutators.HudPresentationPolicy;
import com.openggf.game.mutators.TransientMutatorPolicySource.Entry;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.*;

import static com.openggf.mods.mutators.HudPresentationPolicy.Mode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CPU-only native publication and world-owned presentation; no ROM or display required. */
@Isolated
class TestHudPresentationRuntime {
    EngineContext previous;
    GraphicsManager graphics;
    Camera camera;
    GameStateManager game;
    HudRenderManager hud;
    WorldSession world;
    GameplayModeContext context;
    HudPresentationRuntime runtime;
    Source source;
    LevelGamestate level;

    @BeforeEach void setup() throws ReflectiveOperationException {
        previous = EngineServices.current();
        graphics = new GraphicsManager(); graphics.setHeadlessMode(true);
        var engine = mock(EngineContext.class); when(engine.graphics()).thenReturn(graphics);
        when(engine.configuration()).thenReturn(previous.configuration());
        EngineServices.configure(engine);
        camera = mock(Camera.class); when(camera.getHeight()).thenReturn((short)224);
        var pipeline = new UiRenderPipeline(graphics);
        var pipelineField = GraphicsManager.class.getDeclaredField("uiRenderPipeline");
        pipelineField.setAccessible(true); pipelineField.set(graphics,pipeline);
        graphics.bindRuntimeManagedReferences(camera,new FadeManager());
        game = mock(GameStateManager.class); when(game.getScore()).thenReturn(123); when(game.getLives()).thenReturn(3);
        hud = new HudRenderManager(graphics, camera, game);
        hud.setDigitPatternIndex(200); hud.setLivesNumbersPatternIndex(220);
        hud.setStaticHudArt(1000, new HudStaticArt(new Pattern[7], frame(0,40,16), frame(20,40,16),
                frame(40,32,16), frame(60,32,16), frame(80,40,16), frame(100,40,16), frame(120,32,16)));
        graphics.getUiRenderPipeline().setHudRenderManager(hud);
        source = new Source();
        var module = mock(GameModule.class); when(module.getIdentifier()).thenReturn("hud-test");
        when(module.getGameService(WorldSessionPolicyProvider.class)).thenReturn(w -> {
            runtime = new HudPresentationRuntime(w);
            return CompositeWorldSessionPolicyState.of(source, runtime);
        });
        world = new WorldSession(module);
        HudProfileAccess.bindWorld(hud,world);
        context = mock(GameplayModeContext.class); when(context.getWorldSession()).thenReturn(world);
        when(context.getCamera()).thenReturn(camera);
        level = new LevelGamestate(); level.addRings(7); LevelRingDisplay.publish(level);
    }
    @AfterEach void cleanup() { EngineServices.configure(previous); }

    @Test void optionalTimeFormatResizesTheOwnedPlaqueAndReplaysWithinItsBounds(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
        HudProfileAccess.install(hud, new HudProfile(List.of(new HudRow(true,
                HudLabel.TIME, HudMetric.TIME, 16, 24, 56, 24, 4, HudWarningPolicy.NONE))));
        activate(BOUNCE, 7);
        assertEquals(32, runtime.capture().pairs().getFirst().readout().valueWidth());
        assertEquals(68, runtime.capture().plaque().width(), .001);

        HudProfileAccess.setHostTime(hud, 119, true);
        tick();
        assertEquals(56, runtime.capture().pairs().getFirst().readout().valueWidth());
        assertEquals(92, runtime.capture().plaque().width(), .001);
        var frame = prepare();
        for (int i = 0; i < 350; i++) {
            tick();
            assertWithinViewport(project(frame), 320);
        }
        var saved = runtime.capture();
        for (int i = 0; i < 8; i++) tick();
        var forward = runtime.capture();
        runtime.restore(saved);
        for (int i = 0; i < 8; i++) tick();
        assertEquals(forward, runtime.capture(), "readout bounds retain the owned forward phase");

        HudProfileAccess.setHostTime(hud, -1, false);
        tick();
        assertEquals(32, runtime.capture().pairs().getFirst().readout().valueWidth());
        assertEquals(68, runtime.capture().plaque().width(), .001);
        var config = com.openggf.configuration.SonicConfigurationService.createStandalone(directory);
        config.setConfigValue(com.openggf.configuration.SonicConfiguration.DISPLAY_HUD_TIME_HUNDREDTHS, true);
        HudProfileAccess.applyDisplaySettings(hud, config);
        tick();
        assertEquals(56, runtime.capture().pairs().getFirst().readout().valueWidth());
        assertEquals(92, runtime.capture().plaque().width(), .001);
        assertEquals(0, level.getTimerFrames(), "presentation cannot advance gameplay time");
    }

    @Test void bounceMovesOneCompactPlaqueAndReflectsEveryRowTogetherInNativeAndWideViewports() {
        for (int width : new int[]{320,400,640}) {
            graphics.setProjectionWidth(width); hud.setViewportWidth(width);
            activate(BOUNCE, 7);
            var nativeFrame = prepare();
            float firstX = project(nativeFrame).tiles().getFirst().x();
            var firstPairs = runtime.capture().pairs();
            var offsets = firstPairs.stream().map(p -> p.y()-firstPairs.getFirst().y()).toList();
            double previousVx = firstPairs.getFirst().vx(), previousVy = firstPairs.getFirst().vy();
            boolean reflectedX = false, reflectedY = false;
            for (int tick=0;tick<350;tick++) {
                tick(); var projected = project(nativeFrame);
                assertWithinViewport(projected, width);
                assertTrue(plaqueHeight(projected)<=80, "lives must join the compact stack, not stay at y=200");
                var pairs = runtime.capture().pairs();
                var first = pairs.getFirst();
                reflectedX |= previousVx*first.vx()<0;
                reflectedY |= previousVy*first.vy()<0;
                previousVx = first.vx(); previousVy = first.vy();
                for (int i=0;i<pairs.size();i++) {
                    var pair = pairs.get(i);
                    assertEquals(first.x(), pair.x(), .001);
                    assertEquals(first.vx(), pair.vx(), .001);
                    assertEquals(first.vy(), pair.vy(), .001);
                    assertEquals(offsets.get(i), pair.y()-first.y(), .001, "rows must stay attached through reflections");
                }
                assertPaired(projected);
            }
            assertNotEquals(firstX, project(nativeFrame).tiles().getFirst().x());
            assertTrue(reflectedX, "the complete plaque must reflect at a horizontal edge");
            assertTrue(reflectedY, "the complete plaque must reflect at a vertical edge");
        }
    }

    @Test void retainedWarningLabelsAndFreshVblankValuesUseOnePose() {
        HudProfileAccess.installWarningPolicy(hud, new com.openggf.game.internal.HudWarningPolicyProvider() {
            public boolean isFlashFrame(int frame) { return frame==0; }
            public boolean isTimerWarning(int seconds) { return false; }
        }, () -> 0);
        for (int width : new int[]{320,400,640}) {
            graphics.setProjectionWidth(width); hud.setViewportWidth(width);
            level.setRings(0); LevelRingDisplay.publish(level);
            activate(ORBIT, 2);
            var nativeFrame = prepare();
            var labels = new SpritePresentation.Frame(nativeFrame.tiles().stream()
                    .filter(t -> t.layer()==SpritePresentation.Layer.HUD).toList());
            for(int i=0;i<11;i++) tick();
            assertEquals(nativeFrame, prepare(), "motion must never alter native publication");
            LevelRingDisplay.writeWithoutRefresh(level, 50);
            var counters = SpritePresentationRenderer.prepare(graphics,0,0,
                    () -> HudProfileAccess.drawVBlankCounters(hud, level, null, true));
            counters = new SpritePresentation.Frame(counters.tiles().stream()
                    .filter(t -> t.layer()==SpritePresentation.Layer.HUD_COUNTERS).toList());
            var label = project(labels).tiles().stream().filter(t -> t.patternId()==1100).findFirst().orElseThrow();
            var zero = project(counters).tiles().stream().filter(t -> t.subject().id().equals(label.subject().id())
                    && t.layer()==SpritePresentation.Layer.HUD_COUNTERS).findFirst().orElseThrow();
            assertEquals(200, zero.patternId(), "published ring counter must survive silent gameplay writes");
            assertEquals(label.x()+44, zero.x(), .001);
            assertEquals(label.y(), zero.y(), .001);
            assertEquals(0, level.getTimerFrames(), "counter projection must not advance native state");
            assertEquals(16+(width-320)/2, labels.tiles().getFirst().x(), "retained native geometry remains untouched");
        }
    }

    @Test void shuffleSamplesCompletePairsOncePerActivationAndRewindRestoresItsRandomStream() {
        activate(SHUFFLE, 99); var nativeFrame=prepare();
        var readouts=runtime.capture().pairs().stream().map(HudPresentationRuntime.Pair::readout).toList();
        var stockOrder=new ArrayList<Integer>(); for(int i=0;i<readouts.size();i++) stockOrder.add(i);
        assertNotEquals(stockOrder,runtime.capture().order(),"activation must shuffle the readout slots");
        var naturalLabels=List.of(1000,1020,1080,1120);
        var projectedLabels=project(nativeFrame).tiles().stream().filter(t->t.layer()==SpritePresentation.Layer.HUD)
                .sorted(Comparator.comparingDouble(SpritePresentation.Tile::y))
                .map(SpritePresentation.Tile::patternId).filter(naturalLabels::contains).distinct().toList();
        assertNotEquals(naturalLabels,projectedLabels,"the native labels must actually change places onscreen");
        for(int i=0;i<28;i++) tick();
        var saved=runtime.capture(); var initial=project(nativeFrame);
        for(int i=0;i<65;i++) tick();
        var expected=runtime.capture(); var shuffled=project(nativeFrame);
        assertEquals(initial,shuffled,"musical chairs samples once, not on each period"); assertPaired(shuffled);
        assertTrue(plaqueHeight(shuffled)<=80);
        runtime.restore(saved);
        for(int i=0;i<65;i++) tick();
        assertEquals(expected,runtime.capture()); assertEquals(shuffled,project(nativeFrame));
        assertThrows(UnsupportedOperationException.class, () -> saved.pairs().clear());
        activate(SHUFFLE,99);
        assertEquals(initial,project(nativeFrame),"a new activation with the same seed must reproduce its placement");
        activate(SHUFFLE,0);
        var zeroSeed=project(nativeFrame); activate(SHUFFLE,0);
        assertEquals(zeroSeed,project(nativeFrame),"zero seed must use a reproducible nonzero stream");
    }

    @Test void allMotionModesReplayExactlyAfterRewindAndIgnoreNonActivePresentations() {
        for(var mode:List.of(BOUNCE,ORBIT,EVADE,SCALE)) {
            activate(mode,8); var nativeFrame=prepare();
            for(int i=0;i<20;i++) tick();
            var saved=runtime.capture();
            for(var phase:GameplayObservation.Phase.values()) if(phase!=GameplayObservation.Phase.ACTIVE)
                runtime.onPresentation(context,LogicalInputSnapshot.neutral(),phase);
            assertEquals(saved,runtime.capture());
            for(int i=0;i<31;i++) tick();
            var expected=runtime.capture(); var pixels=project(nativeFrame);
            runtime.restore(saved); for(int i=0;i<31;i++) tick();
            assertEquals(expected,runtime.capture()); assertEquals(pixels,project(nativeFrame));
            assertPaired(pixels);
        }
    }

    @Test void pausedLeaseRemovalRestoresNativePoseAndHiddenHudImmediately() {
        for(var mode:List.of(BOUNCE,HIDE)) {
            activate(mode,7); var nativeFrame=prepare();
            assertNotEquals(nativeFrame,project(nativeFrame));
            source.entries=List.of();
            runtime.onPresentation(context,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.PAUSED);
            assertFalse(runtime.active()); assertFalse(runtime.hidden());
            assertTrue(runtime.capture().pairs().isEmpty()); assertNull(runtime.capture().plaque());
            assertSame(nativeFrame,project(nativeFrame),"paused cancellation must restore the retained native packet");
        }
    }

    @Test void heldLeaseCancellationRestoresNativePoseAndHiddenHudImmediately() {
        for(var mode:List.of(SCALE,HIDE)) for(boolean expired:new boolean[]{false,true}) {
            activate(mode,7); var nativeFrame=prepare();
            assertNotEquals(nativeFrame,project(nativeFrame));
            var lease=source.entries.getFirst();
            source.entries=expired?List.of(new Entry(lease.owner(),lease.activationId(),lease.policy(),1000,0,false)):List.of();
            var cancelledSource=source.capture();
            runtime.onPresentation(context,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.HELD);
            assertFalse(runtime.active()); assertFalse(runtime.hidden());
            assertTrue(runtime.capture().pairs().isEmpty()); assertNull(runtime.capture().plaque());
            assertSame(nativeFrame,project(nativeFrame),"held cancellation must restore even a retained expired lease");
            assertEquals(cancelledSource,source.capture(),"HUD cancellation must not alter the source lease");
        }
    }

    @Test void replacedLeaseClearsCachedPoseDuringNonActivePhasesAndWaitsForActiveAdmission() {
        for(var phase:List.of(GameplayObservation.Phase.PAUSED,GameplayObservation.Phase.HELD,
                GameplayObservation.Phase.REWIND)) for(int changedField=0;changedField<3;changedField++) {
            activate(HIDE,11); var nativeFrame=prepare(); var lease=source.entries.getFirst();
            Entry replacement=switch(changedField) {
                case 0 -> new Entry(lease.owner(),++source.ordinal,lease.policy(),0,1000,false);
                case 1 -> new Entry("replacement-owner",lease.activationId(),lease.policy(),0,1000,false);
                default -> new Entry(lease.owner(),lease.activationId(),new HudPresentationPolicy(BOUNCE,30,11),0,1000,false);
            };
            // A newer winner cancels the cached pose even when its previous lease still exists.
            source.entries=changedField==0?List.of(lease,replacement):List.of(replacement);
            runtime.onPresentation(context,LogicalInputSnapshot.neutral(),phase);
            assertFalse(runtime.active()); assertFalse(runtime.hidden());
            assertTrue(runtime.capture().pairs().isEmpty()); assertNull(runtime.capture().plaque());
            assertSame(nativeFrame,project(nativeFrame));
            var cancelled=runtime.capture();
            runtime.onPresentation(context,LogicalInputSnapshot.neutral(),phase);
            assertEquals(cancelled,runtime.capture(),"a replacement cannot initialize before an active presentation");
            tick();
            assertTrue(runtime.active());
            assertEquals(replacement.owner(),runtime.capture().owner());
            assertEquals(replacement.activationId(),runtime.capture().activation());
            assertEquals(replacement.policy(),runtime.capture().policy());
            assertEquals(1,runtime.capture().presentations());
            assertNotEquals(nativeFrame,project(nativeFrame));
        }
    }

    @Test void unchangedLeasePreservesExactStateWithoutSamplingLayoutOrPlayerOnNonActivePresentations() {
        var sprites=mock(SpriteManager.class); var player=mock(AbstractPlayableSprite.class);
        when(sprites.getMainPlayable()).thenReturn(player); when(context.getSpriteManager()).thenReturn(sprites);
        var input=LogicalInputSnapshot.ofPlayers(PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT,
                AbstractPlayableSprite.INPUT_RIGHT,0,0,true,true),null);
        for(var mode:HudPresentationPolicy.Mode.values()) {
            graphics.setProjectionWidth(320); hud.setViewportWidth(320);
            when(camera.getHeight()).thenReturn((short)224);
            activate(mode,19); var nativeFrame=prepare();
            for(int i=0;i<7;i++) tick();
            var saved=runtime.capture(); var savedSource=source.capture(); var projected=project(nativeFrame);
            graphics.setProjectionWidth(640); hud.setViewportWidth(640);
            when(camera.getHeight()).thenReturn((short)160);
            when(camera.getXWithShake()).thenReturn((short)900); when(camera.getYWithShake()).thenReturn((short)500);
            when(player.getCentreX()).thenReturn((short)1100); when(player.getCentreY()).thenReturn((short)600);
            clearInvocations(context,camera,sprites,player);
            for(var phase:GameplayObservation.Phase.values()) if(phase!=GameplayObservation.Phase.ACTIVE) {
                runtime.onPresentation(context,input,phase);
                assertEquals(saved,runtime.capture(),"unchanged leases must freeze clock, RNG, geometry and velocity in "+phase);
                assertEquals(projected,project(nativeFrame),"input and moved players cannot pose a frozen HUD");
                assertEquals(savedSource,source.capture());
            }
            verify(context,never()).getCamera(); verify(context,never()).getSpriteManager();
            verifyNoInteractions(camera,sprites,player);
        }
    }

    @Test void scaleChangesTileSizeAndEvadeMovesAwayFromTheScreenSpaceLeader() {
        activate(SCALE,5); var nativeFrame=prepare();
        assertTrue(project(nativeFrame).tiles().stream().anyMatch(t -> t.width()!=8));
        var sprites=mock(SpriteManager.class); var player=mock(AbstractPlayableSprite.class);
        when(sprites.getMainPlayable()).thenReturn(player); when(context.getSpriteManager()).thenReturn(sprites);
        activate(EVADE,5);
        var before=project(nativeFrame).tiles().getFirst();
        when(player.getCentreX()).thenReturn((short)(before.x()+16));
        when(player.getCentreY()).thenReturn((short)(before.y()+8));
        double near=Math.hypot(before.x()+20-player.getCentreX(),before.y()+8-player.getCentreY());
        for(int i=0;i<15;i++) tick();
        var after=project(nativeFrame).tiles().getFirst();
        assertTrue(Math.hypot(after.x()+20-player.getCentreX(),after.y()+8-player.getCentreY())>near+5);
    }

    @Test void hideExpiryLoadFailureAndTeardownRestoreTheOriginalNativeFrame() {
        var stockFrame=prepare();
        assertTrue(stockFrame.tiles().stream().allMatch(t->t.subject()==SpritePresentation.Subject.WORLD),
                "stock publication must keep its original identity when no policy lease exists");
        activate(HIDE,1); var nativeFrame=prepare();
        var status=new SpritePresentation.Tile(SpritePresentation.Layer.HUD,9999,0,false,false,true,
                280,8,8,8,false,0,false,1);
        var combinedTiles=new ArrayList<>(nativeFrame.tiles()); combinedTiles.add(status);
        var combined=new SpritePresentation.Frame(combinedTiles);
        assertEquals(List.of(status),project(combined).tiles(),"hide must preserve non-stock HUD such as Chaos status");
        var lease=source.entries.getFirst();
        source.entries=List.of(new TransientMutatorPolicySource.Entry(lease.owner(),lease.activationId(),lease.policy(),1000,0,false));
        tick(); assertSame(nativeFrame,project(nativeFrame)); assertEquals(stockFrame,prepare());
        activate(HIDE,1); source.entries=List.of(); tick(); assertSame(nativeFrame,project(nativeFrame));
        activate(BOUNCE,1); runtime.beforeAssembly(LevelLoadCause.FULL_DEATH_RELOAD);
        assertSame(nativeFrame,project(nativeFrame));
        activate(SCALE,1); runtime.failedAssembly(LevelLoadCause.FULL_LEVEL_ASSEMBLY);
        assertSame(nativeFrame,project(nativeFrame));
        activate(ORBIT,1); runtime.closeScreens(); assertSame(nativeFrame,project(nativeFrame));
        activate(HIDE,1); var saved=runtime.capture(); runtime.retire();
        assertSame(nativeFrame,project(nativeFrame)); assertThrows(IllegalStateException.class,()->runtime.restore(saved));
    }

    @Test void projectionIsReadOnlyAndRepeatedProjectionDoesNotTransformTwice() {
        activate(BOUNCE,4); var nativeFrame=prepare(); var saved=runtime.capture();
        var projected=project(nativeFrame);
        assertEquals(saved,runtime.capture());
        assertSame(projected,project(projected));
        assertEquals(saved,runtime.capture());
        assertEquals(16,nativeFrame.tiles().getFirst().x());
    }

    @Test void ordinaryGameplayLeaveClearsStageTransitionsButPreservesPauseHoldAndRewind() {
        activate(BOUNCE,4); var saved=runtime.capture();
        for(var phase:List.of(GameplayObservation.Phase.PAUSED,GameplayObservation.Phase.HELD,GameplayObservation.Phase.REWIND))
            runtime.leaveOrdinaryGameplay(phase);
        assertEquals(saved,runtime.capture());
        for(var phase:List.of(GameplayObservation.Phase.SETUP,GameplayObservation.Phase.STAGE,GameplayObservation.Phase.TRANSITION)) {
            activate(BOUNCE,4); runtime.leaveOrdinaryGameplay(phase);
            assertFalse(runtime.active());
        }
    }

    @Test void wideEvadeUsesTheRenderedLeaderPositionWithoutAddingTheHudInset() {
        graphics.setProjectionWidth(640); hud.setViewportWidth(640);
        var sprites=mock(SpriteManager.class); var player=mock(AbstractPlayableSprite.class);
        when(sprites.getMainPlayable()).thenReturn(player); when(context.getSpriteManager()).thenReturn(sprites);
        when(camera.getXWithShake()).thenReturn((short)900); when(camera.getYWithShake()).thenReturn((short)500);
        when(player.getCentreX()).thenReturn((short)1118); when(player.getCentreY()).thenReturn((short)516);
        activate(EVADE,5);
        var first=runtime.capture().pairs().getFirst();
        // The compact plaque is centred at screen x=222. The leader is at x=218,
        // so it must flee right. Applying the native HUD inset to the leader
        // would incorrectly place the leader outside the proximity radius.
        assertTrue(first.vx()>0,"the HUD must evade the actual wide-viewport leader");
        double before=plaqueDistance(runtime.capture().pairs(),218,16);
        for(int i=0;i<10;i++) tick();
        assertTrue(plaqueDistance(runtime.capture().pairs(),218,16)>before+10);
    }

    @Test void scalePulsesTheWholePlaqueBetweenSeventyFiveAndOneHundredTwentyFivePercent() {
        for(int width:new int[]{320,400,640}) {
            graphics.setProjectionWidth(width); hud.setViewportWidth(width);
            activate(SCALE,5); var nativeFrame=prepare();
            double minScale=Double.POSITIVE_INFINITY,maxScale=0;
            for(int i=0;i<60;i++) {
                tick(); var pairs=runtime.capture().pairs(); double scale=pairs.getFirst().scale();
                minScale=Math.min(minScale,scale); maxScale=Math.max(maxScale,scale);
                assertTrue(scale>=.75 && scale<=1.25,"breathing scale must stay inside its specified range");
                double rowOffset=0;
                for(var pair:pairs) {
                    assertEquals(scale,pair.scale(),.000001,"all rows share one plaque scale and phase");
                    assertEquals(rowOffset*scale,pair.y()-pairs.getFirst().y(),.001);
                    rowOffset+=pair.readout().compactHeight()+2;
                }
                var projected=project(nativeFrame); assertWithinViewport(projected,width); assertPaired(projected);
                for(int tile=0;tile<nativeFrame.tiles().size();tile++)
                    assertEquals(nativeFrame.tiles().get(tile).patternId(),projected.tiles().get(tile).patternId(),
                            "scaling must retain published label and numeric patterns");
                assertEquals(nativeFrame,prepare(),"scale cannot change the native publication");
            }
            // The 30-presentation period samples close to, but not exactly at, both sine extrema.
            assertEquals(.75,minScale,.002); assertEquals(1.25,maxScale,.002);
        }
    }

    @Test void orbitFollowsTheActualLeaderAndClampsTheCompletePlaqueInEveryViewport() {
        var sprites=mock(SpriteManager.class); var player=mock(AbstractPlayableSprite.class);
        when(sprites.getMainPlayable()).thenReturn(player); when(context.getSpriteManager()).thenReturn(sprites);
        for(int width:new int[]{320,400,640}) {
            graphics.setProjectionWidth(width); hud.setViewportWidth(width);
            when(camera.getXWithShake()).thenReturn((short)900); when(camera.getYWithShake()).thenReturn((short)500);
            int centreX=width/2;
            when(player.getCentreX()).thenReturn((short)(900+centreX));
            when(player.getCentreY()).thenReturn((short)612);
            activate(ORBIT,3); var nativeFrame=prepare();
            var centred=runtime.capture().pairs();
            assertTrue(Math.abs(plaqueCentreX(centred)-centreX)<=48);
            assertTrue(Math.abs(plaqueCentreY(centred)-112)<=36);
            when(player.getCentreX()).thenReturn((short)(900+centreX+24));
            when(player.getCentreY()).thenReturn((short)636);
            activate(ORBIT,3); var moved=runtime.capture().pairs();
            assertEquals(24,plaqueCentreX(moved)-plaqueCentreX(centred),.001);
            assertEquals(24,plaqueCentreY(moved)-plaqueCentreY(centred),.001);
            for(int[] corner:new int[][]{{0,0},{width,0},{0,224},{width,224}}) {
                when(player.getCentreX()).thenReturn((short)(900+corner[0]));
                when(player.getCentreY()).thenReturn((short)(500+corner[1]));
                for(int i=0;i<30;i++) {
                    tick(); var projected=project(nativeFrame); assertWithinViewport(projected,width);
                    assertTrue(plaqueHeight(projected)<=80); assertPaired(projected);
                }
            }
        }
    }

    @Test void shyPlaqueCapsTotalSpeedAndReflectsItsAttachedRowsAtEdges() {
        var sprites=mock(SpriteManager.class); var player=mock(AbstractPlayableSprite.class);
        when(sprites.getMainPlayable()).thenReturn(player); when(context.getSpriteManager()).thenReturn(sprites);
        activate(EVADE,5); var nativeFrame=prepare();
        boolean reflected=false;
        double previousVx=runtime.capture().pairs().getFirst().vx();
        for(int i=0;i<350;i++) {
            var before=runtime.capture().pairs();
            when(player.getCentreX()).thenReturn((short)(plaqueCentreX(before)-20));
            when(player.getCentreY()).thenReturn((short)(plaqueCentreY(before)-20));
            tick(); var pairs=runtime.capture().pairs(); var first=pairs.getFirst();
            assertTrue(Math.hypot(first.vx(),first.vy())<=4.000001,"cap applies to total speed, including diagonal drift");
            reflected|=previousVx>0 && first.vx()<0; previousVx=first.vx();
            for(var pair:pairs) {
                assertEquals(first.x(),pair.x(),.001);
                assertEquals(first.vx(),pair.vx(),.001); assertEquals(first.vy(),pair.vy(),.001);
            }
            assertWithinViewport(project(nativeFrame),320);
        }
        assertTrue(reflected,"the fleeing plaque must reflect at the right viewport edge");
        when(player.getCentreX()).thenReturn((short)-1000); when(player.getCentreY()).thenReturn((short)-1000);
        double displacement=plaqueDistance(runtime.capture().pairs(),62,43);
        for(int i=0;i<200;i++) tick();
        assertTrue(plaqueDistance(runtime.capture().pairs(),62,43)<displacement,
                "a distant leader leaves a small restoring drift toward the compact home position");
    }

    @Test void anotherWorldCannotAdvanceOrRestoreThisPlaque() {
        activate(BOUNCE,4); var nativeFrame=prepare(); var saved=runtime.capture();
        var otherModule=mock(GameModule.class); when(otherModule.getIdentifier()).thenReturn("other-hud-world");
        var otherSource=new Source();
        otherSource.entries=List.of(new Entry("other-owner",1,new HudPresentationPolicy(HIDE,30,4),0,1000,false));
        final HudPresentationRuntime[] otherRuntime=new HudPresentationRuntime[1];
        when(otherModule.getGameService(WorldSessionPolicyProvider.class)).thenReturn(w->{
            otherRuntime[0]=new HudPresentationRuntime(w);
            return CompositeWorldSessionPolicyState.of(otherSource,otherRuntime[0]);
        });
        var otherWorld=new WorldSession(otherModule);
        var otherContext=mock(GameplayModeContext.class); when(otherContext.getWorldSession()).thenReturn(otherWorld);
        for(var phase:GameplayObservation.Phase.values()) {
            runtime.onPresentation(otherContext,LogicalInputSnapshot.neutral(),phase);
            assertEquals(saved,runtime.capture(),"another world's lease cannot cancel this HUD in "+phase);
        }
        source.entries=List.of();
        for(var phase:List.of(GameplayObservation.Phase.PAUSED,GameplayObservation.Phase.HELD,
                GameplayObservation.Phase.REWIND)) {
            runtime.onPresentation(otherContext,LogicalInputSnapshot.neutral(),phase);
            assertEquals(saved,runtime.capture(),"cancellation must be observed through the owning world");
        }
        assertSame(nativeFrame,HudProfileAccess.projectPresentation(otherWorld,graphics,nativeFrame));
        assertThrows(IllegalArgumentException.class,()->otherRuntime[0].restore(saved));
        assertEquals("test",runtime.capture().owner(),"the lease owner comes from this world's source");
    }

    void activate(HudPresentationPolicy.Mode mode,int seed) {
        source.entries=List.of(new TransientMutatorPolicySource.Entry("test", ++source.ordinal,
                new HudPresentationPolicy(mode,30,seed),0,1000,false)); tick();
    }
    void tick() { runtime.onPresentation(context,LogicalInputSnapshot.neutral(),GameplayObservation.Phase.ACTIVE); }
    SpritePresentation.Frame prepare() {
        return SpritePresentationRenderer.prepare(graphics,camera.getXWithShake(),camera.getYWithShake(),()->hud.draw(level,null));
    }
    SpritePresentation.Frame project(SpritePresentation.Frame frame) {
        return HudProfileAccess.projectPresentation(world,graphics,frame);
    }
    static void assertWithinViewport(SpritePresentation.Frame frame,int width) {
        for(var tile:frame.tiles()) {
            assertTrue(tile.x()>=0 && tile.x()+tile.width()<=width+.01,tile.toString());
            assertTrue(tile.y()>=0 && tile.y()+tile.height()<=224+.01,tile.toString());
        }
    }
    static double plaqueHeight(SpritePresentation.Frame frame) {
        return frame.tiles().stream().mapToDouble(t->t.y()+t.height()).max().orElseThrow()
                -frame.tiles().stream().mapToDouble(SpritePresentation.Tile::y).min().orElseThrow();
    }
    static double plaqueCentreX(List<HudPresentationRuntime.Pair> pairs) {
        return pairs.stream().mapToDouble(p->p.x()+p.readout().compactWidth()*p.scale()).max().orElseThrow()/2
                +pairs.stream().mapToDouble(HudPresentationRuntime.Pair::x).min().orElseThrow()/2;
    }
    static double plaqueCentreY(List<HudPresentationRuntime.Pair> pairs) {
        return pairs.stream().mapToDouble(p->p.y()+p.readout().compactHeight()*p.scale()).max().orElseThrow()/2
                +pairs.stream().mapToDouble(HudPresentationRuntime.Pair::y).min().orElseThrow()/2;
    }
    static double plaqueDistance(List<HudPresentationRuntime.Pair> pairs,double x,double y) {
        return Math.hypot(plaqueCentreX(pairs)-x,plaqueCentreY(pairs)-y);
    }
    static void assertPaired(SpritePresentation.Frame frame) {
        var groups=new HashMap<String,List<SpritePresentation.Tile>>();
        for(var tile:frame.tiles()) groups.computeIfAbsent(tile.subject().id(),unused->new ArrayList<>()).add(tile);
        assertEquals(4,groups.size());
        for(var tiles:groups.values()) {
            float labelRight=(float)tiles.stream().filter(t->t.layer()==SpritePresentation.Layer.HUD)
                    .mapToDouble(t->t.x()+t.width()).max().orElseThrow();
            float valueLeft=(float)tiles.stream().filter(t->t.layer()==SpritePresentation.Layer.HUD_COUNTERS)
                    .mapToDouble(SpritePresentation.Tile::x).min().orElseThrow();
            float scale=tiles.getFirst().width()/8;
            assertEquals(labelRight+4*scale,valueLeft,.001,"label/current-value must stay compact and paired");
        }
    }
    static SpriteMappingFrame frame(int id,int width,int height) {
        return new SpriteMappingFrame(List.of(new SpriteMappingPiece(0,0,width/8,height/8,id,false,false,0)));
    }
    static final class Source implements WorldSessionPolicyState,TransientMutatorPolicySource,RewindSnapshottable<List<Entry>> {
        List<Entry> entries=List.of(); long ordinal;
        public List<Entry> entries(){return entries;}
        public boolean acknowledge(long id){return false;} public boolean report(long id,String status){return true;}
        public <T>T getService(Class<T> type){return type==TransientMutatorPolicySource.class?type.cast(this):null;}
        public RewindSnapshottable<?> rewindAdapter(){return this;} public String key(){return "test-hud-source";}
        public List<Entry> capture(){return List.copyOf(entries);} public void restore(List<Entry> state){entries=state;}
        public void beforeAssembly(LevelLoadCause cause){} public void bindRoster(SpriteManager sprites){}
        public void failedAssembly(LevelLoadCause cause){} public void closeScreens(){} public void retire(){}
    }
}
