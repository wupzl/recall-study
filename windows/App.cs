using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Media;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Threading;
using Forms=System.Windows.Forms;

namespace Recall {
    [DataContract] public sealed class Entry {
        [DataMember] public string Started;
        [DataMember] public long Elapsed;
        [DataMember] public long Target;
        [DataMember] public int Reviews;
        [DataMember] public string Result;
    }
    [DataContract] public sealed class Store {
        [DataMember] public int Minutes=45;
        [DataMember] public int Volume=70;
        [DataMember] public List<Entry> Entries=new List<Entry>();
        [DataMember] public Entry Pending;
        public static readonly string DirectoryPath=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"RecallStudy");
        static readonly string FilePath=Path.Combine(DirectoryPath,"data.json");
        public static Store Load() {
            Store data;
            if(!File.Exists(FilePath)) return new Store();
            try { data=Read(FilePath); }
            catch { if(!File.Exists(FilePath+".bak")) throw; data=Read(FilePath+".bak"); }
            if(data.Entries==null) data.Entries=new List<Entry>();
            data.Minutes=Math.Max(1,Math.Min(90,data.Minutes)); data.Volume=Math.Max(10,Math.Min(100,data.Volume));
            if(data.Pending!=null) { data.Pending.Result="中断"; data.Entries.Insert(0,data.Pending); data.Pending=null; data.Save(); }
            return data;
        }
        static Store Read(string path) { using(FileStream file=File.OpenRead(path)) return (Store)new DataContractJsonSerializer(typeof(Store)).ReadObject(file); }
        public void Save() {
            Directory.CreateDirectory(DirectoryPath);
            string temporary=FilePath+".tmp";
            using(FileStream file=new FileStream(temporary,FileMode.Create,FileAccess.Write,FileShare.None)) { new DataContractJsonSerializer(typeof(Store)).WriteObject(file,this); file.Flush(true); }
            if(File.Exists(FilePath)) File.Replace(temporary,FilePath,FilePath+".bak",true); else File.Move(temporary,FilePath);
        }
    }
    sealed class Chime : IDisposable {
        SoundPlayer player;
        MemoryStream stream;
        public void Play(string name,int volume) {
            Dispose(); byte[] wav;
            using(Stream asset=Assembly.GetExecutingAssembly().GetManifestResourceStream("Recall."+name+".wav")) {
                wav=new byte[asset.Length]; int offset=0,read;
                while((read=asset.Read(wav,offset,wav.Length-offset))>0) offset+=read;
            }
            for(int i=44;i+1<wav.Length;i+=2) { short sample=(short)(wav[i]|wav[i+1]<<8); short scaled=(short)(sample*volume/100); wav[i]=(byte)(scaled&255); wav[i+1]=(byte)((scaled>>8)&255); }
            stream=new MemoryStream(wav); player=new SoundPlayer(stream); player.Load(); player.Play();
        }
        public void Dispose() { if(player!=null){ player.Stop(); player.Dispose(); player=null; } if(stream!=null){stream.Dispose();stream=null;} }
    }
    public sealed class App {
        [DllImport("kernel32.dll")] static extern uint SetThreadExecutionState(uint flags);
        [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr hwnd,int attribute,ref int value,int size);
        readonly SessionEngine engine=new SessionEngine(new Random());
        readonly Chime chime=new Chime();
        Store store;
        Window window;
        DispatcherTimer timer;
        Forms.NotifyIcon tray;
        Forms.ToolStripMenuItem trayToggle;
        string started;
        long lastSave;
        bool quitting, persistenceError, completeMessage;
        TextBox minutes;
        Button primary,stop;
        Slider volume;
        static long Now { get { return (long)(Stopwatch.GetTimestamp()*1000.0/Stopwatch.Frequency); } }
        static string Time(long ms) { long sec=(ms+999)/1000; return String.Format(CultureInfo.InvariantCulture,"{0:00}:{1:00}",sec/60,sec%60); }
        T Find<T>(string name) where T:class { return window.FindName(name) as T; }
        void Label(string name,string text) { Find<TextBlock>(name).Text=text; }
        [STAThread] public static void Main(string[] args) {
            bool created;
            using(Mutex mutex=new Mutex(true,"Local\\RecallStudy.SingleInstance",out created)) {
                if(!created){ MessageBox.Show("回想已经在运行，请从系统托盘打开。","回想"); return; }
                try { new App().Run(args); } catch(Exception e){ MessageBox.Show("无法打开回想："+e.Message,"回想",MessageBoxButton.OK,MessageBoxImage.Error); }
            }
        }
        void Run(string[] args) {
            store=Store.Load();
            using(Stream xaml=Assembly.GetExecutingAssembly().GetManifestResourceStream("Recall.MainWindow.xaml")) window=(Window)XamlReader.Load(xaml);
            window.Height=Math.Min(830,SystemParameters.WorkArea.Height-35);
            minutes=Find<TextBox>("MinutesInput"); primary=Find<Button>("PrimaryButton"); stop=Find<Button>("StopButton"); volume=Find<Slider>("VolumeSlider");
            minutes.Text=store.Minutes.ToString(); volume.Value=store.Volume; Label("VolumeLabel","提示音 "+store.Volume+"%");
            primary.Click+=(s,e)=>Primary(); stop.Click+=(s,e)=>Stop();
            foreach(int preset in new int[]{25,45,60,90}) { int selected=preset; Find<Button>("Preset"+preset).Click+=(s,e)=>{ minutes.Text=selected.ToString(); }; }
            minutes.TextChanged+=(s,e)=>Refresh();
            minutes.GotKeyboardFocus+=(s,e)=>minutes.SelectAll();
            volume.ValueChanged+=(s,e)=>{ store.Volume=(int)volume.Value; Label("VolumeLabel","提示音 "+store.Volume+"%"); };
            volume.AddHandler(System.Windows.Controls.Primitives.Thumb.DragCompletedEvent,new System.Windows.Controls.Primitives.DragCompletedEventHandler((s,e)=>Save()));
            Find<Button>("PreviewButton").Click+=(s,e)=>{ Sound("recall"); Save(); };
            Find<Button>("FocusTab").Click+=(s,e)=>ShowHistory(false);
            Find<Button>("HistoryTab").Click+=(s,e)=>ShowHistory(true);
            SetupTray();
            window.StateChanged+=(s,e)=>{ if(window.WindowState==WindowState.Minimized){ window.Hide(); window.ShowInTaskbar=false; } };
            window.Closing+=(s,e)=>{ if(!quitting && engine.Active){ e.Cancel=true; window.Hide(); window.ShowInTaskbar=false; tray.ShowBalloonTip(2500,"学习仍在继续","点击托盘中的回想图标可打开窗口、暂停或结束。",Forms.ToolTipIcon.Info); } };
            window.Closed+=(s,e)=>{ timer.Stop(); chime.Dispose(); tray.Dispose(); KeepAwake(false); Save(); };
            window.SourceInitialized+=(s,e)=>{ try{ int dark=1; DwmSetWindowAttribute(new WindowInteropHelper(window).Handle,20,ref dark,4); }catch{} };
            Microsoft.Win32.SystemEvents.PowerModeChanged+=PowerModeChanged;
            timer=new DispatcherTimer { Interval=TimeSpan.FromMilliseconds(100) }; timer.Tick+=(s,e)=>Tick(); timer.Start(); Refresh();
            if(args.Length==2 && args[0]=="--screenshot") {
                string path=Path.GetFullPath(args[1]);
                window.ContentRendered+=(s,e)=>window.Dispatcher.BeginInvoke(new Action(()=>{ Capture(path); quitting=true; window.Close(); }),DispatcherPriority.ApplicationIdle);
            }
            new Application().Run(window);
            Microsoft.Win32.SystemEvents.PowerModeChanged-=PowerModeChanged;
        }
        void Primary() {
            bool wasActive=engine.Active;
            Tick();
            if(wasActive && !engine.Active)return;
            if(engine.Active) {
                if(engine.Paused) engine.Resume(Now); else { engine.Pause(Now); chime.Dispose(); }
                KeepAwake(!engine.Paused); Checkpoint(); Refresh(); return;
            }
            int selected;
            if(!Int32.TryParse(minutes.Text,out selected)||selected<1||selected>90){ Label("ValidationLabel","请输入 1～90 分钟。");minutes.Focus();return; }
            Label("ValidationLabel",""); store.Minutes=selected; started=DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
            completeMessage=false; engine.Start(selected,Now); KeepAwake(true); Checkpoint(); Refresh();
        }
        void Tick() {
            if(engine.Active) {
                int result=engine.Advance(Now);
                if(result==SessionEngine.Complete) Finish(true);
                else if(result!=SessionEngine.None){ Sound(result==SessionEngine.Review?"recall":"continue"); Checkpoint(); }
                if(engine.Active && Now-lastSave>=5000) Checkpoint();
            }
            Refresh();
        }
        void Stop() {
            if(!engine.Active)return;
            if(MessageBox.Show(window,"结束本次学习？已经学习的时间会保存到本机记录。","回想",MessageBoxButton.YesNo,MessageBoxImage.Question)==MessageBoxResult.Yes) { Tick(); if(engine.Active)Finish(false); Refresh(); }
        }
        void Finish(bool completed) {
            engine.Stop(Now); KeepAwake(false); chime.Dispose();
            Entry entry=Snapshot(); entry.Result=completed?"已完成":"提前结束";
            store.Entries.Insert(0,entry); if(store.Entries.Count>500)store.Entries.RemoveRange(500,store.Entries.Count-500);
            store.Pending=null; Save(); completeMessage=true;
            if(Find<StackPanel>("HistoryPanel").Visibility==Visibility.Visible)ShowHistory(true);
            if(completed){ Sound("complete"); tray.ShowBalloonTip(4000,"本次学习已完成","做得好，让知识沉淀一下。",Forms.ToolTipIcon.Info); }
        }
        Entry Snapshot() { return new Entry { Started=started,Elapsed=engine.Elapsed(Now),Target=engine.TargetMs,Reviews=engine.Reviews }; }
        void Checkpoint(){ store.Pending=Snapshot(); lastSave=Now; Save(); }
        void Save(){
            try{ store.Save(); if(persistenceError){persistenceError=false;Label("ValidationLabel","");} }
            catch(Exception){ persistenceError=true; Label("ValidationLabel","无法保存记录，请检查本机存储空间或目录权限。学习计时仍在继续。"); }
        }
        void Sound(string name){ try{chime.Play(name,store.Volume);}catch{Label("ValidationLabel","提示音播放失败，请检查系统声音设备和音量。");} }
        void KeepAwake(bool active){ SetThreadExecutionState(active?0x80000001u:0x80000000u); }
        void Refresh() {
            if(minutes==null)return;
            bool active=engine.Active; minutes.IsEnabled=!active;
            foreach(int n in new int[]{25,45,60,90}) Find<Button>("Preset"+n).IsEnabled=!active;
            primary.Content=active?(engine.Paused?"继续学习  →":"暂停学习"):"开始学习  →";
            stop.Visibility=active?Visibility.Visible:Visibility.Collapsed;
            long now=Now;
            double fraction=1;
            if(active){
                Label("PhaseLabel",engine.Paused?"已暂停":engine.Reviewing?"闭眼回顾":"专注学习");
                Label("ClockLabel",Time(engine.PhaseRemaining(now)));Label("ClockCaption",engine.Reviewing?"回顾剩余时间":"距离下次回顾");
                Label("StateTitle",engine.Paused?"按自己的节奏来":engine.Reviewing?"闭上眼睛，回想一下":"保持专注，提示音会提醒你");
                Label("StateHint",engine.Paused?"计时已冻结。\n准备好后，继续刚才的节奏。":engine.Reviewing?"刚才学到了什么？\n试着在脑海里复述关键内容。":"不必看倒计时。\n继续学习，直到听见下一声提醒。");
                Label("TotalLabel","本次剩余 "+Time(engine.Remaining(now))); Label("ReviewLabel","已回顾 "+engine.Reviews+" 次 · 总计划 "+engine.TargetMs/60000+" 分钟");
                fraction=(double)engine.PhaseRemaining(now)/Math.Min(engine.PhaseLength,engine.TargetMs-(engine.PhaseEnd-engine.PhaseLength));
            } else {
                int planned; if(!Int32.TryParse(minutes.Text,out planned)||planned<1||planned>90)planned=store.Minutes;
                Label("PhaseLabel",completeMessage?"本次学习已保存":"准备开始");Label("ClockLabel",Time(planned*60000L)); Label("ClockCaption","本次计划");
                Label("StateTitle",completeMessage?"又积累了一点，辛苦了":"按自己的节奏来");Label("StateHint","开始后，不必盯着屏幕。\n听到提示音时，闭眼回想刚才学了什么。");
                Label("TotalLabel","每隔 3～5 分钟 · 随机提醒"); Label("ReviewLabel","闭眼 20 秒后，自动提示继续");
            }
            DrawArc(fraction,active && engine.Reviewing);
            if(tray!=null){tray.Text=active?"回想 · "+(engine.Paused?"已暂停":engine.Reviewing?"闭眼回顾":"学习中")+" · "+Time(engine.Remaining(now)):"回想 · 开始一次学习"; trayToggle.Enabled=active; trayToggle.Text=engine.Paused?"继续学习":"暂停学习";}
        }
        void DrawArc(double fraction,bool reviewing){
            double angle=Math.Max(.001,Math.Min(.9999,fraction))*Math.PI*2;
            Point from=new Point(129,16),to=new Point(129+113*Math.Sin(angle),129-113*Math.Cos(angle));
            PathFigure fig=new PathFigure {StartPoint=from}; fig.Segments.Add(new ArcSegment(to,new Size(113,113),0,angle>Math.PI,SweepDirection.Clockwise,true));
            PathGeometry geometry=new PathGeometry(); geometry.Figures.Add(fig); var arc=Find<System.Windows.Shapes.Path>("ProgressArc");arc.Data=geometry;arc.Stroke=new SolidColorBrush((Color)ColorConverter.ConvertFromString(reviewing?"#EDCA8F":"#A4E5C7"));
        }
        void ShowHistory(bool show){
            Find<StackPanel>("FocusPanel").Visibility=show?Visibility.Collapsed:Visibility.Visible;
            Find<StackPanel>("HistoryPanel").Visibility=show?Visibility.Visible:Visibility.Collapsed;
            if(!show)return;
            Label("HistorySummary",store.Entries.Count==0?"还没有学习记录，从一次专注开始吧。":"共 "+store.Entries.Count+" 次学习 · 累计 "+(store.Entries.Sum(e=>e.Elapsed)/60000)+" 分钟 · 已完成 "+store.Entries.Count(e=>e.Result=="已完成")+" 次");
            StackPanel list=Find<StackPanel>("HistoryItems"); list.Children.Clear();
            foreach(Entry e in store.Entries){
                StackPanel content=new StackPanel();content.Children.Add(new TextBlock {Text=e.Started+"    "+e.Result,FontSize=15,Foreground=new SolidColorBrush((Color)ColorConverter.ConvertFromString(e.Result=="已完成"?"#A4E5C7":"#EDCA8F"))});
                content.Children.Add(new TextBlock {Text="已学习 "+Time(e.Elapsed)+" / "+e.Target/60000+" 分钟    ·    回顾 "+e.Reviews+" 次",FontSize=12,Margin=new Thickness(0,10,0,0),Foreground=new SolidColorBrush((Color)ColorConverter.ConvertFromString("#95AAA5"))});
                list.Children.Add(new Border {Background=new SolidColorBrush((Color)ColorConverter.ConvertFromString("#182626")),CornerRadius=new CornerRadius(14),Padding=new Thickness(20),Margin=new Thickness(0,0,0,12),Child=content});
            }
        }
        void SetupTray(){
            tray=new Forms.NotifyIcon {Icon=LoadIcon(),Visible=true,Text="回想 · 开始一次学习"};
            Forms.ContextMenuStrip menu=new Forms.ContextMenuStrip();
            menu.Items.Add("打开回想",null,(s,e)=>ShowWindow());
            trayToggle=new Forms.ToolStripMenuItem("暂停学习",null,(s,e)=>Primary()); menu.Items.Add(trayToggle);
            menu.Items.Add("结束本次学习",null,(s,e)=>{ShowWindow();Stop();});menu.Items.Add(new Forms.ToolStripSeparator());
            menu.Items.Add("退出",null,(s,e)=>{ShowWindow();if(engine.Active){Stop();if(engine.Active)return;}quitting=true;window.Close();});
            tray.ContextMenuStrip=menu; tray.DoubleClick+=(s,e)=>ShowWindow(); tray.BalloonTipClicked+=(s,e)=>ShowWindow();
        }
        System.Drawing.Icon LoadIcon(){ using(Stream resource=Assembly.GetExecutingAssembly().GetManifestResourceStream("Recall.app.ico")) return new System.Drawing.Icon(resource); }
        void ShowWindow(){window.ShowInTaskbar=true;window.Show();window.WindowState=WindowState.Normal;window.Activate();}
        void PowerModeChanged(object sender,Microsoft.Win32.PowerModeChangedEventArgs e){
            if(e.Mode==Microsoft.Win32.PowerModes.Suspend)window.Dispatcher.Invoke(new Action(()=>{if(engine.Active&&!engine.Paused){Tick();if(engine.Active){engine.Pause(Now);KeepAwake(false);Checkpoint();Label("ValidationLabel","电脑进入睡眠，学习已暂停。唤醒后请点击继续。");Refresh();}}}));
        }
        void Capture(string path){
            var visual=window.Content as System.Windows.Media.Visual;
            var element=window.Content as FrameworkElement;
            var bitmap=new System.Windows.Media.Imaging.RenderTargetBitmap((int)element.ActualWidth,(int)element.ActualHeight,96,96,PixelFormats.Pbgra32);
            bitmap.Render(visual);var encoder=new System.Windows.Media.Imaging.PngBitmapEncoder();encoder.Frames.Add(System.Windows.Media.Imaging.BitmapFrame.Create(bitmap));
            using(FileStream output=File.Create(path))encoder.Save(output);
        }
    }
}
