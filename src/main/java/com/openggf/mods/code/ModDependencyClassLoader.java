package com.openggf.mods.code;

import java.net.URL;
import java.net.URLClassLoader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parent-first to engine classes, then owner-local classes, then direct declared
 * dependencies. Dependency lookup deliberately does not traverse transitively.
 */
public final class ModDependencyClassLoader extends URLClassLoader {
    static {
        registerAsParallelCapable();
    }

    private final List<ModDependencyClassLoader> dependencyLoaders;

    public ModDependencyClassLoader(String modId, URL[] jarUrls, ClassLoader engineParent,
                                    List<ModDependencyClassLoader> dependencyLoaders) {
        super("mod:" + Objects.requireNonNull(modId, "modId"),
                Objects.requireNonNull(jarUrls, "jarUrls"),
                Objects.requireNonNull(engineParent, "engineParent"));
        this.dependencyLoaders = List.copyOf(
                Objects.requireNonNull(dependencyLoaders, "dependencyLoaders"));
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException ownMiss) {
            for (ModDependencyClassLoader dependency : dependencyLoaders) {
                try {
                    return dependency.loadOwnOrParent(name);
                } catch (ClassNotFoundException ignored) {
                    // Continue through this owner's declared direct dependencies.
                }
            }
            throw ownMiss;
        }
    }

    Class<?> loadOwnOrParent(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) return loaded;
            try {
                return getParent().loadClass(name);
            } catch (ClassNotFoundException parentMiss) {
                return super.findClass(name);
            }
        }
    }

    @Override
    public URL findResource(String name) {
        return null;
    }

    @Override
    public Enumeration<URL> findResources(String name) throws IOException {
        return Collections.emptyEnumeration();
    }
}
