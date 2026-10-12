package com.openggf.tools.modsdk;

import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.SpritePresentation;
import com.openggf.io.PixelImage;
import com.openggf.io.PngCodec;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.render.PlayerHeadProfile;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import java.io.PrintStream;
import java.nio.file.Path;

/** Native ROM mask authoring panels for ggfmod sprites; originating task: Mutator Lab, 2026-10-08.
 * Uses production DPLC/presentation composition, never reference-tree or replacement pixel art. */
final class PlayerHeadMaskReview {
    static final int CELL_SIZE = 160;
    private PlayerHeadMaskReview() { }
    static void write(SpriteArtSet art, int[] palette, Path out, int first, int count, int scale,
                      PrintStream output) throws Exception {
        if(first<0||first>=art.mappingFrames().size()||count<1)
            throw new IllegalArgumentException("heads requires first in frame bounds and count>=1");
        int end=(int)Math.min(art.mappingFrames().size(),(long)first+count);
        writeSequence(art,palette,out,java.util.stream.IntStream.range(first,end).boxed().toList(),scale,output);
    }
    /** Walk ROM animation frame order with a fresh production DPLC bank; no timing/physics simulation. */
    static void writeSequence(SpriteArtSet art,int[] palette,Path out,java.util.List<Integer> sequence,int scale,
                              PrintStream output) throws Exception {
        var profile=PlayerHeadProfile.resolve(art);
        if(sequence.isEmpty()||scale<101||scale>200||sequence.stream().anyMatch(f->f<0||f>=art.mappingFrames().size()))
            throw new IllegalArgumentException("heads requires valid mapping frames and scale=101..200");
        int columns=Math.min(4,sequence.size());
        int cell=CELL_SIZE,origin=cell/2,block=cell*4,rows=(sequence.size()+columns-1)/columns;
        PixelImage sheet=PixelImage.blank(columns*block,rows*(cell+12));
        for(int y=0;y<sheet.getHeight();y++) for(int x=0;x<sheet.getWidth();x++) sheet.setRGB(x,y,0xFF303040);
        GraphicsManager graphics=com.openggf.game.session.EngineServices.current().graphics();
        var renderer=new PlayerSpriteRenderer(art,graphics);
        output.println("Profile "+(profile==null?"unreviewed":profile.id())+" art "+PlayerHeadProfile.fingerprint(art));
        output.println("Panels: stock / enlarged head only / unchanged body only / composite; magenta = neck anchor");
        output.println("frame,classification,anchorX,anchorY,pieces,reason");
        for(int item=0;item<sequence.size();item++) {
            int f=sequence.get(item),frame=f;
            var stock=SpritePresentationRenderer.prepare(graphics,0,0,()->renderer.drawFrame(frame,origin,origin,false,false));
            var composed=SpritePresentationRenderer.prepare(graphics,0,0,()->SpritePresentation.withSubject(graphics,
                    new SpritePresentation.Subject("sonic",SpritePresentation.Part.BODY,false,scale),
                    ()->renderer.drawFrame(frame,origin,origin,false,false)));
            int x=item%columns*block,y=item/columns*(cell+12)+12;
            SpriteSheetDump.digits(sheet,f,x+2,y-10);
            paint(sheet,stock,palette,x,y,t->true);
            paint(sheet,composed,palette,x+cell,y,t->t.width()!=8);
            paint(sheet,composed,palette,x+cell*2,y,t->t.width()==8);
            paint(sheet,composed,palette,x+cell*3,y,t->true);
            var mask=profile==null?PlayerHeadProfile.unsupported():profile.frame(f);
            if(mask.kind()==PlayerHeadProfile.Kind.MASKED) for(int panel=0;panel<4;panel++) {
                int ax=x+panel*cell+origin+mask.anchorX(),ay=y+origin+mask.anchorY();
                if(ax>=0&&ay>=0&&ax<sheet.getWidth()&&ay<sheet.getHeight()) sheet.setRGB(ax,ay,0xFFFF00FF);
            }
            output.printf("%02X,%s,%d,%d,\"%s\",%s%n",f,mask.kind(),mask.anchorX(),mask.anchorY(),
                    mask.pieces().stream().sorted().toList(),mask.reason());
        }
        PngCodec.write(out,sheet);
        output.println(sequence.size()+" native frame reviews -> "+out);
    }
    private static void paint(PixelImage out,SpritePresentation.Frame frame,int[] palette,int ox,int oy,
                              java.util.function.Predicate<SpritePresentation.Tile> filter) {
        paint(out,frame,palette,ox,oy,CELL_SIZE,filter);
    }
    static void paint(PixelImage out,SpritePresentation.Frame frame,int[] palette,int ox,int oy,int cell,
                      java.util.function.Predicate<SpritePresentation.Tile> filter) {
        for(var tile:frame.tiles()) {
            if(!filter.test(tile)) continue;
            var pixels=SpritePresentationRenderer.pattern(frame.patternVersions().get(tile.patternId()));
            int left=(int)Math.floor(tile.x()),top=(int)Math.floor(tile.y());
            int right=(int)Math.ceil(tile.x()+tile.width()),bottom=(int)Math.ceil(tile.y()+tile.height());
            for(int y=top;y<bottom;y++) for(int x=left;x<right;x++) {
                int px=Math.min(7,(int)((x+.5-tile.x())*8/tile.width()));
                int py=Math.min(7,(int)((y+.5-tile.y())*8/tile.height()));
                // Half-open pixel-centre coverage, matching the GPU quad rather than smearing its edge.
                if(x+.5<tile.x()||x+.5>=tile.x()+tile.width()
                        ||y+.5<tile.y()||y+.5>=tile.y()+tile.height()) continue;
                if(px<0||py<tile.rowStart()||py>=tile.rowEnd()) continue;
                int value=pixels.getPixel(tile.hFlip()?7-px:px,tile.vFlip()?7-py:py)&15;
                int dx=ox+x,dy=oy+y;
                if(value!=0) {
                    if(dx<ox||dy<oy||dx>=ox+cell||dy>=oy+cell)
                        throw new IllegalStateException("Review panel would crop an opaque ROM pixel");
                    out.setRGB(dx,dy,palette[tile.palette()*16+value]);
                }
            }
        }
    }
}
