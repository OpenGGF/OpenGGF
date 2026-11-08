package com.openggf.game.presentation;

import com.openggf.game.*;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.level.Pattern;
import com.openggf.level.Palette;
import com.openggf.level.objects.*;
import com.openggf.level.render.*;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.io.IOException;
import java.util.*;

/** Character-owned sign pixels; native consumers own pose selection, motion and timing. */
public final class CharacterSignRenderer {
    private CharacterDefinition definition;
    private CharacterPresentation presentation;

    public boolean draw(ObjectServices services, Host host, ObjectSpriteSheet nativeSheet,
            int frame, int faceFrame, int alternateFrame,
            int spin1Frame, int edgeFrame, int spin3Frame, int x, int y) {
        if (nativeSheet == null || services.spriteManager() == null || services.gameModule() == null) return false;
        var player = services.spriteManager().getMainPlayable();
        if (player == null) return false;
        var current = services.gameModule().getPlayableCharacterRegistry().find(player.characterKey()).orElse(null);
        if (current != definition) {
            definition=current; presentation=null;
            if (current != null && current.presentationSupplier()!=null) {
                try { presentation=current.presentationSupplier().load(player.characterKey().persisted()); }
                catch (IOException failure) { throw new IllegalStateException("Unable to load sign presentation",failure); }
            }
        }
        Art art=presentation==null ? null : presentation.art().get(host);
        SignFrame pose=select(art,frame,faceFrame,alternateFrame,spin1Frame,edgeFrame,spin3Frame);
        if (pose==null) return false;
        var graphics=services.graphicsManager();
        var context=player.getSpriteRenderer()==null ? null : player.getSpriteRenderer().getRenderContext();
        int palette=context==null ? 0 : context.getPaletteLineBase();
        var sheet=compose(nativeSheet,frame,pose,palette,
                pose.backing()==null ? null : services.levelManager().getCurrentLevel().getPalette(pose.backing().paletteLine()));
        // Draw-occurrence residency lasts until the current queued sprite batch has completed.
        var renderer=new PatternSpriteRenderer(sheet,graphics);
        renderer.ensurePatternsCached(graphics,graphics.objectHeadFragmentBase());
        renderer.drawFrameIndex(0,x,y,false,false);
        return true;
    }

    public static SignFrame select(Art art,int frame,int face,int alternate,int spin1,int edge,int spin3) {
        if(art==null) return null;
        Sign sign=art.sign();
        if(sign==null) return frame==face && art.signpostFace()!=null ? new SignFrame(art.signpostFace(),null) : null;
        if(frame==face) return sign.face();
        if(frame==alternate) return sign.alternate();
        if(frame==spin1) return sign.spin1();
        if(frame==edge) return sign.edge();
        if(frame==spin3) return sign.spin3();
        return null;
    }

    /** Preserve native pole pieces and replace only the rectangular sign board at y=-16. */
    public static ObjectSpriteSheet compose(ObjectSpriteSheet nativeSheet,int frame,SignFrame pose,
            int characterPalette,Palette nativePalette) {
        var patterns=new ArrayList<Pattern>();
        Collections.addAll(patterns,nativeSheet.getPatterns());
        var pieces=new ArrayList<SpriteMappingPiece>();
        addImage(patterns,pieces,pose.face(),characterPalette);
        if(pose.backing()!=null) {
            var backing=pose.backing();
            byte[] pixels=backing.image().pixels();
            int[] rgb=backing.rgb();
            int[] remap=new int[16];
            for(int i=1;i<16;i++) {
                int best=Integer.MAX_VALUE;
                for(int j=1;j<16;j++) {
                    int dr=((rgb[i]>>16)&255)-(nativePalette.red(j)&255);
                    int dg=((rgb[i]>>8)&255)-(nativePalette.green(j)&255);
                    int db=(rgb[i]&255)-(nativePalette.blue(j)&255);
                    int distance=dr*dr+dg*dg+db*db;
                    if(distance<best) { best=distance;remap[i]=j; }
                }
            }
            for(int i=0;i<pixels.length;i++) pixels[i]=(byte)remap[pixels[i]];
            addImage(patterns,pieces,new Image(backing.image().width(),backing.image().height(),pixels),backing.paletteLine());
        }
        // Native mappings put the pole below the board; S3K's pole is a separate child.
        nativeSheet.getFrame(frame).pieces().stream().filter(piece->piece.yOffset()>=16).forEach(pieces::add);
        if(patterns.size()>256) throw new IllegalArgumentException("Sign exceeds draw-occurrence tile budget");
        return new ObjectSpriteSheet(patterns.toArray(Pattern[]::new),List.of(new SpriteMappingFrame(List.copyOf(pieces))),-1,0);
    }
    private static void addImage(List<Pattern> patterns,List<SpriteMappingPiece> pieces,Image image,int palette) {
        Pattern[] tiles=CharacterNameComposer.tiles(image);
        int base=patterns.size(); Collections.addAll(patterns,tiles);
        int rows=image.height()/8,columns=image.width()/8;
        // At most four tiles per native SAT piece; images are already projected/preflipped.
        for(int col=0;col<columns;col+=3) {
            int width=Math.min(3,columns-col);
            pieces.add(new SpriteMappingPiece(-image.width()/2+col*8,-16,width,rows,
                    base+col*rows,false,false,palette));
        }
    }
}
