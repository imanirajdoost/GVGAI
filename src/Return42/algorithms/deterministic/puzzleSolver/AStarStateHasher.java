package Return42.algorithms.deterministic.puzzleSolver;

import Return42.hashing.StateObservationHasher;
import Return42.knowledgebase.KnowledgeBase;
import core.game.StateObservation;

/**
 * Created by Oliver on 03.05.2015.
 */
public class AStarStateHasher {
	
	private final KnowledgeBase knowledge;

    public AStarStateHasher( KnowledgeBase knowledge ) {
        this.knowledge = knowledge;
    }

    public int hash(StateObservation state) {
        return StateObservationHasher.hashState( state, knowledge );
    }
   
}
