package com.openggf.mods.state;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable ordered key=value settings. Unversioned files retain legacy version zero and their format. */
@com.openggf.game.ModApi
public final class VersionedSettings {
    public static final String VERSION_KEY="formatVersion";
    private final Map<String,String> values;
    private VersionedSettings(Map<String,String> values) {
        this.values=Collections.unmodifiableMap(new LinkedHashMap<>(values));
        if (version()<0) throw new IllegalArgumentException("Negative settings version");
    }
    public static VersionedSettings parse(String text) {
        Objects.requireNonNull(text,"text");
        Map<String,String> values=new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            int equal=line.indexOf('=');
            if (equal<=0) continue;
            values.put(line.substring(0,equal).trim(),line.substring(equal+1).trim());
        }
        return new VersionedSettings(values);
    }
    public static VersionedSettings empty(int version) {
        if (version<0) throw new IllegalArgumentException("Negative settings version");
        return version==0 ? new VersionedSettings(Map.of())
                : new VersionedSettings(Map.of(VERSION_KEY,Integer.toString(version)));
    }
    public int version() {
        try { return Integer.parseInt(values.getOrDefault(VERSION_KEY,"0")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid settings version",invalid); }
    }
    /** Rejects unknown future formats before a consumer overwrites them. */
    public VersionedSettings requireVersion(int supportedVersion) {
        if (version()!=supportedVersion) throw new IllegalArgumentException("Unsupported settings version: " + version());
        return this;
    }
    public Map<String,String> entries() { return values; }
    public VersionedSettings with(String key, String value) {
        if (key==null || key.isBlank() || key.indexOf('=')>=0 || key.indexOf('\n')>=0 || key.indexOf('\r')>=0)
            throw new IllegalArgumentException("Invalid settings key");
        Objects.requireNonNull(value,"value");
        if (value.indexOf('\n')>=0 || value.indexOf('\r')>=0) throw new IllegalArgumentException("Settings value must fit one line");
        Map<String,String> edited=new LinkedHashMap<>(values); edited.put(key,value);
        return new VersionedSettings(edited);
    }
    public String serialize() {
        StringBuilder text=new StringBuilder();
        values.forEach((key,value)->text.append(key).append('=').append(value).append('\n'));
        return text.toString();
    }
}
