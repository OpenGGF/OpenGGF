package starpost.realruins;

import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.mods.scene.*;
import java.util.HashMap;
import java.util.Map;

/** CPU presentation of E2's ROM sheets in their original S1 palette, beside the host HUD claims. */
public final class RomSheetPictures {
    private final ObjectSpriteSheet sheet;
    private final int[] palette;
    private final Map<Integer,SceneSprite> frames=new HashMap<>();
    public RomSheetPictures(ObjectSpriteSheet sheet,int[] palette) { this.sheet=sheet; this.palette=palette.clone(); }
    public SceneSprite frame(int frame) { return frames.computeIfAbsent(frame,this::rasterize); }
    private SceneSprite rasterize(int frame) {
        var pieces=sheet.getFrame(Math.min(frame,sheet.getFrameCount()-1)).pieces();
        if(pieces.isEmpty()) return SceneSprite.of(new SceneImage(1,1,new int[1]));
        int left=pieces.stream().mapToInt(p->p.xOffset()).min().orElse(0);
        int top=pieces.stream().mapToInt(p->p.yOffset()).min().orElse(0);
        int right=pieces.stream().mapToInt(p->p.xOffset()+p.widthTiles()*8).max().orElse(1);
        int bottom=pieces.stream().mapToInt(p->p.yOffset()+p.heightTiles()*8).max().orElse(1);
        int w=right-left,h=bottom-top; int[] pixels=new int[w*h];
        var tiles=sheet.getPatterns();
        for(int i=pieces.size()-1;i>=0;i--) {
            var p=pieces.get(i); int line=(sheet.getPaletteIndex()+p.paletteIndex())&3;
            for(int col=0;col<p.widthTiles();col++) for(int row=0;row<p.heightTiles();row++) {
                int index=p.tileIndex()+col*p.heightTiles()+row;
                if(index<0 || index>=tiles.length) continue;
                int bx=p.xOffset()-left+(p.hFlip()?p.widthTiles()-1-col:col)*8;
                int by=p.yOffset()-top+(p.vFlip()?p.heightTiles()-1-row:row)*8;
                for(int y=0;y<8;y++) for(int x=0;x<8;x++) {
                    int color=tiles[index].getPixel(p.hFlip()?7-x:x,p.vFlip()?7-y:y)&15;
                    if(color!=0) pixels[(by+y)*w+bx+x]=0xFF000000|palette[line*16+color];
                }
            }
        }
        return new SceneSprite(new SceneImage(w,h,pixels),-left,-top);
    }
}
