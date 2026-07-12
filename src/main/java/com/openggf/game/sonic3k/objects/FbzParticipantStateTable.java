package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.RewindStateful;

import java.util.Arrays;
import java.util.IdentityHashMap;

/** Scalable identity-keyed primitive state used by FBZ multi-player carriers. */
final class FbzParticipantStateTable implements RewindStateful<FbzParticipantStateTable.Snapshot> {
    private final int columns;
    private final IdentityHashMap<PlayableEntity,Integer> slots=new IdentityHashMap<>();
    private int[][] values;
    private int size;
    private int bindCursor;

    FbzParticipantStateTable(int columns){this.columns=columns;values=new int[columns][4];}
    int slot(PlayableEntity player){Integer found=slots.get(player);if(found!=null)return found;int slot;if(bindCursor<size)slot=bindCursor++;else{slot=size++;bindCursor=size;ensure(size);}slots.put(player,slot);return slot;}
    int get(int slot,int column){return values[column][slot];}
    void set(int slot,int column,int value){values[column][slot]=value;}
    boolean flag(int slot,int column){return get(slot,column)!=0;}
    void flag(int slot,int column,boolean value){set(slot,column,value?1:0);}
    int size(){return size;}
    private void ensure(int needed){for(int i=0;i<columns;i++)if(values[i].length<needed)values[i]=Arrays.copyOf(values[i],Math.max(needed,values[i].length*2));}
    @Override public Snapshot captureRewindStateValue(){int[][] copy=new int[columns][];for(int i=0;i<columns;i++)copy[i]=Arrays.copyOf(values[i],size);return new Snapshot(size,copy);}
    @Override public void restoreRewindStateValue(Snapshot state){size=state.size;values=new int[columns][Math.max(4,size)];for(int i=0;i<columns;i++)System.arraycopy(state.values[i],0,values[i],0,size);slots.clear();bindCursor=0;}
    record Snapshot(int size,int[][] values){}
}
