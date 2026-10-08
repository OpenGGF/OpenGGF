package com.openggf.tools;

import org.objectweb.asm.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Native metadata preflight for the 2026-10-08 generated retention contract.
 * Queries members without reading static fields or running creator code. This
 * detects absent types/member metadata; successful lookup alone does not prove
 * every invocation works. Exact-class preservation and execution controls are
 * required alongside the audit, especially on this experimental VM.
 */
public final class NativeModMemberAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected: <members.tsv>");
        verify(Path.of(args[0]));
    }

    public static void verify(Path manifest) throws Exception {
        Map<String, Set<String>> members = new HashMap<>();
        int failures = 0;
        int checked = 0;
        for (String line : Files.readAllLines(manifest)) {
            String[] columns = line.split("\t", -1);
            if ((columns.length != 2 || !columns[0].equals("C"))
                    && (columns.length != 4 || !(columns[0].equals("F") || columns[0].equals("M")))) {
                throw new IllegalArgumentException("Malformed contract line: " + line);
            }
            checked++;
            try {
                String owner = columns[1];
                Set<String> available = members.get(owner);
                if (available == null) {
                    Class<?> type = Class.forName(owner, false, NativeModMemberAudit.class.getClassLoader());
                    available = new HashSet<>();
                    available.add("C\t" + owner);
                    for (var field : type.getDeclaredFields()) {
                        available.add("F\t" + owner + "\t" + field.getName() + "\t" + Type.getDescriptor(field.getType()));
                    }
                    for (var method : type.getDeclaredMethods()) {
                        available.add("M\t" + owner + "\t" + method.getName() + "\t" + Type.getMethodDescriptor(method));
                    }
                    for (var constructor : type.getDeclaredConstructors()) {
                        available.add("M\t" + owner + "\t<init>\t" + Type.getConstructorDescriptor(constructor));
                    }
                    members.put(owner, available);
                }
                if (!available.contains(line)) throw new ReflectiveOperationException("member metadata absent");
            } catch (Throwable failure) {
                if (failure instanceof ThreadDeath || failure instanceof VirtualMachineError) throw failure;
                if (++failures <= 20) System.err.println("MISSING native member: " + line + "; " + failure);
            }
        }
        if (checked == 0) throw new IllegalArgumentException("Empty member contract");
        if (failures != 0) throw new AssertionError("Missing native members: " + failures + "/" + checked);
        System.out.println("PASS native member audit: " + checked + " entries; " + members.size() + " types");
    }
}
