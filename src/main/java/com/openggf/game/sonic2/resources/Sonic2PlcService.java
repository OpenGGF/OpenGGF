package com.openggf.game.sonic2.resources;

import com.openggf.data.Rom;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.resources.NemesisPlcPatternCounts;
import com.openggf.level.resources.NemesisPlcServiceQueue;
import com.openggf.level.resources.PlcParser;
import com.openggf.level.resources.PlcParser.PlcDefinition;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Sonic 2-owned façade for the ROM's logical Pattern Load Cue FIFO. */
public final class Sonic2PlcService implements PlcLifecycleService {
    private static final int SAFE_QUEUE_CAPACITY = 15;

    private final Rom rom;
    private final NemesisPlcServiceQueue queue;

    public Sonic2PlcService(Rom rom) {
        this(rom, new NemesisPlcServiceQueue());
    }

    Sonic2PlcService(Rom rom, NemesisPlcServiceQueue queue) {
        this.rom = Objects.requireNonNull(rom, "rom");
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    /** Models S2 {@code LoadPLC2}: replace idle waiting descriptors from one ROM PLC. */
    public void replaceQueued(int plcId) throws IOException {
        Submission submission = readSubmission(plcId);
        requireReplacementFits(submission.definition());
        queue.replaceQueued(submission.definition(), submission.patternCounts());
    }

    /** Models S2 {@code LoadPLC}: append every descriptor from one ROM PLC. */
    public void append(int plcId) throws IOException {
        Submission submission = readSubmission(plcId);
        requireAppendFits(submission.definition());
        queue.append(submission.definition(), submission.patternCounts());
    }

    /** Parses and capacity-checks an append without mutating the native FIFO. */
    public void preflightAppend(int plcId) throws IOException {
        prepareAppendBatch(plcId);
    }

    /** Parses and capacity-checks one ordered append batch without FIFO mutation. */
    public PreparedAppendBatch prepareAppendBatch(int... plcIds) throws IOException {
        List<Submission> submissions = new java.util.ArrayList<>(plcIds.length);
        int descriptorCount = occupiedDescriptorCount();
        for (int plcId : plcIds) {
            Submission submission = readSubmission(plcId);
            descriptorCount += submission.definition().entries().size();
            submissions.add(submission);
        }
        if (descriptorCount > SAFE_QUEUE_CAPACITY) {
            throw new IllegalStateException("Sonic 2 PLC queue cannot use the retail-retained sixteenth slot");
        }
        return new PreparedAppendBatch(List.copyOf(submissions));
    }

    /** Commits a fully preflighted append batch without further parsing or capacity checks. */
    public void appendPrepared(PreparedAppendBatch prepared) {
        for (Submission submission : prepared.submissions()) {
            queue.append(submission.definition(), submission.patternCounts());
        }
    }

    /** Models S2 {@code ClearPLC}; an active decoder is never interrupted. */
    public void clearQueued() {
        queue.clearQueued();
    }

    /** Models S2 {@code RunPLC_RAM}, which arms only the current FIFO head. */
    public void prepare() {
        queue.prepareHead();
    }

    /** Models S2's three-pattern ordinary level VBlank service. */
    public void serviceLevelVBlank() {
        queue.servicePatterns(3);
    }

    /** Models S2's six-pattern normal VBlank service. */
    public void serviceNormalVBlank() {
        queue.servicePatterns(6);
    }

    /** Returns whether either the active decoder or a waiting ROM PLC descriptor remains. */
    public boolean isBusy() {
        return queue.isBusy();
    }

    @Override
    public void serviceVBlank(PlcLifecyclePhase phase) {
        switch (phase) {
            case TITLE_SCREEN, LEVEL_SELECT, LEVEL_TITLE_CARD, PALETTE_FADE,
                    TWO_PLAYER_RESULTS -> serviceNormalVBlank();
            case ORDINARY_LEVEL, SPECIAL_STAGE, SPECIAL_STAGE_RESULTS, NORMAL_PAUSE ->
                    serviceLevelVBlank();
            case LAG, CREDITS_TEXT, CREDITS_DEMO, CREDITS_DEMO_FADE, ENDING,
                    POST_CREDITS, SPECIAL_STAGE_PAUSE -> {
                // The selected S2 VBlank handler does not service PLCs.
            }
        }
    }

    @Override
    public boolean hasPreparationBoundary(PlcLifecyclePhase phase) {
        return switch (phase) {
            case TITLE_SCREEN, LEVEL_TITLE_CARD, ORDINARY_LEVEL, PALETTE_FADE,
                    SPECIAL_STAGE, SPECIAL_STAGE_RESULTS, TWO_PLAYER_RESULTS -> true;
            case LAG, LEVEL_SELECT, CREDITS_TEXT, CREDITS_DEMO, CREDITS_DEMO_FADE,
                    ENDING, POST_CREDITS, NORMAL_PAUSE, SPECIAL_STAGE_PAUSE -> false;
        };
    }

    @Override
    public void prepareAfterLoop(PlcLifecyclePhase phase) {
        if (hasPreparationBoundary(phase)) {
            prepare();
        }
    }

    private Submission readSubmission(int plcId) throws IOException {
        validatePlcId(plcId);
        PlcDefinition definition = PlcParser.parse(rom, Sonic2Constants.ART_LOAD_CUES_ADDR, plcId);
        return new Submission(definition, NemesisPlcPatternCounts.derive(rom, definition));
    }

    private static void validatePlcId(int plcId) {
        if (plcId < 0 || plcId >= Sonic2Constants.ART_LOAD_CUES_ENTRY_COUNT) {
            throw new IllegalArgumentException("Sonic 2 PLC ID out of range: " + plcId
                    + " (expected 0-" + (Sonic2Constants.ART_LOAD_CUES_ENTRY_COUNT - 1) + ")");
        }
    }

    private void requireAppendFits(PlcDefinition definition) {
        if (occupiedDescriptorCount() + definition.entries().size() > SAFE_QUEUE_CAPACITY) {
            throw new IllegalStateException("Sonic 2 PLC queue cannot use the retail-retained sixteenth slot");
        }
    }

    private void requireReplacementFits(PlcDefinition definition) {
        if (definition.entries().size() > SAFE_QUEUE_CAPACITY) {
            throw new IllegalStateException("Sonic 2 PLC replacement exceeds safe queue capacity");
        }
    }

    private int occupiedDescriptorCount() {
        return queue.queuedEntryCount() + (queue.capture().activeEntry() == null ? 0 : 1);
    }

    private record Submission(PlcDefinition definition, List<Integer> patternCounts) {
    }

    public record PreparedAppendBatch(List<Submission> submissions) {
    }
}
