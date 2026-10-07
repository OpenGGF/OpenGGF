package example.platformer;

import com.openggf.game.*;
import com.openggf.game.rules.GameRules;
import com.openggf.level.Level;
import com.openggf.level.objects.*;
import java.util.List;

/** Declarative no-ROM setup; the registry retains the sample's own gameplay factories. */
public final class PlatformerModule extends AbstractStandaloneGameModule {
    public PlatformerModule(String owner, Level level) {
        super(StandaloneGameSpec.builder(owner)
                .zone("BOLT PLAINS", new StandaloneGameSpec.Act(0x400, level, 64, 160,
                        MusicReference.namespaced(owner, "zone-theme")))
                .supportsSidekick(false)
                .physics(PhysicsProvider.fixed(BoltCharacter.physicsSpec().profile(),
                        PhysicsModifiers.STANDARD, GameRules.SONIC_2))
                .touchResponses((byte) 0, (byte) 0, (byte) 8, (byte) 8)
                .objects(PlatformerRegistry::new).build());
    }

    private static final class PlatformerRegistry implements ObjectRegistry {
        @Override public ObjectInstance create(ObjectSpawn spawn) {
            return switch (String.valueOf(spawn.objectKey())) {
                case "sample-platformer:zapbug" -> new ZapBug(spawn);
                case "sample-platformer:springpad" -> new SpringPad(spawn);
                default -> throw new IllegalArgumentException("Unknown standalone object: " + spawn.objectKey());
            };
        }
        @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
        @Override public String getPrimaryName(int objectId) { return "Standalone object"; }
        @Override public boolean hasObjectKey(String key) {
            return "sample-platformer:zapbug".equals(key) || "sample-platformer:springpad".equals(key);
        }
        @Override public List<String> browsableObjectKeys() {
            return List.of("sample-platformer:zapbug", "sample-platformer:springpad");
        }
    }

}
