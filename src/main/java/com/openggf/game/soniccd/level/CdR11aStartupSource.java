package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.CdLevelHud;
import java.io.IOException;
import java.util.*;
/** Authenticated original-USA R11 startup object/font operands. N1ecc5f3a HUD and Points,
 * Title Card/Main, PP Title Card Data. Bounded operand/table decoding only: no instruction
 * interpreter, execution, assets fallback, current-pixel claim or invocation capability. */
public final class CdR11aStartupSource {
 private final CdR11aAssets assets;private final CdMmdImage image;
 private final CdObjectMappings.DeclaredTable hud,title;
 private final CdTitleCard.Assets titleAssets;private final CdReservedHud.Assets hudAssets;
 private final CdLevelHud.Assets font;
 private CdR11aStartupSource(CdR11aAssets assets,CdMmdImage image)throws IOException{
  this.assets=Objects.requireNonNull(assets);this.image=Objects.requireNonNull(image);
  if(assets.identity().variant()!=CdReleaseVariant.USA||assets.identity().file()!=CdNativeFile.R11A)throw new IOException("Original USA R11 startup source required");
  assets.profile().verifyProgram(assets.identity().file(),assets.identity().domain(),image);
  int hudEntry=handler(0x1C),titleEntry=handler(0x3C);
  int hudInit=init(hudEntry,8,2),titleInit=init(titleEntry,0,6);
  require(hudInit+4,"217c");require(hudInit+10,"0004");int hudMap=address(hudInit+6);
  require(titleInit+42,"217c");require(titleInit+48,"0004");int titleMap=address(titleInit+44);
  require(titleInit+60,"45f9");int data=address(titleInit+62);
  hud=mapping(hudMap,4);title=mapping(titleMap,9);
  titleAssets=new CdTitleCard.Assets(read(data,64),0x200000+titleMap);
  hudAssets=new CdReservedHud.Assets(0x200000+hudMap,assets.openingObjects().view(CdR11aObjectAssets.Table.MONITOR_TIME_MAPPINGS).range().nativeAddress());
  // AddPoints follows EVEN after MapSpr_HUD. Native fixed-width source arithmetic is72
  // bytes (8,6,2,6,2,2,2,2,6,2,10,6,6,4,6,2); no CPU instruction is executed.
  int update=hudUpdate(hudMap,hud.bytes().length());
  require(update,"4a79");require(update+8,"6100");
  var code=read(update,0x500);int score=pcReset(code,update,6),rings=pcReset(code,update,2);
  if(rings!=score+8)throw new IOException("Original HUD reset source relation differs");
  int divisors=absoluteLea(code,5);
  var nums=assets.view(CdR11aAsset.ART_HUD_NUMBERS).bytes();
  font=new CdLevelHud.Assets(nums,read(score,12),read(divisors,40));
  long[] expected={100000,10000,1000,100,10,1,0x1000,0x100,0x10,1};for(int i=0;i<10;i++)if(font.divisors().unsignedLong(i*4)!=expected[i])throw new IOException("Original HUD divisor source differs");
  // Native destination operands authenticate the semantic font-writer ranges below.
  requireHudDestinations(code);
 }
 static CdR11aStartupSource bind(CdR11aAssets assets,CdMmdImage image)throws IOException{return new CdR11aStartupSource(assets,image);}
 public boolean owns(CdR11aAssets actual){return assets==actual;}
 public CdTitleCard.Assets titleAssets(){return titleAssets;}public CdReservedHud.Assets hudAssets(){return hudAssets;}
 public CdLevelHud.Assets font(){return font;}
 public List<CdObjectMappings.DeclaredTable> mappings(){return List.of(hud,title);}
 public boolean hudDestination(int destination){return CdLevelHud.isFontDestination(destination);}
 private int handler(int id){return fileAddress(assets.view(CdR11aAsset.OBJECT_INDEX).bytes().unsignedLong((id-1)*4));}
 private int init(int root,int skip,int routines)throws IOException{return init(image,root,skip,routines);}
 private static int init(CdMmdImage image,int root,int skip,int routines)throws IOException{
  require(image,root+skip,"700010280024303b00064efb0002");return routineTarget(image,root+skip+14,routines);
 }
 private static int routineTarget(CdMmdImage image,int table,int routines)throws IOException{
  int relative=(short)read(image,table,2).unsignedWord(0);if(relative!=routines*2)throw new IOException("Original startup routine table differs");return table+relative;
 }
 private CdObjectMappings.DeclaredTable mapping(int at,int frames)throws IOException{return mapping(image,at,frames);}
 private static CdObjectMappings.DeclaredTable mapping(CdMmdImage image,int at,int frames)throws IOException{
  var index=read(image,at,frames*2);int end=frames*2;
  for(int i=0;i<frames;i++){int offset=(short)index.unsignedWord(i*2);if(offset<frames*2||offset>4096)throw new IOException("Unadmitted startup mapping directory");int count=read(image,at+offset,1).unsignedByte(0);if(count<1||count>=128)throw new IOException("Unadmitted startup mapping count");end=Math.max(end,offset+1+count*5);}
  return new CdObjectMappings.DeclaredTable(0x200000+at,read(image,at,end),0,frames);
 }
 private int pcReset(CdAssetBytes code,int start,int count)throws IOException{
  int found=-1;for(int i=0;i<=code.length()-8;i++)if(code.unsignedWord(i)==0x45FA&&code.unsignedWord(i+4)==0x343C&&code.unsignedWord(i+6)==count){if(found!=-1)throw new IOException("Ambiguous HUD reset operand");found=start+i+2+(short)code.unsignedWord(i+2);}if(found<0)throw new IOException("Missing HUD reset operand");return found;
 }
 private int absoluteLea(CdAssetBytes code,int count)throws IOException{
  int found=-1;for(int i=0;i<=code.length()-8;i++)if(code.unsignedWord(i)==0x45F9&&code.unsignedWord(i+6)==(0x7C00|count)){if(found!=-1)throw new IOException("Ambiguous HUD divisor operand");found=fileAddress(code.unsignedLong(i+2));}if(found<0)throw new IOException("Missing HUD divisor operand");return found;
 }
 private int address(int at){return fileAddress(read(at,4).unsignedLong(0));}
 private int fileAddress(long address){return fileAddress(image,address);}
 private static int fileAddress(CdMmdImage image,long address){long at=address-0x200000L;if(at<0||at>=image.fileBytes())throw new IllegalArgumentException("Startup source pointer outside actual MMD");return(int)at;}
 private CdAssetBytes read(int at,int bytes){return read(image,at,bytes);}
 private static CdAssetBytes read(CdMmdImage image,int at,int bytes){return CdProgrammeReader.retainedSpan(image,at,bytes,"Original R11 startup operands");}
 private void require(int at,String hex)throws IOException{require(image,at,hex);}
 private static void require(CdMmdImage image,int at,String hex)throws IOException{if(!HexFormat.of().formatHex(read(image,at,hex.length()/2).copy()).equals(hex))throw new IOException("Original R11 startup operand mismatch at "+at);}
 private static int hudUpdate(int mapping,int bytes){return ((mapping+bytes+1)&~1)+72;}
 private static void requireHudDestinations(CdAssetBytes code)throws IOException{
for(String operand:new String[]{"203c70600002","203c73600002","203c72200002","203c72600002","203c72e00002","203c74a00002"})if(!HexFormat.of().formatHex(code.copy()).contains(operand))throw new IOException("Original HUD destination operand missing");
 }
 /** Same canonical retained image and shared original startup derivation; no aggregate or reader reopen. */
 private static CdAssetBytes standardObjectIndex(CdProgrammeReader actual,CdMmdImage image){
  // A retains its existing aggregate/component read contract and error order.
  if(actual.standardPpzProgramme()==CdPpzDataLayout.Programme.R11A)
   return CdR11aProfile.forStage(CdReleaseVariant.USA,CdStagePayload.R11A.stage()).range(CdR11aAsset.OBJECT_INDEX).read(image);
  return actual.view(CdPpzProgrammeProfiles.R11PastLoadingView.OBJECT_INDEX);
 }
 private static int standardShieldIndex(CdProgrammeReader actual){
  var key=CdPpzProgrammeProfiles.R11PastLoadingView.SHIELD_ART_INDEX;actual.view(key);
  return key.fileOffset();
 }
 static CdAssetBytes standardHudWriter(CdProgrammeReader actual)throws IOException{
  var image=Objects.requireNonNull(actual).retainedStandardPpzImage();
  var index=standardObjectIndex(actual,image);
  int root=fileAddress(image,index.unsignedLong((0x1C-1)*4));int start=init(image,root,8,2);
  require(image,start+4,"217c");require(image,start+10,"0004");int map=fileAddress(image,read(image,start+6,4).unsignedLong(0));
  var declared=mapping(image,map,4);int update=hudUpdate(map,declared.bytes().length());
  require(image,update,"4a79");require(image,update+8,"6100");var code=read(image,update,0x500);
  requireHudDestinations(code);return code;
 }
 record PowerupWindow(int destination,int bytes){
  PowerupWindow{if((destination&31)!=0||(bytes&31)!=0||bytes<32||destination<0||destination>65536-bytes)throw new IllegalArgumentException("Finite original powerup window");}
 }
 /** ObjPowerup_Init tile operand and LoadShieldArt's source-derived length, not supplied constants.
  * N1ecc Powerup/Main:21-27,157-186; LVLDMA declaration at Level/_Definitions:353-361.
  * These fixed source fields authenticate semantic geometry only; no device/register protocol runs. */
 static PowerupWindow standardPowerupWindow(CdProgrammeReader actual)throws IOException{
  var image=Objects.requireNonNull(actual).retainedStandardPpzImage();
  var index=standardObjectIndex(actual,image);
  int root=fileAddress(image,index.unsignedLong((3-1)*4));require(image,root,"700010280024323b00064efb1002");
  int start=routineTarget(image,root+14,4);
  var tile=read(image,start+30,6);if(tile.unsignedWord(0)!=0x317C||tile.unsignedWord(4)!=2)throw new IOException("Original powerup tile store differs");
  int destination=(tile.unsignedWord(2)&0x7FF)*32;
  int table=actual.standardPpzProgramme()==CdPpzDataLayout.Programme.R11A
      ?CdR11aProfile.forStage(CdReleaseVariant.USA,CdStagePayload.R11A.stage()).range(CdR11aAsset.SHIELD_ART_INDEX).fileOffset()
      :standardShieldIndex(actual);
  var source=read(image,table-38,38);int bytes=-1;
  for(int at=0;at<=source.length()-6;at++)if(source.unsignedWord(at)==0x2ABC){
   long field=source.unsignedLong(at+2);if((field&0xFF00FF00L)!=0x94009300L)continue;
   if(bytes!=-1)throw new IOException("Ambiguous original powerup length operand");
   bytes=(int)((((field>>>16)&255)<<8)|(field&255))*2;
  }
  if(bytes<0)throw new IOException("Missing original powerup length operand");return new PowerupWindow(destination,bytes);
 }

}
