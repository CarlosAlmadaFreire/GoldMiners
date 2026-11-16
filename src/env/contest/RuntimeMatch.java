package contest;

import java.nio.file.*;
import java.util.*;

/** One match per JaCaMo process; all artifacts reference the same referee. */
public final class RuntimeMatch {
    static Game game;
    static Strategy strategy;
    static TeamMap.Knowledge[] shared;
    static ClaimBoard.Board[] boards;
    static final Set<Integer> registered=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static final Set<Integer> orgReady=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static boolean written;
    static synchronized void open(String path) throws Exception {
        if(game!=null)return;
        game=new Game(Config.load(path));
        shared=new TeamMap.Knowledge[]{new TeamMap.Knowledge(),new TeamMap.Knowledge()};
        boards=new ClaimBoard.Board[]{new ClaimBoard.Board(),new ClaimBoard.Board()};
        strategy=new Strategy(game,shared,boards);
    }
    static int id(String name){
        int team=name.startsWith("red")?0:name.startsWith("blue")?1:-1;
        if(team<0)throw new IllegalArgumentException("Unknown miner "+name);
        int local=Integer.parseInt(name.substring(team==0?3:4))-1;
        if(local<0||local>=game.config.agents)throw new IllegalArgumentException("Invalid miner "+name);
        return team*game.config.agents+local;
    }
    static String name(int id){return (game.team(id)==0?"red":"blue")+(id%game.config.agents+1);}
    static void observe(int id){strategy.observe(id);}
    static synchronized void finish() throws Exception {
        if(written||!game.ended())return;
        Path p=Path.of(System.getProperty("contest.output","build/results/match.csv"));
        if(p.toAbsolutePath().getParent()!=null)Files.createDirectories(p.toAbsolutePath().getParent());
        Files.writeString(p,Game.HEADER+"\n"+game.csv()+"\n");written=true;
        System.out.println("MATCH_RESULT "+game.csv());
    }
}
