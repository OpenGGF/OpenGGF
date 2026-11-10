package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.List;
import java.util.ArrayList;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aLoadingArtInputs;
import com.openggf.game.soniccd.level.CdLevelLoadingSource;
import com.openggf.game.soniccd.level.CdStagePlayerSource;

/**
 * Pure immutable sparse-known current pattern bytes at $6400..$6500, $6E00..$A400 and $D960..$DAE0.
 * There is one value at each owned physical address, regardless of which actor
 * or producer previously used it. The sole tile-read input is a final native
 * 11-bit pattern ID; SAT attributes must already have been removed by its owner.
 *
 * <p>Sources at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * PLC_Cam1_Full (src/Level/Palmtree Panic/Data (Act 1 Present).asm:85-100),
 * PLC_Cam2_Incr (:183-190), and their named Nemesis art headers. Mosqui70 at
 * $7400 contains Pata50 at $7600. Anton22 occupies $8120. Plant28 at $8480
 * and Ramp63 at $8820 surround one tile of unspecified ingress at $8800;
 * Tamabboh59 at $8500 overwrites portions of both and that intervening tile.
 * NemPCD_WriteRowToVDP (src/Level/Functions (General).asm:495-510) writes
 * four bytes per row; DecompPLC_Main (:752-758) completes eight rows per tile.
 * This owner exposes complete-tile commits, not mid-instruction device state.
 * PLC_Cam1_Full (:85-100) places Boulder19 at $6E80; PLC_Cam4_Full
 * (:138-153) and PLC_Cam4_Incr (:209-222) relocate Anton22 to $6E00,
 * overwriting eighteen Boulder tiles while retaining its final $70C0 tile.
 * These are physical aliases in one value, never separate producer banks.
 * PLC_Std (:54-57) owns Spikes8 at $6400; its $8008 header ends at $6500.
 * The intervening $6500..$6E00 gap remains unowned.
 * PLC_Level (Data (Act 1 Present).asm:47-52) places Checkpoint12 at $D960,
 * between the disjoint Points and Flower producers; its $800C header ends at $DAE0.</p>
 *
 * <p>Supplied bytes carry no asset authentication or cold/title history claim.
 * No decoder, PLC queue, section selection, clock, graphics, object bank, or
 * lifecycle is performed here. The nine disjoint regions own 452 physical
 * tiles, without certifying that their bytes are known. Both legacy factories
 * leave all six added regions UNKNOWN until explicit complete-tile writes.
 * Player $F000, powerup $A880 and Spring $A400 remain outside this half-open
 * envelope. Camera writes outside ownership stop at a typed boundary.
 * The caller retains each returned tile commit before attempting the next one;
 * this class neither batches away a reached boundary nor rolls back a prefix.</p>
 */
// The package-private backing shares one sparse value format; this public profile remains strict.
public final class CdR11aCameraArtResidency {
    private static final int TILE_BYTES = 32;

    private CdR11aCameraArtResidency() { }

    /**
     * Disjoint physical regions, not producer banks. All ends are exclusive.
     * The three historical names, ordinals and extents stay fixed; complements
     * are appended rather than ordered by address.
     */
    public enum Region {
        AT_7400(0x7400, 0x8C0),
        AT_8120(0x8120, 0x2C0),
        AT_8480(0x8480, 0xB80),
        AT_6E00(0x6E00, 0x600),
        AT_7CC0(0x7CC0, 0x460),
        AT_83E0(0x83E0, 0xA0),
        AT_9000(0x9000, 0x1400),
        AT_6400(0x6400, 0x100),
        AT_D960(0xD960, 0x180);

        private final int destinationWord;
        private final int byteLength;

        Region(int destinationWord, int byteLength) {
            this.destinationWord = destinationWord;
            this.byteLength = byteLength;
        }

        public int destinationWord() { return destinationWord; }
        public int byteLength() { return byteLength; }
        public int tileCount() { return byteLength / TILE_BYTES; }
    }

    public enum Boundary {
        UNKNOWN_PATTERN,
        OUTSIDE_OWNED_WINDOW
    }

    /** A valid native address has no owned or known tile; no substitute is returned. */
    public static final class OwnershipBoundary extends UnsupportedOperationException {
        private final Boundary boundary;
        private final int destinationWord;

        private OwnershipBoundary(Boundary boundary, int destinationWord) {
            super(boundary + " at native pattern $" + Integer.toHexString(destinationWord >>> 5)
                    + " (VRAM $" + Integer.toHexString(destinationWord) + ")");
            this.boundary = boundary;
            this.destinationWord = destinationWord;
        }

        public Boundary boundary() { return boundary; }
        public int nativePatternId() { return destinationWord >>> 5; }
        public int destinationWord() { return destinationWord; }
    }

    private static final CdPatternResidency.Profile PROFILE = new CdPatternResidency.Profile(
            Arrays.stream(Region.values()).map(r -> new CdPatternResidency.Span(
                    r.destinationWord, r.byteLength)).toArray(CdPatternResidency.Span[]::new));

    /**
     * Complete immutable owned value. An aggregate owner may retain this exact
     * value for capture/restore; no service, upload or other restore effect exists.
     * Equality and hash include bytes and byte-known masks at the fixed windows.
     */
    public static final class State {
        private final RegionSnapshot[] regions;
        private final CdR11aLoadingArtInputs loading;
        private final CdLevelLoadingSource loadingSource;
        private final List<Window> windows;
        private final CdPatternResidency.Value value;

        private State(RegionSnapshot[] regions) {
            this.loading=null;this.loadingSource=null;this.windows=legacyWindows();
            this.regions = regions.clone();
            var windows = new CdPatternResidency.Window[regions.length];
            for (int i = 0; i < regions.length; i++) windows[i] = regions[i].window;
            value = new CdPatternResidency.Value(PROFILE, windows);
        }

        /** Fresh explicit unknown profile; no pre-clear/title history is inferred. */
        private State(CdPatternResidency.Value value) {this(value,(CdR11aLoadingArtInputs)null,legacyWindows());}
        private State(CdPatternResidency.Value value,CdR11aLoadingArtInputs loading,List<Window> windows) {
            this.loading=loading;this.loadingSource=null;this.windows=windows;this.value=value;
            regions = new RegionSnapshot[Region.values().length];
            for (Region region : Region.values()) {
                regions[region.ordinal()] = new RegionSnapshot(region, value.window(region.ordinal()));
            }
        }
        private State(CdPatternResidency.Value value,CdLevelLoadingSource loadingSource,List<Window> windows) {
            this.loading=null;this.loadingSource=loadingSource;this.windows=windows;this.value=value;
            // This selected geometry has no R11 enum-region interpretation.
            regions=new RegionSnapshot[0];
        }

        private State(CdPatternResidency.Value value, State previous) {
            this.loading=previous.loading;this.loadingSource=previous.loadingSource;this.windows=previous.windows;this.value = value;
            var prior=previous.regions;
            regions = prior.clone();
            for (int i = 0; i < regions.length; i++) {
                if (prior[i].window != value.window(i)) {
                    regions[i] = new RegionSnapshot(prior[i].region, value.window(i));
                }
            }
        }

        public RegionSnapshot region(Region region) {
            if(loadingSource!=null)throw new IllegalStateException("Selected loading geometry uses windows, not R11 regions");
            return regions[Objects.requireNonNull(region, "region").ordinal()];
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof State state && loading==state.loading && loadingSource==state.loadingSource && value.equals(state.value);
        }

        @Override
        public int hashCode() { return loadingSource!=null?31*System.identityHashCode(loadingSource)+value.hashCode():loading==null?Arrays.hashCode(regions):31*System.identityHashCode(loading)+value.hashCode(); }
        public List<Window> windows(){return windows;}
        public boolean ownsLoadingSource(CdLevelLoadingSource actual) {
            return actual!=null&&(loadingSource==actual||loading!=null&&actual.ownsOriginalR11(loading.assets()));
        }
        /** Exact immutable scene source/player pair; unknown pixels retain their current mask. */
        public boolean ownsLoadingSource(CdLevelLoadingSource actual,CdStagePlayerSource player){
            return player!=null&&ownsLoadingSource(actual)&&actual.ownsPlayerSource(player);
        }
        public boolean ownsLoadingAssets(CdR11aAssets actual){return loading!=null&&loading.ownsAssets(actual)||loadingSource!=null&&loadingSource.ownsRetainedStandardAssets(actual);}
    }

    /**
     * Immutable diagnostic projection within State, with no standalone restore
     * operation. Unknown backing bytes are canonical zero internally and cannot
     * be read through this projection or the native tile reader. A mask is one
     * byte per region byte: exactly 0 for unknown, exactly 1 for supplied/written.
     */
    public static final class RegionSnapshot {
        private final Region region;
        private final CdPatternResidency.Window window;

        private RegionSnapshot(Region region, byte[] values, byte[] known) {
            this.region = Objects.requireNonNull(region, "region");
            window = new CdPatternResidency.Window(region.byteLength, values, known);
        }

        private RegionSnapshot(Region region, CdPatternResidency.Window window) {
            this.region = region;
            this.window = window;
        }

        public Region region() { return region; }

        /** Immutable flags only; this does not export unknown RAM values. */
        public CdAssetBytes knownMask() { return window.knownMask(); }

        /** Returns an unsigned supplied byte, or no value for unknown ingress. */
        public OptionalInt knownByte(int byteOffset) {
            return window.knownByte(byteOffset);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof RegionSnapshot snapshot
                    && region == snapshot.region && window.equals(snapshot.window);
        }

        @Override
        public int hashCode() {
            return window.hashWithTag(region);
        }
    }

    /**
     * All owned physical bytes are UNKNOWN. Canonical backing zero is not a pixel
     * or historical clear; the actual program owner must reach its source writer.
     */
    public static State unknown() { return new State(CdPatternResidency.unknown(PROFILE)); }

    public record Window(int destinationWord,int byteLength){public Window{new CdPatternResidency.Span(destinationWord,byteLength);}}
    private static List<Window> legacyWindows(){return Arrays.stream(Region.values()).map(r->new Window(r.destinationWord(),r.byteLength())).toList();}
    /** Full authenticated PLC destination union plus native LoadLifeIcon. All bytes UNKNOWN.
     * Appended complementary spans never broaden a legacy factory or duplicate existing bytes. */
    public static State unknownForLoading(CdR11aLoadingArtInputs actual){
        Objects.requireNonNull(actual);boolean[] owned=new boolean[2048];
        for(var r:Region.values())Arrays.fill(owned,r.destinationWord()/32,(r.destinationWord()+r.byteLength())/32,true);
        for(var list:actual.assets().loadPlan().plcs())for(var e:list.entries()){
            int count=actual.source(e.source()).tileCount();int start=e.vramAddress()/32;
            if(count<=0||start+count>2048)throw new IllegalArgumentException("Original R11 PLC pattern extent");
            Arrays.fill(owned,start,start+count,true);
        }
        Arrays.fill(owned,0xB420/32,0xB4A0/32,true);
        Arrays.fill(owned,CdPowerupArtRuntime.VRAM_BYTE_ADDRESS/32,(CdPowerupArtRuntime.VRAM_BYTE_ADDRESS+CdPowerupArtRuntime.WINDOW_BYTES)/32,true);
        try{var startup=actual.assets().startupObjects();for(int destination=0;destination<0x10000;destination+=32)if(startup.hudDestination(destination))owned[destination/32]=true;}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException("Original R11 HUD destination source",failure);}

        var geometry=unknownLegacyComplements(owned);
        return new State(geometry.value(),actual,geometry.windows());
    }

    private record UnknownGeometry(CdPatternResidency.Value value,List<Window> windows) { }
    /** One geometry algorithm for original aggregate and same-reader standard DATA. */
    private static UnknownGeometry unknownLegacyComplements(boolean[] owned){
        // Remove the existing regions before constructing disjoint complements.
        for(var r:Region.values())Arrays.fill(owned,r.destinationWord()/32,(r.destinationWord()+r.byteLength())/32,false);
        var windows=new ArrayList<>(legacyWindows());
        for(int i=0;i<owned.length;){if(!owned[i]){i++;continue;}int first=i;while(i<owned.length&&owned[i])i++;windows.add(new Window(first*32,(i-first)*32));}
        var fixed=List.copyOf(windows);var profile=new CdPatternResidency.Profile(fixed.stream().map(w->new CdPatternResidency.Span(w.destinationWord(),w.byteLength())).toArray(CdPatternResidency.Span[]::new));
        return new UnknownGeometry(CdPatternResidency.unknown(profile),fixed);
    }
    private static State unknownForStandard(CdLevelLoadingSource actual)throws java.io.IOException{
        actual.requireRetained();boolean[] owned=new boolean[2048];
        for(var r:Region.values())Arrays.fill(owned,r.destinationWord()/32,(r.destinationWord()+r.byteLength())/32,true);
        for(var list:actual.loadTables().plcs())for(var entry:list.entries()){
            int count=entry.outputBytes()/32,start=entry.vramAddress()/32;
            if((entry.vramAddress()&31)!=0||(entry.outputBytes()&31)!=0||count<=0||start+count>2048)
                throw new IllegalArgumentException("Original R11 PLC pattern extent");
            Arrays.fill(owned,start,start+count,true);
        }
        Arrays.fill(owned,0xB420/32,0xB4A0/32,true);
        for(int destination:actual.standardPowerupDestinations())owned[destination/32]=true;
        try{for(int destination:actual.standardHudDestinations())owned[destination/32]=true;}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException("Original R11 HUD destination source",failure);}
        var geometry=unknownLegacyComplements(owned);
        return new State(geometry.value(),actual,geometry.windows());
    }

    /** TT's actual PLC destination union and the source-authenticated native life-icon writer.
     * No inherited R11 region, unknown bytes/patterns remain unknown. This is DATA geometry,
     * never an actual original clear or a residency certificate. R11 delegates unchanged. */
    public static State unknownForLoading(CdLevelLoadingSource actual)throws java.io.IOException {
        Objects.requireNonNull(actual);
        if(actual.hasOriginalR11Aggregate())
            return unknownForLoading(actual.originalR11Assets().loadingArtInputs());
        if(actual.completion()==CdLevelLoadingSource.Completion.STANDARD){
            if(actual.file()!=com.openggf.game.soniccd.CdNativeFile.R11A&&actual.file()!=com.openggf.game.soniccd.CdNativeFile.R11B)throw new IllegalArgumentException("Original PPZ standard residency required");
            return unknownForStandard(actual);
        }
        boolean[] owned=new boolean[2048];
        for(var list:actual.loadTables().plcs())for(var entry:list.entries()) {
            int start=entry.vramAddress()/32,count=entry.outputBytes()/32;
            if((entry.vramAddress()&31)!=0||(entry.outputBytes()&31)!=0||count<=0||start+count>2048)
                throw new IllegalArgumentException("Original PLC pattern extent");
            Arrays.fill(owned,start,start+count,true);
        }
        // AnimateStageGfx R41A writes three 512-byte windows; ownership remains unknown
        // until the reached shared CPU-tile writer publishes each complete tile.
        if(actual.file()==com.openggf.game.soniccd.CdNativeFile.R41A)Arrays.fill(owned,0x4500/32,0x4B00/32,true);
        // Native LoadLifeIcon (r4/main.asm520-562) writes four tiles at B420.
        Arrays.fill(owned,0xB420/32,0xB4A0/32,true);
        // The actual source-authenticated shared HUD writer owns these destination tiles.
        // Ownership only: unknownForLoading still marks every byte UNKNOWN until its real write.
        actual.ordinaryObjectSource().ifPresent(objects->{
            for(int destination=0;destination<0x10000;destination+=32)
                if(objects.hudDestination(destination)||objects.powerupDestination(destination))owned[destination/32]=true;
        });
        var windows=new ArrayList<Window>();
        for(int i=0;i<owned.length;){if(!owned[i]){i++;continue;}int first=i;while(i<owned.length&&owned[i])i++;windows.add(new Window(first*32,(i-first)*32));}
        var fixed=List.copyOf(windows);
        var profile=new CdPatternResidency.Profile(fixed.stream().map(w->new CdPatternResidency.Span(w.destinationWord(),w.byteLength())).toArray(CdPatternResidency.Span[]::new));
        return new State(CdPatternResidency.unknown(profile),actual,fixed);
    }

    /** Pure current-value source adaptation, never a capture/entry or original clear.
     * Retains every supplied known byte and every unknown byte; adds only the selected
     * physical envelope. Module must supply its actual current value and own adoption. */
    public static State withLoadingSource(State current,CdLevelLoadingSource actual)throws java.io.IOException {
        Objects.requireNonNull(current);Objects.requireNonNull(actual);
        if(current.ownsLoadingSource(actual))return current;
        // Original R11's fixed enum windows keep their order and historical API.
        if(actual.hasOriginalR11Aggregate())
            throw new IllegalArgumentException("Original R11 retains its existing source-bound current value");
        var selected=unknownForLoading(actual);
        boolean[] owned=new boolean[2048];
        for(var windows:List.of(current.windows,selected.windows))for(var w:windows)
            Arrays.fill(owned,w.destinationWord()/32,(w.destinationWord()+w.byteLength())/32,true);
        var windows=new ArrayList<Window>();
        for(int i=0;i<owned.length;){if(!owned[i]){i++;continue;}int first=i;while(i<owned.length&&owned[i])i++;windows.add(new Window(first*32,(i-first)*32));}
        var fixed=List.copyOf(windows);var spans=new CdPatternResidency.Span[fixed.size()];
        var values=new CdPatternResidency.Window[fixed.size()];
        for(int i=0;i<fixed.size();i++) {
            var w=fixed.get(i);spans[i]=new CdPatternResidency.Span(w.destinationWord(),w.byteLength());
            byte[] bytes=new byte[w.byteLength()],known=new byte[w.byteLength()];
            for(int old=0;old<current.windows.size();old++) {
                var prior=current.windows.get(old);int begin=Math.max(w.destinationWord(),prior.destinationWord());
                int end=Math.min(w.destinationWord()+w.byteLength(),prior.destinationWord()+prior.byteLength());
                for(int at=begin;at<end;at++) {
                    var value=current.value.window(old).knownByte(at-prior.destinationWord());
                    if(value.isPresent()){int offset=at-w.destinationWord();bytes[offset]=(byte)value.getAsInt();known[offset]=1;}
                }
            }
            values[i]=new CdPatternResidency.Window(w.byteLength(),bytes,known);
        }
        return new State(new CdPatternResidency.Value(new CdPatternResidency.Profile(spans),values),actual,fixed);
    }

    /**
     * Explicit whole-profile zero clear, preserving this strict physical envelope.
     * This pure value operation touches no player art, planes, CPU buffers, decoder,
     * source selection or clock. The caller owns native invocation and adoption.
     */
    public static State clear(State before) {
        Objects.requireNonNull(before, "state");
        return new State(CdPatternResidency.clear(before.value), before);
    }

    /**
     * Explicit DEV source preload, not native cold entry or restart history.
     * Mosqui2240, Plant896 and Ramp2016 must be the caller's current exact bytes.
     * Anton's entire $2C0 window, the $8800 gap and all six added
     * regions remain UNKNOWN. No Boulder or other added art is implicitly seeded.
     */
    public static State devSeed(CdAssetBytes mosqui2240, CdAssetBytes plant896, CdAssetBytes ramp2016) {
        byte[] mosqui = exactBytes(mosqui2240, 2240, "mosqui2240");
        byte[] plant = exactBytes(plant896, 896, "plant896");
        byte[] ramp = exactBytes(ramp2016, 2016, "ramp2016");
        byte[] combined = new byte[0xB80];
        byte[] combinedKnown = new byte[0xB80];
        System.arraycopy(plant, 0, combined, 0, 896);
        System.arraycopy(ramp, 0, combined, 0x3A0, 2016);
        Arrays.fill(combinedKnown, 0, 0x380, (byte) 1);
        Arrays.fill(combinedKnown, 0x3A0, 0xB80, (byte) 1);
        return new State(new RegionSnapshot[]{
                new RegionSnapshot(Region.AT_7400, mosqui, allKnown(0x8C0)),
                new RegionSnapshot(Region.AT_8120, new byte[0x2C0], new byte[0x2C0]),
                new RegionSnapshot(Region.AT_8480, combined, combinedKnown),
                new RegionSnapshot(Region.AT_6E00, new byte[0x600], new byte[0x600]),
                new RegionSnapshot(Region.AT_7CC0, new byte[0x460], new byte[0x460]),
                new RegionSnapshot(Region.AT_83E0, new byte[0xA0], new byte[0xA0]),
                new RegionSnapshot(Region.AT_9000, new byte[0x1400], new byte[0x1400]),
                new RegionSnapshot(Region.AT_6400, new byte[0x100], new byte[0x100]),
                new RegionSnapshot(Region.AT_D960, new byte[0x180], new byte[0x180])
        });
    }

    /**
     * Explicit known ingress for the three historical windows only. All six
     * complementary regions remain UNKNOWN; this is not a certificate for all
     * 452 physical tiles. Its existence establishes no authenticity or native
     * entry provenance; no default zero/cold/restart factory is supplied.
     */
    public static State knownSeed(CdAssetBytes at7400, CdAssetBytes at8120, CdAssetBytes at8480) {
        byte[] first = exactBytes(at7400, 0x8C0, "at7400");
        byte[] second = exactBytes(at8120, 0x2C0, "at8120");
        byte[] third = exactBytes(at8480, 0xB80, "at8480");
        return new State(new RegionSnapshot[]{
                new RegionSnapshot(Region.AT_7400, first, allKnown(0x8C0)),
                new RegionSnapshot(Region.AT_8120, second, allKnown(0x2C0)),
                new RegionSnapshot(Region.AT_8480, third, allKnown(0xB80)),
                new RegionSnapshot(Region.AT_6E00, new byte[0x600], new byte[0x600]),
                new RegionSnapshot(Region.AT_7CC0, new byte[0x460], new byte[0x460]),
                new RegionSnapshot(Region.AT_83E0, new byte[0xA0], new byte[0xA0]),
                new RegionSnapshot(Region.AT_9000, new byte[0x1400], new byte[0x1400]),
                new RegionSnapshot(Region.AT_6400, new byte[0x100], new byte[0x100]),
                new RegionSnapshot(Region.AT_D960, new byte[0x180], new byte[0x180])
        });
    }

    /**
     * Commits exactly one decoded tile, marking its bytes known. Every other
     * byte and known flag is retained. The address is an unsigned native word
     * containing a VRAM byte destination, aligned to $20, with no wrapping.
     * A valid but unowned destination throws before returning any new state.
     */
    public static State writeTile(State state, int destinationWord, CdAssetBytes tile32) {
        Objects.requireNonNull(state, "state");
        try {
            return new State(CdPatternResidency.writeTile(state.value, destinationWord, tile32), state);
        } catch (CdPatternResidency.Fault fault) {
            throw new OwnershipBoundary(Boundary.valueOf(fault.boundary().name()), fault.destinationWord());
        }
    }

    /**
     * Resolves only the final 11-bit native pattern ID against this State's
     * actual current bytes. Unknown or outside-owned tiles never become pixels.
     */
    public static CdAssetBytes readTile(State state, int nativePatternId) {
        Objects.requireNonNull(state, "state");
        try {
            return CdPatternResidency.readTile(state.value, nativePatternId);
        } catch (CdPatternResidency.Fault fault) {
            throw new OwnershipBoundary(Boundary.valueOf(fault.boundary().name()), fault.destinationWord());
        }
    }

    private static byte[] exactBytes(CdAssetBytes bytes, int length, String name) {
        Objects.requireNonNull(bytes, name);
        if (bytes.length() != length) {
            throw new IllegalArgumentException(name + " must contain exactly " + length + " bytes");
        }
        return bytes.copy();
    }

    private static byte[] allKnown(int length) {
        byte[] known = new byte[length];
        Arrays.fill(known, (byte) 1);
        return known;
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
