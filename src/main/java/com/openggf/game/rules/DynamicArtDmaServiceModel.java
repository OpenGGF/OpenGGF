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
