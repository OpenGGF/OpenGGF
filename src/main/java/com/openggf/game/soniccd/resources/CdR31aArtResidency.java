package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.level.*;
import java.util.*;

/** Immutable sparse R31A pattern values over the existing shared format.
 * Windows are exactly the authenticated19-ID PLC union plus LoadLifeIcon's128 bytes.
 * Unknown creation is not an InitVDP clear, source bank, service, GPU or capture owner.
 */
public final class CdR31aArtResidency {
    public record Window(int destinationWord,int byteLength) {
        public Window {new CdPatternResidency.Span(destinationWord,byteLength);}
    }
    public enum Boundary { UNKNOWN_PATTERN, OUTSIDE_OWNED_WINDOW }
    public static final class OwnershipBoundary extends UnsupportedOperationException {
        private final Boundary boundary;private final int destination;
        private OwnershipBoundary(CdPatternResidency.Fault fault){super(fault.getMessage(),fault);boundary=Boundary.valueOf(fault.boundary().name());destination=fault.destinationWord();}
        public Boundary boundary(){return boundary;}public int destinationWord(){return destination;}public int nativePatternId(){return destination>>>5;}
    }
    public static final class State {
        private final CdR31aAssets assets;private final List<Window> windows;private final CdPatternResidency.Value value;
        private State(CdR31aAssets assets,List<Window> windows,CdPatternResidency.Value value){this.assets=assets;this.windows=windows;this.value=value;}
        public boolean ownsAssets(CdR31aAssets assets){return this.assets==assets;}
        public List<Window> windows(){return windows;}
        // BEGIN R31 native-HUD profile predicate 20261107.
        /** Comparison-only profile shape, never writer or invocation authority. */
        public boolean hasNativeHudProfile(){return covers(0xB060,0xB420)&&covers(0xB4A0,0xB4E0);}
        private boolean covers(int start,int end){for(var w:windows)if(w.destinationWord()<=start&&w.destinationWord()+w.byteLength()>=end)return true;return false;}
        // END R31 native-HUD profile predicate 20261107.
        public OptionalInt knownByte(int destinationWord){for(int i=0;i<windows.size();i++){var w=windows.get(i);if(destinationWord>=w.destinationWord()&&destinationWord<w.destinationWord()+w.byteLength())return value.window(i).knownByte(destinationWord-w.destinationWord());}throw new IllegalArgumentException("Outside R31A pattern windows");}
        @Override public boolean equals(Object other){return this==other||other instanceof State s&&assets==s.assets&&windows.equals(s.windows)&&value.equals(s.value);}
        @Override public int hashCode(){return 31*System.identityHashCode(assets)+value.hashCode();}
    }
    private CdR31aArtResidency(){}
    /** Bound actual owner, all bytes UNKNOWN; no generalized full64K pattern admission. */
    public static State unknown(CdR31aAssets assets){
        var plan=CdR31aLoadPlan.fromAssets(Objects.requireNonNull(assets));var rows=new ArrayList<Window>();
        for(var list:plan.plcs())for(var e:list.entries())rows.add(new Window(e.vramAddress(),plan.sources().source(e.source()).outputBytes()));
        rows.add(new Window(0xB420,128));rows.sort(Comparator.comparingInt(Window::destinationWord));
        var merged=new ArrayList<Window>();for(var w:rows){
            if(!merged.isEmpty()){var old=merged.getLast();int end=old.destinationWord()+old.byteLength();if(w.destinationWord()<=end){merged.set(merged.size()-1,new Window(old.destinationWord(),Math.max(end,w.destinationWord()+w.byteLength())-old.destinationWord()));continue;}}
            merged.add(w);
        }
        var windows=List.copyOf(merged);var spans=windows.stream().map(w->new CdPatternResidency.Span(w.destinationWord(),w.byteLength())).toArray(CdPatternResidency.Span[]::new);
        return new State(assets,windows,CdPatternResidency.unknown(new CdPatternResidency.Profile(spans)));
    }
    // BEGIN authenticated R31 native-HUD sparse profile 20261107.
    /** Exact PLC/life union plus original UpdateHUD's dynamic destinations; all bytes UNKNOWN. */
    public static State unknownForNativeHud(CdR31aAssets assets,CdR31aHudAssets hud){
        Objects.requireNonNull(assets);Objects.requireNonNull(hud);
        if(!hud.ownsAssets(assets))throw new IllegalArgumentException("Foreign original R31 HUD profile");
        var rows=new ArrayList<>(unknown(assets).windows);
        for(var d:hud.destinations())rows.add(new Window(d.destinationWord(),d.byteLength()));
        rows.sort(Comparator.comparingInt(Window::destinationWord));var merged=new ArrayList<Window>();
        for(var w:rows){if(!merged.isEmpty()){var old=merged.getLast();int end=old.destinationWord()+old.byteLength();
            if(w.destinationWord()<=end){merged.set(merged.size()-1,new Window(old.destinationWord(),Math.max(end,w.destinationWord()+w.byteLength())-old.destinationWord()));continue;}}
            merged.add(w);}
        var windows=List.copyOf(merged);var spans=windows.stream().map(w->new CdPatternResidency.Span(w.destinationWord(),w.byteLength())).toArray(CdPatternResidency.Span[]::new);
        return new State(assets,windows,CdPatternResidency.unknown(new CdPatternResidency.Profile(spans)));
    }
    // END authenticated R31 native-HUD sparse profile 20261107.
    /** Pure transforms. Actual native clear/tile publication remains caller-owned. */
    public static State clear(State s){Objects.requireNonNull(s);return new State(s.assets,s.windows,CdPatternResidency.clear(s.value));}
    public static State withTile(State s,int destinationWord,CdAssetBytes tile32){Objects.requireNonNull(s);try{return new State(s.assets,s.windows,CdPatternResidency.writeTile(s.value,destinationWord,tile32));}catch(CdPatternResidency.Fault fault){throw new OwnershipBoundary(fault);}}
    public static CdAssetBytes readTile(State s,int nativePatternId){Objects.requireNonNull(s);try{return CdPatternResidency.readTile(s.value,nativePatternId);}catch(CdPatternResidency.Fault fault){throw new OwnershipBoundary(fault);}}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
