package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.*;
/** Finite original-USA R13C/D Results3A metadata; no art/current/decoder/body/programme authority.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff Results/Main.asm42-386 and Level End/Main.asm818-903.
 * Four native counted roots share the complete original include and genuine EVEN byte. There is no
 * Results animation script or uniform120-tile mapping hull: score pieces also use original HUD slots.
 */
public final class CdR13ResultsAssets {
 public enum View {
  CODE(0xDBF6,0xDC0E,704,"3655a2f8a66116033bf9decf5b9eaa3a17e72f32cbd2f0f380701e7334ec8a6e","ef336ba081081a532e038e2fcd4bc81b48f124e521e043bf46e00a5f56341ded"),
  ROUTINE_DIRECTORY(0xDC04,0xDC1C,10,"89ce92572429037c4193d89a9b1e3aaa0443203a40d91732d5450d32d8b51f1f"),
  INIT_DATA(0xDEB6,0xDECE,24,"8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8"),
  RAW_MAPPINGS(0xDECE,0xDEE6,621,"f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621"),
  MAPPING_READ_VIEW(0xDECE,0xDEE6,622,"3421ca10ca8d30d20ebc792104211ebc360321c4e6a4509abf2fa0b9a6ea73e9");
  private final int good,bad,length;private final String goodSha,badSha;
  View(int c,int d,int n,String s){this(c,d,n,s,s);}View(int c,int d,int n,String cs,String ds){good=c;bad=d;length=n;goodSha=cs;badSha=ds;}
 }
 public enum MappingRoot {
  BAD(0),BAD_SSZ3(10),GOOD(20),GOOD_SSZ3(30);
  private final int offset;MappingRoot(int n){offset=n;}public int tableOffset(){return offset;}
 }
 /** A descriptor is data only; the existing selected mapping decoder remains the sole consumer. */
 public record MappingIndex(MappingRoot root,int tableOffset,int address,int frameCount,CdAssetBytes source){public MappingIndex{Objects.requireNonNull(root);Objects.requireNonNull(source);}}
 public record Range(View view,CdNativeFile file,int fileOffset,int length,String sha256){public int nativeAddress(){return 0x200000+fileOffset;}}
 private final CdR13FutureAssets assets;private final CdR13CapsuleAssets capsule;private final CdR13FutureAssets.Identity identity;
 private final CdAssetBytes[] views=new CdAssetBytes[View.values().length];private final List<Range> ranges;private final List<Integer> routines;private final List<MappingIndex> roots;
 private CdR13ResultsAssets(CdR13FutureAssets assets,CdR13CapsuleAssets capsule){
  this.assets=Objects.requireNonNull(assets,"actual original future aggregate");this.capsule=Objects.requireNonNull(capsule,"actual capsule metadata");
  if(!capsule.ownsAssets(assets))throw new IllegalArgumentException("Results and Capsule must retain the exact same original owner");assets.requireAuthenticatedLayout();identity=assets.identity();var image=Objects.requireNonNull(assets.originalImage());
  boolean c=identity.file()==CdNativeFile.R13C&&identity.stage().equals(CdStagePayload.R13C.stage()),d=identity.file()==CdNativeFile.R13D&&identity.stage().equals(CdStagePayload.R13D.stage());
  String sha=c?"3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f":"27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356";
  require(identity.variant()==CdReleaseVariant.USA&&(c||d)&&identity.domain()==CdMmdImage.Domain.ORIGINAL_WORD_RAM&&image.fileBytes()==0x40000&&sha.equals(identity.mmdSha256())&&sha.equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))),"Original R13 Results image/profile differs");
  var rr=new ArrayList<Range>();for(var v:View.values()){int offset=c?v.good:v.bad;String hash=c?v.goodSha:v.badSha;var b=CdAssetBytes.copyOf(image.readFile(offset,v.length));require(hash.equals(CdR11aProfile.sha256(b.copy())),"Original Results source differs: "+v);views[v.ordinal()]=b;rr.add(new Range(v,identity.file(),offset,v.length,hash));}ranges=List.copyOf(rr);
  require(assets.view(CdR13FutureAssets.Asset.OBJECT_INDEX).unsignedLong(0x39*4)==range(View.CODE).nativeAddress(),"actual ObjectIndex3A");
  int[] relative={10,30,234,292,472};var rt=new ArrayList<Integer>();for(int i=0;i<relative.length;i++){int value=(short)view(View.ROUTINE_DIRECTORY).unsignedWord(i*2);require(value==relative[i],"actual Results directory");rt.add(range(View.ROUTINE_DIRECTORY).nativeAddress()+value);}routines=List.copyOf(rt);
  int lea=c?0xDC46:0xDC5E;var operand=CdAssetBytes.copyOf(image.readFile(lea,6));require(operand.unsignedWord(0)==0x45F9&&operand.unsignedLong(2)==range(View.INIT_DATA).nativeAddress(),"actual Results init LEA");
  int[] sites=c?new int[]{0xDC88,0xDC98,0xDCA2,0xDCB2}:new int[]{0xDCA0,0xDCB0,0xDCBA,0xDCCA};int[] offsets={10,30,0,20};for(int i=0;i<sites.length;i++){var b=CdAssetBytes.copyOf(image.readFile(sites[i],8));require(b.unsignedWord(0)==0x237C&&b.unsignedLong(2)==mappingAddress()+offsets[i]&&b.unsignedWord(6)==4,"actual live mapping operand");}
  require(view(View.MAPPING_READ_VIEW).unsignedByte(621)==0&&Arrays.equals(view(View.RAW_MAPPINGS).copy(),mappings().slice(0,621).copy()),"genuine complete mapping include/EVEN");
  var list=new ArrayList<MappingIndex>();for(var root:MappingRoot.values())list.add(new MappingIndex(root,root.tableOffset(),mappingAddress()+root.tableOffset(),5,mappings()));roots=List.copyOf(list);
  // Borrow the original complete producer table, not a reconstructed bonus curve or new view inventory.
  require(timeBonuses().length()==42&&"be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861".equals(CdR11aProfile.sha256(timeBonuses().copy())),"actual complete bonus table");
 }
 public static CdR13ResultsAssets bind(CdR13FutureAssets assets,CdR13CapsuleAssets capsule){return new CdR13ResultsAssets(assets,capsule);}
 public boolean ownsAssets(CdR13FutureAssets expected){return assets==expected;}public CdR13FutureAssets.Identity identity(){return identity;}
 public List<Range> ranges(){return ranges;}public Range range(View view){return ranges.get(Objects.requireNonNull(view).ordinal());}public CdAssetBytes view(View view){return views[Objects.requireNonNull(view).ordinal()];}
 public CdAssetBytes initData(){return view(View.INIT_DATA);}public CdAssetBytes mappings(){return view(View.MAPPING_READ_VIEW);}public int mappingAddress(){return range(View.RAW_MAPPINGS).nativeAddress();}
 public List<Integer> routineTargets(){return routines;}public List<MappingIndex> mappingRoots(){return roots;}
 public CdAssetBytes timeBonuses(){return capsule.view(CdR13CapsuleAssets.View.TIME_BONUSES);}
 public CdR13CapsuleAssets.Range timeBonusRange(){return capsule.range(CdR13CapsuleAssets.View.TIME_BONUSES);}
 private static void require(boolean ok,String text){if(!ok)throw new IllegalArgumentException(text);}
}
