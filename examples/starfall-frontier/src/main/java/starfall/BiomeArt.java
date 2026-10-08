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
    public final int backdropTop;
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
        byte[] maps=archivePair(rom,entry,8);
        blocks=blockImages(rom,primary,secondary,count,secondCount,palette,maps,null);
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
        if(rom.hasZonePictures(biome.zone,act))backdrop=rom.zoneBackdrop(biome.zone,act);
        else {
            int[] backgroundPalette=biome==Biome.ICECAP?icecapOutdoorPalette(rom,palette):palette;
            SceneImage[][] tiles=BackdropTiles.firstFrame(rom,biome,act,backgroundPalette);
            SceneImage[] backgroundBlocks=blockImages(rom,primary,secondary,count,secondCount,backgroundPalette,maps,tiles);
            backdrop=background(rom,entry,index,biome,backgroundPalette,backgroundBlocks);
        }
        backdropTop=switch(biome) {
            case MARBLE_GARDEN,ICECAP -> 0;
            case SANDOPOLIS -> act==0?160:256;
            case HIDDEN_PALACE -> 448;
            case MUSHROOM_HILL -> 320;
            default -> Math.max(0,Math.min(256,backdrop.image().height()-224));
        };
    }
    private static SceneImage[] blockImages(SceneRomArt rom,int primary,int secondary,int count,int secondCount,
                                            int[] palette,byte[] maps,SceneImage[][] overrides) {
        SceneImage[][] sheets=new SceneImage[4][];
        for(int line=0;line<4;line++) {
            int[] colors=Arrays.copyOfRange(palette,line*16,line*16+16);
            List<SceneImage> pages=new ArrayList<>();
            pages.add(sheet(rom,primary,count,colors));
            if(secondCount>0)pages.add(sheet(rom,secondary,secondCount,colors));
            sheets[line]=pages.toArray(new SceneImage[0]);
        }
        SceneImage[] blocks=new SceneImage[maps.length/8];
        for(int block=0;block<blocks.length;block++) {
            int[] pixels=new int[256];boolean valid=true;
            for(int part=0;part<4;part++) {
                int data=word(maps,block*8+part*2),tile=data&0x7FF,line=data>>>13&3;
                SceneImage override=overrides==null?null:overrides[line][tile];
                if(override==null&&tile>=count+secondCount){valid=false;break;}
                SceneImage sheet=override!=null?override:sheets[line][tile<count?0:1];
                int local=override!=null?0:tile<count?tile:tile-count;
                for(int y=0;y<8;y++)for(int x=0;x<8;x++) {
                    int sx=(data&0x0800)==0?x:7-x,sy=(data&0x1000)==0?y:7-y;
                    pixels[((part/2)*8+y)*16+(part%2)*8+x]=sheet.pixel(local%16*8+sx,local/16*8+sy);
                }
            }
            if(valid)blocks[block]=new SceneImage(16,16,pixels);
        }
        return blocks;
    }
    private static int[] icecapOutdoorPalette(SceneRomArt rom,int[] palette) {
        int[] outdoor=palette.clone();
        // Lockon S3 ICZ1_SetIntroPal / sub_23DE96 writes seven immediate longs
        // and one word to line 4 colours 1..15. Read those colours from ROM code.
        for(int n=0;n<7;n++) {
            int address=0x23DE96+n*6;
            if(word(rom.read(address,2),0)!=0x22FC)throw new IllegalArgumentException("ICZ intro palette opcode");
            System.arraycopy(rom.palette(address+2,2),0,outdoor,49+n*2,2);
        }
        if(word(rom.read(0x23DEC0,2),0)!=0x32BC)throw new IllegalArgumentException("ICZ intro palette tail");
        outdoor[63]=rom.palette(0x23DEC2,1)[0];return outdoor;
    }
    private static byte[] archivePair(SceneRomArt rom,byte[] entry,int offset) {
        int first=pointer(entry,offset),second=pointer(entry,offset+4);
        byte[] a=new BlockArchive(rom.read(first,65536)).decode();
        if(first==second)return a;
        byte[] b=new BlockArchive(rom.read(second,65536)).decode();
        byte[] result=Arrays.copyOf(a,a.length+b.length);
        System.arraycopy(b,0,result,a.length,b.length);return result;
    }
    /** LoadLevelLoadBlock2 appends both map/chunk banks; LevelPtrs supplies the BG row pointers.
     * Uses the outdoor ICZ1 intro plane (ICZ1_BackgroundInit's 0x1880), not its cave plane.
     * These are cached static ROM pictures; the creator scene owns their scroll presentation.
     */
    private SceneBackdrop background(SceneRomArt rom,byte[] entry,int index,Biome biome,int[] palette,SceneImage[] backgroundBlocks) {
        byte[] chunks=archivePair(rom,entry,16);
        int address=pointer(rom.read(0x09D5C0+index*4,4),0);
        byte[] layout=rom.read(address,4096);
        int columns=word(layout,2),rows=word(layout,6);
        int originX=biome==Biome.ICECAP?0x1880:0;
        int width=512,height=Math.min(rows*128,768);
        if(columns<=0||rows<=0||rows>32||height<224)throw new IllegalArgumentException("Invalid ROM background layout");
        int[] pixels=new int[width*height];Arrays.fill(pixels,palette[32]|0xFF000000);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
            int wx=originX+x,row=word(layout,10+(y/128)*4)&0x7FFF;
            int column=wx/128%columns;
            if(row==0)continue;
            if(row+column>=layout.length)throw new IllegalArgumentException("ROM background row outside layout");
            int chunk=layout[row+column]&255;
            int offset=chunk*128+((y%128/16)*8+wx%128/16)*2;
            if(offset+1>=chunks.length)throw new IllegalArgumentException("ROM background chunk outside bank");
            int descriptor=word(chunks,offset),block=descriptor&0x3FF;
            if(block>=backgroundBlocks.length)throw new IllegalArgumentException("ROM background block outside bank");
            SceneImage image=backgroundBlocks[block];
            // Slots outside the loaded art remain the native VDP backdrop colour.
            if(image==null)continue;
            int bx=(descriptor&0x400)==0?wx%16:15-wx%16;
            int by=(descriptor&0x800)==0?y%16:15-y%16;
            int pixel=image.pixel(bx,by);
            if(pixel>>>24!=0)pixels[y*width+x]=pixel;
        }
        SceneImage image=new SceneImage(width,height,pixels);
        // Creator-world parallax: stock level events and AniPLC timelines do not run here.
        return new SceneBackdrop(image,List.of(new SceneBackdrop.Band(0,height,.125,0)));
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
            byte[] output=new byte[65536];int size=0;refill();
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
