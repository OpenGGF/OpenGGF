package com.openggf.stageplay.runtime;

import com.openggf.physics.NativePlayerTerrainPlacement;
import com.openggf.physics.RouteTerrainProbe;
import com.openggf.sprites.playable.PlayablePlacementProfile;
import com.openggf.stageplay.StageplayGround;
import com.openggf.stageplay.StageplayGround.*;
import com.openggf.stageplay.route.StageplayVisualSupport;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiFunction;

/** Exhaustive integer-seed discovery over one frozen terrain moment; no simulation in discovery. */
final class StageplayGroundSurvey {
    private StageplayGroundSurvey() {}
    static Report answer(Basis basis, Batch batch, GroundWork work, RouteTerrainProbe terrain,
            NativePlayerTerrainPlacement players, Function<Player,PlayablePlacementProfile> factory,
            BiFunction<Query,PlayablePlacementProfile,Reason> unsupported) {
        var answers = new ArrayList<Answer>();
        var profiles = new HashMap<Player,PlayablePlacementProfile>();
        var failures = new HashMap<Player,String>();
        for (var query : batch.queries()) {
            var placements = new TreeMap<Integer,Placement>();
            var refusals = new TreeMap<Integer,Refusal>();
            boolean truncated = false;
            Outcome outcome;
            try {
                work.check();
                if (terrain.dynamicCollision()) {
                    answers.add(refused(query,Outcome.UNSUPPORTED,Reason.DUAL_PLANE,"Active background-plane collision")); continue;
                }
                if (query.performer() instanceof Player player) {
                    if (!profiles.containsKey(player) && !failures.containsKey(player)) {
                        try { profiles.put(player,factory.apply(player)); }
                        catch (IllegalArgumentException failure) { failures.put(player,Objects.toString(failure.getMessage(),"Unsupported donor profile")); }
                    }
                    work.check();
                    if (failures.containsKey(player)) { answers.add(refused(query,Outcome.UNSUPPORTED,Reason.PROFILE,failures.get(player))); continue; }
                    Reason reason = unsupported.apply(query,profiles.get(player));
                    if (reason != null) { answers.add(refused(query,Outcome.UNSUPPORTED,reason,reason.name())); continue; }
                }
                var seen = new HashSet<Integer>();
                for (int seed = query.top()-16; seed <= query.bottom()+16; seed++) {
                    work.check();
                    if (query.performer() instanceof Visual visual) {
                        var floor = terrain.floor(query.x(),seed,visual.path().index());
                        if (!floor.found() || floor.y() < query.top() || floor.y() > query.bottom() || !seen.add(floor.y())) continue;
                        var rest = StageplayVisualSupport.rest(terrain,visual.footprint(),query.x(),floor.y());
                        switch (rest) {
                            case StageplayVisualSupport.Resting resting -> {
                                var s = resting.support();
                                int surface = Math.min(s.left().y(),Math.min(s.centre().y(),s.right().y()));
                                if (surface < query.top() || surface > query.bottom()) continue;
                                int angle = s.left().y() == surface ? s.left().angle() : s.centre().y() == surface ? s.centre().angle() : s.right().angle();
                                placements.putIfAbsent(s.anchorY(),new Placement(query.x(),s.anchorY(),surface,angle,
                                        new VisualSupport(s.left().y(),s.centre().y(),s.right().y())));
                            }
                            case StageplayVisualSupport.Unsupported gap -> refusals.put(floor.y(),new Refusal(floor.y(),Reason.GAP_UNDER_FOOTPRINT,"No floor at x="+gap.gap().columnX()));
                            case StageplayVisualSupport.Unstable unstable -> refusals.put(floor.y(),new Refusal(floor.y(),Reason.UNSTABLE_SUPPORT,"Recheck anchor="+unstable.secondAnchorY()));
                            case StageplayVisualSupport.Obstructed blocked -> refusals.put(floor.y(),new Refusal(floor.y(),Reason.OBSTRUCTED,"Body rectangle is obstructed"));
                        }
                    } else {
                        var profile = profiles.get((Player)query.performer());
                        var near = players.near(profile,query.x(),seed-profile.yRadius());
                        if (near.proposal() == null) {
                            // Empty/default scans are not discovered surfaces. Orientation refusals are useful.
                            if (near.refusal() == NativePlayerTerrainPlacement.Reason.UNSUITABLE_ORIENTATION
                                    && near.surfaceY() >= query.top() && near.surfaceY() <= query.bottom() && seen.add(near.surfaceY()))
                                refusals.putIfAbsent(near.surfaceY(),new Refusal(near.surfaceY(),Reason.UNSUITABLE_ORIENTATION,"Surface requires wall or ceiling mode"));
                            continue;
                        }
                        var proposal = near.proposal();
                        if (proposal.surfaceY() < query.top() || proposal.surfaceY() > query.bottom() || !seen.add(proposal.surfaceY())) continue;
                        var exact = players.exact(profile,query.x(),proposal.y());
                        if (exact.proposal() == null) {
                            refusals.put(proposal.surfaceY(),new Refusal(proposal.surfaceY(),Reason.valueOf(exact.refusal().name()),"Native standing recheck refused the candidate")); continue;
                        }
                        var support = exact.proposal();
                        placements.putIfAbsent(support.y(),new Placement(query.x(),support.y(),support.surfaceY(),support.angle(),
                                new PlayerTerrainProposal(Foot.valueOf(support.foot().name()),support.distance(),profile.xRadius(),profile.yRadius())));
                    }
                    if (placements.size() > StageplayGround.MAX_PLACEMENTS) { placements.pollLastEntry(); truncated = true; }
                }
                work.check();
                outcome = !placements.isEmpty() ? Outcome.FOUND
                        : refusals.values().stream().anyMatch(r -> r.reason() == Reason.UNSUITABLE_ORIENTATION)
                                ? Outcome.UNSUPPORTED : Outcome.NONE_IN_BAND;
            } catch (GroundWork.Stopped stop) { outcome = Outcome.INCOMPLETE; }
            while (refusals.size() > StageplayGround.MAX_REFUSALS) { refusals.pollLastEntry(); truncated = true; }
            answers.add(new Answer(query.id(),outcome,List.copyOf(placements.values()),List.copyOf(refusals.values()),truncated));
        }
        return new Report(basis,answers,work.snapshot());
    }
    static Answer refused(Query query, Outcome outcome, Reason reason, String detail) {
        return new Answer(query.id(),outcome,List.of(),List.of(new Refusal(query.top(),reason,detail)),false);
    }
    static Report unavailable(Basis basis, Batch batch, GroundWork work, String detail) {
        boolean interrupted = work.snapshot().stopped() != StopReason.NONE;
        return new Report(basis,batch.queries().stream().map(q -> interrupted
                ? new Answer(q.id(),Outcome.INCOMPLETE,List.of(),List.of(),false)
                : refused(q,Outcome.CONTEXT_UNAVAILABLE,Reason.CONTEXT_UNAVAILABLE,detail)).toList(),work.snapshot());
    }
}
