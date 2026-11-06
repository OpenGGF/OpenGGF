package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.*;
/** Original-USA C/D Capsule15 data, N1ecc5f3a Level/_Objects/Level End/Main.asm105-379,818-929.
* Actual ObjectIndex/direct/PC-indexed operands and full original images authenticate every finite view.
* Counted visuals use the existing decoder and existing19-source capsule art's104-tile metadata.
* No art decode/source/bank, palette mutation, body/World/Frame/clock/publication is owned. */
public final class CdR13CapsuleAssets {
    public enum View {
        OBJECT_INDEX(0x3B82,0x3B9A,256,"430ff3d7defba4d3c7da091a430c95024d9a58c318b6174d704c9e7386ff8f86","a1e9e48e9cf08f0b2ba01e7d4d781b9a23082702f84305e94f318f990b24fcb2"),
        CAPSULE_CODE(0xAF16,0xAF2E,570,"72baf92b3421505b64151858093bff115e5791bb7c3b5c43ef30ffa7b9e60e2d","3acb3db3cc9bdb4ee4d5ab3476f2bae05934f5b99c5925f09a78cbc72f000295"),
        ROUTINE_DIRECTORY(0xAF42,0xAF5A,12,"2957dcab3ebd0839ee6ddd36722afd60212232127ba63e0dd7ef68a7b5810672"),
        RESULTS_CODE(0xB52E,0xB546,156,"5c0e27ee76bcd0888a9ee507bf1359c82b2b29a6e4e0a4230f118a623df56310","b22a16a516ce357e9d9e9a36127e3653613061d486862b3d88368d0347ad9d3a"),
        RESULTS_ACTIVE(0xB5F4,0xB60C,2,"1ceeabf0c6a5a30bad12cdac0e3ab015a7188a42e6aebb556aad00bb9cd693ad"),
        LOAD_CAPSULE_PALETTE(0xB5F6,0xB60E,22,"5f9fed22ce8a644324e8e6cbb2ade0309ec36a0e96a36d894007981e8b5d7f0d","7a617083e850f12feb57cf2a60f02fe36137409ef67c55c1c883ed9d098c29fe"),
        CAPSULE_PALETTE(0xB60C,0xB624,32,"d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d"),
        EXPLOSION_POSITIONS(0xB04E,0xB066,16,"2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1"),
        SEED_VELOCITIES(0xB0C2,0xB0DA,22,"826f8fb8b7b2e24319abfa2ee4631e03a8a611e7158c6f02954ea4fb07cadd59"),
        TIME_BONUSES(0xB5CA,0xB5E2,42,"be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861"),
        ANIMATIONS(0x3C90E,0x3CB0C,52,"1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9"),
        MAPPINGS(0x3C942,0x3CB40,210,"9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646");
        final int good,bad,length;
        final String goodSha,badSha;
        View(int c,int d,int n,String sha){
            this(c,d,n,sha,sha);
        }
        View(int c,int d,int n,String cs,String ds){
            good=c;
            bad=d;
            length=n;
            goodSha=cs;
            badSha=ds;
        }
    }
    public record Range(View view,CdNativeFile file,int fileOffset,int length,String sha256){
        public int nativeAddress(){
            return 0x200000+fileOffset;
        }
    }
    public record CapsulePalette(int sourceNativeAddress,int destinationWord,int longwordCounter,CdAssetBytes bytes){
        public CapsulePalette{
            Objects.requireNonNull(bytes);
        }
    }
    private final CdR13FutureAssets assets;
    private final CdR13FutureAssets.Identity identity;
    private final CdAssetBytes[] views=new CdAssetBytes[View.values().length];
    private final List<Range> ranges;
    private final List<Integer> routines;
    private final CdObjectVisualAssets visual;
    private final CapsulePalette capsulePalette;
    private final CdR13FutureAssets.PaletteBinding restoredPalette;
    private CdR13CapsuleAssets(CdR13FutureAssets a){
        assets=Objects.requireNonNull(a);
        a.requireAuthenticatedLayout();
        identity=Objects.requireNonNull(a.identity());
        var image=Objects.requireNonNull(a.originalImage());
        boolean good=identity.file()==CdNativeFile.R13C&&identity.stage().equals(CdStagePayload.R13C.stage()),bad=identity.file()==CdNativeFile.R13D&&identity.stage().equals(CdStagePayload.R13D.stage());
        String expected=good?"3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f":"27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356";
        if(identity.variant()!=CdReleaseVariant.USA||(!good&&!bad)||identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM||image.fileBytes()!=0x40000||!expected.equals(identity.mmdSha256())||!expected.equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))throw new IllegalArgumentException("Original R13 Capsule source differs");
        var rr=new ArrayList<Range>();
        for(var v:View.values()){
            int offset=good?v.good:v.bad;
            String sha=good?v.goodSha:v.badSha;
            var b=CdAssetBytes.copyOf(image.readFile(offset,v.length));
            if(!sha.equals(CdR11aProfile.sha256(b.copy())))throw new IllegalArgumentException("Original Capsule view differs: "+v);
            views[v.ordinal()]=b;
            rr.add(new Range(v,identity.file(),offset,v.length,sha));
        }
        ranges=List.copyOf(rr);
        require(view(View.OBJECT_INDEX).unsignedLong(20*4)==range(View.CAPSULE_CODE).nativeAddress(),"ObjectIndex15");
        var rt=new ArrayList<Integer>();
        int[] rel={
            12,60,160,1516,1714,406
        };
        for(int i=0;i<6;i++){
            int r=(short)view(View.ROUTINE_DIRECTORY).unsignedWord(i*2);
            require(r==rel[i],"Capsule routine directory");
            rt.add(range(View.ROUTINE_DIRECTORY).nativeAddress()+r);
        }
        routines=List.copyOf(rt);
        int delta=good?0:24;
        requireHex(image,0xAF5E+delta,"217c"+hex32(mappingAddress())+"0004");
        requireHex(image,0xB09A+delta,"237c"+hex32(mappingAddress())+"0004");
        requireHex(image,0xAF66+delta,"317c24810002");
        requireHex(image,0xB0A2+delta,"337c24810002");
        String ani="43f9"+hex32(range(View.ANIMATIONS).nativeAddress());
        requireHex(image,0xAF7E+delta,ani);
        requireHex(image,0xB0D8+delta,ani);
        requireIndexed(image,0xAFFC+delta,0x45FB,View.EXPLOSION_POSITIONS);
        requireIndexed(image,0xB0B4+delta,0x337B,View.SEED_VELOCITIES);
        requireIndexed(image,0xB5B4+delta,0x31FB,View.TIME_BONUSES);
        int levelIdAddress=a.range(CdR13FutureAssets.Asset.LEVEL_DATA_INDEX).nativeAddress()+14;
        requireHex(image,0xB060+delta,"1039"+hex32(levelIdAddress));
        requireHex(image,0xB068+delta,"4eb9"+hex32(good?0x20049C:0x200508));
        int paletteId=a.view(CdR13FutureAssets.Asset.LEVEL_DATA_INDEX).unsignedByte(14);
        restoredPalette=a.paletteBindings().stream().filter(p->p.nativeId()==paletteId).findFirst().orElseThrow(()->new IllegalArgumentException("Capsule restored palette binding absent"));
        require(paletteId==4&&restoredPalette.source()==CdR13FutureAssets.Asset.PAL_LEVEL&&restoredPalette.bytes().length()==96&&restoredPalette.destinationWord()==0xFB20&&restoredPalette.longwordCounter()==23,"actual level palette4");
        var directory=CdAssetBytes.copyOf(image.readFile(good?0x4EC:0x558,48));
        int paletteAt=paletteId*8;
        int levelPaletteOffset=CdR13FutureAssets.Asset.PAL_LEVEL.fileOffset(identity.file());
        require(directory.unsignedLong(paletteAt)==0x200000L+levelPaletteOffset
        && directory.unsignedWord(paletteAt+4)==restoredPalette.destinationWord()
        && directory.unsignedWord(paletteAt+6)==restoredPalette.longwordCounter()
        && Arrays.equals(restoredPalette.bytes().copy(),image.readFile(levelPaletteOffset,96)),
        "original level palette pointer/descriptor/bytes");
        var pal=view(View.LOAD_CAPSULE_PALETTE);
        require(pal.unsignedWord(0)==0x3C3C&&pal.unsignedWord(4)==0x43F9&&pal.unsignedLong(6)==range(View.CAPSULE_PALETTE).nativeAddress()&&pal.unsignedWord(10)==0x45F8,"capsule palette loader");
        capsulePalette=new CapsulePalette((int)pal.unsignedLong(6),pal.unsignedWord(12),pal.unsignedWord(2),view(View.CAPSULE_PALETTE));
        require(capsulePalette.destinationWord()==0xFB20&&capsulePalette.longwordCounter()==7,"capsule palette copy bounds");
        var art=CdR13SectionSources.fromAssets(a).source(CdR13SectionSources.SourceId.ART_FLOWER_CAPSULE);
        require(art.tileCount()==104,"capsule original art header");
        visual=CdObjectVisualAssets.decode(view(View.MAPPINGS).copy(),9,view(View.ANIMATIONS).copy(),2,art.tileCount());
    }
    public static CdR13CapsuleAssets bind(CdR13FutureAssets a){
        return new CdR13CapsuleAssets(a);
    }
    public boolean ownsAssets(CdR13FutureAssets a){
        return assets==a;
    }
    public CdR13FutureAssets.Identity identity(){
        return identity;
    }
    public List<Range> ranges(){
        return ranges;
    }
    public Range range(View v){
        return ranges.get(Objects.requireNonNull(v).ordinal());
    }
    public CdAssetBytes view(View v){
        return views[Objects.requireNonNull(v).ordinal()];
    }
    public List<Integer> routineTargets(){
        return routines;
    }
    public CdObjectVisualAssets visual(){
        return visual;
    }
    public int mappingAddress(){
        return range(View.MAPPINGS).nativeAddress();
    }
    public int tileWord(){
        return 0x2481;
    }
    public CapsulePalette capsulePalette(){
        return capsulePalette;
    }
    public CdR13FutureAssets.PaletteBinding restoredPalette(){
        return restoredPalette;
    }
    private void requireIndexed(CdMmdImage image,int at,int opcode,View target){
        byte[] b=image.readFile(at,4);
        require(((b[0]&255)<<8|(b[1]&255))==opcode&&0x200000+at+2+(byte)b[3]==range(target).nativeAddress(),"actual PC indexed Capsule consumer");
    }
    private static void requireHex(CdMmdImage image,int at,String hex){
        require(HexFormat.of().formatHex(image.readFile(at,hex.length()/2)).equals(hex),"Capsule operand at "+Integer.toHexString(at));
    }
    private static String hex32(int x){
        return String.format(Locale.ROOT,"%08x",x);
    }
    private static void require(boolean b,String what){
        if(!b)throw new IllegalArgumentException(what);
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
