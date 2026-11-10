package com.openggf.game.soniccd.player;

import java.util.List;
import java.util.Objects;
import static com.openggf.game.soniccd.player.CdR5ConveyorScope.Kind.*;

/** Shared semantic terrain-belt algorithm. N1ecc5f3 r5/player_block_check.asm:3-82,308-339,
 * FixBugs=0. Called after PlayerStates and during Hurt by the actual original Body owner.
 * It neither supplies movement nor repeats those callers. Two feet are source-ordered; the first
 * match skips the second. Era/outcome directories are DATA, not duplicated per-act algorithms.
 * The native front owner remains injected; no generic collision substitute is manufactured.
 * Position proposals are raw16.16 values; actual adoption must use the existing NativePositionOps
 * owner and lease. Returned results/prefixes provide no right to replay, resume or publish stores. */
public final class CdR5Conveyor {
    public interface Environment{int bossFlags();int timeZone();int goodFuture();}
    public interface Player{
        int xRaw();int yWord();int heightByte();int widthByte();
        void publishX(PositionChange change,CdR5ConveyorScope.Operation operation);
    }
    public interface Terrain{int collisionBlock(int xWord,int yWord);int frontDistance(int angleByte);}
    /** Native signed-directory/count-minus-one WORDs; actual adapter authenticates the finite views. */
    public interface BlockDirectory{int directoryWord(int slot);int countMinusOne(int slot);int entryWord(int slot,int index);}
    public record PositionChange(int beforeRaw,int afterRaw,boolean wholeWordCorrection){ }
    public record Result(boolean displaced,List<CdR5ConveyorScope.Completion> prefix){public Result{prefix=List.copyOf(prefix);}}
    private final Environment environment;
    private final Player player;
    private final Terrain terrain;
    private final BlockDirectory tables;
    private final CdR5ConveyorReverse.Port reverse;
    private final CdR5ConveyorScope scope;
    public CdR5Conveyor(Environment environment,Player player,Terrain terrain,BlockDirectory tables,
                        CdR5ConveyorReverse.Port reverse,CdR5ConveyorScope.Guard guard){
        this.environment=Objects.requireNonNull(environment);this.player=Objects.requireNonNull(player);
        this.terrain=Objects.requireNonNull(terrain);this.tables=Objects.requireNonNull(tables);
        this.reverse=Objects.requireNonNull(reverse);scope=new CdR5ConveyorScope(guard);
    }
    public boolean isCurrent(CdR5ConveyorScope.Operation operation){return scope.isCurrent(operation);}
    public List<CdR5ConveyorScope.Completion> lastPrefix(){return scope.prefix();}
    public Result run(){
        scope.begin();boolean moved=false;
        try{
            if(byteRead(BOSS_FLAGS,environment::bossFlags)!=0)return new Result(false,lastPrefix());
            if(foot(false)||foot(true)){displace();moved=true;}
            return new Result(moved,lastPrefix());
        }finally{scope.end();}
    }
    private boolean foot(boolean right){
        int x=scope.call(PLAYER_X,0,0,player::xRaw)>>>16;
        int y=wordRead(PLAYER_Y,player::yWord);
        int height=(byte)byteRead(PLAYER_HEIGHT,player::heightByte);
        y=(y+height+2)&65535;
        int width=(byte)byteRead(PLAYER_WIDTH,player::widthByte);
        x=(x+(right?width:-width))&65535;
        final int footX=x,footY=y;
        int block=scope.call(FOOT_BLOCK,x,y,()->word(terrain.collisionBlock(footX,footY)))&0x7FF;
        int slot=byteRead(TIME_ZONE,environment::timeZone)&0x7F;
        if(slot==2)slot=(slot+byteRead(GOOD_FUTURE,environment::goodFuture))&255;
        final int selected=slot;
        // Both native reads are separate fault cuts. Offset is consumed/authenticated by DATA.
        scope.call(DIRECTORY,slot,0,()->word(tables.directoryWord(selected)));
        int last=scope.call(COUNT,slot,0,()->word(tables.countMinusOne(selected)));
        for(int i=0;i<=last;i++){
            final int index=i;
            if(scope.call(ENTRY,slot,index,()->word(tables.entryWord(selected,index)))==block)return true;
        }
        return false;
    }
    private void displace(){
        // Deliberately RAW timeZone: marked0x80 indexes Past above but selects speed2 here.
        int era=byteRead(TIME_ZONE,environment::timeZone);
        int delta=era==0?0x8000:era==1?0x10000:0x20000;
        int backwards=byteRead(REVERSE,reverse::read);
        int angle=0xC0;
        if(backwards!=0){delta=-delta;angle=0x40;}
        int before=scope.call(PLAYER_X,0,0,player::xRaw),after=before+delta;
        var change=new PositionChange(before,after,false);
        scope.run(MOVE_X,before,after,()->player.publishX(change,scope.current()));
        final int selectedAngle=angle;
        int distance=(short)(int)scope.call(FRONT,angle,0,()->word(terrain.frontDistance(selectedAngle)));
        if(distance>=0)return;
        if(byteRead(REVERSE,reverse::read)!=0)distance=(short)-distance;
        before=scope.call(PLAYER_X,0,0,player::xRaw);
        // Native ADD.W to x's high WORD preserves its subpixel low WORD.
        after=((((before>>>16)+distance)&65535)<<16)|(before&65535);
        var correction=new PositionChange(before,after,true);
        scope.run(CORRECT_X,before,after,()->player.publishX(correction,scope.current()));
    }
    private int byteRead(CdR5ConveyorScope.Kind kind,java.util.function.IntSupplier read){return scope.call(kind,0,0,()->CdR5ConveyorReverse.requireByte(read.getAsInt()));}
    private int wordRead(CdR5ConveyorScope.Kind kind,java.util.function.IntSupplier read){return scope.call(kind,0,0,()->word(read.getAsInt()));}
    private static int word(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Unsigned WORD required");return value;}
}
