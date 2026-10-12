package com.openggf.tools;

/*
 * LevelLayoutDumpTool — dumps an act's ring placements and object spawns (with registry names) to CSV
 * so promo shots can be planned on real layouts (task e0603654). Boots through
 * GameplayCaptureSession on the display-free GL path; reads the loaded Level only.
 * Args: game romPath zoneName act(1-based) outDir
 */
import com.openggf.game.GameServices;
import java.nio.file.*;
import java.util.Locale;

public final class LevelLayoutDumpTool {
    public static void main(String[] a) throws Exception {
        if (a.length != 5) {
            throw new IllegalArgumentException("Usage: LevelLayoutDumpTool <game> <ROM> <zone> <act-one-based> <new-output-dir>");
        }
        MutatorGameplayCaptureTool.requireDisplayFreeContext();
        String game = a[0]; Path rom = Path.of(a[1]); String zone = a[2]; int act = Integer.parseInt(a[3]) - 1;
        Path out = Path.of(a[4]);
        if (act < 0) throw new IllegalArgumentException("Act must be one-based and positive");
        if (Files.exists(out)) throw new IllegalArgumentException("Output directory already exists: " + out);
        Files.createDirectories(out);
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null, null, false,
                false, null, null, false, null, false);
        int z = GameplayCaptureTool.ZoneIds.resolve(game, zone);
        try (var s = new GameplayCaptureSession(settings)) {
            s.boot(rom, z, act, settings);
            var level = GameServices.level().getCurrentLevel();
            var reg = GameServices.module().createObjectRegistry();
            var sb = new StringBuilder("x,y\n");
            for (var r : level.getRings()) sb.append(r.x()).append(',').append(r.y()).append('\n');
            Files.writeString(out.resolve("rings.csv"), sb);
            var ob = new StringBuilder("x,y,id,subtype,name\n");
            for (var o : level.getObjects())
                ob.append(o.x()).append(',').append(o.y()).append(',').append(String.format("%02X", o.objectId())).append(',')
                  .append(String.format("%02X", o.subtype())).append(',').append(reg.getPrimaryName(o.objectId())).append('\n');
            Files.writeString(out.resolve("objects.csv"), ob);
            var p = s.player();
            Files.writeString(out.resolve("start.txt"), p.getCentreX() + "," + p.getCentreY() + "\n");
        }
    }
}
