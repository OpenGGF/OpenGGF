package com.openggf.level.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.Pattern;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.render.PlayerHeadProfile;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class) @FullReset @Isolated
class TestPlayerHeadPresentation {
    @Test void clippedIndexedHeadMovesAroundNeckWhileBodyPalettePriorityAndFeetStayNative() {
        Pattern pixels = new Pattern();
        for (int y=0;y<8;y++) for(int x=0;x<8;x++) pixels.setPixel(x,y,(byte)(y<4?5:9));
        var mask = new PlayerHeadProfile.FrameHead(PlayerHeadProfile.Kind.MASKED, 0, 4, Set.of(0),
                List.of(new PlayerHeadProfile.Point(0,0),new PlayerHeadProfile.Point(8,0),
                        new PlayerHeadProfile.Point(8,4),new PlayerHeadProfile.Point(0,4)),"test anatomy");
        int id = PatternAtlasRange.SIDEKICK_BANKS.base();
        var version = SpritePresentationRenderer.version(pixels);
        for (boolean flip : new boolean[]{false,true}) for (boolean vertical : new boolean[]{false,true}) {
            int x = flip?92:100;
            var head = new SpritePresentation.HeadTransform(mask,0,100,100,flip,vertical,150,PatternAtlasRange.PLAYER_PRESENTATION.base(),Map.of(id,version));
            var subject = new SpritePresentation.Subject("sonic_p2",SpritePresentation.Part.BODY,false,150,head);
            var tile = new SpritePresentation.Tile(SpritePresentation.Layer.PLAYER,id,2,flip,vertical,true,
                    x-10,vertical?72:80,8,8,true,4,true,.5f,2,7,subject);
            var stock = new SpritePresentation.Frame(List.of(tile),List.of(),Map.of(id,version));
            var out = PlayerHeadPresentation.compose(stock,10,20);
            assertEquals(2,out.tiles().size());
            var body=out.tiles().get(0);var enlarged=out.tiles().get(1);
            assertEquals(tile.x(),body.x());assertEquals(tile.y(),body.y());assertEquals(8,body.height());
            assertEquals(2,body.rowStart());assertEquals(7,body.rowEnd());
            assertEquals(12,enlarged.height());assertEquals(12,enlarged.width());
            assertEquals(vertical?70:78,enlarged.y(),"neck at104 remains fixed at 150%");
            assertEquals(flip?78:90,enlarged.x());
            assertEquals(2,enlarged.palette());assertTrue(enlarged.priority());assertTrue(enlarged.priorityShader());
            assertEquals(4,enlarged.occlusionMask());assertTrue(enlarged.ghost());
            Pattern b=SpritePresentationRenderer.pattern(out.patternVersions().get(body.patternId()));
            Pattern h=SpritePresentationRenderer.pattern(out.patternVersions().get(enlarged.patternId()));
            for(int xx=0;xx<8;xx++) for(int yy=0;yy<8;yy++) {
                boolean selected=vertical?yy>=1&&yy<4:yy>=2&&yy<4;
                assertEquals(selected?0:pixels.getPixel(xx,yy),b.getPixel(xx,yy));
                assertEquals(selected?5:0,h.getPixel(xx,yy),"unadmitted head rows cannot reappear enlarged");
            }
            assertEquals(out,PlayerHeadPresentation.compose(out,10,20),"published/rewound frame cannot enlarge twice");
            pixels.setPixel(0,0,(byte)7);
            assertEquals((byte)5,SpritePresentationRenderer.pattern(version).getPixel(0,0),"published art immutable");
            pixels.setPixel(0,0,(byte)5);
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1NativeInventoryAndRendering() throws Exception {
        check(new com.openggf.game.sonic1.Sonic1(TestEnvironment.currentRom()).loadPlayerSpriteArt("sonic"),"s1",88,55,9);
    }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2NativeInventoryAndRendering() throws Exception {
        check(new com.openggf.game.sonic2.Sonic2(TestEnvironment.currentRom()).loadPlayerSpriteArt("sonic"),"s2",214,81,12);
    }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3kNativeInventoryAndRendering() throws Exception {
        var game=new com.openggf.game.sonic3k.Sonic3k(TestEnvironment.currentRom());
        check(game.loadPlayerSpriteArt("sonic"),"s3k",251,87,11);
        var art=game.loadPlayerSpriteArt("sonic");var profile=PlayerHeadProfile.resolve(art);
        assertFalse(profile.frame(0xA1).contains(0,9,-16),"raised balance glove is body, not head");
        GraphicsManager graphics=GraphicsManager.getInstance();
        var renderer=new PlayerSpriteRenderer(art,graphics);
        var stock=SpritePresentationRenderer.prepare(graphics,0,0,()->renderer.drawFrame(0xA1,100,100,false,false));
        var head=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentation.withSubject(graphics,
                new SpritePresentation.Subject("sonic",SpritePresentation.Part.BODY,false,200),
                ()->renderer.drawFrame(0xA1,100,100,false,false)));
        int glove=indexedAt(stock,109,84);
        assertNotEquals(0,glove,"real native glove coordinate is opaque");
        assertEquals(glove,indexedAt(head,109,84),"glove stays at its stock body coordinate and palette index");
        assertNull(PlayerHeadProfile.resolve(game.loadPlayerSpriteArt("tails")));
        assertNull(PlayerHeadProfile.resolve(game.loadPlayerSpriteArt("knuckles")));
    }
    private int indexedAt(SpritePresentation.Frame frame,int x,int y) {
        for(var tile:frame.tiles()) {
            if(tile.width()!=8||tile.height()!=8||x<tile.x()||x>=tile.x()+8||y<tile.y()||y>=tile.y()+8) continue;
            var pattern=SpritePresentationRenderer.pattern(frame.patternVersions().get(tile.patternId()));
            int px=x-(int)tile.x(),py=y-(int)tile.y();
            int value=pattern.getPixel(tile.hFlip()?7-px:px,tile.vFlip()?7-py:py)&15;
            if(value!=0)return value;
        }
        return 0;
    }
    @SuppressWarnings("unchecked")
    private List<com.openggf.graphics.SpriteSatEntry> admitted(GraphicsManager graphics,PlayerSpriteRenderer renderer,
                                                              int frame,SpritePresentation.Subject subject) {
        var result=new java.util.ArrayList<com.openggf.graphics.SpriteSatEntry>();
        SpritePresentationRenderer.prepare(graphics,0,0,()->{
            graphics.beginSpriteSatCollection();graphics.requestSpriteMask();
            SpritePresentation.withSubject(graphics,subject,()->renderer.drawFrame(frame,100,100,true,true));
            try {
                var field=GraphicsManager.class.getDeclaredField("spriteSatEntries");field.setAccessible(true);
                var entries=(List<com.openggf.graphics.SpriteSatEntry>)field.get(graphics);
                result.addAll(com.openggf.graphics.SpriteSatMaskPostProcessor.process(entries,true).stream()
                        .map(e->e.withPresentationSubject(SpritePresentation.Subject.WORLD)).toList());
            } catch(ReflectiveOperationException failure) {throw new AssertionError(failure);}
            graphics.endSpriteSatCollectionAndReplay();
        });
        return List.copyOf(result);
    }
    @SuppressWarnings("unchecked")
    private void check(SpriteArtSet art,String game,int count,int masked,int balls) {
        PlayerHeadProfile profile=PlayerHeadProfile.resolve(art);
        assertNotNull(profile,game+" actual ROM art identity "+PlayerHeadProfile.fingerprint(art));
        assertEquals(game,profile.id());assertEquals(count,profile.frameCount());
        assertEquals(masked,java.util.stream.IntStream.range(0,count).filter(i->profile.frame(i).kind()==PlayerHeadProfile.Kind.MASKED).count());
        assertEquals(balls,java.util.stream.IntStream.range(0,count).filter(i->profile.frame(i).kind()==PlayerHeadProfile.Kind.BALL_STOCK).count());
        GraphicsManager graphics=GraphicsManager.getInstance();graphics.initHeadless();
        var renderer=new PlayerSpriteRenderer(art,graphics);
        for(int f=0;f<count;f++) {
            int frameIndex=f;
            var status=profile.frame(f);
            for(int p:status.pieces()) assertTrue(p>=0&&p<art.mappingFrames().get(f).pieces().size(),game+" mask piece bound "+f);
            for(boolean flip:new boolean[]{false,true}) {
                var stock=SpritePresentationRenderer.prepare(graphics,0,0,()->renderer.drawFrame(frameIndex,100,100,flip,false));
                var head=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentation.withSubject(graphics,
                        new SpritePresentation.Subject("sonic",SpritePresentation.Part.BODY,false,150),
                        ()->renderer.drawFrame(frameIndex,100,100,flip,false)));
                if(status.kind()!=PlayerHeadProfile.Kind.MASKED) {
                    assertEquals(stock.patternVersions(),head.patternVersions());
                    assertEquals(stock.tiles().size(),head.tiles().size(),game+" explicitly stock pose "+f);
                    assertTrue(head.tiles().stream().allMatch(t->t.width()==8&&t.height()==8));
                } else {
                    assertTrue(head.tiles().size()>stock.tiles().size(),game+" nonblank reviewed head "+f);
                    assertTrue(head.tiles().stream().anyMatch(t->t.width()==12));
                    for(var tile:head.tiles()) assertEquals(art.paletteIndex(),tile.palette());
                    assertTrue(head.tiles().stream().allMatch(t->t.subject().head()==null),"composed snapshot complete");
                    assertEquals(head,PlayerHeadPresentation.compose(head,0,0));
                }
            }
        }
        // Simultaneously queued live S1/S2 draws use independently shifted production banks.
        var shifted=new SpriteArtSet(art.artTiles(),art.mappingFrames(),art.dplcFrames(),art.paletteIndex(),
                PatternAtlasRange.SIDEKICK_BANKS.base(),art.frameDelay(),art.bankSize(),art.animationProfile(),art.animationSet());
        var duplicate=new PlayerSpriteRenderer(shifted,graphics);
        int firstMasked=java.util.stream.IntStream.range(0,count).filter(i->profile.frame(i).kind()==PlayerHeadProfile.Kind.MASKED).findFirst().orElseThrow();
        int secondMasked=java.util.stream.IntStream.range(firstMasked+1,count).filter(i->profile.frame(i).kind()==PlayerHeadProfile.Kind.MASKED).findFirst().orElseThrow();
        var first=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentation.withSubject(graphics,
                new SpritePresentation.Subject("sonic",SpritePresentation.Part.BODY,false,150),
                ()->renderer.drawFrame(firstMasked,100,100,false,false)));
        var second=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentation.withSubject(graphics,
                new SpritePresentation.Subject("sonic_p2",SpritePresentation.Part.BODY,false,150),
                ()->duplicate.drawFrame(secondMasked,160,100,true,false)));
        var firstIds=first.tiles().stream().filter(t->t.width()!=8).map(SpritePresentation.Tile::patternId).collect(java.util.stream.Collectors.toSet());
        var secondIds=second.tiles().stream().filter(t->t.width()!=8).map(SpritePresentation.Tile::patternId).collect(java.util.stream.Collectors.toSet());
        assertTrue(java.util.Collections.disjoint(firstIds,secondIds),"queued duplicate banks must have independent head fragments");
        SpritePresentationRenderer.draw(graphics,first,0,0,l->true);
        SpritePresentationRenderer.draw(graphics,second,0,0,l->true);
        for(int fragment:firstIds) assertEquals(first.patternVersions().get(fragment),SpritePresentation.patternSample(graphics,fragment),
                "second queued draw cannot overwrite first Sonic slot's frozen head pixels");
        for(int nativeFrame:new int[]{firstMasked,secondMasked}) {
            var nativeAdmission=admitted(graphics,renderer,nativeFrame,SpritePresentation.Subject.WORLD);
            var headAdmission=admitted(graphics,renderer,nativeFrame,new SpritePresentation.Subject("sonic",SpritePresentation.Part.BODY,false,200));
            assertEquals(nativeAdmission,headAdmission,"Big Head cannot resize/reorder/retime native SAT or its mask post-pass");
        }
        // Normal source art must not receive a normal mask during a powered transition.
        var sonic=new com.openggf.sprites.playable.Sonic("sonic",(short)100,(short)100);
        sonic.setSpriteRenderer(renderer);sonic.setMappingFrame(firstMasked);
        com.openggf.sprites.playable.PlayableSpriteInternalAccess.bindMutatorPolicies(sonic,
                p->new com.openggf.sprites.playable.PlayableMutatorPolicy(100,false,false,false,200));
        sonic.setSuperSonic(true);
        var powered=SpritePresentationRenderer.prepare(graphics,0,0,sonic::draw);
        assertFalse(powered.tiles().isEmpty());
        assertTrue(powered.tiles().stream().allMatch(t->t.width()==8&&t.subject().head()==null),"powered art uses stock geometry");
        com.openggf.sprites.playable.PlayableSpriteInternalAccess.bindMutatorPolicies(sonic,null);
        // Exercise the actual live SAT branch without a surrounding CPU builder.
        graphics.setUseSpritePriorityShader(true);graphics.setCurrentSpriteHighPriority(true);
        var satOracle=SpritePresentationRenderer.prepare(graphics,0,0,()->{
            graphics.beginSpriteSatCollection();
            SpritePresentation.withSubject(graphics,new SpritePresentation.Subject("sonic",SpritePresentation.Part.BODY,false,150),
                    ()->renderer.drawFrame(firstMasked,100,100,false,false));
            graphics.endSpriteSatCollectionAndReplay();
        });
        graphics.beginSpriteSatCollection();
        SpritePresentation.withSubject(graphics,new SpritePresentation.Subject("sonic",SpritePresentation.Part.BODY,false,150),
                ()->renderer.drawFrame(firstMasked,100,100,false,false));
        graphics.endSpriteSatCollectionAndReplay();
        assertTrue(graphics.isUseSpritePriorityShader(),"live replay restores caller shader state");
        assertFalse(graphics.getCurrentSpriteHighPriority(),"stock replay clears the producer's terrain priority");
        assertFalse(graphics.isSpriteSatCollectionActive());
        for(var tile:satOracle.tiles()) assertEquals(satOracle.patternVersions().get(tile.patternId()),
                SpritePresentation.patternSample(graphics,tile.patternId()),
                "actual live native SAT path composes the same admitted/SAT-ordered indexed pixels");
        // Captured replay carries plain SAT shader state while preserving effective priority per tile.
        var headEntries=new java.util.ArrayList<com.openggf.graphics.SpriteSatEntry>();
        SpritePresentationRenderer.prepare(graphics,0,0,()->{
            graphics.beginSpriteSatCollection();
            SpritePresentation.withSubject(graphics,new SpritePresentation.Subject("sonic",SpritePresentation.Part.BODY,false,150),
                    ()->renderer.drawFrame(firstMasked,100,100,false,false));
            try {
                var field=GraphicsManager.class.getDeclaredField("spriteSatEntries");field.setAccessible(true);
                headEntries.addAll((List<com.openggf.graphics.SpriteSatEntry>)field.get(graphics));
            } catch(ReflectiveOperationException failure) {throw new AssertionError(failure);}
            graphics.endSpriteSatCollectionAndReplay();
        });
        var emitted=new java.util.concurrent.atomic.AtomicInteger();
        SpritePresentationRenderer.replayHeadSat(graphics,headEntries,entry->{
            assertFalse(graphics.isUseSpritePriorityShader(),"native live SAT replay uses the plain shader");
            emitted.incrementAndGet();
            var desc=new com.openggf.level.PatternDesc();desc.setPriority(entry.effectiveHighPriority());
            SpritePieceRenderer.renderPreparedPiece(entry.toPreparedPiece(),(id,h,v,p,x,y)->{
                desc.setPaletteIndex(p);desc.setHFlip(h);desc.setVFlip(v);graphics.renderPatternWithId(id,desc,x,y);
            });
        });
        assertEquals(headEntries.size(),emitted.get());
        assertTrue(graphics.isUseSpritePriorityShader());
        assertFalse(graphics.getCurrentSpriteHighPriority());
        var altered=new Pattern[art.artTiles().length];System.arraycopy(art.artTiles(),0,altered,0,altered.length);
        altered[0]=new Pattern();altered[0].setPixel(0,0,(byte)15);
        var custom=new SpriteArtSet(altered,art.mappingFrames(),art.dplcFrames(),art.paletteIndex(),art.basePatternIndex(),
                art.frameDelay(),art.bankSize(),art.animationProfile(),art.animationSet());
        assertNull(PlayerHeadProfile.resolve(custom),"custom art cannot receive a normal Sonic mask");
        var clone=new SpriteArtSet(art.artTiles(),art.mappingFrames(),art.dplcFrames(),3,PatternAtlasRange.SIDEKICK_BANKS.base(),
                art.frameDelay(),art.bankSize(),art.animationProfile(),art.animationSet());
        assertEquals(profile.id(),PlayerHeadProfile.resolve(clone).id(),"duplicate bank/palette uses same actual anatomy");
    }
}
