package example.phase3standalone;

import com.openggf.game.*;
import com.openggf.game.rules.GameRules;
import com.openggf.level.Level;
import com.openggf.level.objects.*;
import java.util.List;

/** Declarative no-ROM setup; the registry retains the sample's own gameplay factories. */
public final class SampleStandaloneModule extends AbstractStandaloneGameModule {
    public SampleStandaloneModule(String owner, Level level) {
        super(StandaloneGameSpec.builder(owner)
                .zone("SAMPLE PLAINS", new StandaloneGameSpec.Act(0x400, level, 32, 96,
                        MusicReference.namespaced(owner, "zone-theme")))
                .supportsSidekick(true)
                .physics(PhysicsProvider.fixed(SampleCharacter.physicsSpec().profile(),
                        PhysicsModifiers.STANDARD, GameRules.SONIC_2))
                .touchResponses((byte) 0, (byte) 0, (byte) 8, (byte) 8)
                .objects(SampleRegistry::new).build());
    }

    private static final class SampleRegistry implements ObjectRegistry {
        @Override public ObjectInstance create(ObjectSpawn spawn) {
            if ("phase3-standalone:walker".equals(spawn.objectKey())) return new SampleBadnik(spawn);
            throw new IllegalArgumentException("Unknown standalone object: " + spawn.objectKey());
        }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int objectId) { return "Standalone object"; }
        @Override public boolean hasObjectKey(String key) { return "phase3-standalone:walker".equals(key); }
        @Override public List<String> browsableObjectKeys() { return List.of("phase3-standalone:walker"); }
    }

}
