package com.openggf.game.sonic1.events;

import com.openggf.camera.Camera;
import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.sonic1.resources.Sonic1PlcService;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.function.Supplier;
import java.io.IOException;

/**
 * Base class for Sonic 1 per-zone dynamic level events.
 * Each zone has its own event routine counter (ROM: v_dle_routine)
 * that tracks progress through Act 3 boss sequences.
 */
abstract class Sonic1ZoneEvents {
    protected int eventRoutine;
    private Integer pendingPlcId;

    Sonic1ZoneEvents() {
    }

    /**
     * Returns the current Camera singleton. Always call this accessor rather
     * than caching the reference, so it survives singleton replacement.
     */
    protected Camera camera() {
        return GameServices.camera();
    }

    protected LevelManager levelManager() {
        return GameServices.level();
    }

    protected AudioManager audio() {
        return GameServices.audio();
    }

    protected GameStateManager gameState() {
        return GameServices.gameState();
    }

    protected ZoneLayoutMutationPipeline mutationPipeline() {
        return GameServices.zoneLayoutMutationPipeline();
    }

    protected <T> T gameService(Class<T> type) {
        return GameServices.module().getGameService(type);
    }

    /** Services rejected one-shot work before re-entering an event state machine. */
    protected boolean retryPendingPlc() {
        if (pendingPlcId == null) return false;
        if (publishSonic1Plc(pendingPlcId)) pendingPlcId = null;
        return true;
    }

    /** Submits an S1 {@code AddPLC} cue while preserving the eager object-art path. */
    protected boolean requestSonic1Plc(int plcId) {
        if (publishSonic1Plc(plcId)) return true;
        pendingPlcId = plcId;
        return false;
    }

    private boolean publishSonic1Plc(int plcId) {
        try {
            Sonic1PlcService plcService = gameService(Sonic1PlcService.class);
            if (plcService != null) {
                plcService.append(plcId);
            }
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    static AbstractPlayableSprite focusedSpriteOrNull() {
        try {
            return GameServices.camera().getFocusedSprite();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    /** Reset event state for a new level. */
    void init() {
        eventRoutine = 0;
        pendingPlcId = null;
    }

    /** Run per-frame event logic for the given act. */
    abstract void update(int act);

    protected <T extends ObjectInstance> T spawnObject(Supplier<T> factory) {
        LevelManager lm = levelManager();
        if (lm == null || lm.getObjectManager() == null) {
            return null;
        }
        return lm.getObjectManager().createDynamicObject(factory);
    }

    int getEventRoutine() {
        return eventRoutine;
    }

    void setEventRoutine(int routine) {
        this.eventRoutine = routine;
    }
}
