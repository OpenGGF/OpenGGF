package starfall;

import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneRomArt;
import static starfall.BiomeArt.*;

/** Private, static presentation art. No writes to the live level or its animation clocks. */
final class BackdropTiles {
    private BackdropTiles() { }
    static SceneImage[][] firstFrame(SceneRomArt rom,Biome biome,int act,int[] palette) {
        SceneImage[][] tiles=new SceneImage[4][0x800];
        // Offs_AniFunc dispatch: SOZ uses direct DMA only, despite its unused LRZ list.
        int script=switch(biome) {
            case MARBLE_GARDEN -> 0x028862;case CARNIVAL_NIGHT -> 0x028882;
            case ICECAP -> 0x028990;case MUSHROOM_HILL -> 0x0289E8;
            case HYDROCITY -> 0x02882C;
            case LAVA_REEF -> act==0?0x028A6A:0x028A84;
            case HIDDEN_PALACE -> 0x028C40;
            default -> -1;
        };
        if(script>=0)prime(rom,script,palette,tiles);
        // AnimateTiles_* direct-DMA strips at phase zero. Sizes below are bytes,
        // twice the routine's DMA word count. These replace numbered ROM filler art.
        switch(biome) {
            case CARNIVAL_NIGHT -> upload(rom,0x2B5B80,0x400,0x308,palette,tiles);
            case MUSHROOM_HILL -> {
                upload(rom,0x0BA1C0,0x100,0x1B8,palette,tiles);
                upload(rom,0x0BA9C0,0x400,0x1D5,palette,tiles);
            }
            case ICECAP -> {
                upload(rom,0x2B8580,0x200,0x10E,palette,tiles);
                upload(rom,0x2B9580,0x100,0x122,palette,tiles);
                upload(rom,0x2B9780,0x080,0x12A,palette,tiles);
                upload(rom,0x2B9880,0x040,0x12E,palette,tiles);
                upload(rom,0x2B9900,0x020,0x130,palette,tiles);
            }
            case SANDOPOLIS -> {
                if(act==0) {
                    upload(rom,0x0BD9C0,0x180,0x330,palette,tiles);
                    upload(rom,0x0BE5C0,0x0C0,0x33C,palette,tiles);
                }else upload(rom,0x0BFDC0,0x0C0,0x330,palette,tiles);
            }
            case LAVA_REEF -> {
                upload(rom,0x0C0300,0x480,0x320,palette,tiles);
                upload(rom,0x0C2700,0x180,0x344,palette,tiles);
            }
            default -> { }
        }
        return tiles;
    }
    /** AniPLC's signed duration determines the size of its aligned frame table. */
    private static void prime(SceneRomArt rom,int address,int[] palette,SceneImage[][] tiles) {
        int count=word(rom.read(address,2),0);
        if(count==0xFFFF)return;
        if(count>=64)throw new IllegalArgumentException("ROM AniPLC list too large");
        int position=address+2;
        for(int n=0;n<=count;n++) {
            byte[] header=rom.read(position,8);
            int frames=header[6]&255,size=header[7]&255,destination=word(header,4)/32;
            if(frames==0||size==0)throw new IllegalArgumentException("Empty ROM AniPLC frame");
            int first=rom.read(position+8,1)[0]&255;
            upload(rom,pointer(header,0)+first*32,size*32,destination,palette,tiles);
            int bytes=frames*(header[0]<0?2:1);
            position+=8+((bytes+1)&~1);
        }
    }
    private static void upload(SceneRomArt rom,int address,int bytes,int destination,int[] palette,SceneImage[][] tiles) {
        if(bytes<=0||bytes%32!=0||destination<0||destination+bytes/32>0x800)
            throw new IllegalArgumentException("ROM background tile range");
        byte[] raw=rom.read(address,bytes);
        for(int tile=0;tile<bytes/32;tile++)for(int line=0;line<4;line++) {
            int[] pixels=new int[64];
            for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                int packed=raw[tile*32+y*4+x/2]&255;
                int color=x%2==0?packed>>>4:packed&15;
                pixels[y*8+x]=color==0?0:palette[line*16+color];
            }
            tiles[line][destination+tile]=new SceneImage(8,8,pixels);
        }
    }
}
