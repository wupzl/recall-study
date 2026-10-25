package cn.recall.study;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

final class Chime {
    private MediaPlayer player;
    String error;
    void play(Context context, String name) {
        close(); error = null;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            AssetFileDescriptor afd = context.getAssets().openFd(name + ".wav");
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength()); afd.close();
            float volume = Store.prefs(context).getInt("volume", 70) / 100f;
            player.setVolume(volume, volume);
            player.setOnCompletionListener(p -> close());
            player.setOnErrorListener((p, what, extra) -> { error = "提示音播放失败，请检查闹钟音量"; close(); return true; });
            player.prepare(); player.start();
        } catch (Exception e) { error = "提示音播放失败，请检查闹钟音量"; close(); }
    }
    void close() { if (player != null) { player.release(); player = null; } }
}
