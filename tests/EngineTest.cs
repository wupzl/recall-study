using System;
using System.Collections.Generic;
using System.IO;
using Recall;
class EngineTest {
    static int assertions;
    static void Check(bool value,string message){assertions++;if(!value)throw new Exception(message);}
    class Lower:Random{public override int Next(int bound){return 0;}}
    class Upper:Random{public override int Next(int bound){return bound-1;}}
    static void Main(string[] args){
        SessionEngine engine=null;string scenario="";
        foreach(string line in File.ReadAllLines(args[0])){
            if(line.StartsWith("#")||String.IsNullOrWhiteSpace(line))continue;
            string[] p=line.Split('|');if(p[0]!=scenario){scenario=p[0];engine=new SessionEngine(new Lower());}
            long now=Int64.Parse(p[2]);int result=0;
            switch(p[1]){case "start":engine.Start(Int32.Parse(p[3]),now);break;case "tick":result=engine.Advance(now);break;case "pause":engine.Pause(now);break;case "resume":engine.Resume(now);break;case "stop":engine.Stop(now);break;}
            string actual=(result+"|"+engine.Active+"|"+engine.Paused+"|"+engine.Reviewing+"|"+engine.Elapsed(now)+"|"+engine.PhaseRemaining(now)+"|"+engine.Reviews).ToLowerInvariant();
            string expected=String.Join("|",p,4,7);Check(actual==expected,line+" actual: "+actual);
        }
        engine=new SessionEngine(new Upper());engine.Start(90,0);Check(engine.PhaseEnd==1800000,"upper study bound is 30 minutes");Check(engine.Advance(1800000)==1,"upper-bound rest cue");Check(engine.PhaseLength==480000,"upper rest bound is 8 minutes");Check(engine.Advance(2280000)==2,"8-minute rest completed");Check(engine.PhaseEnd==4080000,"draw again after rest");
        engine=new SessionEngine(new Random(42));HashSet<long> draws=new HashSet<long>(),rests=new HashSet<long>();
        for(int i=0;i<10000;i++){engine.Start(90,0);Check(engine.PhaseEnd>=1200000&&engine.PhaseEnd<=1800000,"study random bounds");draws.Add(engine.PhaseEnd);engine.Advance(engine.PhaseEnd);Check(engine.PhaseLength>=300000&&engine.PhaseLength<=480000,"rest random bounds");rests.Add(engine.PhaseLength);engine.Stop(engine.Anchor);}
        Check(draws.Count>9000&&rests.Count>9000,"study and rest intervals both vary");
        engine=new SessionEngine(new Lower());engine.Configure(2,4,1,3);engine.Start(10,0);Check(engine.PhaseEnd==120000,"custom study lower bound");engine.Advance(120000);Check(engine.PhaseLength==60000,"custom rest lower bound");
        bool locked=false;try{engine.Configure(3,3,2,2);}catch(InvalidOperationException){locked=true;}Check(locked,"active settings locked");engine.Stop(130000);
        engine.Configure(1,1,1,1);engine.Start(3,0);Check(engine.Advance(60000)==1&&engine.PhaseLength==60000,"equal endpoints give fixed rest");Check(engine.Advance(120000)==2&&engine.PhaseLength==60000,"equal endpoints give fixed study");Check(engine.Advance(180000)==3,"fixed cycle total cap");
        foreach(int[] bad in new int[][]{new int[]{0,30,5,8},new int[]{31,30,5,8},new int[]{20,91,5,8},new int[]{20,30,0,8},new int[]{20,30,9,8},new int[]{20,30,5,91}}){bool rejected=false;try{engine.Configure(bad[0],bad[1],bad[2],bad[3]);}catch(ArgumentOutOfRangeException){rejected=true;}Check(rejected,"invalid range rejected");}
        engine.Configure(90,90,90,90);engine.Start(90,0);Check(engine.Advance(5400000)==3&&engine.Reviews==0,"session cap before oversized rest");
        foreach(int invalid in new int[]{-1,0,91,Int32.MaxValue}){bool rejected=false;try{engine.Start(invalid,0);}catch(ArgumentOutOfRangeException){rejected=true;}Check(rejected,"duration cap");}
        engine.Start(90,0);Check(engine.TargetMs==5400000,"90 minute limit");
        Console.WriteLine("C#: "+assertions+" assertions passed.");
    }
}
