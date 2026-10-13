package chaos;

import com.openggf.mods.scene.*;
import com.openggf.mods.scene.art.StockSceneArt;
import com.openggf.mods.ui.PixelCanvas;
import java.util.*;

/** Bounded ROM motifs. No disassembly bytes, creator images or stand-ins supply game artwork. */
public final class ChaosArt {
    public enum Motif { RING,MONITOR,BADNIK,SPRING,SONIC,TAILS,ROLL,FIRE,LIGHTNING,BUBBLE }
    private final Map<Motif,SceneImage> images=new EnumMap<>(Motif.class);
    public ChaosArt(SceneRomArt rom) {
        // Named recipes enforce game + exact locked-on ROM SHA-1 before any raw request below.
        StockSceneArt.S3K_RING.request(Objects.requireNonNull(rom));
        int[] palette=new int[64];
        System.arraycopy(rom.palette(0x0A8A3C,16),0,palette,0,16); // Pal_SonicTails
        System.arraycopy(rom.palette(0x0A8B7C,48),0,palette,16,48); // Pal_AIZ, lines 1..3
        images.put(Motif.RING,visible(rom.sprites(StockSceneArt.S3K_RING.request(rom),palette),0));
        images.put(Motif.MONITOR,visible(rom.sprites(StockSceneArt.S3K_MONITOR.request(rom),palette),0));
        images.put(Motif.BADNIK,visible(rom.sprites(StockSceneArt.S3K_BLOOMINATOR.request(rom),palette),0));
        // Obj_Spring uses SpikesSprings+$10; Map_Spring frame 0 is the red plate/base.
        // Map_Spring2 shares the adjacent pointer block: explicit 11 avoids inferring 22 frames.
        images.put(Motif.SPRING,visible(rom.sprites(RomSpriteRequest.of(0x1927FE,
                RomSpriteRequest.Compression.NEMESIS,0x02375C,0).withTileOffset(-16).withMappingFrameCount(11),palette),0));
        var sonic=rom.character("sonic");var tails=rom.character("tails");
        images.put(Motif.SONIC,animated(sonic,5));images.put(Motif.TAILS,animated(tails,5));images.put(Motif.ROLL,animated(sonic,2));
        // Obj_*Shield selects palette 0 and the PLAYER DPLC layout; native verified tables.
        images.put(Motif.FIRE,shield(rom,palette,0x18C704,8608,0x019AC6,0x019CE6,25));
        images.put(Motif.LIGHTNING,shield(rom,palette,0x18E8A4,4160,0x019DC8,0x019EFA,24));
        images.put(Motif.BUBBLE,shield(rom,palette,0x18F984,4416,0x019F82,0x01A076,13));
    }
    private static SceneImage shield(SceneRomArt rom,int[] palette,int art,int size,int mappings,int dplc,int count) {
        return visible(rom.sprites(RomSpriteRequest.streamed(art,size,mappings,dplc,RomSpriteRequest.DplcLayout.PLAYER,0)
                .withMappingFrameCount(count),palette),0);
    }
    private static SceneImage animated(SceneSpriteSet sprites,int animation) {
        int[] frames=sprites.animationFrames(animation);
        if(frames.length==0)throw new IllegalStateException("Native character animation unavailable");
        return visible(sprites,frames[0]);
    }
    private static SceneImage visible(SceneSpriteSet sprites,int preferred) {
        Objects.requireNonNull(sprites,"Native sprite bank");
        for(int offset=0;offset<Math.min(32,sprites.frameCount());offset++) {
            int frame=Math.floorMod(preferred+offset,sprites.frameCount());var image=sprites.frame(frame).image();
            if(image.width()>256 || image.height()>256)throw new IllegalStateException("Native motif exceeds bounds");
            if(image.width()>1 && image.height()>1 && Arrays.stream(image.pixels()).anyMatch(p->(p>>>24)!=0))return image;
        }
        throw new IllegalStateException("Native motif has no visible frame");
    }
    /** Nearest native pixels, centred in the box, coalesced into horizontal fill runs. */
    public void draw(PixelCanvas canvas,Motif motif,int left,int top,int boxWidth,int boxHeight) {
        var image=images.get(motif);double scale=Math.min((double)boxWidth/image.width(),(double)boxHeight/image.height());
        int width=Math.max(1,(int)(image.width()*scale)),height=Math.max(1,(int)(image.height()*scale));
        left+=(boxWidth-width)/2;top+=(boxHeight-height)/2;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int pixel=image.pixel(x*image.width()/width,y*image.height()/height);if((pixel>>>24)==0)continue;
            int start=x;
            while(x+1<width && image.pixel((x+1)*image.width()/width,y*image.height()/height)==pixel)x++;
            canvas.fill(left+start,top+y,x-start+1,1,pixel);
        }
    }
}
