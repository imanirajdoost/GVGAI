package YBCriber;

import core.game.Observation;
import core.game.StateObservation;
import core.player.AbstractPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import ontology.Types;
import tools.ElapsedCpuTimer;
import tools.Vector2d;

public class Agent extends AbstractPlayer {
  static ArrayList<Types.ACTIONS> actions;
  static boolean deterministic;
  static int h;
  static long hp;
  static int w;
  static long wp;
  static HashMap<Integer, HashMap<Integer, Properties>> obsProperties = new HashMap<>();
  static Random random;
  static boolean oriented;
  static Vector2d currentPos;
  static int Hashpos;
  static int[][] dangerPos;
  static boolean isDet;
  static HashSet<Integer> movObj = new HashSet<Integer> ();
  static double killmin = 0.15;
  static int simDanger = 25;
  static int simImmediateDanger = 10;
  static int simDanger2 = 8;
  static boolean won;
  static int limit;
  static double maxsc;
  static int[] simByAction;
  static boolean chasers;
  static boolean slow;
  static double chaseMin;
  static int approxSimulation, numTries, depthDanger;
  static double niceFactor = 10;
  static boolean sameState;

  static int myType;
  HashMap <Integer, Integer> myResources;

  private int sims = 2;

  private Bfs bfs;
  ArrayList<Integer> candidateActionSeq;
  ArrayList<Integer> tests;
  private int thinkingTurns;

  private StateObservation lastState;
  private int lastAction;

  static int maxTurns;
  private StateObservation stateMax = null;

  public Agent(StateObservation so, ElapsedCpuTimer timer) {
    //System.out.print(so.getAvatarSpeed());
    maxTurns = 250;
    obsProperties = new HashMap<>();
    movObj = new HashSet<>();
    candidateActionSeq = new ArrayList<>();
    // does not include ACTION_NIL
    actions = so.getAvailableActions(false);
    // ACTION_NIL will have index 0
    actions.add(0, Types.ACTIONS.ACTION_NIL);

    deterministic = isDeterministic(so);
    oriented = isOriented(so);
    isDet = true;
    won = false;
    chaseMin = 0.68;
    chasers = false;
    slow = false;
    maxsc = 0;
    approxSimulation = 0;
    numTries = 0;
    depthDanger = 0;
    sameState = false;

    ArrayList<Observation>[][] obsGrid = so.getObservationGrid();
    myResources = null;
    h = obsGrid[0].length;
    w = obsGrid.length;
    hp = (long)so.getWorldDimension().height;
    wp = (long)so.getWorldDimension().width;
    Hashpos = 3 * w + 2 * (int)niceFactor; //Hope map sizes don't change!!!
    random = new Random();

    // used for checking deterministic games
    lastState = null;
    lastAction = 0;

    // number of simulations at each step
    int[] nSimAtStep = {2, 1};
    if (deterministic) {
      nSimAtStep = new int[] {1};
    }
    this.bfs = new Bfs(nSimAtStep);

    this.candidateActionSeq = new ArrayList<>();
    this.thinkingTurns = 0;

    // initial BFS
    this.bfs.reset(so);
    this.bfs.run(timer);
    ++this.thinkingTurns;
  }

  public Types.ACTIONS act(StateObservation so, ElapsedCpuTimer timer) {
    // slow motion
    /*
    try {
      Thread.sleep(1000);
    } catch(InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    // */

    /*if (so.getGameTick() == 1){
      lastState = so.copy();
    }
    else if (Agent.deterministic && lastAction == 0 && so.getGameTick() > 1){
      checkDeterministic(so);
      lastState = so.copy();
    }*/
    
    //System.out.println("==================================");
	int limit = Math.max(250, maxTurns*3/4 - 100);
    testDet(so);
    if (deterministic) deterministic = isDet;
    if (so.getAvatarSpeed() < 1) slow = true;
    checkChasers(so);
    sims = 1;
    if (isDet || deterministic) sims = 1;
    else if (dangerous(so)){
      sims = 2; //ToDo This can be improved!!!!
      if (so.getGameTick() < 50) sims = 5;
    }

    //DANGER PART!!!!
    currentPos = getAvatarGridPosition(so);

    doDanger(so);
    //printMatrix(dangerPos);

    //getSimByAction(so);


    computeDepthOfFirstSimulation();
    ArrayList<Integer> actionDeaths = computeDeaths(so, timer);
    computeDanger(so, actionDeaths);

    if (deterministic) updateMaxTurns(so, timer);

    double candScore = this.evalActionSeq(so, this.candidateActionSeq, timer);

    if (this.thinkingTurns == 0) {
      this.bfs.reset(so);
    }
    this.bfs.run(timer);

    approxSimulation = bfs.nodesExplored();
    ++numTries;

    double bfsScore = this.bfs.getBestScore();

    int action = random.nextInt(actions.size());


    //If it is not deterministic, or have won a simple game, or > 700 turns thinking and (have found something || bfs not empty).

    if ((!deterministic || (won && maxsc <= 0) || so.getGameTick() > Math.min(700, limit)) &&
        (candScore > 0.0 || (bfsScore > 0.0 && this.bfs.mxDepth > 0) || !this.bfs.hasWork())) {

      // act

      if (bfsScore > candScore) {
        this.candidateActionSeq = this.bfs.getBestActionSeq();
      }
      if (!this.candidateActionSeq.isEmpty()) {
        action = this.candidateActionSeq.remove(0);
        //System.out.println("acting: " + actions.get(action));
      } else {
        //System.out.println("acting: random");
      }
      this.thinkingTurns = 0;
    } else {
      // keep thinking
      action = 0;
      ++this.thinkingTurns;
      //System.out.println("thinking: " + actions.get(action));
    }

    //System.out.print(getAvatarGridPositionDouble(so).x + " " + getAvatarGridPositionDouble(so).y);
    //System.out.println();

    //Let us compute the minimum number of times we die in any direction

    int minDeaths = Collections.min(actionDeaths);

    // grid BFS

    if (!deterministic && ((bfsScore <= 0 && candScore <= 0) || actionDeaths.get(action) > minDeaths)) {

      checkStatus(so);
      GridBFSDouble gridBfs = new GridBFSDouble(so);
      int gridBfsAction = gridBfs.firstStep(timer);
      if (gridBfs.interesting){
        updateStatus(so);
      }
      if (gridBfsAction > 0) {
        // stop thinking and discard candidate action sequence
        this.thinkingTurns = 0;
        this.candidateActionSeq.clear();
      }
      action = gridBfsAction;
      //System.out.println("grid BFS: " + actions.get(action));
    }

    // handle emergency case!

    //ArrayList<Double> actionScores = this.bfs.getFirstActionScores();
    if (actionDeaths.get(action) > minDeaths) {
      int emergencyAction = -1;
      for (int i = 0; i < actions.size(); ++i) {
        if (actionDeaths.get(i) == minDeaths &&
            (emergencyAction == -1)) {
          emergencyAction = i;
        }
      }
      if (emergencyAction != 0) {
        // stop thinking and discard candidate action sequence
        this.thinkingTurns = 0;
        this.candidateActionSeq.clear();
      }
      action = emergencyAction;
      //System.out.println("emergency action: " + actions.get(emergencyAction));
    }


    /*System.out.println(".............");
    for (int i = 0; i < actions.size(); ++i){
      System.out.println(actions.get(i) + "\t" + actionDeaths.get(i) + "\t" + tests.get(i));
    }
    System.out.println(".............");*/


    //lastAction = action;
    //printProperties(false);
    //printITypeMatrix(so);

    //System.out.println("action: " + action + " " +  actions.get(action));
    return actions.get(action);
  }

  //get the score difference between so and initSo
  static public double getScore(StateObservation so, StateObservation initSo) {
    double score = (so.getGameScore() - initSo.getGameScore());
    if (so.isGameOver()) {
      Types.WINNER winner = so.getGameWinner();
      // win
      if (winner == Types.WINNER.PLAYER_WINS) {
        won = true;
        if (deterministic || initSo.getGameTick() > Math.min(maxTurns/3, 700)) return score + 1e6;
        return 0.1;
      }
      // lose
      if (winner == Types.WINNER.PLAYER_LOSES) {
        return -1e6;
      }
      // no winner
      return -0.1;
    }
    else maxsc = Math.max(score, maxsc);

    Map<Integer, Integer> res = so.getAvatarResources();
    Map<Integer, Integer> initRes = initSo.getAvatarResources();


    // resources gained
    for (Map.Entry<Integer, Integer> r : res.entrySet()) {
      int amount = r.getValue();
      int initAmount = 0;
      if (initRes.containsKey(r.getKey())) {
        initAmount = initRes.get(r.getKey());
      }
      if (amount > initAmount) {
        score += 0.1 * (double)(amount - initAmount);
      }
    }

    // resources lost
    for (Map.Entry<Integer, Integer> iR : initRes.entrySet()) {
      int initAmount = iR.getValue();
      int amount = 0;
      if (res.containsKey(iR.getKey())) {
        amount = res.get(iR.getKey());
      }
      if (amount < initAmount) {
        score += 0.01 * (double)(amount - initAmount);
      }
    }

    return score;
  }

  private void getSimByAction(StateObservation so) {
    ArrayList<Observation> [][] grid = so.getObservationGrid();
    Vector2d pos = getAvatarGridPosition(so);
    simByAction = new int[actions.size()];
    for (int k = 0; k < actions.size(); ++k){
      simByAction[k] = 1;
      if (!(isDet || deterministic)) {
        //simByAction[k] = 2;
        Vector2d posNext = simNoOri(pos, actions.get(k));
        if (oriented) posNext = sim(pos, so.getAvatarOrientation(), actions.get(k));
        int margin = 0;
        if (oriented) margin = 1;
        for (int i = (int) posNext.x - margin; i <= (int) posNext.x + margin; ++i) {
          for (int j = (int) posNext.y - margin; j <= (int) posNext.y + margin; ++j) {
            if (i < 0 || j < 0 || i >= w || j >= h) continue;
            if (i != posNext.x && j != posNext.y) continue;
            if (dangerPos[i][j] > 1) {
              if (i != posNext.x || j != posNext.y) simByAction[k] = Math.max(simByAction[k], simImmediateDanger);
              else simByAction[k] = Math.max(simByAction[k], simDanger2);
            }
          }
        }
      }
    }
  }

  //Checks if a SO is dangerous or not.
  private boolean dangerous(StateObservation so){
    ArrayList<Observation> [][] grid = so.getObservationGrid();
    Vector2d pos = getAvatarGridPosition(so);
    int type = getAvatarItype(grid, pos);
    for (int i = (int)pos.x - 2; i <= (int)pos.x + 2; ++i){
      for (int j = (int)pos.y - 2; j <= (int)pos.y + 2; ++j){
        if (i < 0 || j < 0 || i >= w || j >= h) continue;
        for (Observation o : grid[i][j]){
          if (o.itype == type) continue;
          if (!obsProperties.containsKey(type)) return true;
          if (!obsProperties.get(type).containsKey(o.itype)) return true;
          Properties p = obsProperties.get(type).get(o.itype);
          if ((p.uKill < 10 || p.kill > killmin) && movObj.contains(o.itype)) return true;
        }
      }
    }
    return false;
  }


  //Returns if game is static!!
  private boolean isDeterministic(StateObservation so) {
    so = so.copy();
    for (int i = 0; i < 17; ++i) {
      so.advance(actions.get(0));
    }
    AtomSet as = new Atom1Set(new BfsNode(so));
    StateObservation futureSo = so.copy();
    for (int i = 0; i < 27; ++i) {
      futureSo.advance(actions.get(0));
      AtomSet futureAs = new Atom1Set(new BfsNode(futureSo));
      if (!as.containsAll(futureAs) || !futureAs.containsAll(as)) {
        return false;
      }
    }
    return true;
  }



  //Returns the score of a miniBFS around ActionSeq
  private double evalActionSeq(StateObservation so, ArrayList<Integer> actionSeq, ElapsedCpuTimer timer) {
    if (actionSeq.isEmpty()) return -1e9;
    StateObservation futureSo = so.copy();
    for (int a : actionSeq) {
      futureSo.advance(actions.get(a));
      if (timer.remainingTimeMillis() < 10) break;
    }
    double scoreSeq =  getScore(futureSo, so);
    if (deterministic || scoreSeq > 0 || actionSeq.size() <= 2) return scoreSeq;
    // if the sequence is short don't do the mini BFS
    // perform a mini BFS starting in the last state
    int[] nSimAtDepth = {1};
    Bfs miniBFS = new Bfs(nSimAtDepth);
    miniBFS.reset(futureSo);
    ElapsedCpuTimer miniBfsTimer = new ElapsedCpuTimer();
    miniBfsTimer.setMaxTimeMillis(timer.remainingTimeMillis() - 15);
    miniBFS.run(miniBfsTimer);
    double scoreBFS = miniBFS.getBestScore();
    ArrayList<Integer> extraActions = miniBFS.getBestActionSeq();
    if (extraActions.size() > 0) {
      this.candidateActionSeq.add(extraActions.get(0));
    }
    return scoreSeq + scoreBFS;
  }


  private void computeDanger(StateObservation so, ArrayList<Integer> actionDeaths) {
    int minDeaths = Collections.min(actionDeaths);
    //ArrayList<Double> actionScores = this.bfs.getFirstActionScores();
    for (int i = 0; i < actions.size(); ++i) {
      if (actionDeaths.get(i) > minDeaths) {
        Vector2d posdang = simNoOri(getAvatarGridPosition(so), actions.get(i)); //ToDo
        dangerPos[(int)posdang.x][(int)posdang.y] = 1;
      }
    }
  }

  private void computeDepthOfFirstSimulation(){
    int computations = approxSimulation;
    /*if (chasers && computations > 150){
      depthDanger = 2;
    }
    else depthDanger = 1;*/
    depthDanger = 1;
    //System.out.println(computations);
  }

  private ArrayList<Integer> computeDeaths(StateObservation so, ElapsedCpuTimer timer){
    ArrayList<Integer> actionDeaths = new ArrayList<>();
    //ArrayList<Vector2d> positions = new ArrayList<>();
    ArrayList<Integer> numSims = new ArrayList<>();
    tests = new ArrayList<>();
    int cont = 0;
    while (actionDeaths.size() < actions.size()) {
      actionDeaths.add(0);
      //positions.add(null);
      //numSims.add(simByAction[cont]);
      numSims.add(sims);
      ++cont;
      tests.add(0);
    }
    for (int j = 0; j < simDanger && timer.remainingTimeMillis() > 10; ++j){
      for (int i = 0; i < actions.size() && timer.remainingTimeMillis() > 10; ++i){
        if (tests.get(i) >= numSims.get(i)) continue;
        //if action i can't be the safest, stop simulating
        for (int k = 0; k < actions.size(); ++k){
          if (numSims.get(k) <= tests.get(k)){
            if (actionDeaths.get(i)*tests.get(k) > actionDeaths.get(k)*simImmediateDanger){
              numSims.set(i, tests.get(i));
            }
          }
        }

        if (tests.get(i) >= numSims.get(i)) continue;


        //
        tests.set(i, tests.get(i) + 1);
        StateObservation nextSo = so.copy();
        nextSo.advance(actions.get(i));
        analyzeStates(so, nextSo, actions.get(i));
        if (!nextSo.isGameOver()){
          Vector2d v = getAvatarGridPosition(nextSo);
          //positions.set(i, v);
          if (!outOfBounds(v)) { //FK Missilecommand
            int x = (int) v.x, y = (int)v.y;
            //System.out.println(i);
            //System.out.println(numSims.size());
            if (dangerPos[x][y] > 1){
              numSims.set(i, simImmediateDanger);
            }
            if (oriented) {
              for (int ii = -1; ii <= 1; ++ii) {
                for (int jj = -1; jj <= 1; ++jj) {
                  if (ii == 0 || jj == 0){
                    if (!outOfBounds(new Vector2d(x +ii, y+jj))){
                      if (dangerPos[x + ii][y + jj] > 1) numSims.set(i, Math.max(simDanger2, numSims.get(i)));
                    }
                  }
                }
              }
            }
          }
        }


        boolean exhaustiveCheck = false;
        if (!deterministic && !reallyBadState(nextSo) && j == 0 && (slow || chasers)){
          if (chasers){
            if (alwaysDie(nextSo, depthDanger, timer)) actionDeaths.set(i, actionDeaths.get(i) + 1);
          }
          else if (alwaysDie(nextSo, depthDanger, timer)) actionDeaths.set(i, actionDeaths.get(i) + 1);
          exhaustiveCheck = true;
        }



        if (reallyBadState(nextSo)){
          actionDeaths.set(i, actionDeaths.get(i) + 1);
        } else if (!exhaustiveCheck && !nextSo.isGameOver() && (oriented)) {
          StateObservation nextSo2 = nextSo.copy();
          nextSo.advance(actions.get(i));
          analyzeStates(nextSo2, nextSo, actions.get(i));
          if (reallyBadState(nextSo)) {
            nextSo2.advance(actions.get(0));
            if (reallyBadState(nextSo2)){
              actionDeaths.set(i, actionDeaths.get(i) + 1);
            }
          }
        }
      }
    }

    int mcm = 1;

    for (int i = 0; i < actions.size(); ++i){
      mcm *= tests.get(i);
    }

    for (int i = 0; i < actions.size(); ++i){
      if (tests.get(i) > 0) actionDeaths.set(i, actionDeaths.get(i)*(mcm/tests.get(i)));
    }

    /*for (int i = 0; i < actions.size(); ++i){
      if (numSims.get(i) == simDanger && tests.get(i) > 0) actionDeaths.set(i, (actionDeaths.get(i)*sims + tests.get(i) - 1)/tests.get(i));
    }*/

    /*Vector2d pos = getAvatarGridPosition(so);
    for (int i = 0; i < actions.size(); ++i){
      Vector2d pos2 = positions.get(i);
      if (pos2 == null) pos2 = sim(pos, so.getAvatarOrientation(), actions.get(i));
      if (dangerPos[(int)pos2.x][(int)pos2.y] == 1) {
        actionDeaths.set(i, actionDeaths.get(i)+1);
      }
    }*/
    return actionDeaths;
  }

  private boolean noAdvance(StateObservation so, int k, ArrayList<Observation>[][] grid, int type){
    Vector2d pos = getAvatarGridPositionDouble(so);
    Vector2d newPos = simDouble(pos,  so.getAvatarOrientation(), actions.get(k), so.getAvatarSpeed());
    //if (oriented) newPos = sim(pos, so.getAvatarOrientation(), actions.get(k));
    if (outOfBounds(newPos)) return true;
    HashSet<Integer> H = positionsOccupied(newPos);
    for (int h : H) {
      Vector2d v = Properties.decode(h);
      for (Observation obs : grid[(int) v.x][(int) v.y]) {
        if (obs.itype == type) continue;
        if (obsProperties.containsKey(type)) {
          if (obsProperties.get(type).containsKey(obs.itype)) {
            Properties p = obsProperties.get(type).get(obs.itype);
            if (p.access > 0.8 && p.uAccess > 20 && p.destroyed < 0.1) return true;
            if (p.kill > killmin || p.uKill < 4) return true;
          }
        }
      }
    }
    return false;
  }

  private boolean alwaysDie(StateObservation st, int a, ElapsedCpuTimer elapsedCpuTimer){
    if (a == 0) return reallyBadState(st);
    StateObservation newSt;
    ArrayList<Observation>[][] grid = st.getObservationGrid();
    Vector2d pos = getAvatarGridPosition(st);
    int type = getAvatarItype(grid, pos);
    int count = 0;
    for (int i = actions.size() - 1; i >= 0 && elapsedCpuTimer.remainingTimeMillis() > 10; --i){
      if (noAdvance(st, i, grid, type) && i > 0){
        ++count;
        continue;
      }
      newSt = st.copy();
      newSt.advance(actions.get(i));
      if (!alwaysDie(newSt, a-1, elapsedCpuTimer)) return false;
      ++count;
    }
    if (count == actions.size()) return true;
    return false;
  }




  void doDanger(StateObservation so){
    ArrayList<Observation>[][] grid = so.getObservationGrid();
    int blocksize = so.getBlockSize();
    int type = getAvatarItype(grid, getAvatarGridPosition(so));
    dangerPos = new int[grid.length][grid[0].length];
    if (!obsProperties.containsKey(type)) return;
    int block = so.getBlockSize();
    for (int x = 0; x < grid.length; ++x){
      for (int y = 0; y < grid[0].length; ++y){
        for (Observation o : grid[x][y]){
          if (o.itype == type) continue;
          if (!obsProperties.get(type).containsKey(o.itype)){
            //dangerPos[x][y] = Math.max(dangerPos[x][y], 2);
            continue;
          }
          Properties p = obsProperties.get(type).get(o.itype);
          if (p.kill < killmin) continue;
          //Vector2d pos = getObservationPositionGrid(o.position, block);
          Vector2d pos = getObservationPositionGridDouble(o.position, blocksize);
          if (outOfBounds(pos)) continue;
          boolean movesRandomly = movObj.contains(o.itype);
          if (movesRandomly) dangerPos[x][y] = Math.max(dangerPos[x][y], 2);
          else dangerPos[x][y] = Math.max(dangerPos[x][y], 1);
          if (distance1(pos, currentPos) > 3){
            continue;
          }
          for (Vector2d v : p.getMovements()){

            //Vector2d v = Properties.decode(t);
            v.x += pos.x;
            v.y += pos.y;

            v.x = Math.max(v.x, 0.0);
            v.x = Math.min(grid.length-1, v.x);
            v.y = Math.max(v.y, 0.0);
            v.y = Math.min(grid[0].length-1, v.y);

            HashSet<Integer> S = positionsOccupied(v);

            for (int u : S) {
              Vector2d w = Properties.decode(u);
              if (movesRandomly) dangerPos[(int) w.x][(int) w.y] = Math.max(dangerPos[(int) w.x][(int) w.y], 2);
              else dangerPos[(int) w.x][(int) w.y] = Math.max(dangerPos[(int) w.x][(int) w.y], 1);

            }
          }
          /*for (int t1 : p.M.keySet()){
            for (int t2 : p.M.keySet()){
              Vector2d v1 = Properties.decode(t1);
              Vector2d v2 = Properties.decode(t2);
              v1.x += v2.x + pos.x;
              v1.y += v2.y + pos.y;
              v1.x = Math.max(v1.x, 0.0);
              v1.x = Math.min(grid.length-1, v1.x);
              v1.y = Math.max(v1.y, 0.0);
              v1.y = Math.min(grid[0].length-1, v1.y);
              if (dangerPos[(int)v1.x][(int)v1.y] == 0) dangerPos[(int)v1.x][(int)v1.y] = 2;
            }
          }*/
        }
      }
    }
  }

  //test if game is not-randomized (deterministic)
  private void testDet(StateObservation so){
    int n = actions.size();
    int i = random.nextInt(n);
    StateObservation so1 = so.copy();
    StateObservation so2 = so.copy();
    so1.advance(actions.get(i));
    so2.advance(actions.get(i));
    HashMap<Integer, Vector2d> M1 = new HashMap<Integer, Vector2d> ();
    int block = so.getBlockSize();
    ArrayList<Observation> [][] grid = so1.getObservationGrid();
    for (int x = 0; x < grid.length; ++x){
      for (int y = 0; y < grid[0].length; ++y){
        for (Observation o : grid[x][y]){
          M1.put(o.obsID, getObservationPositionGrid(o.position, block));
        }
      }
    }
    grid = so2.getObservationGrid();
    for (int x = 0; x < grid.length; ++x){
      for (int y = 0; y < grid[0].length; ++y){
        for (Observation o : grid[x][y]){
          if (!M1.containsKey(o.obsID)) isDet = false;
          else{
            if (!M1.get(o.obsID).equals(getObservationPositionGrid(o.position, block))){
              isDet = false;
              movObj.add(o.itype);
            }
          }
        }
      }
    }
  }

  private boolean isOriented(StateObservation so) {
    Vector2d ori = so.getAvatarOrientation();
    if (ori.x == 0 && ori.y == 0) return false;

    StateObservation soc = so.copy();
    Vector2d posini = so.getAvatarPosition();
    int cont = 0;
    for (int i = 0; i < actions.size(); ++i) {
      so.advance(actions.get(i));
      Vector2d posend = so.getAvatarPosition();
      if (posini.x != posend.x || posini.y != posend.y) ++cont;
      posini = posend;
    }
    posini = soc.getAvatarPosition();
    for (int i = actions.size() - 1; i >= 0; --i) {
      soc.advance(actions.get(i));
      Vector2d posend = soc.getAvatarPosition();
      if (posini.x != posend.x || posini.y != posend.y) ++cont;
      posini = posend;
    }
    if (cont >= 2) return false;
    return true;
  }

  static boolean reallyBadState(StateObservation so) {
    if (!so.isGameOver()) return false;
    return so.getGameWinner() != Types.WINNER.PLAYER_WINS;
  }

  static boolean reallyGoodState(StateObservation so) {
    if (!so.isGameOver()) return false;
    return so.getGameWinner() == Types.WINNER.PLAYER_WINS;
  }

  static boolean outOfBounds(Vector2d pos) {
    if (pos.x < 0 || pos.x >= w) return true;
    if (pos.y < 0 || pos.y >= h) return true;
    return false;
  }

  //De momento no hay resources!!
  static void analyzeStates(StateObservation prev, StateObservation next, Types.ACTIONS act) {
    Vector2d posPrev = getAvatarGridPosition(prev);
    Vector2d posPrevDouble = getAvatarGridPositionDouble(prev);
    Vector2d posNext = getAvatarGridPosition(next);
    Vector2d posNextDouble = getAvatarGridPositionDouble(next);
    if (outOfBounds(posPrev) || outOfBounds(posNext)) return;
    ArrayList<Observation>[][] objNext = next.getObservationGrid();
    ArrayList<Observation>[][] objPrev = prev.getObservationGrid();
    int typePrev = getAvatarItype(objPrev, posPrev);
    if (!obsProperties.containsKey(typePrev)) {
      // HashMap type --> Properties
      obsProperties.put(typePrev, new HashMap<Integer, Properties>());
    }
    int typeNext = getAvatarItype(objNext, posNext);
    boolean gameOver = next.isGameOver();
    if (prev.isGameOver()) return;
    //if (typeNext != typePrev && !gameOver) return;

    HashSet<Integer> prevPositions = positionsOccupied(posPrevDouble);
    HashSet<Integer> nextPositions = positionsOccupied(posNextDouble);

    //If you lose set kill = 1 and not destroyable.

    //Vector2d posNextIntended = sim(posPrev, prev.getAvatarOrientation(), act);

    Vector2d posNextIntendedDouble = simDouble(posPrevDouble, prev.getAvatarOrientation(), act, prev.getAvatarSpeed());
    HashSet<Integer> nextIntendedPositions = positionsOccupied(posNextIntendedDouble);


    if (posPrevDouble.x == posNextIntendedDouble.x && posPrevDouble.y == posNextIntendedDouble.y){
      if (reallyBadState(next)){
        for (int pp : prevPositions) {
          Vector2d pvec = Properties.decode(pp);
          for (Observation o : objNext[(int) pvec.x][(int) pvec.y]) {
            refresh(typePrev, null, null, 1, null, null, 0, o.itype, null, null);
          }
        }
      }
      return;
    }

    HashSet<Integer> objNextPosIntended = new HashSet<>();
    HashSet<Integer> pushedObj = new HashSet<Integer>();
    HashSet<Integer> objPrevPos = new HashSet<Integer>();
    HashSet<Integer> missingIDs = findMissing(prev, next, typePrev);
    HashSet<Integer> objPrevPosRe = new HashSet<>();

    ArrayList<Observation> objPrevPosIntended = new ArrayList<Observation>();
    ArrayList<Observation> objNextPosInt = new ArrayList<Observation>();
    ArrayList<Observation> objPrevPosReal = new ArrayList<Observation> ();
    ArrayList<Observation> objNextPosPrev = new ArrayList<Observation> ();

    for (int pp : nextIntendedPositions){
      Vector2d pvec = Properties.decode(pp);
      for (Observation o : objPrev[(int)pvec.x][(int)pvec.y]) objPrevPosIntended.add(o);
      for (Observation o : objNext[(int)pvec.x][(int)pvec.y]) objNextPosInt.add(o);
    }

    for (int pp : prevPositions){
      Vector2d pvec = Properties.decode(pp);
      for (Observation o : objPrev[(int)pvec.x][(int)pvec.y]){
        objPrevPosReal.add(o);
        objPrevPosRe.add(o.obsID);
      }
      for (Observation o : objNext[(int)pvec.x][(int)pvec.y]) objNextPosPrev.add(o);
    }



    boolean still = (posNextDouble.x == posPrevDouble.x && posNextDouble.y == posPrevDouble.y);
    for (int pp : nextIntendedPositions){
      Vector2d pvec = Properties.decode(pp);
      for (Observation o : objNext[(int)pvec.x][(int)pvec.y]) objNextPosIntended.add(o.obsID);
      for (Observation o : objPrev[(int)pvec.x][(int)pvec.y]) objPrevPos.add(o.obsID);
    }

    double deltaScore = next.getGameScore() - prev.getGameScore();


    //if you die while moving

    if (reallyBadState(next)){

      for (Observation o : objNextPosPrev){
        if (objPrevPos.contains(o.obsID)) {
          refresh(typePrev, null, null, 1, null, null, 0, o.itype, null, null); //Do we need this???
        }
      }
      for (Observation o : objNextPosInt) {
        refresh(typePrev, null, deltaScore, 1, null, null, 0, o.itype, null, null);
      }
    }

    for (Observation o : objPrevPosIntended){
      if (objPrevPosRe.contains(o.obsID)) continue;
      Integer access = null;
      Integer destroyed = null;
      Integer kill = null;
      Integer movable = null;
      Integer resources = null;
      //int r = getResourcesGained(prev, next);
      Double score = deltaScore;
      if (reallyGoodState(next)) score += 1;
      if (missingIDs.contains(o.obsID)) destroyed = 1;
      else destroyed = 0;
      // check access
      if (objNextPosIntended.contains(o.obsID) && !gameOver){
        // If it was there before and after
        if (still) access = 1;
        else access = 0;
      }
      // check kill
      if (objNextPosIntended.contains(o.obsID) || missingIDs.contains(o.obsID)){ // the missing thing is because sometimes both disappear
        if (reallyBadState(next)) kill = 1;
        else kill = 0;
      }
      // detroyed checked in find missing
      // check score and resources (everything that is missing has this deltaScore and resources)
      if (missingIDs.contains(o.obsID)){
        movable = 0;
        //score = deltaScore;

        //resources = r;
        if (access == null) access = 0; // If it was there, and I moved and it disappeared --> Accessible
        if (kill == null) kill = 0; // If it was there, and I moved and it disappeared --> Not kill
      } else {
        if (!objNextPosIntended.contains(o.obsID)) movable = 1;
        else movable = 0;
      }
      if (movable == 1) refresh(typePrev, null, null, null, 1, null, null, o.itype, null, null);
      else refresh(typePrev, access, score, kill, movable, resources, destroyed, o.itype, null, null);
    }
  }

  static void refresh(int avatarType, Integer access, Double deltaScore, Integer kill, Integer movable, Integer resources, Integer destroyed, Integer obsType, Integer spawned, Integer chasing){
    if (!obsProperties.get(avatarType).keySet().contains(obsType)){
      obsProperties.get(avatarType).put(obsType, new Properties(access, deltaScore, kill, movable, resources, destroyed, spawned, chasing));
    } else {
      obsProperties.get(avatarType).get(obsType).update(access, deltaScore, kill, movable, resources, destroyed, spawned, chasing);
    }
  }

  static Vector2d getPosDestPushable(Vector2d posPrev, Vector2d posNext){
    double x, y;
    x = posNext.x + (posNext.x - posPrev.x);
    y = posNext.y + (posNext.y - posPrev.y);

    x = Math.max(0, x);
    x = Math.min(w-1, x);
    y = Math.max(0, y);
    y = Math.min(h-1, y);
    return new Vector2d(x, y);
  }

  static int getResourcesGained(StateObservation prev, StateObservation next){
    int rPrev = getNumberResources(prev);
    int rNext = getNumberResources(next);
    return rNext - rPrev;
  }

  static int getNumberResources(StateObservation so){
    HashMap<Integer, Integer> res = so.getAvatarResources();
    int total = 0;
    for (int key : res.keySet()) total += res.get(key);
    return total;
  }

  static HashSet<Integer> findMissing(StateObservation prev, StateObservation next, int type) {
    ArrayList<Observation>[][] objPrev = prev.getObservationGrid();
    ArrayList<Observation>[][] objNext = next.getObservationGrid();
    int blockSize = prev.getBlockSize();
    HashMap<Integer, Vector2d> IDnext = new HashMap<Integer, Vector2d>(); //objects checked with position in objNext
    HashSet<Integer> res = new HashSet<>(); // objects in objPrev not in objNext
    HashSet<Integer> checkedIDs = new HashSet<>(); //checked IDs in objPrev

    for (int x = 0; x < objNext.length; ++x) {
      for (int y = 0; y < objNext[x].length; ++y) {
        for (Observation o : objNext[x][y]){
          if (!IDnext.containsKey(o.obsID)){
            Vector2d pos = getObservationPositionGridDouble(o.position, blockSize);
            IDnext.put(o.obsID, pos);
          }
        }
      }
    }
    for (int x = 0; x < objPrev.length; ++x) {
      for (int y = 0; y < objPrev[x].length; ++y) {
        for (Observation o : objPrev[x][y]) {
          if (!checkedIDs.contains(o.obsID)){
            checkedIDs.add(o.obsID);
            if (!IDnext.containsKey(o.obsID)) {
              res.add(o.obsID);
              if (!obsProperties.get(type).containsKey(o.itype)) { //Destroyed stuff
                obsProperties.get(type).put(o.itype, new Properties(null, null, null, null, null, 1, null, null));
              } else {
                obsProperties.get(type).get(o.itype).update(null, null, null, null, null, 1, null, null);
              }
            }  else { //Position changed
              //ToDo speed < 1
              Vector2d pos = getObservationPositionGridDouble(o.position, blockSize);
              Vector2d newPos = IDnext.get(o.obsID);
              Vector2d changePos = new Vector2d(newPos.x - pos.x, newPos.y - pos.y);
              if (!obsProperties.get(type).containsKey(o.itype)) {
                obsProperties.get(type).put(o.itype, new Properties(changePos));
              } else {
                obsProperties.get(type).get(o.itype).updateM(changePos);
              }

              Vector2d myPos = getAvatarGridPositionDouble(prev);

              if (pos.x != newPos.x || pos.y != newPos.y){
                double dIni = distance1(myPos, pos), dEnd = distance1(myPos, newPos);
                if (dIni > dEnd) refresh(type, null, null, null, null, null, null, o.itype, null, 1);
                if (dEnd > dIni) refresh(type, null, null, null, null, null, null, o.itype, null, 0);
              }


            }
          }
        }
      }
    }
    for (int x = 0; x < objNext.length; ++x) {
      for (int y = 0; y < objNext[x].length; ++y) {
        for (Observation o : objNext[x][y]){
          if(!checkedIDs.contains(o.obsID)) refresh(type, null, null, null, null, null, null, o.itype, 1, null);
        }
      }
    }
    return res;
  }

  static Vector2d getObservationPositionGrid(Vector2d posPix, int blockSize){
    Vector2d posGrid = new Vector2d();
    posGrid.x = (int)((posPix.x + blockSize/2) / blockSize);
    posGrid.y = (int)((posPix.y + blockSize/2) / blockSize);
    return posGrid;
  }

  static Vector2d getObservationPositionGridDouble(Vector2d posPix, int blockSize){
    Vector2d posGrid = new Vector2d();
    posGrid.x = posPix.x / blockSize;
    posGrid.y = posPix.y / blockSize;
    return posGrid;
  }

  static Vector2d sim(Vector2d pos, Vector2d ori, Types.ACTIONS act) {
    Vector2d posInt = pos.copy();
    int[] xv = {0, -1, 0, 1, 0};
    int[] yv = {1, 0, -1, 0, 0};
    int i = -1;
    if (act.toString().toLowerCase().contains("down")) i = 0;
    else if (act.toString().toLowerCase().contains("left")) i = 1;
    else if (act.toString().toLowerCase().contains("up")) i = 2;
    else if (act.toString().toLowerCase().contains("right")) i = 3;
    else i = 4;
    if (!oriented) {
      posInt.x += xv[i];
      posInt.y += yv[i];
    } else {
      posInt.x += (xv[i] + (int)ori.x)/2;
      posInt.y += (yv[i] + (int)ori.y)/2;
    }
    posInt.x = Math.max(0, posInt.x);
    posInt.x = Math.min(w-1, posInt.x);
    posInt.y = Math.max(0, posInt.y);
    posInt.y = Math.min(h-1, posInt.y);
    return posInt;
  }

  static HashSet<Integer> positionsOccupied (Vector2d pos){
    double[] xv = {0, 0.95, 0.95, 0};
    double[] yv = {0.95, 0, 0.95, 0};
    HashSet<Integer> res = new HashSet<>();
    for (int i = 0; i < 4; ++i){
      Vector2d newPos = pos.copy();
      newPos.x += xv[i];
      newPos.y += yv[i];

      newPos = new Vector2d ((int) newPos.x, (int) newPos.y);

      if (!outOfBounds(newPos))
      res.add(Properties.encode(newPos));
    }
    return res;
  }

  static Vector2d simDouble(Vector2d pos, Vector2d ori, Types.ACTIONS act, double speed) {
    Vector2d posInt = new Vector2d (0,0);
    int[] xv = {0, -1, 0, 1, 0};
    int[] yv = {1, 0, -1, 0, 0};
    int i = -1;
    if (act.toString().toLowerCase().contains("down")) i = 0;
    else if (act.toString().toLowerCase().contains("left")) i = 1;
    else if (act.toString().toLowerCase().contains("up")) i = 2;
    else if (act.toString().toLowerCase().contains("right")) i = 3;
    else i = 4;
    if (!oriented) {
      posInt.x += xv[i];
      posInt.y += yv[i];
    } else {
      posInt.x += (xv[i] + (int)ori.x)/2;
      posInt.y += (yv[i] + (int)ori.y)/2;
    }
    posInt.x *= speed;
    posInt.y *= speed;

    //System.out.println(posInt.x + " " + posInt.y);

    posInt.x += pos.x;
    posInt.y += pos.y;

    posInt.x = Math.max(0, posInt.x);
    posInt.x = Math.min(w-1, posInt.x);
    posInt.y = Math.max(0, posInt.y);
    posInt.y = Math.min(h-1, posInt.y);
    return posInt;
  }

  static Vector2d simNoOri(Vector2d pos, Types.ACTIONS act) {
    Vector2d posInt = pos.copy();
    int[] xv = {0, -1, 0, 1, 0};
    int[] yv = {1, 0, -1, 0, 0};
    int i = -1;
    if (act.toString().toLowerCase().contains("down")) i = 0;
    else if (act.toString().toLowerCase().contains("left")) i = 1;
    else if (act.toString().toLowerCase().contains("up")) i = 2;
    else if (act.toString().toLowerCase().contains("right")) i = 3;
    else i = 4;
    posInt.x += xv[i];
    posInt.y += yv[i];
    posInt.x = Math.max(0, posInt.x);
    posInt.x = Math.min(w-1, posInt.x);
    posInt.y = Math.max(0, posInt.y);
    posInt.y = Math.min(h-1, posInt.y);
    return posInt;
  }

  static int getAvatarItype(ArrayList<Observation>[][] grid, Vector2d pos) {
    int posX = (int) pos.x;
    int posY = (int) pos.y;

    if (posX < 0 || posY < 0 || posX >= grid.length || posY >= grid[0].length) return 0;
    ArrayList<Observation> obsPosAvatar = grid[posX][posY];
    Observation auxObs;
    int avatarCat = 0;

    for (int k = 0; k < obsPosAvatar.size(); k++) {
      auxObs = obsPosAvatar.get(k);
      if (auxObs.category == avatarCat) return auxObs.itype;
    }
    return 0;
  }

  static Vector2d getAvatarGridPosition(StateObservation so) {
    Vector2d v = new Vector2d();
    int factor = so.getBlockSize();
    v.x = ((int)so.getAvatarPosition().x) / factor;
    v.y = ((int)so.getAvatarPosition().y) / factor;
    return v;
  }

  static Vector2d getAvatarGridPositionDouble(StateObservation so) {
    Vector2d v = new Vector2d();
    int factor = so.getBlockSize();
    v.x = (so.getAvatarPosition().x) / factor;
    v.y = (so.getAvatarPosition().y) / factor;
    return v;
  }

  private void printProperties(boolean b) {
    for (int key1 : obsProperties.keySet()){
      System.out.println("-------------------------------------");
      System.out.println(key1);
      System.out.println("Key\t\tValue\t\tNumber updated");
      for (int key : obsProperties.get(key1).keySet()) {
        System.out.println("______________");
        System.out.println(key);
        Properties p = obsProperties.get(key1).get(key);
        System.out.printf("Sco:\t%.3f\t\t%d%n", p.score, p.uScore);
        System.out.printf("Kil:\t%.3f\t\t%d%n", p.kill, p.uKill);
        System.out.printf("Des:\t%.3f\t\t%d%n", p.destroyed, p.uDestroyed);
        System.out.printf("Acc:\t%.3f\t\t%d%n", p.access, p.uAccess);
        System.out.printf("Res:\t%.3f\t\t%d%n", p.resources, p.uResources);
        System.out.printf("Mov:\t%.3f\t\t%d%n", p.movable, p.uMovable);
        System.out.println(p.chasing);
        System.out.println(movObj.contains(key));
        System.out.println(p.spawn);
        if (!b) continue;
        System.out.println("Directions:");

        for (Vector2d v : p.getMovements()){
          System.out.println(v.x + " " + v.y);
        }

      }
    }
  }

  private void checkDeterministic(StateObservation currState){
    Agent.deterministic = statesAreEqual(currState, lastState);
  }

  private boolean statesAreEqual(StateObservation currState, StateObservation lastState){
    Set<Integer> currObj = new HashSet<Integer>();
    ArrayList<Observation>[][] currGrid = currState.getObservationGrid();
    ArrayList<Observation>[][] lastGrid = lastState.getObservationGrid();
    ArrayList<Observation> currCell;
    ArrayList<Observation> lastCell;

    for (int i = 0; i < currGrid.length; ++i){
      for (int j = 0; j < currGrid[i].length; ++j){
        lastCell = lastGrid[i][j];
        currCell = currGrid[i][j];
        if (lastCell.size() != currCell.size()){
          return false;
        }
        for (int k = 0; k < currCell.size(); ++k) currObj.add(currCell.get(k).itype);
        for (int k2 = 0; k2 < lastCell.size(); ++k2) if (!currObj.contains(lastCell.get(k2).itype)) return false;
        currObj.clear();
      }
    }
    return true;
  }

  public void updateMaxTurns(StateObservation so, ElapsedCpuTimer timer){
    if (stateMax == null) stateMax = so.copy();
    for (int i = 0; i < 35 && timer.remainingTimeMillis() > 25; ++i){
      stateMax.advance(actions.get(0));
      if (!stateMax.isGameOver()) maxTurns = Math.max(stateMax.getGameTick(), maxTurns);
      else return;
    }
  }

  private void checkChasers(StateObservation so){
    ArrayList<Observation>[][] obj = so.getObservationGrid();
    Vector2d myPos = getAvatarGridPosition(so);
    int type = getAvatarItype(obj, myPos);
    for (int i = 0; i < obj.length; ++i) {
      for (int j = 0; j < obj[i].length; ++j) {
        for (Observation o : obj[i][j]){
          if (obsProperties.containsKey(type)){
            if (obsProperties.get(type).containsKey(o.itype)){
              Properties p = obsProperties.get(type).get(o.itype);
              if (p.uChasing > 20 && p.chasing > chaseMin && p.kill > killmin && p.uKill > 10) chasers = true;
              return;
            }
          }
        }
      }
    }
    chasers = false;
  }

  static double distance1 (Vector2d v, Vector2d w) {
    return Math.abs(v.x - w.x) + Math.abs(v.y - w.y);
  }

  static void printMatrix(int[][] M) {
    System.out.println("====================================");
    for (int i = 0; i < M[0].length; ++i){
      for (int j = 0; j < M.length; ++j){
        if (i == currentPos.y && j == currentPos.x) System.out.print(M[j][i] + 5);
        else System.out.print(M[j][i]);
      }
      System.out.println();
    }
    System.out.println("====================================");
  }

  private void printITypeMatrix(StateObservation so) {
    ArrayList<Observation>[][] obj = so.getObservationGrid();
    int[][] iTypeMatrix = new int[obj.length][obj[0].length];
    for (int i = 0; i < obj.length; ++i){
      for (int j= 0; j < obj[0].length; ++j){
        iTypeMatrix[i][j] = 8;
        for (Observation obs : obj[i][j]){
          iTypeMatrix[i][j] = obs.itype;
        }
      }
    }
    printMatrix (iTypeMatrix);
  }

  void updateStatus(StateObservation so){
    ArrayList<Observation> obs [][] = so.getObservationGrid();
    Vector2d pos = getAvatarGridPosition(so);
    myType = getAvatarItype(obs, pos);
    myResources = so.getAvatarResources();
    sameState = true;

  }

  void checkStatus(StateObservation so){
    if (!sameState) return;
    ArrayList<Observation> obs [][] = so.getObservationGrid();
    Vector2d pos = getAvatarGridPosition(so);
    int type = getAvatarItype(obs, pos);
    if (myType != type) sameState = false;
    if (myResources == null) sameState = false;
    HashMap<Integer, Integer> resources = so.getAvatarResources();
    for (int x : resources.keySet()){
      if (!myResources.containsKey(x)){
        sameState = false;
        return;
      }
      if (myResources.get(x) != resources.get(x)){
        sameState = false;
        return;
      }
    }
  }

}

