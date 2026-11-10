package com.openggf.level.objects;

import com.openggf.game.*;
import com.openggf.game.internal.PoweredAnimationSource;
import com.openggf.level.Pattern;
import com.openggf.level.render.*;
import com.openggf.sprites.animation.*;
import com.openggf.sprites.art.SpriteArtSet;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestPoweredPlayableArtBinding {
    private SpriteArtSet art(boolean canonical, Map<String,Integer> keys) {
        var scripts = new SpriteAnimationSet();
        scripts.addScript(5, new SpriteAnimationScript(2, List.of(0), SpriteAnimationEndAction.LOOP, 0));
        return new SpriteArtSet(new Pattern[]{new Pattern()}, List.of(new SpriteMappingFrame(List.of())),
                List.of(new SpriteDplcFrame(List.of(new TileLoadRequest(0,1,0)))),0,0,1,1,
                canonical ? new PlayableSheetAnimationBinding.CanonicalProfile(Map.of(),keys) : new ScriptedVelocityAnimationProfile(),scripts);
    }
    private SuperFormProfile.Form form(PoweredPlayableSheet art) {
        var palette=List.of(Collections.nCopies(16,0));
        return new SuperFormProfile.Form(palette,palette,List.of(),1,1,PhysicsProfile.SONIC_2_SUPER_SONIC,
                CanonicalAnimation.WAIT,Map.of(),false,false,art);
    }
    @Test void absentBankRetainsEveryResourceAndCompatibilityConstructor() {
        var normal=art(false,Map.of());
        var old=form(null);
        var result=PoweredPlayableArtBinding.combine(normal,Map.of(SuperFormProfile.Tier.SUPER,old),null);
        assertTrue(result.powered().isEmpty());
        assertSame(normal.animationSet(),result.normal().animationSet());
        assertSame(normal.animationProfile(),result.normal().animationProfile());
        assertEquals(normal.mappingFrames(),result.normal().mappingFrames());
    }
    @Test void completeFamilyPreservesExactScriptAndRelocatesDplcTilesWhileHyperSharesStorage() {
        var normal=art(false,Map.of());
        var bank=new PoweredPlayableSheet(art(true,Map.of("family-wait/0",0,"family-wait/1",0)),Set.of());
        var scripts=new SpriteAnimationSet();
        scripts.addScript(5,new SpriteAnimationScript(7,List.of(0x72,0x73,0x72),SpriteAnimationEndAction.SWITCH,0));
        var source=new PoweredAnimationSource(scripts,Map.of(5,new AnimationFamilies.Binding(CanonicalAnimation.WAIT,CanonicalAnimation.WAIT,List.of(0,1,0))),8,1,3,32,181);
        var result=PoweredPlayableArtBinding.combine(normal,Map.of(SuperFormProfile.Tier.SUPER,form(bank),SuperFormProfile.Tier.HYPER,form(bank)),source);
        var powered=result.powered().get(SuperFormProfile.Tier.SUPER);
        var script=powered.animationSet().getScript(5);
        assertEquals(7,script.delay()); assertEquals(SpriteAnimationEndAction.SWITCH,script.endAction()); assertEquals(0,script.endParam());
        assertEquals(script.frames().get(0),script.frames().get(2));
        assertEquals(1,script.frames().get(1)-script.frames().get(0));
        assertEquals(1,powered.dplcFrames().get(script.frames().getFirst()).requests().getFirst().startTile());
        assertSame(powered.artTiles(),result.normal().artTiles());
        assertSame(powered.animationSet(),result.powered().get(SuperFormProfile.Tier.HYPER).animationSet());
        assertEquals(2,powered.artTiles().length);
    }
    @Test void incompleteFamilyFallsBackAsAWholeAndExplicitAliasWins() {
        var normal=art(false,Map.of());var scripts=new SpriteAnimationSet();
        scripts.addScript(5,new SpriteAnimationScript(7,List.of(1,2),SpriteAnimationEndAction.LOOP,0));
        var source=new PoweredAnimationSource(scripts,Map.of(5,new AnimationFamilies.Binding(CanonicalAnimation.WAIT,CanonicalAnimation.WAIT,List.of(0,1))),8,1,3,32,181);
        for(var bank:List.of(new PoweredPlayableSheet(art(true,Map.of("family-wait/0",0)),Set.of()),
                new PoweredPlayableSheet(art(true,Map.of("family-wait/0",0,"family-wait/1",0)),Set.of(CanonicalAnimation.WAIT)))) {
            var result=PoweredPlayableArtBinding.combine(normal,Map.of(SuperFormProfile.Tier.SUPER,form(bank)),source);
            assertSame(normal.animationSet().getScript(5),result.powered().get(SuperFormProfile.Tier.SUPER).animationSet().getScript(5));
        }
    }
    @Test void mixedFamilyCanAliasANormalPhaseWithoutCopyingNormalTiles() {
        var normal=art(false,Map.of());
        var bank=new PoweredPlayableSheet(art(true,Map.of("family-wait/0",0)),Set.of(),Map.of("family-wait/1","family-wait/0"));
        var scripts=new SpriteAnimationSet();
        scripts.addScript(5,new SpriteAnimationScript(7,List.of(0x72,0x73),SpriteAnimationEndAction.LOOP_BACK,1));
        var source=new PoweredAnimationSource(scripts,Map.of(5,new AnimationFamilies.Binding(CanonicalAnimation.WAIT,CanonicalAnimation.WAIT,List.of(0,1))),8,1,3,32,181);
        var result=PoweredPlayableArtBinding.combine(normal,Map.of(SuperFormProfile.Tier.SUPER,form(bank)),source);
        var view=result.powered().get(SuperFormProfile.Tier.SUPER); var sequence=view.animationSet().getScript(5);
        assertEquals(1,view.dplcFrames().get(sequence.frames().get(0)).requests().getFirst().startTile());
        assertSame(normal.dplcFrames().getFirst(),view.dplcFrames().get(sequence.frames().get(1)));
        assertEquals(2,view.artTiles().length);
        assertEquals(SpriteAnimationEndAction.LOOP_BACK,sequence.endAction()); assertEquals(1,sequence.endParam());
    }
    @Test void hostWithoutNativePoweredScriptsUsesAuthoredTimingWithoutADonorRom() {
        var normal=art(false,Map.of());
        var bank=new PoweredPlayableSheet(art(true,Map.of()),Set.of());
        var host=new com.openggf.game.sonic1.Sonic1GameModule().getDonorCapabilities();
        var result=PoweredPlayableArtBinding.combine(normal,Map.of(SuperFormProfile.Tier.SUPER,form(bank)),null,host);
        var script=result.powered().get(SuperFormProfile.Tier.SUPER).animationSet().getScript(host.resolveNativeId(CanonicalAnimation.WAIT));
        assertEquals(2,script.delay()); assertEquals(List.of(1),script.frames());
    }

}
