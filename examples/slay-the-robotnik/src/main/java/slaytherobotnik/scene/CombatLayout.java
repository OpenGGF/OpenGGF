package slaytherobotnik.scene;

import java.util.Map;
import slaytherobotnik.core.Enemy;

/** One logic tick's immutable stage and poses; drawing never produces interaction authority. */
record CombatLayout(LevelStages.Placement stage, Map<Enemy, EnemyVisuals.Pose> poses) {
    CombatLayout { poses = Map.copyOf(poses); }
    EnemyVisuals.Box box(Enemy enemy) {
        EnemyVisuals.Pose pose = poses.get(enemy);
        return pose == null ? null : pose.box();
    }
}
