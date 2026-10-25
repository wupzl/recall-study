package cn.recall.study;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16,26,27), PANEL = Color.rgb(24,38,38), MINT = Color.rgb(164,229,199), TEXT = Color.rgb(239,245,239), MUTED = Color.rgb(149,170,165);
    private LinearLayout root;
    private EditText minutes;
    private Button primary, stop;
    private TextView status, caption, volumeLabel, permissions;
    private Dial dial;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Chime preview = new Chime();
    private boolean pendingStart, wasActive;
    private int requestedMinutes;

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setIncludeFontPadding(false); return t;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g;
    }
    private Button button(String value, boolean solid) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(solid ? BG : TEXT); b.setBackground(shape(solid ? MINT : PANEL, 16)); b.setMinHeight(dp(50));
        b.setStateListAnimator(null); return b;
    }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void space(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private LinearLayout.LayoutParams weight(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(dp(margin),0,dp(margin),0); return p;
    }
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_ALARM);
        if (SessionService.instance == null && Store.recoverInterrupted(this)) Toast.makeText(this, "上次学习已中断，记录已保存在本机", Toast.LENGTH_LONG).show();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG); scroll.setClipToPadding(false);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(20),dp(24),dp(28));
        scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(i.left, i.top, i.right, i.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = row(); TextView brand = text("回想  /  RECALL", 18, MINT); brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1)); brand.setGravity(Gravity.CENTER_VERTICAL);
        Button history = button("学习记录", false); history.setTextSize(13); header.addView(history, new LinearLayout.LayoutParams(dp(100),dp(44))); history.setOnClickListener(v -> showHistory()); root.addView(header);
        space(root, 28);
        TextView eyebrow = text("给记忆一点留白", 12, MINT); eyebrow.setLetterSpacing(.15f); root.addView(eyebrow); space(root,12);
        TextView title = text("专注一会儿，\n记住多一点。", 31, TEXT); title.setTypeface(null,Typeface.BOLD); title.setLineSpacing(dp(5),1); root.addView(title);
        space(root,10); root.addView(text("每隔随机 3～5 分钟，闭眼回顾 20 秒。",13,MUTED)); space(root,18);
        status = text("准备好，开始一次学习",14,MINT); status.setGravity(Gravity.CENTER); root.addView(status);
        dial = new Dial(); root.addView(dial,new LinearLayout.LayoutParams(-1,dp(242)));
        caption = text("随机提醒，不必盯着时间",13,MUTED); caption.setGravity(Gravity.CENTER); root.addView(caption); space(root,24);
        LinearLayout duration = row(); duration.addView(text("本次学习",14,TEXT),new LinearLayout.LayoutParams(0,dp(40),1));
        minutes = new EditText(this); minutes.setSingleLine(true); minutes.setTextColor(TEXT); minutes.setTextSize(20); minutes.setGravity(Gravity.CENTER);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER); minutes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        minutes.setSelectAllOnFocus(true); minutes.setText(String.valueOf(Store.prefs(this).getInt("minutes",45))); minutes.setBackground(shape(PANEL,10));
        duration.addView(minutes,new LinearLayout.LayoutParams(dp(68),dp(44))); TextView unit=text("  分钟",14,MUTED); duration.addView(unit); root.addView(duration); space(root,12);
        LinearLayout presets=row(); for (int n : new int[]{25,45,60,90}) { Button b=button(n+" 分钟",false); b.setTextSize(13); b.setOnClickListener(v -> { if (current()==null) { minutes.setText(String.valueOf(n)); dial.invalidate(); } }); presets.addView(b,weight(3)); } root.addView(presets); space(root,18);
        primary = button("开始学习  →",true); root.addView(primary,new LinearLayout.LayoutParams(-1,dp(54))); primary.setOnClickListener(v -> primaryClick()); space(root,10);
        stop = button("提前结束",false); stop.setVisibility(View.GONE); root.addView(stop,new LinearLayout.LayoutParams(-1,dp(48)));
        stop.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("结束本次学习？").setMessage("已经学习的时间会保存到本机记录。").setNegativeButton("继续学习",null).setPositiveButton("结束并保存",(d,w) -> command("STOP")).show());
        space(root,24);
        LinearLayout soundPanel=new LinearLayout(this); soundPanel.setOrientation(LinearLayout.VERTICAL); soundPanel.setPadding(dp(16),dp(16),dp(16),dp(16)); soundPanel.setBackground(shape(PANEL,18));
        LinearLayout soundHeader=row(); volumeLabel=text("提示音 · 70%",14,TEXT); soundHeader.addView(volumeLabel,new LinearLayout.LayoutParams(0,-2,1));
        Button test=button("试听",false); soundHeader.addView(test,new LinearLayout.LayoutParams(dp(65),dp(40)));
        test.setOnClickListener(v -> { preview.play(this,"recall"); if (preview.error != null) Toast.makeText(this,preview.error,Toast.LENGTH_LONG).show(); }); soundPanel.addView(soundHeader);
        SeekBar volume=new SeekBar(this); volume.setMax(90); volume.setProgress(Store.prefs(this).getInt("volume",70)-10); volumeLabel.setText("提示音 · "+(volume.getProgress()+10)+"%");
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} public void onProgressChanged(SeekBar s,int progress,boolean user){ volumeLabel.setText("提示音 · "+(progress+10)+"%"); Store.prefs(MainActivity.this).edit().putInt("volume",progress+10).apply(); }});
        soundPanel.addView(volume); space(soundPanel,8); soundPanel.addView(text("闭眼和继续使用不同音色。试听时可用音量键\n调节系统闹钟音量；勿扰模式可能影响响铃。",12,MUTED)); root.addView(soundPanel); space(root,18);
        permissions=text("",12,MUTED); permissions.setLineSpacing(dp(4),1); root.addView(permissions); permissions.setOnClickListener(v -> backgroundSettings());
        space(root,14); TextView footer=text("离线使用 · 记录仅保存在此设备\n回顾包含在总时长内，到点自动结束",11,MUTED); footer.setLineSpacing(dp(5),1); footer.setGravity(Gravity.CENTER); root.addView(footer);
        refresh();
    }
    private SessionService current() { SessionService s=SessionService.instance; return s!=null && s.engine.active ? s : null; }
    private void command(String action) { startService(new Intent(this,SessionService.class).setAction(action)); }
    private void primaryClick() {
        if(current()!=null){ command("TOGGLE"); return; }
        try { requestedMinutes=Integer.parseInt(minutes.getText().toString()); } catch(Exception e){ requestedMinutes=0; }
        if(requestedMinutes<1 || requestedMinutes>90){ minutes.setError("请输入 1～90 分钟"); return; }
        pendingStart=true; continueStart();
    }
    private boolean batteryReady() { return ((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName()); }
    private boolean notificationReady() { return getSystemService(NotificationManager.class).areNotificationsEnabled(); }
    private void continueStart() {
        if(!pendingStart || current()!=null) return;
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7); return; }
        if(!notificationReady()) {
            pendingStart=false; new AlertDialog.Builder(this).setTitle("请开启学习通知").setMessage("通知用于锁屏查看计时，并随时暂停或结束学习。").setNegativeButton("稍后",null).setPositiveButton("打开设置",(d,w)->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()))).show(); return;
        }
        if(!batteryReady()) {
            pendingStart=false; new AlertDialog.Builder(this).setTitle("允许锁屏后继续计时").setMessage("为让每隔 3～5 分钟的提醒在锁屏后正常工作，请允许“回想”不受电池优化限制。设置后再次点击开始。").setNegativeButton("稍后",null).setPositiveButton("设置后台运行",(d,w)->requestBattery()).show(); return;
        }
        pendingStart=false; preview.close(); Store.prefs(this).edit().putInt("minutes",requestedMinutes).apply();
        getSystemService(NotificationManager.class).cancel(2);
        Intent start=new Intent(this,SessionService.class).setAction("START").putExtra("minutes",requestedMinutes);
        try { startForegroundService(start); } catch(Exception e){ Toast.makeText(this,"无法启动后台计时，请检查系统后台运行权限",Toast.LENGTH_LONG).show(); }
        minutes.clearFocus(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(minutes.getWindowToken(),0);
    }
    private void requestBattery() {
        try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()))); }
        catch(Exception e) { try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch(Exception ignored){ Toast.makeText(this,"请在系统设置中允许后台运行",Toast.LENGTH_LONG).show(); } }
    }
    private void backgroundSettings() {
        new AlertDialog.Builder(this).setTitle("后台提醒设置").setMessage("通知权限："+(notificationReady()?"已开启":"未开启")+"\n电池优化豁免："+(batteryReady()?"已开启":"未开启")+"\n\n部分品牌还需在系统设置中允许自启动、后台运行，并锁定后台任务。强行停止、关机后不能继续提醒。").setNegativeButton("关闭",null).setNeutralButton("应用设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setPositiveButton("电池设置",(d,w)->requestBattery()).show();
    }
    public void onRequestPermissionsResult(int code,String[] names,int[] results){ super.onRequestPermissionsResult(code,names,results); if(code==7){ if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) continueStart(); else { pendingStart=false; Toast.makeText(this,"请开启通知权限后再开始",Toast.LENGTH_LONG).show(); } } }
    private final Runnable refreshLoop=new Runnable(){ public void run(){ refresh(); handler.postDelayed(this,250); }};
    protected void onResume(){ super.onResume(); handler.removeCallbacks(refreshLoop); handler.post(refreshLoop); }
    protected void onPause(){ handler.removeCallbacks(refreshLoop); preview.close(); super.onPause(); }
    protected void onDestroy(){ preview.close(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void refresh(){
        if(primary==null)return;
        SessionService s=current(); boolean running=s!=null;
        minutes.setEnabled(!running); primary.setText(running?(s.engine.paused?"继续学习  →":"暂停学习"):"开始学习  →"); stop.setVisibility(running?View.VISIBLE:View.GONE);
        if(running){ SessionEngine e=s.engine; status.setText(e.paused?"已暂停 · 按自己的节奏来":e.reviewing?"闭上眼睛，回想刚才学了什么":"专注学习 · 下一次回顾即将到来"); caption.setText("本次剩余 "+SessionService.time(e.remaining(SessionService.now()))+"  ·  已回顾 "+e.reviews+" 次"); if(s.chime.error!=null)caption.setText(s.chime.error); }
        else { status.setText(wasActive?"本次学习已保存，辛苦了":"准备好，开始一次学习"); caption.setText("随机提醒，不必盯着时间"); }
        permissions.setText("后台提醒："+(notificationReady()&&batteryReady()?"权限已就绪":"需要设置")+"  ·  点击检查设置  ›");
        wasActive=running || wasActive; dial.invalidate();
    }
    private void showHistory(){
        JSONArray history=Store.history(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(22),dp(12),dp(22),dp(12));
        if(history.length()==0)list.addView(text("还没有学习记录。\n从一次专注开始吧。",15,TEXT));
        SimpleDateFormat format=new SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA);
        for(int i=0;i<history.length();i++)try{ JSONObject r=history.getJSONObject(i); TextView item=text(format.format(new Date(r.getLong("started")))+"   "+r.getString("result")+"\n已学习 "+SessionService.time(r.getLong("elapsed"))+" / "+r.getLong("target")/60000+" 分钟 · 回顾 "+r.getInt("reviews")+" 次",14,TEXT); item.setLineSpacing(dp(8),1); item.setPadding(0,dp(12),0,dp(12)); list.addView(item); }catch(Exception ignored){}
        ScrollView scroll=new ScrollView(this); scroll.addView(list); new AlertDialog.Builder(this).setTitle("学习记录 · 仅本机").setView(scroll).setPositiveButton("完成",null).show();
    }
    private final class Dial extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Dial(){super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE,null); setContentDescription("学习倒计时");}
        protected void onDraw(Canvas canvas){
            super.onDraw(canvas); float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(101); SessionService s=current();
            float progress=1; String value="准备开始",label="一次专注，一点积累"; int color=MINT;
            if(s!=null){ long now=SessionService.now(); SessionEngine e=s.engine; long remaining=e.phaseRemaining(now); progress=(float)remaining/(float)Math.min(e.phaseLength,e.targetMs-(e.phaseEnd-e.phaseLength)); value=SessionService.time(remaining); label=e.paused?"已暂停":e.reviewing?"闭眼回顾":"距离下次回顾"; if(e.reviewing)color=Color.rgb(237,202,143); }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setColor(Color.rgb(41,61,57)); canvas.drawCircle(cx,cy,r,p);
            p.setColor(color);p.setStrokeCap(Paint.Cap.ROUND);canvas.drawArc(new RectF(cx-r,cy-r,cx+r,cy+r),-90,Math.min(1,Math.max(0,progress))*359.9f,false,p);
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(TEXT);p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextSize(dp(s==null?28:49));canvas.drawText(value,cx,cy+dp(9),p);
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setColor(MUTED);p.setTextSize(dp(12));canvas.drawText(label,cx,cy+dp(39),p);
        }
    }
}
