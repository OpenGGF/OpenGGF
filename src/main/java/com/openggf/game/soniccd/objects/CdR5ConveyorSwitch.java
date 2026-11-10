package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.player.CdR5ConveyorReverse;
import com.openggf.game.soniccd.player.CdR5ConveyorScope;
import java.util.List;
import java.util.Objects;
import static com.openggf.game.soniccd.player.CdR5ConveyorScope.Kind.*;

/** Pure original ConveyorSwitchObject family, N1ecc5f3 objects/r5/conveyor_switch.asm:3-133.
 * FixBugs=0: routine6's two comparisons use BYTE absolute distances, unlike routine2's WORDs.
 * No allocator, private reverse owner, animation interpreter or per-act algorithm is created.
 * Source/frame DATA is supplied separately; ID31 is Null in native Act3 and is NOT admitted here.
 * All state and completed-prefix values are observations, never publication/restore/retry authority.
 * Actual pool/Draw/despawn/FM owners must be bound by the original invocation guard. */
public final class CdR5ConveyorSwitch {
    public static final int OBJECT_ID=0x31;
    public enum Field{ROUTINE,SPRITE_FLAGS,LAYER,WIDTH,HEIGHT,TILE,MAPPINGS,FRAME,TIMER,SEEN_REVERSE,REMOTE_MARKER}
    public record State(int xWord,int yWord,int routine,int spriteFlags,int layer,int width,int height,
                        int tile,int mappings,int frame,int timer,int seenReverse,int remoteMarker){
        public State{
            word(xWord);word(yWord);word(tile);
            for(int value:new int[]{routine,spriteFlags,layer,width,height,frame,timer,seenReverse,remoteMarker})CdR5ConveyorReverse.requireByte(value);
        }
        public State with(Field field,int value){
            Objects.requireNonNull(field);
            return new State(xWord,yWord,field==Field.ROUTINE?value:routine,field==Field.SPRITE_FLAGS?value:spriteFlags,
                field==Field.LAYER?value:layer,field==Field.WIDTH?value:width,field==Field.HEIGHT?value:height,
                field==Field.TILE?value:tile,field==Field.MAPPINGS?value:mappings,field==Field.FRAME?value:frame,
                field==Field.TIMER?value:timer,field==Field.SEEN_REVERSE?value:seenReverse,field==Field.REMOTE_MARKER?value:remoteMarker);
        }
    }
    public record Change(Field field,State before,State after){
        public Change{Objects.requireNonNull(field);Objects.requireNonNull(before);Objects.requireNonNull(after);}
    }
    public interface Ports{
        int debugMode();int playerYWord();int playerXWord();
        /** Synchronous primitive: validate the actual lease/current source then adopt this store once.
         * Throw before any write on rejection. Actual completed-store/fault authority stays here. */
        void publishState(Change change,CdR5ConveyorScope.Operation operation);
        void sound(int command,CdR5ConveyorScope.Operation operation);
        /** Prefix state is the last switch-owned image. Draw's bit7 and saved/delete mutations belong
         * to the existing actual owners and must be tracked by their private receipts/Frame capture. */
        void draw(State prefix,CdR5ConveyorScope.Operation operation);
        void despawn(State prefix,CdR5ConveyorScope.Operation operation);
    }
    private final int mappings;
    private final Ports ports;
    private final CdR5ConveyorReverse.Port reverse;
    private final CdR5ConveyorScope scope;
    private State state;
    public CdR5ConveyorSwitch(int mappingAddress,Ports ports,CdR5ConveyorReverse.Port sharedReverse,CdR5ConveyorScope.Guard guard){
        if(mappingAddress<=0)throw new IllegalArgumentException("Actual finite mapping address required");
        mappings=mappingAddress;this.ports=Objects.requireNonNull(ports);reverse=Objects.requireNonNull(sharedReverse);
        scope=new CdR5ConveyorScope(guard);
    }
    public boolean isCurrent(CdR5ConveyorScope.Operation operation){return scope.isCurrent(operation);}
    public State lastState(){return state;}
    public List<CdR5ConveyorScope.Completion> lastPrefix(){return scope.prefix();}
    public State run(State initial){
        Objects.requireNonNull(initial);scope.begin();
        try{
            state=initial;
            scope.run(DISPATCH,state.routine(),0,()->{
                if(state.routine()!=0&&state.routine()!=2&&state.routine()!=4&&state.routine()!=6)
                    throw new UnsupportedOperationException("Original ConveyorSwitch routine $"+Integer.toHexString(state.routine()));
            });
            switch(state.routine()){
                case 0 -> {initialize();contact();}
                case 2 -> contact();
                case 4 -> transition();
                case 6 -> separation();
                default -> throw new AssertionError("Validated switch dispatch");
            }
            scope.run(DRAW,0,0,()->ports.draw(state,scope.current()));
            scope.run(DESPAWN,0,0,()->ports.despawn(state,scope.current()));
            return state;
        }finally{scope.end();}
    }
    private void initialize(){
        put(Field.ROUTINE,(state.routine()+2)&255);
        put(Field.SPRITE_FLAGS,state.spriteFlags()|4);
        put(Field.LAYER,3);put(Field.WIDTH,0x18);put(Field.HEIGHT,0x18);put(Field.TILE,0x4797);put(Field.MAPPINGS,mappings);
        if((readReverse()&1)!=0)put(Field.FRAME,2);
        put(Field.SEEN_REVERSE,readReverse());
    }
    private void contact(){
        if(byteRead(DEBUG_MODE,ports::debugMode)==0){
            int y=wordRead(CONTACT_Y,ports::playerYWord);
            if(absoluteWordDistance(y,state.yWord())<0x10){
                int x=wordRead(CONTACT_X,ports::playerXWord);
                if(absoluteWordDistance(x,state.xWord())<8){
                    int before=readReverse(),after=before^1;
                    var change=new CdR5ConveyorReverse.Change(before,after);
                    scope.run(TOGGLE_REVERSE,before,after,()->reverse.publish(change,scope.current()));
                    put(Field.ROUTINE,(state.routine()+2)&255);
                    if((state.spriteFlags()&0x80)!=0)scope.run(PLAY_SOUND,0xBF,0,()->ports.sound(0xBF,scope.current()));
                    return;
                }
            }
        }
        int observed=readReverse();
        if(observed!=state.seenReverse()){
            put(Field.REMOTE_MARKER,255);put(Field.ROUTINE,(state.routine()+2)&255);
        }
        put(Field.SEEN_REVERSE,observed);
    }
    private void transition(){
        put(Field.TIMER,(state.timer()+1)&255);
        if(state.timer()==7){put(Field.FRAME,1);return;}
        if(state.timer()!=14)return;
        put(Field.TIMER,0);put(Field.FRAME,0);
        if((readReverse()&1)!=0)put(Field.FRAME,2);
        put(Field.ROUTINE,(state.routine()+2)&255);put(Field.REMOTE_MARKER,0);
    }
    private void separation(){
        int y=wordRead(CONTACT_Y,ports::playerYWord);
        // Native CMPI.B, not CMPI.W: retain wrap/alias behavior rather than fixing the game.
        if((absoluteWordDistance(y,state.yWord())&255)<0x20){
            int x=wordRead(CONTACT_X,ports::playerXWord);
            if((absoluteWordDistance(x,state.xWord())&255)<0x10)return;
        }
        put(Field.ROUTINE,2);put(Field.SEEN_REVERSE,readReverse());
    }
    private void put(Field field,int value){
        var before=state;var after=before.with(field,value);var change=new Change(field,before,after);
        scope.run(SWITCH_STORE,field.ordinal(),value,()->{ports.publishState(change,scope.current());state=after;});
    }
    private int readReverse(){return byteRead(REVERSE,reverse::read);}
    private int byteRead(CdR5ConveyorScope.Kind kind,java.util.function.IntSupplier read){return scope.call(kind,0,0,()->CdR5ConveyorReverse.requireByte(read.getAsInt()));}
    private int wordRead(CdR5ConveyorScope.Kind kind,java.util.function.IntSupplier read){return scope.call(kind,0,0,()->word(read.getAsInt()));}
    private static int absoluteWordDistance(int a,int b){return Math.abs(a-b)&65535;}
    private static int word(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Unsigned WORD required");return value;}
}
