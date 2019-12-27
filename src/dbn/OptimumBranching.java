package dbn;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import utils.DisjointSets;
import utils.Edge;
import utils.Forest;
import utils.TreeNode;

import java.util.*;
//import java.util.Collections;

public class OptimumBranching {

	public List<Edge> branching;

	public static int root;

	public static int N;

	public static List<LinkedList<Integer>> Adj;

	/*
	 * public static List<Edge> evaluate(double[][] scoresMatrix) { return
	 * evaluate(scoresMatrix, -1, false); }
	 */
	//

	public List<Edge> getBranching() {
		return branching;
	}

	public OptimumBranching(double[][] scoresMatrix, int finalRoot, boolean spanning) {

		// INIT phase

		int n = scoresMatrix.length;

		int root_final = 0;

		// set of strongly-connected graph components
		DisjointSets scc = new DisjointSets(n);

		// set of weakly-connected graph components
		DisjointSets wcc = new DisjointSets(n);

		// maintains track of edges hierarchy to build final tree
		Forest<Edge> forest = new Forest<Edge>();

		List<List<Edge>> incidentEdges = new ArrayList<List<Edge>>(n);

		List<List<Edge>> cycleEdges = new ArrayList<List<Edge>>(n);

		List<Edge> enteringEdge = new ArrayList<Edge>(n);

		List<TreeNode<Edge>> forestLeaf = new ArrayList<TreeNode<Edge>>(n);

		int[] min = new int[n];

		List<Edge> branchingEdges = new LinkedList<Edge>();

		Deque<Integer> vertices = new ArrayDeque<Integer>(n);

		// stupid initialization
		Set<Integer> roots = new HashSet<Integer>();
		if (finalRoot >= 0)
			roots.add(finalRoot);

		for (int i = 0; i < n; i++) {

			incidentEdges.add(new LinkedList<Edge>());

			cycleEdges.add(new ArrayList<Edge>(n));

			enteringEdge.add(null);
			forestLeaf.add(null);

			// initial root of the strongly connected component of i
			min[i] = i;

			vertices.add(i);
		}

		// remove supplied final root node
		vertices.remove(finalRoot);

		// fill incident edges, already sorted by source
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				// skip self edges
				if (i != j) {
					incidentEdges.get(i).add(new Edge(j, i, scoresMatrix[i][j]));
				}
			}
		}

		// BRANCH phase
		while (!vertices.isEmpty()) {
			int r = vertices.pop();
			List<Edge> inEdges = incidentEdges.get(r);
			// input graph assumed strongly connected
			// if there is no edge incident on r, then r is a super-node
			// containing all vertices
			if (inEdges.isEmpty()) {
				// root of the final MWDST
				roots.add(min[r]);
				root_final = min[r];
			} else {

				// get heaviest edge (i,j) incident on r
				int maxIndex = 0;
				for (int i = 1; i < inEdges.size(); i++)
					if (inEdges.get(i).getWeight() > inEdges.get(maxIndex).getWeight())
						maxIndex = i;
				// edge is deleted from I[r]
				Edge heaviest = inEdges.remove(maxIndex);
				if (!spanning && heaviest.getWeight() <= 0) {
					roots.add(min[r]);
				} else {

					int i = heaviest.getTail();
					int j = heaviest.getHead();
					int iWeakComponentRoot = wcc.find(i);
					int jWeakComponentRoot = wcc.find(j);

					// add heaviest edge to forest of edges
					TreeNode<Edge> tn = forest.add(heaviest, cycleEdges.get(r));
					if (cycleEdges.get(r).isEmpty()) {
						forestLeaf.set(j, tn); // points leaf edge in F
					}

					// no cycle is created by heaviest edge
					if (iWeakComponentRoot != jWeakComponentRoot) {
						// join i and j in the same weakly-connected set
						wcc.union(iWeakComponentRoot, jWeakComponentRoot);
						// heaviest is the only chosen edge incident on r
						enteringEdge.set(r, heaviest);
					} else {
						// heaviest edge introduces a cycle
						// reset cycle edges
						cycleEdges.get(r).clear();

						Edge lightest = heaviest;
						// find cycle edges and obtain the lightest one
						for (Edge cycleEdge = heaviest; cycleEdge != null; cycleEdge = enteringEdge
								.get(scc.find(cycleEdge.getTail()))) {

							if (cycleEdge.getWeight() < lightest.getWeight())
								lightest = cycleEdge;

							// add (x,y) to the list of cycle edges
							cycleEdges.get(r).add(cycleEdge);
						}

						// update incident edges on r
						for (Edge e : inEdges) {
							e.setWeight(e.getWeight() + lightest.getWeight() - heaviest.getWeight());
						}

						// keep track of root for the spanning tree
						min[r] = min[scc.find(lightest.getHead())];

						// loop over cycle edges excluding heaviest
						for (Edge cycleEdge = enteringEdge.get(scc.find(i)); cycleEdge != null; cycleEdge = enteringEdge
								.get(scc.find(cycleEdge.getTail()))) {

							int headStrongComponentRoot = scc.find(cycleEdge.getHead());

							// update incident edges on other nodes of the cycle
							for (Edge e : incidentEdges.get(headStrongComponentRoot)) {
								e.setWeight(e.getWeight() + lightest.getWeight() - cycleEdge.getWeight());
							}

							// join vertices of the cycle into one scc
							scc.union(r, headStrongComponentRoot);

							// join incident edges lists;
							incidentEdges.set(r,
									merge(incidentEdges.get(r), incidentEdges.get(headStrongComponentRoot), scc, r));
						}

						vertices.push(r);
					}
				}
			}
		}

		// LEAF phase
		for (int root : roots) {
			TreeNode<Edge> rootLeaf = forestLeaf.get(root);
			if (rootLeaf != null) {
				forest.deleteUp(rootLeaf);
			}
		}

		while (!forest.isEmpty()) {
			TreeNode<Edge> forestRoot = forest.getRoot();
			Edge e = forestRoot.getData();
			branchingEdges.add(e);
			TreeNode<Edge> forestRootLeaf = forestLeaf.get(e.getHead());
			forest.deleteUp(forestRootLeaf);
		}

		branching = branchingEdges;
		root = root_final;
		N = n;
		
		List<LinkedList<Integer>> adj = new ArrayList<LinkedList<Integer>>();
		for (int i = 0; i < N; i++) {
			adj.add(new LinkedList<Integer>());
		}

		for (Edge e : branching) {
			adj.get(e.getTail()).add(e.getHead());
		}

		Adj = adj;
	}
	
	public OptimumBranching(double[][] scoresMatrix) {
		this(scoresMatrix, -1, true);

	}

	public ArrayList<Integer> ancestors(int i) {
		ArrayList<Integer> anc = new ArrayList<Integer>();
		boolean b = true;

		int node = i;

		while (b == true) {
			boolean b2 = false;
			for (Edge e : branching) {

				int head = e.getHead();
				int tail = e.getTail();

				if (head == node) {
					anc.add(tail);
					node = tail;
					b2 = true;
					break;
				}

			}

			if (b2 == false)
				b = false;

		}

		return anc;
	}

	public static ArrayList<ArrayList<Integer>> Subsets(ArrayList<Integer> anc, int k) {

		ArrayList<ArrayList<Integer>> total = new ArrayList<ArrayList<Integer>>();

		int n = anc.size();

		// Run a loop for printing all 2^n
		// subsets one by one
		for (int i = 0; i < (1 << n); i++) {

			if (0 < Integer.bitCount(i) && Integer.bitCount(i) <= k) {

				ArrayList<Integer> part = new ArrayList<Integer>();

				// Print current subset
				for (int j = 0; j < n; j++) {

					// (1<<j) is a number with jth bit 1
					// so when we 'and' them with the
					// subset number we get which numbers
					// are present in the subset and which
					// are not
					if ((i & (1 << j)) > 0)
						part.add(anc.get(j));

				}

				total.add(part);

			}
		}

		total.add(new ArrayList<Integer>());

		return total;
	}

	public List<List<Integer>> Anc(double[][] scoresMatrix, int k) {

		List<List<Integer>> parents = new ArrayList<List<Integer>>();

		for (int i = 0; i < N; i++) {

			ArrayList<Integer> anc = ancestors(i);

			if (anc.size() > 0) {

				ArrayList<ArrayList<Integer>> total = OptimumBranching.Subsets(anc, k);

				double score_max = Double.NEGATIVE_INFINITY;
				;

				ArrayList<Integer> best_anc = new ArrayList<Integer>();

				for (int j = 0; j < total.size(); j++) {

					double score = 0;

					for (int h = 0; h < total.get(j).size(); h++) {
						score += scoresMatrix[i][total.get(j).get(h)];
					}

					if (score > score_max) {
						score_max = score;
						best_anc = total.get(j);
					}

				}

				parents.add(best_anc);

			}

			else {
				parents.add(new ArrayList<Integer>());
			}

		}
		return parents;
	}

	public void Ckg(double[][] scoresMatrix, ScoringFunction sf, Observations observations, int k) {
		// Get BFS order of branchingEdges_partial
		BFS();

		// Consistent graph
		List<Edge> branchingEdges2 = new LinkedList<Edge>();

		for (int i = 0; i < N; i++) {
			ArrayList<Integer> anc = ancestors(i);

			if (anc.size() > 0) {

				ArrayList<ArrayList<Integer>> total = OptimumBranching.Subsets(anc, k);

				double score_max = Double.NEGATIVE_INFINITY;

				ArrayList<Integer> best_anc = new ArrayList<Integer>();

				for (int j = 0; j < total.size(); j++) {
					double score = 0;

					for (int h = 0; h < total.get(j).size(); h++) {
						score += scoresMatrix[i][total.get(j).get(h)];
					}

					if (score > score_max) {
						score_max = score;
						best_anc = total.get(j);
					}

				}

				for (int m = 0; m < best_anc.size(); m++) {
					Edge e = new Edge(best_anc.get(m), i);
					branchingEdges2.add(e);
				}

			}

		}

		branching = branchingEdges2;

	}

	public void BFS() {
		// Mark all the vertices as not visited(By default

		List<Integer> order = new ArrayList<Integer>();

		List<Edge> branching_total = new ArrayList<Edge>();

		// set as false)
		boolean visited[] = new boolean[N];

		// Create a queue for BFS
		LinkedList<Integer> queue = new LinkedList<Integer>();

		// Mark the current node as visited and enqueue it
		visited[root] = true;
		queue.add(root);

		while (queue.size() != 0) {
			// Dequeue a vertex from queue and print it
			root = queue.poll();
			order.add(root);

			// Get all adjacent vertices of the dequeued vertex s
			// If a adjacent has not been visited, then mark it
			// visited and enqueue it
			Iterator<Integer> i = Adj.get(root).listIterator();
			while (i.hasNext()) {
				int m = i.next();
				if (!visited[m]) {
					visited[m] = true;
					queue.add(m);
				}
			}
		}

		for (int i = 0; i < order.size() - 1; i++) {
			branching_total.add(new Edge(order.get(i), order.get(i + 1)));

		}

		branching = branching_total;
	}

	/**
	 * Merges two sorted list of edges, eliminating those that are inside the
	 * strongly-connected component passed as argument. Incoming lists must be
	 * sorted by tail/source. If there is more than one edge with the same source,
	 * keeps only the heaviest.
	 * 
	 * @param l1        first sorted list
	 * @param l2        second sorted list
	 * @param scc       strongly-connect components
	 * @param component id of the relevant component
	 * @return merged list
	 */
	private static List<Edge> merge(List<Edge> l1, List<Edge> l2, DisjointSets scc, int component) {
		List<Edge> merged = new ArrayList<Edge>(l1.size() + l2.size());
		ListIterator<Edge> i1 = l1.listIterator();
		ListIterator<Edge> i2 = l2.listIterator();

		while (i1.hasNext() && i2.hasNext()) {
			// skip edges inside the strongly-connected component
			while (i1.hasNext()) {
				if (scc.find(i1.next().getTail()) != component) {
					i1.previous();
					break;
				}
			}
			while (i2.hasNext()) {
				if (scc.find(i2.next().getTail()) != component) {
					i2.previous();
					break;
				}
			}

			if (!i1.hasNext() && !i2.hasNext())
				break;

			if (!i1.hasNext())
				merged.add(i2.next());

			else if (!i2.hasNext())
				merged.add(i1.next());

			// i1.hasNext() && i2.hasNext()
			else {
				Edge e1 = i1.next();
				Edge e2 = i2.next();

				if (e1.getTail() < e2.getTail()) {
					merged.add(e1);
					i2.previous();
				}

				else if (e1.getTail() > e2.getTail()) {
					merged.add(e2);
					i1.previous();
				}

				// if both have the same source, keep the heaviest
				else {
					if (e1.getWeight() > e2.getWeight())
						merged.add(e1);
					else
						merged.add(e2);
				}
			}
		}

		return merged;
	}

	public static void main(String[] args) {

	}
}
