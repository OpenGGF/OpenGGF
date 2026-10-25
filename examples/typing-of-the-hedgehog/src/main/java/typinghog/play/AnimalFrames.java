package typinghog.play;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AnimalType;

/** Resolves the loaded zone pair, exactly as native S1 Anml_FromEnemy / S2 Obj28 / S3K Obj_Animal. */
final class AnimalFrames {
    private static final int FRAMES_PER_ANIMAL = 3;

    private AnimalFrames() { }

    /** Selects one of the zone's two ROM art banks; no duplicated zone/species tables. */
    static void select(Stagehand.Visual visual, int variant) {
        var render = GameServices.level().getObjectRenderManager();
        int species = variant == 0 ? render.getAnimalTypeA() : render.getAnimalTypeB();
        int mapping = AnimalType.fromIndex(species).mappingSet().ordinal();
        visual.frame = (mapping * 2 + variant) * FRAMES_PER_ANIMAL + 2;
    }

    /** Keeps the species and art bank while changing its local pose (0/1 hopping, 2 released). */
    static void animate(Stagehand.Visual visual, int pose) {
        visual.frame = visual.frame / FRAMES_PER_ANIMAL * FRAMES_PER_ANIMAL + pose;
    }
}
