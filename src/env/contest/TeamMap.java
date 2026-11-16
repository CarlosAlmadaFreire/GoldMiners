package contest;

import cartago.*;
import java.util.*;

/** Team knowledge is updated exclusively from an agent's 3x3 observation. */
public class TeamMap extends Artifact {
    public static final class Knowledge {
        final Map<Game.Pos,Game.Cell> cells=new HashMap<>();
        final Map<Integer,Game.Pos> allies=new HashMap<>();
        final Map<Game.Pos,Integer> enemies=new HashMap<>();
        synchronized void observe(int id,Game.View view,Game game){
            allies.put(id,view.pos());
            for(Game.Cell c:view.cells()){
                cells.put(c.pos(),c);enemies.remove(c.pos());
                if(c.occupant()>=0&&game.team(c.occupant())!=game.team(id))enemies.put(c.pos(),view.round());
            }
            enemies.entrySet().removeIf(e->view.round()-e.getValue()>5);
        }
        public synchronized int size(){return cells.size();}
    }
    int team;
    void init(String config,int team) throws Exception {RuntimeMatch.open(config);this.team=team;defineObsProperty("known_cells",0);}
    @OPERATION void publish(){int id=RuntimeMatch.id(getOpUserName());RuntimeMatch.observe(id);getObsProperty("known_cells").updateValue(RuntimeMatch.shared[team].size());}
}
