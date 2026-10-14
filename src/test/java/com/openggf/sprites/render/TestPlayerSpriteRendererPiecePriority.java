package com.openggf.sprites.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** First opaque mapping piece wins on VDP; painter and collected SAT must agree. */
@ExtendWith(SingletonResetExtension.class) @FullReset @Isolated
class TestPlayerSpriteRendererPiecePriority {
    @ParameterizedTest @CsvSource({"false,false,false","true,false,false","false,true,false","true,true,false",
            "false,false,true","true,false,true","false,true,true","true,true,true"})
    void firstOpaquePieceWinsWithFlipsInPainterAndNativeSat(boolean hFlip,boolean vFlip,boolean sat){
        var early=new Pattern();var late=new Pattern();
        for(int y=0;y<8;y++)for(int x=0;x<8;x++){
            early.setPixel(x,y,(byte)(x==5&&y==3?0:2));late.setPixel(x,y,(byte)5);
        }
        var art=new SpriteArtSet(new Pattern[]{early,late},List.of(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0,0,1,1,0,false,false,0),
                new SpriteMappingPiece(4,0,1,1,1,false,false,0)))),
                List.of(new SpriteDplcFrame(List.of(new TileLoadRequest(0,2)))),0,1696,0,2,null,null);
        var graphics=GraphicsManager.getInstance();var renderer=new PlayerSpriteRenderer(art,graphics);
        var published=SpritePresentationRenderer.prepare(graphics,0,0,()->{
            if(sat)graphics.beginSpriteSatCollection();
            renderer.drawFrame(0,100,100,hFlip,vFlip);
            if(sat)graphics.endSpriteSatCollectionAndReplay();
        });
        // Independent source coordinates: piece0 covers [0,8), piece1 [4,12).
        // Reflection around the origin moves both coordinates and source texels.
        for(int y=0;y<8;y++)for(int x=0;x<12;x++){
            int px=100+(hFlip?-1-x:x),py=100+(vFlip?-1-y:y);
            int expected=x<8&&!(x==5&&y==3)?2:x>=4?5:0;
            int actual=0;
            for(var tile:published.tiles()){
                int dx=px-(int)tile.x(),dy=py-(int)tile.y();if(dx<0||dx>=8||dy<0||dy>=8)continue;
                var pattern=SpritePresentationRenderer.pattern(published.patternVersions().get(tile.patternId()));
                int color=pattern.getPixel(tile.hFlip()?7-dx:dx,tile.vFlip()?7-dy:dy)&15;
                if(color!=0)actual=color;
            }
            assertEquals(expected,actual,"first opaque owner at source "+x+","+y+" sat="+sat);
        }
    }
}
