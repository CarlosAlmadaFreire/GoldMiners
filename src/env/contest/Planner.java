package contest;

import java.util.*;

/** A* uses observed walls only. Unknown cells remain traversable for exploration. */
public final class Planner {
    private record Node(Game.Pos p,int g,int f,long order) {}
    public static List<Game.Pos> path(Game game,Map<Game.Pos,Game.Cell> known,Game.Pos start,Game.Pos goal,Set<Game.Pos> occupied){
        return path(game.config.width,game.config.height,known,start,goal,occupied);
    }
    public static List<Game.Pos> path(int width,int height,Map<Game.Pos,Game.Cell> known,Game.Pos start,Game.Pos goal,Set<Game.Pos> occupied){
        if(goal.x()<0||goal.y()<0||goal.x()>=width||goal.y()>=height)return List.of();
        PriorityQueue<Node> open=new PriorityQueue<>(Comparator.comparingInt(Node::f).thenComparingLong(Node::order));
        Map<Game.Pos,Integer> costs=new HashMap<>();Map<Game.Pos,Game.Pos> parent=new HashMap<>();long seq=0;
        costs.put(start,0);open.add(new Node(start,0,start.distance(goal),seq++));
        while(!open.isEmpty()){
            Node n=open.remove();if(n.g!=costs.getOrDefault(n.p,Integer.MAX_VALUE))continue;
            if(n.p.equals(goal)){
                LinkedList<Game.Pos> route=new LinkedList<>();Game.Pos p=goal;
                while(!p.equals(start)){route.addFirst(p);p=parent.get(p);}return route;
            }
            for(Game.Pos p:List.of(new Game.Pos(n.p.x()-1,n.p.y()),new Game.Pos(n.p.x()+1,n.p.y()),new Game.Pos(n.p.x(),n.p.y()-1),new Game.Pos(n.p.x(),n.p.y()+1))){
                if(p.x()<0||p.y()<0||p.x()>=width||p.y()>=height)continue;
                Game.Cell c=known.get(p);if((c!=null&&c.obstacle())||occupied.contains(p))continue;
                int g=n.g+1;if(g<costs.getOrDefault(p,Integer.MAX_VALUE)){costs.put(p,g);parent.put(p,n.p);open.add(new Node(p,g,g+p.distance(goal),seq++));}
            }
        }
        return List.of();
    }
    static int distance(Game game,Map<Game.Pos,Game.Cell> known,Game.Pos a,Game.Pos b){if(a.equals(b))return 0;List<Game.Pos> p=path(game,known,a,b,Set.of());return p.isEmpty()?100000:p.size();}
    public static String direction(Game.Pos a,Game.Pos b){if(b.x()<a.x())return "left";if(b.x()>a.x())return "right";return b.y()<a.y()?"up":"down";}
}
