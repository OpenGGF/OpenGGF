package starpost.realruins;

import com.openggf.game.*;
import com.openggf.data.Rom;
import com.openggf.level.Palette;
import starpost.ruins.Chamber;

/** The generated waterline drives the engine's real water movement and drowning countdown. */
public final class RuinsWater implements WaterDataProvider {
    private final int line;
    public RuinsWater(Chamber chamber) { line=chamber.waterY; }
    public boolean hasWater(int zone,int act,PlayerCharacter player) { return line!=Chamber.NO_WATER; }
    public int getStartingWaterLevel(int zone,int act) { return line+RuinsLevel.ORIGIN; }
    public Palette[] getUnderwaterPalette(Rom rom,int zone,int act,PlayerCharacter player) { return null; }
    public DynamicWaterHandler getDynamicHandler(int zone,int act,PlayerCharacter player) { return null; }
}
