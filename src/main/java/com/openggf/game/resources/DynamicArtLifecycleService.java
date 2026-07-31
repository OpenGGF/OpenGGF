package com.openggf.game.resources;

import com.openggf.game.GameId;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.TileLoadRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Session-owned production lifecycle for player DPLC decisions.
 *
 * <p>The service derives facts only from runtime owner calls. It has no trace
 * parser, expected-event, or recorded-authority input.
 */
public final class DynamicArtLifecycleService
        implements DynamicArtDiagnosticsProvider,
        RewindSnapshottable<DynamicArtLifecycleService.RewindState> {

    public static final String REWIND_KEY = "dynamic-art-diagnostics";
    private static final Set<String> OWNERS = Set.of(
            "sonic", "tails", "tails-tails",
            "ss-sonic", "ss-tails", "ss-tails-tails");
    private static final Map<GameId, Map<String, ProductionArtProfile>>
            PLAYER_PROFILES = Map.of(
                    GameId.S1, Map.of(
                            "sonic", new ProductionArtProfile(
                                    Sonic1Constants.ART_UNC_SONIC_ADDR,
                                    0xF000, 0xC800, 0x2E0)),
                    GameId.S2, Map.of(
                            "sonic", new ProductionArtProfile(
                                    Sonic2Constants.ART_UNC_SONIC_ADDR,
                                    0xF000, -1, 0),
                            "tails", new ProductionArtProfile(
                                    Sonic2Constants.ART_UNC_TAILS_ADDR,
                                    0xF400, -1, 0),
                            "tails-tails", new ProductionArtProfile(
                                    Sonic2Constants.ART_UNC_TAILS_ADDR,
                                    0xF600, -1, 0)));

    private final Map<String, Integer> lastMappingFrames = new HashMap<>();
    private final LinkedHashMap<Long, Descriptor> ledger = new LinkedHashMap<>();
    private final List<BufferedEdge> bufferedEdges = new ArrayList<>();
    private final List<Long> pendingS2TransferIds = new ArrayList<>();
    private final List<DynamicArtGapTransition> gapTransitions = new ArrayList<>();
    private Preparation s1Preparation;
    private List<Long> publishedOutstanding = List.of();
    private DynamicArtDiagnosticsSnapshot latest =
            DynamicArtDiagnosticsSnapshot.empty();
    private long deliverySerial;
    private long segmentGeneration;
    private boolean runActive;
    private boolean comparisonSegmentOpen;
    private long nextTransferId;
    private long nextEdgeOrdinal;
    private int logicalFrame;
    private int nextLogicalEdgeIndex;
    private int nextPublicationFrame;
    private int movieLogicalFrame;
    private int nextGapEdgeIndex;

    private record ProductionArtProfile(
            int romArtBase,
            int vramDestination,
            int stagingRamAddress,
            int stagingByteLength) {
    }

    public record ArtUpdate(
            boolean mappingChanged,
            long transferId,
            List<TileLoadRequest> tileRequests) {
        public ArtUpdate {
            tileRequests = List.copyOf(tileRequests);
        }

        public boolean submitted() {
            return transferId >= 0;
        }
    }

    /** Native S2 entry class for the semantic owner decision. */
    public enum DecisionKind {
        NORMAL_OBJECT,
        DIRECT_PART2
    }

    public record Descriptor(
            long transferId,
            String owner,
            int mappingFrame,
            List<DynamicArtDiagnosticsSnapshot.Request> requests) {
        public Descriptor {
            requests = List.copyOf(requests);
        }
    }

    public record Preparation(
            String owner,
            int mappingFrame,
            List<DynamicArtDiagnosticsSnapshot.Request> requests) {
        public Preparation {
            requests = List.copyOf(requests);
        }
    }

    public record BufferedEdge(
            long edgeOrdinal,
            long transferId,
            String phase,
            String owner,
            int mappingFrame,
            int logicalFrame,
            int logicalEdgeIndex,
            List<DynamicArtDiagnosticsSnapshot.Request> requests) {
        public BufferedEdge {
            requests = List.copyOf(requests);
        }
    }

    public record RewindState(
            Map<String, Integer> lastMappingFrames,
            List<Descriptor> ledger,
            List<BufferedEdge> bufferedEdges,
            List<Long> pendingS2TransferIds,
            List<DynamicArtGapTransition> gapTransitions,
            List<Long> publishedOutstanding,
            int latestFrame,
            List<DynamicArtDiagnosticsSnapshot.Edge> latestEdges,
            List<Long> latestOutstandingTransferIds,
            boolean latestPublished,
            Preparation s1Preparation,
            boolean runActive,
            boolean comparisonSegmentOpen,
            long nextTransferId,
            long nextEdgeOrdinal,
            int logicalFrame,
            int nextLogicalEdgeIndex,
            int nextPublicationFrame,
            int movieLogicalFrame,
            int nextGapEdgeIndex) {
        public RewindState {
            lastMappingFrames = Map.copyOf(lastMappingFrames);
            ledger = List.copyOf(ledger);
            bufferedEdges = List.copyOf(bufferedEdges);
            pendingS2TransferIds = List.copyOf(pendingS2TransferIds);
            gapTransitions = List.copyOf(gapTransitions);
            publishedOutstanding = List.copyOf(publishedOutstanding);
            latestEdges = List.copyOf(latestEdges);
            latestOutstandingTransferIds =
                    List.copyOf(latestOutstandingTransferIds);
        }
    }

    public void beginRun() {
        if (runActive) {
            throw new IllegalStateException("dynamic-art run is already active");
        }
        resetState();
        runActive = true;
    }

    public void openComparisonSegment() {
        requireRunActive();
        if (comparisonSegmentOpen) {
            throw new IllegalStateException(
                    "dynamic-art comparison segment is already open");
        }
        if (!bufferedEdges.isEmpty()) {
            throw new IllegalStateException(
                    "cannot open dynamic-art segment with unpublished production work");
        }
        if (!ledger.isEmpty() && pendingS2TransferIds.isEmpty()) {
            throw new IllegalStateException(
                    "cannot open dynamic-art segment with pending production work");
        }
        comparisonSegmentOpen = true;
        segmentGeneration++;
        publishedOutstanding = List.copyOf(ledger.keySet());
        latest = new DynamicArtDiagnosticsSnapshot(
                -1, List.of(), publishedOutstanding,
                deliverySerial, segmentGeneration, false);
        logicalFrame = 0;
        nextLogicalEdgeIndex = 0;
        nextPublicationFrame = 0;
    }

    public void closeComparisonSegment() {
        requireComparisonSegmentOpen();
        if (!bufferedEdges.isEmpty()) {
            if (nextPublicationFrame == 0) {
                throw new IllegalStateException(
                        "cannot forward dynamic-art edges without a production row");
            }
            int terminalFrame = nextPublicationFrame - 1;
            // The forwarded edges land on a row that was already published,
            // so they extend that row rather than replacing it: the recorder
            // reports one row per publication frame carrying both its own
            // edges and anything the following main-loop iteration forwarded
            // back onto it.
            List<DynamicArtDiagnosticsSnapshot.Edge> alreadyPublished =
                    latest.published() && latest.frame() == terminalFrame
                            ? latest.edges()
                            : List.of();
            latest = publishBuffered(terminalFrame, true, alreadyPublished);
        }
        comparisonSegmentOpen = false;
    }

    public void finishRun() {
        requireRunActive();
        if (comparisonSegmentOpen) {
            closeComparisonSegment();
        }
        runActive = false;
    }

    public ArtUpdate observeRomDplc(
            String owner,
            int mappingFrame,
            List<TileLoadRequest> tileRequests,
            int romArtBase,
            int vramDestination) {
        return observeDplc(owner, mappingFrame, tileRequests,
                romArtBase, -1, vramDestination);
    }

    /**
     * Applies the typed per-game player-art profile to one ROM-loaded DPLC
     * decision. Unsupported games/owners remain on their existing renderer
     * path and emit no S1/S2 audit fact.
     */
    public ArtUpdate observePlayerDplc(
            GameId gameId,
            String owner,
            int mappingFrame,
            SpriteDplcFrame dplcFrame) {
        return observePlayerDplc(gameId, owner, mappingFrame, dplcFrame,
                DecisionKind.NORMAL_OBJECT);
    }

    public ArtUpdate observePlayerDplc(
            GameId gameId,
            String owner,
            int mappingFrame,
            SpriteDplcFrame dplcFrame,
            DecisionKind decisionKind) {
        requireRunActive();
        Objects.requireNonNull(decisionKind, "decisionKind");
        Map<String, ProductionArtProfile> gameProfiles =
                PLAYER_PROFILES.get(gameId);
        ProductionArtProfile profile =
                gameProfiles != null ? gameProfiles.get(owner) : null;
        if (profile == null) {
            return new ArtUpdate(false, -1, List.of());
        }
        List<TileLoadRequest> requests = dplcFrame != null
                && dplcFrame.requests() != null
                ? dplcFrame.requests() : List.of();
        if (gameId == GameId.S1) {
            return prepareS1(owner, mappingFrame, requests, profile);
        }
        ArtUpdate update = observeRomDplc(owner, mappingFrame, requests,
                profile.romArtBase(), profile.vramDestination());
        if (gameId == GameId.S2 && update.submitted()) {
            pendingS2TransferIds.add(update.transferId());
        }
        return update;
    }

    public void completePlayerDplc(
            GameId gameId,
            String owner,
            ArtUpdate update) {
        if (gameId == GameId.S1 || !update.submitted()) {
            return;
        }
        ProductionArtProfile profile =
                PLAYER_PROFILES.getOrDefault(gameId, Map.of()).get(owner);
        if (profile == null) {
            throw new IllegalArgumentException(
                    "missing player dynamic-art profile for "
                            + gameId + "/" + owner);
        }
        if (gameId == GameId.S2) {
            throw new IllegalStateException(
                    "S2 dynamic art completes only at the production VBlank boundary");
        }
        if (profile.stagingRamAddress() >= 0) {
            completeApplied(update,
                    List.of(DynamicArtDiagnosticsSnapshot.Request.ram(
                            profile.stagingRamAddress(),
                            profile.vramDestination(),
                            profile.stagingByteLength())));
        } else {
            completeApplied(update);
        }
    }

    public ArtUpdate observeRamDplc(
            String owner,
            int mappingFrame,
            List<TileLoadRequest> tileRequests,
            int ramArtBase,
            int vramDestination) {
        return observeDplc(owner, mappingFrame, tileRequests,
                -1, ramArtBase, vramDestination);
    }

    public ArtUpdate observeRamDplc(
            GameId gameId,
            String owner,
            int mappingFrame,
            List<TileLoadRequest> tileRequests,
            int ramArtBase,
            int vramDestination) {
        ArtUpdate update = observeRamDplc(owner, mappingFrame, tileRequests,
                ramArtBase, vramDestination);
        if (gameId == GameId.S2 && update.submitted()) {
            pendingS2TransferIds.add(update.transferId());
        }
        return update;
    }

    private ArtUpdate observeDplc(
            String owner,
            int mappingFrame,
            List<TileLoadRequest> tileRequests,
            int romArtBase,
            int ramArtBase,
            int vramDestination) {
        requireRunActive();
        validateOwner(owner);
        if (mappingFrame < 0) {
            throw new IllegalArgumentException("mappingFrame must be nonnegative");
        }
        List<TileLoadRequest> checked =
                List.copyOf(Objects.requireNonNull(tileRequests, "tileRequests"));
        Integer previous = lastMappingFrames.put(owner, mappingFrame);
        if (previous != null && previous == mappingFrame) {
            return new ArtUpdate(false, -1, List.of());
        }
        if (checked.isEmpty()) {
            return new ArtUpdate(true, -1, List.of());
        }

        List<DynamicArtDiagnosticsSnapshot.Request> requests =
                toDiagnosticRequests(checked, romArtBase, ramArtBase,
                        vramDestination);
        long transferId = nextTransferId++;
        List<Long> before = List.copyOf(ledger.keySet());
        Descriptor descriptor =
                new Descriptor(transferId, owner, mappingFrame, requests);
        ledger.put(transferId, descriptor);
        buffer(transferId, "submitted", owner, mappingFrame, requests, before);
        return new ArtUpdate(true, transferId, checked);
    }

    private ArtUpdate prepareS1(
            String owner,
            int mappingFrame,
            List<TileLoadRequest> tileRequests,
            ProductionArtProfile profile) {
        validateOwner(owner);
        if (mappingFrame < 0) {
            throw new IllegalArgumentException("mappingFrame must be nonnegative");
        }
        List<TileLoadRequest> checked =
                List.copyOf(Objects.requireNonNull(tileRequests, "tileRequests"));
        Integer previous = lastMappingFrames.put(owner, mappingFrame);
        if (previous != null && previous == mappingFrame) {
            return new ArtUpdate(false, -1, List.of());
        }
        if (checked.isEmpty()) {
            return new ArtUpdate(true, -1, List.of());
        }
        s1Preparation = new Preparation(owner, mappingFrame,
                toDiagnosticRequests(checked, profile.romArtBase(), -1,
                        profile.vramDestination()));
        return new ArtUpdate(true, -1, checked);
    }

    public void completeApplied(ArtUpdate update) {
        Objects.requireNonNull(update, "update");
        if (!update.submitted()) {
            return;
        }
        Descriptor descriptor = requirePending(update.transferId());
        completeApplied(update, descriptor.requests());
    }

    public void completeApplied(
            ArtUpdate update,
            List<DynamicArtDiagnosticsSnapshot.Request> completionRequests) {
        requireRunActive();
        Objects.requireNonNull(update, "update");
        if (!update.submitted()) {
            return;
        }
        Descriptor descriptor = requirePending(update.transferId());
        List<DynamicArtDiagnosticsSnapshot.Request> requests =
                List.copyOf(Objects.requireNonNull(
                        completionRequests, "completionRequests"));
        List<Long> before = List.copyOf(ledger.keySet());
        ledger.remove(update.transferId());
        buffer(update.transferId(), "completed", descriptor.owner(),
                descriptor.mappingFrame(), requests, before);
    }

    /** Retires the S2 DMA FIFO at the production ProcessDMAQueue boundary. */
    public void serviceProductionVBlank() {
        requireRunActive();
        if (s1Preparation != null) {
            Preparation preparation = s1Preparation;
            s1Preparation = null;
            long transferId = nextTransferId++;
            List<Long> before = List.copyOf(ledger.keySet());
            Descriptor descriptor = new Descriptor(
                    transferId, preparation.owner(),
                    preparation.mappingFrame(), preparation.requests());
            ledger.put(transferId, descriptor);
            buffer(transferId, "submitted", descriptor.owner(),
                    descriptor.mappingFrame(), descriptor.requests(), before);
            completeApplied(new ArtUpdate(true, transferId, List.of()),
                    List.of(DynamicArtDiagnosticsSnapshot.Request.ram(
                            PLAYER_PROFILES.get(GameId.S1).get("sonic")
                                    .stagingRamAddress(),
                            PLAYER_PROFILES.get(GameId.S1).get("sonic")
                                    .vramDestination(),
                            PLAYER_PROFILES.get(GameId.S1).get("sonic")
                                    .stagingByteLength())));
        }
        retireSubmittedTransfers();
    }

    /**
     * Retires already-submitted transfers at the V-blank of the main-loop
     * iteration that follows the last sampled frame
     * (docs/s2disasm/s2.asm:5091 WaitForVint reaching ProcessDMAQueue at
     * docs/s2disasm/s2.asm:1769). It deliberately does not flush art that a
     * display pass has only staged into a RAM buffer: that art is not queued
     * work yet (docs/s1disasm/_incObj/01 Sonic.asm:2392 Sonic_LoadGfx copies
     * tiles into v_sgfx_buffer and merely sets f_sonframechg; the transfer
     * itself is issued by the V-int at docs/s1disasm/sonic.asm:831), and the
     * boundary this models ends before that issue.
     */
    public void serviceTerminalProductionVBlank() {
        requireRunActive();
        retireSubmittedTransfers();
    }

    private void retireSubmittedTransfers() {
        List<Long> retiring = List.copyOf(pendingS2TransferIds);
        pendingS2TransferIds.clear();
        for (long transferId : retiring) {
            Descriptor descriptor = requirePending(transferId);
            completeApplied(new ArtUpdate(
                    true, transferId, List.of()), descriptor.requests());
        }
    }

    public DynamicArtDiagnosticsSnapshot publishRow(
            int publicationFrame,
            boolean lagged) {
        requireComparisonSegmentOpen();
        requirePublicationFrame(publicationFrame);
        // A ROM lag frame publishes a heartbeat with no edges, but the logical
        // row cursor still advances: VBlank still ran and dispatched its lag
        // handler (docs/s1disasm/sonic.asm:651-655 tst.b v_vblank_routine /
        // beq.s VBlank_Lag, :709 VBlank_Lag; docs/s2disasm/s2.asm:484,513,529
        // Vint_Lag) even though the main loop did not complete an iteration and
        // v_framecount was not bumped (docs/s1disasm/sonic.asm:2995-2999). The
        // logical frame is therefore the publication row index, not a count of
        // non-lag iterations.
        latest = lagged
                ? new DynamicArtDiagnosticsSnapshot(
                        publicationFrame, List.of(), publishedOutstanding,
                        ++deliverySerial, segmentGeneration, true)
                : publishBuffered(publicationFrame, false);
        logicalFrame++;
        nextLogicalEdgeIndex = 0;
        return latest;
    }

    public DynamicArtDiagnosticsSnapshot publishTerminal(int publicationFrame) {
        requireComparisonSegmentOpen();
        requirePublicationFrame(publicationFrame);
        latest = publishBuffered(publicationFrame, true);
        return latest;
    }

    /** Publishes one live/headless row from production phase state. */
    public void finishProductionIteration(boolean lagged) {
        requireRunActive();
        if (comparisonSegmentOpen) {
            publishRow(nextPublicationFrame++, lagged);
        }
        movieLogicalFrame++;
    }

    private DynamicArtDiagnosticsSnapshot publishBuffered(
            int publicationFrame,
            boolean terminalForwarded) {
        return publishBuffered(publicationFrame, terminalForwarded, List.of());
    }

    private DynamicArtDiagnosticsSnapshot publishBuffered(
            int publicationFrame,
            boolean terminalForwarded,
            List<DynamicArtDiagnosticsSnapshot.Edge> alreadyPublished) {
        List<DynamicArtDiagnosticsSnapshot.Edge> edges =
                new ArrayList<>(alreadyPublished.size() + bufferedEdges.size());
        edges.addAll(alreadyPublished);
        for (BufferedEdge edge : bufferedEdges) {
            edges.add(new DynamicArtDiagnosticsSnapshot.Edge(
                    edge.edgeOrdinal(), edge.transferId(), edge.phase(),
                    edge.owner(), edge.mappingFrame(), edge.logicalFrame(),
                    edge.logicalEdgeIndex(), publicationFrame,
                    terminalForwarded, edge.requests()));
        }
        bufferedEdges.clear();
        publishedOutstanding = List.copyOf(ledger.keySet());
        return new DynamicArtDiagnosticsSnapshot(
                publicationFrame, edges, publishedOutstanding,
                ++deliverySerial, segmentGeneration, true);
    }

    @Override
    public DynamicArtDiagnosticsSnapshot latestSnapshot() {
        return latest;
    }

    @Override
    public List<DynamicArtGapTransition> gapTransitions() {
        return List.copyOf(gapTransitions);
    }

    public List<DynamicArtGapTransition.GapEdge> gapEdges() {
        return gapTransitions.stream()
                .map(DynamicArtGapTransition::edge).toList();
    }

    @Override
    public String key() {
        return REWIND_KEY;
    }

    @Override
    public RewindState capture() {
        return new RewindState(lastMappingFrames,
                List.copyOf(ledger.values()), bufferedEdges,
                pendingS2TransferIds, gapTransitions,
                publishedOutstanding, latest.frame(), latest.edges(),
                latest.outstandingTransferIds(), latest.published(),
                s1Preparation, runActive,
                comparisonSegmentOpen,
                nextTransferId, nextEdgeOrdinal,
                logicalFrame, nextLogicalEdgeIndex, nextPublicationFrame,
                movieLogicalFrame, nextGapEdgeIndex);
    }

    @Override
    public void restore(RewindState snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        lastMappingFrames.clear();
        lastMappingFrames.putAll(snapshot.lastMappingFrames());
        ledger.clear();
        for (Descriptor descriptor : snapshot.ledger()) {
            ledger.put(descriptor.transferId(), descriptor);
        }
        bufferedEdges.clear();
        bufferedEdges.addAll(snapshot.bufferedEdges());
        pendingS2TransferIds.clear();
        pendingS2TransferIds.addAll(snapshot.pendingS2TransferIds());
        gapTransitions.clear();
        gapTransitions.addAll(snapshot.gapTransitions());
        publishedOutstanding = snapshot.publishedOutstanding();
        latest = new DynamicArtDiagnosticsSnapshot(
                snapshot.latestFrame(), snapshot.latestEdges(),
                snapshot.latestOutstandingTransferIds(), deliverySerial,
                segmentGeneration, snapshot.latestPublished());
        s1Preparation = snapshot.s1Preparation();
        runActive = snapshot.runActive();
        comparisonSegmentOpen = snapshot.comparisonSegmentOpen();
        nextTransferId = snapshot.nextTransferId();
        nextEdgeOrdinal = snapshot.nextEdgeOrdinal();
        logicalFrame = snapshot.logicalFrame();
        nextLogicalEdgeIndex = snapshot.nextLogicalEdgeIndex();
        nextPublicationFrame = snapshot.nextPublicationFrame();
        movieLogicalFrame = snapshot.movieLogicalFrame();
        nextGapEdgeIndex = snapshot.nextGapEdgeIndex();
    }

    @Override
    public void resetForMissingSnapshot() {
        resetState();
    }

    private void resetState() {
        lastMappingFrames.clear();
        ledger.clear();
        bufferedEdges.clear();
        pendingS2TransferIds.clear();
        gapTransitions.clear();
        s1Preparation = null;
        publishedOutstanding = List.of();
        latest = DynamicArtDiagnosticsSnapshot.unpublished(
                deliverySerial, segmentGeneration);
        runActive = false;
        comparisonSegmentOpen = false;
        nextTransferId = 0;
        nextEdgeOrdinal = 0;
        logicalFrame = 0;
        nextLogicalEdgeIndex = 0;
        nextPublicationFrame = 0;
        movieLogicalFrame = 0;
        nextGapEdgeIndex = 0;
    }

    public boolean isSegmentArmed() {
        return comparisonSegmentOpen;
    }

    public boolean isRunActive() {
        return runActive;
    }

    public boolean isComparisonSegmentOpen() {
        return comparisonSegmentOpen;
    }

    private void buffer(
            long transferId,
            String phase,
            String owner,
            int mappingFrame,
            List<DynamicArtDiagnosticsSnapshot.Request> requests,
            List<Long> beforeOutstanding) {
        long edgeOrdinal = nextEdgeOrdinal++;
        if (comparisonSegmentOpen) {
            bufferedEdges.add(new BufferedEdge(edgeOrdinal, transferId,
                    phase, owner, mappingFrame, logicalFrame,
                    nextLogicalEdgeIndex++, requests));
            return;
        }
        DynamicArtGapTransition.GapEdge edge =
                new DynamicArtGapTransition.GapEdge(
                        edgeOrdinal, transferId, phase, owner,
                mappingFrame, movieLogicalFrame, nextGapEdgeIndex++, requests);
        gapTransitions.add(new DynamicArtGapTransition(edge, beforeOutstanding,
                List.copyOf(ledger.keySet())));
    }

    private Descriptor requirePending(long transferId) {
        Descriptor descriptor = ledger.get(transferId);
        if (descriptor == null) {
            throw new IllegalStateException(
                    "unknown or completed dynamic-art transfer: " + transferId);
        }
        return descriptor;
    }

    private static List<DynamicArtDiagnosticsSnapshot.Request>
            toDiagnosticRequests(
                    List<TileLoadRequest> tileRequests,
                    int romArtBase,
                    int ramArtBase,
                    int vramDestination) {
        if ((romArtBase >= 0) == (ramArtBase >= 0)) {
            throw new IllegalArgumentException(
                    "dynamic art must select one production source domain");
        }
        List<DynamicArtDiagnosticsSnapshot.Request> requests =
                new ArrayList<>(tileRequests.size());
        int destination = vramDestination;
        for (TileLoadRequest tileRequest : tileRequests) {
            if (tileRequest == null || tileRequest.startTile() < 0
                    || tileRequest.count() <= 0) {
                throw new IllegalArgumentException(
                        "DPLC requests require nonnegative source and positive count");
            }
            int byteLength = Math.multiplyExact(tileRequest.count(), 0x20);
            int sourceOffset = Math.multiplyExact(tileRequest.startTile(), 0x20);
            if (romArtBase >= 0) {
                requests.add(DynamicArtDiagnosticsSnapshot.Request.rom(
                        Math.addExact(romArtBase, sourceOffset),
                        tileRequest.startTile(), destination, byteLength));
            } else {
                requests.add(DynamicArtDiagnosticsSnapshot.Request.ram(
                        Math.addExact(ramArtBase, sourceOffset),
                        destination, byteLength));
            }
            destination = (destination + byteLength) & 0xFFFF;
        }
        return List.copyOf(requests);
    }

    private void requireRunActive() {
        if (!runActive) {
            throw new IllegalStateException(
                    "dynamic-art production run is not active");
        }
    }

    private void requireComparisonSegmentOpen() {
        requireRunActive();
        if (!comparisonSegmentOpen) {
            throw new IllegalStateException(
                    "dynamic-art comparison segment is not open");
        }
    }

    private static void validateOwner(String owner) {
        if (!OWNERS.contains(owner)) {
            throw new IllegalArgumentException(
                    "unknown dynamic-art owner: " + owner);
        }
    }

    private static void requirePublicationFrame(int publicationFrame) {
        if (publicationFrame < 0) {
            throw new IllegalArgumentException(
                    "publicationFrame must be nonnegative");
        }
    }
}
