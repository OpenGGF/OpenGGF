package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.level.CdR13SectionSources;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/** Typed immutable values over CdPatternResidency. All initial bytes are UNKNOWN.
 * Windows are exactly the union of actual17-ID PLC outputs plus LoadLifeIcon's128-byte writer.
 * This contains no source bank, GPU allocation, clear event, clock or registered capture owner.
 */
public final class CdR13ArtResidency {
    public enum Region {
        LEVEL(0,0x6280,0x65C0), OBJECTS(0x6B20,0x2280), CAPSULE(0x9020,0xD00),
        BOULDER(0x9FC0,0x260), SPRINGS(0xA400,0x480), HUD(0xAD00,0x360),
        LIFE_ICON(0xB420,0x80), MONITORS(0xB500,0xA40), EXPLOSIONS(0xD000,0x960),
        POINTS_FLOWER(0xDAE0,0x4E0), RINGS(0xF5C0,0x1C0);
        private final int destination,goodLength,badLength;
        Region(int destination,int length){this(destination,length,length);}
        Region(int destination,int goodLength,int badLength){this.destination=destination;this.goodLength=goodLength;this.badLength=badLength;}
        public int destinationWord(){return destination;}
        private int length(CdNativeFile file){if(file==CdNativeFile.R13C)return goodLength;if(file==CdNativeFile.R13D)return badLength;throw new IllegalArgumentException("Only original R13C/D profile");}
    }
    public enum Boundary { UNKNOWN_PATTERN, OUTSIDE_OWNED_WINDOW }
    public static final class OwnershipBoundary extends UnsupportedOperationException {
        private final Boundary boundary;private final int destination;
        private OwnershipBoundary(CdPatternResidency.Fault fault){super(fault.getMessage(),fault);boundary=Boundary.valueOf(fault.boundary().name());destination=fault.destinationWord();}
        public Boundary boundary(){return boundary;}public int destinationWord(){return destination;}public int nativePatternId(){return destination>>>5;}
    }
    public static final class RegionSnapshot {
        private final Region region;private final CdPatternResidency.Window window;
        private RegionSnapshot(Region region,CdPatternResidency.Window window){this.region=region;this.window=window;}
        public Region region(){return region;}public int byteLength(){return window.length();}
        public CdAssetBytes knownMask(){return window.knownMask();}public OptionalInt knownByte(int offset){return window.knownByte(offset);}
        @Override public boolean equals(Object other){return this==other || other instanceof RegionSnapshot s && region==s.region && window.equals(s.window);}
        @Override public int hashCode(){return window.hashWithTag(region);}
    }
    public static final class State {
        private final CdR13FutureAssets assets;private final CdPatternResidency.Value value;private final RegionSnapshot[] regions;
        private State(CdR13FutureAssets assets,CdPatternResidency.Value value,State before){this.assets=assets;this.value=value;regions=new RegionSnapshot[Region.values().length];for(var r:Region.values()){int i=r.ordinal();var window=value.window(i);regions[i]=before!=null && before.regions[i].window==window?before.regions[i]:new RegionSnapshot(r,window);}}
        public boolean ownsAssets(CdR13FutureAssets assets){return this.assets==assets;}
        public RegionSnapshot region(Region region){return regions[Objects.requireNonNull(region).ordinal()];}
        @Override public boolean equals(Object other){return this==other || other instanceof State s && assets==s.assets && Arrays.equals(regions,s.regions);}
        @Override public int hashCode(){return 31*System.identityHashCode(assets)+Arrays.hashCode(regions);}
    }
    private CdR13ArtResidency(){}
    public static State unknown(CdR13FutureAssets assets){
        // This existing finite source binding authenticates the exact original C/D owner; it creates no known bytes.
        var source=CdR13SectionSources.fromAssets(Objects.requireNonNull(assets));
        var regions=Region.values();var spans=new CdPatternResidency.Span[regions.length];for(var r:regions)spans[r.ordinal()]=new CdPatternResidency.Span(r.destinationWord(),r.length(source.identity().file()));
        return new State(assets,CdPatternResidency.unknown(new CdPatternResidency.Profile(spans)),null);
    }
    /** Explicit pure value transform. Caller owns any actual native clear. */
    public static State clear(State state){Objects.requireNonNull(state);return new State(state.assets,CdPatternResidency.clear(state.value),state);}
    public static State withTile(State state,int destinationWord,CdAssetBytes tile32){Objects.requireNonNull(state);try{return new State(state.assets,CdPatternResidency.writeTile(state.value,destinationWord,tile32),state);}catch(CdPatternResidency.Fault fault){throw new OwnershipBoundary(fault);}}
    public static CdAssetBytes readTile(State state,int nativePatternId){Objects.requireNonNull(state);try{return CdPatternResidency.readTile(state.value,nativePatternId);}catch(CdPatternResidency.Fault fault){throw new OwnershipBoundary(fault);}}
}
