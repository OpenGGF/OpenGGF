package com.openggf.game.mutators;

import com.openggf.graphics.SpritePresentation.*;
import com.openggf.mods.mutators.SpritePresentationPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.mods.mutators.SpritePresentationPolicy.Mode.*;
import static com.openggf.mods.mutators.SpritePresentationPolicy.Target.*;

class TestSpritePictureTransform {
    private Tile tile(Role role,boolean suppressed,boolean hFlip,boolean vFlip) {
        return new Tile(Layer.OBJECT,123,2,hFlip,vFlip,true,22,28,8,8,true,5,false,1,2,7,
                new Subject("actor",Part.OBJECT_BODY,suppressed,100,null,new Body(role,130,240)));
    }
    private TransientMutatorPolicySource.Entry effect(SpritePresentationPolicy.Mode mode,SpritePresentationPolicy.Target target,int strength,long age) {
        return new TransientMutatorPolicySource.Entry("test",1,new SpritePresentationPolicy(mode,target,strength,120),age,20,false);
    }
    @Test void wholeBadnikScalingUsesProducerAnchorAndOnlyAdmittedScanlinesAtBothViewportSizes() {
        for(int viewport:new int[]{320,640})for(int strength:new int[]{75,200})for(boolean h:new boolean[]{false,true})for(boolean v:new boolean[]{false,true}) {
            Tile source=tile(Role.BADNIK,false,h,v);
            var transform=SpritePictureTransform.forTile(source,100,200,List.of(effect(SCALE,BADNIKS,strength,0)));
            float[] quad=transform.quad(source,100,200);
            double scale=strength/100.0;
            assertEquals(130+(122-130)*scale,quad[0],.0001);
            assertEquals(240+(230-240)*scale,quad[1],.0001);
            assertEquals(5*scale,quad[5]-quad[1],.0001,"native hidden rows must not reappear at width "+viewport);
            assertEquals(source.hFlip(),h);assertEquals(source.vFlip(),v);assertTrue(source.priority());assertEquals(5,source.occlusionMask());
        }
    }
    @Test void rotationUsesNativeProducerCentreAndDoesNotReuseHeadMaskGeometry() {
        Tile source=tile(Role.BADNIK,false,false,false);
        var transform=SpritePictureTransform.forTile(source,100,200,List.of(effect(ROTATE,BADNIKS,100,30)));
        assertEquals(30,transform.x(30,40),.0001);assertEquals(40,transform.y(30,40),.0001);
        assertEquals(42,transform.x(22,28),.0001);assertEquals(32,transform.y(22,28),.0001);
        assertEquals(source,new Frame(List.of(source)).tiles().getFirst(),"native frame stays immutable");
    }
    @Test void semanticTargetingAndStealthTakePrecedenceOverEveryWholePictureEffect() {
        for(var mode:List.of(SCALE,ROTATE,SQUASH,RAINBOW)) {
            int strength=mode==SCALE?200:mode==SQUASH?120:100;
            assertSame(SpritePictureTransform.IDENTITY,SpritePictureTransform.forTile(tile(Role.BADNIK,true,false,false),100,200,List.of(effect(mode,BADNIKS,strength,30))));
            assertTrue(SpritePictureTransform.forTile(tile(Role.PICKUP,false,false,false),100,200,List.of(effect(mode,BADNIKS,strength,30))).identity());
        }
    }
    @Test void jellyPreservesAreaWhileRainbowLeavesGeometryNative() {
        Tile source=tile(Role.PICKUP,false,false,false);
        var jelly=SpritePictureTransform.forTile(source,100,200,List.of(effect(SQUASH,BADNIKS_AND_PICKUPS,120,30)));
        assertEquals(1,jelly.xx()*jelly.yy()-jelly.xy()*jelly.yx(),.0001);
        var disco=SpritePictureTransform.forTile(tile(Role.LEADER,false,false,false),100,200,List.of(effect(RAINBOW,LEADER,100,30)));
        assertEquals((float)(Math.PI/2),disco.hue(),.0001);assertEquals(22,disco.x(22,28));assertEquals(28,disco.y(22,28));
    }
    @Test void anatomyCompositionRetainsWholePictureMetadataAndUsesSeparateAnchors() {
        var graphics=new com.openggf.graphics.GraphicsManager();graphics.initHeadless();
        var pattern=new com.openggf.level.Pattern();for(int y=0;y<8;y++)for(int x=0;x<8;x++)pattern.setPixel(x,y,(byte)1);
        var version=com.openggf.level.render.SpritePresentationRenderer.version(pattern);
        var mask=new HeadMask(2,2,java.util.Set.of(0),List.of(new MaskPoint(0,0),new MaskPoint(4,0),new MaskPoint(4,4),new MaskPoint(0,4)));
        var body=new Body(Role.BADNIK,104,204);
        var subject=new Subject("native-badnik",Part.OBJECT_BODY,false,200,null,body);
        var head=new HeadTransform(mask,0,100,200,false,false,200,com.openggf.graphics.PatternAtlasRange.OBJECT_HEAD_PRESENTATION.base(),
                java.util.Map.of(42,version),(g,e,emitter)->{});
        var frame=com.openggf.level.render.SpritePresentationRenderer.prepare(graphics,0,0,()-> {
            com.openggf.graphics.SpritePresentation.layer(graphics,Layer.OBJECT);
            com.openggf.graphics.SpritePresentation.bindPatternVersion(graphics,42,version);
            com.openggf.graphics.SpritePresentation.withSubject(graphics,subject,()->com.openggf.graphics.SpritePresentation.withHead(graphics,head,
                    ()->graphics.renderPatternWithId(42,new com.openggf.level.PatternDesc(),100,200)));
        });
        assertTrue(frame.tiles().stream().anyMatch(t->t.width()==16),"head composition ran before whole picture transform");
        for(var tile:frame.tiles()) {
            assertEquals(body,tile.subject().body());assertNull(tile.subject().head());
            var transform=SpritePictureTransform.forTile(tile,0,0,List.of(effect(SCALE,BADNIKS,75,0)));
            assertEquals(104,transform.x(104,204),.0001);assertEquals(204,transform.y(104,204),.0001);
        }
        graphics.cleanup();
    }
}
