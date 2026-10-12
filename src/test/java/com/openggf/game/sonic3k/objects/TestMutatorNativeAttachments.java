package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameModule;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.playable.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SingletonResetExtension.class) @FullReset @Isolated
class TestMutatorNativeAttachments {
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void nativeElementalAndInstaShieldDplcOverridesAreSuppressedWithoutStoppingTheirProducers() throws Exception {
        GraphicsManager graphics=GraphicsManager.getInstance();graphics.initHeadless();
        var provider=new Sonic3kObjectArtProvider();
        var load=Sonic3kObjectArtProvider.class.getDeclaredMethod("loadShieldArt");load.setAccessible(true);load.invoke(provider);
        GameModule module=mock(GameModule.class);when(module.getObjectArtProvider()).thenReturn(provider);
        var services=new StubObjectServices() { @Override public GameModule gameModule(){return module;} };
        Sonic player=new Sonic("sonic_p2",(short)100,(short)100);
        List<ShieldObjectInstance> shields=List.of(
                ObjectConstructionContext.construct(services,()->new BubbleShieldObjectInstance(player)),
                ObjectConstructionContext.construct(services,()->new FireShieldObjectInstance(player)),
                ObjectConstructionContext.construct(services,()->new LightningShieldObjectInstance(player)),
                ObjectConstructionContext.construct(services,()->new InstaShieldObjectInstance(player)));
        shields.forEach(shield -> shield.setServices(services));
        ((InstaShieldObjectInstance)shields.get(3)).triggerAttack();
        shields.get(3).update(1,player);
        for(var shield:shields) {
            var stock=prepare(graphics,shield);
            assertFalse(stock.tiles().isEmpty(),shield.getClass()+" real native shield art must render");
            assertFalse(stock.patternVersions().isEmpty());
            var state=player.captureRewindState();
            for(boolean effects:new boolean[]{false,true}) {
                PlayableSpriteInternalAccess.bindMutatorPolicies(player,p->new PlayableMutatorPolicy(100,true,true,effects));
                var frame=prepare(graphics,shield);
                assertEquals(stock.patternVersions(),frame.patternVersions(),"DPLC still runs with suppression");
                assertEquals(stock.tiles().size(),frame.tiles().size());
                assertTrue(frame.tiles().stream().allMatch(t->t.subject().id().equals("sonic_p2")
                        &&t.subject().part()==SpritePresentation.Part.ATTACHED_EFFECT&&t.subject().suppressed()==effects));
                var display=SpritePresentationRenderer.prepare(graphics,0,0,
                        ()->SpritePresentationRenderer.draw(graphics,frame,0,0,l->true));
                assertEquals(effects?0:stock.tiles().size(),display.tiles().size());
                MutatorPlayerStateAssertions.assertNativeStateEquals(state,player.captureRewindState(),"display cannot change ability state");
            }
            PlayableSpriteInternalAccess.bindMutatorPolicies(player,null);
            shield.refreshArtAfterRewindRestore();
            assertEquals(stock,prepare(graphics,shield),"rewind art refresh restores the same native display");
        }
    }
    @Test void realStarOwnersAndWireFallbacksKeepDuplicateSlotsIndependent() {
        TestEnvironment.configureGameModuleFixture(new com.openggf.game.sonic2.Sonic2GameModule());
        GraphicsManager graphics=GraphicsManager.getInstance();graphics.initHeadless();
        int id=PatternAtlasRange.TRANSIENT_EFFECTS.base()+100;
        var renderer=mock(PatternSpriteRenderer.class);
        doAnswer(call->{graphics.renderPatternWithId(id,new com.openggf.level.PatternDesc(),
                call.getArgument(1),call.getArgument(2));return null;}).when(renderer).drawFrameIndex(anyInt(),anyInt(),anyInt(),anyBoolean(),anyBoolean());
        var manager=mock(ObjectRenderManager.class);
        when(manager.getInvincibilityStarsRenderer()).thenReturn(renderer);
        when(manager.getSuperSonicStarsRenderer()).thenReturn(renderer);
        var services=new StubObjectServices(){@Override public ObjectRenderManager renderManager(){return manager;}};
        Sonic a=new Sonic("sonic",(short)100,(short)100),b=new Sonic("sonic_p2",(short)160,(short)100);
        a.setGSpeed((short)0x900);b.setGSpeed((short)0x900);
        var starsA=ObjectConstructionContext.construct(services,()->new com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance(a));
        var starsB=ObjectConstructionContext.construct(services,()->new com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance(b));
        var inv=ObjectConstructionContext.construct(services,()->new Sonic3kInvincibilityStarsObjectInstance(a));
        starsA.setServices(services);starsB.setServices(services);inv.setServices(services);
        starsA.update(0,a);starsB.update(0,b);
        PlayableSpriteInternalAccess.bindMutatorPolicies(a,p->new PlayableMutatorPolicy(100,false,false,true));
        var frame=SpritePresentationRenderer.prepare(graphics,0,0,()->{
            starsA.appendRenderCommands(new ArrayList<>());starsB.appendRenderCommands(new ArrayList<>());inv.appendRenderCommands(new ArrayList<>());
        });
        assertEquals(10,frame.tiles().size());
        assertEquals(9,frame.tiles().stream().filter(t->t.subject().suppressed()&&t.subject().id().equals("sonic")).count());
        var display=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentationRenderer.draw(graphics,frame,0,0,l->true));
        assertEquals(1,display.tiles().size(),"second native Sonic slot remains visible");
        // Real missing-art native fallback goes through the same immutable attachment tag.
        var wireServices=new StubObjectServices();
        var wire=ObjectConstructionContext.construct(wireServices,()->new BubbleShieldObjectInstance(a));
        wire.setServices(wireServices);
        var commands=new ArrayList<com.openggf.graphics.GLCommand>();
        var wireFrame=SpritePresentationRenderer.prepare(graphics,0,0,()->wire.appendRenderCommands(commands));
        assertTrue(commands.isEmpty());assertEquals(8,wireFrame.primitives().size());
        assertTrue(wireFrame.primitives().stream().allMatch(p->p.subject().suppressed()));
        var hiddenWire=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentationRenderer.draw(graphics,wireFrame,0,0,l->true));
        assertTrue(hiddenWire.primitives().isEmpty());
        PlayableSpriteInternalAccess.bindMutatorPolicies(a,null);
        wire.appendRenderCommands(commands);assertEquals(8,commands.size(),"disabled list ownership is unchanged");
    }
    private SpritePresentation.Frame prepare(GraphicsManager graphics,ShieldObjectInstance shield) {
        return SpritePresentationRenderer.prepare(graphics,0,0,()->shield.appendRenderCommands(new ArrayList<>()));
    }
}
