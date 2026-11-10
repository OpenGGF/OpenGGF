package com.openggf.game.soniccd.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Supplier;

/** Synchronous conveyor-family occurrence fence. Values/records confer no publication or retry
 * authority. The actual Frame/placement owner must validate its lease, thread, generation, source,
 * complete external state and exact actor/slot before and after every reached native primitive.
 * No instruction dispatch, clock, RAM image or continuation is modeled here. */
public final class CdR5ConveyorScope {
    public enum Kind {
        BOSS_FLAGS, PLAYER_X, PLAYER_Y, PLAYER_HEIGHT, PLAYER_WIDTH, FOOT_BLOCK,
        TIME_ZONE, GOOD_FUTURE, DIRECTORY, COUNT, ENTRY, REVERSE, MOVE_X, FRONT, CORRECT_X,
        DISPATCH, SWITCH_STORE, DEBUG_MODE, CONTACT_Y, CONTACT_X, TOGGLE_REVERSE, PLAY_SOUND, DRAW, DESPAWN
    }
    /** first/second carry semantic read arguments or expected/proposed store values. */
    public record Operation(long ordinal,Kind kind,int first,int second) { }
    /** observedValue is present only for a completed semantic scalar read, never a command. */
    public record Completion(Operation operation,boolean completed,OptionalInt observedValue) { }
    public interface Guard {
        void before(Operation operation);
        void after(Operation operation,boolean completed);
        /** Constructed format tests only. Never sufficient for a live owner. */
        Guard NONE=new Guard(){
            @Override public void before(Operation operation) { }
            @Override public void after(Operation operation,boolean completed) { }
        };
    }
    private final Guard guard;
    private final Thread thread=Thread.currentThread();
    private boolean running;
    private long ordinal;
    private Operation current;
    private final List<Completion> reached=new ArrayList<>();
    public CdR5ConveyorScope(Guard guard){this.guard=Objects.requireNonNull(guard);}
    public void begin(){
        // Rejection precedes any trace reset or healthy outer-call closure.
        if(Thread.currentThread()!=thread||running)throw new IllegalStateException("Original non-reentrant conveyor-family scope required");
        running=true;ordinal=0;reached.clear();
    }
    public void end(){current=null;running=false;}
    public boolean isCurrent(Operation operation){return running&&Thread.currentThread()==thread&&operation!=null&&operation==current;}
    public List<Completion> prefix(){return List.copyOf(reached);}
    public Operation current(){
        if(!running||Thread.currentThread()!=thread||current==null)throw new IllegalStateException("No original conveyor occurrence");
        return current;
    }
    public <T>T call(Kind kind,int first,int second,Supplier<T> action){
        if(!running||Thread.currentThread()!=thread||current!=null)throw new IllegalStateException("Conveyor occurrence reentry");
        var op=new Operation(++ordinal,Objects.requireNonNull(kind),first,second);current=op;
        boolean completed=false;Throwable primary=null;OptionalInt observed=OptionalInt.empty();
        try{guard.before(op);T result=action.get();if(result instanceof Integer value)observed=OptionalInt.of(value);completed=true;return result;}
        catch(RuntimeException|Error fault){primary=fault;throw fault;}
        finally{
            try{guard.after(op,completed);}
            catch(RuntimeException|Error after){if(primary!=null){if(primary!=after)primary.addSuppressed(after);}else throw after;}
            finally{reached.add(new Completion(op,completed,observed));current=null;}
        }
    }
    public void run(Kind kind,int first,int second,Runnable action){call(kind,first,second,()->{action.run();return null;});}
}
