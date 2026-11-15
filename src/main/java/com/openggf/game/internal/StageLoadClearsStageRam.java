package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * Game service marker: every stage load starts from cleared stage RAM, whatever stage ran before it in the
 * session. Sonic CD's {@code LevelStart} clears camera RAM, {@code objSpawnRoutine} included (Level/Main.asm:210-216;
 * Level/_Definitions.inc:237, 292), and the block from {@code flowerPosBuf}, which holds {@code levelVIntCounter}
 * (Level/Main.asm:177-183; Main CPU Variables.inc:148, 155). Two shared owners follow it:
 * <ul>
 *   <li>the object spawner's window is rebuilt from the loaded stage's start camera ({@code LevelSizeLoad},
 *       Level/Main.asm:245, then the first {@code SpawnObjects} runs {@code SpawnObjects_Init} from that camera,
 *       Level/Main.asm:402; Object Spawner.asm:41-60), never from the previous stage's camera;</li>
 *   <li>the object V-int clock objects read ({@code levelVIntCounter}, incremented by every V-INT,
 *       Level/Main.asm:712) starts from zero instead of carrying the previous stage's count.</li>
 * </ul>
 * Without it the shared level owner keeps both from the previous stage, as for a game whose V-int count is
 * cross-reset RAM.
 */
public interface StageLoadClearsStageRam {
    /** Whether {@code module} selects the rule. */
    static boolean appliesTo(GameModule module) {
        return module != null && module.getGameService(StageLoadClearsStageRam.class) != null;
    }
}
