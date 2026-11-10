package com.openggf.game.soniccd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Finite ordering metadata for Root's future water-stage service producer, NOT a scheduler.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, r4/main.asm VBlankStage:1078-1157,
 * VBlankDispatchDone:964-973, HBlank/HBlankUpdateStage:1316-1374.
 * No method executes an action, creates an operation, publishes state or authenticates completion.
 * Root must bind each reached action to its original current private invocation/source/pool and full
 * State/Environment; prepare all fallible adoption work first and retain reached failure prefixes.
 * Mid-service capture is unqualified; these values must NEVER be used to replay an operation.
 */
public final class CdR4StageServicePlan {
    private CdR4StageServicePlan() { }
    public enum Action {
        FLUSH_FM, READ_JOYPADS, UPDATE_WATER, SELECT_INITIAL_PALETTE,
        ARM_WATER_SPLIT, SELECT_OLD_CPU_SAT, COPY_HSCROLL_PREFIX,
        LOAD_PLAYER_GFX, PUBLISH_PLAYER_GFX_IF_DIRTY, ANIMATE_STAGE_GFX,
        COPY_CAMERAS_32_BYTES, COPY_FLAGS_8_BYTES, SET_DEFERRED_BYTE,
        DRAW_PLANES, SLOW_PLC, HUD, BORED_TIMER, WARP_TIMER, INCREMENT_VBLANK_LONG,
        CLEAR_SPLIT_ARM_WORD, SELECT_CURRENT_WATER_PALETTE, CLEAR_DEFERRED_BYTE
    }
    private static final List<Action> DRAW=List.of(Action.DRAW_PLANES,Action.SLOW_PLC,Action.HUD);
    /** Comparison-only continuation description; no operation or replay authority. */
    public record DeferredMetadata(List<Action> afterWaterPalette) {
        public DeferredMetadata {afterWaterPalette=List.copyOf(afterWaterPalette);if(!afterWaterPalette.equals(DRAW))throw new IllegalArgumentException("Only native stage draw tail");}
    }
    public record VblankPlan(List<Action> beforeFinish,List<Action> finish,java.util.Optional<DeferredMetadata> deferred) {
        public VblankPlan {beforeFinish=List.copyOf(beforeFinish);finish=List.copyOf(finish);Objects.requireNonNull(deferred);}
    }
    /** Input is B's reached immutable UpdateWater output; no second water producer here.
     * Dirty split bytes use unsigned CMP.B, independently of fullWaterByte. */
    public static VblankPlan stage(CdR4WaterPresentation.WaterProjection water,int timeStopWord,int pausedByte) {
        Objects.requireNonNull(water);word(timeStopWord);octet(pausedByte);
        var prefix=new ArrayList<>(List.of(Action.FLUSH_FM,Action.READ_JOYPADS,Action.UPDATE_WATER,
                Action.SELECT_INITIAL_PALETTE,Action.ARM_WATER_SPLIT,Action.COPY_HSCROLL_PREFIX,
                Action.SELECT_OLD_CPU_SAT,Action.LOAD_PLAYER_GFX,Action.PUBLISH_PLAYER_GFX_IF_DIRTY));
        if(timeStopWord==0)prefix.add(Action.ANIMATE_STAGE_GFX);
        prefix.add(Action.COPY_CAMERAS_32_BYTES);prefix.add(Action.COPY_FLAGS_8_BYTES);
        boolean deferred=water.splitByte()<0x60;
        if(deferred)prefix.add(Action.SET_DEFERRED_BYTE);else prefix.addAll(DRAW);
        var finish=new ArrayList<Action>();if(pausedByte==0){finish.add(Action.BORED_TIMER);finish.add(Action.WARP_TIMER);}finish.add(Action.INCREMENT_VBLANK_LONG);
        return new VblankPlan(prefix,finish,deferred?java.util.Optional.of(new DeferredMetadata(DRAW)):java.util.Optional.empty());
    }
    public record HblankPlan(List<Action> actions,int splitArmWordAfter,int deferredByteAfter) {
        public HblankPlan {actions=List.copyOf(actions);word(splitArmWordAfter);octet(deferredByteAfter);}
    }
    /** The water palette is read CURRENT at HBlank, not the earlier selected/initial palette image.
     * $DF/zero-arm returns retain a dirty deferred byte. Clearing it early loses the source prefix. */
    public static HblankPlan waterBoundary(int splitArmWord,int splitByte,int deferredByte) {
        word(splitArmWord);octet(splitByte);octet(deferredByte);
        if(splitArmWord==0)return new HblankPlan(List.of(),splitArmWord,deferredByte);
        var actions=new ArrayList<Action>();actions.add(Action.CLEAR_SPLIT_ARM_WORD);
        if(splitByte==0xDF)return new HblankPlan(actions,0,deferredByte);
        actions.add(Action.SELECT_CURRENT_WATER_PALETTE);
        if(deferredByte!=0){actions.add(Action.CLEAR_DEFERRED_BYTE);actions.addAll(DRAW);}
        return new HblankPlan(actions,0,0);
    }
    /** Explicit proposed capture boundary. Root must enforce this against its own actual continuation,
     * not trust a caller-supplied byte as evidence that capture is safe. */
    public static void requireNoDeferredCapture(int deferredByte){octet(deferredByte);if(deferredByte!=0)throw new UnsupportedOperationException("TT deferred stage service capture requires the existing Frame owner's original continuation");}
    private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
}
