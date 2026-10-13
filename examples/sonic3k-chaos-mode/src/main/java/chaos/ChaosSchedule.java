package chaos;

import com.openggf.mods.state.SnapshotRandom;
import java.util.*;

/** Native pacing never advances this clock. The owner calls once per eligible presentation. */
public final class ChaosSchedule {
    public record Active(long id,String effect,Map<String,Object> options,long started,long expires) {
        public Active { options=Map.copyOf(options); }
    }
    public record Snapshot(long random,long clock,long nextDraw,long majorAfter,long graceUntil,
                           ChaosSettings settings,List<String> recent,List<Active> active) {
        public Snapshot { recent=List.copyOf(recent); active=List.copyOf(active); }
    }
    public interface Admission {
        default int nativeZone() {return -1;}
        boolean eligible(ChaosCatalog.Effect effect,Map<String,Object> options);
        long activate(ChaosCatalog.Effect effect,Map<String,Object> options,int duration);
        void cancel(long id);
    }
    private final ChaosCatalog catalogue;
    private final DecisionSource decisions;
    private final SnapshotRandom random;
    private ChaosSettings settings;
    private long clock,nextDraw,majorAfter,graceUntil;
    private final List<String> recent=new ArrayList<>();
    private final List<Active> active=new ArrayList<>();
    public ChaosSchedule(ChaosCatalog catalogue,ChaosSettings settings,long seed,DecisionSource decisions) {
        this.catalogue=catalogue; this.settings=settings.validate(catalogue); this.decisions=decisions;
        random=new SnapshotRandom(seed==0?0x534F4E4943434841L:seed);
        boundary();
    }
    public ChaosSettings settings() { return settings; }
    public long clock() { return clock; }
    public int countdown() { return (int)Math.max(0,nextDraw-clock); }
    public List<Active> active() { return List.copyOf(active); }
    public void boundary() { active.clear(); graceUntil=clock+300; nextDraw=graceUntil+settings.intervalSeconds()*60L; }
    public void edit(ChaosSettings next,Admission admission) {
        next.validate(catalogue);
        for(var value:List.copyOf(active)) if(!next.enabled() || !next.selected().contains(value.effect())) {
            admission.cancel(value.id()); active.remove(value);
        }
        if(next.intervalSeconds()!=settings.intervalSeconds()) nextDraw=Math.max(clock,graceUntil)+next.intervalSeconds()*60L;
        settings=next;
    }
    public void clear(Admission admission) { active.forEach(a->admission.cancel(a.id())); active.clear(); }
    /** Host completion also retires a one-shot; it never reapplies that action at expiry. */
    public void retainHostActivations(Set<Long> ids) { active.removeIf(a->!ids.contains(a.id())); }
    /** Reward quiet time ages existing leases but postpones selection for a fresh interval. */
    public void quietPresentation(Admission admission) {
        if(!settings.enabled()) return;
        advance(admission);
        nextDraw=clock+settings.intervalSeconds()*60L;
    }
    private void advance(Admission admission) {
        clock++;
        for(var value:List.copyOf(active)) if(value.expires()>0 && clock>=value.expires()) {
            admission.cancel(value.id()); active.remove(value);
        }
    }
    public void presentation(Admission admission) {
        if(!settings.enabled()) return;
        advance(admission);
        if(clock<graceUntil || clock<nextDraw) return;
        nextDraw=clock+settings.intervalSeconds()*60L;
        if(settings.selected().isEmpty() || active.size()>=2) return;
        List<ChaosCatalog.Effect> candidates=catalogue.effects().stream().filter(e->settings.selected().contains(e.id()))
                .filter(e->active.stream().noneMatch(a->catalogue.effect(a.effect()).family().equals(e.family())))
                .filter(e->e.category()==ChaosCatalog.Category.VISUAL || active.stream().noneMatch(a->catalogue.effect(a.effect()).category()!=ChaosCatalog.Category.VISUAL))
                .filter(e->e.severity()!=ChaosCatalog.Severity.MAJOR || clock>=majorAfter).toList();
        if(candidates.isEmpty()) return;
        // Eligibility is checked with a deterministic representative before any RNG is consumed.
        candidates=candidates.stream().filter(e->admission.eligible(e,catalogue.sample(e,new SnapshotRandom(1),admission.nativeZone()))).toList();
        if(candidates.isEmpty()) return;
        int ticket=random.nextInt(100);
        var tier=ticket<90?ChaosCatalog.Severity.ORDINARY:ticket<99?ChaosCatalog.Severity.DISRUPTIVE:ChaosCatalog.Severity.MAJOR;
        List<ChaosCatalog.Effect> eligible=List.of();
        for(int index=tier.ordinal();index>=0;index--) {
            int rank=index; eligible=candidates.stream().filter(e->e.severity().ordinal()==rank).toList();
            if(!eligible.isEmpty()) break;
        }
        if(eligible.isEmpty()) return;
        var exclusion=new ArrayList<>(recent);
        List<ChaosCatalog.Effect> fresh=eligible.stream().filter(e->!exclusion.contains(e.id())).toList();
        while(fresh.isEmpty() && !exclusion.isEmpty()) {
            exclusion.removeFirst();fresh=eligible.stream().filter(e->!exclusion.contains(e.id())).toList();
        }
        if(!fresh.isEmpty())eligible=fresh;
        var effect=decisions.choose(eligible,random);
        if(!eligible.contains(effect)) throw new IllegalArgumentException("Decision outside admitted tier/pool");
        var options=catalogue.sample(effect,random,admission.nativeZone());
        if(!admission.eligible(effect,options)) return;
        int duration=effect.duration(settings.durationQuarters());
        long id=admission.activate(effect,options,duration);
        if(id<=0) return;
        active.add(new Active(id,effect.id(),options,clock,duration==0?0:clock+duration));
        recent.add(effect.id()); while(recent.size()>3) recent.removeFirst();
        if(effect.severity()==ChaosCatalog.Severity.MAJOR) majorAfter=clock+10800;
    }
    public Snapshot capture() { return new Snapshot(random.snapshot(),clock,nextDraw,majorAfter,graceUntil,settings,recent,active); }
    public void restore(Snapshot snapshot) {
        settings=snapshot.settings().validate(catalogue); random.restore(snapshot.random()); clock=snapshot.clock();
        nextDraw=snapshot.nextDraw(); majorAfter=snapshot.majorAfter(); graceUntil=snapshot.graceUntil();
        recent.clear(); recent.addAll(snapshot.recent()); active.clear(); active.addAll(snapshot.active());
    }
}
