package starfall;

import com.openggf.mods.scene.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.openggf.mods.scene.RomSpriteRequest.Compression.KOSINSKI_MODULED;

/** Native 16px block composition from LevelLoadBlock, cached once at scene entry. */
public final class BiomeArt {
    public final SceneImage[] blocks;
    public final SceneBackdrop backdrop;
    public final SceneImage surface,interior;
    public final SceneImage[] variations;

    public BiomeArt(SceneRomArt rom,Biome biome,int act) {
        // LoadLevelLoadBlock / levartptrs: 24-byte entries, palette ID in map pointer's high byte.
        int index=biome==Biome.HIDDEN_PALACE?47:biome.zone*2+act;
        byte[] entry=rom.read(0x091F0C+index*24,24);
        int[] palette=new int[64];
        System.arraycopy(rom.palette(0x0A8A3C,16),0,palette,0,16);
        int pal=pointer(rom.read(0x0A872C+(entry[8]&255)*8,4),0);
        System.arraycopy(rom.palette(pal,48),0,palette,16,48);
        int primary=pointer(entry,0),secondary=pointer(entry,4);
        int count=word(rom.read(primary,2),0)/32;
        int secondCount=primary==secondary?0:word(rom.read(secondary,2),0)/32;
        SceneImage[][] sheets=new SceneImage[4][];
        for(int line=0;line<4;line++) {
            int[] colors=Arrays.copyOfRange(palette,line*16,line*16+16);
            List<SceneImage> pages=new ArrayList<>();
            pages.add(sheet(rom,primary,count,colors));
            if(secondCount>0)pages.add(sheet(rom,secondary,secondCount,colors));
            sheets[line]=pages.toArray(new SceneImage[0]);
        }
        byte[] maps=new BlockArchive(rom.read(pointer(entry,8),65536)).decode();
        blocks=new SceneImage[maps.length/8];
        for(int block=0;block<blocks.length;block++) {
            int[] pixels=new int[256];boolean valid=true;
            for(int part=0;part<4;part++) {
                int data=word(maps,block*8+part*2),tile=data&0x7FF,line=data>>>13&3;
                if(tile>=count+secondCount){valid=false;break;}
                SceneImage sheet=sheets[line][tile<count?0:1];int local=tile<count?tile:tile-count;
                for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                    int sx=(data&0x0800)==0?x:7-x,sy=(data&0x1000)==0?y:7-y;
                    pixels[((part/2)*8+y)*16+(part%2)*8+x]=sheet.pixel(local%16*8+sx,local/16*8+sy);
                }
            }
            if(valid)blocks[block]=new SceneImage(16,16,pixels);
        }
        // Material indices were checked against decoded native block sheets, not raw tile order.
        // Use trim above soil/ice/sand, and coherent native rock or masonry inside it.
        int[] samples=switch(biome) {
            case MARBLE_GARDEN -> new int[]{280,2,36};
            case MUSHROOM_HILL -> new int[]{50,51,44};
            case CARNIVAL_NIGHT -> new int[]{56,20,45};
            case ICECAP -> new int[]{36,70,71};
            case SANDOPOLIS -> new int[]{1,4,162};
            case LAUNCH_BASE -> new int[]{4,11,14};
            case HYDROCITY -> new int[]{36,33,38};
            case LAVA_REEF -> new int[]{5,17,23};
            case HIDDEN_PALACE -> new int[]{13,1,3};
            case SKY_SANCTUARY -> new int[]{4,1,2};
            default -> throw new IllegalArgumentException("Angel Island has its own material bank");
        };
        variations=new SceneImage[samples.length-1];
        for(int n=0;n<samples.length;n++)if(blocks[samples[n]]==null||!opaque(blocks[samples[n]]))
            throw new IllegalStateException(biome.label+" ROM material "+samples[n]+" is incomplete");
        surface=blocks[samples[0]];interior=blocks[samples[1]];
        for(int n=1;n<samples.length;n++)variations[n-1]=blocks[samples[n]];
        backdrop=rom.hasZonePictures(biome.zone,act)?rom.zoneBackdrop(biome.zone,act):null;
    }
    private static SceneImage sheet(SceneRomArt rom,int address,int count,int[] palette) {
        int rows=count/16,remainder=count%16;int[] pixels=new int[128*((count+15)/16)*8];
        if(rows>0) {
            SceneImage main=rom.tiles(address,KOSINSKI_MODULED,0,16,rows,false,palette);
            System.arraycopy(main.pixels(),0,pixels,0,main.width()*main.height());
        }
        if(remainder>0) {
            SceneImage tail=rom.tiles(address,KOSINSKI_MODULED,rows*16,remainder,1,false,palette);
            for(int y=0;y<8;y++)for(int x=0;x<remainder*8;x++)pixels[(rows*8+y)*128+x]=tail.pixel(x,y);
        }
        return new SceneImage(128,((count+15)/16)*8,pixels);
    }
    private static boolean opaque(SceneImage image){for(int pixel:image.pixels())if(pixel>>>24!=255)return false;return true;}
    static int word(byte[] bytes,int offset){return (bytes[offset]&255)<<8|bytes[offset+1]&255;}
    static int pointer(byte[] bytes,int offset){return (bytes[offset+1]&255)<<16|(bytes[offset+2]&255)<<8|bytes[offset+3]&255;}

    /** Bounded standard Kosinski reader for the creator's ROM block table (not a gameplay queue). */
    static final class BlockArchive {
        private final byte[] input;
        private int position,descriptor,bits;
        BlockArchive(byte[] input){this.input=input;}
        private int next(){if(position>=input.length)throw new IllegalArgumentException("Truncated ROM blocks");return input[position++]&255;}
        private void refill(){descriptor=next()|next()<<8;bits=16;}
        private int bit(){int value=descriptor&1;descriptor>>>=1;if(--bits==0)refill();return value;}
        byte[] decode() {
            byte[] output=new byte[16384];int size=0;refill();
            while(size<output.length) {
                if(bit()!=0){output[size++]=(byte)next();continue;}
                int distance,length;
                if(bit()!=0) {
                    int low=next(),high=next();distance=(((high&0xF8)<<5)|low)-8192;
                    length=high&7;
                    if(length==0){length=next();if(length==0)return Arrays.copyOf(output,size);if(length==1)continue;length++;}
                    else length+=2;
                }else {length=2+(bit()<<1)+bit();distance=next()-256;}
                if(size+distance<0||size+length>output.length)throw new IllegalArgumentException("Invalid ROM block match");
                for(int n=0;n<length;n++){output[size]=output[size+distance];size++;}
            }
            throw new IllegalArgumentException("ROM block table too large");
        }
    }
}
