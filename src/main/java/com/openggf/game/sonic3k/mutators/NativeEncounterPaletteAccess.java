package com.openggf.game.sonic3k.mutators;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;

/** Read-only exact-world producer query; ordinary native objects keep their current native palette. */
public final class NativeEncounterPaletteAccess {
    private NativeEncounterPaletteAccess() { }
    public static int base(ObjectServices services,ObjectInstance object,String key) {
        var caller=com.openggf.util.EngineCallerAccess.callerOutside(NativeEncounterPaletteAccess.class);
        if(caller.getClassLoader()!=NativeEncounterPaletteAccess.class.getClassLoader())
            throw new SecurityException("Encounter palette query belongs to native sprite producers");
        if(services==null || services.worldSession()==null) return -1;
        var runtime=com.openggf.game.session.WorldSessionPolicyAccess.getService(services.worldSession(),NativeMutatorRuntime.class);
        return runtime==null?-1:runtime.encounterPaletteBase(services,object,key);
    }
    public static void draw(ObjectServices services,ObjectInstance object,String key,Runnable nativeDraw) {
        var caller=com.openggf.util.EngineCallerAccess.callerOutside(NativeEncounterPaletteAccess.class);
        if(caller.getClassLoader()!=NativeEncounterPaletteAccess.class.getClassLoader())
            throw new SecurityException("Encounter palette projection belongs to native sprite producers");
        var runtime=services==null?null:com.openggf.game.session.WorldSessionPolicyAccess.getService(services.worldSession(),NativeMutatorRuntime.class);
        var replay=runtime==null?null:runtime.encounterPaletteReplay(services,object,key);
        if(replay==null) { nativeDraw.run();return; }
        var graphics=services.graphicsManager();var subject=com.openggf.graphics.SpritePresentation.subject(graphics);
        var scoped=new com.openggf.graphics.SpritePresentation.Subject(subject.id(),subject.part(),subject.suppressed(),
                subject.headScalePercent(),subject.head(),subject.body(),replay);
        com.openggf.graphics.SpritePresentation.withSubject(graphics,scoped,nativeDraw);
    }
}
