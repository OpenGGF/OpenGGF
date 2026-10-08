package com.openggf.mods.scene.host.music;

/** Bounded presentation residuals; no PCM crosses the creator boundary or changes the song clock. */
final class PartCueMixer implements AutoCloseable {
    private short[] full, masked;
    private final int sampleRate;
    private final Voice[] voices = new Voice[6];
    PartCueMixer(short[] full, short[] masked, int sampleRate) {
        if (sampleRate <= 0 || full.length != masked.length || (full.length & 1) != 0)
            throw new IllegalArgumentException("invalid cue PCM");
        this.full = full; this.masked = masked; this.sampleRate = sampleRate;
    }
    synchronized boolean play(long source, int length, double from, double to, double gain, double pan) {
        if (length < 1 || length > sampleRate / 4 || !bounded(from,.5,2) || !bounded(to,.5,2)
                || !bounded(gain,0,1) || !bounded(pan,-1,1) || source < 0)
            throw new IllegalArgumentException("invalid part cue");
        if (full == null) return false;
        if (source >= full.length / 2) throw new IllegalArgumentException("cue source outside song");
        if (gain == 0) return false;
        for (int i=0;i<voices.length;i++) if (voices[i] == null) {
            voices[i] = new Voice(source,length,from,to,gain,pan,sampleRate); return true;
        }
        return false;
    }
    static boolean bounded(double value, double min, double max) { return Double.isFinite(value) && value >= min && value <= max; }
    synchronized void mix(short[] target, int frames) {
        if (full == null) return;
        for (int frame=0;frame<frames;frame++) {
            double left=target[frame*2],right=target[frame*2+1];
            for (int slot=0;slot<voices.length;slot++) {
                Voice v=voices[slot]; if(v==null)continue;
                if(v.age>=v.length || v.source>=full.length/2) { voices[slot]=null;continue; }
                double envelope = Math.min(1,v.age/(double)v.attack) * v.decay
                        * Math.min(1,Math.max(0,v.length-v.age-1)/(double)v.tail)
                        * Math.min(1,Math.max(0,full.length/2-v.source-1)/Math.max(1,sampleRate/500)) * v.gain;
                int sample=(int)v.source; double fraction=v.source-sample;
                int next=Math.min(full.length/2-1,sample+1);
                for(int channel=0;channel<2;channel++) {
                    double first=full[sample*2+channel]-masked[sample*2+channel];
                    double second=full[next*2+channel]-masked[next*2+channel];
                    double balance=channel==0?Math.min(1,1-v.pan):Math.min(1,1+v.pan);
                    double value=(first+(second-first)*fraction)*envelope*balance;
                    if(channel==0)left+=value;else right+=value;
                }
                v.source+=v.from+(v.to-v.from)*v.age/Math.max(1.0,v.length-1.0);
                v.age++;v.decay*=v.decayStep;
                if(v.age==v.length)voices[slot]=null;
            }
            target[frame*2]=clamp(left);target[frame*2+1]=clamp(right);
        }
    }
    private static short clamp(double value) { return (short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(value))); }
    @Override public synchronized void close() { java.util.Arrays.fill(voices,null);full=null;masked=null; }
    private static final class Voice {
        double source,decay=1;
        final double from,to,gain,pan,decayStep;
        final int length,attack,tail;
        int age;
        Voice(long source,int length,double from,double to,double gain,double pan,int rate) {
            this.source=source;this.length=length;this.from=from;this.to=to;this.gain=gain;this.pan=pan;
            attack=Math.max(1,Math.min(length/2,rate/250)); // 4 ms complements part audibility smoothing.
            tail=Math.max(1,Math.min(length/2,rate/500));
            decayStep=Math.pow(10,-1.8/Math.max(1,length-1)); // -36 dB before the zero tail.
        }
    }
}
