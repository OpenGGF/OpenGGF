package com.openggf.level.render;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.Pattern;
import com.openggf.sprites.render.PlayerHeadProfile;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.IntConsumer;

/** A reviewed source frame supplies anatomy; caller keeps native piece geometry and SAT admission. */
final class ObjectHeadPiecePresentation {
    private ObjectHeadPiecePresentation() { }
    static boolean draw(GraphicsManager graphics,PlayerHeadProfile profile,SpriteSheet<?> sheet,
                        List<? extends SpriteFramePiece> pieces,Pattern[] patterns,int base,
                        int x,int y,boolean hFlip,boolean vFlip,IntConsumer nativePiece) {
        var subject=SpritePresentation.subject(graphics);
        if(profile==null||subject.part()!=SpritePresentation.Part.OBJECT_BODY||subject.headScalePercent()==100) return false;
        int frame=-1;
        for(int i=0;i<sheet.getFrameCount();i++) if(sheet.getFrame(i).pieces()==pieces) { frame=i; break; }
        var head=profile.frame(frame);
        if(head.kind()!=PlayerHeadProfile.Kind.MASKED) return false;
        Map<Integer,SpritePresentation.PatternVersion> versions=new HashMap<>();
        for(var p:pieces) for(int tile=p.tileIndex();tile<p.tileIndex()+p.widthTiles()*p.heightTiles();tile++)
            versions.put(base+tile,SpritePresentationRenderer.version(patterns[tile]));
        int fragments=graphics.objectHeadFragmentBase();
        // Frozen slots cover only this mapping; source banks shared by repeated enemies are safe.
        SpritePresentation.bindPatternVersions(graphics,versions);
        for(int ordinal=0;ordinal<pieces.size();ordinal++) {
            int index=graphics.isSpriteSatCollectionActive()?ordinal:pieces.size()-1-ordinal;
            var transform=new SpritePresentation.HeadTransform(head.mask(),index,x,y,hFlip,vFlip,
                    subject.headScalePercent(),fragments,versions,SpritePresentationRenderer.headSatReplay());
            SpritePresentation.withHead(graphics,transform,()->nativePiece.accept(index));
        }
        return true;
    }
}
