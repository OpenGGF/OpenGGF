package com.openggf.mods;

import com.openggf.io.ModAssetRoot;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Completes a source manifest from an immutable build snapshot without loading creator classes. */
public final class ModEntrypointInference {
    private static final String GGF_MOD = "com/openggf/mods/code/GgfMod";

    private ModEntrypointInference() { }

    public static byte[] completeManifest(ModAssetRoot assets, List<String> names)
            throws IOException, ModManifestException {
        List<String> classes = names.stream().filter(name -> name.endsWith(".class")).toList();
        byte[] manifest = assets.readBounded(DefaultModRepositoryScanner.MANIFEST_PATH,
                assets.limits().maxMetadataBytes());
        try {
            return new ModManifestParser(assets.limits()).completeEntrypoint(manifest, !classes.isEmpty(),
                    () -> infer(assets, classes));
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }

    private static String infer(ModAssetRoot assets, List<String> classes) {
        Map<String, List<String>> children = new HashMap<>();
        Set<String> concrete = new HashSet<>();
        for (String path : classes) {
            ClassReader type;
            try {
                type = new ClassReader(assets.readBounded(path, assets.limits().maxAssetBytes()));
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot read " + path + " for entrypoint inference", failure);
            } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
                throw new IllegalArgumentException("Invalid class file for entrypoint inference: " + path, failure);
            }
            String name = type.getClassName();
            if (!path.equals(name + ".class")) {
                throw new IllegalArgumentException("Class path does not match its binary name: " + path);
            }
            if ((type.getAccess() & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) == 0) {
                concrete.add(name);
            }
            children.computeIfAbsent(type.getSuperName(), ignored -> new ArrayList<>()).add(name);
            for (String implemented : type.getInterfaces()) {
                children.computeIfAbsent(implemented, ignored -> new ArrayList<>()).add(name);
            }
        }
        Set<String> visited = new HashSet<>();
        var pending = new ArrayDeque<String>();
        var candidates = new TreeSet<String>();
        pending.add(GGF_MOD);
        while (!pending.isEmpty()) {
            String type = pending.removeFirst();
            if (!visited.add(type)) continue;
            if (concrete.contains(type)) candidates.add(type.replace('/', '.'));
            pending.addAll(children.getOrDefault(type, List.of()));
        }
        if (candidates.size() != 1) {
            String found = candidates.isEmpty() ? "none" : String.join(", ", candidates);
            throw new IllegalArgumentException("Cannot infer entrypoint: expected exactly one concrete GgfMod; candidates: "
                    + found + ". Declare entrypoint explicitly if the project has multiple implementations.");
        }
        return candidates.first();
    }
}
