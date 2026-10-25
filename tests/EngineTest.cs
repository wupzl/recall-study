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
        engine=new SessionEngine(new Upper());engine.Start(90,0);Check(engine.PhaseEnd==300000,"upper bound must be 5 minutes");Check(engine.Advance(300000)==1,"upper-bound cue");Check(engine.Advance(320000)==2,"20 second recall");Check(engine.PhaseEnd==620000,"draw again after recall");
        engine=new SessionEngine(new Random(42));HashSet<long> draws=new HashSet<long>();
        for(int i=0;i<10000;i++){engine.Start(90,0);Check(engine.PhaseEnd>=180000&&engine.PhaseEnd<=300000,"random bounds");draws.Add(engine.PhaseEnd);}
        Check(draws.Count>9000,"intervals should vary at millisecond resolution");
        foreach(int invalid in new int[]{-1,0,91,Int32.MaxValue}){bool rejected=false;try{engine.Start(invalid,0);}catch(ArgumentOutOfRangeException){rejected=true;}Check(rejected,"duration cap");}
        engine.Start(90,0);Check(engine.TargetMs==5400000,"90 minute limit");
        Console.WriteLine("C#: "+assertions+" assertions passed.");
    }
}
