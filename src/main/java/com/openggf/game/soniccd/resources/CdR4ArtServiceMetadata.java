package com.openggf.game.soniccd.resources;

import java.util.List;

/** Source-derived art service metadata only, not loaders/decoders or proof of current patterns.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: r4/stage_gfx_animate_1a.asm:3-95;
 * other nine stage_gfx_animate suffixes:3-4. Root/data lane binds the original media scripts/pointers.
 * Current art must remain distinct from OLD selected CPU SAT. Unknown residency stays unknown;
 * Root reuses CdPatternResidency/section/player kernels and their masks, never fabricated pixels.
 */
public final class CdR4ArtServiceMetadata {
    private CdR4ArtServiceMetadata() { }
    public enum Strategy { ACT1_PRESENT_WINDOW, POWERUP_ONLY }
    /** One native script call; DBF#$7F copies128 LONGs to the same staging allocation.
     * Destination is a semantic pattern-byte offset, not a hardware protocol. */
    public record AnimationWindow(int scriptOrdinal,int destination,int bytes) { }
    private static final List<AnimationWindow> UPPER=List.of(new AnimationWindow(0,0x4500,512),
            new AnimationWindow(1,0x4700,512),new AnimationWindow(2,0x4900,512));
    public record Eligibility(boolean loadPowerupArt,List<AnimationWindow> orderedScripts) {
        public Eligibility {orderedScripts=List.copyOf(orderedScripts);}
    }
    /** VBlank WORD time_stop gate is external; R41A alone tests paused before even powerup art.
     * Other nine suffixes jump directly to LoadPowerupArt, including while paused. */
    public static Eligibility stageAnimation(Strategy strategy,int timeStopWord,int pausedByte,int cameraX,int cameraY) {
        java.util.Objects.requireNonNull(strategy);word(timeStopWord);octet(pausedByte);word(cameraX);word(cameraY);
        if(timeStopWord!=0||strategy==Strategy.ACT1_PRESENT_WINDOW&&pausedByte!=0)return new Eligibility(false,List.of());
        return new Eligibility(true,strategy==Strategy.ACT1_PRESENT_WINDOW&&cameraX<0x280&&cameraY<0x160?UPPER:List.of());
    }
    private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
}
