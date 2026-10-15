package com.openggf.game.sonic3k.resources;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.level.Pattern;
import java.util.Arrays;
import java.util.Objects;

/** Frontend ROM pattern uploads retained through the next native level resource overlay. */
public final class S3kFrontendPatternResidency implements RewindSnapshottable<S3kFrontendPatternResidency.State> {
    public static final String KEY="s3k-frontend-pattern-residency";
    private static final int BYTES=0x10000;
    private byte[] physical;
    private long generation;
    private State lastCapture;
    public record State(long generation,byte[] packed) {
        public State {packed=packed==null?null:packed.clone();}
        @Override public byte[] packed(){return packed==null?null:packed.clone();}
    }
    /** Immutable copied construction input and matching live publication token; not gameplay RAM. */
    public record Seed(S3kFrontendPatternResidency owner,long generation,byte[] packed) {
        public Seed {Objects.requireNonNull(owner);packed=packed.clone();}
        @Override public byte[] packed(){return packed.clone();}
    }
    public static S3kFrontendPatternResidency currentOrNull(){
        var context=SessionManager.getCurrentGameplayMode();
        return context!=null&&context.runtimeArtCoordinator() instanceof S3kRuntimeArtCoordinator owner
                ?owner.frontendPatternResidency():null;
    }
    private void begin(){
        if(physical!=null)return;
        physical=new byte[BYTES];
        var levelManager=GameServices.levelOrNull();
        if(levelManager!=null&&levelManager.getCurrentLevel() instanceof Sonic3kLevel level
                &&level.hasStockRomZoneIdentity()){
            int count=level.getPatternCount();
            if(count>BYTES/32)throw new IllegalStateException("Native pattern address space exceeded");
            for(int tile=0;tile<count;tile++)write(tile,level.getPattern(tile));
        }
    }
    /** Called only by a frontend's admitted CPU upload owner, never virtual atlas caching/draw. */
    public void upload(int firstTile,Pattern[] patterns){
        Objects.requireNonNull(patterns);
        if(firstTile<0||(long)firstTile+patterns.length>BYTES/32)
            throw new IllegalArgumentException("Frontend upload exceeds native pattern address space");
        begin();for(int i=0;i<patterns.length;i++)if(patterns[i]!=null)write(firstTile+i,patterns[i]);
        generation++;lastCapture=null;
    }
    /** Literal native pattern-range fill; this does not represent name tables or SAT. */
    public void clearRange(int byteOffset,int length){
        if(byteOffset<0||length<0||(long)byteOffset+length>BYTES)throw new IllegalArgumentException("Native pattern fill range");
        begin();Arrays.fill(physical,byteOffset,byteOffset+length,(byte)0);generation++;lastCapture=null;
    }
    private void write(int tile,Pattern pattern){
        byte[] pixels=new byte[64];Objects.requireNonNull(pattern).copyInto(pixels,0);
        for(int p=0;p<64;p+=2)physical[tile*32+p/2]=(byte)(((pixels[p]&15)<<4)|(pixels[p+1]&15));
    }
    public Seed seed(){return physical==null?null:new Seed(this,generation,physical);}
    public static void validate(Seed seed){
        if(seed!=null&&(currentOrNull()!=seed.owner||seed.owner.generation!=seed.generation
                ||!Arrays.equals(seed.owner.physical,seed.packed)))
            throw new IllegalStateException("Prepared native pattern handoff generation changed");
    }
    public static void installed(Seed seed){if(seed!=null){validate(seed);seed.owner.retire();}}
    public byte[] handoff(){return physical==null?null:physical.clone();}
    public void inherit(byte[] packed){
        Objects.requireNonNull(packed);if(packed.length!=BYTES)throw new IllegalArgumentException("Native pattern handoff size");
        physical=packed.clone();generation++;lastCapture=null;
    }
    public void retire(){if(physical!=null){physical=null;generation++;lastCapture=null;}}
    @Override public String key(){return KEY;}
    @Override public State capture(){
        if(lastCapture==null)lastCapture=new State(generation,physical);
        return lastCapture;
    }
    @Override public void restore(State state){generation=state.generation();physical=state.packed();lastCapture=state;}
    @Override public void resetForMissingSnapshot(){physical=null;generation=0;lastCapture=null;}
}
