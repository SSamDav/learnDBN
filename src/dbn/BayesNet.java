package dbn;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import utils.Edge;
import utils.Utils;

import java.util.Random;

/**
 * Class that describes a Bayesian Network (BN).
 * 
 * @author josemonteiro
 * @author MargaridanarSousa
 * @author SSamDav
 *
 */
public class BayesNet {

	/**
	 * List of attributes of this BN.
	 */
	private List<Attribute> attributes;

	/**
	 * "processed" (unshifted) relations.
	 */
	private List<List<List<Integer>>> parentNodesPerSlice;

	/**
	 * "raw" (shifted) relations
	 */
	private List<List<Integer>> parentNodes;

	/**
	 * Parameters of a BN (CPT).
	 */
	private List<Map<Configuration, List<Double>>> parameters;

	private List<Integer> topologicalOrder;

	/**
	 * Markov Lag corresponding to this BN.
	 */
	private int markovLag;

	// for random sampling
	private Random r;

	/**
	 * Getter for the parameters.
	 * 
	 * @return List<Map<Configuration, List<Double>>> Returns the parameters of this
	 *         BN.
	 */
	public List<Map<Configuration, List<Double>>> getParameters() {
		return parameters;
	}

	/**
	 * Getter for the topological order.
	 * 
	 * @return List<Integer> Returns the topological order of the BN.
	 */
	public List<Integer> getTop() {
		return topologicalOrder;
	}

	/**
	 * Getter of the parent nodes.
	 * 
	 * @return List<List<Integer>> Returns the list of parents nodes of the BN.
	 */
	public List<List<Integer>> getParents() {
		return parentNodes;
	}

	// prior network
	public BayesNet(List<Attribute> attributes, List<Edge> intraRelations, Random r) {
		this(attributes, 0, intraRelations, (List<Edge>) null, r);
	}

	public BayesNet(List<Attribute> attributes, List<Edge> intraRelations) {
		this(attributes, 0, intraRelations, (List<Edge>) null, null);
	}

	// transition network, standard Markov lag = 1
	public BayesNet(List<Attribute> attributes, List<Edge> intraRelations, List<Edge> interRelations, Random r) {
		this(attributes, 1, intraRelations, interRelations, r);
	}

	public BayesNet(List<Attribute> attributes, List<Edge> intraRelations, List<Edge> interRelations) {
		this(attributes, 1, intraRelations, interRelations, null);
	}

	// transition network, arbitrary Markov lag
	public BayesNet(List<Attribute> attributes, int markovLag, List<Edge> intraRelations, List<Edge> interRelations) {
		this(attributes, markovLag, intraRelations, interRelations, null);
	}

	/**
	 * Constructor of a Bayesian Network. The edge heads are already unshifted
	 * (i.e., in the interval [0, n[).
	 * 
	 * @param attributes     Attributes of the BN.
	 * @param markovLag      Markov lag corresponding to the BN.
	 * @param intraRelations Intra-Relations between the attributes of this BN. The
	 *                       edge tails are unshifted.
	 * @param interRelations Inter-Relations between the attributes of this BN. The
	 *                       edge tails are shifted in Configuration style (i.e, [0,
	 *                       markovLag*n[).
	 * @param r
	 */
	public BayesNet(List<Attribute> attributes, int markovLag, List<Edge> intraRelations, List<Edge> interRelations,
			Random r) {

		this.attributes = attributes;
		this.markovLag = markovLag;
		int n = attributes.size();

		this.r = (r != null) ? r : new Random();

		// for topological sorting of t+1 slice
		List<List<Integer>> childNodes = new ArrayList<List<Integer>>(n);
		for (int i = n; i-- > 0;) {
			childNodes.add(new ArrayList<Integer>(n));
		}

		parentNodesPerSlice = new ArrayList<List<List<Integer>>>(markovLag + 1);
		for (int slice = 0; slice < markovLag + 1; slice++) {
			parentNodesPerSlice.add(new ArrayList<List<Integer>>(n));
			for (int i = 0; i < n; i++) {
				parentNodesPerSlice.get(slice).add(new ArrayList<Integer>());
			}
		}

		parentNodes = new ArrayList<List<Integer>>(n);
		for (int i = 0; i < n; i++)
			parentNodes.add(new ArrayList<Integer>());

		if (interRelations != null) {
			for (Edge e : interRelations) {
				// tail is shifted and refers to a previous slice
				int tail = e.getTail();
				int slice = tail / n;
				int unshiftedTail = tail % n;
				// head refers to the foremost slice
				int head = e.getHead();

				parentNodesPerSlice.get(slice).get(head).add(unshiftedTail);
				parentNodes.get(head).add(tail);
			}
		}

		// edges inside the same slice
		for (Edge e : intraRelations) {
			// tail is unshifted
			int tail = e.getTail();
			int shiftedTail = tail + n * markovLag;
			int head = e.getHead();

			parentNodesPerSlice.get(markovLag).get(head).add(tail);
			parentNodes.get(head).add(shiftedTail);
			childNodes.get(tail).add(head);
		}

		// sort for when applying configuration mask
		for (int i = n; i-- > 0;)
			Collections.sort(parentNodes.get(i));

		// obtain nodes by topological order
		topologicalOrder = Utils.topologicalSort(childNodes);
	}

	/**
	 * Function that generate the parameters of a given BN.
	 */
	public void generateParameters() {
		int n = attributes.size();
		parameters = new ArrayList<Map<Configuration, List<Double>>>(n);

		for (int i = 0; i < n; i++) {

			LocalConfiguration c = new LocalConfiguration(attributes, markovLag, parentNodes.get(i), i);
			int parentsRange = c.getParentsRange();
			if (parentsRange == 0) {
				parameters.add(new HashMap<Configuration, List<Double>>(2));
				int range = c.getChildRange();
				parameters.get(i).put(new Configuration(c), generateProbabilities(range));

			} else {
				parameters.add(new HashMap<Configuration, List<Double>>((int) Math.ceil(parentsRange / 0.75)));

				do {
					int range = c.getChildRange();
					parameters.get(i).put(new Configuration(c), generateProbabilities(range));
				} while (c.nextParents());
			}

		}
	}

	/**
	 * Learns the parameters of a stationary BN.
	 * 
	 * @param o Observations
	 */
	public void learnParameters(Observations o) {
		learnParameters(o, -1);
	}

	/**
	 * Learns the parameters of an BN.
	 * 
	 * @param o          Observations
	 * @param transition of the BN.
	 * @return String Corresponding to the learnt CPT.
	 */
	public String learnParameters(Observations o, int transition) {

		if (!o.getAttributes().equals(this.attributes)) {
			throw new IllegalArgumentException(
					"Attributes of the observations don't" + "match the attributes of the BN");
		}

		int n = attributes.size();
		parameters = new ArrayList<Map<Configuration, List<Double>>>(n);

		// for each node, generate its local CPT
		for (int i = 0; i < n; i++) {

			LocalConfiguration c = new LocalConfiguration(attributes, markovLag, parentNodes.get(i), i);

			int parentsRange = c.getParentsRange();

			// node i has no parents
			if (parentsRange == 0) {
				parameters.add(new HashMap<Configuration, List<Double>>(2));
				// specify its priors
				int range = c.getChildRange();
				List<Double> probabilities = new ArrayList<Double>(range - 1);
				// count for all except one of possible child values
				for (int j = range - 1; j-- > 0;) {
					double Nijk = o.count(c, transition);
					probabilities.add(1.0 * Nijk / o.numObservations(transition));
					c.nextChild();
				}
				// important, configuration is indexed by parents only
				// child must be reset
				c.resetChild();
				parameters.get(i).put(new Configuration(c), probabilities);

			} else {
				parameters.add(new HashMap<Configuration, List<Double>>((int) Math.ceil(parentsRange / 0.75)));

				do {
					c.setConsiderChild(false);
					double Nij = o.count(c, transition);
					c.setConsiderChild(true);

					int range = c.getChildRange();
					List<Double> probabilities = new ArrayList<Double>(range - 1);

					// no data found for given configuration
					if (Nij == 0) {
						for (int j = range - 1; j-- > 0;)
							// assume uniform distribution
							probabilities.add(1.0 / range);
					} else {
						// count for all except one of possible child values
						for (int j = range - 1; j-- > 0;) {
							double Nijk = o.count(c, transition);
							probabilities.add(1.0 * Nijk / Nij);
							c.nextChild();
						}
					}
					// important, configuration is index by parents only
					// child must be reset
					c.resetChild();
					parameters.get(i).put(new Configuration(c), probabilities);
				} while (c.nextParents());
			}

		}

		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");
		for (Map<Configuration, List<Double>> cpt : parameters)
			sb.append(Arrays.toString(cpt.entrySet().toArray()) + ls);

		return sb.toString();

	}

	/**
	 * Function that for a given configuration and for a given node calculates the
	 * probability of that observation.
	 * 
	 * @param node   Node where we want to calculate the probability.
	 * @param config Configuration of the observation.
	 * @return List<Double> Returns the probability.
	 */
	public List<Double> getParameters(int node, int[] config) {
		double prob = 0;
		List<Double> aux = new ArrayList<Double>();
		int node_aux = attributes.size() * markovLag + node;
		MutableConfiguration c = new MutableConfiguration(attributes, markovLag, config);
		Configuration indexParameters = c.applyMask(parentNodes.get(node), node);
		if (parameters.get(node).get(indexParameters).size() - 1 < config[node_aux]) {
			for (int i = 0; i < attributes.get(node).size() - 1; i++) {
				prob += parameters.get(node).get(indexParameters).get(i);
			}
			prob = 1 - prob;
			aux.add(prob);
		} else {
			prob = parameters.get(node).get(indexParameters).get(config[node_aux]);
			aux.add(prob);
		}
//		System.out.println(" Prob: " + prob);
		return aux;
	}

	private List<Double> generateProbabilities(int numValues) {
		List<Double> values = new ArrayList<Double>(numValues);
		List<Double> probabilities;

		// uniform sampling from [0,1[, more info at
		// http://cs.stackexchange.com/questions/3227/uniform-sampling-from-a-simplex
		// http://www.cs.cmu.edu/~nasmith/papers/smith+tromble.tr04.pdf
		// generate n-1 random values in [0,1[, sort them
		// use the distances between adjacent values as probabilities
		if (numValues > 2) {
			values.add(0.0);
			for (int j = numValues - 1; j-- > 0;) {
				values.add(r.nextDouble());
			}

			Collections.sort(values);

			probabilities = new ArrayList<Double>(numValues - 1);
			for (int j = 0; j < numValues - 1; j++) {
				probabilities.add(values.get(j + 1) - values.get(j));
			}
		} else if (numValues == 2) {
			probabilities = Arrays.asList(r.nextDouble());
		} else {
			probabilities = Arrays.asList(1.0);
		}
		return probabilities;
	}

	/**
	 * Calculates what is the next observation.
	 * 
	 * @param previousObservation Previous Observation.
	 * @param mostProbable        If true assigns the most probable values
	 * @return int[] Returns the next observation.
	 */
	public int[] nextObservation(int[] previousObservation, boolean mostProbable) {
		MutableConfiguration c = new MutableConfiguration(attributes, markovLag, previousObservation);
		for (int node : topologicalOrder) {

//			System.out.println("Node: " + node + " parents: " + parentNodes.get(node));
//			System.out.println("previousOBS: " + Arrays.toString(previousObservation));
//			System.out.println("Teste: " + Arrays.toString(c.configuration) );
			Configuration indexParameters = c.applyMask(parentNodes.get(node), node);
			List<Double> probabilities = parameters.get(node).get(indexParameters);
//			System.out.println("Probs: " + parameters.get(node));
//			System.out.println("indexParameters "+ Arrays.toString(indexParameters.configuration));
//			System.out.println("probabilities "+probabilities);
//			
			// System.out.println("Observation "+Arrays.toString(previousObservation));
			// System.out.println("Attributes "+attributes);

			int size = probabilities.size();
			int value;

			if (mostProbable) {
				int maxIndex = -1;
				double max = 0;
				double sum = 0;
				for (int i = 0; i < size; i++) {
					double p = probabilities.get(i);
					sum += p;
					if (max < p) {
						max = p;
						maxIndex = i;
					}
				}
				if (max < 1 - sum)
					maxIndex = size;
				value = maxIndex;
			}

			// random sampling
			else {
				double sample = r.nextDouble();

				double accum = probabilities.get(0);
				value = 0;

				while (sample > accum) {
					if (!(value < size - 1)) {
						++value;
						break;
					}
					accum += probabilities.get(++value);
				}
			}

			c.update(node, value);
		}
		int n = attributes.size();
		return Arrays.copyOfRange(c.toArray(), markovLag * n, (markovLag + 1) * n);
	}

	public static double[] compare(BayesNet original, BayesNet recovered) {
		return compare(original, recovered, false);
	}

	/**
	 * Compares a network that was learned from observations (recovered) with the
	 * original network used to generate those observations.
	 * 
	 * @param original  The original BN.
	 * @param recovered The recovered BN.
	 * @param verbose   If set, prints net comparison.
	 * @return int[] Returns the precision, recall and f1 scores in the format
	 *         [precision, recall, f1].
	 */
	public static double[] compare(BayesNet original, BayesNet recovered, boolean verbose) {
		// intra edges only, assume graph is a tree

		assert (original.attributes == recovered.attributes);
		int n = original.attributes.size();
		// maxParents
		// assert (original.maxParents == recovered.maxParents);

		List<List<Integer>> parentNodesTruePositive = new ArrayList<List<Integer>>(n);
		for (int i = 0; i < n; i++) {
			parentNodesTruePositive.add(new ArrayList<Integer>(original.parentNodes.get(i)));
			parentNodesTruePositive.get(i).retainAll(recovered.parentNodes.get(i));
		}

		int truePositive = 0;
		int conditionPositive = 0;
		int testPositive = 0;
		for (int i = 0; i < n; i++) {
			truePositive += parentNodesTruePositive.get(i).size();
			conditionPositive += original.parentNodes.get(i).size();
			testPositive += recovered.parentNodes.get(i).size();
		}

		double precision = 1.0 * truePositive / testPositive;
		double recall = 1.0 * truePositive / conditionPositive;
		double f1 = 2 * precision * recall / (precision + recall);

		if (verbose) {

			System.out.println("Original network (" + conditionPositive + ")");
			for (int i = 0; i < n; i++) {
				System.out.print(i + ": ");
				System.out.println(original.parentNodes.get(i));
			}

			System.out.println("Learnt network (" + testPositive + ")");
			for (int i = 0; i < n; i++) {
				System.out.print(i + ": ");
				System.out.println(recovered.parentNodes.get(i));
			}

			System.out.println("In common (" + truePositive + ")");
			for (int i = 0; i < n; i++) {
				System.out.print(i + ": ");
				System.out.println(parentNodesTruePositive.get(i));
			}

			System.out.println("Precision = " + precision);
			System.out.println("Recall  = " + recall);
			System.out.println("F1 = " + f1);
		}
		// { truePositive, conditionPositive, testPositive };

		return new double[] { precision, recall, f1 };

	}

	public int getMarkovLag() {
		return markovLag;
	}

	public String toDot(int t, boolean compactFormat) {

		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");

		int n = attributes.size();
		int presentSlice = t + markovLag;

		if (compactFormat)
			for (int head = 0; head < n; head++)
				for (Integer tail : parentNodesPerSlice.get(0).get(head))
					sb.append("X" + tail + " -> " + "X" + head + ls);

		else {
			for (int ts = 0; ts < markovLag + 1; ts++) {
				List<List<Integer>> parentNodesOneSlice = parentNodesPerSlice.get(ts);
				int slice = t + ts;
				for (int head = 0; head < n; head++)
					for (Integer tail : parentNodesOneSlice.get(head))
						sb.append("X" + tail + "_" + slice + " -> " + "X" + head + "_" + presentSlice + ls);
				sb.append(ls);
			}
		}

		return sb.toString();
	}

	public String toString(int t, boolean printParameters) {

		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");

		DecimalFormat df = new DecimalFormat("0.000");
		DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
		dfs.setDecimalSeparator('.');
		df.setDecimalFormatSymbols(dfs);

		int n = attributes.size();
		int presentSlice = t + markovLag;

		for (int ts = 0; ts < markovLag + 1; ts++) {
			List<List<Integer>> parentNodesOneSlice = parentNodesPerSlice.get(ts);
			int slice = t + ts;
			for (int head = 0; head < n; head++)
				for (Integer tail : parentNodesOneSlice.get(head))
					sb.append(attributes.get(tail).getName() + "[" + slice + "] -> " + attributes.get(head).getName()
							+ "[" + presentSlice + "]" + ls);
			sb.append(ls);
		}

		if (printParameters) {
			sb.append(ls);

			for (int i = 0; i < n; i++) {
				sb.append(attributes.get(i).getName() + ": " + attributes.get(i) + ls);
				Map<Configuration, List<Double>> cpt = parameters.get(i);
				Iterator<Entry<Configuration, List<Double>>> iter = cpt.entrySet().iterator();
				while (iter.hasNext()) {
					Entry<Configuration, List<Double>> e = iter.next();
					sb.append(e.getKey().toString());

					sb.append(": ");

					List<Double> probabilities = e.getValue();
					double sum = 1;
					for (double p : probabilities) {
						sb.append(df.format(p) + " ");
						sum -= p;
					}
					sb.append(sum < 0 ? df.format(0) : df.format(sum));

					sb.append(ls);
				}
				sb.append(ls);
			}
		}

		return sb.toString();
	}

	public String toString() {
		return toString(0, false);
	}

	public static void main(String[] args) {

		int range = 5;
		List<Double> values = new ArrayList<Double>(range);
		List<Double> probabilities;

		// generating a random probabilities vector

		Random r1 = new Random();

		if (range > 2) {
			values.add(0.0);
			for (int j = range - 1; j-- > 0;) {
				values.add(r1.nextDouble());
			}

			Collections.sort(values);

			probabilities = new ArrayList<Double>(range - 1);
			for (int j = 0; j < range - 1; j++) {
				probabilities.add(values.get(j + 1) - values.get(j));
			}
		} else {
			probabilities = Arrays.asList(r1.nextDouble());
		}

		System.out.println(probabilities);

		double sum = 0;
		for (int i = probabilities.size(); i-- > 0;)
			sum += probabilities.get(i);
		System.out.println(sum);

		int size = probabilities.size();
		int maxIndex = -1;
		double max = 0;
		sum = 0;
		for (int i = 0; i < size; i++) {
			double p = probabilities.get(i);
			sum += p;
			if (max < p) {
				max = p;
				maxIndex = i;
			}
		}

		if (max < 1 - sum)
			maxIndex = size;

		System.out.println("Most probable: " + maxIndex);

		// sample from given probability vector

		int[] count = new int[5];
		List<Double> prob = Arrays.asList(0.1, 0.2, 0.4, 0.2);

		Random r2 = new Random();

		for (int i = 1000000; i-- > 0;) {

			double sample = r2.nextDouble();

			double accum = prob.get(0);
			int value = 0;

			while (sample > accum) {
				if (!(value < prob.size() - 1)) {
					++value;
					break;
				}
				accum += prob.get(++value);
			}

			count[value]++;
		}

		System.out.println(Arrays.toString(count));

	}

}
