package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdZone;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.AbstractObjectRegistry;
import com.openggf.level.objects.ObjectFactory;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Sonic CD object registry. Factories come from the common fragment, the shared fragment and one
 * fragment per zone; an ID resolves as the stage's zone fragment, then the shared fragment (at the ID
 * the stage's ObjectIndex gives the routine), then the common fragment. A zone registration may be
 * stage-scoped: in the zone's stages its predicate rejects, the ID falls through to shared and common.
 * An ID without a factory spawns the shared placeholder, as in the other modules.
 */
public final class SonicCdObjectRegistry extends AbstractObjectRegistry {
    private final Map<Integer, ObjectFactory> common = new HashMap<>();
    private final Map<CdZone, Map<Integer, ObjectFactory>> zones = new EnumMap<>(CdZone.class);
    /** The stages a zone's stage-scoped registration applies in (absent: every stage of the zone). */
    private final Map<CdZone, Map<Integer, java.util.function.Predicate<com.openggf.game.soniccd.CdStagePayload>>> zoneScopes =
            new EnumMap<>(CdZone.class);
    /** Each zone's boss fragment, consulted first on that zone's act 3 stages (objects/boss). */
    private final Map<CdZone, Map<Integer, ObjectFactory>> bosses = new EnumMap<>(CdZone.class);
    private final Map<Integer, String> names = new HashMap<>();
    /** Common routines placed by the stage's ObjectIndex (catalogue ID column -> factory). */
    private final Map<String, ObjectFactory> stageMapped = new java.util.LinkedHashMap<>();
    /** Shared routines (several zones, not all) placed by the stage's ObjectIndex (shared catalogue ID column). */
    private final Map<String, ObjectFactory> sharedMapped = new java.util.LinkedHashMap<>();

    /** The common fragment. */
    private static CdObjectRegistration commonFragment() {
        return new com.openggf.game.soniccd.objects.common.CommonObjects();
    }

    /** The shared fragment: objects several zones assemble. */
    private static CdObjectRegistration sharedFragment() {
        return new com.openggf.game.soniccd.objects.shared.SharedObjects();
    }

    /** The zone's fragment. */
    private static CdObjectRegistration zoneFragment(CdZone zone) {
        return switch (zone) {
            case PALMTREE_PANIC -> new com.openggf.game.soniccd.objects.ppz.PpzObjects();
            case COLLISION_CHAOS -> new com.openggf.game.soniccd.objects.ccz.CczObjects();
            case TIDAL_TEMPEST -> new com.openggf.game.soniccd.objects.ttz.TtzObjects();
            case QUARTZ_QUADRANT -> new com.openggf.game.soniccd.objects.qqz.QqzObjects();
            case WACKY_WORKBENCH -> new com.openggf.game.soniccd.objects.wwz.WwzObjects();
            case STARDUST_SPEEDWAY -> new com.openggf.game.soniccd.objects.ssz.SszObjects();
            case METALLIC_MADNESS -> new com.openggf.game.soniccd.objects.mmz.MmzObjects();
        };
    }

    /**
     * The fragments whose sheets a stage of {@code zone} loads: common, shared, the zone's, then the zone's boss
     * fragment (which registers sheets on its act 3 stages only).
     */
    public static java.util.List<CdObjectRegistration> artFragments(CdZone zone) {
        return java.util.List.of(commonFragment(), sharedFragment(), zoneFragment(zone),
                com.openggf.game.soniccd.objects.boss.CdBossObjects.fragment(zone));
    }

    @Override
    protected void registerDefaultFactories() {
        commonFragment().register(registrar(common));
        sharedFragment().register(registrar(new HashMap<>(), sharedMapped));
        for (CdZone zone : CdZone.values()) register(zone, zoneFragment(zone));
        for (CdZone zone : CdZone.values()) {
            Map<Integer, ObjectFactory> factories = new HashMap<>();
            com.openggf.game.soniccd.objects.boss.CdBossObjects.fragment(zone).register(registrar(factories));
            bosses.put(zone, factories);
        }
    }

    private void register(CdZone zone, CdObjectRegistration fragment) {
        Map<Integer, ObjectFactory> factories = new HashMap<>();
        Map<Integer, java.util.function.Predicate<com.openggf.game.soniccd.CdStagePayload>> scopes = new HashMap<>();
        fragment.register(registrar(factories, stageMapped, scopes));
        zones.put(zone, factories);
        zoneScopes.put(zone, scopes);
    }

    private CdObjectRegistration.Registrar registrar(Map<Integer, ObjectFactory> target) {
        return registrar(target, stageMapped);
    }

    private CdObjectRegistration.Registrar registrar(Map<Integer, ObjectFactory> target, Map<String, ObjectFactory> mapped) {
        return registrar(target, mapped, null);
    }

    private CdObjectRegistration.Registrar registrar(Map<Integer, ObjectFactory> target, Map<String, ObjectFactory> mapped,
            Map<Integer, java.util.function.Predicate<com.openggf.game.soniccd.CdStagePayload>> scopes) {
        return new CdObjectRegistration.Registrar() {
            @Override public void object(int objectId, String name, ObjectFactory factory) {
                target.put(objectId, factory);
                names.putIfAbsent(objectId, name);
            }

            @Override public void object(int objectId, String name,
                                         java.util.function.Predicate<com.openggf.game.soniccd.CdStagePayload> indexedIn,
                                         ObjectFactory factory) {
                if (scopes == null) throw new UnsupportedOperationException("stage-scoped objects belong to zone fragments");
                object(objectId, name, factory);
                scopes.put(objectId, indexedIn);
            }

            @Override public void badnik(int objectId, String name, ObjectFactory factory) {
                object(objectId, name, factory);
            }

            @Override public void stageObject(String idColumn, String name, ObjectFactory factory) {
                mapped.put(idColumn, factory);
            }
        };
    }

    @Override
    public ObjectInstance create(ObjectSpawn spawn) {
        if (spawn.objectKey() != null) return super.create(spawn);
        ensureLoaded();
        ObjectFactory factory = currentLevel() instanceof CdLevel level ? stageFactory(level.payload(), spawn.objectId()) : null;
        if (factory == null) factory = common.get(spawn.objectId());
        return (factory != null ? factory : defaultFactory).create(spawn, this);
    }

    /**
     * The loaded stage's factory for {@code objectId}: its zone fragment (scoped registrations only in the
     * stages they apply to), then the shared and common routines at the IDs the stage's ObjectIndex gives them.
     * Null where only a fixed common ID, or nothing, applies.
     */
    ObjectFactory stageFactory(com.openggf.game.soniccd.CdStagePayload payload, int objectId) {
        ObjectFactory factory = null;
        // A zone's boss fragment is consulted first on its act 3 stages (objects/boss).
        if (com.openggf.game.soniccd.objects.boss.CdBossObjects.bossStage(payload)) {
            factory = bosses.getOrDefault(payload.stage().zone(), Map.of()).get(objectId);
        }
        if (factory == null) factory = zoneFactory(payload, objectId);
        for (var shared : sharedMapped.entrySet()) {
            if (factory != null) break;
            if (com.openggf.game.soniccd.objects.shared.SharedObjectCatalogue.offset(payload, shared.getKey())
                    == objectId) factory = shared.getValue();
        }
        if (factory == null) {
            var catalogue = com.openggf.game.soniccd.objects.art.CdObjectCatalogue.entry(payload);
            for (var mapped : stageMapped.entrySet()) {
                if (catalogue.offset(mapped.getKey()) == objectId) {
                    factory = mapped.getValue();
                    break;
                }
            }
        }
        return factory;
    }

    /** The stage's zone fragment factory for {@code objectId}, or null where none applies in this stage. */
    ObjectFactory zoneFactory(com.openggf.game.soniccd.CdStagePayload payload, int objectId) {
        ensureLoaded();
        CdZone zone = payload.stage().zone();
        ObjectFactory factory = zones.getOrDefault(zone, Map.of()).get(objectId);
        var scope = zoneScopes.getOrDefault(zone, Map.of()).get(objectId);
        return factory != null && scope != null && !scope.test(payload) ? null : factory;
    }

    @Override
    public String getPrimaryName(int objectId) {
        ensureLoaded();
        return names.getOrDefault(objectId, String.format("CD object $%02X", objectId));
    }
}
