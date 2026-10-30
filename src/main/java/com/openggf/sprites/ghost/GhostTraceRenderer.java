package com.openggf.sprites.ghost;

import com.openggf.sprites.art.PlayerSpriteArtProvider;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.physics.Direction;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceReplayBootstrap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Diagnostic trace ghosts with isolated sidekick-style DPLC banks. Admitted
 * comparison rows own visual animation; drawing only reads the prepared pose.
 */
public final class GhostTraceRenderer {
    private static final Logger LOGGER = Logger.getLogger(GhostTraceRenderer.class.getName());
    private static final int FULL_OPACITY_DISTANCE = 32;

    private final Map<String, GhostSlot> slots = new HashMap<>();
    private TraceData visualTrace;
    private TraceFrame visualFrame;
    private int consumedRows;
    // Presentation checkpoints only: captured from our unregistered copies, never
    // the live team. At most one entry per trace row and two native character slots.
    // Keeping the owner's snapshot (without follower history) also retains private
    // controller state rather than guessing which animation fields matter next.
    private List<VisualCheckpoint> visualHistory = new ArrayList<>();
    private int validHistoryRows;
    private boolean visualStateAvailable;
    private boolean missingHistoryReported;
    private LevelManager artLevel;
    private long artLoadGeneration;

    /**
     * Receives admitted comparator rows, never window pictures. A forward row runs
     * Animate once against the production context at that boundary. Seeks restore
     * the private presentation snapshot taken there, without calling Animate under
     * the destination's (possibly different) water/gravity context.
     *
     * <p>Each seek restores at most two owner snapshots: O(1) in prefix length,
     * including held backward stepping. Storage is O(observed trace rows), bounded
     * by this trace's row count; session/fixture/load replacement retires it. A
     * resumed forward branch overwrites history and invalidates its old future in
     * O(1). Unknown history is hidden, never rebuilt with invented context.
     */
    public void synchronizeVisualRows(TraceData trace, int rows, boolean discontinuity) {
        LevelManager level = GameServices.levelOrNull();
        long generation = level != null ? level.getCompletedProductionLoadGeneration() : -1;
        boolean reloaded = artLevel != level || artLoadGeneration != generation;
        int end = Math.max(0, Math.min(rows, trace.frameCount()));
        if (reloaded || visualTrace != trace) {
            slots.clear();
            clearVisualHistory();
            artLevel = level;
            artLoadGeneration = generation;
        } else if (end == 0 && (discontinuity || consumedRows > 0)) {
            // A fresh observer at cursor zero starts a new presentation lifetime.
            slots.replaceAll((key, slot) -> createSlot(slot.code(), slot.art(), slot.renderer()));
            clearVisualHistory();
        }
        visualTrace = trace;
        if (end == 0) return;

        if (discontinuity || end < consumedRows) {
            if (restoreVisualRow(end)) return;
            // Row zero has no prior private script to reconstruct. It can be
            // initialized at the actual initial boundary, including bind-at-one.
            if (end != 1) {
                missingVisualHistory(end);
                return;
            }
            slots.replaceAll((key, slot) -> createSlot(slot.code(), slot.art(), slot.renderer()));
            clearVisualHistory();
        }
        if (end == consumedRows) return;
        if (end != consumedRows + 1 || (consumedRows > 0 && !visualStateAvailable)) {
            missingVisualHistory(end);
            return;
        }
        TraceFrame row = trace.getFrame(end - 1);
        TraceFrame previous = end > 1 ? trace.getFrame(end - 2) : null;
        TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, previous, row);
        boolean animate = phase == TraceExecutionPhase.FULL_LEVEL_FRAME
                || phase == TraceExecutionPhase.FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD
                || phase == TraceExecutionPhase.PLAYABLE_ANIMATION_ONLY;
        List<String> recorded = trace.metadata().recordedCharacters();
        prepareCharacter("main", recorded.isEmpty() ? "sonic" : recorded.getFirst(),
                row.primaryCharacterState(), row, row.input(), animate);
        prepareCharacter("sidekick0", recorded.size() > 1 ? recorded.get(1) : "tails",
                row.sidekick(), row, 0,
                animate && phase != TraceExecutionPhase.FULL_LEVEL_FRAME_WITH_SIDEKICK_ANIMATION_HELD);
        var checkpoint = new VisualCheckpoint(slots.entrySet().stream()
                .map(entry -> new SlotCheckpoint(entry.getKey(), entry.getValue(),
                        entry.getValue().sprite().captureRewindState(false)))
                .toList());
        if (end <= visualHistory.size()) visualHistory.set(end - 1, checkpoint);
        else visualHistory.add(checkpoint);
        validHistoryRows = end;
        consumedRows = end;
        visualFrame = row;
        visualStateAvailable = true;
    }

    private boolean restoreVisualRow(int end) {
        if (end > validHistoryRows) return false;
        slots.clear();
        for (SlotCheckpoint checkpoint : visualHistory.get(end - 1).slots()) {
            checkpoint.slot().sprite().restoreRewindState(checkpoint.state());
            slots.put(checkpoint.key(), checkpoint.slot());
        }
        consumedRows = end;
        visualFrame = visualTrace.getFrame(end - 1);
        visualStateAvailable = true;
        return true;
    }

    private void missingVisualHistory(int end) {
        consumedRows = end;
        visualFrame = null;
        visualStateAvailable = false;
        if (!missingHistoryReported) {
            LOGGER.warning("Trace ghost hidden: historical presentation context was not observed. "
                    + "Replay from the start to establish visual history.");
            missingHistoryReported = true;
        }
    }

    private void clearVisualHistory() {
        visualHistory = new ArrayList<>();
        validHistoryRows = 0;
        consumedRows = 0;
        visualFrame = null;
        visualStateAvailable = false;
        missingHistoryReported = false;
    }

    /** Whether the current row has an observed private presentation checkpoint. */
    public boolean isVisualStateAvailable() {
        return visualStateAvailable;
    }

    private void prepareCharacter(String slotId, String code, TraceCharacterState state,
                                  TraceFrame row, int input, boolean animate) {
        String key = slotId + ":" + normalizeCode(code);
        if (state == null || !state.present()) {
            GhostSlot old = slots.get(key);
            if (old != null) old.sprite().setNativeSlotPresent(false);
            return;
        }
        GhostSlot slot = slotFor(slotId, code);
        if (slot == null) return;
        if (!slot.sprite().isNativeSlotPresent()) {
            slot = createSlot(slot.code(), slot.art(), slot.renderer());
            slots.put(key, slot);
        }
        hydrateVisualState(slot.sprite(), state, input);
        if (animate) {
            int levelClock = row.gameplayFrameCounter() >= 0 ? row.gameplayFrameCounter() : row.frame();
            slot.sprite().getAnimationManager().update(levelClock);
        }
    }

    /** Retires diagnostic history at session teardown; the level owns pattern storage. */
    public void reset() {
        slots.clear();
        visualTrace = null;
        clearVisualHistory();
        artLevel = null;
    }

    public void render(AbstractPlayableSprite realMain,
                       List<AbstractPlayableSprite> realSidekicks) {
        renderForLayer(realMain, realSidekicks, Integer.MIN_VALUE, false, false);
    }

    public void renderForLayer(AbstractPlayableSprite realMain,
                               List<AbstractPlayableSprite> realSidekicks,
                               int bucket,
                               boolean highPriority) {
        renderForLayer(realMain, realSidekicks, bucket, highPriority, true);
    }

    private void renderForLayer(AbstractPlayableSprite realMain,
                                List<AbstractPlayableSprite> realSidekicks,
                                int bucket,
                                boolean highPriority,
                                boolean filterLayer) {
        if (visualTrace == null || visualFrame == null || realMain == null || artLevel == null
                || artLevel != GameServices.levelOrNull()
                || artLoadGeneration != artLevel.getCompletedProductionLoadGeneration()) {
            return;
        }
        TraceFrame frame = visualFrame;
        List<String> recorded = visualTrace.metadata().recordedCharacters();
        String mainCode = recorded.isEmpty() ? realMain.getCode() : recorded.getFirst();
        renderCharacter("main", mainCode, frame.primaryCharacterState(), realMain,
                bucket, highPriority, filterLayer);

        TraceCharacterState sidekickState = frame.sidekick();
        if (sidekickState == null || !sidekickState.present()) {
            return;
        }
        AbstractPlayableSprite realSidekick = realSidekicks != null && !realSidekicks.isEmpty()
                ? realSidekicks.getFirst()
                : null;
        String sidekickCode = recorded.size() > 1 ? recorded.get(1)
                : realSidekick != null ? realSidekick.getCode() : "tails";
        renderCharacter("sidekick0", sidekickCode, sidekickState, realSidekick,
                bucket, highPriority, filterLayer);
    }

    private void renderCharacter(String slotId, String characterCode, TraceCharacterState state,
                                 AbstractPlayableSprite realSprite,
                                 int bucket, boolean highPriority, boolean filterLayer) {
        if (state == null || !state.present() || realSprite == null) {
            return;
        }
        if (filterLayer && !GhostLayerFilter.matches(
                bucket, highPriority, realSprite.getPriorityBucket(), realSprite.isHighPriority())) {
            return;
        }
        int dx = (state.x() & 0xFFFF) - (realSprite.getCentreX() & 0xFFFF);
        int dy = (state.y() & 0xFFFF) - (realSprite.getCentreY() & 0xFFFF);
        float alpha = GhostOpacityCalculator.alphaForDistance(dx, dy, FULL_OPACITY_DISTANCE);
        if (alpha <= 0.0f) {
            return;
        }
        GhostSlot slot = slots.get(slotId + ":" + normalizeCode(characterCode));
        if (slot == null) {
            return;
        }

        GraphicsManager graphics = GameServices.graphics();
        graphics.flushPatternBatch();
        boolean previousHighPriority = graphics.getCurrentSpriteHighPriority();
        graphics.setCurrentSpriteHighPriority(realSprite.isHighPriority());
        try {
            graphics.beginGhostRenderEffect(alpha);
            graphics.beginPatternBatch();
            slot.renderer().drawFrame(
                    slot.sprite().getMappingFrame(),
                    state.x(),
                    state.y(),
                    slot.sprite().getRenderHFlip(),
                    slot.sprite().getRenderVFlip());
        } finally {
            try {
                graphics.flushPatternBatch();
            } finally {
                try {
                    graphics.endGhostRenderEffect();
                } finally {
                    graphics.setCurrentSpriteHighPriority(previousHighPriority);
                }
            }
        }
    }

    private GhostSlot slotFor(String slotId, String characterCode) {
        String normalizedCode = normalizeCode(characterCode);
        String key = slotId + ":" + normalizedCode;
        GhostSlot existing = slots.get(key);
        if (existing != null) {
            return existing;
        }
        LevelManager level = GameServices.levelOrNull();
        if (level == null || !(level.getGame() instanceof PlayerSpriteArtProvider artProvider)) {
            return null;
        }
        try {
            SpriteArtSet sourceArt = artProvider.loadPlayerSpriteArt(normalizedCode);
            if (sourceArt == null || sourceArt.isEmpty() || sourceArt.bankSize() <= 0) {
                return null;
            }
            int bankBase = level.reserveSidekickPatternBank(sourceArt.bankSize());
            SpriteArtSet ghostArt = GhostArtBankAllocator.shiftToGhostBank(sourceArt, bankBase);
            GhostSlot slot = createSlot(normalizedCode, ghostArt, new PlayerSpriteRenderer(ghostArt));
            slots.put(key, slot);
            return slot;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to create trace ghost for " + characterCode, e);
            return null;
        }
    }

    private static GhostSlot createSlot(String code, SpriteArtSet art, PlayerSpriteRenderer renderer) {
        AbstractPlayableSprite sprite = createVisualSprite(code);
        sprite.setSpriteRenderer(renderer);
        sprite.setAnimationFrameCount(art.mappingFrames().size());
        sprite.setAnimationProfile(art.animationProfile());
        sprite.setAnimationSet(art.animationSet());
        return new GhostSlot(sprite, renderer, code, art);
    }

    private static AbstractPlayableSprite createVisualSprite(String characterCode) {
        return switch (normalizeCode(characterCode)) {
            case "tails" -> new Tails("tails", (short) 0, (short) 0);
            case "knuckles" -> new Knuckles("knuckles", (short) 0, (short) 0);
            default -> new Sonic("sonic", (short) 0, (short) 0);
        };
    }

    private static void hydrateVisualState(AbstractPlayableSprite sprite, TraceCharacterState state, int inputMask) {
        sprite.setDirection((state.statusByte() & 0x01) != 0 ? Direction.LEFT : Direction.RIGHT);
        sprite.setRolling(state.rolling());
        sprite.setAir(state.air());
        sprite.setGroundMode(groundMode(state.groundMode()));
        sprite.setCentreXPreserveSubpixel(state.x());
        sprite.setCentreYPreserveSubpixel(state.y());
        sprite.setSubpixelRaw(state.xSub(), state.ySub());
        sprite.setXSpeed(state.xSpeed());
        sprite.setYSpeed(state.ySpeed());
        sprite.setGSpeed(state.gSpeed());
        sprite.setAngle(state.angle());
        sprite.setMovementInputActive(movementInputActive(inputMask, state));
        sprite.setObjectMappingFrameControl(false);
    }

    private static boolean movementInputActive(int inputMask, TraceCharacterState state) {
        int horizontal = AbstractPlayableSprite.INPUT_LEFT | AbstractPlayableSprite.INPUT_RIGHT;
        return (inputMask & horizontal) != 0 || state.gSpeed() != 0 || state.xSpeed() != 0;
    }

    private static GroundMode groundMode(int ordinal) {
        GroundMode[] values = GroundMode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return GroundMode.GROUND;
        }
        return values[ordinal];
    }

    private static String normalizeCode(String characterCode) {
        if (characterCode == null || characterCode.isBlank()) {
            return "sonic";
        }
        return characterCode.trim().toLowerCase(Locale.ROOT);
    }

    private record VisualCheckpoint(List<SlotCheckpoint> slots) { }

    private record SlotCheckpoint(String key, GhostSlot slot, PerObjectRewindSnapshot state) { }

    private record GhostSlot(AbstractPlayableSprite sprite, PlayerSpriteRenderer renderer,
                             String code, SpriteArtSet art) {
    }
}
