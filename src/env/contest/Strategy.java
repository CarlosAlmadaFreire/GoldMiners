package contest;

import java.util.*;

/** Policy support for Jason; takes only private/team knowledge and the local view. */
public final class Strategy {
    final Game game;
    final TeamMap.Knowledge[] privateMaps;
    final TeamMap.Knowledge[] shared;
    final ClaimBoard.Board[] boards;
    final Game.Pos[] targets;
    public Strategy(Game game,TeamMap.Knowledge[] shared,ClaimBoard.Board[] boards){
        this.game=game;this.shared=shared;this.boards=boards;
        privateMaps=new TeamMap.Knowledge[game.positions.length];targets=new Game.Pos[game.positions.length];
        Arrays.setAll(privateMaps,i->new TeamMap.Knowledge());
    }
    TeamMap.Knowledge knowledge(int id){return game.config.variants[game.team(id)].equals("A")?privateMaps[id]:shared[game.team(id)];}
    private int observedRound=-1;
    private final int[] plannedRound={-1,-1};
    private String[] actions;
    synchronized void observe(int id){
        if(observedRound==game.round())return;
        for(int i=0;i<game.positions.length;i++)knowledge(i).observe(i,game.view(i),game);
        for(int i=0;i<targets.length;i++){
            Game.Cell cell=knowledge(i).cells.get(targets[i]);
            if(targets[i]!=null&&cell!=null&&cell.ore()==null)game.recordLost(i,targets[i]);
        }
        observedRound=game.round();
    }
    public synchronized String action(int id){
        int team=game.team(id);
        if(actions==null)actions=new String[game.positions.length];
        if(plannedRound[team]!=game.round()){
            String v=game.config.variants[team];
            for(int j=0;j<game.config.agents;j++){
                int local=(j+game.round())%game.config.agents;
                int member=team*game.config.agents+local;
                actions[member]=decide(member,v.equals("C")||v.equals("D"));
            }
            plannedRound[team]=game.round();
        }
        return actions[id];
    }
    boolean scouting(int id){return game.config.variants[game.team(id)].equals("D")&&id%game.config.agents==0&&knowledge(id).size()<game.config.width*game.config.height*0.85;}
    public synchronized Map<Game.Pos,Double> bids(int id){
        observe(id);Game.View v=game.view(id);Map<Game.Pos,Double> bids=new LinkedHashMap<>();
        if(v.cargo().size()>=game.config.capacity||scouting(id))return bids;
        var k=knowledge(id);
        for(Game.Cell c:k.cells.values().stream().sorted(Comparator.comparingInt((Game.Cell c)->c.pos().y()).thenComparingInt(c->c.pos().x())).toList())if(c.ore()!=null){
            int out=Planner.distance(game,k.cells,v.pos(),c.pos());int back=depotDistance(k,c.pos());
            if(out+back+2>=game.config.steps-v.round())continue;
            double urgency=k.enemies.keySet().stream().mapToInt(p->p.distance(c.pos())).min().orElse(100)<out?1.5:1;
            bids.put(c.pos(),(out+back+2)/(c.ore().value()*urgency));
        }
        return bids;
    }
    int depotDistance(TeamMap.Knowledge k,Game.Pos p){return game.config.depots.stream().mapToInt(d->Planner.distance(game,k.cells,p,d)).min().orElse(100000);}
    Game.Pos depot(TeamMap.Knowledge k,Game.Pos p){return game.config.depots.stream().min(Comparator.comparingInt(d->Planner.distance(game,k.cells,p,d))).orElseThrow();}
    public synchronized String decide(int id,boolean auction){
        observe(id);Game.View v=game.view(id);var k=knowledge(id);int team=game.team(id);var board=boards[team];
        boolean coordinated=!game.config.variants[team].equals("A");
        if(v.ended())return "skip";
        int back=depotDistance(k,v.pos());
        boolean goHome=!v.cargo().isEmpty()&&(v.cargo().size()>=game.config.capacity||game.config.steps-v.round()<=back+4);
        if(goHome){board.release(id);targets[id]=null;return travel(k,v,depot(k,v.pos()));}
        Game.Cell here=k.cells.get(v.pos());
        if(!scouting(id)&&here!=null&&here.ore()!=null&&v.cargo().size()<game.config.capacity&&(!coordinated||board.reserve(v.pos(),id,v.round(),game.config.lease))){targets[id]=v.pos();return "pick";}
        Map<Game.Pos,Double> bids=bids(id);
        Game.Pos target=targets[id];
        if(target!=null&&(!bids.containsKey(target)||!board.available(target,id,v.round()))){game.recordLost(id,target);board.release(id);targets[id]=target=null;}
        if(!auction){
            for(var e:bids.entrySet().stream().sorted(Map.Entry.comparingByValue()).toList()){
                if(!coordinated||board.reserve(e.getKey(),id,v.round(),game.config.lease)){
                    if(target!=null&&!target.equals(e.getKey())){board.release(id);board.reserve(e.getKey(),id,v.round(),game.config.lease);}
                    target=e.getKey();targets[id]=target;break;
                }
            }
        }
        if(target!=null){if(coordinated)board.reserve(target,id,v.round(),game.config.lease);return travel(k,v,target);}
        if(!v.cargo().isEmpty())return travel(k,v,depot(k,v.pos()));
        Game.Pos frontier=null;int best=Integer.MAX_VALUE;
        for(int y=0;y<game.config.height;y++)for(int x=0;x<game.config.width;x++){
            Game.Pos p=new Game.Pos(x,y);if(k.cells.containsKey(p))continue;
            int sector=x*game.config.agents/game.config.width;
            int penalty=game.config.exploration.equals("sectors")&&sector!=id%game.config.agents?game.config.width*game.config.height:0;
            int d=Planner.distance(game,k.cells,v.pos(),p)+penalty;
            if(coordinated)d+=k.allies.entrySet().stream().anyMatch(e->e.getKey()!=id&&e.getValue().distance(p)<v.pos().distance(p))?5:0;
            if(d<best){best=d;frontier=p;}
        }
        if(frontier==null&&game.config.depots.contains(v.pos())){
            for(Game.Pos p:game.neighbors(v.pos())){
                String a=travel(k,v,p);if(!a.equals("skip"))return a;
            }
        }
        return frontier==null?"skip":travel(k,v,frontier);
    }
    private String travel(TeamMap.Knowledge k,Game.View v,Game.Pos target){
        if(v.pos().equals(target))return game.config.depots.contains(target)&&!v.cargo().isEmpty()?"drop":"skip";
        Set<Game.Pos> occupied=new HashSet<>();for(var c:v.cells())if(c.occupant()>=0&&!c.pos().equals(v.pos()))occupied.add(c.pos());
        List<Game.Pos> path=Planner.path(game,k.cells,v.pos(),target,occupied);
        return path.isEmpty()?"skip":Planner.direction(v.pos(),path.get(0));
    }
    synchronized void allocate(int team,Map<Integer,Map<Game.Pos,Double>> offers){
        record Bid(int id,Game.Pos pos,double cost){}
        List<Bid> bids=new ArrayList<>();
        offers.forEach((id,list)->list.forEach((pos,cost)->bids.add(new Bid(id,pos,cost))));
        bids.sort(Comparator.comparingDouble(Bid::cost).thenComparingInt(Bid::id).thenComparingInt(b->b.pos().y()).thenComparingInt(b->b.pos().x()));
        Set<Integer> assigned=new HashSet<>();Set<Game.Pos> allocated=new HashSet<>();
        for(int id:offers.keySet()){boards[team].release(id);targets[id]=null;}
        for(Bid b:bids)if(!assigned.contains(b.id)&&!allocated.contains(b.pos)&&boards[team].reserve(b.pos,b.id,game.round(),game.config.lease)){
            targets[b.id]=b.pos;assigned.add(b.id);allocated.add(b.pos);
        }
    }
}
