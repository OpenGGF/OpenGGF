package com.openggf.mods.code;

import com.openggf.game.ModApi;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

/** Reflection model used by the compatibility snapshot and release tooling. */
public final class ModApiSignatureSurface {
    /**
     * Explicitly audited Java SE types permitted in the compatibility surface.
     * No package-prefix exemption is used: a newly leaked platform type fails closed.
     */
    private static final Set<Class<?>> ALLOWED_PLATFORM_TYPES = Set.of(
            // Jackson binding metadata on TraceMetadata. Annotations carry no
            // structural obligation for creators; the record's components are
            // what the contract pins.
            com.fasterxml.jackson.annotation.JsonIgnoreProperties.class,
            com.fasterxml.jackson.annotation.JsonProperty.class,
            com.fasterxml.jackson.annotation.JsonProperty.Access.class,
            com.fasterxml.jackson.annotation.OptBoolean.class,
            java.io.IOException.class, java.io.InputStream.class,
            AutoCloseable.class, Boolean.class, Class.class, Deprecated.class, Enum.class,
            Exception.class, Float.class, Integer.class, Long.class, Object.class, Record.class,
            Runnable.class, String.class, StringBuilder.class, Throwable.class,
            FunctionalInterface.class, java.lang.annotation.Annotation.class,
            java.lang.annotation.ElementType.class, java.lang.annotation.Retention.class,
            java.lang.annotation.RetentionPolicy.class, java.lang.annotation.Target.class,
            java.lang.reflect.Field.class,
            java.net.URI.class, java.nio.FloatBuffer.class, java.nio.channels.FileChannel.class,
            java.nio.file.Path.class, java.security.GeneralSecurityException.class,
            java.time.Instant.class, java.util.BitSet.class, java.util.Collection.class,
            java.util.EnumSet.class, java.util.List.class, java.util.Map.class,
            java.util.Optional.class, java.util.OptionalInt.class, java.util.Set.class,
            java.util.concurrent.Callable.class, java.util.concurrent.CompletableFuture.class,
            java.util.function.BiConsumer.class, java.util.function.BooleanSupplier.class,
            java.util.function.Consumer.class, java.util.function.Function.class,
            java.util.function.LongSupplier.class, java.util.function.Predicate.class,
            java.util.function.Supplier.class, javax.net.ssl.SSLContext.class);

    /**
     * Engine-owned runtime types that appear in engine signatures but are not part
     * of the creator contract.
     *
     * <p>The recursive walk treats each as a terminal: it is neither required to
     * carry {@link ModApi} nor pinned into the signature surface. That keeps the
     * published creator contract from silently absorbing engine internals just
     * because an engine class that creators do touch happens to expose one.
     *
     * <p>Creators can still receive these references at runtime; the contract
     * promise is simply that nothing about their shape is guaranteed. Adding an
     * entry is an explicit review step, pinned by
     * {@code mods/mod-api-engine-internal-types.txt}. A type that creator content
     * is genuinely expected to consume belongs in the inventory with {@code @ModApi}
     * instead of here.
     */
    private static final Set<String> ENGINE_INTERNAL_TYPES = Set.of(
            // Hardware-timing service and its ROM work ledger: engine scheduling of
            // ROM-paced work, reached via ObjectServices.hardwareTiming().
            "com.openggf.game.timing.HardwareTimingService",
            "com.openggf.game.timing.HardwareTimingSnapshot",
            "com.openggf.game.timing.HardwareWorkHandle",
            "com.openggf.game.timing.HardwareWorkKind",
            "com.openggf.game.timing.HardwareWorkSubmission",
            "com.openggf.game.timing.HardwareServiceBoundary",
            "com.openggf.game.timing.HardwareReadinessAdmissionPolicy",
            "com.openggf.game.timing.RecordedCompletionAuthority",
            "com.openggf.game.timing.RomWorkBudgetScheduler",
            // Initial ProcessSprites assembly: the engine's own level-start dispatch
            // ordering, exposed through GameModule/LevelManager/SpriteManager plumbing.
            "com.openggf.level.InitialFixedSstDispatcher",
            "com.openggf.level.InitialProcessSpritesLevelManagerBase",
            "com.openggf.level.objects.InitialObjectDispatchScope",
            "com.openggf.sprites.managers.InitialPlayableInput",
            "com.openggf.sprites.managers.PlayableSstDispatcher",
            "com.openggf.sprites.managers.ProcessSpritesEpoch",
            "com.openggf.game.InitialProcessSpritesLifecycle",
            "com.openggf.game.LevelAssemblyKind",
            // Solid-contact resolution internals reached from ObjectManager.
            "com.openggf.level.objects.ObjectSolidContactController",
            "com.openggf.game.rewind.snapshot.ObjectManagerSnapshot$CollisionResponseState",
            "com.openggf.game.rewind.snapshot.ObjectManagerSnapshot$CollisionBuildStage",
            // Trace replay profiles: engine test/replay plumbing on GameModule.
            "com.openggf.game.profiles.trace.TracePlaybackProfile",
            "com.openggf.game.profiles.trace.TracePlaybackProfile$LevelIdentity",
            "com.openggf.game.profiles.trace.TracePlaybackProfile$RecordedLevelIdentityProfile",
            // Audio presentation ownership: the producer/sink pipeline behind
            // AudioManager. Creator audio goes through StreamedMusicPort instead.
            "com.openggf.audio.MusicRestoreSink",
            "com.openggf.audio.LiveCaptureAudioHandle",
            "com.openggf.audio.output.AudioPresentationSink",
            "com.openggf.audio.presentation.AudioPresentationFrameView",
            "com.openggf.audio.presentation.AudioPresentationSnapshot",
            "com.openggf.audio.presentation.PresentationMode",
            "com.openggf.audio.smps.SmpsCoordFlagHandlerOwner",
            "com.openggf.audio.smps.SmpsCoordFlagRuntimeState",
            // Host-side observers and input chords.
            "com.openggf.camera.Camera$UpdateObserver",
            "com.openggf.configuration.KeyChord",
            "com.openggf.configuration.KeyChord$Modifier",
            "com.openggf.debug.FrameSampleSink",
            "com.openggf.audio.AudioPresentationTuning",
            "com.openggf.game.timing.HardwareTimingBoundaryObserver",
            // The hardware-timing trace input port. Hard rule 4 confines this
            // contract to the timing port itself; it is never creator API.
            "com.openggf.trace.timing.HardwareTimingSchedule",
            "com.openggf.trace.timing.HardwareCompletionEdge");

    /** Curated engine-internal terminals, for explicit review pinning. */
    public static SortedSet<String> engineInternalTypeNames() {
        return new TreeSet<>(ENGINE_INTERNAL_TYPES);
    }

    private static boolean isEngineInternal(Class<?> type) {
        return ENGINE_INTERNAL_TYPES.contains(type.getName());
    }

    private ModApiSignatureSurface() { }

    public static SortedSet<Class<?>> recursiveTypes() {
        SortedSet<Class<?>> result = new TreeSet<>(Comparator.comparing(Class::getName));
        ArrayDeque<Class<?>> pending = new ArrayDeque<>(ModApiSurfaceInventory.rootTypes());
        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (!isEngine(type) || isEngineInternal(type) || !result.add(type)) continue;
            for (String name : classfileAnnotationReferencedTypeNames(type)) {
                Class<?> referenced = loadReference(name, type.getClassLoader());
                if (isEngine(referenced)) pending.add(referenced);
            }
            for (Class<?> nested : type.getDeclaredClasses())
                if (supported(nested.getModifiers())) pending.add(nested);
            if (type.isSealed())
                for (Class<?> permitted : type.getPermittedSubclasses())
                    if (isEngine(permitted)) pending.add(permitted);
            for (TypeVariable<?> variable : type.getTypeParameters()) {
                for (Type bound : variable.getBounds()) collectType(bound, pending);
                for (Annotation annotation : variable.getAnnotations()) pending.add(annotation.annotationType());
            }
            collectType(type.getGenericSuperclass(), pending);
            for (Type value : type.getGenericInterfaces()) collectType(value, pending);
            if (type.isRecord()) {
                for (RecordComponent value : type.getRecordComponents()) {
                    collectType(value.getGenericType(), pending);
                    for (Annotation annotation : value.getAnnotations()) pending.add(annotation.annotationType());
                }
            }
            for (Constructor<?> value : type.getDeclaredConstructors()) {
                if (supported(value.getModifiers())) collectExecutable(value, pending);
            }
            for (Method value : type.getDeclaredMethods()) {
                if (supported(value.getModifiers())) {
                    collectType(value.getGenericReturnType(), pending);
                    collectExecutable(value, pending);
                }
            }
            for (Field value : type.getDeclaredFields()) {
                if (supported(value.getModifiers())) {
                    collectType(value.getGenericType(), pending);
                    for (Annotation annotation : value.getAnnotations()) pending.add(annotation.annotationType());
                }
            }
            for (Annotation annotation : type.getAnnotations()) pending.add(annotation.annotationType());
        }
        return Collections.unmodifiableSortedSet(result);
    }

    /** Non-JDK, non-engine classes leaked through the recursive public/protected surface. */
    public static SortedSet<Class<?>> externalSignatureTypes() {
        SortedSet<Class<?>> referenced = new TreeSet<>(Comparator.comparing(Class::getName));
        for (Class<?> type : recursiveTypes()) {
            for (String name : classfileAnnotationReferencedTypeNames(type))
                referenced.add(loadReference(name, type.getClassLoader()));
            collectReferenced(type.getGenericSuperclass(), referenced);
            for (TypeVariable<?> variable : type.getTypeParameters()) {
                for (Type bound : variable.getBounds()) collectReferenced(bound, referenced);
                collectAnnotationTypes(variable.getAnnotations(), referenced);
            }
            for (Type value : type.getGenericInterfaces()) collectReferenced(value, referenced);
            collectAnnotationTypes(type.getDeclaredAnnotations(), referenced);
            if (type.isRecord()) {
                for (RecordComponent value : type.getRecordComponents()) {
                    collectReferenced(value.getGenericType(), referenced);
                    collectAnnotationTypes(value.getDeclaredAnnotations(), referenced);
                }
            }
            for (Constructor<?> value : type.getDeclaredConstructors()) {
                if (supported(value.getModifiers())) collectReferenced(value, referenced);
            }
            for (Method value : type.getDeclaredMethods()) {
                if (supported(value.getModifiers())) {
                    collectReferenced(value.getGenericReturnType(), referenced);
                    collectReferenced(value, referenced);
                }
            }
            for (Field value : type.getDeclaredFields()) {
                if (supported(value.getModifiers())) {
                    collectReferenced(value.getGenericType(), referenced);
                    collectAnnotationTypes(value.getDeclaredAnnotations(), referenced);
                }
            }
        }
        referenced.removeIf(value -> value.isPrimitive() || value.getName().startsWith("com.openggf.")
                || ALLOWED_PLATFORM_TYPES.contains(value));
        return Collections.unmodifiableSortedSet(referenced);
    }

    public static SortedSet<String> allowedPlatformTypeNames() {
        return ALLOWED_PLATFORM_TYPES.stream().map(Class::getName)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    public static SortedSet<String> snapshotLines() {
        TreeSet<String> lines = new TreeSet<>();
        for (Class<?> type : recursiveTypes()) {
            lines.addAll(classfileAnnotationLines(type));
            lines.add("TYPE " + kind(type) + " " + typeModifiers(type) + " "
                    + type.getName() + typeParameters(type.getTypeParameters())
                    + " SUPER " + typeName(type.getGenericSuperclass()));
            for (Class<?> nested : type.getDeclaredClasses())
                if (supported(nested.getModifiers()))
                    lines.add("NESTED " + type.getName() + " " + nested.getName());
            if (type.isSealed())
                for (Class<?> permitted : type.getPermittedSubclasses())
                    lines.add("PERMITS " + type.getName() + " " + permitted.getName());
            for (Type value : type.getGenericInterfaces())
                lines.add("INTERFACE " + type.getName() + " " + typeName(value));
            addAnnotations(lines, "TYPE-ANNOTATION " + type.getName(), type.getDeclaredAnnotations());
            if (type.isRecord()) {
                for (RecordComponent value : type.getRecordComponents()) {
                    String owner = "RECORD-COMPONENT " + type.getName() + " " + value.getName();
                    lines.add(owner + " " + typeName(value.getGenericType()));
                    addAnnotations(lines, owner + "-ANNOTATION", value.getDeclaredAnnotations());
                }
            }
            for (Constructor<?> value : type.getDeclaredConstructors())
                if (supported(value.getModifiers())) addExecutable(lines, type, value, null);
            for (Method value : type.getDeclaredMethods())
                if (supported(value.getModifiers())) addExecutable(lines, type, value, value.getGenericReturnType());
            for (Field value : type.getDeclaredFields()) {
                if (!supported(value.getModifiers())) continue;
                String owner = "FIELD " + type.getName() + " " + modifiers(value.getModifiers())
                        + " " + typeName(value.getGenericType()) + " " + value.getName();
                lines.add(owner);
                addAnnotations(lines, owner + "-ANNOTATION", value.getDeclaredAnnotations());
            }
        }
        return Collections.unmodifiableSortedSet(lines);
    }

    /** Canonical classfile annotation attributes for a single type. */
    public static SortedSet<String> classfileAnnotationLines(Class<?> type) {
        return Collections.unmodifiableSortedSet(ModApiClassfileAnnotationSurface.lines(type));
    }

    /** Types referenced by declaration/type-use annotations and their encoded values. */
    public static SortedSet<String> classfileAnnotationReferencedTypeNames(Class<?> type) {
        return Collections.unmodifiableSortedSet(
                ModApiClassfileAnnotationSurface.referencedTypeNames(type));
    }

    /** Policy supplied by release tooling when comparing two signature surfaces. */
    public enum Comparison {
        /** Candidate pins and same-release-line maintenance pins must be identical. */
        EXACT,
        /** A later configured release line may add signatures but may not remove or change them. */
        FORWARD_LATER_RELEASE_LINE
    }

    /**
     * Compares a checked-in baseline with the current surface. Release topology is
     * deliberately supplied by the caller rather than inferred from version numbers.
     */
    public static List<String> signatureViolations(
            Set<String> baseline, Set<String> current, Comparison comparison) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(comparison, "comparison");
        List<String> violations = new ArrayList<>();
        List<String> missing = baseline.stream().filter(line -> !current.contains(line)).sorted().toList();
        if (!missing.isEmpty()) {
            violations.add("Breaking Mod API signature removals/changes:\n"
                    + String.join("\n", missing.stream().limit(100).toList()));
        }
        List<String> additions = current.stream().filter(line -> !baseline.contains(line)).sorted().toList();
        if (!additions.isEmpty() && comparison == Comparison.EXACT) {
            violations.add("Exact Mod API signature comparison rejects additions:\n"
                    + String.join("\n", additions.stream().limit(100).toList()));
        }
        return List.copyOf(violations);
    }

    private static void addExecutable(Set<String> lines, Class<?> ownerType,
                                      Executable value, Type returnType) {
        String category = value instanceof Constructor<?> ? "CONSTRUCTOR" : "METHOD";
        String name = value instanceof Constructor<?> ? "<init>" : value.getName();
        String owner = category + " " + ownerType.getName() + " " + modifiers(value.getModifiers())
                + (value.isVarArgs() ? " varargs" : "")
                + " " + typeParameters(value.getTypeParameters())
                + (returnType == null ? "" : " " + typeName(returnType))
                + " " + name + parameters(value);
        Type[] exceptions = value.getGenericExceptionTypes();
        if (exceptions.length != 0) {
            owner += " THROWS " + Arrays.stream(exceptions).map(ModApiSignatureSurface::typeName)
                    .sorted().collect(java.util.stream.Collectors.joining(","));
        }
        if (value instanceof Method method && method.getDefaultValue() != null)
            owner += " DEFAULT " + annotationValue(method.getDefaultValue());
        lines.add(owner);
        addAnnotations(lines, owner + "-ANNOTATION", value.getDeclaredAnnotations());
    }

    private static String parameters(Executable executable) {
        Type[] types = executable.getGenericParameterTypes();
        Annotation[][] annotations = executable.getParameterAnnotations();
        List<String> values = new ArrayList<>(types.length);
        for (int index = 0; index < types.length; index++) {
            String prefix = Arrays.stream(annotations[index])
                    .map(ModApiSignatureSurface::annotation)
                    .sorted().collect(java.util.stream.Collectors.joining(" "));
            values.add((prefix.isEmpty() ? "" : prefix + " ") + typeName(types[index]));
        }
        return "(" + String.join(",", values) + ")";
    }

    private static void addAnnotations(Set<String> lines, String owner, Annotation[] annotations) {
        Arrays.stream(annotations).map(ModApiSignatureSurface::annotation).sorted()
                .forEach(value -> lines.add(owner + " " + value));
    }

    private static String annotation(Annotation annotation) {
        List<String> elements = new ArrayList<>();
        for (Method element : annotation.annotationType().getDeclaredMethods()) {
            try {
                elements.add(element.getName() + "=" + annotationValue(element.invoke(annotation)));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot read annotation " + annotation, exception);
            }
        }
        Collections.sort(elements);
        return "@" + annotation.annotationType().getName()
                + (elements.isEmpty() ? "" : "(" + String.join(",", elements) + ")");
    }

    private static String annotationValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return "\"" + escape(text) + "\"";
        if (value instanceof Character character) return "'" + escape(character.toString()) + "'";
        if (value instanceof Class<?> type) return typeName(type) + ".class";
        if (value instanceof Enum<?> enumValue)
            return enumValue.getDeclaringClass().getName() + "." + enumValue.name();
        if (value instanceof Annotation annotation) return annotation(annotation);
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<String> elements = new ArrayList<>(length);
            for (int index = 0; index < length; index++) elements.add(annotationValue(Array.get(value, index)));
            return "[" + String.join(",", elements) + "]";
        }
        return String.valueOf(value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String typeName(Type type) {
        if (type == null) return "<none>";
        if (type instanceof Class<?> value) {
            return value.isArray() ? typeName(value.getComponentType()) + "[]" : value.getName();
        }
        if (type instanceof ParameterizedType value) {
            String arguments = Arrays.stream(value.getActualTypeArguments())
                    .map(ModApiSignatureSurface::typeName).collect(java.util.stream.Collectors.joining(","));
            String raw = typeName(value.getRawType());
            if (value.getOwnerType() != null && value.getRawType() instanceof Class<?> rawClass)
                raw = typeName(value.getOwnerType()) + "." + rawClass.getSimpleName();
            return raw + "<" + arguments + ">";
        }
        if (type instanceof WildcardType value) {
            if (value.getLowerBounds().length != 0) return "? super " + typeName(value.getLowerBounds()[0]);
            Type[] upper = value.getUpperBounds();
            return upper.length == 0 || upper[0] == Object.class ? "?"
                    : "? extends " + Arrays.stream(upper).map(ModApiSignatureSurface::typeName)
                    .collect(java.util.stream.Collectors.joining("&"));
        }
        if (type instanceof GenericArrayType value) return typeName(value.getGenericComponentType()) + "[]";
        if (type instanceof TypeVariable<?> value) return value.getName();
        throw new IllegalArgumentException("Unsupported reflective type " + type);
    }

    private static String typeParameters(TypeVariable<?>[] variables) {
        if (variables.length == 0) return "";
        return Arrays.stream(variables).map(variable -> {
            String bounds = Arrays.stream(variable.getBounds()).filter(bound -> bound != Object.class)
                    .map(ModApiSignatureSurface::typeName)
                    .collect(java.util.stream.Collectors.joining("&"));
            String annotations = Arrays.stream(variable.getAnnotations())
                    .map(ModApiSignatureSurface::annotation).sorted()
                    .collect(java.util.stream.Collectors.joining(" "));
            return (annotations.isEmpty() ? "" : annotations + " ") + variable.getName()
                    + (bounds.isEmpty() ? "" : " extends " + bounds);
        }).collect(java.util.stream.Collectors.joining(",", "<", ">"));
    }

    private static String kind(Class<?> type) {
        if (type.isAnnotation()) return "annotation";
        if (type.isEnum()) return "enum";
        if (type.isRecord()) return "record";
        if (type.isInterface()) return "interface";
        return "class";
    }

    private static String modifiers(int value) {
        List<String> result = new ArrayList<>();
        if (Modifier.isPublic(value)) result.add("public");
        if (Modifier.isProtected(value)) result.add("protected");
        if (Modifier.isPrivate(value)) result.add("private");
        if (Modifier.isAbstract(value)) result.add("abstract");
        if (Modifier.isStatic(value)) result.add("static");
        if (Modifier.isFinal(value)) result.add("final");
        if (Modifier.isTransient(value)) result.add("transient");
        if (Modifier.isVolatile(value)) result.add("volatile");
        if (Modifier.isSynchronized(value)) result.add("synchronized");
        if (Modifier.isNative(value)) result.add("native");
        if (Modifier.isStrict(value)) result.add("strictfp");
        return result.isEmpty() ? "-" : String.join(",", result);
    }

    private static String typeModifiers(Class<?> type) {
        String modifiers = modifiers(type.getModifiers());
        if (type.isSealed()) return appendModifier(modifiers, "sealed");
        if (!Modifier.isFinal(type.getModifiers()) && directlyPermittedBySealedType(type))
            return appendModifier(modifiers, "non-sealed");
        return modifiers;
    }

    private static boolean directlyPermittedBySealedType(Class<?> type) {
        List<Class<?>> parents = new ArrayList<>();
        if (type.getSuperclass() != null) parents.add(type.getSuperclass());
        parents.addAll(List.of(type.getInterfaces()));
        return parents.stream().filter(Class::isSealed)
                .flatMap(parent -> Arrays.stream(parent.getPermittedSubclasses()))
                .anyMatch(type::equals);
    }

    private static String appendModifier(String modifiers, String addition) {
        return "-".equals(modifiers) ? addition : modifiers + "," + addition;
    }

    private static void collectExecutable(Executable value, ArrayDeque<Class<?>> pending) {
        for (TypeVariable<?> variable : value.getTypeParameters()) {
            for (Type bound : variable.getBounds()) collectType(bound, pending);
            for (Annotation annotation : variable.getAnnotations()) pending.add(annotation.annotationType());
        }
        for (Type type : value.getGenericParameterTypes()) collectType(type, pending);
        for (Type type : value.getGenericExceptionTypes()) collectType(type, pending);
        for (Annotation annotation : value.getAnnotations()) pending.add(annotation.annotationType());
        for (Annotation[] annotations : value.getParameterAnnotations())
            for (Annotation annotation : annotations) pending.add(annotation.annotationType());
    }

    private static void collectReferenced(Executable value, Set<Class<?>> referenced) {
        for (TypeVariable<?> variable : value.getTypeParameters()) {
            for (Type bound : variable.getBounds()) collectReferenced(bound, referenced);
            collectAnnotationTypes(variable.getAnnotations(), referenced);
        }
        for (Type type : value.getGenericParameterTypes()) collectReferenced(type, referenced);
        for (Type type : value.getGenericExceptionTypes()) collectReferenced(type, referenced);
        collectAnnotationTypes(value.getDeclaredAnnotations(), referenced);
        for (Annotation[] annotations : value.getParameterAnnotations())
            collectAnnotationTypes(annotations, referenced);
    }

    private static void collectAnnotationTypes(Annotation[] annotations, Set<Class<?>> referenced) {
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            referenced.add(annotationType);
            for (Method element : annotationType.getDeclaredMethods()) {
                collectReferenced(element.getGenericReturnType(), referenced);
                collectAnnotationValueTypes(element.getDefaultValue(), referenced);
                try {
                    collectAnnotationValueTypes(element.invoke(annotation), referenced);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot audit annotation " + annotation, exception);
                }
            }
        }
    }

    private static void collectAnnotationValueTypes(Object value, Set<Class<?>> referenced) {
        if (value == null) return;
        Class<?> valueType = value.getClass();
        if (valueType.isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++)
                collectAnnotationValueTypes(Array.get(value, index), referenced);
        } else if (value instanceof Enum<?> enumValue) {
            referenced.add(enumValue.getDeclaringClass());
        } else if (value instanceof Class<?> type) {
            referenced.add(type);
        } else if (value instanceof Annotation annotation) {
            collectAnnotationTypes(new Annotation[]{annotation}, referenced);
        }
    }

    private static void collectReferenced(Type type, Set<Class<?>> referenced) {
        collectReferenced(type, referenced, new HashSet<>());
    }

    private static void collectReferenced(Type type, Set<Class<?>> referenced, Set<Type> seen) {
        if (type == null || !seen.add(type)) return;
        if (type instanceof Class<?> value) {
            while (value.isArray()) value = value.getComponentType();
            referenced.add(value);
        } else if (type instanceof ParameterizedType value) {
            collectReferenced(value.getRawType(), referenced, seen);
            for (Type argument : value.getActualTypeArguments()) collectReferenced(argument, referenced, seen);
        } else if (type instanceof WildcardType value) {
            for (Type bound : value.getLowerBounds()) collectReferenced(bound, referenced, seen);
            for (Type bound : value.getUpperBounds()) collectReferenced(bound, referenced, seen);
        } else if (type instanceof GenericArrayType value) {
            collectReferenced(value.getGenericComponentType(), referenced, seen);
        } else if (type instanceof TypeVariable<?> value) {
            for (Type bound : value.getBounds()) collectReferenced(bound, referenced, seen);
        }
    }

    private static void collectType(Type type, ArrayDeque<Class<?>> pending) {
        collectType(type, pending, new HashSet<>());
    }

    private static void collectType(Type type, ArrayDeque<Class<?>> pending, Set<Type> seen) {
        if (type == null) return;
        if (!seen.add(type)) return;
        if (type instanceof Class<?> value) {
            while (value.isArray()) value = value.getComponentType();
            if (isEngine(value)) pending.add(value);
        } else if (type instanceof ParameterizedType value) {
            collectType(value.getRawType(), pending, seen);
            for (Type argument : value.getActualTypeArguments()) collectType(argument, pending, seen);
        } else if (type instanceof WildcardType value) {
            for (Type bound : value.getLowerBounds()) collectType(bound, pending, seen);
            for (Type bound : value.getUpperBounds()) collectType(bound, pending, seen);
        } else if (type instanceof GenericArrayType value) {
            collectType(value.getGenericComponentType(), pending, seen);
        } else if (type instanceof TypeVariable<?> value) {
            for (Type bound : value.getBounds()) collectType(bound, pending, seen);
        }
    }

    private static boolean isEngine(Class<?> type) {
        return !type.isPrimitive() && type.getName().startsWith("com.openggf.");
    }

    private static Class<?> loadReference(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot load API annotation reference " + name, exception);
        }
    }

    private static boolean supported(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    public static void main(String[] args) {
        SortedSet<Class<?>> types = recursiveTypes();
        if (args.length != 0 && "--enumerate".equals(args[0])) {
            types.forEach(type -> System.out.println(type.getName()));
            return;
        }
        List<String> missing = types.stream()
                .filter(type -> !type.isAnnotationPresent(ModApi.class))
                .map(Class::getName)
                .toList();
        if (args.length != 0 && "--missing".equals(args[0])) {
            missing.forEach(System.out::println);
            return;
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Recursive API types lack @ModApi: " + missing);
        SortedSet<Class<?>> external = externalSignatureTypes();
        if (!external.isEmpty()) {
            external.forEach(type -> System.err.println("EXTERNAL " + type.getName()));
            throw new IllegalStateException("External API signature leaks: "
                    + external.stream().map(Class::getName).toList());
        }
        if (args.length != 0 && "--snapshot".equals(args[0]))
            snapshotLines().forEach(System.out::println);
        else
            types.forEach(type -> System.out.println(type.getName()));
    }
}
