package tracks.singlePlayer.imanirt;

import core.game.StateObservation;
import core.player.AbstractPlayer;
import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import tracks.singlePlayer.tools.Heuristics.SimpleStateHeuristic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Agent extends AbstractPlayer {

//    private Random randomGenerator;
//    private double min;

    public int num_actions;
    public Types.ACTIONS[] actions;

    private static int REMAINING_TIME_LIMIT = 5;
    private static int MAX_GREEDY_DEPTH = 30;
    private static int MIN_SEQUENCE_LENGTH = MAX_GREEDY_DEPTH / 2;
    private List<ACTIONS> actionSequence;
    private StateObservation finalGoodState;

    private boolean shouldGoForLongThinking = false;

    private SingleMCTSPlayer mctsPlayer;

    //    private long nanoTime;
    private long miliSec;

    private int selectedActionInLongThinking = -1;

    public Agent(StateObservation so, ElapsedCpuTimer elapsedTimer) {
        //Initialize everything
//        randomGenerator = new Random();
//        min = -Double.MAX_VALUE;
//        nanoTime = 0;
        miliSec = 0;
        actionSequence = new ArrayList<>();

        ArrayList<Types.ACTIONS> act = so.getAvailableActions();
        actions = new Types.ACTIONS[act.size()];
        for (int i = 0; i < actions.length; ++i)
            actions[i] = act.get(i);
        num_actions = actions.length;

        //Create the player:
        mctsPlayer = getPlayer(so, elapsedTimer);
        finalGoodState = so;
    }

    private SingleMCTSPlayer getPlayer(StateObservation so, ElapsedCpuTimer elapsedTimer) {
        return new SingleMCTSPlayer(new Random(), num_actions, actions);
    }

    private void print(String s) {
        System.out.println(s);
    }

    private void addActions(StateObservation so, ElapsedCpuTimer elapsedTimer) {

        miliSec = System.currentTimeMillis();

        for (int i = 0; i < MAX_GREEDY_DEPTH; i++) {

            Types.ACTIONS bestNextAction = null;
            double maxQ = Double.NEGATIVE_INFINITY;
            SimpleStateHeuristic heuristic = new SimpleStateHeuristic(finalGoodState);

            for (Types.ACTIONS action : finalGoodState.getAvailableActions()) {

                StateObservation stCopy = finalGoodState.copy();
                stCopy.advance(action);
                double Q = heuristic.evaluateState(stCopy);

                //System.out.println("Action:" + action + " score:" + Q);
                if (Q > maxQ) {
                    maxQ = Q;
                    bestNextAction = action;
                    finalGoodState = stCopy;
                }
            }
            actionSequence.add((bestNextAction));

            if (isCriticalTimeReached(elapsedTimer)) {
                print("CRITICAL TIME REACHED, RETURNING BEST ACTION IN ACTION SEQUENCE");
//                shouldGoForLongThinking = false;
                break;
            }

//            print("Action " + i + " took " + (System.currentTimeMillis() - miliSec) + " ms");
        }
        print("Actions took " + (System.currentTimeMillis() - miliSec) + " ms");
        shouldGoForLongThinking = true;
        print("================================");
    }

    private boolean isCriticalTimeReached(ElapsedCpuTimer elapsedTimer) {
        return elapsedTimer.remainingTimeMillis() < REMAINING_TIME_LIMIT;
    }

    private int getActionMCTS(ElapsedCpuTimer elapsedTimer,StateObservation so)
    {
        //Set the state observation object as the new root of the tree.
//        mctsPlayer.init(finalGoodState);
        //Determine the action using MCTS...
        //... and return it.
//        return mctsPlayer.run(elapsedTimer);
        return mctsPlayer.continueRun(elapsedTimer,so);
    }

    @Override
    public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {

        //THIS IS THE ENHANCED ONE STEP LOOK AHEAD:
        if (!shouldGoForLongThinking) {
            addActions(stateObs,elapsedTimer);
        } else {
            //We have enough actions saved to make a very good decision
            if(actionSequence.size() >= 2) {
                //Do time consuming algorithms here::
                //MCTS:
                selectedActionInLongThinking = getActionMCTS(elapsedTimer,finalGoodState);
//                print("Action in MCTS: " + action);
//                actionSequence.add(actions[action]);
//                finalGoodState.advance(actions[action]);
            }
            else {
                if(selectedActionInLongThinking != -1) {
                    actionSequence.add(actions[selectedActionInLongThinking]);
                    finalGoodState.advance(actions[selectedActionInLongThinking]);
                    selectedActionInLongThinking = -1;
                }
                shouldGoForLongThinking = false;
            }
        }
        return actionSequence.remove(0);
        //END OF ENHANCED ONE STEP LOOK AHEAD


//        nanoTime = System.currentTimeMillis();
//
//        List<ACTIONS> actions = stateObs.getAvailableActions();
//        StateObservation so = stateObs.copy();
//
//        ACTIONS finalAction = null;
//        List<ACTIONS> actionsToChooseFrom = new ArrayList<>();
////        List<ACTIONS> actionsToChooseFrom = actions;
//
//        for(int i = 0; i < actions.size(); i++)
//        {
//            so.advance(actions.get(i));
//
//            if(so.isGameOver())
//                continue;
//
//            actionsToChooseFrom.add(actions.get(i));
//
//            if(so.getGameScore() > min)
//            {
//                finalAction = actions.get(i);
//                min = so.getGameScore();
//            }
//            else {
//                int index = randomGenerator.nextInt(actionsToChooseFrom.size());
//                finalAction = actionsToChooseFrom.get(index);
//            }
//        }
//
////        for (int j = 0; j < 100000; j++)
////        {
////            //do some calcualtions
////            double f = Math.cos(j);
////        }
//
////        System.out.println("TIME= " + (System.currentTimeMillis() - nanoTime));
//
//        return finalAction;
    }
}
