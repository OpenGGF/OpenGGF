package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMotionState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * One synchronous physical ID07 call; native Tunnel Path/Main.asm:119-478,
 * Object Spawner.asm:385-446 and Object Functions.asm:228-237 at
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. No FixBugs alternate exists here.
 * API base 558dd7d00a6c9ba447c83f1bb98effd7de4675c7, State8/unchanged Environment4.
 *
 * <p>Final immutable values are adopted once at native cuts. Scalar ordered stores describe
 * completed work; they are never commands to replay. Cross-owner calls carry BOTH ready
 * owner values. Runtime must preflight its actual pool overlay, Frame aggregate and FM ledger
 * before any assignment, then adopt ready values without allocation/callback between owners.
 * Public metadata is not producer, invocation, phase, site, freshness or exact-once authority.
 * No Frame/Runtime, draw, animator, Solid, ObjMove, math, graphics, art or capture owner is used.
 *
 * <p>Native stops and host failures preserve reached published prefixes. Full raw32 pointers
 * are retained while actual WORD operands resolve on the 24-bit bus over only GVels8/Paths284.
 * No subtype whitelist, default operand, successful padding, whole-call rollback or hidden budget.
 * An initial read port concealing its own mutation and equal-value no-op acceptance cannot be
 * authenticated by component value comparisons; Root's private actual invocation owns those limits.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdTunnelPath {
    public static final int OBJECT_ID=0x07;
    public enum NativeRoutine { INIT, MAIN, GOT_PLAYER, MOVE_PLAYER }
    public enum NativeCall { SET_PLAYER_GVEL, SET_PLAYER_SPEED, PLAY_FM_SOUND, CHECK_OBJ_DESPAWN }
    public enum FmSound {
        FM_91;
        public int nativeId() { return 0x91; }
    }
    public enum FmSite { MAIN_ACQUIRE, GOT_PLAYER }
    public enum BankSite { TRAVEL_PRELUDE, DESPAWN }
    public enum StoreKind {
        MOVE_BYTE, MOVE_WORD, MOVE_LONG, ADD_BYTE, SUB_BYTE, BCLR_BYTE, BSET_BYTE, OR_BYTE, CLEAR_BYTE, AND_WORD
    }
    public enum NativeCut {
        INIT_HEADER, INIT_INDEX_END, INIT_DATA, INIT_TARGET_X, INIT_TARGET_Y,
        ACQUIRE_BASE, ACQUIRE_GVEL_RAISE, ACQUIRE_FINISH, SPEED_Y, SPEED_X, GOT_ROUTINE,
        MOVE_DECREMENT, COMPLETE_SNAP, NEXT_INDEX, NEXT_TARGET_X, NEXT_TARGET_Y, INTEGRATE, RELEASE
    }
    public enum ObjectField {
        INIT_MAPPINGS(StoreKind.MOVE_LONG,32), INIT_SPRITE_FLAGS(StoreKind.MOVE_BYTE,8),
        INIT_PRIORITY(StoreKind.MOVE_BYTE,8), INIT_WIDTH(StoreKind.MOVE_BYTE,8),
        INIT_TILE(StoreKind.MOVE_WORD,16), INIT_ROUTINE(StoreKind.ADD_BYTE,8),
        INIT_INDEX_END(StoreKind.MOVE_WORD,16), INIT_DATA(StoreKind.MOVE_LONG,32),
        INIT_TARGET_X(StoreKind.MOVE_WORD,16), INIT_TARGET_Y(StoreKind.MOVE_WORD,16),
        ACQUIRE_ROUTINE(StoreKind.ADD_BYTE,8), ACQUIRE_PUSH_CLEAR(StoreKind.BCLR_BYTE,8),
        ACQUIRE_SCRATCH_CLEAR(StoreKind.CLEAR_BYTE,8),
        SPEED_STEPS_Y(StoreKind.MOVE_WORD,16), SPEED_STEPS_X(StoreKind.MOVE_WORD,16),
        GOT_ROUTINE(StoreKind.ADD_BYTE,8), MOVE_STEPS_HIGH(StoreKind.SUB_BYTE,8),
        NEXT_INDEX(StoreKind.MOVE_BYTE,8), NEXT_TARGET_X(StoreKind.MOVE_WORD,16),
        NEXT_TARGET_Y(StoreKind.MOVE_WORD,16), RELEASE_ROUTINE_CLEAR(StoreKind.CLEAR_BYTE,8),
        RELEASE_ROUTINE_WORD(StoreKind.MOVE_WORD,16);
        private final StoreKind kind; private final int width;
        ObjectField(StoreKind kind,int width) { this.kind=kind; this.width=width; }
        public StoreKind kind() { return kind; }
        public int widthBits() { return width; }
    }
    public enum TunnelPlayerField {
        HURT_ROUTINE(StoreKind.SUB_BYTE,8), HURT_TIMER(StoreKind.MOVE_WORD,16),
        ACQUIRE_CONTROL(StoreKind.MOVE_BYTE,8), ACQUIRE_INVISIBLE(StoreKind.OR_BYTE,8),
        ACQUIRE_ANIMATION(StoreKind.MOVE_BYTE,8), ACQUIRE_GVEL_BASE(StoreKind.MOVE_WORD,16),
        ACQUIRE_GVEL_RAISE(StoreKind.MOVE_WORD,16), ACQUIRE_X_VELOCITY(StoreKind.MOVE_WORD,16),
        ACQUIRE_Y_VELOCITY(StoreKind.MOVE_WORD,16), ACQUIRE_PUSH_CLEAR(StoreKind.BCLR_BYTE,8),
        ACQUIRE_AIR(StoreKind.BSET_BYTE,8), ACQUIRE_JUMP_CLEAR(StoreKind.CLEAR_BYTE,8),
        ACQUIRE_X_SNAP(StoreKind.MOVE_WORD,16), ACQUIRE_Y_SNAP(StoreKind.MOVE_WORD,16),
        SPEED_Y_X_VELOCITY(StoreKind.MOVE_WORD,16), SPEED_Y_Y_VELOCITY(StoreKind.MOVE_WORD,16),
        SPEED_X_Y_VELOCITY(StoreKind.MOVE_WORD,16), SPEED_X_X_VELOCITY(StoreKind.MOVE_WORD,16),
        COMPLETE_X_SNAP(StoreKind.MOVE_WORD,16), COMPLETE_Y_SNAP(StoreKind.MOVE_WORD,16),
        MOVE_X_LONG(StoreKind.MOVE_LONG,32), MOVE_Y_LONG(StoreKind.MOVE_LONG,32),
        RELEASE_Y_MASK(StoreKind.AND_WORD,16), RELEASE_CONTROL_CLEAR(StoreKind.CLEAR_BYTE,8);
        private final StoreKind kind; private final int width;
        TunnelPlayerField(StoreKind kind,int width) { this.kind=kind; this.width=width; }
        public StoreKind kind() { return kind; }
        public int widthBits() { return width; }
    }
    public enum DeleteLong {
        LONG_00,LONG_04,LONG_08,LONG_0C,LONG_10,LONG_14,LONG_18,LONG_1C,
        LONG_20,LONG_24,LONG_28,LONG_2C,LONG_30,LONG_34,LONG_38,LONG_3C
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, DIRECTORY_WORD, HEADER_WORD, INIT_X_WORD, INIT_Y_WORD, GVEL_WORD,
        NEXT_X_WORD, NEXT_Y_WORD, DIVIDE_Y_TIME, DIVIDE_X_SPEED, DIVIDE_X_TIME, DIVIDE_Y_SPEED, SAVED_FLAGS_ADDRESS
    }
    public enum StopReason { UNOWNED_ROUTINE, UNOWNED_ADDRESS, UNALIGNED_WORD, AMBIGUOUS_ADDRESS, ZERO_DIVISOR }

    /** Constructed views are values, not media authentication. No selected operand is read eagerly. */
    public record Assets(CdAssetBytes groundSpeeds,int groundSpeedsAddressBits,
                         CdAssetBytes paths,int pathsAddressBits,int mappingAddressBits) {
        public Assets { Objects.requireNonNull(groundSpeeds); Objects.requireNonNull(paths); }
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner);
            var speeds=owner.view(CdR11aObjectAssets.Table.TUNNEL_PATH_GROUND_SPEEDS);
            var paths=owner.view(CdR11aObjectAssets.Table.TUNNEL_PATH_COORDINATES);
            return new Assets(speeds.bytes(),speeds.range().nativeAddress(),paths.bytes(),paths.range().nativeAddress(),
                    CdR11aObjectAssets.Table.POWERUP_MAPPINGS.range().nativeAddress());
        }
    }
    public record Globals(int rawTimeZoneByte,int rawDirectionByte,int preScrollCameraXWord) {
        public Globals { byteValue(rawTimeZoneByte); byteValue(rawDirectionByte); wordValue(preScrollCameraXWord); }
    }
    public record PlayerCut(CdPlayerBody.State state,CdPlayerBody.Environment environment) {
        public PlayerCut { Objects.requireNonNull(state); Objects.requireNonNull(environment); }
    }
    public sealed interface NativeStore permits ObjectStore,PlayerStore { }
    public record ObjectStore(ObjectField field,int expectedRaw,int value) implements NativeStore {
        public ObjectStore {
            Objects.requireNonNull(field); rawValue(field.widthBits(),expectedRaw); rawValue(field.widthBits(),value);
            fixedObjectOperation(field,expectedRaw,value);
        }
    }
    public record PlayerStore(TunnelPlayerField field,int expectedRaw,int value) implements NativeStore {
        public PlayerStore {
            Objects.requireNonNull(field); rawValue(field.widthBits(),expectedRaw); rawValue(field.widthBits(),value);
            fixedPlayerOperation(field,expectedRaw,value);
        }
    }
    public record PlayerResult(CdPlayerBody.State finalState,CdPlayerBody.Environment finalEnvironment,List<PlayerStore> stores) {
        public PlayerResult { Objects.requireNonNull(finalState); Objects.requireNonNull(finalEnvironment); stores=List.copyOf(stores); }
    }
    public record PlayerCall(CdObjectPool.Snapshot actualCut,CdObjectState expectedSource,PlayerCut expectedPlayer,
                             CdObjectState finalSource,PlayerResult result,NativeCut site,List<NativeStore> stores) {
        public PlayerCall(CdObjectPool.Snapshot actualCut,CdObjectState expectedSource,PlayerCut expectedPlayer,
                          CdObjectState finalSource,PlayerResult result,NativeCut site,List<NativeStore> stores) {
            this.actualCut=Objects.requireNonNull(actualCut); this.expectedSource=Objects.requireNonNull(expectedSource);
            this.expectedPlayer=Objects.requireNonNull(expectedPlayer); this.finalSource=Objects.requireNonNull(finalSource);
            this.result=Objects.requireNonNull(result); this.site=Objects.requireNonNull(site); this.stores=List.copyOf(stores);
            sourceInCut(actualCut,expectedSource); CdObjectPool.overlay(actualCut,List.of(finalSource));
            validatePlayerCall(this);
        }
    }
    public interface PlayerOwner { PlayerCut read(); void commit(PlayerCall call); }
    public record FmCall(CdObjectPool.Snapshot actualCut,CdObjectState expectedSource,PlayerCut expectedPlayer,FmSite site,FmSound sound) {
        public FmCall { sourceInCut(actualCut,expectedSource); Objects.requireNonNull(expectedPlayer); Objects.requireNonNull(site); Objects.requireNonNull(sound); }
    }
    @FunctionalInterface public interface PlayFmCall { void play(FmCall call); }

    public sealed interface Effect permits RoutineEntered,CallReturned,StoresCompleted,FmPlayed,SavedFlagChanged,Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { public RoutineEntered { Objects.requireNonNull(routine); } }
    public record CallReturned(NativeCall call) implements Effect { public CallReturned { Objects.requireNonNull(call); } }
    public record StoresCompleted(NativeCut site,List<NativeStore> stores) implements Effect {
        public StoresCompleted { Objects.requireNonNull(site); stores=List.copyOf(stores); }
    }
    public record FmPlayed(FmSite site,FmSound sound) implements Effect {
        public FmPlayed { Objects.requireNonNull(site); Objects.requireNonNull(sound); }
    }
    public record SavedFlagChanged(BankSite site,int byteIndex,int previousValue,int value) implements Effect {
        public SavedFlagChanged { Objects.requireNonNull(site); byteValue(previousValue); byteValue(value); }
    }
    /** Observation of one of DeleteObject's fixed sixteen LONG clears, never a writable address. */
    public record DeleteStore(DeleteLong field,int expectedBits) { public DeleteStore { Objects.requireNonNull(field); } }
    public record Deleted(List<DeleteStore> stores) implements Effect { public Deleted { stores=List.copyOf(stores); } }
    public record Result(CdObjectState state,List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects=List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public static final class UnsupportedTunnelPath extends UnsupportedOperationException {
        private static final long serialVersionUID=1L;
        private final UnsupportedBoundary boundary; private final StopReason reason; private final int wholeIndex,value;
        private UnsupportedTunnelPath(UnsupportedBoundary boundary,StopReason reason,Cursor c,int value) {
            super(boundary+" / "+reason+" at Tunnel Path whole slot "+c.wholeIndex+", raw32 $"+Integer.toHexString(value));
            this.boundary=boundary; this.reason=reason; this.wholeIndex=c.wholeIndex; this.value=value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public StopReason reason() { return reason; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final PlayerOwner playerOwner;
    private final PlayFmCall playFm;
    private boolean running;

    public CdTunnelPath(CdObjectPlacementRuntime placement,Assets assets,PlayerOwner playerOwner,PlayFmCall playFm) {
        this.placement=Objects.requireNonNull(placement); this.assets=Objects.requireNonNull(assets);
        this.playerOwner=Objects.requireNonNull(playerOwner); this.playFm=Objects.requireNonNull(playFm);
    }

    /** ObjTunnelPath:119-168. Travel preclear precedes both primary gates and raw dispatch. */
    public Result run(int wholeIndex,Globals globals) {
        if(running) throw new IllegalStateException("Tunnel Path cannot reenter its synchronous physical call");
        running=true;
        try {
            Objects.requireNonNull(globals); var pool=placement.pool(); var source=pool.state(wholeIndex);
            if(source.id()!=OBJECT_ID) throw new IllegalArgumentException("Selected slot is not Tunnel Path ID $07");
            var c=new Cursor(pool,source);
            if((globals.rawTimeZoneByte()&0x80)!=0&&c.state.savedFlagsId()!=0)
                clearSaved(c,globals,BankSite.TRAVEL_PRELUDE);
            var player=readPlayer(c).state().motion();
            if(player.animation()==0x2B||player.routine()>=6) return new Result(c.state,c.effects);
            switch(c.state.routine()) {
                case 0 -> { init(c); main(c); }
                case 2 -> main(c);
                case 4 -> gotPlayer(c);
                case 6 -> movePlayer(c);
                default -> throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH,StopReason.UNOWNED_ROUTINE,c,c.state.routine());
            }
            captureAndRequire(c); c.state=c.pool.state(wholeIndex);
            if(c.state.routine()<4) despawn(c,globals);
            return new Result(c.state,c.effects);
        } finally { running=false; }
    }
    /** Init:180-196. Each selected read follows the completed preceding native stores. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        publishOwn(c,NativeCut.INIT_HEADER,List.of(
                new ObjectStore(ObjectField.INIT_MAPPINGS,c.state.longBits(MAPPINGS),assets.mappingAddressBits()),
                new ObjectStore(ObjectField.INIT_SPRITE_FLAGS,c.state.spriteFlags(),4),
                new ObjectStore(ObjectField.INIT_PRIORITY,c.state.unsignedByte(PRIORITY),1),
                new ObjectStore(ObjectField.INIT_WIDTH,c.state.unsignedByte(WIDTH),0x10),
                new ObjectStore(ObjectField.INIT_TILE,c.state.word(TILE),0x0541),
                new ObjectStore(ObjectField.INIT_ROUTINE,c.state.routine(),(c.state.routine()+2)&255)));
        int directory=(c.state.subtype()*2)&0xFE;
        int displacement=(short)readWord(c,UnsupportedBoundary.DIRECTORY_WORD,assets.pathsAddressBits()+directory);
        int pointer=assets.pathsAddressBits()+displacement; // ADDA.W, preserving the entire raw32 register.
        int header=readWord(c,UnsupportedBoundary.HEADER_WORD,pointer);
        publishOwn(c,NativeCut.INIT_INDEX_END,List.of(new ObjectStore(ObjectField.INIT_INDEX_END,c.state.word(TUNNEL_PATH_INDEX_END),header)));
        pointer+=2;
        publishOwn(c,NativeCut.INIT_DATA,List.of(new ObjectStore(ObjectField.INIT_DATA,c.state.longBits(TUNNEL_PATH_DATA),pointer)));
        int x=readWord(c,UnsupportedBoundary.INIT_X_WORD,pointer);
        publishOwn(c,NativeCut.INIT_TARGET_X,List.of(new ObjectStore(ObjectField.INIT_TARGET_X,c.state.word(TUNNEL_PATH_TARGET_X),x)));
        int y=readWord(c,UnsupportedBoundary.INIT_Y_WORD,pointer+2);
        publishOwn(c,NativeCut.INIT_TARGET_Y,List.of(new ObjectStore(ObjectField.INIT_TARGET_Y,c.state.word(TUNNEL_PATH_TARGET_Y),y)));
    }
    /** Main:200-243. Rectangle arithmetic is WORD-wrapped; whole nonzero control rejects. */
    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN); var before=readPlayer(c); var m=before.state().motion();
        if((((m.centreX16_16()>>>16)-c.state.centreX()+16)&0xFFFF)>=32) return;
        if((((m.centreY16_16()>>>16)-c.state.centreY()+16)&0xFFFF)>=32) return;
        if(m.playerControl()!=0) return;
        var q=new PlayerProjection(before); var stores=new ArrayList<NativeStore>();
        if(m.routine()==4) {
            stores.add(q.store(TunnelPlayerField.HURT_ROUTINE,2));
            stores.add(q.store(TunnelPlayerField.HURT_TIMER,0x78));
        }
        stores.add(new ObjectStore(ObjectField.ACQUIRE_ROUTINE,c.state.routine(),(c.state.routine()+2)&255));
        stores.add(q.store(TunnelPlayerField.ACQUIRE_CONTROL,0x81));
        if(c.state.subtype2()!=0) stores.add(q.store(TunnelPlayerField.ACQUIRE_INVISIBLE,q.control|0x40));
        stores.add(q.store(TunnelPlayerField.ACQUIRE_ANIMATION,2));
        // SetPlayerGVel:375 starts with this unconditional store BEFORE its reached table read.
        stores.add(q.store(TunnelPlayerField.ACQUIRE_GVEL_BASE,0x1000));
        publishPlayer(c,before,q,NativeCut.ACQUIRE_BASE,stores);
        setPlayerGVel(c);
        before=readPlayer(c); q=new PlayerProjection(before); stores=new ArrayList<>();
        stores.add(q.store(TunnelPlayerField.ACQUIRE_X_VELOCITY,0));
        stores.add(q.store(TunnelPlayerField.ACQUIRE_Y_VELOCITY,0));
        stores.add(new ObjectStore(ObjectField.ACQUIRE_PUSH_CLEAR,c.state.flags(),c.state.flags()&~0x20));
        stores.add(q.store(TunnelPlayerField.ACQUIRE_PUSH_CLEAR,q.flags&~0x20));
        stores.add(q.store(TunnelPlayerField.ACQUIRE_AIR,q.flags|2));
        stores.add(q.store(TunnelPlayerField.ACQUIRE_JUMP_CLEAR,0));
        stores.add(q.store(TunnelPlayerField.ACQUIRE_X_SNAP,c.state.centreX()));
        stores.add(q.store(TunnelPlayerField.ACQUIRE_Y_SNAP,c.state.centreY()));
        stores.add(new ObjectStore(ObjectField.ACQUIRE_SCRATCH_CLEAR,c.state.unsignedByte(TUNNEL_PATH_SCRATCH),0));
        publishPlayer(c,before,q,NativeCut.ACQUIRE_FINISH,stores);
        fm(c,FmSite.MAIN_ACQUIRE);
    }
    /** SetPlayerGVel:375-388 continuation after the already published initial $1000 store. */
    private void setPlayerGVel(Cursor c) {
        int subtype=c.state.subtype();
        if((subtype&0x80)==0) {
            int selected=readWord(c,UnsupportedBoundary.GVEL_WORD,assets.groundSpeedsAddressBits()+2*(subtype&15));
            var before=readPlayer(c);
            if((short)selected>(short)before.state().motion().groundVelocity()) {
                var q=new PlayerProjection(before); var store=q.store(TunnelPlayerField.ACQUIRE_GVEL_RAISE,selected);
                publishPlayer(c,before,q,NativeCut.ACQUIRE_GVEL_RAISE,List.of(store));
            }
        }
        c.returned(NativeCall.SET_PLAYER_GVEL);
    }
    private void gotPlayer(Cursor c) {
        c.enter(NativeRoutine.GOT_PLAYER); setPlayerSpeed(c);
        publishOwn(c,NativeCut.GOT_ROUTINE,List.of(new ObjectStore(ObjectField.GOT_ROUTINE,c.state.routine(),(c.state.routine()+2)&255)));
        fm(c,FmSite.GOT_PLAYER);
    }
    /** SetPlayerSpeed:303-371. Native SUB/BGE, unsigned magnitudes and DIVS overflow are retained. */
    private void setPlayerSpeed(Cursor c) {
        var before=readPlayer(c); var q=new PlayerProjection(before);
        int targetX=c.state.word(TUNNEL_PATH_TARGET_X), targetY=c.state.word(TUNNEL_PATH_TARGET_Y);
        int playerX=q.x>>>16,playerY=q.y>>>16;
        int dx=(targetX-playerX)&0xFFFF,dy=(targetY-playerY)&0xFFFF;
        int mx=dx,my=dy,sx=q.groundVelocity&0xFFFF,sy=sx;
        if(!bgeSubWord(targetX,playerX)) { mx=(-mx)&0xFFFF; sx=(-sx)&0xFFFF; }
        if(!bgeSubWord(targetY,playerY)) { my=(-my)&0xFFFF; sy=(-sy)&0xFFFF; }
        var stores=new ArrayList<NativeStore>();
        if(my>=mx) { // CMP.W/BCS: tie takes the Y branch.
            int time=divideSigned(c,UnsupportedBoundary.DIVIDE_Y_TIME,dy<<16,sy);
            int xv=dx==0?0:divideSigned(c,UnsupportedBoundary.DIVIDE_X_SPEED,dx<<16,time&0xFFFF);
            stores.add(q.store(TunnelPlayerField.SPEED_Y_X_VELOCITY,xv&0xFFFF));
            stores.add(q.store(TunnelPlayerField.SPEED_Y_Y_VELOCITY,sy));
            int steps=time&0xFFFF; if((short)steps<0) steps=(-steps)&0xFFFF;
            stores.add(new ObjectStore(ObjectField.SPEED_STEPS_Y,c.state.word(TUNNEL_PATH_STEPS),steps));
            publishPlayer(c,before,q,NativeCut.SPEED_Y,stores);
        } else {
            int time=divideSigned(c,UnsupportedBoundary.DIVIDE_X_TIME,dx<<16,sx);
            int yv=dy==0?0:divideSigned(c,UnsupportedBoundary.DIVIDE_Y_SPEED,dy<<16,time&0xFFFF);
            stores.add(q.store(TunnelPlayerField.SPEED_X_Y_VELOCITY,yv&0xFFFF));
            stores.add(q.store(TunnelPlayerField.SPEED_X_X_VELOCITY,sx));
            int steps=time&0xFFFF; if((short)steps<0) steps=(-steps)&0xFFFF;
            stores.add(new ObjectStore(ObjectField.SPEED_STEPS_X,c.state.word(TUNNEL_PATH_STEPS),steps));
            publishPlayer(c,before,q,NativeCut.SPEED_X,stores);
        }
        c.returned(NativeCall.SET_PLAYER_SPEED);
    }
    /** MovePlayer:257-299. HIGH byte countdown, WORD snaps, ordered LONG integration, release WORD bug. */
    private void movePlayer(Cursor c) {
        c.enter(NativeRoutine.MOVE_PLAYER);
        int steps=(c.state.unsignedByte(TUNNEL_PATH_STEPS_HIGH)-1)&255;
        publishOwn(c,NativeCut.MOVE_DECREMENT,List.of(new ObjectStore(ObjectField.MOVE_STEPS_HIGH,c.state.unsignedByte(TUNNEL_PATH_STEPS_HIGH),steps)));
        var before=readPlayer(c); var q=new PlayerProjection(before);
        if(steps<0x80) {
            // Both source longs/velocities are read before the first native LONG store.
            int x=q.x+((short)q.xVelocity<<8), y=q.y+((short)q.yVelocity<<8);
            var a=q.store(TunnelPlayerField.MOVE_X_LONG,x); var b=q.store(TunnelPlayerField.MOVE_Y_LONG,y);
            publishPlayer(c,before,q,NativeCut.INTEGRATE,List.of(a,b)); return;
        }
        var x=q.store(TunnelPlayerField.COMPLETE_X_SNAP,c.state.word(TUNNEL_PATH_TARGET_X));
        var y=q.store(TunnelPlayerField.COMPLETE_Y_SNAP,c.state.word(TUNNEL_PATH_TARGET_Y));
        publishPlayer(c,before,q,NativeCut.COMPLETE_SNAP,List.of(x,y));
        int index=(c.state.unsignedByte(TUNNEL_PATH_INDEX)+4)&255;
        if(index<c.state.unsignedByte(TUNNEL_PATH_END)) {
            publishOwn(c,NativeCut.NEXT_INDEX,List.of(new ObjectStore(ObjectField.NEXT_INDEX,c.state.unsignedByte(TUNNEL_PATH_INDEX),index)));
            int pointer=c.state.longBits(TUNNEL_PATH_DATA);
            int tx=readWord(c,UnsupportedBoundary.NEXT_X_WORD,pointer+index);
            publishOwn(c,NativeCut.NEXT_TARGET_X,List.of(new ObjectStore(ObjectField.NEXT_TARGET_X,c.state.word(TUNNEL_PATH_TARGET_X),tx)));
            int ty=readWord(c,UnsupportedBoundary.NEXT_Y_WORD,pointer+index+2);
            publishOwn(c,NativeCut.NEXT_TARGET_Y,List.of(new ObjectStore(ObjectField.NEXT_TARGET_Y,c.state.word(TUNNEL_PATH_TARGET_Y),ty)));
            setPlayerSpeed(c); return;
        }
        before=readPlayer(c); q=new PlayerProjection(before);
        var stores=new ArrayList<NativeStore>();
        stores.add(q.store(TunnelPlayerField.RELEASE_Y_MASK,(q.y>>>16)&0x7FF));
        stores.add(new ObjectStore(ObjectField.RELEASE_ROUTINE_CLEAR,c.state.routine(),0));
        stores.add(q.store(TunnelPlayerField.RELEASE_CONTROL_CLEAR,0));
        // The preceding CLR.B changes the high byte only; low routine2 survives until this WORD.
        stores.add(new ObjectStore(ObjectField.RELEASE_ROUTINE_WORD,c.state.word(TUNNEL_PATH_ROUTINE_WORD)&255,2));
        publishPlayer(c,before,q,NativeCut.RELEASE,stores);
    }
    private void fm(Cursor c,FmSite site) {
        var before=readPlayer(c); var cut=captureAndRequire(c);
        var call=new FmCall(cut,c.state,before,site,FmSound.FM_91);
        var observation=new FmPlayed(site,FmSound.FM_91);
        try { playFm.play(call); }
        finally {
            requirePool(c,cut,call.expectedSource()); requirePlacement(c);
            requirePair(before,readActualPlayer(c,before));
        }
        c.effects.add(observation); c.returned(NativeCall.PLAY_FM_SOUND);
    }
    private void despawn(Cursor c,Globals globals) {
        int x=c.state.centreX();
        boolean outside=c.state.signedByte(SPRITE_FLAGS)>=0
                && (((x&0xFF80)-((globals.preScrollCameraXWord()-0x80)&0xFF80))&0xFFFF)>0x280;
        if(outside||(globals.rawTimeZoneByte()&0x80)!=0) {
            if(c.state.savedFlagsId()!=0) clearSaved(c,globals,BankSite.DESPAWN);
            delete(c);
        }
        c.returned(NativeCall.CHECK_OBJ_DESPAWN);
    }
    private void clearSaved(Cursor c,Globals globals,BankSite site) {
        captureAndRequire(c); var at=savedAddress(c,globals);
        int old=placement.savedFlag(at.id(),at.period()), value=old&0x7F;
        var observation=new SavedFlagChanged(site,at.byteIndex(),old,value);
        byte[] bytes=c.savedFlags.copy(); bytes[at.byteIndex()]=(byte)value;
        var ready=CdAssetBytes.copyOf(bytes);
        placement.bclrSavedFlag(at.id(),at.period(),CdObjectPlacementRuntime.SavedBit.LOADED);
        c.savedFlags=ready; requirePlacement(c); c.effects.add(observation);
    }
    private static SavedAddress savedAddress(Cursor c,Globals globals) {
        int period=globals.rawTimeZoneByte()&0x7F;
        if((globals.rawTimeZoneByte()&0x80)!=0) {
            int adjusted=(period-(byte)globals.rawDirectionByte())&0xFFFF;
            period=(short)adjusted<0?0:adjusted>=3?2:adjusted;
        }
        int index=2+3*c.state.savedFlagsId()+period;
        if(index<2||index>=CdObjectPlacementRuntime.SAVED_BANK_BYTES)
            throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS,StopReason.UNOWNED_ADDRESS,c,index);
        return new SavedAddress((index-2)/3,(index-2)%3,index);
    }
    private record SavedAddress(int id,int period,int byteIndex) { }
    private int readWord(Cursor c,UnsupportedBoundary boundary,int addressBits) {
        if((addressBits&1)!=0) throw unsupported(boundary,StopReason.UNALIGNED_WORD,c,addressBits);
        return resolveWord(c,boundary,addressBits);
    }
    /** Two reached bus bytes, not a script-count whitelist or four-byte pair alignment test. */
    private int resolveWord(Cursor c,UnsupportedBoundary boundary,int addressBits) {
        int word=0;
        for(int byteIndex=0;byteIndex<2;byteIndex++) {
            int bus=(addressBits+byteIndex)&0xFFFFFF, selected=-1;
            int speedOffset=(bus-(assets.groundSpeedsAddressBits()&0xFFFFFF))&0xFFFFFF;
            int pathOffset=(bus-(assets.pathsAddressBits()&0xFFFFFF))&0xFFFFFF;
            if(speedOffset<8&&speedOffset<assets.groundSpeeds().length())
                selected=assets.groundSpeeds().unsignedByte(speedOffset);
            if(pathOffset<284&&pathOffset<assets.paths().length()) {
                int value=assets.paths().unsignedByte(pathOffset);
                if(selected>=0&&selected!=value) throw unsupported(boundary,StopReason.AMBIGUOUS_ADDRESS,c,addressBits);
                selected=value;
            }
            if(selected<0) throw unsupported(boundary,StopReason.UNOWNED_ADDRESS,c,addressBits);
            word=(word<<8)|selected;
        }
        return word;
    }
    private static int divideSigned(Cursor c,UnsupportedBoundary boundary,int dividend,int divisorBits) {
        int divisor=(short)divisorBits;
        if(divisor==0) throw unsupported(boundary,StopReason.ZERO_DIVISOR,c,divisorBits);
        long quotient=(long)dividend/divisor;
        if(quotient<-32768||quotient>32767) return dividend; // 68000 DIVS overflow leaves Dn unreplaced.
        int remainder=(int)((long)dividend%divisor);
        return (remainder<<16)|((int)quotient&0xFFFF);
    }
    private static boolean bgeSubWord(int left,int right) {
        int result=(left-right)&0xFFFF;
        boolean negative=(result&0x8000)!=0, overflow=((left^right)&(left^result)&0x8000)!=0;
        return negative==overflow;
    }

    private PlayerCut readPlayer(Cursor c) {
        var value=readActualPlayer(c,c.player); c.player=value; return value;
    }
    /** Initial ingress cannot authenticate a read port which hides its own same-owner mutation. */
    private PlayerCut readActualPlayer(Cursor c,PlayerCut expected) {
        var cut=captureAndRequire(c); var source=c.state;
        try {
            var value=Objects.requireNonNull(playerOwner.read(),"actual primary State/Environment");
            if(expected!=null) requirePair(expected,value);
            return value;
        } finally { requirePool(c,cut,source); requirePlacement(c); }
    }
    /** One synchronous call carries all prebuilt owner values, never historical write commands. */
    private void publishPlayer(Cursor c,PlayerCut before,PlayerProjection q,NativeCut site,List<NativeStore> stores) {
        requirePair(before,readPlayer(c)); var cut=captureAndRequire(c); var original=c.state;
        CdObjectState after=original;
        for(var store:stores) if(store instanceof ObjectStore own) {
            if(objectRaw(after,own.field())!=own.expectedRaw()) throw new IllegalArgumentException("Stale own store at "+own.field());
            after=objectValue(after,own.field(),own.value());
        }
        var result=q.result();
        var call=new PlayerCall(cut,original,before,after,result,site,stores);
        var readyPool=CdObjectPool.overlay(cut,List.of(after));
        var readyPair=new PlayerCut(result.finalState(),result.finalEnvironment());
        var observation=new StoresCompleted(site,stores);
        boolean returned=false;
        try { playerOwner.commit(call); returned=true; }
        finally {
            requirePlacement(c);
            var actual=c.pool.capture(); var actualSource=c.pool.state(c.wholeIndex);
            c.pool.validateRestore(actual);
            boolean originalPool=actual.slots().equals(cut.slots()), finalPool=actual.slots().equals(readyPool.slots());
            if(!originalPool&&!finalPool) throw new IllegalStateException("Tunnel publication changed source64/complete96 nonwrites");
            c.expectedPool=actual; c.state=actualSource;
            var actualPlayer=readActualPlayer(c,null);
            boolean originalPair=actualPlayer.equals(before), finalPair=actualPlayer.equals(readyPair);
            if(returned ? !(finalPool&&finalPair) : !((originalPool&&originalPair)||(finalPool&&finalPair)))
                throw new IllegalStateException("Tunnel publication failed its complete prebuilt pool/player pair");
            c.player=actualPlayer;
        }
        c.effects.add(observation);
    }
    private void publishOwn(Cursor c,NativeCut site,List<ObjectStore> stores) {
        var cut=captureAndRequire(c); CdObjectState after=c.state;
        for(var store:stores) {
            if(objectRaw(after,store.field())!=store.expectedRaw()) throw new IllegalArgumentException("Stale own store at "+store.field());
            after=objectValue(after,store.field(),store.value());
        }
        var readyPool=CdObjectPool.overlay(cut,List.of(after));
        var observation=new StoresCompleted(site,new ArrayList<NativeStore>(stores));
        c.pool.write(after); c.state=after; c.expectedPool=readyPool;
        requirePool(c,readyPool,after); requirePlacement(c); c.effects.add(observation);
    }
    private void delete(Cursor c) {
        var cut=captureAndRequire(c); byte[] before=c.state.copyBytes(); var stores=new ArrayList<DeleteStore>();
        for(var field:DeleteLong.values()) {
            int at=4*field.ordinal(), value=0;
            for(int i=0;i<4;i++) value=(value<<8)|Byte.toUnsignedInt(before[at+i]);
            stores.add(new DeleteStore(field,value));
        }
        var observation=new Deleted(stores);
        c.pool.deleteObject(c.wholeIndex); c.state=c.pool.state(c.wholeIndex);
        if(!Arrays.equals(new byte[CdObjectState.BYTE_SIZE],c.state.copyBytes()))
            throw new IllegalStateException("DeleteObject did not clear all64 bytes");
        var readyPool=CdObjectPool.overlay(cut,List.of(c.state)); c.expectedPool=readyPool;
        requirePool(c,readyPool,c.state); requirePlacement(c); c.effects.add(observation);
    }
    private CdObjectPool.Snapshot captureAndRequire(Cursor c) {
        var actual=c.pool.capture(); c.pool.validateRestore(c.expectedPool);
        if(!actual.slots().equals(c.expectedPool.slots())||!c.pool.state(c.wholeIndex).equals(c.state))
            throw new IllegalStateException("Tunnel actual source64/complete96 changed outside its native stores");
        requirePlacement(c); return actual;
    }
    private static void requirePool(Cursor c,CdObjectPool.Snapshot expected,CdObjectState source) {
        c.pool.validateRestore(expected);
        if(!c.pool.capture().slots().equals(expected.slots())||!c.pool.state(c.wholeIndex).equals(source))
            throw new IllegalStateException("Tunnel callback changed source64 or complete96 pool nonwrites");
    }
    private void requirePlacement(Cursor c) {
        var actual=placement.capture();
        if(!actual.status().equals(c.placementStatus)||!Arrays.equals(actual.savedFlags().copy(),c.savedFlags.copy()))
            throw new IllegalStateException("Tunnel callback changed placement cursors or saved-bank nonwrites");
    }
    private static void requirePair(PlayerCut expected,PlayerCut actual) {
        if(!expected.equals(actual)) throw new IllegalStateException("Tunnel actual full State8/Environment4 changed");
    }
    private static void sourceInCut(CdObjectPool.Snapshot cut,CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if(!cut.slots().get(source.identity().dynamicIndex()).equals(source))
            throw new IllegalArgumentException("Tunnel source is not the supplied full cut's physical image");
    }

    /** Pure finite old/value/nonwrite predicate. It authenticates neither invocation nor same-value acceptance. */
    public static void validatePlayerResult(PlayerCut expected,PlayerResult result) {
        Objects.requireNonNull(expected); Objects.requireNonNull(result);
        var q=new PlayerProjection(expected);
        for(var store:result.stores()) {
            if(q.raw(store.field())!=store.expectedRaw()) throw new IllegalArgumentException("Stale Tunnel player store at "+store.field());
            q.acceptValue(store.field(),store.value());
        }
        if(!q.stateValue().equals(result.finalState())||!expected.environment().equals(result.finalEnvironment()))
            throw new IllegalArgumentException("Tunnel result changes undeclared State8/Environment4 fields or misses its stores");
    }
    private static void validatePlayerCall(PlayerCall call) {
        validatePlayerResult(call.expectedPlayer(),call.result());
        if(!call.expectedSource().identity().equals(call.finalSource().identity()))
            throw new IllegalArgumentException("Tunnel publication changes physical identity");
        var playerStores=call.stores().stream().filter(PlayerStore.class::isInstance).map(PlayerStore.class::cast).toList();
        if(!playerStores.equals(call.result().stores())) throw new IllegalArgumentException("Tunnel mixed order disagrees with player store order");
        var own=call.expectedSource();
        for(var store:call.stores()) if(store instanceof ObjectStore s) {
            if(objectRaw(own,s.field())!=s.expectedRaw()) throw new IllegalArgumentException("Stale Tunnel object store");
            own=objectValue(own,s.field(),s.value());
        }
        if(!own.equals(call.finalSource())) throw new IllegalArgumentException("Tunnel final source changes undeclared owner/bytes");
        validateSiteShape(call);
    }
    private static void validateSiteShape(PlayerCall call) {
        var expected=new ArrayList<Object>(); var source=call.expectedSource(); var motion=call.expectedPlayer().state().motion();
        switch(call.site()) {
            case ACQUIRE_BASE -> {
                if(source.routine()!=2||motion.playerControl()!=0||motion.routine()>=6||motion.animation()==0x2B
                        ||((((motion.centreX16_16()>>>16)-source.centreX()+16)&0xFFFF)>=32)
                        ||((((motion.centreY16_16()>>>16)-source.centreY()+16)&0xFFFF)>=32))
                    throw new IllegalArgumentException("Not a reached Tunnel Main acquisition");
                if(motion.routine()==4) { expected.add(TunnelPlayerField.HURT_ROUTINE); expected.add(TunnelPlayerField.HURT_TIMER); }
                expected.add(ObjectField.ACQUIRE_ROUTINE); expected.add(TunnelPlayerField.ACQUIRE_CONTROL);
                if(source.subtype2()!=0) expected.add(TunnelPlayerField.ACQUIRE_INVISIBLE);
                expected.add(TunnelPlayerField.ACQUIRE_ANIMATION); expected.add(TunnelPlayerField.ACQUIRE_GVEL_BASE);
            }
            case ACQUIRE_GVEL_RAISE -> expected.add(TunnelPlayerField.ACQUIRE_GVEL_RAISE);
            case ACQUIRE_FINISH -> {
                expected.add(TunnelPlayerField.ACQUIRE_X_VELOCITY); expected.add(TunnelPlayerField.ACQUIRE_Y_VELOCITY);
                expected.add(ObjectField.ACQUIRE_PUSH_CLEAR); expected.add(TunnelPlayerField.ACQUIRE_PUSH_CLEAR);
                expected.add(TunnelPlayerField.ACQUIRE_AIR); expected.add(TunnelPlayerField.ACQUIRE_JUMP_CLEAR);
                expected.add(TunnelPlayerField.ACQUIRE_X_SNAP); expected.add(TunnelPlayerField.ACQUIRE_Y_SNAP);
                expected.add(ObjectField.ACQUIRE_SCRATCH_CLEAR);
            }
            case SPEED_Y -> {
                expected.add(TunnelPlayerField.SPEED_Y_X_VELOCITY); expected.add(TunnelPlayerField.SPEED_Y_Y_VELOCITY);
                expected.add(ObjectField.SPEED_STEPS_Y);
            }
            case SPEED_X -> {
                expected.add(TunnelPlayerField.SPEED_X_Y_VELOCITY); expected.add(TunnelPlayerField.SPEED_X_X_VELOCITY);
                expected.add(ObjectField.SPEED_STEPS_X);
            }
            case COMPLETE_SNAP -> { expected.add(TunnelPlayerField.COMPLETE_X_SNAP); expected.add(TunnelPlayerField.COMPLETE_Y_SNAP); }
            case INTEGRATE -> { expected.add(TunnelPlayerField.MOVE_X_LONG); expected.add(TunnelPlayerField.MOVE_Y_LONG); }
            case RELEASE -> {
                expected.add(TunnelPlayerField.RELEASE_Y_MASK); expected.add(ObjectField.RELEASE_ROUTINE_CLEAR);
                expected.add(TunnelPlayerField.RELEASE_CONTROL_CLEAR); expected.add(ObjectField.RELEASE_ROUTINE_WORD);
            }
            default -> throw new IllegalArgumentException("This native cut is not a cross-owner publication");
        }
        var actual=call.stores().stream().map(s->s instanceof PlayerStore p?(Object)p.field():((ObjectStore)s).field()).toList();
        if(!expected.equals(actual)) throw new IllegalArgumentException("Wrong/missing/duplicate Tunnel native field/site store order");
        for(var s:call.result().stores()) {
            if((s.field()==TunnelPlayerField.ACQUIRE_X_SNAP&&s.value()!=source.centreX())
                    ||(s.field()==TunnelPlayerField.ACQUIRE_Y_SNAP&&s.value()!=source.centreY())
                    ||(s.field()==TunnelPlayerField.COMPLETE_X_SNAP&&s.value()!=source.word(TUNNEL_PATH_TARGET_X))
                    ||(s.field()==TunnelPlayerField.COMPLETE_Y_SNAP&&s.value()!=source.word(TUNNEL_PATH_TARGET_Y)))
                throw new IllegalArgumentException("Tunnel WORD snap differs from actual selected source");
        }
    }

    private static int objectRaw(CdObjectState s,ObjectField field) {
        return switch(field) {
            case INIT_MAPPINGS -> s.longBits(MAPPINGS);
            case INIT_SPRITE_FLAGS -> s.spriteFlags();
            case INIT_PRIORITY -> s.unsignedByte(PRIORITY);
            case INIT_WIDTH -> s.unsignedByte(WIDTH);
            case INIT_TILE -> s.word(TILE);
            case INIT_ROUTINE,ACQUIRE_ROUTINE,GOT_ROUTINE,RELEASE_ROUTINE_CLEAR -> s.routine();
            case INIT_INDEX_END -> s.word(TUNNEL_PATH_INDEX_END);
            case INIT_DATA -> s.longBits(TUNNEL_PATH_DATA);
            case INIT_TARGET_X,NEXT_TARGET_X -> s.word(TUNNEL_PATH_TARGET_X);
            case INIT_TARGET_Y,NEXT_TARGET_Y -> s.word(TUNNEL_PATH_TARGET_Y);
            case ACQUIRE_PUSH_CLEAR -> s.flags();
            case ACQUIRE_SCRATCH_CLEAR -> s.unsignedByte(TUNNEL_PATH_SCRATCH);
            case SPEED_STEPS_Y,SPEED_STEPS_X -> s.word(TUNNEL_PATH_STEPS);
            case MOVE_STEPS_HIGH -> s.unsignedByte(TUNNEL_PATH_STEPS_HIGH);
            case NEXT_INDEX -> s.unsignedByte(TUNNEL_PATH_INDEX);
            case RELEASE_ROUTINE_WORD -> s.word(TUNNEL_PATH_ROUTINE_WORD);
        };
    }
    /** Local immutable value construction for fixed semantic aliases, never external RAM replay. */
    private static CdObjectState objectValue(CdObjectState s,ObjectField field,int value) {
        return switch(field) {
            case INIT_MAPPINGS -> s.moveLong(MAPPINGS,value);
            case INIT_SPRITE_FLAGS -> s.moveByte(SPRITE_FLAGS,value);
            case INIT_PRIORITY -> s.moveByte(PRIORITY,value);
            case INIT_WIDTH -> s.moveByte(WIDTH,value);
            case INIT_TILE -> s.moveWord(TILE,value);
            case INIT_ROUTINE,ACQUIRE_ROUTINE,GOT_ROUTINE,RELEASE_ROUTINE_CLEAR -> s.moveByte(ROUTINE,value);
            case INIT_INDEX_END -> s.moveWord(TUNNEL_PATH_INDEX_END,value);
            case INIT_DATA -> s.moveLong(TUNNEL_PATH_DATA,value);
            case INIT_TARGET_X,NEXT_TARGET_X -> s.moveWord(TUNNEL_PATH_TARGET_X,value);
            case INIT_TARGET_Y,NEXT_TARGET_Y -> s.moveWord(TUNNEL_PATH_TARGET_Y,value);
            case ACQUIRE_PUSH_CLEAR -> s.moveByte(FLAGS,value);
            case ACQUIRE_SCRATCH_CLEAR -> s.moveByte(TUNNEL_PATH_SCRATCH,value);
            case SPEED_STEPS_Y,SPEED_STEPS_X -> s.moveWord(TUNNEL_PATH_STEPS,value);
            case MOVE_STEPS_HIGH -> s.moveByte(TUNNEL_PATH_STEPS_HIGH,value);
            case NEXT_INDEX -> s.moveByte(TUNNEL_PATH_INDEX,value);
            case RELEASE_ROUTINE_WORD -> s.moveWord(TUNNEL_PATH_ROUTINE_WORD,value);
        };
    }
    private static void fixedObjectOperation(ObjectField field,int old,int value) {
        boolean valid=switch(field) {
            case INIT_SPRITE_FLAGS -> value==4;
            case INIT_PRIORITY -> value==1;
            case INIT_WIDTH -> value==0x10;
            case INIT_TILE -> value==0x0541;
            case INIT_ROUTINE,ACQUIRE_ROUTINE,GOT_ROUTINE -> value==((old+2)&255);
            case ACQUIRE_PUSH_CLEAR -> value==(old&~0x20);
            case ACQUIRE_SCRATCH_CLEAR,RELEASE_ROUTINE_CLEAR -> value==0;
            case MOVE_STEPS_HIGH -> value==((old-1)&255);
            case NEXT_INDEX -> value==((old+4)&255);
            case RELEASE_ROUTINE_WORD -> value==2;
            case INIT_MAPPINGS,INIT_INDEX_END,INIT_DATA,INIT_TARGET_X,INIT_TARGET_Y,
                    SPEED_STEPS_Y,SPEED_STEPS_X,NEXT_TARGET_X,NEXT_TARGET_Y -> true;
        };
        if(!valid) throw new IllegalArgumentException("Not the fixed native Tunnel operation at "+field);
    }
    private static void fixedPlayerOperation(TunnelPlayerField field,int old,int value) {
        boolean valid=switch(field) {
            case HURT_ROUTINE -> old==4&&value==2;
            case HURT_TIMER -> value==0x78;
            case ACQUIRE_CONTROL -> value==0x81;
            case ACQUIRE_INVISIBLE -> value==(old|0x40);
            case ACQUIRE_ANIMATION -> value==2;
            case ACQUIRE_GVEL_BASE -> value==0x1000;
            case ACQUIRE_GVEL_RAISE -> old==0x1000&&(short)value>0x1000;
            case ACQUIRE_X_VELOCITY,ACQUIRE_Y_VELOCITY,ACQUIRE_JUMP_CLEAR,RELEASE_CONTROL_CLEAR -> value==0;
            case ACQUIRE_PUSH_CLEAR -> value==(old&~0x20);
            case ACQUIRE_AIR -> value==(old|2);
            case RELEASE_Y_MASK -> value==(old&0x7FF);
            case ACQUIRE_X_SNAP,ACQUIRE_Y_SNAP,SPEED_Y_X_VELOCITY,SPEED_Y_Y_VELOCITY,
                    SPEED_X_Y_VELOCITY,SPEED_X_X_VELOCITY,COMPLETE_X_SNAP,COMPLETE_Y_SNAP,MOVE_X_LONG,MOVE_Y_LONG -> true;
        };
        if(!valid) throw new IllegalArgumentException("Not the fixed native Tunnel operation at "+field);
    }
    /** Complete nonwrite preservation uses the borrowed pair, not a Disc or Solid projection. */
    private static final class PlayerProjection {
        final PlayerCut input; final List<PlayerStore> stores=new ArrayList<>();
        int x,y,xVelocity,yVelocity,groundVelocity,routine,flags,animation,jump,control,hurt;
        PlayerProjection(PlayerCut input) {
            this.input=input; var m=input.state().motion();
            x=m.centreX16_16(); y=m.centreY16_16(); xVelocity=m.xVelocity(); yVelocity=m.yVelocity();
            groundVelocity=m.groundVelocity(); routine=m.routine(); flags=m.flags(); animation=m.animation();
            jump=m.playerJump(); control=m.playerControl(); hurt=input.state().timers().hurt();
        }
        int raw(TunnelPlayerField field) {
            return switch(field) {
                case HURT_ROUTINE -> routine;
                case HURT_TIMER -> hurt;
                case ACQUIRE_CONTROL,ACQUIRE_INVISIBLE,RELEASE_CONTROL_CLEAR -> control;
                case ACQUIRE_ANIMATION -> animation;
                case ACQUIRE_GVEL_BASE,ACQUIRE_GVEL_RAISE -> groundVelocity&0xFFFF;
                case ACQUIRE_X_VELOCITY,SPEED_Y_X_VELOCITY,SPEED_X_X_VELOCITY -> xVelocity&0xFFFF;
                case ACQUIRE_Y_VELOCITY,SPEED_Y_Y_VELOCITY,SPEED_X_Y_VELOCITY -> yVelocity&0xFFFF;
                case ACQUIRE_PUSH_CLEAR,ACQUIRE_AIR -> flags;
                case ACQUIRE_JUMP_CLEAR -> jump;
                case ACQUIRE_X_SNAP,COMPLETE_X_SNAP -> x>>>16;
                case ACQUIRE_Y_SNAP,COMPLETE_Y_SNAP,RELEASE_Y_MASK -> y>>>16;
                case MOVE_X_LONG -> x;
                case MOVE_Y_LONG -> y;
            };
        }
        PlayerStore store(TunnelPlayerField field,int value) {
            var store=new PlayerStore(field,raw(field),value); stores.add(store); acceptValue(field,value); return store;
        }
        void acceptValue(TunnelPlayerField field,int value) {
            switch(field) {
                case HURT_ROUTINE -> routine=value;
                case HURT_TIMER -> hurt=value;
                case ACQUIRE_CONTROL,ACQUIRE_INVISIBLE,RELEASE_CONTROL_CLEAR -> control=value;
                case ACQUIRE_ANIMATION -> animation=value;
                case ACQUIRE_GVEL_BASE,ACQUIRE_GVEL_RAISE -> groundVelocity=(short)value;
                case ACQUIRE_X_VELOCITY,SPEED_Y_X_VELOCITY,SPEED_X_X_VELOCITY -> xVelocity=(short)value;
                case ACQUIRE_Y_VELOCITY,SPEED_Y_Y_VELOCITY,SPEED_X_Y_VELOCITY -> yVelocity=(short)value;
                case ACQUIRE_PUSH_CLEAR,ACQUIRE_AIR -> flags=value;
                case ACQUIRE_JUMP_CLEAR -> jump=value;
                case ACQUIRE_X_SNAP,COMPLETE_X_SNAP -> x=(value<<16)|(x&0xFFFF);
                case ACQUIRE_Y_SNAP,COMPLETE_Y_SNAP,RELEASE_Y_MASK -> y=(value<<16)|(y&0xFFFF);
                case MOVE_X_LONG -> x=value;
                case MOVE_Y_LONG -> y=value;
            }
        }
        CdPlayerBody.State stateValue() {
            var s=input.state(); var m=s.motion(); var timers=s.timers();
            var motion=new CdPlayerMotionState(x,y,xVelocity,yVelocity,groundVelocity,routine,m.angle(),flags,m.spriteFlags(),
                    m.xRadius(),m.yRadius(),animation,m.previousAnimation(),jump,m.charge(),m.moveLock(),m.stick(),control,
                    m.previousPrimaryAngle(),m.previousSecondaryAngle());
            return new CdPlayerBody.State(motion,s.animation(),s.header(),
                    new CdPlayerBody.Timers(hurt,timers.invincibility(),timers.speedShoes(),timers.reset()),
                    s.history(),s.rotationDistance(),s.objectLinks(),s.rotationAngle());
        }
        PlayerResult result() { return new PlayerResult(stateValue(),input.environment(),stores); }
    }
    private final class Cursor {
        final CdObjectPool pool; final int wholeIndex;
        final List<Effect> effects=new ArrayList<>();
        final CdObjectPlacementRuntime.Status placementStatus;
        CdAssetBytes savedFlags; CdObjectPool.Snapshot expectedPool; CdObjectState state; PlayerCut player;
        Cursor(CdObjectPool pool,CdObjectState state) {
            this.pool=pool; this.state=state; wholeIndex=state.identity().wholeIndex();
            var cut=placement.capture(); placementStatus=cut.status(); savedFlags=cut.savedFlags(); expectedPool=cut.pool();
            sourceInCut(expectedPool,state);
        }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        void returned(NativeCall call) { effects.add(new CallReturned(call)); }
    }
    private static UnsupportedTunnelPath unsupported(UnsupportedBoundary boundary,StopReason reason,Cursor c,int value) {
        return new UnsupportedTunnelPath(boundary,reason,c,value);
    }
    private static void byteValue(int value) {
        if((value&~255)!=0) throw new IllegalArgumentException("Expected unsigned native byte");
    }
    private static void wordValue(int value) {
        if((value&~65535)!=0) throw new IllegalArgumentException("Expected unsigned native word");
    }
    private static void rawValue(int width,int value) {
        if(width==8) byteValue(value); else if(width==16) wordValue(value);
    }
}
