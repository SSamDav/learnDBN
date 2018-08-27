package dbn;

import java.util.Arrays;
import java.util.List;

public class LLScoringFunction implements ScoringFunction {

	@Override
	public double evaluate(Observations observations, int transition, List<Integer> parentNodesPast, int childNode) {
		return evaluate(observations, transition, parentNodesPast, null, childNode);
	}

	@Override
	public double evaluate(Observations observations, int transition, List<Integer> parentNodesPast,
			Integer parentNodePresent, int childNode) {
		
		LocalConfiguration c = new LocalConfiguration(observations.getAttributes(), observations.getMarkovLag(),
				parentNodesPast, parentNodePresent, childNode);
		
		double score = 0;

		do {
			
			c.setConsiderChild(false);
			double Nij = observations.count(c, transition);
//			System.out.println("Node: " + childNode + " Parents" + parentNodesPast.toString() + " ParentPresent: " +  parentNodePresent + " NIJ: " + Nij);
			c.setConsiderChild(true);
			
			do {
				
				double Nijk = observations.count(c, transition);
//				System.out.println("Configuration: " + c + " NIJK: " + Nijk);
				if ((double)Math.round(Nijk * 1000d) / 1000d != 0 && Nijk != Nij) {
					score += Nijk * (Math.log(Nijk) - Math.log(Nij));	
//					if(Double.isNaN(score)) {
//						System.out.println("nijk: " + Nijk + " nij:" + Nij);
//					}
					
				}
			} while (c.nextChild());
		} while (c.nextParents());
		
		return score;
	}
	
	@Override
	public double evaluate_2(Observations observations, int transition, List<Integer> parentNodesPast,
			List<Integer> parentNodePresent, int childNode) {

		LocalConfiguration c = new LocalConfiguration(observations.getAttributes(), observations.getMarkovLag(),
				parentNodesPast, parentNodePresent, childNode);

		double score = 0;

		do {
			c.setConsiderChild(false);
			double Nij = observations.count(c, transition);
			c.setConsiderChild(true);
			do {
				double Nijk = observations.count(c, transition);
				if ((double)Math.round(Nijk * 1000d) / 1000d != 0 && Nijk != Nij) {
					score += Nijk * (Math.log(Nijk) - Math.log(Nij));
				}
			} while (c.nextChild());
		} while (c.nextParents());

		return score;
	}
	
	
	

	@Override
	public double evaluate(Observations observations, List<Integer> parentNodesPast, int childNode) {
		return evaluate(observations, parentNodesPast, null, childNode);
	}
	


	@Override
	public double evaluate(Observations observations, List<Integer> parentNodesPast, Integer parentNodePresent,
			int childNode) {
		return evaluate(observations, -1, parentNodesPast, parentNodePresent, childNode);
	}
	
	
	@Override
	public double evaluate_2(Observations observations, List<Integer> parentNodesPast, List<Integer>  parentNodePresent,
			int childNode) {
		return evaluate_2(observations, -1, parentNodesPast, parentNodePresent, childNode);
	}
	
	
	

}
