package cn.recall.study;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.util.Log;
import java.util.Random;

public final class SessionService extends Service {
    static SessionService instance;
    final SessionEngine engine = new SessionEngine(new Random());
    final Chime chime = new Chime();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private long started, lastSave;
    private boolean recorded;
    private static final String CHANNEL = "study_timer";
    static long now() { return SystemClock.elapsedRealtime(); }
    static String time(long ms) {
        long sec = (ms + 999) / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", sec / 60, sec % 60);
    }
    public void onCreate() {
        super.onCreate(); instance = this;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "学习计时", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); channel.setDescription("显示当前学习状态，并提供暂停和结束操作");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "recall:study");
    }
    public int onStartCommand(Intent intent, int flags, int id) {
        String action = intent == null ? "STOP" : intent.getAction();
        if ("START".equals(action) && !engine.active) {
            handler.removeCallbacksAndMessages(null); chime.close();
            int minutes = Math.max(1, Math.min(90, intent.getIntExtra("minutes", 45)));
            Store.recoverInterrupted(this);
            engine.start(minutes, now()); started = System.currentTimeMillis(); recorded = false;
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(1, notification());
            acquire(); checkpoint(); handler.removeCallbacks(tick); handler.post(tick);
            Log.i("RecallTimer", "START target=" + engine.targetMs);
        } else if ("TOGGLE".equals(action) && engine.active) {
            int event = engine.advance(now());
            if (event == SessionEngine.COMPLETE) { finish(true); return START_NOT_STICKY; }
            if (engine.paused) { engine.resume(now()); acquire(); }
            else { engine.pause(now()); chime.close(); release(); }
            checkpoint(); updateNotification();
            Log.i("RecallTimer", engine.paused ? "PAUSE" : "RESUME");
        } else if ("STOP".equals(action)) finish(false);
        return START_NOT_STICKY;
    }
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!engine.active) return;
            int event = engine.advance(now());
            if (event == SessionEngine.COMPLETE) { finish(true); return; }
            if (event != SessionEngine.NONE) {
                Log.i("RecallTimer", (event == SessionEngine.RECALL ? "RECALL" : "STUDY") + " elapsed=" + engine.elapsed(now()));
                chime.play(SessionService.this, event == SessionEngine.RECALL ? "recall" : "continue");
                checkpoint(); updateNotification();
            }
            if (now() - lastSave >= 5000) checkpoint();
            handler.postDelayed(this, 150);
        }
    };
    private void checkpoint() { Store.checkpoint(this, engine, started, now()); lastSave = now(); }
    private void acquire() { if (!wakeLock.isHeld()) wakeLock.acquire(91 * 60 * 1000L); }
    private void release() { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); }
    private void updateNotification() { getSystemService(NotificationManager.class).notify(1, notification()); }
    private PendingIntent command(String action, int id) {
        return PendingIntent.getService(this, id, new Intent(this, SessionService.class).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = engine.paused ? "学习已暂停" : engine.reviewing ? "闭眼回顾 · 20 秒" : "专注学习中";
        String text = "本次剩余 " + time(engine.remaining(now())) + " · 已回顾 " + engine.reviews + " 次";
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(cn.recall.study.R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(new Notification.Action.Builder(null, engine.paused ? "继续" : "暂停", command("TOGGLE", 1)).build())
            .addAction(new Notification.Action.Builder(null, "结束", command("STOP", 2)).build());
        if (!engine.paused) builder.setWhen(System.currentTimeMillis() + engine.phaseRemaining(now())).setUsesChronometer(true).setChronometerCountDown(true);
        return builder.build();
    }
    private void finish(boolean completed) {
        Log.i("RecallTimer", completed ? "COMPLETE" : "STOP");
        handler.removeCallbacks(tick);
        if (!recorded && engine.targetMs > 0) {
            engine.stop(now());
            Store.record(this, started, engine.elapsed(now()), engine.targetMs, engine.reviews, completed ? "已完成" : "提前结束");
            recorded = true;
        }
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (completed) {
            chime.play(this, "complete");
            Notification done = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("本次学习已完成").setContentText("做得好，让知识沉淀一下。")
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(true).build();
            getSystemService(NotificationManager.class).notify(2, done);
            handler.postDelayed(() -> stopSelf(), 2200);
        } else { chime.close(); stopSelf(); }
    }
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (engine.active && !recorded) {
            engine.stop(now()); Store.record(this, started, engine.elapsed(now()), engine.targetMs, engine.reviews, "中断");
        }
        release(); chime.close(); if (instance == this) instance = null; super.onDestroy();
    }
    public IBinder onBind(Intent intent) { return null; }
}
