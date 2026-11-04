package com.openggf.stageplay.route;

import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.PadRow;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerView;
import com.openggf.route.RecordedStep;
import com.openggf.route.ReplayWorld;
import com.openggf.route.RouteHostException;
import com.openggf.route.RouteSample;
import com.openggf.route.RouteWorld;
import com.openggf.route.RouteWorldFactory;
import com.openggf.route.WorldCheckpoint;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayablePuppet;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.stageplay.runtime.StageplayResources;
import com.openggf.mods.scene.host.SceneRomLibrary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stageplay's route hosts. Two tiers, one row boundary:
 *
 * <ul>
 *   <li>{@link #probe}: the private static-terrain {@link PlayablePuppet} (one stock playable, no
 *   objects, events or session). Cheap draft tier for point searches; it is never a qualifier. It
 *   declares restricted controller support: Left, Right, Down and one jump button.</li>
 *   <li>{@link #scene}: a fresh offline {@link StageplayLiveWorld} replaying the whole document
 *   from frame zero, whose routed actor reads the document's frozen input track. Each offered
 *   row must equal the track's row; the host only observes. This is the qualification host.</li>
 * </ul>
 */
final class StageplayRouteHosts {
    private StageplayRouteHosts() {
    }

    /** Draft-tier hosts over one probe; every open restores the shot-entry snapshot. */
    static RouteWorldFactory probe(PlayablePuppet probe, int startFrame) {
        PlayablePuppet.Snapshot entry = probe.capture();
        return new RouteWorldFactory() {
            @Override public RouteWorld openSearch(LaunchContract launch) {
                probe.restore(entry);
                return new ProbeWorld(probe, startFrame);
            }

            @Override public ReplayWorld openColdReplay(LaunchContract launch) {
                throw new RouteHostException(RouteHostException.Reason.UNSUPPORTED_LAUNCH,
                        "the static-terrain probe drafts rows; it never qualifies them");
            }

            @Override public String environment() {
                return "stageplay-probe/1";
            }
        };
    }

    /**
     * Qualification hosts for one player route: replaying a route installs its rows as the
     * document's frozen track for {@code actorId} and replays that candidate document from frame
     * zero. The last opened world stays readable for shot checks.
     */
    static final class SceneQualifier implements RouteWorldFactory {
        private final StageplayResources resources;
        private final SceneRomLibrary library;
        private final StageplayDocument document;
        private final String actorId;
        private final int startFrame;
        private final String trackId;
        private SceneWorld current;

        SceneQualifier(StageplayResources resources, SceneRomLibrary library, StageplayDocument document, String actorId,
                       int startFrame) {
            this.resources = resources;
            this.library = library;
            this.document = document;
            this.actorId = actorId;
            this.startFrame = startFrame;
            this.trackId = StageplayRouteContract.nextId(StageplayRouteContract.identities(document), "route.rows.");
        }

        /** The candidate document {@code tape} would produce. */
        StageplayDocument candidate(InputTape tape) {
            return StageplayRoutes.install(document, StageplayRoutes.track(trackId, actorId, startFrame, tape));
        }

        StageplayDocument.FrozenInputTrack track(InputTape tape) {
            return StageplayRoutes.track(trackId, actorId, startFrame, tape);
        }

        @Override public RouteWorld openSearch(LaunchContract launch) {
            throw new RouteHostException(RouteHostException.Reason.UNSUPPORTED_LAUNCH,
                    "the scene host replays a frozen track; live scene search is not available");
        }

        @Override public ReplayWorld openColdReplay(LaunchContract launch) {
            throw new RouteHostException(RouteHostException.Reason.UNSUPPORTED_LAUNCH,
                    "a scene replay needs the route's rows to install as the frozen track");
        }

        @Override public ReplayWorld openColdReplay(com.openggf.route.RouteArtifact route) {
            current = SceneWorld.open(resources, library, candidate(route.tape()), actorId, startFrame);
            return current;
        }

        @Override public String environment() {
            return "stageplay-scene/1";
        }

        StageplayTimeline.Frame frame() {
            return current == null ? null : current.frame();
        }

        StageplayLiveWorld world() {
            return current.world();
        }

        StageplayDocument.SetSource set() {
            return StageplayRouteContract.source(document, startFrame);
        }
    }

    /**
     * Probe hosts that also replay rows on the probe: only for focused tests of the draft tier.
     * Production never qualifies on static terrain.
     */
    static RouteWorldFactory probeOnly(PlayablePuppet probe, int startFrame) {
        PlayablePuppet.Snapshot entry = probe.capture();
        return new RouteWorldFactory() {
            @Override public RouteWorld openSearch(LaunchContract launch) {
                probe.restore(entry);
                return new ProbeWorld(probe, startFrame);
            }

            @Override public ReplayWorld openColdReplay(LaunchContract launch) {
                probe.restore(entry);
                return new ProbeWorld(probe, startFrame);
            }

            @Override public String environment() {
                return "stageplay-probe-replay/1";
            }
        };
    }

    static PlayerView view(PlayablePuppet.Observation o, boolean dead, boolean hurt, int rings, boolean rolling,
                           boolean onObject, boolean facingLeft) {
        int x = (int) Math.floor(o.x());
        int y = (int) Math.floor(o.y());
        return new PlayerView(x, y, (int) Math.round((o.x() - x) * 65536) & 0xFFFF,
                (int) Math.round((o.y() - y) * 65536) & 0xFFFF, o.xSpeed(), o.ySpeed(), o.groundSpeed(), o.angle(),
                o.airborne(), rolling, o.spindash(), o.crouching(), hurt, dead, rings, onObject, false, facingLeft,
                new PlayerView.Motion(o.maxSpeed(), o.runAccel(), o.runDecel(), o.friction(), o.slopeImpulse(),
                        o.skidding()));
    }

    private static final class ProbeWorld implements RouteWorld {
        private static final int SUPPORTED = PhysicalButton.LEFT.bit() | PhysicalButton.RIGHT.bit()
                | PhysicalButton.DOWN.bit() | PhysicalButton.A.bit() | PhysicalButton.B.bit() | PhysicalButton.C.bit();
        private final PlayablePuppet probe;
        private final int startFrame;
        private int rows;

        ProbeWorld(PlayablePuppet probe, int startFrame) {
            this.probe = probe;
            this.startFrame = startFrame;
        }

        @Override public RouteSample observe() {
            var o = probe.sample();
            return new RouteSample(rows, 0, new RouteSample.Level(0, 0, 0, "PROBE"),
                    new RouteSample.Clocks(startFrame + rows, startFrame + rows),
                    view(o, false, false, 0, false, false, o.flipX()), Optional.empty(), 0, 0, 0);
        }

        @Override public RecordedStep step(PadRow offered) {
            if ((offered.p1() & ~SUPPORTED) != 0 || offered.p2() != 0) {
                throw new IllegalArgumentException("the static-terrain probe cannot reproduce " + offered
                        + "; it supports Left, Right, Down and one jump button on P1");
            }
            int lane = offered.p1();
            boolean jump = (lane & (PhysicalButton.A.bit() | PhysicalButton.B.bit() | PhysicalButton.C.bit())) != 0;
            probe.tick((lane & PhysicalButton.LEFT.bit()) != 0, (lane & PhysicalButton.RIGHT.bit()) != 0,
                    (lane & PhysicalButton.DOWN.bit()) != 0, jump, startFrame + rows);
            rows++;
            return new RecordedStep(offered, observe());
        }

        @Override public int rowsOffered() {
            return rows;
        }

        @Override public WorldCheckpoint capture() {
            return new ProbeCheckpoint(this, probe.capture(), rows);
        }

        @Override public void restore(WorldCheckpoint checkpoint) {
            if (!(checkpoint instanceof ProbeCheckpoint saved) || saved.owner() != this) {
                throw new RouteHostException(RouteHostException.Reason.CHECKPOINT_MISMATCH, "probe checkpoint from another host");
            }
            probe.restore(saved.snapshot());
            rows = saved.rows();
        }

        /** Static terrain: every future-relevant probe field plus the frame clock certifies equality. */
        @Override public Optional<Object> equivalenceKey() {
            var o = probe.sample();
            return Optional.of(List.of(o.x(), o.y(), o.xSpeed(), o.ySpeed(), o.groundSpeed(), o.angle(), o.airborne(),
                    o.xRadius(), o.yRadius(), o.spindash(), o.crouching(), o.skidding(), o.runAccel(), o.runDecel(),
                    o.friction(), o.maxSpeed(), o.slopeImpulse()));
        }

        @Override public void close() {
        }
    }

    private record ProbeCheckpoint(ProbeWorld owner, PlayablePuppet.Snapshot snapshot, int rows) implements WorldCheckpoint { }

    /** Replays the document from frame zero; rows after the shot entry must equal the frozen track's. */
    private static final class SceneWorld implements ReplayWorld {
        private final StageplayResources resources;
        private final StageplayLiveWorld world;
        private final StageplayDocument document;
        private final String actorId;
        private final int startFrame;
        private final InputTape track;
        private int rows;
        private StageplayTimeline.Frame frame;

        private SceneWorld(StageplayResources resources, StageplayLiveWorld world, StageplayDocument document,
                           String actorId, int startFrame, InputTape track) {
            this.resources = resources;
            this.world = world;
            this.document = document;
            this.actorId = actorId;
            this.startFrame = startFrame;
            this.track = track;
        }

        static SceneWorld open(StageplayResources resources, SceneRomLibrary library, StageplayDocument document,
                               String actorId, int startFrame) {
            var frozen = document.inputTracks().stream()
                    .filter(t -> t.startFrame() == startFrame && t.binds(actorId)).findFirst()
                    .orElseThrow(() -> new RouteHostException(RouteHostException.Reason.UNSUPPORTED_LAUNCH,
                            "the scene has no frozen track for " + actorId + " at " + startFrame));
            var live = StageplayLiveWorld.openOffline(library);
            try {
                resources.setLiveWorld(live);
                var scene = new SceneWorld(resources, live, document, actorId, startFrame, StageplayRoutes.tape(frozen));
                for (int f = 0; f < startFrame; f++) scene.resolve(f);
                return scene;
            } catch (RuntimeException | Error failure) {
                live.close();
                throw failure;
            }
        }

        private void resolve(int f) {
            frame = resources.resolveFrame(document, StageplayTimeline.sample(document, f));
        }

        @Override public RouteSample observe() {
            if (rows == 0) return entry();
            AbstractPlayableSprite sprite = world.routeSprite(actorId);
            var o = PlayablePuppet.observe(sprite);
            var level = com.openggf.game.session.SessionManager.getCurrentGameplayMode().getLevelManager();
            return new RouteSample(rows, 0, new RouteSample.Level(level.getCurrentZone(), level.getCurrentAct(),
                    level.getApparentAct(), "SCENE"), new RouteSample.Clocks(startFrame + rows, level.getFrameCounter()),
                    view(o, sprite.getDead(), sprite.isHurt(), sprite.getRingCount(), sprite.getRolling(),
                            sprite.isOnObject(), sprite.getDirection() == com.openggf.physics.Direction.LEFT),
                    Optional.empty(), 0, 0, 0);
        }

        @Override public RecordedStep step(PadRow offered) {
            if (rows >= track.size() || !track.row(rows).equals(offered)) {
                throw new IllegalArgumentException("scene row " + rows + " offered " + offered
                        + " but the document's frozen track holds " + (rows < track.size() ? track.row(rows) : "nothing"));
            }
            resolve(startFrame + rows);
            rows++;
            return new RecordedStep(offered, observe());
        }

        /**
         * Before row 0 the shot has not begun (resolving its first frame is row 0), so the entry
         * observation is the authored anchor at rest. It can seed checks; it is never a post-row sample.
         */
        private RouteSample entry() {
            var key = document.actors().stream().filter(a -> a.id().equals(actorId)).findFirst().orElseThrow()
                    .keys().stream().filter(k -> k.frame() <= startFrame).reduce((a, b) -> b).orElseThrow();
            int x = (int) Math.floor(key.x());
            int y = (int) Math.floor(key.y());
            var view = new PlayerView(x, y, 0, 0, 0, 0, 0, 0, true, false, false, false, false, false, 0, false, false,
                    key.facing() == StageplayDocument.Facing.LEFT, PlayerView.Motion.NONE);
            return new RouteSample(0, 0, new RouteSample.Level(0, 0, 0, "SCENE"),
                    new RouteSample.Clocks(startFrame, 0), view, Optional.empty(), 0, 0, 0);
        }

        /** The last resolved timeline frame, for shot checks (set, puppet status, events). */
        StageplayTimeline.Frame frame() {
            return frame;
        }

        StageplayLiveWorld world() {
            return world;
        }

        @Override public int rowsOffered() {
            return rows;
        }

        @Override public void close() {
            world.close();
        }
    }
}
