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
        engine=new SessionEngine(new Upper());engine.start(90,0);check(engine.phaseEnd==1800000,"upper study bound is 30 minutes");check(engine.advance(1800000)==1,"upper-bound rest cue");check(engine.phaseLength==480000,"upper rest bound is 8 minutes");check(engine.advance(2280000)==2,"8-minute rest completed");check(engine.phaseEnd==4080000,"draw study again after rest");
        engine=new SessionEngine(new Random(42));Set<Long> draws=new HashSet<>(),rests=new HashSet<>();
        for(int i=0;i<10000;i++){engine.start(90,0);check(engine.phaseEnd>=1200000&&engine.phaseEnd<=1800000,"study random bounds");draws.add(engine.phaseEnd);engine.advance(engine.phaseEnd);check(engine.phaseLength>=300000&&engine.phaseLength<=480000,"rest random bounds");rests.add(engine.phaseLength);engine.stop(engine.anchor);}
        check(draws.size()>9000&&rests.size()>9000,"study and rest intervals both vary");
        engine=new SessionEngine(new Lower());engine.configure(2,4,1,3);engine.start(10,0);check(engine.phaseEnd==120000,"custom study lower bound");engine.advance(120000);check(engine.phaseLength==60000,"custom rest lower bound");
        boolean locked=false;try{engine.configure(3,3,2,2);}catch(IllegalStateException e){locked=true;}check(locked,"active settings locked");engine.stop(130000);
        engine.configure(1,1,1,1);engine.start(3,0);check(engine.advance(60000)==1&&engine.phaseLength==60000,"equal endpoints give fixed rest");check(engine.advance(120000)==2&&engine.phaseLength==60000,"equal endpoints give fixed study");check(engine.advance(180000)==3,"fixed cycle total cap");
        for(int[] bad:new int[][]{{0,30,5,8},{31,30,5,8},{20,91,5,8},{20,30,0,8},{20,30,9,8},{20,30,5,91}}){boolean rejected=false;try{engine.configure(bad[0],bad[1],bad[2],bad[3]);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid range rejected");}
        engine.configure(90,90,90,90);engine.start(90,0);check(engine.advance(5400000)==3&&engine.reviews==0,"session cap before oversized rest");
        for(int invalid:new int[]{-1,0,91,Integer.MAX_VALUE}){boolean rejected=false;try{engine.start(invalid,0);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"duration cap");}
        engine.start(90,0);check(engine.targetMs==5400000,"90 minute limit");
        System.out.println("Java: "+assertions+" assertions passed.");
    }
}
