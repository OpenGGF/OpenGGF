package com.openggf.control;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mod-owned named actions, each with up to eight physical alternatives (64 actions maximum). */
@com.openggf.game.ModApi
public final class ActionMap {
    private final LinkedHashMap<String, List<PhysicalBinding>> bindings = new LinkedHashMap<>();
    private long revision;
    public ActionMap bind(String action, PhysicalBinding... alternatives) {
        Objects.requireNonNull(action, "action");
        if (!action.matches("[A-Za-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid action name");
        List<PhysicalBinding> copy = List.of(alternatives);
        if (copy.isEmpty() || copy.size() > 8) throw new IllegalArgumentException("Action needs 1..8 alternatives");
        if (!bindings.containsKey(action) && bindings.size() >= 64) throw new IllegalArgumentException("Action map exceeds 64 actions");
        if (!copy.equals(bindings.put(action, copy))) revision++;
        return this;
    }
    public List<PhysicalBinding> bindings(String action) { return bindings.getOrDefault(action, List.of()); }
    public Map<String, List<PhysicalBinding>> bindings() { return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(bindings)); }
    long revision() { return revision; }
    /** Stable UTF-8 text suitable for owner-scoped storage; no game-specific settings are included. */
    public String encode() {
        StringBuilder out = new StringBuilder("openggf-actions=1\n");
        bindings.forEach((name, values) -> {
            out.append(name).append('=');
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) out.append('|');
                out.append(values.get(i).encode());
            }
            out.append('\n');
        });
        return out.toString();
    }
    /** Malformed entries retain existing defaults; an unknown format version changes nothing. */
    public void read(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() > 65_536) throw new IllegalArgumentException("Action settings exceed 64 KiB");
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !lines[0].equals("openggf-actions=1")) return;
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf('=');
            if (separator < 1) continue;
            try {
                String[] encoded = lines[i].substring(separator + 1).split("\\|", -1);
                if (encoded.length > 8) continue;
                PhysicalBinding[] values = new PhysicalBinding[encoded.length];
                for (int j = 0; j < encoded.length; j++) values[j] = PhysicalBinding.decode(encoded[j]);
                bind(lines[i].substring(0, separator), values);
            } catch (IllegalArgumentException ignored) { /* a malformed entry retains defaults */ }
        }
    }
    /** Optional keyboard/standard-pad conventions; mods may replace any binding. */
    public static ActionMap menu() {
        return new ActionMap()
                .bind("accept",new PhysicalBinding('K',-1,257,1),new PhysicalBinding('K',-1,32,1),
                        new PhysicalBinding('B',0,0,1),new PhysicalBinding('B',0,2,1))
                .bind("back",new PhysicalBinding('K',-1,256,1),new PhysicalBinding('K',-1,259,1),new PhysicalBinding('B',0,1,1))
                .bind("up",new PhysicalBinding('K',-1,265,1),new PhysicalBinding('B',0,11,1))
                .bind("down",new PhysicalBinding('K',-1,264,1),new PhysicalBinding('B',0,13,1))
                .bind("left",new PhysicalBinding('K',-1,263,1),new PhysicalBinding('B',0,14,1))
                .bind("right",new PhysicalBinding('K',-1,262,1),new PhysicalBinding('B',0,12,1));
    }
}
