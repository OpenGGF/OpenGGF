package com.openggf.mods.mutators;

import com.openggf.game.ModKeySyntax;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Requested preferences for one game profile, including inert uninstalled-owner entries. */
public record MutatorPreferences(String profile, Map<String, Entry> entries) {
    public MutatorPreferences {
        ModKeySyntax.requireManifestId(profile);
        entries = Map.copyOf(entries);
        if (entries.size() > 128) throw new IllegalArgumentException("Too many saved mutators");
        entries.keySet().forEach(ModKeySyntax::requireDisplayKey);
    }

    /**
     * Unknown owners stay inert. Changed schemas require an explicit reset, never guessed migration.
     * An option added later under the same schema takes its declared default; saved values never change.
     */
    public Map<String, MutatorSessionState.Configuration> forSession(List<OwnedMutator> definitions) {
        Map<String, MutatorSessionState.Configuration> result = new LinkedHashMap<>();
        for (OwnedMutator owned : definitions) {
            Entry entry = entries.get(owned.key());
            if (entry == null) continue;
            if (entry.schemaVersion() != owned.definition().schemaVersion()) {
                throw new IllegalArgumentException("Saved schema changed for " + owned.key() + "; reset its preferences");
            }
            Map<String, Object> options = new LinkedHashMap<>(owned.definition().defaults());
            if (!options.keySet().containsAll(entry.options().keySet())) {
                throw new IllegalArgumentException("Unknown saved options for " + owned.key());
            }
            options.putAll(entry.options());
            owned.definition().options().forEach(option -> option.validate(options.get(option.id())));
            result.put(owned.key(), new MutatorSessionState.Configuration(entry.enabled(), options));
        }
        return Map.copyOf(result);
    }

    /** Replaces this session's requested entries while preserving inert orphan preferences. */
    public MutatorPreferences withRequested(MutatorSessionState session) {
        Map<String, Entry> updated = new LinkedHashMap<>(entries);
        Map<String, MutatorSessionState.Configuration> requested = session.requested();
        session.definitions().forEach(owned -> {
            MutatorSessionState.Configuration value = requested.get(owned.key());
            updated.put(owned.key(), new Entry(owned.definition().schemaVersion(), value.enabled(), value.options()));
        });
        return new MutatorPreferences(profile, updated);
    }

    public record Entry(int schemaVersion, boolean enabled, Map<String, Object> options) {
        public Entry {
            if (schemaVersion < 1) throw new IllegalArgumentException("Invalid saved schema version");
            options = new MutatorOptions(options).values();
            if (options.size() > 32) throw new IllegalArgumentException("Too many saved options");
            options.forEach((key, value) -> {
                ModKeySyntax.requireLocalName(key);
                if (value instanceof String text) ModKeySyntax.requireLocalName(text);
            });
        }
    }
}
