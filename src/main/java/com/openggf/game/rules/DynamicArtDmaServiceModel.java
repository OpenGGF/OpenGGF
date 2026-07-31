package com.openggf.game.rules;

import com.openggf.game.resources.PlcLifecyclePhase;

/**
 * Game-wide policy for claims that represent the player's dynamic-art DMA
 * service boundary.
 */
public enum DynamicArtDmaServiceModel {
    EVERY_CLAIM(true) {
        @Override
        public boolean services(PlcLifecyclePhase phase) {
            return phase != null;
        }
    },
    /**
     * S1 writes Sonic's staged DPLC art from the {@code f_sonframechg}-gated
     * {@code writeVRAM v_sgfx_buffer,ArtTile_Sonic*tile_size} that lives inside
     * each per-mode VBlank handler (docs/s1disasm/sonic.asm:829-833
     * VBlank_Levels, 890-894 VBlank_SpecialStage, 927-931 VBlank_TitleCards,
     * 985-989 VBlank_Paused -- so NORMAL_PAUSE stays a service boundary).
     * VBlank branches to VBlank_Lag before reaching any of them
     * (sonic.asm:652-655) and VBlank_Lag only runs the sound driver
     * (sonic.asm:709-715 -> VBlank_Music, sonic.asm:678-684), so
     * {@code f_sonframechg} stays set across a lag frame and the transfer lands
     * on the next real VBlank.
     */
    SONIC_1_VBLANK_SONIC_GFX(true) {
        @Override
        public boolean services(PlcLifecyclePhase phase) {
            return phase != null && phase != PlcLifecyclePhase.LAG;
        }
    },
    SONIC_2_PROCESS_DMA_QUEUE(true) {
        @Override
        public boolean services(PlcLifecyclePhase phase) {
            if (phase == null) {
                return false;
            }
            return switch (phase) {
                case ORDINARY_LEVEL, SPECIAL_STAGE, SPECIAL_STAGE_RESULTS,
                        TWO_PLAYER_RESULTS, CREDITS_TEXT, CREDITS_DEMO,
                        ENDING, POST_CREDITS, NORMAL_PAUSE,
                        SPECIAL_STAGE_PAUSE, LAG -> true;
                case TITLE_SCREEN, LEVEL_SELECT, LEVEL_TITLE_CARD,
                        PALETTE_FADE, CREDITS_DEMO_FADE -> false;
            };
        }
    },
    EVERY_CLAIM_WITHOUT_PLAYER_ART_AUDIT(false) {
        @Override
        public boolean services(PlcLifecyclePhase phase) {
            return phase != null;
        }
    };

    private final boolean supportsPlayerDynamicArtAudit;

    DynamicArtDmaServiceModel(boolean supportsPlayerDynamicArtAudit) {
        this.supportsPlayerDynamicArtAudit = supportsPlayerDynamicArtAudit;
    }

    public abstract boolean services(PlcLifecyclePhase phase);

    public boolean supportsPlayerDynamicArtAudit() {
        return supportsPlayerDynamicArtAudit;
    }
}
