package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.io.IOException;
import java.util.*;
/** Actual original-USA retained source views for the existing shared queued/immediate kernels.
 * A source include and a finite prefetch envelope are distinct. Up to two REAL adjacent bytes
 * are available (one source WORD); EOF is retained, never padded. This is not a measured cursor
 * or a claim that every available byte is consumed. All algorithm reads remain fallible.
 * N1ecc5f3a Functions (General).asm NemDecMain:401-490; source authority is the whole MMD hash. */
public final class CdR11aLoadingArtInputs {
 public record Source(CdR11aAsset asset,CdR11aProfile.FileRange include,CdAssetBytes readerBytes,int tileCount){}
 private final CdR11aAssets assets;private final List<Source> sources;private final Source blocks;
 private final CdAssetBytes lifeIcon,music;private final int musicAddress;
 public static final class MusicReadBoundary extends UnsupportedOperationException{
  private final int address,index;private MusicReadBoundary(int address,int index){super("Unowned original R11 music byte at $"+Integer.toHexString(address));this.address=address;this.index=index;}
  public int address(){return address;}public int index(){return index;}
 }
 private CdR11aLoadingArtInputs(CdR11aAssets assets,CdMmdImage image)throws IOException{
  this.assets=Objects.requireNonNull(assets);var profile=assets.profile();var id=assets.identity();
  if(id.variant()!=CdReleaseVariant.USA||id.file()!=CdNativeFile.R11A||id.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM)throw new IllegalArgumentException("Original USA R11A loading inputs required");
  profile.verifyProgram(id.file(),id.domain(),image);
  var keys=new LinkedHashSet<CdR11aAsset>();for(var list:assets.loadPlan().plcs())for(var e:list.entries())keys.add(e.source());
  var inputs=new ArrayList<Source>();for(var key:keys)inputs.add(source(profile,image,key));sources=List.copyOf(inputs);
  blocks=source(profile,image,CdR11aAsset.LEVEL_BLOCKS);var life=profile.range(CdR11aAsset.ART_LIFE_ICON);
  lifeIcon=CdProgrammeReader.retainedSpan(image,life.fileOffset(),life.length(),"R11A life icon");
  int musicOffset=profile.range(CdR11aAsset.LEVEL_COL_INDEX).fileOffset()+32+148+58;
  musicAddress=0x200000+musicOffset;
  var consumer=CdProgrammeReader.retainedSpan(image,musicOffset-58,52,"R11A original music consumer");
  if(!HexFormat.of().formatHex(consumer.copy()).equals("70007200103900ff152e088000074a3900ff0f01660c0c0000026606d03900ff156a123900ff1506d241d241d2407000103b1008"))throw new IOException("Original R11 music selector differs");
  music=CdProgrammeReader.retainedSpan(image,musicOffset,28,"R11A original music table");
  if(!CdR11aProfile.sha256(music.copy()).equals("6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932"))throw new IOException("Original R11 music table differs");
 }
 static CdR11aLoadingArtInputs bind(CdR11aAssets assets,CdMmdImage image)throws IOException{return new CdR11aLoadingArtInputs(assets,image);}
 private static Source source(CdR11aProfile profile,CdMmdImage image,CdR11aAsset key){
  var include=profile.range(key);if(key.encodingForReviewedConsumer()!=CdR11aAsset.Encoding.NEMESIS)throw new IllegalArgumentException("Unreviewed R11A Nemesis source "+key);
  int length=Math.min(include.length()+2,image.fileBytes()-include.fileOffset());
  var bytes=CdProgrammeReader.retainedSpan(image,include.fileOffset(),length,"R11A native art input");
  return new Source(key,include,bytes,bytes.unsignedWord(0)&0x7FFF);
 }
 public CdR11aAssets assets(){return assets;}public boolean ownsAssets(CdR11aAssets actual){return actual==assets;}
 public List<Source> sources(){return sources;}public Source blocks(){return blocks;}public CdAssetBytes lifeIcon(){return lifeIcon;}
 public CdCommand selectLevelMusic(int zoneAct,int rawPeriod,int timeAttack,int goodFuture){
  int index=CdLevelMusicIndex.select(zoneAct,rawPeriod,timeAttack,goodFuture);
  if(index>=music.length())throw new MusicReadBoundary(musicAddress+index,index);
  return CdCommand.fromId(music.unsignedByte(index));
 }
 public Source source(CdR11aAsset key){return sources.stream().filter(x->x.asset()==key).findFirst().orElseThrow(()->new IllegalArgumentException("Unregistered R11 loading source"));}
}
