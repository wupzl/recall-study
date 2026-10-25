using System;

namespace Recall {
    public sealed class SessionEngine {
        public const int None=0, Review=1, Study=2, Complete=3;
        public const long ReviewMs=20000;
        readonly Random random;
        public long TargetMs, ElapsedBefore, Anchor, PhaseEnd, PhaseLength;
        public bool Active, Paused, Reviewing;
        public int Reviews;
        public SessionEngine(Random random) { this.random=random; }
        public void Start(int minutes,long now) {
            if(minutes<1 || minutes>90) throw new ArgumentOutOfRangeException("minutes");
            TargetMs=minutes*60000L; ElapsedBefore=0; Anchor=now; Reviews=0;
            Active=true; Paused=false; Reviewing=false; PhaseLength=Interval(); PhaseEnd=PhaseLength;
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
                if(Reviewing){ Reviews++; PhaseLength=ReviewMs; } else PhaseLength=Interval();
                PhaseEnd=elapsed+PhaseLength;
                return Reviewing?Review:Study;
            }
            return None;
        }
        public void Pause(long now) { if(Active && !Paused){ ElapsedBefore=Elapsed(now); Paused=true; } }
        public void Resume(long now) { if(Active && Paused){ Anchor=now; Paused=false; } }
        public void Stop(long now) { ElapsedBefore=Elapsed(now); Active=false; Paused=false; }
        long Interval() { return 180000L+random.Next(120001); }
    }
}
