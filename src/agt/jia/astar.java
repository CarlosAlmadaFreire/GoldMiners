package jia;

import contest.*;
import jason.asSemantics.*;
import jason.asSyntax.*;
import java.util.*;

/** jia.astar(W,H,X,Y,TX,TY,[cell(X,Y,Kind,Value,Occupant),...],Direction). */
public class astar extends DefaultInternalAction {
    @Override public Object execute(TransitionSystem ts,Unifier un,Term[] terms) throws Exception {
        if(terms.length!=8)throw new IllegalArgumentException("astar expects 8 arguments");
        int[] n=new int[6];for(int i=0;i<6;i++)n[i]=(int)((NumberTerm)terms[i]).solve();
        Map<Game.Pos,Game.Cell> known=new HashMap<>();Set<Game.Pos> occupied=new HashSet<>();
        for(Term t:(ListTerm)terms[6]){
            Structure cell=(Structure)t;
            var pos=new Game.Pos((int)((NumberTerm)cell.getTerm(0)).solve(),(int)((NumberTerm)cell.getTerm(1)).solve());
            known.put(pos,new Game.Cell(pos,cell.getTerm(2).toString().equals("obstacle"),null,false,-1));
        }
        var from=new Game.Pos(n[2],n[3]);var goal=new Game.Pos(n[4],n[5]);
        var path=Planner.path(n[0],n[1],known,from,goal,occupied);
        return un.unifies(terms[7],new Atom(path.isEmpty()?"skip":Planner.direction(from,path.get(0))));
    }
}
