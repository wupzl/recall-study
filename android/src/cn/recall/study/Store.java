package cn.recall.study;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

final class Store {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("recall", Context.MODE_PRIVATE); }
    static JSONArray history(Context c) {
        try { return new JSONArray(prefs(c).getString("history", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    static void record(Context c, long started, long elapsed, long target, int reviews, String result) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("started", started); entry.put("elapsed", elapsed); entry.put("target", target);
            entry.put("reviews", reviews); entry.put("result", result);
            JSONArray old = history(c), list = new JSONArray(); list.put(entry);
            for (int i = 0; i < Math.min(old.length(), 499); i++) list.put(old.get(i));
            prefs(c).edit().putString("history", list.toString()).remove("pending").commit();
        } catch (Exception e) { throw new IllegalStateException("无法保存学习记录", e); }
    }
    static void checkpoint(Context c, SessionEngine e, long started, long now) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("started", started); entry.put("elapsed", e.elapsed(now));
            entry.put("target", e.targetMs); entry.put("reviews", e.reviews);
            prefs(c).edit().putString("pending", entry.toString()).apply();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    static boolean recoverInterrupted(Context c) {
        String pending = prefs(c).getString("pending", null);
        if (pending == null) return false;
        try {
            JSONObject entry = new JSONObject(pending);
            record(c, entry.getLong("started"), entry.getLong("elapsed"), entry.getLong("target"), entry.getInt("reviews"), "中断");
            return true;
        } catch (Exception e) { prefs(c).edit().remove("pending").commit(); return false; }
    }
}
