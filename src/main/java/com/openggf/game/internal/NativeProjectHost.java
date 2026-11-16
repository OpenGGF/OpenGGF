package com.openggf.game.internal;

import com.openggf.level.Level;
import java.io.IOException;

/** Native authoring materialization; keeps game-owned stage data and launch coordinates. */
public interface NativeProjectHost {
    record Target(int zone, int act, int levelIndex) { }
    Target target(NativeProjectSource source) throws IOException;
    Level load(NativeProjectSource source, Level authored, int startX, int startY) throws IOException;
}
