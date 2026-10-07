package com.openggf.tools.modsdk;

import com.openggf.version.AppVersion;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

/** Materializes complete maintained starters from SDK resources, without a source checkout. */
final class StarterProjects {
    private static final String ROOT = "META-INF/openggf-mod-sdk/starters/";
    private StarterProjects() { }

    static void copyMaintained(Path output, String kind, String id, String javaPackage) throws IOException {
        String fixture = kind.equals("character") ? "sample-character-src" : "sample-standalone-src";
        String oldId = kind.equals("character") ? "phase3-character" : "phase3-standalone";
        String oldPackage = kind.equals("character") ? "example.phase3character" : "example.phase3standalone";
        String prefix = "mods/" + fixture + "/project/";
        for (String entry : new String(read(ROOT + "index.txt"), StandardCharsets.UTF_8).lines().toList()) {
            if (!entry.startsWith(prefix)) continue;
            String relative = entry.substring(prefix.length()).replace(oldPackage.replace('.', '/'), javaPackage.replace('.', '/'));
            Path path = output.resolve(relative);
            Files.createDirectories(path.getParent());
            byte[] bytes = read(ROOT + entry);
            if (relative.endsWith(".java") || relative.endsWith(".xml") || relative.endsWith(".yaml")
                    || relative.endsWith(".json") || relative.endsWith(".md")) {
                bytes = new String(bytes, StandardCharsets.UTF_8).replace(oldPackage, javaPackage)
                        .replace(oldId, id).replace("0.7.prerelease", AppVersion.identity().baseVersion())
                        .getBytes(StandardCharsets.UTF_8);
            }
            if (relative.endsWith(".base64")) {
                path = output.resolve(relative.substring(0, relative.length() - ".base64".length()));
                bytes = Base64.getMimeDecoder().decode(bytes);
            }
            Files.write(path, bytes);
        }
        try (var files = Files.walk(output)) {
            for (Path encoded : files.filter(p -> p.getFileName().toString().equals("binary-assets.properties")).toList()) {
                Properties assets = new Properties();
                try (InputStream input = Files.newInputStream(encoded)) { assets.load(input); }
                for (String name : assets.stringPropertyNames())
                    Files.write(encoded.getParent().resolve(name), Base64.getDecoder().decode(assets.getProperty(name)));
                Files.delete(encoded);
            }
        }
    }

    private static byte[] read(String name) throws IOException {
        try (InputStream input = StarterProjects.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) throw new IOException("SDK starter resource is missing: " + name);
            return input.readAllBytes();
        }
    }
}
