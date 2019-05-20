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

public class GridBFSDouble {
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
  HashSet<Integer> spriteIDs;
  public boolean interesting;
  private int w,h;

  double x0, y0, speed;

  //int[][] BFSvisited;

  void computeLengths(StateObservation so){
    Vector2d pos = Agent.getObservationPositionGridDouble(so.getAvatarPosition(), so.getBlockSize());
    speed = so.getAvatarSpeed();
    double t = (int) (pos.x/speed);
    x0 = pos.x - t*speed;
    t = (int) (pos.y/speed);
    y0 = pos.y - t*speed;

    w = 1 + (int) (((double)grid.length - 1.0)/speed);
    h = 1 + (int) (((double)grid[0].length - 1.0)/speed);

  }

  GridBFSDouble (StateObservation so){
    this.grid = so.getObservationGrid();
    computeLengths(so);
    this.visited = new int [w][h];
    this.laststep = new int [w][h];
    //this.BFSvisited = new int [w][h];
    this.maxscore = 0;
    this.res = -1;

    this.posini = Agent.getAvatarGridPositionDouble(so);

    this.type = Agent.getAvatarItype(grid, posini);

    posini.x = Math.round((posini.x - x0)/speed);
    posini.y = Math.round((posini.y - y0)/speed);

    foundTarget = false;
    spriteDirection = new HashMap<>();
    spriteCount = new HashMap<>();
    spriteLocation = new HashMap<>();
    spriteIDs = new HashSet<>();
    interesting = false;
  }

  public int firstStep(ElapsedCpuTimer timer){
    System.out.println(w + " " + h + " " + Agent.sameState);
    Queue <Vector2d> Q = new LinkedList <Vector2d> ();
    if (Agent.actions.size() < 5) return 0;
    //System.out.println(w + " " + h);
    if (posini.x < 0 || posini.y < 0 || posini.x >= visited.length || posini.y >= visited[(int)posini.x].length) return 0;
    //System.out.println(w + " " + h);
    visited[(int)posini.x][(int)posini.y] = 1;
    laststep[(int)posini.x][(int)posini.y] = -1;
    Q.add(posini);


    while (!(Q.size() == 0) && timer.remainingTimeMillis() > 2){
      Vector2d curpos = Q.remove();
      for (int i = 0; i < 4; ++i){
        //x = (int) curpos.x; y = (int) curpos.y;
        //if (Math.abs(x - curpos.x) > 0.1) {

        //}
        if (fill(curpos, i)) Q.add(new Vector2d(curpos.x + xv[i], curpos.y + yv[i]));
        //BFSvisited[(int) curpos.x][(int)curpos.y] = 1;
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

  private boolean isDangerous(int x, int y){
    Vector2d w = new Vector2d(x0 + speed*x, y0 + speed*y);
    HashSet<Integer> H = Agent.positionsOccupied(w);
    for (int h : H) {
      Vector2d v = Properties.decode(h);
      if (Agent.dangerPos[(int)v.x][(int)v.y]> 0) return true;
    }
    return false;
  }

  private HashSet<Integer> objectsIn(int x, int y){
    Vector2d w = new Vector2d(x0 + speed*x, y0 + speed*y);
    HashSet<Integer> H = Agent.positionsOccupied(w);
    HashSet<Integer> aux = new HashSet<> ();
    for (int h : H){
      Vector2d v = Properties.decode(h);
      for (Observation o : grid[(int)v.x][(int)v.y]) aux.add(o.obsID*100 + o.itype);
    }
    return aux;
  }

  private void updateMaps(Vector2d curpos, int x, int y, int otype, int oID) {
    if (!spriteCount.containsKey(otype)){
      spriteDirection.put(otype, laststep[(int)curpos.x][(int)curpos.y]);
      spriteCount.put(otype, 1);
      spriteLocation.put(otype, new Vector2d(x,y));
      spriteIDs.add(oID);
    }
    else if (!spriteIDs.contains(oID)){
      int a = spriteCount.get(otype);
      spriteCount.remove(otype);
      spriteCount.put(otype, a+1);
    }
  }

  private boolean fill (Vector2d curpos, int i){
    boolean acc = true;
    int x = (int)curpos.x + xv[i];
    int y = (int)curpos.y + yv[i];
    double auxmaxscore = maxscore;
    int auxres = res;
    if (x < 0 || y < 0 || x >= visited.length || y >= visited[0].length) return false;
    //BFSvisited[x][y] = 1;
    if (visited[x][y] > 0) return false;
    visited[x][y] = visited[(int)curpos.x][(int)curpos.y] + 1;
    if (visited[x][y] == 2) laststep[x][y] = i;
    else laststep[x][y] = laststep[(int)curpos.x][(int)curpos.y];
    if (isDangerous(x,y)) return false;
    for (int o : objectsIn(x,y)){
      int oID = o/100;
      int otype = o%100;
      if (otype == type) continue;
      if (!Agent.obsProperties.containsKey(type)) return false;
      if (!Agent.obsProperties.get(type).containsKey(otype)){
        if (auxmaxscore < newobject_score/visited[x][y]){
          auxmaxscore = newobject_score/visited[x][y];
          auxres = laststep[(int)curpos.x][(int)curpos.y];
          myTarget = new Vector2d(y,x);
          foundTarget = true;
        }
        updateMaps(curpos, x, y, otype, oID);
      }
      else {
        //System.out.println("ENTRO!!");
        Properties p = Agent.obsProperties.get(type).get(otype);
        if (p.kill > Agent.killmin) return false;
        if (p.access > 0.8) acc = false;
        else updateMaps(curpos, x, y, otype, oID);
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
        if (count < 2) {
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
      int lastMove = spriteDirection.get(bestKey);
      //System.out.println("Most interesting sprite at: " + spriteLocation.get(bestKey).x + " " + spriteLocation.get(bestKey).y + "     " + lastMove);
      if (lastMove <= 0) interesting = true;
      return findAction(lastMove);
    }
    return 0;
  }

}

