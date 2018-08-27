package dbn;

import java.util.List;

public interface ScoringFunction {

	public abstract double evaluate(Observations observations, int transition, List<Integer> parentNodesPast,
			int childNode);

	public abstract double evaluate(Observations observations, int transition, List<Integer> parentNodesPast,
			Integer parentNodePresent, int childNode);
	
	
	public abstract double evaluate_2(Observations observations, int transition, List<Integer> parentNodesPast,
			List<Integer> parentNodePresent, int childNode);
	
	
	

	/**
	 * Calculate score when process is stationary.
	 */
	public abstract double evaluate(Observations observations, List<Integer> parentNodesPast,
			Integer parentNodePresent, int childNode);

	
	/**
	 * Calculate score when process is stationary.
	 */
	public abstract double evaluate_2(Observations observations, List<Integer> parentNodesPast,
			List<Integer> parentNodePresent, int childNode);
	
	
	
	
	/**
	 * Calculate score when process is stationary.
	 */
	public abstract double evaluate(Observations observations, List<Integer> parentNodesPast, int childNode);

}
