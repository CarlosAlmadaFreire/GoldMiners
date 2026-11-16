package contest;

import cartago.*;
import java.util.*;

/** Atomic, expiring reservations. Owners are checked for renewals and release. */
public class ClaimBoard extends Artifact {
    public record Claim(int owner,int expires) {}
    public static final class Board {
        final Map<Game.Pos,Claim> claims=new HashMap<>();
        synchronized boolean reserve(Game.Pos p,int id,int round,int ttl){
            expire(round);Claim c=claims.get(p);
            if(c!=null&&c.owner()!=id)return false;
            claims.put(p,new Claim(id,round+ttl));return true;
        }
        synchronized void expire(int round){claims.entrySet().removeIf(e->e.getValue().expires()<=round);}
        synchronized boolean available(Game.Pos p,int id,int round){expire(round);Claim c=claims.get(p);return c==null||c.owner()==id;}
        synchronized void release(int id){claims.entrySet().removeIf(e->e.getValue().owner()==id);}
    }
    int team;
    void init(String config,int team) throws Exception {RuntimeMatch.open(config);this.team=team;}
    @OPERATION void reserve(int x,int y,OpFeedbackParam<Boolean> result){int id=RuntimeMatch.id(getOpUserName());if(RuntimeMatch.game.team(id)!=team){failed("wrong_team");return;}result.set(RuntimeMatch.boards[team].reserve(new Game.Pos(x,y),id,RuntimeMatch.game.round(),RuntimeMatch.game.config.lease));}
    @OPERATION void release(){int id=RuntimeMatch.id(getOpUserName());RuntimeMatch.boards[team].release(id);}
}
