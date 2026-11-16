package contest;

import java.nio.file.*;
import java.util.*;

/** Fast policy benchmark. Jason/Moise integration must be tested with gradle run. */
public final class Experiment {
    public static Game play(Config config){
        Game g=new Game(config);
        var maps=new TeamMap.Knowledge[]{new TeamMap.Knowledge(),new TeamMap.Knowledge()};
        var boards=new ClaimBoard.Board[]{new ClaimBoard.Board(),new ClaimBoard.Board()};
        Strategy s=new Strategy(g,maps,boards);
        while(!g.ended()){
            for(int t=0;t<2;t++)if(Set.of("C","D").contains(config.variants[t])){
                Map<Integer,Map<Game.Pos,Double>> offers=new TreeMap<>();
                for(int i=t*config.agents;i<(t+1)*config.agents;i++)offers.put(i,s.bids(i));
                s.allocate(t,offers);g.messages[t]+=config.agents*3;
            }
            String[] actions=new String[config.agents*2];
            for(int i=0;i<actions.length;i++)actions[i]=s.action(i);
            for(int t=0;t<2;t++){
                Set<Game.Pos> seen=new HashSet<>();
                for(int i=t*config.agents;i<(t+1)*config.agents;i++)if(s.targets[i]!=null&&!seen.add(s.targets[i]))g.duplicates[t]++;
            }
            int round=g.round();for(int i=0;i<actions.length;i++)g.submit(i,round,actions[i]);
        }
        return g;
    }
    public static void main(String[] args) throws Exception {
        String path=args.length>0?args[0]:"config/default.properties";
        int seeds=args.length>1?Integer.parseInt(args[1]):5;
        Path output=Path.of(args.length>2?args[2]:"build/results/policies.csv");
        Files.createDirectories(output.toAbsolutePath().getParent());
        StringBuilder csv=new StringBuilder(Game.HEADER+"\n");
        Properties base=new Properties();try(var r=Files.newBufferedReader(Path.of(path))){base.load(r);}
        long first=Long.parseLong(base.getProperty("seed","42"));
        // D shares C's policy here: Moise overhead/compliance is measured only in MAS runs.
        for(String variant:List.of("A","B","C"))for(String exploration:List.of("frontier","sectors"))for(int seed=0;seed<seeds;seed++){
            Properties p=new Properties();p.putAll(base);p.setProperty("red",variant);p.setProperty("blue","A");p.setProperty("exploration",exploration);p.setProperty("seed",""+(first+seed));
            Game g=play(new Config(p));csv.append(g.csv()).append('\n');System.out.println(g.csv());
        }
        Files.writeString(output,csv);
    }
}
