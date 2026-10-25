package cn.recall.study;

import java.util.Random;

/** Pure monotonic-clock state machine. Deadlines are measured in active session milliseconds. */
public final class SessionEngine {
    public static final int NONE = 0, RECALL = 1, STUDY = 2, COMPLETE = 3;
    public static final long REVIEW_MS = 20000;
    private final Random random;
    public long targetMs, elapsedBefore, anchor, phaseEnd, phaseLength;
    public boolean active, paused, reviewing;
    public int reviews;

    public SessionEngine(Random random) { this.random = random; }
    public void start(int minutes, long now) {
        if (minutes < 1 || minutes > 90) throw new IllegalArgumentException("时长应为 1～90 分钟");
        targetMs = minutes * 60000L;
        elapsedBefore = 0; anchor = now; reviews = 0;
        active = true; paused = false; reviewing = false;
        phaseLength = interval(); phaseEnd = phaseLength;
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
            if (reviewing) { reviews++; phaseLength = REVIEW_MS; }
            else phaseLength = interval();
            // A late callback starts a real 20-second review, never a burst of missed reminders.
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
    private long interval() { return 180000L + random.nextInt(120001); }
}
