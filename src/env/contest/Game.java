package contest;

import java.util.*;

/** Round based referee. All mutations are atomic under this instance's monitor. */
public final class Game {
    public record Pos(int x,int y) {
        public int distance(Pos p){return Math.abs(x-p.x)+Math.abs(y-p.y);}
    }
    public record Ore(String type,int value) {}
    public record Cell(Pos pos,boolean obstacle,Ore ore,boolean depot,int occupant) {}
    public record View(int round,Pos pos,List<Ore> cargo,List<Cell> cells,int red,int blue,boolean ended,String result) {}
    public final Config config;
    final Set<Pos> walls=new HashSet<>();
    final Map<Pos,Ore> ores=new HashMap<>();
    final Pos[] positions;
    final List<List<Ore>> cargo=new ArrayList<>();
    final String[] outcomes;
    final String[] pending;
    final int[] scores=new int[2];
    final int[] stolen=new int[2];
    final int[] duplicates=new int[2];
    final long[] messages=new long[2];
    final List<Set<Pos>> removedBy=List.of(new HashSet<>(),new HashSet<>());
    final List<Set<Pos>> lostTargets=List.of(new HashSet<>(),new HashSet<>());
    int round=0, emptied=-1;
    private int delivered=0;
    public Game(Config config) {
        this.config=config;
        int n=config.agents*2;
        positions=new Pos[n];outcomes=new String[n];pending=new String[n];
        Arrays.fill(outcomes,"ready");
        Random rng=new Random(config.seed);
        List<Pos> free=new ArrayList<>();
        for(int y=0;y<config.height;y++)for(int x=0;x<config.width;x++) {
            Pos p=new Pos(x,y);if(!config.depots.contains(p))free.add(p);
        }
        Collections.shuffle(free,rng);
        for(int i=0;i<n;i++){positions[i]=free.remove(free.size()-1);cargo.add(new ArrayList<>());}
        // Preserve connectivity of all traversable cells, avoiding unreachable minerals.
        for(Pos p:new ArrayList<>(free)) {
            if(walls.size()==config.obstacles)break;
            walls.add(p);
            if(!connected())walls.remove(p);
        }
        if(walls.size()!=config.obstacles)throw new IllegalArgumentException("Cannot place connected obstacles");
        free.removeAll(walls);
        for(int i=0;i<config.minerals;i++) {
            Pos p=free.remove(free.size()-1);int type=rng.nextInt(3);
            ores.put(p,new Ore(new String[]{"gold","silver","bronze"}[type],config.values[type]));
        }
        if(ores.isEmpty())emptied=0;
    }
    synchronized void recordLost(int id,Pos p){int t=team(id);if(removedBy.get(1-t).contains(p)&&lostTargets.get(t).add(p))stolen[t]++;}
    private boolean connected() {
        Set<Pos> seen=new HashSet<>();ArrayDeque<Pos> q=new ArrayDeque<>();q.add(config.depots.get(0));
        while(!q.isEmpty()){Pos p=q.remove();if(!seen.add(p))continue;for(Pos n:neighbors(p))if(!walls.contains(n)&&!seen.contains(n))q.add(n);}
        return seen.size()==config.width*config.height-walls.size();
    }
    public boolean inside(Pos p){return p.x>=0&&p.y>=0&&p.x<config.width&&p.y<config.height;}
    public List<Pos> neighbors(Pos p){return List.of(new Pos(p.x-1,p.y),new Pos(p.x+1,p.y),new Pos(p.x,p.y-1),new Pos(p.x,p.y+1)).stream().filter(this::inside).toList();}
    public int team(int id){return id/config.agents;}
    public synchronized boolean ended(){return round>=config.steps || delivered==config.minerals;}
    public synchronized int round(){return round;}
    public synchronized View view(int id) {
        List<Cell> cells=new ArrayList<>();Pos p=positions[id];
        for(int y=p.y-1;y<=p.y+1;y++)for(int x=p.x-1;x<=p.x+1;x++){
            Pos c=new Pos(x,y);if(inside(c))cells.add(new Cell(c,walls.contains(c),ores.get(c),config.depots.contains(c),occupant(c)));
        }
        return new View(round,p,List.copyOf(cargo.get(id)),List.copyOf(cells),scores[0],scores[1],ended(),outcomes[id]);
    }
    private int occupant(Pos p){for(int i=0;i<positions.length;i++)if(positions[i].equals(p))return i;return -1;}
    public synchronized void submit(int id,int expectedRound,String action) {
        if(ended())return;
        if(expectedRound!=round||pending[id]!=null)throw new IllegalStateException("Duplicate/stale action");
        if(!Set.of("up","down","left","right","pick","drop","skip").contains(action))throw new IllegalArgumentException("Invalid action");
        pending[id]=action;
        for(String a:pending)if(a==null)return;
        // Rotating priority prevents permanent low-ID advantage; independent of thread timing.
        for(int j=0;j<positions.length;j++){
            int i=(j+round)%positions.length;String a=pending[i];Pos p=positions[i];outcomes[i]="ok";
            switch(a){
                case "pick" -> {
                    if(cargo.get(i).size()>=config.capacity)outcomes[i]="full";
                    else {Ore ore=ores.remove(p);if(ore==null){outcomes[i]="missing";if(removedBy.get(1-team(i)).contains(p))recordLost(i,p);}
                        else {cargo.get(i).add(ore);removedBy.get(team(i)).add(p);}}
                }
                case "drop" -> {
                    if(!config.depots.contains(p))outcomes[i]="not_at_depot";
                    else {for(Ore ore:cargo.get(i))scores[team(i)]+=ore.value;delivered+=cargo.get(i).size();cargo.get(i).clear();}
                }
                case "skip" -> {}
                default -> {
                    Pos target=switch(a){case "up"->new Pos(p.x,p.y-1);case "down"->new Pos(p.x,p.y+1);case "left"->new Pos(p.x-1,p.y);default->new Pos(p.x+1,p.y);};
                    if(!inside(target)||walls.contains(target)||occupant(target)>=0)outcomes[i]="blocked";else positions[i]=target;
                }
            }
        }
        round++;Arrays.fill(pending,null);
        if(ores.isEmpty()&&emptied<0)emptied=round;
        notifyAll();
    }
    public synchronized String csv(){return config.seed+","+config.variants[0]+","+config.variants[1]+","+config.exploration+","+round+","+scores[0]+","+scores[1]+","+(scores[0]==scores[1]?"draw":scores[0]>scores[1]?"red":"blue")+","+emptied+","+messages[0]+","+messages[1]+","+duplicates[0]+","+duplicates[1]+","+stolen[0]+","+stolen[1];}
    public static final String HEADER="seed,red_variant,blue_variant,exploration,rounds,red_score,blue_score,winner,empty_round,red_messages,blue_messages,red_duplicates,blue_duplicates,red_lost,blue_lost";
}
