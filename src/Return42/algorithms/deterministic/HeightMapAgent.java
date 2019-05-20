package Return42.algorithms.deterministic;

import java.awt.Graphics2D;

import ontology.Types;
import ontology.Types.ACTIONS;
import tools.ElapsedCpuTimer;
import Return42.algorithms.KnowledgebasedAgent;
import Return42.algorithms.deterministic.randomSearch.RandomSearch;
import Return42.algorithms.deterministic.randomSearch.depthControl.FixedHorizon;
import Return42.algorithms.deterministic.randomSearch.planning.PlanGenerator;
import Return42.algorithms.deterministic.randomSearch.planning.PlanKeeper;
import Return42.algorithms.deterministic.randomSearch.planning.update.CombinedUpdatePolicy;
import Return42.algorithms.deterministic.randomSearch.planning.update.HeightMapChangedUpdatePolicy;
import Return42.algorithms.deterministic.randomSearch.planning.update.NpcAwareUpdatePolicy;
import Return42.algorithms.deterministic.randomSearch.rollout.heuristic.GameScoreAndHeightMapHeuristic;
import Return42.algorithms.deterministic.randomSearch.rollout.picker.FixedRolloutPicker;
import Return42.algorithms.deterministic.randomSearch.rollout.strategy.SoftMaxHeuristicRollOut;
import Return42.heuristics.action.ActionHeuristic;
import Return42.heuristics.action.HeightMapWithUseAndNilHeuristic;
import Return42.knowledgebase.GameInformation;
import Return42.knowledgebase.KnowledgeBase;
import Return42.knowledgebase.ScoreHeightMapGenerator;
import Return42.knowledgebase.WalkableSpaceGenerator;
import Return42.knowledgebase.observation.WalkableSpace;
import Return42.util.StateObservationUtils;
import Return42.util.TimerUtils;
import Return42.util.debug.DebugVisualization;
import core.game.StateObservation;

public class HeightMapAgent extends KnowledgebasedAgent {
	private final RandomSearch search;
	private final GameInformation gameInformation;
	private final ScoreHeightMapGenerator scoreHeightMapGenerator;
	private final WalkableSpaceGenerator walkableSpaceGenerator;
	
	private WalkableSpace[][] walkableGrid;
	private double[][] scoreMap;

	/**
	 * Public constructor with state observation and time due.
	 * 
	 * @param so
	 *            state observation of the current game.
	 * @param elapsedTimer
	 *            Timer for the controller creation.
	 */
	public HeightMapAgent( KnowledgeBase knowledge, StateObservation so, ElapsedCpuTimer elapsedTimer) {
		super(knowledge);
		this.scoreHeightMapGenerator = knowledge.getScoreHeightMapGenerator();
		this.gameInformation = knowledge.getGameInformation();
		this.walkableSpaceGenerator = knowledge.getWalkableSpaceGenerator();
		
		ActionHeuristic actionHeuristic = new HeightMapWithUseAndNilHeuristic( knowledge );

		this.search = new RandomSearch(
				new PlanKeeper(new CombinedUpdatePolicy(new HeightMapChangedUpdatePolicy(knowledge.getScoreHeightMapGenerator()), new NpcAwareUpdatePolicy())), 
				new PlanGenerator( 
						new GameScoreAndHeightMapHeuristic( knowledge ), 
						new FixedHorizon(), 
						new FixedRolloutPicker( new SoftMaxHeuristicRollOut( actionHeuristic, 4) )
				),
				Integer.MAX_VALUE,
				true
		);

		search.useConstructorExtraTime( learnFromActions(so), elapsedTimer );
	}

	/**
	 * Picks an action. This function is called every game step to request an
	 * action from the player.
	 * 
	 * @param stateObs
	 *            Observation of the current state.
	 * @param elapsedTimer
	 *            Timer when the action returned is due.
	 * @return An action for the current state
	 */
	public ACTIONS act(StateObservation stateObs, ElapsedCpuTimer elapsedTimer) {
		scoreMap = scoreHeightMapGenerator.getHeightMap(stateObs);
		ACTIONS action = search.act( learnFromActions(stateObs), TimerUtils.copyWithLessTime(elapsedTimer, 8) );
		walkableGrid = walkableSpaceGenerator.getWalkableSpace(stateObs, StateObservationUtils.getAvatarType(stateObs));

		return action;
	}
	
	@Override
	public void draw(Graphics2D g) {
		super.draw(g);
		
		DebugVisualization.drawWalkableSapce(walkableGrid, gameInformation, g);
		DebugVisualization.drawScoreMap(scoreMap, gameInformation, g);
	}
}