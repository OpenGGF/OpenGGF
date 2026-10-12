package com.openggf.mods.code;

import com.openggf.mods.ModDescriptor;
import java.util.*;

/** Immutable diagnostics of successfully published registration contributions, in actual application order. */
public record ModContributionReport(List<Target> targets) {
    public ModContributionReport { targets = List.copyOf(targets); }
    public record Target(String gameId, String contribution, String winner,
                         List<String> shadowedOwners, String policy, String failurePolicy,
                         String winnerValue, List<String> shadowedValues) {
        public Target {
            shadowedOwners = List.copyOf(shadowedOwners);
            shadowedValues = List.copyOf(shadowedValues);
        }
        public Target(String gameId, String contribution, String winner,
                      List<String> shadowedOwners, String policy, String failurePolicy) {
            this(gameId, contribution, winner, shadowedOwners, policy, failurePolicy, null, List.of());
        }
    }
    static ModContributionReport build(Map<String,ModRegistrationPlan> plans, Map<String,ModDescriptor> descriptors) {
        Map<String,List<String>> sources = sources(plans,descriptors);
        List<Target> result = new ArrayList<>();
        sources.forEach((scoped, owners) -> {
            int split = scoped.indexOf('/'); String target = scoped.substring(split+1);
            // Audio publication has its own prepared-session report; registration
            // success does not prove decoding or actual stock override publication.
            if (target.startsWith("audio:")) return;
            boolean singular = singular(target);
            List<String> values = owners.stream().flatMap(owner -> values(owner, target, plans.get(owner)).stream()).toList();
            result.add(new Target(scoped.substring(0,split), target, singular ? owners.getLast() : null,
                    singular ? owners.subList(0,owners.size()-1) : owners,
                    singular ? "later application wins" : "owner-scoped additive",
                    "callback failure disables actual owner and hard dependents; launch preparation aborts",
                    singular ? values.getLast() : null,
                    singular ? values.subList(0, values.size()-1) : values));
        });
        result.sort(Comparator.comparing(Target::gameId).thenComparing(Target::contribution));
        return new ModContributionReport(result);
    }
    private static List<String> values(String owner, String target, ModRegistrationPlan plan) {
        if (target.equals("game-start")) return plan.preparedZones().stream().filter(PreparedModZone::gameStart)
                .map(zone -> owner + ":" + zone.localKey() + "/0").toList();
        if (target.equals("display-width")) return List.of(owner + ":" + plan.requiredDisplayAspect());
        if (target.startsWith("art:")) return List.of(owner + ":" + plan.objectArt().get(target.substring(4)).entryPath());
        return List.of(owner + ":" + target);
    }
    static Map<String,List<String>> sources(Map<String,ModRegistrationPlan> plans, Map<String,ModDescriptor> descriptors) {
        Map<String,List<String>> result = new TreeMap<>();
        plans.forEach((owner,plan) -> {
            for (String game : scopes(plan)) for (String target : contributions(plan, descriptors.get(owner)))
                result.computeIfAbsent(game+"/"+target, ignored -> new ArrayList<>()).add(owner);
        });
        return result;
    }
    static List<String> scopes(ModRegistrationPlan plan) {
        return "any".equals(plan.baseGameId()) ? List.of("s1","s2","s3k")
                : List.of(plan.baseGameId() == null ? plan.ownerModId() : plan.baseGameId());
    }
    static Set<String> contributions(ModRegistrationPlan plan, ModDescriptor descriptor) {
        Set<String> result = new TreeSet<>();
        if (plan.startupScene()!=null) result.add("startup-scene");
        if (plan.requiredDisplayAspect()!=null) result.add("display-width");
        if (plan.preparedZones().stream().anyMatch(PreparedModZone::gameStart)) result.add("game-start");
        plan.objectArt().keySet().stream().filter(key -> !key.startsWith(plan.ownerModId()+":"))
                .forEach(key -> result.add("art:"+key));
        if (descriptor!=null) descriptor.manifest().audioOverrides().keySet().forEach(key -> result.add("audio:"+key));
        plan.zones().forEach(zone -> result.add("zone:"+plan.ownerModId()+":"+zone.localKey()));
        plan.objectFactories().keySet().forEach(key -> result.add("object:"+key));
        plan.characters().keySet().forEach(key -> result.add("character:"+key.persisted()));
        plan.serviceBundles().keySet().forEach(key -> result.add("service-bundle:"+plan.ownerModId()+":"+key));
        plan.decodedLevelPatches().keySet().forEach(key -> result.add("decoded-level:"+plan.ownerModId()+":"+key));
        plan.launchTeams().keySet().forEach(key -> result.add("launch-team:"+key.ownerModId()+":"+key.localName()));
        plan.inputFilters().keySet().forEach(key -> result.add("input-filter:"+key.ownerModId()+":"+key.localName()));
        plan.hudProfiles().keySet().forEach(key -> result.add("hud:"+key.ownerModId()+":"+key.localName()));
        return Collections.unmodifiableSet(result);
    }
    private static boolean singular(String target) {
        return Set.of("startup-scene","display-width","game-start").contains(target)
                || target.startsWith("art:") || target.startsWith("audio:");
    }
}
