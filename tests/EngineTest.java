import cn.recall.study.SessionEngine;
import java.nio.file.*;
import java.util.*;

public final class EngineTest {
    static int assertions;
    static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    static final class Lower extends Random { public int nextInt(int bound){return 0;} }
    static final class Upper extends Random { public int nextInt(int bound){return bound-1;} }
    public static void main(String[] args)throws Exception{
        SessionEngine engine=null; String scenario="";
        for(String line:Files.readAllLines(Paths.get(args[0]),java.nio.charset.StandardCharsets.UTF_8)){
            if(line.startsWith("#")||line.trim().isEmpty())continue;
            String[] p=line.split("\\|"); if(!p[0].equals(scenario)){scenario=p[0];engine=new SessionEngine(new Lower());}
            long now=Long.parseLong(p[2]);int event=0;
            switch(p[1]){case "start":engine.start(Integer.parseInt(p[3]),now);break;case "tick":event=engine.advance(now);break;case "pause":engine.pause(now);break;case "resume":engine.resume(now);break;case "stop":engine.stop(now);break;}
            String actual=event+"|"+engine.active+"|"+engine.paused+"|"+engine.reviewing+"|"+engine.elapsed(now)+"|"+engine.phaseRemaining(now)+"|"+engine.reviews;
            String expected=String.join("|",Arrays.copyOfRange(p,4,11));check(actual.equals(expected),line+" actual: "+actual);
        }
        engine=new SessionEngine(new Upper());engine.start(90,0);check(engine.phaseEnd==300000,"upper bound must be 5 minutes");check(engine.advance(300000)==1,"upper-bound cue");check(engine.advance(320000)==2,"20 second recall");check(engine.phaseEnd==620000,"draw again after recall");
        engine=new SessionEngine(new Random(42));Set<Long> draws=new HashSet<>();
        for(int i=0;i<10000;i++){engine.start(90,0);check(engine.phaseEnd>=180000&&engine.phaseEnd<=300000,"random bounds");draws.add(engine.phaseEnd);}
        check(draws.size()>9000,"intervals should vary at millisecond resolution");
        for(int invalid:new int[]{-1,0,91,Integer.MAX_VALUE}){boolean rejected=false;try{engine.start(invalid,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"duration cap");}
        engine.start(90,0);check(engine.targetMs==5400000,"90 minute limit");
        System.out.println("Java: "+assertions+" assertions passed.");
    }
}
