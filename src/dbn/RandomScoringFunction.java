package dbn;

import java.util.List;
import java.util.Random;

public class RandomScoringFunction implements ScoringFunction {

	public RandomScoringFunction() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public double evaluate(Observations observations, int transition, List<Integer> parentNodesPast, int childNode) {
		return evaluate(observations, transition, parentNodesPast, null, childNode);
	}

	@Override
	public double evaluate(Observations observations, int transition, List<Integer> parentNodesPast,
			Integer parentNodePresent, int childNode) {
		Random r = new Random();
		return -100 + (0 + 100) * r.nextDouble();
	}

	@Override
	public double evaluate_2(Observations observations, int transition, List<Integer> parentNodesPast,
			List<Integer> parentNodePresent, int childNode) {
		Random r = new Random();
		return -100 + (0 + 100) * r.nextDouble();
	}

	@Override
	public double evaluate(Observations observations, List<Integer> parentNodesPast, Integer parentNodePresent,
			int childNode) {
		return evaluate(observations, -1, parentNodesPast, null, childNode);
	}

	@Override
	public double evaluate_2(Observations observations, List<Integer> parentNodesPast, List<Integer> parentNodePresent,
			int childNode) {
		return evaluate_2(observations, -1, parentNodesPast, parentNodePresent, childNode);
	}

	@Override
	public double evaluate(Observations observations, List<Integer> parentNodesPast, int childNode) {
		return evaluate(observations, parentNodesPast, null, childNode);
	}

}
