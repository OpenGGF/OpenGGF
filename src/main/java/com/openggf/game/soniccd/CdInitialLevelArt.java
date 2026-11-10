package com.openggf.game.soniccd;
import java.util.Objects;
/** One native Main154-165 algorithm: source-selected music, conditional level PLC, standard1.
 * Ports adapt the real source/current owners; no returned values, callback graph, queue loop,
 * native service, loading state or capture authority is introduced. Original callers retain
 * each actual completed publication and the source kernel's terminal prefix on a fault. */
final class CdInitialLevelArt {
 private CdInitialLevelArt(){}
 interface Port {CdCommand selectMusic();void publishMusic(CdCommand command);int levelPlcId();void loadLevel(int id);void loadStandard();}
 static void load(Port p){Objects.requireNonNull(p);p.publishMusic(p.selectMusic());int id=p.levelPlcId();if(id!=0)p.loadLevel(id);p.loadStandard();}
}
