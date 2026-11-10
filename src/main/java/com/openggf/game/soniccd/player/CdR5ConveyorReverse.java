package com.openggf.game.soniccd.player;

/** One shared source-owned BYTE, separate from checkpoint BYTE. Native R5 Compatibility.asm:324
 * aliases reverse to checkpoint+1, not checkpoint. No constructor/definition grants a native clear.
 * The actual lifetime owner captures knowledge and all eight bits once; switches retain only their
 * own observed byte. This immutable value is observation, not a live owner or restore permission. */
public final class CdR5ConveyorReverse {
    public interface Port {
        int read();
        /** Actual lease validates before==current, then publishes exactly after. */
        void publish(Change change,CdR5ConveyorScope.Operation operation);
    }
    public record Change(int before,int after){
        public Change{requireByte(before);requireByte(after);if(after!=(before^1))throw new IllegalArgumentException("Conveyor contact toggles only bit zero");}
    }
    private final boolean known;
    private final int value;
    private CdR5ConveyorReverse(boolean known,int value){this.known=known;this.value=value;}
    public static CdR5ConveyorReverse unknown(){return new CdR5ConveyorReverse(false,0);}
    /** Use only a completed original source writer/restore; DATA never calls this automatically. */
    public static CdR5ConveyorReverse known(int value){return new CdR5ConveyorReverse(true,requireByte(value));}
    public boolean isKnown(){return known;}
    public int read(){if(!known)throw new IllegalStateException("Conveyor reverse BYTE has no reached source writer");return value;}
    public CdR5ConveyorReverse toggle(){return known(read()^1);}
    public static int requireByte(int value){if((value&~255)!=0)throw new IllegalArgumentException("Unsigned BYTE required");return value;}
}
