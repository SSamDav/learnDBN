package dbn;

import java.util.List;

public class MDLScoringFunction extends LLScoringFunction {
	private double epsilon = 0.0000000001;

	@Override
	public double evaluate(Observations observations, int transition, List<Integer> parentNodesPast,
			Integer parentNodePresent, int childNode) {

		LocalConfiguration c = new LocalConfiguration(observations.getAttributes(), observations.getMarkovLag(),
				parentNodesPast, parentNodePresent, childNode);

		double score = super.evaluate(observations, transition, parentNodesPast, parentNodePresent, childNode);

		// regularizer term
		score -= 0.5 * Math.log(observations.numObservations(transition) + epsilon) * c.getNumParameters();
		return score;
	}

	@Override
	public double evaluate_2(Observations observations, int transition, List<Integer> parentNodesPast,
			List<Integer> parentNodePresent, int childNode) {

		LocalConfiguration c = new LocalConfiguration(observations.getAttributes(), observations.getMarkovLag(),
				parentNodesPast, parentNodePresent, childNode);

		double score = super.evaluate_2(observations, transition, parentNodesPast, parentNodePresent, childNode);

		// regularizer term
		score -= 0.5 * Math.log(observations.numObservations(transition) + epsilon) * c.getNumParameters();

		return score;
	}

}
