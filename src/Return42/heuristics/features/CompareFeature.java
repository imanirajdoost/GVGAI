package Return42.heuristics.features;

import Return42.GameStateCache;

public interface CompareFeature {
    public boolean isUseful(GameStateCache state);

    public double evaluate(GameStateCache newState, GameStateCache oldState);

    public double getWeight();

    public void setWeight(double weight);

    public void adjustWeight(double factor);
}
