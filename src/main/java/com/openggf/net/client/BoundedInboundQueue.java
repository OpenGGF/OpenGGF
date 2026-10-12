package com.openggf.net.client;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Small cross-thread event queue; a producer must close its transport on overflow. */
final class BoundedInboundQueue<E> extends AbstractQueue<E> {
    static final int MAX_EVENTS = 512;

    private final ArrayDeque<E> events = new ArrayDeque<>();

    @Override
    public synchronized boolean offer(E event) {
        if (events.size() >= MAX_EVENTS) {
            return false;
        }
        return events.offer(event);
    }

    @Override
    public synchronized E poll() {
        return events.poll();
    }

    @Override
    public synchronized E peek() {
        return events.peek();
    }

    @Override
    public synchronized int size() {
        return events.size();
    }

    @Override
    public synchronized void clear() {
        events.clear();
    }

    @Override
    public synchronized Iterator<E> iterator() {
        return new ArrayList<>(events).iterator();
    }

    synchronized List<E> drain() {
        int count = events.size();
        List<E> drained = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drained.add(events.removeFirst());
        }
        return drained;
    }
}
