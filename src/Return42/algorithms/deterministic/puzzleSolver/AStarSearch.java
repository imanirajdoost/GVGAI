package Return42.algorithms.deterministic.puzzleSolver;

import java.awt.Graphics2D;
import java.util.LinkedList;
import java.util.Queue;

import core.game.StateObservation;
import Return42.algorithms.deterministic.DeterministicAgent;
import Return42.algorithms.deterministic.puzzleSolver.AStar.Result;
import Return42.knowledgebase.KnowledgeBase;
import Return42.util.debug.DebugVisualization;
import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;

/**
 * Created by Oliver on 28.04.2015.
 */
public class AStarSearch implements DeterministicAgent {

	private final Object drawLock;
    private final Queue<ACTIONS> plan;
    private final boolean applyPartialSolution;
    private final AStar solver;

    private boolean didFinish;

    public AStarSearch(KnowledgeBase knowledge, StateObservation state, int iterations, boolean applyPartialSolution) {
    	this.applyPartialSolution = applyPartialSolution;
    	this.solver = AStarFactory.build( knowledge, state, iterations );
        this.plan = new LinkedList<>();
        this.didFinish = false;
        this.drawLock = new Object();
	}

	public void useConstructorExtraTime( StateObservation state, ElapsedCpuTimer timer ) {
		continueSimulation(timer);
    }

	private void continueSimulation( ElapsedCpuTimer timer ) {
        if (didFinish)
            return;
        
        Result result = null;
        synchronized (drawLock) {
            result = solver.simulate( timer );
		}
        
        if ( result == Result.FOUND_SOLUTION ) {
            plan.addAll(solver.getSolution());
            didFinish = true;
        } else if ( result == Result.NO_SOLUTION_FOUND) {
        	if (applyPartialSolution) {
        		plan.addAll(solver.getSolution());
        	}
        	
        	didFinish = true;
        } 
	}
	
	@Override
	public ACTIONS act(StateObservation state, ElapsedCpuTimer timer) {
        continueSimulation(timer);
		
		if (plan.isEmpty()) {
            return ACTIONS.ACTION_NIL;
        } else {
            return plan.poll();
        }
	}
	
    public boolean didFinish() {
    	return didFinish && plan.isEmpty();
    }

	public void draw(Graphics2D g) {
		/*
		synchronized (drawLock) {
			DebugVisualization.drawPath( 
					solver.getStartState().copy(),
					solver.getSolution(),
					g
			);
		}*/
	}
}
