package utils;

import java.util.List;

import dbn.Observations;
import dbn.ScoringFunction;

public class ScoreCalculationThread extends Thread{
	private int t;
	private int i_init;
	private int i_final;
	private List<List<Integer>> parentSets;
	private Observations observations;
	private double[][][] scoresMatrix;
	private List<List<List<Integer>>> parentNodesPast;
	private List<List<List<List<Integer>>>> parentNodes;
	private int[][] numBestScores;
	private int[] numBestScoresPast;
	private int n;
	private ScoringFunction sf;
	private Boolean stationaryProcess;
	
	
	
	
	
	
	/**
	 * @param t
	 * @param i_init
	 * @param i_final
	 * @param parentSets
	 * @param observations
	 * @param scoresMatrix
	 * @param parentNodesPast
	 * @param parentNodes
	 * @param numBestScores
	 * @param numBestScoresPast
	 * @param n
	 * @param sf
	 * @param stationaryProcess
	 */
	public ScoreCalculationThread(int t, int i_init, int i_final, int n, List<List<Integer>> parentSets,
			Observations observations, double[][][] scoresMatrix, List<List<List<Integer>>> parentNodesPast,
			List<List<List<List<Integer>>>> parentNodes, int[][] numBestScores, int[] numBestScoresPast,
			ScoringFunction sf, Boolean stationaryProcess) {
		super();
		this.t = t;
		this.i_init = i_init;
		this.i_final = i_final;
		this.parentSets = parentSets;
		this.observations = observations;
		this.scoresMatrix = scoresMatrix;
		this.parentNodesPast = parentNodesPast;
		this.parentNodes = parentNodes;
		this.numBestScores = numBestScores;
		this.numBestScoresPast = numBestScoresPast;
		this.n = n;
		this.sf = sf;
		this.stationaryProcess = stationaryProcess;
	}






	public void run() {
		for (int i = i_init; i < i_final; i++) {
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
			for (int j = 0; j < n; j++) {
				if (i != j) {
					bestScore = Double.NEGATIVE_INFINITY;
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
	}
	
	
}