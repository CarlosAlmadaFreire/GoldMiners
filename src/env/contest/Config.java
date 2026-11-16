package contest;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Immutable, validated experiment parameters. */
public final class Config {
    public final int width, height, agents, capacity, steps, minerals, obstacles, lease;
    public final long seed;
    public final String[] variants;
    public final String exploration;
    public final List<Game.Pos> depots;
    public final int[] values;
    public Config(Properties p) {
        width = number(p,"width",16); height = number(p,"height",16);
        agents = number(p,"agents",4); capacity = number(p,"capacity",3);
        steps = number(p,"steps",300); minerals = number(p,"minerals",60);
        obstacles = number(p,"obstacles",25); lease = number(p,"lease",20);
        seed = Long.parseLong(p.getProperty("seed","42"));
        variants = new String[]{p.getProperty("red","B"),p.getProperty("blue","A")};
        exploration = p.getProperty("exploration","frontier");
        values = Arrays.stream(p.getProperty("values","10,5,2").split(",")).mapToInt(Integer::parseInt).toArray();
        if (width<3 || height<3 || agents<1 || agents>8 || capacity<1 || steps<1 || minerals<0 || obstacles<0 || lease<1
                || values.length!=3 || Arrays.stream(values).anyMatch(v->v<=0)
                || !Set.of("frontier","sectors").contains(exploration)
                || Arrays.stream(variants).anyMatch(v->!Set.of("A","B","C","D").contains(v)))
            throw new IllegalArgumentException("Invalid scenario parameters");
        List<Game.Pos> ds = new ArrayList<>();
        for (String pair:p.getProperty("depots","0,0;"+(width-1)+","+(height-1)).split(";")) {
            String[] xy=pair.split(",");
            Game.Pos pos=new Game.Pos(Integer.parseInt(xy[0]),Integer.parseInt(xy[1]));
            if(pos.x()<0||pos.y()<0||pos.x()>=width||pos.y()>=height||ds.contains(pos)) throw new IllegalArgumentException("Invalid depot");
            ds.add(pos);
        }
        depots=List.copyOf(ds);
        if(depots.isEmpty() || obstacles+minerals+depots.size()+agents*2>width*height)
            throw new IllegalArgumentException("Grid too small for scenario");
    }
    private static int number(Properties p,String k,int fallback){return Integer.parseInt(p.getProperty(k,""+fallback));}
    public static Config load(String path) throws IOException {
        Properties p=new Properties();
        try(Reader r=Files.newBufferedReader(Path.of(path))){p.load(r);}
        for(String k:List.of("seed","red","blue","steps","exploration","agents")) {
            String v=System.getProperty("contest."+k); if(v!=null)p.setProperty(k,v);
        }
        return new Config(p);
    }
}
