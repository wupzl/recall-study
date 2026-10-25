using System;
using System.Collections;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Threading;

class WindowsIntegration {
    static string qa;
    static object app, engine;
    static Type appType;
    static Window window;
    static DispatcherTimer timer;
    static Stopwatch watch=Stopwatch.StartNew();
    static int stage,assertions;
    static long paused;
    static long reviewStarted;
    static bool failed;
    static bool captureOnly,homePreview;
    static string pauseText;
    static BindingFlags flags=BindingFlags.Instance|BindingFlags.NonPublic;
    static void Check(bool value,string message){assertions++;if(!value)throw new Exception(message);Console.WriteLine("PASS: "+message);}
    static object Field(object obj,string name){return obj.GetType().GetField(name,BindingFlags.Instance|BindingFlags.Public|BindingFlags.NonPublic).GetValue(obj);}
    static object Call(string name,params object[] args){return appType.GetMethod(name,flags).Invoke(app,args);}
    static T Find<T>(string name)where T:class{return window.FindName(name) as T;}
    static void Click(string name){Find<Button>(name).RaiseEvent(new RoutedEventArgs(Button.ClickEvent));}
    static long Elapsed(){long now=(long)(Stopwatch.GetTimestamp()*1000.0/Stopwatch.Frequency);return (long)engine.GetType().GetMethod("Elapsed").Invoke(engine,new object[]{now});}
    static bool Active{get{return(bool)Field(engine,"Active");}}
    static void Exit(){timer.Stop();appType.GetField("quitting",flags).SetValue(app,true);window.Close();}
    [STAThread]static int Main(string[] args){
        captureOnly=args.Length>2;homePreview=args.Length>2&&args[2]=="--home-preview";
        qa=Path.GetFullPath(args[1]);Directory.CreateDirectory(qa);
        Assembly assembly=Assembly.LoadFrom(Path.GetFullPath(args[0]));
        Type store=assembly.GetType("Recall.Store");string testData=Path.Combine(qa,"windows-data-"+Process.GetCurrentProcess().Id);Directory.CreateDirectory(testData);
        File.WriteAllText(Path.Combine(testData,"data.json"),"{\"Minutes\":45,\"Volume\":70,\"Entries\":[],\"Pending\":null}");
        store.GetField("DirectoryPath",BindingFlags.Static|BindingFlags.Public).SetValue(null,testData);
        store.GetField("FilePath",BindingFlags.Static|BindingFlags.NonPublic).SetValue(null,Path.Combine(testData,"data.json"));
        appType=assembly.GetType("Recall.App");app=Activator.CreateInstance(appType);
        timer=new DispatcherTimer{Interval=TimeSpan.FromMilliseconds(200)};timer.Tick+=(s,e)=>Step();timer.Start();
        Call("Run",new object[]{new string[0]});
        if(captureOnly)return failed?1:0;
        File.WriteAllText(Path.Combine(qa,"windows-result.json"),"{\"result\":\""+(failed?"failed":"passed")+"\",\"assertions\":"+assertions+",\"real_session_minutes\":3}");
        return failed?1:0;
    }
    static void Step(){try{
        if(window==null){window=(Window)appType.GetField("window",flags).GetValue(app);if(window==null)return;engine=appType.GetField("engine",flags).GetValue(app);}
        if(captureOnly){if(!homePreview)Click("HistoryTab");window.UpdateLayout();Call("Capture",Path.Combine(qa,homePreview?"windows-home-v110.png":"windows-history.png"));Exit();return;}
        if(stage==0){
            Check(Find<TextBox>("StudyMinInput").Text=="20"&&Find<TextBox>("StudyMaxInput").Text=="30","upgrade initializes study defaults");
            Check(Find<TextBox>("RestMinInput").Text=="5"&&Find<TextBox>("RestMaxInput").Text=="8","upgrade initializes rest defaults");
            Find<TextBox>("MinutesInput").Text="91";Click("PrimaryButton");Check(!Active,"reject duration above 90 minutes");Check(Find<TextBlock>("ValidationLabel").Text.Contains("90"),"show duration validation");
            Click("Preset45");Check(Find<TextBox>("MinutesInput").Text=="45","duration preset");
            Find<TextBox>("StudyMinInput").Text="31";Click("PrimaryButton");Check(!Active,"reject reversed study range");
            Find<TextBox>("StudyMinInput").Text="20";Find<TextBox>("RestMinInput").Text="0";Click("PrimaryButton");Check(!Active,"reject zero rest range");
            Find<TextBox>("RestMinInput").Text="5";Find<TextBox>("RestMaxInput").Text="91";Click("PrimaryButton");Check(!Active,"reject rest above 90");
            foreach(string name in new string[]{"StudyMinInput","StudyMaxInput","RestMinInput","RestMaxInput"})Find<TextBox>(name).Text="1";
            Find<TextBox>("MinutesInput").Text="3";Click("PrimaryButton");Check(Active,"start three-minute custom cycle");
            foreach(string name in new string[]{"StudyMinInput","StudyMaxInput","RestMinInput","RestMaxInput"})Check(!Find<TextBox>(name).IsEnabled,"lock range input "+name);Check(!Find<TextBox>("MinutesInput").IsEnabled,"lock duration during session");
            stage=1;watch.Restart();
        }else if(stage==1&&watch.ElapsedMilliseconds>2000){
            Click("PrimaryButton");Check((bool)Field(engine,"Paused"),"pause session");paused=Elapsed();pauseText=Find<TextBlock>("ClockLabel").Text;stage=2;watch.Restart();
        }else if(stage==2&&watch.ElapsedMilliseconds>2500){
            Check(Elapsed()==paused,"pause freezes elapsed clock");Check(Find<TextBlock>("ClockLabel").Text==pauseText,"pause freezes displayed countdown");Click("PrimaryButton");Check(!(bool)Field(engine,"Paused"),"resume session");window.WindowState=WindowState.Minimized;stage=3;watch.Restart();
        }else if(stage==3&&watch.ElapsedMilliseconds>2500){
            Check(!window.IsVisible&&Active,"minimize to tray keeps session running");Check(Elapsed()>paused+1500,"background clock continues");Call("ShowWindow");Call("Capture",Path.Combine(qa,"windows-learning.png"));
            long phaseEnd=(long)Field(engine,"PhaseEnd");Check(phaseEnd==60000,"actual UI honors custom fixed one-minute study");
            stage=4;watch.Restart();
        }else if(stage==4&&(bool)Field(engine,"Reviewing")){
            reviewStarted=(long)Field(engine,"PhaseEnd")-(long)Field(engine,"PhaseLength");
            Check(Find<TextBlock>("PhaseLabel").Text=="安静休息","actual UI enters rest phase");
            Call("Capture",Path.Combine(qa,"windows-reviewing.png"));Click("PrimaryButton");paused=Elapsed();pauseText=Find<TextBlock>("ClockLabel").Text;stage=5;watch.Restart();
        }else if(stage==5&&watch.ElapsedMilliseconds>2500){
            Check(Elapsed()==paused,"pause freezes recall time");Check(Find<TextBlock>("ClockLabel").Text==pauseText,"pause freezes recall countdown");Click("PrimaryButton");stage=6;watch.Restart();
        }else if(stage==6&&!(bool)Field(engine,"Reviewing")){
            Check(Elapsed()-reviewStarted>=60000&&Elapsed()-reviewStarted<60700,"rest lasts 60 active seconds");
            Check(Find<TextBlock>("PhaseLabel").Text=="专注学习","actual UI automatically continues");
            long next=(long)Field(engine,"PhaseLength");Check(next==60000,"next study uses saved custom interval");stage=7;watch.Restart();
        }else if(stage==7&&!Active){
            Check(Elapsed()==180000,"finish at total three minutes including rest");
            object data=appType.GetField("store",flags).GetValue(app);IList entries=(IList)Field(data,"Entries");object entry=entries[0];
            Check((string)Field(entry,"Result")=="已完成","persist completed history");Check((long)Field(entry,"Elapsed")==180000,"persist exact completed duration");Check((int)Field(entry,"Reviews")==1,"persist actual recall count");
            Click("HistoryTab");Check(Find<StackPanel>("HistoryItems").Children.Count>0,"history visible in actual UI");window.UpdateLayout();Call("Capture",Path.Combine(qa,"windows-history.png"));
            Click("FocusTab");
            object loaded=app.GetType().Assembly.GetType("Recall.Store").GetMethod("Load").Invoke(null,null);
            Check((int)Field(loaded,"StudyMin")==1&&(int)Field(loaded,"RestMax")==1,"custom rhythm persisted to disk");
            Find<TextBox>("MinutesInput").Text="45";Exit();Console.WriteLine("Windows integration passed: "+assertions+" assertions");
        }else if(watch.ElapsedMilliseconds>80000){throw new Exception("session timed out");}
    }catch(Exception error){failed=true;Console.WriteLine("FAIL: "+error);File.WriteAllText(Path.Combine(qa,"windows-failure.txt"),error.ToString());Exit();}}
}
