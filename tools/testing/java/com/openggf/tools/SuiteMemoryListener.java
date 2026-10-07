package com.openggf.tools;

import com.sun.management.ThreadMXBean;
import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.management.ObjectName;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.*;

/** Temporary-classpath Surefire observer. Inputs: openggf.memory.events and
 * openggf.memory.gc (between-class/none). Origin: 2026-10-07 ordinary-suite
 * memory-cause investigation. Completed windows retain strings/counters only. */
public final class SuiteMemoryListener implements TestExecutionListener {
    private static State shared;
    private final State state;
    public SuiteMemoryListener() {
        synchronized (SuiteMemoryListener.class) {
            if (shared == null && System.getProperty("openggf.memory.events") != null) shared = new State();
            state = shared;
        }
    }
    private record Snapshot(long heapBytes, long committedHeapBytes, long metaspaceBytes,
                            long directBytes, long mappedBytes, Long rssBytes, Long swapBytes,
                            int threads, Integer osThreads, long gcCount, long gcMillis) {
        Map<String,Object> fields() {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("heapBytes",heapBytes); m.put("committedHeapBytes",committedHeapBytes);
            m.put("metaspaceBytes",metaspaceBytes); m.put("directBytes",directBytes); m.put("mappedBytes",mappedBytes);
            m.put("rssBytes",rssBytes); m.put("swapBytes",swapBytes); m.put("threads",threads);m.put("osThreads",osThreads);
            m.put("gcCount",gcCount); m.put("gcMillis",gcMillis); return m;
        }
    }
    private static final class Window {
        final Map<String,Object> identity;
        final long started = System.nanoTime(), thread = Thread.currentThread().threadId(), allocated;
        long peakHeap, peakRss;
        Window(Map<String,Object> identity, long allocated) { this.identity=identity; this.allocated=allocated; }
        synchronized void observe(Snapshot s) { peakHeap=Math.max(peakHeap,s.heapBytes); if(s.rssBytes!=null)peakRss=Math.max(peakRss,s.rssBytes); }
    }
    private static final class State {
        final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        final java.lang.management.ThreadMXBean allThreads = ManagementFactory.getThreadMXBean();
        final ThreadMXBean allocation = allThreads instanceof ThreadMXBean t && t.isThreadAllocatedMemorySupported() ? t : null;
        final Map<String,Window> active = new ConcurrentHashMap<>();
        final ScheduledExecutorService sampler;
        final BufferedWriter output;
        String previousClass;
        int executed, skipped, failed, plans, completedPlans, snapshots;
        long highFloor, highRss;
        long nextHeartbeat;
        State() {
            try { output=Files.newBufferedWriter(Path.of(System.getProperty("openggf.memory.events")),StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
            catch(IOException e) { throw new UncheckedIOException(e); }
            if(allocation!=null&&!allocation.isThreadAllocatedMemoryEnabled())allocation.setThreadAllocatedMemoryEnabled(true);
            sampler=Executors.newSingleThreadScheduledExecutor(r->{ Thread t=new Thread(r,"suite-memory-sampler");t.setDaemon(true);return t;});
            sampler.scheduleWithFixedDelay(()->{
                Snapshot s=sample();active.values().forEach(w->w.observe(s));
                if(!active.isEmpty() && System.nanoTime()>=nextHeartbeat) {
                    System.out.println("MEMORY-HEARTBEAT heapMiB="+s.heapBytes/(1024*1024)+" rssBytes="+s.rssBytes);
                    nextHeartbeat=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
                }
            },1,1,TimeUnit.SECONDS);
            emit(new LinkedHashMap<>(Map.of("type","observer-start","pid",ProcessHandle.current().pid(),"maxHeapBytes",Runtime.getRuntime().maxMemory())));
            Runtime.getRuntime().addShutdownHook(new Thread(()->{
                sampler.shutdownNow();
                // A finished class/plan callback can still own Jupiter fixtures.
                // This final floor is collected after launcher execution returns.
                // Within-plan class floors remain retention candidates: PER_CLASS
                // instances may stay live until the complete plan is released.
                if (active.isEmpty() && plans == completedPlans && previousClass != null) boundary(null);
                emit(new LinkedHashMap<>(Map.of("type","observer-end","executed",executed,"skipped",skipped,"failures",failed,
                        "plans",plans,"completedPlans",completedPlans,"activeWindows",active.size())));
                try { output.close(); } catch(IOException ignored) {}
            },"suite-memory-close"));
            System.out.println("OPENGGF-MEMORY-OBSERVER pid="+ProcessHandle.current().pid());
        }
        long allocated(long thread) { return allocation==null?-1:allocation.getThreadAllocatedBytes(thread); }
        Snapshot sample() {
            long direct=0,mapped=0,meta=0,count=0,millis=0;
            for(BufferPoolMXBean b:ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                if(b.getName().equals("direct"))direct+=b.getMemoryUsed(); if(b.getName().equals("mapped"))mapped+=b.getMemoryUsed();
            }
            for(MemoryPoolMXBean b:ManagementFactory.getMemoryPoolMXBeans())if(b.getName().equals("Metaspace"))meta=b.getUsage().getUsed();
            for(GarbageCollectorMXBean b:ManagementFactory.getGarbageCollectorMXBeans()){count+=Math.max(0,b.getCollectionCount());millis+=Math.max(0,b.getCollectionTime());}
            Long rss=null,swap=null;Integer osThreads=null;
            try { for(String line:Files.readAllLines(Path.of("/proc/self/status"))) {
                if(line.startsWith("VmRSS:"))rss=Long.parseLong(line.split("\\s+")[1])*1024;
                if(line.startsWith("VmSwap:"))swap=Long.parseLong(line.split("\\s+")[1])*1024;
                if(line.startsWith("Threads:"))osThreads=Integer.parseInt(line.split("\\s+")[1]);
            }}catch(Exception ignored){}
            MemoryUsage h=memory.getHeapMemoryUsage();
            return new Snapshot(h.getUsed(),h.getCommitted(),meta,direct,mapped,rss,swap,allThreads.getThreadCount(),osThreads,count,millis);
        }
        synchronized void emit(Map<String,Object> row) {
            row.put("elapsedSeconds",ManagementFactory.getRuntimeMXBean().getUptime()/1000.0);
            try { output.write(MemoryProfileLauncher.json(row));output.newLine();output.flush(); }
            catch(IOException e) { throw new UncheckedIOException(e); }
        }
        void boundary(String next) {
            Snapshot before=sample(); boolean observed=false;
            if(System.getProperty("openggf.memory.gc","between-class").equals("between-class")) {
                memory.gc();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(System.nanoTime()<deadline) {
                    if(sample().gcCount>before.gcCount){observed=true;break;}
                    try{Thread.sleep(10);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
                }
            }
            Snapshot s=sample();Map<String,Object> row=s.fields(); row.put("type","boundary");
            row.put("afterClass",previousClass);row.put("beforeClass",next);row.put("gcObserved",observed);
            row.put("allPlansFinished",plans==completedPlans);
            row.put("beforeGcHeapBytes",before.heapBytes);emit(row);
            if(previousClass!=null && snapshots<12 && (s.heapBytes>highFloor+128L*1024*1024 || s.rssBytes!=null&&s.rssBytes>highRss+512L*1024*1024)) {
                Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("type","snapshot");evidence.put("afterClass",previousClass);
                evidence.put("heapBytes",s.heapBytes);evidence.put("rssBytes",s.rssBytes);
                evidence.put("histogram",diagnostic("gcClassHistogram",new String[]{"-all"},45));
                evidence.put("nativeMemory",diagnostic("vmNativeMemory",new String[]{"summary","scale=KB"},100));emit(evidence);snapshots++;
            }
            highFloor=Math.max(highFloor,s.heapBytes);if(s.rssBytes!=null)highRss=Math.max(highRss,s.rssBytes);
            previousClass=null;
        }
        String diagnostic(String operation,String[] arguments,int lines) {
            try {
                Object result=ManagementFactory.getPlatformMBeanServer().invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"),operation,
                        new Object[]{arguments},new String[]{"[Ljava.lang.String;"});
                return result.toString().lines().limit(lines).reduce("",(a,b)->a+b+"\n");
            }catch(Exception e){return e.toString();}
        }
    }
    private TestPlan plan;
    @Override public void testPlanExecutionStarted(TestPlan p){if(state!=null){plan=p;state.plans++;}}
    private boolean topClass(TestIdentifier id) {
        return id.getSource().orElse(null) instanceof ClassSource
                && plan.getParent(id).map(p->!(p.getSource().orElse(null) instanceof ClassSource)).orElse(true);
    }
    private Map<String,Object> identity(TestIdentifier id) {
        Map<String,Object> m=new LinkedHashMap<>();m.put("id",id.getUniqueId());m.put("displayName",id.getDisplayName());
        for(TestIdentifier i=id;i!=null;i=plan.getParent(i).orElse(null)) {
            var source=i.getSource().orElse(null);
            if(source instanceof MethodSource s){m.put("className",s.getClassName());m.put("methodName",s.getMethodName());break;}
            if(source instanceof ClassSource s){m.put("className",s.getClassName());break;}
        }return m;
    }
    @Override public void executionStarted(TestIdentifier id) {
        if(state==null)return;
        if(topClass(id)) {
            state.boundary(((ClassSource)id.getSource().orElseThrow()).getClassName());
            System.out.println("MEMORY-CLASS "+id.getDisplayName());
        }
        if(id.isTest()||topClass(id)) {
            Window w=new Window(identity(id),state.allocated(Thread.currentThread().threadId()));w.observe(state.sample());state.active.put(id.getUniqueId(),w);
        }
    }
    @Override public void executionSkipped(TestIdentifier id,String reason) {
        if(state==null)return;if(id.isTest())state.skipped++;
        Map<String,Object> m=identity(id);m.put("type","skipped");m.put("reason",reason);state.emit(m);
    }
    @Override public void executionFinished(TestIdentifier id,TestExecutionResult result) {
        if(state==null)return;if(id.isTest())state.executed++;
        if(result.getStatus()==TestExecutionResult.Status.FAILED)state.failed++;
        Window w=state.active.remove(id.getUniqueId());if(w==null)return;
        Snapshot s=state.sample();w.observe(s);state.active.values().forEach(other->other.observe(s));
        Map<String,Object> row=w.identity;row.put("type",id.isTest()?"test":"class");row.put("status",result.getStatus().name());
        row.put("durationSeconds",(System.nanoTime()-w.started)/1e9);row.put("peakHeapBytes",w.peakHeap);row.put("peakRssBytes",w.peakRss);
        long a=state.allocated(w.thread);row.put("threadAllocatedBytes",a<0||w.allocated<0?null:a-w.allocated);
        result.getThrowable().ifPresent(t->{
            String detail=t.toString();row.put("failure",detail.substring(0,Math.min(4096,detail.length())));
            if(detail.length()>4096)row.put("failureTruncated",true);
        });state.emit(row);
        if(topClass(id))state.previousClass=((ClassSource)id.getSource().orElseThrow()).getClassName();
    }
    @Override public void testPlanExecutionFinished(TestPlan p) {
        if(state!=null){state.completedPlans++;plan=null;}
    }
}
