package YBCriber;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.LinkedList;
import java.util.Queue;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

public class GridBFS {
  ArrayList<Observation>[][] grid;
  int [][] visited;
  int [][] laststep;
  int [] xv = {0, -1, 0, 1};
  int [] yv = {1, 0, -1, 0};
  double maxscore;
  int res;
  int type;
  double newobject_score = 0.01;
  double mov_score = 0.0001;
  Vector2d posini;
  Vector2d myTarget;
  boolean foundTarget;
  HashMap<Integer, Integer> spriteDirection;
  HashMap<Integer, Vector2d> spriteLocation;
  HashMap<Integer, Integer> spriteCount;
  public boolean interesting;

  int[][] BFSvisited;

  GridBFS (StateObservation so){
    this.grid = so.getObservationGrid();
    this.visited = new int [grid.length][grid[0].length];
    this.laststep = new int [grid.length][grid[0].length];
    this.BFSvisited = new int [grid.length][grid[0].length];
    this.maxscore = 0;
    this.res = -1;
    this.posini = Agent.getAvatarGridPositionDouble(so);
    this.type = Agent.getAvatarItype(grid, posini);
    foundTarget = false;
    spriteDirection = new HashMap<>();
    spriteCount = new HashMap<>();
    spriteLocation = new HashMap<>();
    interesting = false;
  }

  public int firstStep(){
    Queue <Vector2d> Q = new LinkedList <Vector2d> ();
    if (Agent.actions.size() < 5) return 0;
    if (posini.x < 0 || posini.y < 0 || posini.x >= grid.length || posini.y >= grid[(int)posini.x].length) return 0;
    int x = (int)Math.round(posini.x), y = (int)Math.round(posini.y);
    //System.out.println("My Position " + x + " " + y);
    if (Math.abs(x - posini.x) < 0.001 && Math.abs(y - posini.y) < 0.001) {
      visited[x][y] = 1;
      laststep[x][y] = -1;
      Q.add(posini);
    }
    else if (Math.abs(x - posini.x) >= 0.001){
      visited[(int)Math.floor(posini.x)][y] = 2;
      laststep[(int)Math.floor(posini.x)][y] = 1;
      Q.add(new Vector2d (Math.floor(posini.x), y));
      visited[(int)Math.ceil(posini.x)][y] = 2;
      laststep[(int)Math.ceil(posini.x)][y] = 3;
      Q.add(new Vector2d (Math.ceil(posini.x), y));
    }

    else {
      visited[x][(int)Math.floor(posini.y)] = 2;
      laststep[x][(int)Math.floor(posini.y)] = 2;
      Q.add(new Vector2d (x, Math.floor(posini.y)));
      visited[x][(int)Math.ceil(posini.y)] = 2;
      laststep[x][(int)Math.ceil(posini.y)] = 0;
      Q.add(new Vector2d (x, Math.ceil(posini.y)));
    }


    while (!(Q.size() == 0)){
      Vector2d curpos = Q.remove();
      for (int i = 0; i < 4; ++i){
        //x = (int) curpos.x; y = (int) curpos.y;
        //if (Math.abs(x - curpos.x) > 0.1) {

        //}
        if (fill(curpos, i)) Q.add(new Vector2d(curpos.x + xv[i], curpos.y + yv[i]));
        BFSvisited[(int) curpos.x][(int)curpos.y] = 1;
      }
    }
    //Agent.printMatrix(BFSvisited);
    int a = findAction(res);
    if (foundTarget){
      //System.out.println("My Target " + myTarget.x + " " + myTarget.y);
      return a;
    }
    return findMostInterestingSprite();
    //return 0;
  }

  private boolean fill (Vector2d curpos, int i){
    boolean acc = true;
    int x = (int)curpos.x + xv[i];
    int y = (int)curpos.y + yv[i];
    double auxmaxscore = maxscore;
    int auxres = res;
    if (x < 0 || y < 0 || x >= grid.length || y >= grid[0].length) return false;
    //BFSvisited[x][y] = 1;
    if (visited[x][y] > 0 || Agent.dangerPos[x][y] > 0) return false;
    visited[x][y] = visited[(int)curpos.x][(int)curpos.y] + 1;
    if (visited[x][y] == 2) laststep[x][y] = i;
    else laststep[x][y] = laststep[(int)curpos.x][(int)curpos.y];
    for (Observation o : grid[x][y]){
      if (!spriteCount.containsKey(o.itype)){
        spriteDirection.put(o.itype, laststep[(int)curpos.x][(int)curpos.y]);
        spriteCount.put(o.itype, 1);
        spriteLocation.put(o.itype, new Vector2d(x,y));
      }
      else{
        int a = spriteCount.get(o.itype);
        spriteCount.remove(o.itype);
        spriteCount.put(o.itype, a+1);
      }
      if (!Agent.obsProperties.containsKey(type)) return false;
      if (!Agent.obsProperties.get(type).containsKey(o.itype)){
        if (auxmaxscore < newobject_score/visited[x][y]){
          auxmaxscore = newobject_score/visited[x][y];
          auxres = laststep[(int)curpos.x][(int)curpos.y];
          myTarget = new Vector2d(y,x);
          foundTarget = true;
        }
      }
      else {
        //System.out.println("ENTRO!!");
        Properties p = Agent.obsProperties.get(type).get(o.itype);
        if (p.kill > Agent.killmin) return false;
        if (p.access > 0.8) acc = false;
        if (p.score > 0.5 && auxmaxscore < p.score/visited[x][y]) {
          auxmaxscore = p.score / visited[x][y];
          auxres = laststep[(int) curpos.x][(int) curpos.y];
          myTarget = new Vector2d(y, x);
          foundTarget = true;
        }

        if (p.uAccess == 0 && auxmaxscore < newobject_score/visited[x][y]){
          auxmaxscore = newobject_score/visited[x][y];
          auxres = laststep[(int)curpos.x][(int)curpos.y];
          myTarget = new Vector2d(y,x);
          foundTarget = true;
        }
        //Movable? Destructible?
      }
    }
    
    //System.out.println("MYSCORE: " + maxscore);
    //System.out.println("MYSTEP: " + res);
    maxscore = auxmaxscore;
    res = auxres;
    return acc;
  }

  private int findAction(int re){ //WTF como se cambia esto xD
    //System.out.println("MY SOLUTION :" + res);
    if (re < 0) return 0;
    if (re == 0){
      for (int i = 0; i < Agent.actions.size(); ++i){
        if (Agent.actions.get(i) == Types.ACTIONS.ACTION_DOWN) return i;
      }
    }
    if (re == 1){
      for (int i = 0; i < Agent.actions.size(); ++i){
        if (Agent.actions.get(i) == Types.ACTIONS.ACTION_LEFT) return i;
      }
    }
    if (re == 2){
      for (int i = 0; i < Agent.actions.size(); ++i){
        if (Agent.actions.get(i) == Types.ACTIONS.ACTION_UP) return i;
      }
    }
    if (re == 3){
      for (int i = 0; i < Agent.actions.size(); ++i){
        if (Agent.actions.get(i) == Types.ACTIONS.ACTION_RIGHT) return i;
      }
    }
    return 0;
  }

  private int findMostInterestingSprite(){
    if (Agent.sameState) return 0;
    int bestKey = 0;
    int minCount = 0;
    boolean foundSprites = false;
    for (int key : spriteCount.keySet()){
      if (!foundSprites){
        int count = spriteCount.get(key);
        if (count < 5) {
          foundSprites = true;
          bestKey = key;
          minCount = spriteCount.get(key);
        }
      }
      else{
        int count = spriteCount.get(key);
        if (count < minCount){
          minCount = count;
          bestKey = key;
        }
      }
    }
    if (foundSprites){
      //System.out.println("Most interesting sprite at: " + spriteLocation.get(bestKey).x + " " + spriteLocation.get(bestKey).y);
      int lastMove = spriteDirection.get(bestKey);
      interesting = true;
      return findAction(lastMove);
    }
    return 0;
  }

}

