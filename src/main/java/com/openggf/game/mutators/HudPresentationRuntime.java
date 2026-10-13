package com.openggf.game.mutators;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameplayObservation;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.level.objects.HudProfileAccess;
import com.openggf.mods.mutators.HudPresentationPolicy;
import com.openggf.sprites.managers.SpriteManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** World-owned, presentation-clock HUD motion. Native publication remains comparison-free and unchanged. */
public final class HudPresentationRuntime implements WorldSessionPolicyState,
        RewindSnapshottable<HudPresentationRuntime.State> {
    private static final double MARGIN = 4;
    private static final double ROW_GAP = 2;
    private static final double ORBIT_RADIUS_X = 32, ORBIT_RADIUS_Y = 24;
    private static final double EVADE_SPEED_LIMIT = 4;
    private final WorldSession world;
    private final Object snapshotOwner = new Object();
    private String owner = "";
    private long activation;
    private HudPresentationPolicy policy;
    private long presentations;
    private int random;
    private int width = 320, height = 224;
    private List<Pair> pairs = List.of();
    private List<Integer> order = List.of();
    private Plaque plaque;
    private boolean retired;

    public HudPresentationRuntime(WorldSession world) { this.world = Objects.requireNonNull(world, "world"); }

    /** Native label/value geometry, before rows are stacked inside the compact plaque. */
    public record Readout(int id, int labelX, int labelY, int labelWidth, int labelHeight,
                          int valueY, int valueWidth, int valueHeight) {
        public int top() { return Math.min(labelY, valueY); }
        public int compactWidth() { return labelWidth + (labelWidth == 0 ? 0 : 4) + valueWidth; }
        public int compactHeight() { return Math.max(labelY + labelHeight, valueY + valueHeight) - top(); }
    }
    public record Pair(Readout readout, double x, double y, double vx, double vy, double scale) { }
    public record Plaque(double x, double y, double vx, double vy, double width, double height, double scale) { }
    public record State(Object snapshotOwner, String owner, long activation, HudPresentationPolicy policy,
                        long presentations, int random, int width, int height, List<Pair> pairs, List<Integer> order,
                        Plaque plaque) {
        public State { pairs = List.copyOf(pairs); order = List.copyOf(order); }
    }

    public boolean active() { return !retired && policy != null; }
    public boolean tags() {
        return !retired && TransientMutatorPolicyAccess.entries(world).stream()
                .anyMatch(e -> e.remainingPresentations() > 0 && e.policy() instanceof HudPresentationPolicy);
    }
    public boolean hidden() { return active() && policy.mode() == HudPresentationPolicy.Mode.HIDE; }
    public Pair pair(int id) {
        if (!active()) return null;
        for (Pair pair : pairs) if (pair.readout().id() == id) return pair;
        return null;
    }

    @Override public void onPresentation(GameplayModeContext context, LogicalInputSnapshot input,
                                         GameplayObservation.Phase phase) {
        if (retired || context == null || context.getWorldSession() != world) return;
        TransientMutatorPolicySource.Entry selected = null;
        for (var entry : TransientMutatorPolicyAccess.entries(world)) {
            if (entry.remainingPresentations() > 0 && entry.policy() instanceof HudPresentationPolicy
                    && (selected == null || entry.activationId() >= selected.activationId())) selected = entry;
        }
        if (selected == null) { clear(); return; }
        var nextPolicy = (HudPresentationPolicy) selected.policy();
        boolean changed = policy == null || activation != selected.activationId()
                || !owner.equals(selected.owner()) || !policy.equals(nextPolicy);
        // Cancellation is current-world authority even while motion is frozen. A
        // replacement waits for ACTIVE before sampling layout, RNG or player pose.
        if (phase != GameplayObservation.Phase.ACTIVE) {
            if (changed && policy != null) clear();
            return;
        }
        var graphics = EngineServices.current().graphics();
        width = Math.max(320, graphics.getProjectionWidth());
        height = context.getCamera() == null ? 224 : Math.max(1, context.getCamera().getHeight());
        var readouts = HudProfileAccess.presentationReadouts(graphics);
        if (changed) {
            owner = selected.owner(); activation = selected.activationId(); policy = nextPolicy;
            presentations = 0; random = policy.seed() == 0 ? 0x6D2B79F5 : policy.seed();
        }
        if (changed || !readouts.equals(pairs.stream().map(Pair::readout).toList())) initialize(readouts, changed);
        presentations++;
        double playerX = Double.NaN, playerY = Double.NaN;
        var sprites = context.getSpriteManager();
        var player = sprites == null ? null : sprites.getMainPlayable();
        if (player != null && context.getCamera() != null) {
            playerX = player.getCentreX() - context.getCamera().getXWithShake();
            playerY = player.getCentreY() - context.getCamera().getYWithShake();
        }
        advance(playerX, playerY);
        attachRows();
    }

    private void initialize(List<Readout> readouts, boolean activationChanged) {
        List<Pair> initial = new ArrayList<>(readouts.size()); List<Integer> indices = new ArrayList<>(readouts.size());
        // A layout refresh keeps the sampled slot order of surviving readouts. Musical
        // Chairs consumes the seeded stream only when its policy activation changes.
        if (!activationChanged) for (int previousIndex : order) {
            int id = pairs.get(previousIndex).readout().id();
            for (int index = 0; index < readouts.size(); index++)
                if (readouts.get(index).id() == id) { indices.add(index); break; }
        }
        for (int index = 0; index < readouts.size(); index++) {
            var readout = readouts.get(index);
            initial.add(new Pair(readout, 0, 0, 0, 0, 1));
            if (!indices.contains(index)) indices.add(index);
        }
        pairs = List.copyOf(initial); order = List.copyOf(indices);
        double plaqueWidth = readouts.stream().mapToDouble(Readout::compactWidth).max().orElse(0);
        double plaqueHeight = readouts.stream().mapToDouble(Readout::compactHeight).sum()
                + ROW_GAP * Math.max(0, readouts.size() - 1);
        if (activationChanged || plaque == null) {
            double vx = 0, vy = 0;
            if (policy.mode() == HudPresentationPolicy.Mode.BOUNCE) {
                vx = 1 + (nextRandom() & 255) / 255.0;
                vy = 1 + (nextRandom() & 255) / 255.0;
                if ((nextRandom() & 1) != 0) vx = -vx;
                if ((nextRandom() & 1) != 0) vy = -vy;
            }
            plaque = new Plaque(homeX(), homeY(), vx, vy, plaqueWidth, plaqueHeight, 1);
            if (policy.mode() == HudPresentationPolicy.Mode.SHUFFLE) shuffle();
        } else {
            plaque = new Plaque(plaque.x(), plaque.y(), plaque.vx(), plaque.vy(),
                    plaqueWidth, plaqueHeight, plaque.scale());
        }
    }

    private double homeX() {
        return pairs.stream().mapToDouble(p -> p.readout().labelX()).min().orElse(MARGIN) + (width - 320) / 2.0;
    }

    private double homeY() {
        return pairs.stream().mapToDouble(p -> p.readout().top()).min().orElse(MARGIN);
    }

    private void advance(double playerX, double playerY) {
        double x = plaque.x(), y = plaque.y(), vx = plaque.vx(), vy = plaque.vy(), scale = 1;
        double angle = 2 * Math.PI * (presentations % policy.periodPresentations()) / policy.periodPresentations();
        switch (policy.mode()) {
            case BOUNCE -> { x += vx; y += vy; }
            case ORBIT -> {
                double centreX = Double.isFinite(playerX) ? playerX : width / 2.0;
                double centreY = Double.isFinite(playerY) ? playerY : height / 2.0;
                x = centreX - plaque.width() / 2 + Math.cos(angle) * ORBIT_RADIUS_X;
                y = centreY - plaque.height() / 2 + Math.sin(angle) * ORBIT_RADIUS_Y;
            }
            case EVADE -> {
                double dx = x + plaque.width() / 2 - playerX, dy = y + plaque.height() / 2 - playerY;
                double distance = Math.hypot(dx, dy);
                if (distance < 100) {
                    if (distance < .01) { dx = 1; dy = 1; distance = Math.sqrt(2); }
                    vx = vx * .85 + dx / distance * 1.4;
                    vy = vy * .85 + dy / distance * 1.4;
                } else {
                    vx = vx * .9 + (homeX() - x) * .008;
                    vy = vy * .9 + (homeY() - y) * .008;
                }
                double speed = Math.hypot(vx, vy);
                if (speed > EVADE_SPEED_LIMIT) { vx *= EVADE_SPEED_LIMIT / speed; vy *= EVADE_SPEED_LIMIT / speed; }
                x += vx; y += vy;
            }
            case SCALE -> {
                scale = 1 + Math.sin(angle) * .25;
                x = homeX() + plaque.width() * (1 - scale) / 2;
                y = homeY() + plaque.height() * (1 - scale) / 2;
            }
            case SHUFFLE -> { x = homeX(); y = homeY(); }
            case HIDE -> { }
        }
        // Bounds belong to the complete plaque, including the reflowed lives row.
        scale = Math.min(scale, Math.min((width - 2 * MARGIN) / Math.max(1, plaque.width()),
                (height - 2 * MARGIN) / Math.max(1, plaque.height())));
        double maxX = Math.max(MARGIN, width - MARGIN - plaque.width() * scale);
        double maxY = Math.max(MARGIN, height - MARGIN - plaque.height() * scale);
        if (policy.mode() == HudPresentationPolicy.Mode.BOUNCE || policy.mode() == HudPresentationPolicy.Mode.EVADE) {
            if (x < MARGIN) { x = MARGIN + Math.min(MARGIN - x, maxX - MARGIN); vx = Math.abs(vx); }
            else if (x > maxX) { x = maxX - Math.min(x - maxX, maxX - MARGIN); vx = -Math.abs(vx); }
            if (y < MARGIN) { y = MARGIN + Math.min(MARGIN - y, maxY - MARGIN); vy = Math.abs(vy); }
            else if (y > maxY) { y = maxY - Math.min(y - maxY, maxY - MARGIN); vy = -Math.abs(vy); }
        } else {
            x = clamp(x, MARGIN, maxX); y = clamp(y, MARGIN, maxY);
        }
        plaque = new Plaque(x, y, vx, vy, plaque.width(), plaque.height(), scale);
    }

    private void attachRows() {
        double[] rowOffsets = new double[pairs.size()]; double offset = 0;
        for (int index : order) {
            rowOffsets[index] = offset;
            offset += pairs.get(index).readout().compactHeight() + ROW_GAP;
        }
        List<Pair> updated = new ArrayList<>(pairs.size());
        for (int index = 0; index < pairs.size(); index++) updated.add(new Pair(pairs.get(index).readout(),
                plaque.x(), plaque.y() + rowOffsets[index] * plaque.scale(), plaque.vx(), plaque.vy(), plaque.scale()));
        pairs = List.copyOf(updated);
    }

    private void shuffle() {
        var shuffled = new ArrayList<>(order);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = Math.floorMod(nextRandom(), i + 1);
            int item = shuffled.get(i); shuffled.set(i, shuffled.get(j)); shuffled.set(j, item);
        }
        if (shuffled.size() > 1 && shuffled.equals(order)) java.util.Collections.rotate(shuffled, 1);
        order = List.copyOf(shuffled);
    }
    private int nextRandom() { random ^= random << 13; random ^= random >>> 17; random ^= random << 5; return random; }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private void clear() {
        owner = ""; activation = 0; policy = null; presentations = 0; random = 0;
        width = 320; height = 224; pairs = List.of(); order = List.of(); plaque = null;
    }
    @Override public <T> T getService(Class<T> type) { return type == HudPresentationRuntime.class ? type.cast(this) : null; }
    @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
    @Override public String key() { return "hud-presentation-runtime"; }
    @Override public State capture() { return new State(snapshotOwner, owner, activation, policy, presentations, random, width, height, pairs, order, plaque); }
    @Override public void restore(State state) {
        if (retired) throw new IllegalStateException("HUD presentation belongs to a retired world");
        if (state.snapshotOwner() != snapshotOwner) throw new IllegalArgumentException("HUD snapshot belongs to another world");
        owner = state.owner(); activation = state.activation(); policy = state.policy(); presentations = state.presentations();
        random = state.random(); width = state.width(); height = state.height(); pairs = state.pairs(); order = state.order();
        plaque = state.plaque();
    }
    @Override public void resetForMissingSnapshot() { clear(); }
    @Override public void beforeAssembly(LevelLoadCause cause) { clear(); }
    @Override public void bindRoster(SpriteManager sprites) { }
    @Override public void failedAssembly(LevelLoadCause cause) { clear(); }
    public void leaveOrdinaryGameplay(GameplayObservation.Phase phase) {
        if (phase == GameplayObservation.Phase.SETUP || phase == GameplayObservation.Phase.STAGE
                || phase == GameplayObservation.Phase.TRANSITION) clear();
    }
    @Override public void closeScreens() { clear(); }
    @Override public void retire() { retired = true; clear(); }
}
