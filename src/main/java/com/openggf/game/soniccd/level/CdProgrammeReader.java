package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.io.IOException;
import java.util.*;
/** Original-USA finite source custody; DATA only, never a live publication or admission token. */
final class CdProgrammeReader {
 private record Span(int offset,int length,String sha) { }
 private record Compressed(int offset,int include,int javaBytes,int header,int table,int consumed,
                           int output,int capacity,String includeSha,String javaSha,String outputSha) { }
 private record NativeSpan(int length,String sha) { }
 enum Borrow { MUSIC_CONSUMER,MUSIC_COMMANDS,SCROLL_CONSUMER,COMMON_EVENTS,ZONE_DIRECTORY,
               CCZ_DISPATCH,ACT_DIRECTORY,CCZ12,HUD_UPDATE,HUD_RESET,HUD_DIVISORS,HUD_VINT }
 enum Blocks { R31 }
 private final CdStagePayload payload;
 private final CdMmdImage image;
 private final String discSha, mmdSha;
 private final Map<Enum<?>,Span> spans=new LinkedHashMap<>();
 private final Map<Enum<?>,Span> borrowedSpans=new HashMap<>();
 private final Map<Enum<?>,CdAssetBytes> views=new HashMap<>();
 private final Map<Enum<?>,Compressed> compressed=new LinkedHashMap<>();
 private final Map<Enum<?>,NativeSpan> nativeSpans=new HashMap<>();
 private final Map<Enum<?>,CdAssetBytes[]> inputs=new HashMap<>();
 private final Map<Enum<?>,CdNemesisAsset.Decoded> products=new HashMap<>();
 private final CdSonicVisualAssets sonic;
 private CdStageTerrain terrain;
 private CdProgrammeReader(CdStagePayload payload,CdMmdImage image,String discSha)throws IOException {
  this.payload=payload;this.image=image;this.discSha=discSha;
  mmdSha=payload==CdStagePayload.R31A?"30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e":CdR32aDataLayout.MMD_SHA256;
  verifyImage();
  if(payload==CdStagePayload.R31A) {
   operand(0x15E6,"45f90020fac0");
   operand(0x3580,"45f90020fac0");
   operand(0x35FC,"43f90021f9bc");
   operand(0x2450,"43f90020fad0");
   operand(0x39A2,"43f900203db4");
   operand(0x283A,"41f90020287c");
   operand(0x28C8,"43f900202940");
   operand(0x2934,"43f900202944");
   operand(0x3FD6,"217c0022df200004");
   operand(0x5B62,"45f90022e8ac");
   operand(0x5B92,"43f900220000");
   operand(0x561A,"43f90020590a");
   operand(0x71CA,"45f90020754a");
   operand(0x2BE8,"47f900202c1249f90023a746");
   operand(0xFAC0,"03237bee02236c760021000000810404");
   operand(0x6838,"d241323b101c43fb10187c003c197200b0711000677e544151cefff6");
   operand(0x68E8,"303b10384ebb0034");
   operand(0x6ABC,"044100126b2c303b106a43fb00660242000f0243000f");
   operand(0x15E4,"700045f90020fac070001012670461000fca700161000fc4");
   operand(0x3580,"45f90020fac02f0a588a221a0281003fffff204149f900ff20006100ed82");
   operand(0x35B4,"4a3900ff152267064ef90020bef60839000100ff151c670a7000101267046100ee784e75");
   operand(0x1AD6,"203c742000027400143900ff152e08820007ef4a2d40000443f900232f3c47f12000");
   operand(0xBEF6,"43fa0050700072003038f700b0596504540160f813c100ff1574303b10464ef90020244c");
   operand(0xBF1A,"43fa002c700072003038f700b0596504540160f8b23900ff157466024e7513c100ff1574303b10104ef90020247e");
   for(var a:CdR31aAssets.Asset.values())spans.put(a,new Span(a.offset,a.length,a.sha));
  } else {
   for(var o:CdR32aDataLayout.OPERANDS)operand(o.fileOffset(),o.hexBytes());
   for(var a:CdR32aDataLayout.Asset.values())spans.put(a,new Span(a.fileOffset(),a.length(),a.sha256()));
  }
  // Preserve ordered eager authentication before visual/product construction.
  for(var e:spans.entrySet()) {
   var s=e.getValue();var bytes=read(s.offset,s.length);
   if(!s.sha.equals(CdR11aProfile.sha256(bytes.copy())))throw new IOException(name()+" original source span differs: "+e.getKey());
   views.put(e.getKey(),bytes);
  }
  if(payload==CdStagePayload.R31A) {
   for(var id:CdR31aLoadMetadata.SourceId.values()) {
    var s=new CdR31aLoadMetadata.Source(id);
    compressed.put(id,new Compressed(s.fileOffset(),s.includeBytes(),s.javaReaderBytes(),s.headerWord(),s.codeTableBytes(),s.expectedJavaConsumedBytes(),s.outputBytes(),65536,s.includeSha256(),s.javaReaderSha256(),s.decodedSha256()));
   }
   // Separate legacy blocks contract: header comes from its already authenticated raw span; no new prefetch/table claim.
   compressed.put(Blocks.R31,new Compressed(0x36C76,3960,3960,view(CdR31aAssets.Asset.LEVEL_BLOCKS).unsignedWord(0),0,3959,5248,0x2000,
    "51c6a77d51fc3817ff0935c4095cd2ad6c3e809e9348507299b03f313dff09a3","51c6a77d51fc3817ff0935c4095cd2ad6c3e809e9348507299b03f313dff09a3","108e1dc9e6fb4c14578ca3073fbd9099f9fe5028d10fed98dad102ce56076964"));
   for(var line:CdR31aLoadPlan.NATIVE_READERS.strip().lines().toList()) {
    var w=line.split(" ");var id=CdR31aLoadMetadata.SourceId.valueOf(w[0]);
    if(id.ordinal()!=nativeSpans.size())throw new IllegalArgumentException("R31A native source order differs");
    var profile=new CdR31aLoadPlan.NativeReader(new CdR31aLoadMetadata.Source(id),Integer.parseInt(w[1]),Integer.parseInt(w[2]),Integer.parseInt(w[3]),w[4]);
    nativeSpans.put(id,new NativeSpan(profile.byteLength(),profile.sha256()));
   }
   borrowedSpans.put(Borrow.MUSIC_CONSUMER,new Span(0x1A76,58,null));
   borrowedSpans.put(Borrow.MUSIC_COMMANDS,new Span(0x1AB0,28,null));
   borrowedSpans.put(Borrow.SCROLL_CONSUMER,new Span(0x299C,40,null));
   borrowedSpans.put(Borrow.COMMON_EVENTS,new Span(0x3626,122,null));
   borrowedSpans.put(Borrow.ZONE_DIRECTORY,new Span(0x36A0,14,null));
   borrowedSpans.put(Borrow.CCZ_DISPATCH,new Span(0x3748,18,null));
   borrowedSpans.put(Borrow.ACT_DIRECTORY,new Span(0x375A,6,null));
   borrowedSpans.put(Borrow.CCZ12,new Span(0x3760,8,null));
   borrowedSpans.put(Borrow.HUD_UPDATE,new Span(0xA940,1118,null));
   borrowedSpans.put(Borrow.HUD_RESET,new Span(0xAB86,12,null));
   borrowedSpans.put(Borrow.HUD_DIVISORS,new Span(0xACE6,40,null));
   borrowedSpans.put(Borrow.HUD_VINT,new Span(0x1DFA,6,null));
  } else {
   for(var s:CdR32aLoadLayout.SourceId.values())compressed.put(s,new Compressed(s.fileOffset(),s.includeBytes(),s.readerBytes(),s.headerWord(),s.codeTableBytes(),s.consumedInputBytes(),s.outputBytes(),s==CdR32aLoadLayout.SourceId.STAGE_BLOCKS?0x2000:65536,s.includeSha256(),s.readerSha256(),s.outputSha256()));
  }
  for(var s:compressed.values())if(s.offset<0 || s.include<2 || s.javaBytes<s.include || s.javaBytes>s.include+1
   || s.javaBytes>image.fileBytes()-s.offset || s.consumed>s.javaBytes || s.output<0 || s.output>s.capacity
   || (s.header&0x7fff)*32!=s.output || s.table<0 || s.table>s.consumed)throw new IllegalArgumentException(name()+" fixed source extent differs");
  sonic=CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_167,role(Role.MAP).copy(),role(Role.DPLC).copy(),role(Role.ART).copy());
 }
 static CdProgrammeReader open(CdProgramAssets actual,CdStageKey selected)throws IOException {
  Objects.requireNonNull(actual,"actual complete-disc data source");Objects.requireNonNull(selected,"explicit selected stage");
  final CdStagePayload payload;
  if(selected.equals(CdStagePayload.R31A.stage()))payload=CdStagePayload.R31A;
  else if(selected.equals(CdStagePayload.R32A.stage()))payload=CdStagePayload.R32A;
  else throw new UnsupportedOperationException("Only original USA R31A/R32A programme data reviewed");
  String n=payload.name();
  if(actual.header().observedVariant().orElse(null)!=CdReleaseVariant.USA)throw new IOException(n+" requires original USA media");
  var release=CdRetailReleases.find(actual.identity()).orElseThrow(()->new IOException(n+" requires a known complete-disc content profile"));
  if(release.variant()!=CdReleaseVariant.USA)throw new IOException(n+" source release differs");
  var catalogue=actual.catalogue().orElseThrow(()->new IOException(n+" source catalogue unavailable"));
  if(catalogue.release()!=CdReleaseVariant.USA || catalogue.stage(selected)!=payload || payload.file()!=(payload==CdStagePayload.R31A?CdNativeFile.R31A:CdNativeFile.R32A)
   || !payload.stage().equals(selected))throw new IOException(n+" catalogue stage/file/release differs");
  return new CdProgrammeReader(payload,actual.mmd(payload.file()),actual.identity().normalizedSha256());
 }
 private String name(){return payload.name();}
 private CdAssetBytes read(int offset,int length) {
  if(offset<0 || length<0 || offset>image.fileBytes() || length>image.fileBytes()-offset)throw new IllegalArgumentException(name()+" finite original span");
  return CdAssetBytes.copyOf(image.readFile(offset,length));
 }
 private void verifyImage()throws IOException {
  Objects.requireNonNull(image,"actual selected original MMD");
  if(image.fileBytes()!=262144 || !CdR32aDataLayout.HEADER.equals(image.header()) || image.fileBackedCopyBytes()!=0 || image.unresolvedCopyBytes()!=0
   || !mmdSha.equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))throw new IOException("Original USA "+name()+" image/header differs");
 }
 private void operand(int offset,String hex)throws IOException {
  if(!HexFormat.of().formatHex(read(offset,hex.length()/2).copy()).equals(hex))throw new IOException(name()+" original selected operand differs at "+Integer.toHexString(offset));
 }
 synchronized CdAssetBytes view(Enum<?> key) {
  var b=views.get(Objects.requireNonNull(key));
  if(b==null && borrowedSpans.containsKey(key)){var fixed=borrowedSpans.get(key);b=read(fixed.offset,fixed.length);views.put(key,b);}
  if(b==null)throw new IllegalArgumentException(name()+" foreign fixed source key: "+key);return b;
 }
 private Compressed source(Enum<?> key) {
  var s=compressed.get(Objects.requireNonNull(key));if(s==null)throw new IllegalArgumentException(name()+" foreign compressed source key: "+key);return s;
 }
 synchronized CdAssetBytes canonicalInput(Enum<?> key){return input(key,0);}
 synchronized CdAssetBytes javaInput(Enum<?> key){return input(key,1);}
 synchronized CdAssetBytes nativeInput(Enum<?> key){return input(key,2);}
 private CdAssetBytes input(Enum<?> key,int kind) {
  var s=source(key);var n=nativeSpans.get(key);
  if(kind==2 && n==null)throw new UnsupportedOperationException(name()+" native prefetch profile unavailable: "+key);
  var cache=inputs.computeIfAbsent(key,k->new CdAssetBytes[3]);if(cache[kind]!=null)return cache[kind];
  int length=kind==0?s.include:kind==1?s.javaBytes:n.length;
  String sha=kind==0?s.includeSha:kind==1?s.javaSha:n.sha;
  CdAssetBytes b=null;
  // Same reviewed extent aliases only after independently checking its expected hash/header.
  for(var existing:cache)if(existing!=null && existing.length()==length){b=existing;break;}
  if(b==null)b=read(s.offset,length);
  if(b.unsignedWord(0)!=s.header || !sha.equals(CdR11aProfile.sha256(b.copy())))throw new IllegalArgumentException(name()+" original bounded compressed source differs");
  cache[kind]=b;return b;
 }
 synchronized CdNemesisAsset.Decoded decoded(Enum<?> key)throws IOException {
  var s=source(key);var cached=products.get(key);if(cached!=null)return cached;
  var result=CdNemesisAsset.decode(javaInput(key).copy(),s.output,s.capacity);
  if(result.consumedInputBytes()!=s.consumed || !s.outputSha.equals(CdR11aProfile.sha256(result.bytes().copy())))throw new IOException(name()+" existing Java source product/cursor differs");
  products.put(key,result);return result;
 }
 CdSonicVisualAssets sonic(){return sonic;}
 void requireRetained(){try{verifyImage();}catch(IOException fault){throw new IllegalStateException("Retained "+name()+" source differs",fault);}}
 CdStageKey stage(){return payload.stage();}
 CdNativeFile file(){return payload.file();}
 String mmdSha(){return mmdSha;}
 String discSha(){return discSha;}
 int ordinaryZoneActWord(){return payload==CdStagePayload.R31A?0x100:0x101;}
 CdStageTerrain.Shape terrainShape(){return payload==CdStagePayload.R31A?CdStageTerrain.Shape.CCZ1_PRESENT:CdStageTerrain.Shape.CCZ2_PRESENT;}
 enum Role { ANI, MINI, SPECIAL, SINE, ATAN, MAP, DPLC, ART, CHUNKS, FG, BG, COLLISION, ANGLE, HEIGHT, WIDTH, PLACEMENTS }
 CdAssetBytes role(Role role) {
  Enum<?> key=payload==CdStagePayload.R31A?R31_ROLES[role.ordinal()]:R32_ROLES[role.ordinal()];return view(key);
 }
 private static final Enum<?>[] R31_ROLES={CdR31aAssets.Asset.ANI_SONIC,CdR31aAssets.Asset.MINI_ANIMATIONS,CdR31aAssets.Asset.SPEC_CHUNKS,CdR31aAssets.Asset.SINE_TABLE,CdR31aAssets.Asset.ARC_TAN_TABLE,CdR31aAssets.Asset.MAP_SPR_SONIC,CdR31aAssets.Asset.DPLC_SONIC,CdR31aAssets.Asset.ART_SONIC,CdR31aAssets.Asset.LEVEL_CHUNKS,CdR31aAssets.Asset.LAYOUT_FG,CdR31aAssets.Asset.LAYOUT_BG,CdR31aAssets.Asset.LEVEL_COLLISION,CdR31aAssets.Asset.COL_ANGLE_MAP,CdR31aAssets.Asset.COL_HEIGHT_MAP,CdR31aAssets.Asset.COL_WIDTH_MAP,CdR31aAssets.Asset.OBJECT_LAYOUTS};
 private static final Enum<?>[] R32_ROLES={CdR32aDataLayout.Asset.ANI_SONIC,CdR32aDataLayout.Asset.MINI_ANIMATIONS,CdR32aDataLayout.Asset.SPEC_CHUNKS,CdR32aDataLayout.Asset.SINE_TABLE,CdR32aDataLayout.Asset.ARC_TAN_TABLE,CdR32aDataLayout.Asset.MAP_SPR_SONIC,CdR32aDataLayout.Asset.DPLC_SONIC,CdR32aDataLayout.Asset.ART_SONIC,CdR32aDataLayout.Asset.LEVEL_CHUNKS,CdR32aDataLayout.Asset.LAYOUT_FG,CdR32aDataLayout.Asset.LAYOUT_BG,CdR32aDataLayout.Asset.LEVEL_COLLISION,CdR32aDataLayout.Asset.COL_ANGLE_MAP,CdR32aDataLayout.Asset.COL_HEIGHT_MAP,CdR32aDataLayout.Asset.COL_WIDTH_MAP,CdR32aDataLayout.Asset.OBJECT_LAYOUTS};
 synchronized CdStageTerrain terrain()throws IOException {
  if(terrain==null)terrain=CdStageTerrain.decode(terrainShape(),decoded(payload==CdStagePayload.R31A?Blocks.R31:CdR32aLoadLayout.SourceId.STAGE_BLOCKS).bytes().copy(),
   decoded(payload==CdStagePayload.R31A?CdR31aLoadMetadata.SourceId.STAGE_GFX:CdR32aLoadLayout.SourceId.STAGE_GFX).bytes().copy(),
   role(Role.CHUNKS).copy(),role(Role.FG).copy(),role(Role.BG).copy(),role(Role.COLLISION).copy(),role(Role.ANGLE).copy(),role(Role.HEIGHT).copy(),role(Role.WIDTH).copy());
  return terrain;
 }
}
