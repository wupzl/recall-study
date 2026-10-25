package cn.recall.study;

import java.util.Random;

/** Pure monotonic-clock state machine. Deadlines are measured in active session milliseconds. */
public final class SessionEngine {
    public static final int NONE = 0, RECALL = 1, STUDY = 2, COMPLETE = 3;
    private final Random random;
    public int studyMin=20, studyMax=30, restMin=5, restMax=8;
    public long targetMs, elapsedBefore, anchor, phaseEnd, phaseLength;
    public boolean active, paused, reviewing;
    public int reviews;

    public SessionEngine(Random random) { this.random = random; }
    public static boolean validRange(int min,int max) { return min>=1 && max<=90 && min<=max; }
    public void configure(int studyMin,int studyMax,int restMin,int restMax) {
        if(active) throw new IllegalStateException("学习进行中不能修改节奏");
        if(!validRange(studyMin,studyMax)||!validRange(restMin,restMax)) throw new IllegalArgumentException("时间范围应为 1～90 分钟，且下限不能大于上限");
        this.studyMin=studyMin; this.studyMax=studyMax; this.restMin=restMin; this.restMax=restMax;
    }
    public void start(int minutes, long now) {
        if (minutes < 1 || minutes > 90) throw new IllegalArgumentException("时长应为 1～90 分钟");
        targetMs = minutes * 60000L;
        elapsedBefore = 0; anchor = now; reviews = 0;
        active = true; paused = false; reviewing = false;
        phaseLength = interval(studyMin,studyMax); phaseEnd = phaseLength;
    }
    public long elapsed(long now) {
        return Math.min(targetMs, Math.max(0, elapsedBefore + (active && !paused ? Math.max(0, now - anchor) : 0)));
    }
    public long remaining(long now) { return Math.max(0, targetMs - elapsed(now)); }
    public long phaseRemaining(long now) { return Math.min(remaining(now), Math.max(0, phaseEnd - elapsed(now))); }
    public int advance(long now) {
        if (!active || paused) return NONE;
        long elapsed = elapsed(now);
        if (elapsed >= targetMs) {
            elapsedBefore = targetMs; active = false; paused = false;
            return COMPLETE;
        }
        if (elapsed >= phaseEnd) {
            reviewing = !reviewing;
            if (reviewing) { reviews++; phaseLength = interval(restMin,restMax); }
            else phaseLength = interval(studyMin,studyMax);
            // A late callback starts a full rest, never a burst of missed reminders.
            phaseEnd = elapsed + phaseLength;
            return reviewing ? RECALL : STUDY;
        }
        return NONE;
    }
    public void pause(long now) {
        if (active && !paused) { elapsedBefore = elapsed(now); paused = true; }
    }
    public void resume(long now) { if (active && paused) { anchor = now; paused = false; } }
    public void stop(long now) { elapsedBefore = elapsed(now); active = false; paused = false; }
    private long interval(int min,int max) { return min*60000L+random.nextInt((max-min)*60000+1); }
}
