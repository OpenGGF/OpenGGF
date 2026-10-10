package com.openggf.graphics;

import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.*;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.sprites.playable.*;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.tests.MutatorPlayerStateAssertions.assertNativeStateEquals;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestPlayableMutatorPresentation {
    // Replayed DPLC versions pass the same atlas governance as native actor art.
    private static final int SONIC_BODY = PatternAtlasRange.SIDEKICK_BANKS.base() + 0x100;
    private static final int TAILS_BODY = PatternAtlasRange.SIDEKICK_BANKS.base() + 0x200;
    private static final int TAILS_APPENDAGE = PatternAtlasRange.SIDEKICK_BANKS.base() + 0x300;
    private static final int OTHER_BODY = PatternAtlasRange.SIDEKICK_BANKS.base() + 0x400;
    private static final int DUST = PatternAtlasRange.TRANSIENT_EFFECTS.base() + 0x100;
    private static final int SHIELD = PatternAtlasRange.TRANSIENT_EFFECTS.base() + 0x200;
    private static final int STARS = PatternAtlasRange.TRANSIENT_EFFECTS.base() + 0x300;
    private GraphicsManager graphics;
    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
        graphics = GraphicsManager.getInstance();
        graphics.initHeadless();
    }

    private PlayerSpriteRenderer renderer(int base) {
        var pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 7);
        var mapping = new SpriteMappingFrame(List.of(new SpriteMappingPiece(0, 0, 1, 1, 0, false, false, 0)));
        var dplc = new SpriteDplcFrame(List.of(new TileLoadRequest(0, 1)));
        return new PlayerSpriteRenderer(new SpriteArtSet(new Pattern[]{pattern},
                java.util.Collections.nCopies(256, mapping), java.util.Collections.nCopies(256, dplc),
                0, base, 0, 1, null, null), graphics);
    }

    private Sonic sonic(String code, int base) {
        var sonic = new Sonic(code, (short) 32, (short) 48);
        sonic.setSpriteRenderer(renderer(base));
        sonic.setMappingFrame(0);
        return sonic;
    }

    private SpritePresentation.Frame prepare(Runnable draw, boolean sat) {
        return SpritePresentationRenderer.prepare(graphics, 0, 0, () -> {
            SpritePresentation.layer(graphics, SpritePresentation.Layer.PLAYER);
            if (sat) graphics.beginSpriteSatCollection();
            draw.run();
            if (sat) graphics.endSpriteSatCollectionAndReplay();
        });
    }

    private SpritePresentation.Frame replay(SpritePresentation.Frame frame) {
        return SpritePresentationRenderer.prepare(graphics, 0, 0,
                () -> SpritePresentationRenderer.draw(graphics, frame, 0, 0, layer -> true));
    }

    private List<SpritePresentation.Tile> nativeTiles(SpritePresentation.Frame frame) {
        return frame.tiles().stream().map(t -> new SpritePresentation.Tile(t.layer(), t.patternId(), t.palette(),
                t.hFlip(), t.vFlip(), t.priority(), t.x(), t.y(), t.width(), t.height(), t.priorityShader(),
                t.occlusionMask(), t.ghost(), t.ghostAlpha(), t.rowStart(), t.rowEnd())).toList();
    }

    @Test void bodySuppressionKeepsNativeAdmissionDplcAndGameplayFlags() {
        var sonic = sonic("sonic", SONIC_BODY);
        for (boolean sat : new boolean[]{false, true}) {
            var stock = prepare(sonic::draw, sat);
            var state = sonic.captureRewindState();
            PlayableSpriteInternalAccess.bindMutatorPolicies(sonic,
                    player -> new PlayableMutatorPolicy(100, true, true, false));
            var stealth = prepare(sonic::draw, sat);
            assertEquals(nativeTiles(stock), nativeTiles(stealth), "native producer still admits every piece");
            assertEquals(stock.patternVersions(), stealth.patternVersions(), "DPLC generation retained");
            assertNativeStateEquals(state, sonic.captureRewindState(), "Stealth cannot change native gameplay visibility flags");
            assertFalse(sonic.isHidden());
            assertEquals(SpritePresentation.Part.BODY, stealth.tiles().getFirst().subject().part());
            assertEquals("sonic", stealth.tiles().getFirst().subject().id());
            assertTrue(replay(stealth).tiles().isEmpty());
            PlayableSpriteInternalAccess.bindMutatorPolicies(sonic, null);
        }
    }

    @Test void tailsAppendageCannotLeakWhenItsBodyIsSuppressedOrBlinking() throws Exception {
        var tails = new Tails("tails", (short) 32, (short) 48);
        tails.setSpriteRenderer(renderer(TAILS_BODY));
        tails.setMappingFrame(0);
        var appendage = new TailsTailsController(tails, renderer(TAILS_APPENDAGE));
        var animation = TailsTailsController.class.getDeclaredField("currentAnim");
        animation.setAccessible(true); animation.setInt(appendage, 1);
        var mapping = TailsTailsController.class.getDeclaredField("mappingFrame");
        mapping.setAccessible(true); mapping.setInt(appendage, 0);
        tails.setTailsTailsController(appendage);
        PlayableSpriteInternalAccess.bindMutatorPolicies(tails,
                player -> new PlayableMutatorPolicy(100, true, true, false));
        for (boolean sat : new boolean[]{false, true}) {
            tails.setInvulnerableFrames(0);
            var frame = prepare(tails::draw, sat);
            assertEquals(2, frame.tiles().size());
            assertEquals(java.util.Set.of(SpritePresentation.Part.BODY, SpritePresentation.Part.APPENDAGE),
                    frame.tiles().stream().map(tile -> tile.subject().part()).collect(java.util.stream.Collectors.toSet()));
            assertTrue(replay(frame).tiles().isEmpty());
            tails.setInvulnerableFrames(1);
            var blink = prepare(tails::draw, sat);
            assertFalse(tails.shouldRefreshRenderFlagThisFrame(), "native blink remains authoritative");
            assertEquals(1, blink.tiles().size(), "native appendage still draws independently of blink");
            assertEquals(SpritePresentation.Part.APPENDAGE, blink.tiles().getFirst().subject().part());
            assertTrue(replay(blink).tiles().isEmpty(), "Stealth prevents the admitted tail from leaking");
        }
    }

    @Test void bodyAndAppendagePoliciesStayIndependent() throws Exception {
        var tails = new Tails("tails", (short) 32, (short) 48);
        tails.setSpriteRenderer(renderer(TAILS_BODY));
        tails.setMappingFrame(0);
        var appendage = new TailsTailsController(tails, renderer(TAILS_APPENDAGE));
        var animation = TailsTailsController.class.getDeclaredField("currentAnim");
        animation.setAccessible(true); animation.setInt(appendage, 1);
        var mapping = TailsTailsController.class.getDeclaredField("mappingFrame");
        mapping.setAccessible(true); mapping.setInt(appendage, 0);
        tails.setTailsTailsController(appendage);
        for (boolean hideBody : new boolean[]{false, true}) {
            PlayableSpriteInternalAccess.bindMutatorPolicies(tails,
                    player -> new PlayableMutatorPolicy(100, hideBody, !hideBody, false));
            var frame = prepare(tails::draw, true);
            assertEquals(2, frame.tiles().size(), "both native producers still run");
            var display = replay(frame);
            assertEquals(1, display.tiles().size());
            assertEquals(hideBody ? TAILS_APPENDAGE : TAILS_BODY, display.tiles().getFirst().patternId());
        }
    }

    @Test void attachedDustOptionIsIndependentOfBodyAndDoesNotHideWorldOrHud() {
        var sonic = sonic("sonic", SONIC_BODY);
        var other = sonic("other", OTHER_BODY);
        var dust = new SpindashDustController(sonic, renderer(DUST));
        dust.triggerSplash(32, 60, false);
        sonic.setSpindashDustController(dust);
        for (boolean hideEffects : new boolean[]{false, true}) {
            PlayableSpriteInternalAccess.bindMutatorPolicies(sonic,
                    player -> new PlayableMutatorPolicy(100, true, true, hideEffects));
            var frame = prepare(() -> {
                sonic.draw();
                other.draw();
                SpritePresentation.layer(graphics, SpritePresentation.Layer.OBJECT);
                graphics.renderPatternWithId(5, new PatternDesc(), 64, 64);
                SpritePresentation.layer(graphics, SpritePresentation.Layer.HUD);
                graphics.renderPatternWithId(6, new PatternDesc(), 0, 0);
            }, true);
            assertEquals(5, frame.tiles().size(), "all native producers run");
            var effect = frame.tiles().stream().filter(t -> t.patternId() == DUST).findFirst().orElseThrow();
            assertEquals(SpritePresentation.Part.WORLD, effect.subject().part());
            assertFalse(effect.subject().suppressed(), "fixed world splash survives attached-effect suppression");
            assertEquals(4, replay(frame).tiles().size());
        }
    }

    @Test void shieldAndInvincibilityStarOwnersTagOnlyTheirOwnAttachedVisuals() {
        var sonic = sonic("sonic", SONIC_BODY);
        var render = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectRenderManager.class);
        var shieldRenderer = org.mockito.Mockito.mock(PatternSpriteRenderer.class);
        var starsRenderer = org.mockito.Mockito.mock(PatternSpriteRenderer.class);
        org.mockito.Mockito.when(render.getShieldRenderer()).thenReturn(shieldRenderer);
        org.mockito.Mockito.when(render.getInvincibilityStarsRenderer()).thenReturn(starsRenderer);
        org.mockito.Mockito.doAnswer(call -> {
            graphics.renderPatternWithId(SHIELD, new PatternDesc(), call.getArgument(1), call.getArgument(2));
            return null;
        }).when(shieldRenderer).drawFrameIndex(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.doAnswer(call -> {
            graphics.renderPatternWithId(STARS, new PatternDesc(), call.getArgument(1), call.getArgument(2));
            return null;
        }).when(starsRenderer).drawFrameIndex(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
        var services = new com.openggf.level.objects.StubObjectServices() {
            @Override public com.openggf.level.objects.ObjectRenderManager renderManager() { return render; }
        };
        var shield = com.openggf.level.objects.ObjectConstructionContext.construct(services,
                () -> new com.openggf.level.objects.ShieldObjectInstance(sonic));
        var stars = com.openggf.level.objects.ObjectConstructionContext.construct(services,
                () -> new com.openggf.level.objects.InvincibilityStarsObjectInstance(sonic));
        for (boolean effects : new boolean[]{false, true}) {
            PlayableSpriteInternalAccess.bindMutatorPolicies(sonic,
                    player -> new PlayableMutatorPolicy(100, true, true, effects));
            var frame = prepare(() -> {
                shield.appendRenderCommands(new java.util.ArrayList<>());
                stars.appendRenderCommands(new java.util.ArrayList<>());
            }, false);
            assertEquals(9, frame.tiles().size(), "native shield and all eight star sub-sprites still render");
            assertTrue(frame.tiles().stream().allMatch(tile ->
                    tile.subject().part() == SpritePresentation.Part.ATTACHED_EFFECT
                            && tile.subject().id().equals("sonic") && tile.subject().suppressed() == effects));
            assertEquals(effects ? 0 : 9, replay(frame).tiles().size());
        }
    }

    @Test void suppressedSatEntryStillArmsNativeMaskBeforeDisplayFiltering() {
        var hidden = new SpritePresentation.Subject("sonic", SpritePresentation.Part.BODY, true);
        var armer = SpriteSatEntry.of(8, 0, 1, 1, 2, 0, false, false, false, false)
                .withPresentationSubject(hidden);
        var marker = SpriteSatEntry.of(1, 0, 1, 1, 0x7C0, 0, false, false, false, false);
        var companion = SpriteSatEntry.of(0, 0, 1, 1, 3, 0, false, false, false, false);
        var behind = SpriteSatEntry.of(16, 0, 1, 1, 4, 0, false, false, false, false);
        var nativeEntries = SpriteSatMaskPostProcessor.process(List.of(armer, marker, companion, behind), true);
        assertEquals(1, nativeEntries.size(), "hidden body still arms the mask hiding later sprites");
        assertEquals(armer.toPreparedPiece(), nativeEntries.getFirst().toPreparedPiece());
        assertEquals(hidden, nativeEntries.getFirst().presentationSubject());
        assertEquals(hidden, armer.withVisibleScanlines(1, 7).presentationSubject());
        assertEquals(hidden, armer.clipRows(0, 1).presentationSubject());
    }

    @Test void activeSpindashDustIsAttachedWhileBothFixedWorldSplashesSurvive() {
        var sonic=sonic("sonic",SONIC_BODY);
        sonic.setSpindash(true);sonic.setAir(false);
        var dust=new SpindashDustController(sonic,renderer(DUST));
        dust.triggerSplash(32,60,false);
        dust.triggerSurfaceSplash(renderer(DUST+1),40,60);
        dust.update();
        var state=sonic.captureRewindState();
        for(boolean hidden:new boolean[]{false,true}) {
            PlayableSpriteInternalAccess.bindMutatorPolicies(sonic,p->new PlayableMutatorPolicy(100,true,true,hidden));
            var frame=prepare(dust::draw,true);
            assertEquals(3,frame.tiles().size());
            assertEquals(2,frame.tiles().stream().filter(t->t.subject().part()==SpritePresentation.Part.WORLD).count());
            var attached=frame.tiles().stream().filter(t->t.subject().part()==SpritePresentation.Part.ATTACHED_EFFECT).findFirst().orElseThrow();
            assertEquals(hidden,attached.subject().suppressed());
            assertEquals(hidden?2:3,replay(frame).tiles().size());
            assertNativeStateEquals(state,sonic.captureRewindState(),"dust display does not change spindash state");
        }
    }

    @Test void liveSuppressionRunsProducerAndRestoresGraphicsStateAcrossFramesAndFaults() {
        var sonic = sonic("sonic", SONIC_BODY);
        PlayableSpriteInternalAccess.bindMutatorPolicies(sonic,
                player -> new PlayableMutatorPolicy(100, true, true, true));
        var calls = new AtomicInteger();
        graphics.setUseSpritePriorityShader(true);
        graphics.setCurrentSpriteTileOcclusionPaletteMask(3);
        graphics.beginGhostRenderEffect(0.4f);
        for (int frame = 0; frame < 5; frame++) {
            PlayableMutatorPresentation.draw(sonic, SpritePresentation.Part.BODY, () -> {
                calls.incrementAndGet();
                graphics.renderPatternWithId(SONIC_BODY, new PatternDesc(), 0, 0);
            });
            assertEquals(SpritePresentation.Subject.WORLD, graphics.spritePresentationSubject);
            assertNull(graphics.spritePresentationBuilder);
            assertTrue(graphics.commands.isEmpty());
            assertTrue(graphics.isUseSpritePriorityShader());
            assertEquals(3, graphics.getCurrentSpriteTileOcclusionPaletteMask());
            assertTrue(graphics.isGhostRenderEffectActive());
            assertEquals(0.4f, graphics.getGhostRenderAlpha());
        }
        assertEquals(5, calls.get());
        assertThrows(IllegalStateException.class, () -> PlayableMutatorPresentation.draw(sonic,
                SpritePresentation.Part.BODY, () -> { throw new IllegalStateException("draw fault"); }));
        assertEquals(SpritePresentation.Subject.WORLD, graphics.spritePresentationSubject);
        assertNull(graphics.spritePresentationBuilder);
        assertFalse(graphics.isSpriteSatCollectionActive());
        var hud = SpritePresentationRenderer.prepare(graphics, 0, 0,
                () -> graphics.renderPatternWithId(6, new PatternDesc(), 0, 0));
        assertFalse(hud.tiles().getFirst().subject().suppressed());
    }
}
