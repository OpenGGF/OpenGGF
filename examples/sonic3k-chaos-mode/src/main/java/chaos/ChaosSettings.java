package chaos;

import java.util.*;

/** Player preferences are durable; the admitted copy belongs to captured run history. */
public record ChaosSettings(int intervalSeconds, int durationQuarters, Set<String> selected,
                            boolean enabled, long seed) {
    public ChaosSettings {
        if (intervalSeconds<5 || intervalSeconds>120 || durationQuarters<1 || durationQuarters>12)
            throw new IllegalArgumentException("Interval must be 5..120 seconds; duration must be 0.25..3 times");
        selected=Set.copyOf(selected);
        if(selected.size()>105) throw new IllegalArgumentException("Too many selected effects");
    }
    public static ChaosSettings defaults(ChaosCatalog catalogue) {
        return new ChaosSettings(30,4,catalogue.defaults(),false,0);
    }
    public ChaosSettings validate(ChaosCatalog catalogue) {
        selected.forEach(catalogue::effect); return this;
    }
    public ChaosSettings select(String id,boolean include,ChaosCatalog catalogue) {
        catalogue.effect(id); var next=new LinkedHashSet<>(selected);
        if(include) next.add(id); else next.remove(id);
        return new ChaosSettings(intervalSeconds,durationQuarters,next,enabled,seed);
    }
    public ChaosSettings interval(int seconds) { return new ChaosSettings(seconds,durationQuarters,selected,enabled,seed); }
    public ChaosSettings multiplier(int quarters) { return new ChaosSettings(intervalSeconds,quarters,selected,enabled,seed); }
    public ChaosSettings enabled(boolean value) { return new ChaosSettings(intervalSeconds,durationQuarters,selected,value,seed); }
}
