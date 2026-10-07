package com.openggf.mods.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Bounded ordered pool with explicit value-copy capture and atomic restore. Oldest entries expire first. */
@com.openggf.game.ModApi
public final class CapturedPool<T> {
    private final int capacity;
    private final Function<T, T> copy;
    private final ArrayList<T> values=new ArrayList<>();
    public CapturedPool(int capacity, Function<T, T> snapshotCopy) {
        if (capacity < 1 || capacity > 8192) throw new IllegalArgumentException("Pool capacity outside 1..8192");
        this.capacity=capacity; this.copy=Objects.requireNonNull(snapshotCopy,"snapshotCopy");
    }
    public int capacity() { return capacity; }
    public int size() { return values.size(); }
    public T get(int index) { return values.get(index); }
    public void add(T value) {
        Objects.requireNonNull(value,"value");
        if (values.size()==capacity) values.removeFirst();
        values.add(value);
    }
    public void clear() { values.clear(); }
    public List<T> snapshot() { return copyValues(values); }
    public void restore(List<T> snapshot) {
        Objects.requireNonNull(snapshot,"snapshot");
        if (snapshot.size()>capacity) throw new IllegalArgumentException("Snapshot exceeds pool capacity");
        List<T> replacement=copyValues(snapshot);
        values.clear(); values.addAll(replacement);
    }
    private List<T> copyValues(List<T> source) {
        return source.stream().map(value -> Objects.requireNonNull(copy.apply(Objects.requireNonNull(value)),
                "Pool snapshot copy returned null")).toList();
    }
}
