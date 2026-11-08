package com.openggf.game.soniccd;
import com.openggf.game.soniccd.level.CdR31aAssets;import com.openggf.game.soniccd.objects.CdLevelGlobals;import java.util.*;
/** Immutable semantic Flower position slice, never addressable RAM or a second owner.
 * N1ecc5f3 Main CPU Variables:148-150, Main:178-184 and Flower/Main:132-144.
 * Unknown bytes have NO readable default. Only original private clear/store occurrences
 * can prepare knowledge; values/captures do not authorize adoption into the actual Core. */
final class CdFlowerPositions {
 static final int BYTES=0x300;
 enum Boundary { UNKNOWN_WORD,UNREPRESENTED_WORD,FOREIGN_SOURCE }
 static final class Unavailable extends UnsupportedOperationException {
  private final Boundary boundary;private final int offset;
  private Unavailable(Boundary b,int offset){super("Flower positions "+b+" at byte$"+Integer.toHexString(offset));this.boundary=b;this.offset=offset;}
  Boundary boundary(){return boundary;}int offset(){return offset;}
 }
 private final CdR31aAssets source;private final byte[] bytes;private final BitSet known;private final boolean cleared;
 private CdFlowerPositions(CdR31aAssets source,byte[] bytes,BitSet known,boolean cleared){this.source=source;this.bytes=bytes;this.known=known;this.cleared=cleared;}
 static CdFlowerPositions unknown(){return new CdFlowerPositions(null,null,new BitSet(BYTES),false);}
 static CdFlowerPositions originalClear(SonicCdR31aLoadingVisit.NativeClearCall original){var source=original.flowerPositionClearSource();var known=new BitSet(BYTES);known.set(0,BYTES);return new CdFlowerPositions(source,new byte[BYTES],known,true);}
 static CdFlowerPositions originalStore(CdFlowerPositions before,SonicCdR31aObjectPass.FlowerPositionStore original){
  var source=original.requirePositionSource(before);before.requireSource(source);int offset=original.offset(),value=original.value();represented(offset);wordValue(value);
  byte[] bytes=before.bytes==null?new byte[BYTES]:before.bytes.clone();var known=(BitSet)before.known.clone();bytes[offset]=(byte)(value>>>8);bytes[offset+1]=(byte)value;known.set(offset,offset+2);return new CdFlowerPositions(source,bytes,known,before.cleared);
 }
 int word(int offset){represented(offset);if(!known.get(offset)||!known.get(offset+1))throw new Unavailable(Boundary.UNKNOWN_WORD,offset);return(Byte.toUnsignedInt(bytes[offset])<<8)|Byte.toUnsignedInt(bytes[offset+1]);}
 int knownBytes(){return known.cardinality();}boolean clearedByOriginal(){return cleared;}
 void requireSource(CdR31aAssets source){if(this.source!=null&&this.source!=source)throw new Unavailable(Boundary.FOREIGN_SOURCE,0);}
 private static void represented(int offset){if(offset<0||offset>BYTES-2||(offset&1)!=0)throw new Unavailable(Boundary.UNREPRESENTED_WORD,offset);}
 private static void wordValue(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Native Flower WORD");}
 /** Pure projection of ONLY the already owned adjacent count/fade aliases; no invocation authority. */
 static CdLevelGlobals aliasWord(CdLevelGlobals l,int offset,int value){wordValue(value);int count=l.flowerCount(),fade=l.fadeEnableDisplay();
  if(offset==0x300)count=(value<<8)|(count&255);else if(offset==0x302){count=(count&0xFFFF00)|(value>>>8);fade=value&255;}else throw new Unavailable(Boundary.UNREPRESENTED_WORD,offset);
  return new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),count,fade,l.powerup(),l.timeOver());
 }
 @Override public boolean equals(Object o){return this==o||o instanceof CdFlowerPositions p&&source==p.source&&cleared==p.cleared&&known.equals(p.known)&&Arrays.equals(bytes,p.bytes);}
 @Override public int hashCode(){return Objects.hash(System.identityHashCode(source),cleared,known,Arrays.hashCode(bytes));}
 @Override public String toString(){return "FlowerPositions[known="+knownBytes()+",originalClear="+cleared+",valueHash="+hashCode()+"]";}
}
