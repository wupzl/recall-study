using System;

namespace Recall {
    public sealed class SessionEngine {
        public const int None=0, Review=1, Study=2, Complete=3;
        readonly Random random;
        public int StudyMin=20, StudyMax=30, RestMin=5, RestMax=8;
        public long TargetMs, ElapsedBefore, Anchor, PhaseEnd, PhaseLength;
        public bool Active, Paused, Reviewing;
        public int Reviews;
        public SessionEngine(Random random) { this.random=random; }
        public void Configure(int studyMin,int studyMax,int restMin,int restMax) {
            if(Active) throw new InvalidOperationException("学习进行中不能修改节奏");
            if(!ValidRange(studyMin,studyMax)||!ValidRange(restMin,restMax)) throw new ArgumentOutOfRangeException("range","时间范围应为 1～90 分钟，且下限不能大于上限");
            StudyMin=studyMin; StudyMax=studyMax; RestMin=restMin; RestMax=restMax;
        }
        public static bool ValidRange(int min,int max) { return min>=1 && max<=90 && min<=max; }
        public void Start(int minutes,long now) {
            if(minutes<1 || minutes>90) throw new ArgumentOutOfRangeException("minutes");
            TargetMs=minutes*60000L; ElapsedBefore=0; Anchor=now; Reviews=0;
            Active=true; Paused=false; Reviewing=false; PhaseLength=Interval(StudyMin,StudyMax); PhaseEnd=PhaseLength;
        }
        public long Elapsed(long now) { return Math.Min(TargetMs,Math.Max(0,ElapsedBefore+(Active && !Paused?Math.Max(0,now-Anchor):0))); }
        public long Remaining(long now) { return Math.Max(0,TargetMs-Elapsed(now)); }
        public long PhaseRemaining(long now) { return Math.Min(Remaining(now),Math.Max(0,PhaseEnd-Elapsed(now))); }
        public int Advance(long now) {
            if(!Active || Paused) return None;
            long elapsed=Elapsed(now);
            if(elapsed>=TargetMs) { ElapsedBefore=TargetMs; Active=false; Paused=false; return Complete; }
            if(elapsed>=PhaseEnd) {
                Reviewing=!Reviewing;
                if(Reviewing){ Reviews++; PhaseLength=Interval(RestMin,RestMax); } else PhaseLength=Interval(StudyMin,StudyMax);
                PhaseEnd=elapsed+PhaseLength;
                return Reviewing?Review:Study;
            }
            return None;
        }
        public void Pause(long now) { if(Active && !Paused){ ElapsedBefore=Elapsed(now); Paused=true; } }
        public void Resume(long now) { if(Active && Paused){ Anchor=now; Paused=false; } }
        public void Stop(long now) { ElapsedBefore=Elapsed(now); Active=false; Paused=false; }
        long Interval(int min,int max) { return min*60000L+random.Next((max-min)*60000+1); }
    }
}
