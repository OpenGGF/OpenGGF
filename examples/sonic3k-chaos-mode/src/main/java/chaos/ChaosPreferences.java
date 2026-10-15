package chaos;

import com.openggf.mods.ModStorage;
import com.openggf.mods.state.*;
import java.util.*;

/** Bounded owner-local preferences. Retry writes never rerun an activation or reward. */
public final class ChaosPreferences {
    private final ChaosCatalog catalogue;
    private final ModSaveSlot<ChaosSettings> slot;
    private ChaosSettings saved;
    private String error="";
    public ChaosPreferences(ModStorage storage,ChaosCatalog catalogue) {
        this.catalogue=catalogue;
        saved=ChaosSettings.defaults(catalogue);
        slot=new ModSaveSlot<>(storage,"settings.txt","settings-backup.txt",new ModSaveCodec<ChaosSettings>() {
            @Override public String encode(ChaosSettings settings) {
                settings.validate(catalogue);
                String selected=String.join(",",catalogue.effects().stream().map(ChaosCatalog.Effect::id)
                        .filter(settings.selected()::contains).toList());
                return VersionedSettings.empty(1).with("interval",Integer.toString(settings.intervalSeconds()))
                        .with("durationQuarters",Integer.toString(settings.durationQuarters()))
                        .with("selected",selected).with("enabled",Boolean.toString(settings.enabled()))
                        .with("seed",Long.toString(settings.seed())).serialize();
            }
            @Override public ModSaveLoadResult<ChaosSettings> decode(String text) {
                try {
                    var seen=new HashSet<String>();
                    for(String line:text.split("\\R")) {
                        int equal=line.indexOf('=');
                        if(equal>0 && !seen.add(line.substring(0,equal).trim()))
                            return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT);
                    }
                    var document=VersionedSettings.parse(text);
                    if(document.version()!=1) return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.INCOMPATIBLE);
                    var values=document.entries();
                    if(!values.keySet().equals(Set.of("formatVersion","interval","durationQuarters","selected","enabled","seed")))
                        return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT);
                    String enabled=values.get("enabled");
                    if(!enabled.equals("true") && !enabled.equals("false")) throw new IllegalArgumentException("Invalid toggle");
                    String ids=values.get("selected");
                    List<String> selected=ids.isEmpty()?List.of():List.of(ids.split(",",-1));
                    if(new HashSet<>(selected).size()!=selected.size()) throw new IllegalArgumentException("Duplicate effect");
                    var settings=new ChaosSettings(Integer.parseInt(values.get("interval")),
                            Integer.parseInt(values.get("durationQuarters")),new LinkedHashSet<>(selected),
                            Boolean.parseBoolean(enabled),Long.parseLong(values.get("seed"))).validate(catalogue);
                    return ModSaveLoadResult.valid(settings);
                } catch(RuntimeException invalid) {
                    return ModSaveLoadResult.rejected(ModSaveLoadResult.Status.CORRUPT);
                }
            }
        });
        var loaded=slot.load();
        if(loaded.value().isPresent()) saved=loaded.value().orElseThrow();
        else if(loaded.status()!=ModSaveLoadResult.Status.ABSENT) error="Settings unavailable: "+loaded.status();
    }
    public ChaosSettings saved() { return saved; }
    public String error() { return error; }
    public boolean save(ChaosSettings next) {
        var result=slot.save(next.validate(catalogue));
        if(result!=ModSaveWriteResult.SAVED) { error="Settings not saved: "+result; return false; }
        saved=next; error=""; return true;
    }
    /** Explicit Reset is the only path that elects replacement of rejected preference bytes. */
    public boolean reset() { slot.allowReplacement(); return save(ChaosSettings.defaults(catalogue)); }
}
