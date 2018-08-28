package dbn;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import utils.Edge;
import utils.Utils;

public class Scores {

	private Observations observations;

	/**
	 * scoresMatrix[t][i][j] is the score of the arc
	 * Xj[t+markovLag]->Xi[t+markovLag].
	 */
	private double[][][] scoresMatrix;

	/**
	 * parentNodes.get(t).get(i) is the list of optimal parents in
	 * {X[t],...,X[t+markovLag-1]} of Xi[t+markovLag] when there is no arc from
	 * X[t+markovLag] to X[t+markovLag].
	 */
	private List<List<List<Integer>>> parentNodesPast;

	/**
	 * parentNodes.get(t).get(i).get(j) is the list of optimal parents in
	 * {X[t],...,X[t+markovLag-1]} of Xi[t+markovLag] when the arc
	 * Xj[t+markovLag]->Xi[t+markovLag] is present.
	 */
	private List<List<List<List<Integer>>>> parentNodes;

	/**
	 * Upper limit on the number of parents from previous time slices.
	 */
	private int maxParents;

	/**
	 * A list of all possible sets of parent nodes. Set cardinality lies within
	 * the range [1, maxParents].
	 */
	private List<List<Integer>> parentSets;

	/**
	 * If true, evaluates only one score matrix for all transitions.
	 */
	private boolean stationaryProcess;

	private boolean evaluated = false;

	private boolean verbose;
	
	
	private List<List<Integer>> ancestors;
	
	
	private List<List<Integer>> PastParents;

	
	private List<List<Integer>> PresentParents;
	
	public Scores(Observations observations, int maxParents) {
		this(observations, maxParents, true, true);
	}

	public Scores(Observations observations, int maxParents, boolean stationaryProcess, boolean verbose) {
		this.observations = observations;
		this.maxParents = maxParents;
		this.stationaryProcess = stationaryProcess;
		this.verbose = verbose;

		int n = this.observations.numAttributes();
		int p = this.maxParents;
		int markovLag = observations.getMarkovLag();

		// calculat sum_i=1^k nCi
		int size = n * markovLag;
		for (int previous = n, i = 2; i <= p; i++) {
			int current = previous * (n - i + 1) / i;
			size += current;
			previous = current;
		}
		size+=1; // To count with the empty set!
		
		
		// TODO: check for size overflow

		// generate parents sets
		parentSets = new ArrayList<List<Integer>>(size);
		for (int i = 1; i <= p; i++) {
			generateCombinations(n * markovLag, i);
		}
		
		parentSets.add(new ArrayList<Integer>());

		int numTransitions = stationaryProcess ? 1 : observations.numTransitions();
		parentNodesPast = new ArrayList<List<List<Integer>>>(numTransitions);
		parentNodes = new ArrayList<List<List<List<Integer>>>>(numTransitions);

		for (int t = 0; t < numTransitions; t++) {

			parentNodesPast.add(new ArrayList<List<Integer>>(n));
			// allocate parentNodesPast
			List<List<Integer>> parentNodesPastTransition = parentNodesPast.get(t);
			for (int i = 0; i < n; i++) {
				parentNodesPastTransition.add(new ArrayList<Integer>());
			}

			parentNodes.add(new ArrayList<List<List<Integer>>>(n));
			// allocate parentNodes
			List<List<List<Integer>>> parentNodesTransition = parentNodes.get(t);
			for (int i = 0; i < n; i++) {
				parentNodesTransition.add(new ArrayList<List<Integer>>(n));
				List<List<Integer>> parentNodesTransitionHead = parentNodesTransition.get(i);
				for (int j = 0; j < n; j++) {
					parentNodesTransitionHead.add(new ArrayList<Integer>());
				}
			}
		}

		// allocate scoresMatrix
		scoresMatrix = new double[numTransitions][n][n];


	}

	public Scores evaluate(ScoringFunction sf) {

		int n = observations.numAttributes();
		int numTransitions = scoresMatrix.length;

		int[] numBestScoresPast = new int[n];
		int[][] numBestScores = new int[n][n];

		for (int t = 0; t < numTransitions; t++) {
			// System.out.println("evaluating score in transition " + t + "/" +
			// numTransitions);
			for (int i = 0; i < n; i++) {
				// System.out.println("evaluating node " + i + "/" + n);
				double bestScore = Double.NEGATIVE_INFINITY;
		
				
				for (List<Integer> parentSet : parentSets) {
					double score = stationaryProcess ? sf.evaluate(observations, parentSet, i) : sf.evaluate(
							observations, t, parentSet, i);
					// System.out.println("Xi:" + i + " ps:" + parentSet +
					// " score:" + score);
					if (bestScore < score) {
						bestScore = score;
						parentNodesPast.get(t).set(i, parentSet);
						numBestScoresPast[i] = 1;
					} else if (bestScore == score)
						numBestScoresPast[i]++;
				}
				
				
				//System.out.println("Finished parents past");
			
				for (int j = 0; j < n; j++) {
					scoresMatrix[t][i][j] = -bestScore;
				}
			}

			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (i != j) {
						double bestScore = Double.NEGATIVE_INFINITY;
						for (List<Integer> parentSet : parentSets) {
							double score = stationaryProcess ? sf.evaluate(observations, parentSet, j, i) : sf
									.evaluate(observations, t, parentSet, j, i);
							// System.out.println("Xi:" + i + " Xj:" + j +
							// " ps:" + parentSet + " score:" + score);
							if (bestScore < score) {
								bestScore = score;
								parentNodes.get(t).get(i).set(j, parentSet);
								numBestScores[i][j] = 1;
							} else if (bestScore == score)
								numBestScores[i][j]++;
						}

						scoresMatrix[t][i][j] += bestScore;

					}
				}
			}

			if (verbose) {
				// System.out.println(Arrays.toString(numBestScoresPast));
				// System.out.println(Arrays.deepToString(numBestScores));
				long numSolutions = 1;
				for (int i = 0; i < n; i++)
					numSolutions *= numBestScoresPast[i];
				for (int i = 0; i < n; i++)
					for (int j = 0; j < n; j++)
						if (i != j)
							numSolutions *= numBestScores[i][j];
				//System.out.println("Number of networks with max score: " + numSolutions);
			}

		}

		evaluated = true;

		return this;

	}
	
	
	
	
	
	public List<Integer> Best_Past_Parents(List<Integer> ancestors,int i,int t,ScoringFunction sf){
		
		
		List<Integer> best_parent_set = new ArrayList<Integer>();
		double bestScore =Double.NEGATIVE_INFINITY;
	
		for (List<Integer> parentSet : parentSets) {
			double score = stationaryProcess ? sf.evaluate_2(observations, parentSet, ancestors, i) : sf
					.evaluate_2(observations, t, parentSet, ancestors, i);
			if (bestScore < score) {
				bestScore = score;
				best_parent_set=parentSet;
			}
			//System.out.println("Node "+i);
			//System.out.println("bestScore "+bestScore);
			
			double score_empty =stationaryProcess ? sf.evaluate_2(observations, new ArrayList<Integer>(), ancestors, i) : sf
					.evaluate_2(observations, t, new ArrayList<Integer>(), ancestors, i);
			//System.out.println("Score empty "+score_empty);
			
			//System.out.println("----------------------------------------");
			
			if(score_empty>bestScore) {
				bestScore=score;
				best_parent_set= new ArrayList<Integer>();
			}
			
			
			
		}
		
		return best_parent_set;
	}
		

	
	public double prob() {
		
		
		// PARENTS PAST ????????
		
			int n = observations.numAttributes();
			int numTransitions = scoresMatrix.length;

			int[] numBestScoresPast = new int[n];
			int[][] numBestScores = new int[n][n];
			double score = 0;
	
			for (int t = 0; t < numTransitions; t++) {
				// System.out.println("evaluating score in transition " + t + "/" +
				// numTransitions);
				for (int i = 0; i < n; i++) {
					// System.out.println("evaluating node " + i + "/" + n);
					for (List<Integer> parentSet : parentSets) {
						
						LocalConfiguration c = new LocalConfiguration(observations.getAttributes(), observations.getMarkovLag(),
								parentSet, i);


						do {
							c.setConsiderChild(false);
							double Nij = observations.count(c, t);
							c.setConsiderChild(true);
							do {
								double Nijk = observations.count(c, t);
								if (Nijk != 0 && Nijk != Nij) {
									score +=(Math.log(Nijk) - Math.log(Nij));
								}
							} while (c.nextChild());
						} while (c.nextParents());
						
					}
				}
			}
						return score;

	}
	
	
	

	// adapted from http://stackoverflow.com/a/7631893
	private void generateCombinations(int n, int k) {

		int[] comb = new int[k];
		for (int i = 0; i < comb.length; i++) {
			comb[i] = i;
		}

		boolean done = false;
		while (!done) {

			List<Integer> intList = new ArrayList<Integer>(k);
			for (int i : comb) {
				intList.add(i);
			}
			this.parentSets.add(intList);

			int target = k - 1;
			comb[target]++;
			if (comb[target] > n - 1) {
				// carry the one
				while (comb[target] > ((n - 1 - (k - target)))) {
					target--;
					if (target < 0) {
						break;
					}
				}
				if (target < 0) {
					done = true;
				} else {
					comb[target]++;
					for (int i = target + 1; i < comb.length; i++) {
						comb[i] = comb[i - 1] + 1;
					}
				}
			}
		}
	}

	public double[][] getScoresMatrix(int transition) {
		return scoresMatrix[transition];
	}

	public DynamicBayesNet toDBN() {
		return toDBN(-1, false, false);
	}
	
	public DynamicBayesNet toDBN(int root, boolean spanning) {
		return toDBN(root, spanning, false);
	}
	
	public DynamicBayesNet toDBN(int root, boolean spanning, boolean prior) {

		if (!evaluated)
			throw new IllegalStateException("Scores must be evaluated before being converted to DBN");

		int n = observations.numAttributes();

		int numTransitions = scoresMatrix.length;

		List<BayesNet> transitionNets = new ArrayList<BayesNet>(numTransitions);

		for (int t = 0; t < numTransitions; t++) {

			OptimumBranching intraRelations = new  OptimumBranching(scoresMatrix[t]);
			
			//System.out.println("intraRelations "+intraRelations.branching);
			
	
			if (verbose) {
				double score = 0;
				boolean[][] adj = Utils.adjacencyMatrix(intraRelations.branching, n);

				for (int i = 0; i < n; i++) {
					boolean isRoot = true;
					for (int j = 0; j < n; j++) {
						if (adj[i][j]) {
							// score
							score += (scoresMatrix[t][i][j] - scoresMatrix[t][i][i]);
							isRoot = false;
						}
					}
					if (isRoot)
						// subtract since sign was inverted
						score -= scoresMatrix[t][i][i];
				}

				//System.out.println("Network score: " + score);
			}

			List<Edge> interRelations = new ArrayList<Edge>(n * maxParents);

			boolean[] hasParent = new boolean[n];

			for (Edge intra : intraRelations.branching) {
				int tail = intra.getTail();
				int head = intra.getHead();
				List<List<List<Integer>>> parentNodesT = parentNodes.get(t);
				
				for (Integer nodePast : parentNodesT.get(head).get(tail)) {
					interRelations.add(new Edge(nodePast, head));
					hasParent[head] = true;
				}
			}

			for (int i = 0; i < n; i++)
				if (!hasParent[i]) {
					List<List<Integer>> parentNodesPastT = parentNodesPast.get(t);
					for (int nodePast : parentNodesPastT.get(i))
						interRelations.add(new Edge(nodePast, i));
				}
			
			
			
			//System.out.println("Inter "+interRelations);
			// parentsnodes past 
			
			
			

			BayesNet bt = new BayesNet(observations.getAttributes(), observations.getMarkovLag(), intraRelations.branching,
					interRelations);
			
			transitionNets.add(bt);
		}
		if(prior) {
			List<Edge> prior_array = new ArrayList<Edge>();
			List<Attribute> a = observations.getAttributes();
			BayesNet b0 = new BayesNet(a, prior_array);
			return new DynamicBayesNet(observations.getAttributes(), b0, transitionNets);
		}

		return new DynamicBayesNet(observations.getAttributes(), transitionNets);

	}
	
	public DynamicBayesNet to_bcDBN(ScoringFunction sf,int k) {
		return to_bcDBN(sf, k, false); 
	}
	
	public DynamicBayesNet to_bcDBN(ScoringFunction sf,int k, boolean prior) {

		if (!evaluated)
			throw new IllegalStateException("Scores must be evaluated before being converted to DBN");

		int n = observations.numAttributes();

		int numTransitions = scoresMatrix.length;

		List<BayesNet> transitionNets= new ArrayList<BayesNet>(numTransitions);
		
	
		for (int t = 0; t < numTransitions; t++) {

			
			OptimumBranching intraRelations= new OptimumBranching(scoresMatrix[t]);
			
			intraRelations.BFS();
			
			PastParents = new ArrayList<List<Integer>>(n);
			
			PresentParents = new ArrayList<List<Integer>>(n);
			
			for(int i=0; i<n;i++) {
				
				ArrayList<Integer> anc = intraRelations.ancestors(i);

				PastParents.add(new ArrayList<Integer>());
				
				PresentParents.add(new ArrayList<Integer>());
				
				double bestScore = Double.NEGATIVE_INFINITY;
				
				
				for (List<Integer> parentSet : parentSets) {
					
					for(ArrayList<Integer> S:OptimumBranching.Subsets(anc,k)) {
					

					double score = stationaryProcess ? sf.evaluate_2(observations, parentSet, S, i) : sf
							.evaluate_2(observations, t, parentSet, S, i);
				
				if(score>bestScore) {
					bestScore=score;
					PastParents.set(i, parentSet);
					PresentParents.set(i, S);
				}
				
				
				
			}
					
					
					
				}
				
				
			}				
				
				List<Edge> intra= new ArrayList<Edge>();
				
				List<Edge> inter= new ArrayList<Edge>();

				
				for(int node=0;node<n;node++) {
					
					for(int j=0; j<PastParents.get(node).size();j++) {
						
						inter.add(new Edge(PastParents.get(node).get(j),node));
					}
				}
				
				
				
				for(int node=0;node<n;node++) {
					
					for(int j=0; j<PresentParents.get(node).size();j++) {
						
						intra.add(new Edge(PresentParents.get(node).get(j),node));
					}
				}
				
				
		

	
			BayesNet bt = new BayesNet(observations.getAttributes(), observations.getMarkovLag(),intra,
					inter);

			transitionNets.add(bt);
		}
		
		if(prior) {
			List<Edge> prior_array = new ArrayList<Edge>();
			List<Attribute> a = observations.getAttributes();
			BayesNet b0 = new BayesNet(a, prior_array);
			return new DynamicBayesNet(observations.getAttributes(), b0, transitionNets);
		}

		return new DynamicBayesNet(observations.getAttributes(), transitionNets);

	}
	
	
	
	public DynamicBayesNet to_cDBN(ScoringFunction sf,int k) {
		return to_cDBN(sf, k, false); 
	}
	
	public DynamicBayesNet to_cDBN(ScoringFunction sf,int k, boolean prior) {

		if (!evaluated)
			throw new IllegalStateException("Scores must be evaluated before being converted to DBN");

		int n = observations.numAttributes();

		int numTransitions = scoresMatrix.length;

		List<BayesNet> transitionNets= new ArrayList<BayesNet>(numTransitions);
		
	
		for (int t = 0; t < numTransitions; t++) {

			
			OptimumBranching intraRelations= new OptimumBranching(scoresMatrix[t]);

			PastParents = new ArrayList<List<Integer>>(n);
			
			PresentParents = new ArrayList<List<Integer>>(n);
			
			for(int i=0; i<n;i++) {
				
				ArrayList<Integer> anc = intraRelations.ancestors(i);

				PastParents.add(new ArrayList<Integer>());
				
				PresentParents.add(new ArrayList<Integer>());
				
				double bestScore = Double.NEGATIVE_INFINITY;
				
				
				for (List<Integer> parentSet : parentSets) {
					
					for(ArrayList<Integer> S:OptimumBranching.Subsets(anc,k)) {
					

					double score = stationaryProcess ? sf.evaluate_2(observations, parentSet, S, i) : sf
							.evaluate_2(observations, t, parentSet, S, i);
				
				if(score>bestScore) {
					bestScore=score;
					PastParents.set(i, parentSet);
					PresentParents.set(i, S);
				}
				
				
				
			}
					
					
					
				}
				
				
			}				
				
				List<Edge> intra= new ArrayList<Edge>();
				
				List<Edge> inter= new ArrayList<Edge>();

				
				for(int node=0;node<n;node++) {
					
					for(int j=0; j<PastParents.get(node).size();j++) {
						
						inter.add(new Edge(PastParents.get(node).get(j),node));
					}
				}
				
				
				
				for(int node=0;node<n;node++) {
					
					for(int j=0; j<PresentParents.get(node).size();j++) {
						
						intra.add(new Edge(PresentParents.get(node).get(j),node));
					}
				}

			BayesNet bt = new BayesNet(observations.getAttributes(), observations.getMarkovLag(),intra,
					inter);

			transitionNets.add(bt);
		}
		
		if(prior) {
			List<Edge> prior_array = new ArrayList<Edge>();
			List<Attribute> a = observations.getAttributes();
			BayesNet b0 = new BayesNet(a, prior_array);
			return new DynamicBayesNet(observations.getAttributes(), b0, transitionNets);
		}

		return new DynamicBayesNet(observations.getAttributes(), transitionNets);

	}

/*	
	public DynamicBayesNet toDBN_Ckg(ScoringFunction sf,int k) {

		if (!evaluated)
			throw new IllegalStateException("Scores must be evaluated before being converted to DBN");

		int n = observations.numAttributes();

		int numTransitions = scoresMatrix.length;

		List<BayesNet> transitionNets= new ArrayList<BayesNet>(numTransitions);
		
	
		for (int t = 0; t < numTransitions; t++) {

			
			OptimumBranching intraRelations= new OptimumBranching(scoresMatrix[t]);
			
			//System.out.println(intraRelations_before);
			
			
			intraRelations.Ckg(scoresMatrix[t], sf, observations, k);
					
					
			
			
			//System.out.println(intraRelations);
			
			
			if (verbose) {
				double score = 0;

				boolean[][] adj = Utils.adjacencyMatrix(intraRelations.getBranching(), n);

				for (int i = 0; i < n; i++) {
					boolean isRoot = true;
					for (int j = 0; j < n; j++) {
						if (adj[i][j]) {
							// score
							score += (scoresMatrix[t][i][j] - scoresMatrix[t][i][i]);
							isRoot = false;
						}
					}
					if (isRoot)
						// subtract since sign was inverted
						score -= scoresMatrix[t][i][i];
				}

				
			}

			List<Edge> interRelations = new ArrayList<Edge>(n * maxParents);

			boolean[] hasParent = new boolean[n];*/
			// intraRelations_before
			/*for (Edge intra : intraRelations) {
				int tail = intra.getTail();
				int head = intra.getHead();
				List<List<List<Integer>>> parentNodesT = parentNodes.get(t);
				for (Integer nodePast : parentNodesT.get(head).get(tail)) {
					interRelations.add(new Edge(nodePast, head));
					hasParent[head] = true;
				}
			}*/
			
			/*ancestors=intraRelations.Anc(scoresMatrix[t],k);
			
			for(int i=0;i<n;i++) {
				
				List<Integer> anc = ancestors.get(i);				
				if(anc.size()>0) {
					
					hasParent[i]=true;
					
					
					List<Integer> past_parents = Best_Past_Parents(anc,i,t,sf);
					for(int j=0; j<past_parents.size();j++) {
						interRelations.add(new Edge(past_parents.get(j),i));
					}

				}
				
				else hasParent[i]=false;

			}

			for (int i = 0; i < n; i++)
				if (!hasParent[i]) {
					List<List<Integer>> parentNodesPastT = parentNodesPast.get(t);
					for (int nodePast : parentNodesPastT.get(i))
						interRelations.add(new Edge(nodePast, i));
				}*/
			
	/*		List<List<Integer>> parents = new ArrayList<List<Integer>>();
			
			
			for(int i=0; i<n;i++) {
				parents.add(new ArrayList<Integer>());
			}
			
			
			
			
			for(Edge e : interRelations) {
				
				for(int i=0; i<n;i++) {
					
					if(e.getHead()==i) parents.get(i).add(e.getTail());
				}	
			}*/
			
			
			/*
			
			
			List<List<Edge>> best = OptimumBranching.Best2(ancestors,scoresMatrix[t], sf, observations, k);

			interRelations=best.get(0);
			
			intraRelations=best.get(1);
			
			System.out.println("Inter relations "+interRelations);
			
			System.out.println("Intra relations "+intraRelations);*/
			/*
			
			BayesNet bt = new BayesNet(observations.getAttributes(), observations.getMarkovLag(), intraRelations.branching,
					interRelations);

			transitionNets.add(bt);
		}

		return new DynamicBayesNet(observations.getAttributes(), transitionNets);

	}*/
	
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");
		int n = scoresMatrix[0].length;
		DecimalFormat df = new DecimalFormat("0.00");

		int numTransitions = scoresMatrix.length;

		for (int t = 0; t < numTransitions; t++) {
			// sb.append("--- Transition " + t + " ---" + ls);
			// sb.append("Maximum number of parents in t: " + maxParents + ls);
			//
			// sb.append(ls);

			sb.append("Scores matrix:" + ls);
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					sb.append(df.format(scoresMatrix[t][i][j]) + " ");
				}
				sb.append(ls);
			}

			// sb.append(ls);
			//
			// sb.append("Parents only in t:" + ls);
			// for (int i = 0; i < n; i++) {
			// sb.append(i + ": " + parentNodesPast.get(t).get(i) + ls);
			// }
			//
			// sb.append(ls);
			//
			// sb.append("Parents in t for each parent in t+1:" + ls);
			// sb.append("t+1:	");
			// for (int i = 0; i < n; i++) {
			// sb.append(i + "	");
			// }
			// sb.append(ls);
			// for (int i = 0; i < n; i++) {
			// sb.append(i + ":	");
			// for (int j = 0; j < n; j++) {
			// sb.append(parentNodes.get(t).get(i).get(j) + "	");
			// }
			// sb.append(ls);
			// }
			//
			// sb.append(ls);
		}

		return sb.toString();
	}

}
