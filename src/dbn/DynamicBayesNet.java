package dbn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import utils.Edge;
import utils.Utils;

import java.io.FileNotFoundException;
import java.lang.Math;

public class DynamicBayesNet {

	private List<Attribute> attributes;

	private int markovLag;

	private BayesNet initialNet;

	public List<BayesNet> transitionNets;

	public DynamicBayesNet(List<Attribute> attributes, BayesNet initialNet, List<BayesNet> transitionNets) {
		this.attributes = attributes;
		this.initialNet = initialNet;
		this.transitionNets = transitionNets;
		this.markovLag = transitionNets.get(0).getMarkovLag();
	}

	public List<Attribute> getAttributes(){
		return attributes;
	}
	
	
	public List<BayesNet> getTrans(){
		return transitionNets;
	}
	
	
	public BayesNet getInit() {
		
		return initialNet;
	}
	
	
	
	
	public DynamicBayesNet(List<Attribute> attributes, List<BayesNet> transitionNets) {
		this(attributes, null, transitionNets);
	}

	public DynamicBayesNet generateParameters() {
		if(initialNet != null)
			initialNet.generateParameters();
		for (BayesNet transitionNet : transitionNets)
			transitionNet.generateParameters();
		return this;
	}

	public void learnParameters(Observations o) {
		learnParameters(o, false);
	}

	public String learnParameters(Observations o, boolean stationaryProcess) {

		if (stationaryProcess) {
			// assert there is only one transition network
			if (transitionNets.size() > 1)
				throw new IllegalArgumentException("DBN has more than one transition network, cannot "
						+ "learn parameters considering a stationary process");
			
			return transitionNets.get(0).learnParameters(o, -1);

		} else {
			int T = transitionNets.size();
			for (int t = 0; t < T; t++) {
				transitionNets.get(t).learnParameters(o, t);
			}
		}
		return null;
	}
	
	public DynamicBayesNet parameterEM(Observations o, boolean stationaryProcess) {
		DynamicBayesNet dbn = this;
		Observations o_new;
		double score = Double.NEGATIVE_INFINITY;
		double scorePrev;
		
		this.generateParameters();
		int i = 0;
		do {
			scorePrev = score;
			o_new = o.fillMissingValues(dbn, stationaryProcess);
			dbn.learnParameters(o_new, stationaryProcess);
			score = dbn.getScore(o_new, new LLScoringFunction(), stationaryProcess);
			i++;
			System.out.println("Parameter EM step: " + i + " score: " + score);
		}while(score > scorePrev);
		
		return dbn;
	}
	
	public double getScore(Observations o, ScoringFunction sf, boolean stationaryProcess) {
		double score = 0;
		if (stationaryProcess) {
			// assert there is only one transition network
			if (transitionNets.size() > 1)
				throw new IllegalArgumentException("DBN has more than one transition network, cannot "
						+ "learn parameters considering a stationary process");
			for(int n = 0; n < attributes.size(); n++) {
				score += sf.evaluate(o, transitionNets.get(0).getParents().get(n), n);
			}

		} else {
			int T = transitionNets.size();
			for (int t = 0; t < T; t++) {
				for(int n = 0; n < attributes.size(); n++) {
					score += sf.evaluate(o, transitionNets.get(t).getParents().get(n), n);
				}
			}
		}
		return score;
	}

	public Observations generateObservations(int numIndividuals) {
		return generateObservations(numIndividuals, transitionNets.size(), false);
	}

	public Observations generateObservations(int numIndividuals, int numTransitions, boolean stationaryProcess) {
		int[][][] obsMatrix = generateObservationsMatrix(null, numIndividuals, numTransitions, stationaryProcess, false);
		double[][] counts = new double[numTransitions][numIndividuals];
		for(int i = 0; i < numTransitions; i++) {
			Arrays.fill(counts[i], 1);
		}
		return new Observations(attributes, obsMatrix, counts);
	}

	public Observations forecast(Observations originalObservations, int numTransitions, boolean stationaryProcess,
			boolean mostProbable) {
		if (stationaryProcess) {
			// assert there is only one transition network
			if (transitionNets.size() > 1)
				throw new IllegalArgumentException("DBN has more than one transition network, cannot "
						+ "learn parameters considering a stationary process");

		}
		List<int[]> initialObservations = originalObservations.getFirst();
		int[][][] obsMatrix = generateObservationsMatrix(initialObservations, initialObservations.size(),
				numTransitions, stationaryProcess, mostProbable);
		return new Observations(originalObservations, obsMatrix);
	}

	public Observations forecast(Observations originalObservations) {
		return forecast(originalObservations, transitionNets.size(), false, false);
	}
	
	private int[][][] generateObservationsMatrix(List<int[]> initialObservations, int numIndividuals,
			int numTransitions, boolean stationaryProcess, boolean mostProbable) {
		// System.out.println("generating observations");

		if (!stationaryProcess && numTransitions > transitionNets.size())
			throw new IllegalArgumentException("DBN only has " + transitionNets.size() + " "
					+ "transitions defined, cannot generate " + numTransitions + ".");

		int n = attributes.size();
		//
		int[][][] obsMatrix = new int[numTransitions][numIndividuals][(markovLag + 1) * n];

		for (int subject = 0; subject < numIndividuals; subject++) {
			
			int[] observation0 = initialObservations != null ? initialObservations.get(subject) : initialNet
					.nextObservation(null, mostProbable);
			int[] observationT = observation0;
			for (int transition = 0; transition < numTransitions; transition++) {
				System.arraycopy(observationT, 0, obsMatrix[transition][subject], 0, n * markovLag);

				int[] observationTplus1 = stationaryProcess ? transitionNets.get(0).nextObservation(observationT,
						mostProbable) : transitionNets.get(transition).nextObservation(observationT, mostProbable);
				System.arraycopy(observationTplus1, 0, obsMatrix[transition][subject], n * markovLag, n);

				observationT = Arrays.copyOfRange(obsMatrix[transition][subject], n, (markovLag + 1) * n);
			}
		}

		return obsMatrix;
	}

	

	public static double[] compare(DynamicBayesNet original, DynamicBayesNet recovered) {
		return compare(original, recovered, false);
	}
	
	
	//List<int[]> 
		
	public static double[] compare(DynamicBayesNet original, DynamicBayesNet recovered, boolean verbose) {
		assert (original.transitionNets.size() == recovered.transitionNets.size());
		//int numTransitions = original.transitionNets.size();
		//List<int[]> counts = new ArrayList<int[]>(numTransitions);
		/*for (int t = 0; t < numTransitions; t++) {
			counts.add(BayesNet.compare(original.transitionNets.get(t), recovered.transitionNets.get(t), verbose));
		}*/
		return BayesNet.compare(original.transitionNets.get(0), recovered.transitionNets.get(0), verbose);
	}

	public String toDot(boolean compactFormat) {
		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");
		String dl = ls + ls;
		int n = attributes.size();
		int T = transitionNets.size();

		if (compactFormat && (T != 1 || markovLag != 1))
			throw new IllegalStateException(
					"More than one transition network or Markov lag larger than 1, cannot create compact graph.");

		// digraph init
		sb.append("digraph dbn{" + dl);

		if (compactFormat) {
			for (int i = 0; i < n; i++) {
				sb.append("X" + i);
				String attributeName = attributes.get(i).getName();
				if (attributeName != null)
					sb.append("[label=\"" + attributeName);
				else
					sb.append("[label=\"X" + i);
				sb.append("\"];" + ls);
			}
			sb.append(ls);
		} else {
			for (int t = 0; t < T + markovLag; t++) {
				// slice t attributes
				for (int i = 0; i < n; i++) {
					sb.append("X" + i + "_" + t);
					String attributeName = attributes.get(i).getName();
					if (attributeName != null)
						sb.append("[label=\"" + attributeName);
					else
						sb.append("[label=\"X" + i);
					sb.append("[" + t + "]\", group=g"+t+ "];" + ls);
				}
				sb.append(ls);
			}
			for(int i = 0; i < n; i++) {
				sb.append("{ rank=same ");
				for(int t = 0; t < T+markovLag; t++) {
					sb.append("X" + i + "_" + t + " ");
				}
				sb.append("}");
				sb.append(ls);
			}
		}
		sb.append(ls);

		// transition and intra-slice (t>0) edges
		for (int t = 0; t < T; t++)
			sb.append(transitionNets.get(t).toDot(t, compactFormat));
		
		if(!compactFormat) {
			sb.append("edge[style=invis];");
			for(int t = 0; t <  T+markovLag; t++) {
				
				sb.append(ls);
				sb.append("X" + 0 + "_" + t);
				for(int i = 1; i < n; i++) {
					sb.append(" -> X" + i + "_" + t);
				}
				sb.append(";");
				sb.append(ls);
			}
		}
		
		
		sb.append(ls + "}" + ls);

		return sb.toString();
	}

	public String toString(boolean printParameters) {
		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");

		if (initialNet != null)
			sb.append(initialNet.toString(-1, printParameters));

		int i = 0;
		for (Iterator<BayesNet> iter = transitionNets.iterator(); iter.hasNext();) {
			sb.append(iter.next().toString(i, printParameters));
			i++;
			if (iter.hasNext())
				sb.append("-----------------" + ls + ls);
		}

		return sb.toString();
	}

	public String toString() {
		return toString(false);
	}
	
	
	
	public static double Mean( double[] obs) {
		
		double sum=0;
		
		for(int i=0; i <obs.length ; i++) {
			sum+=obs[i];
			
		}

		
		sum= sum/(double)obs.length;
		return sum;
	}
	
	
	public static double StandDes( double[] obs,double mean) {
		
		double sum=0;
		
		for(int i=0; i <obs.length ; i++) {
			sum+= Math.pow(obs[i]-mean,2);
			
		}
		sum=sum/(double) obs.length;
		sum= (Math.sqrt(sum)/ Math.sqrt((double) obs.length))*1.96;
		return sum;
	}
	
	@SuppressWarnings("unused")
	public static void main(String[] args) throws FileNotFoundException  {
		
		

		Attribute a1 = new NominalAttribute();
		a1.setName("X1");
		a1.add("yes");
		a1.add("no");
		//a1.add("ok");
		//a1.add("me");
		

		Attribute a2 = new NumericAttribute();
		a2.setName("X2");
		a2.add("10");
		a2.add("20");
		//a2.add("30");
		//a2.add("40");

		Attribute a3 = new NumericAttribute();
		a3.setName("X3");
		a3.add("0");
		a3.add("1");
		//a3.add("2");
		//a3.add("3");

		Attribute a4 = new NumericAttribute();
		a4.setName("X4");
		a4.add("0");
		a4.add("1");
		//a4.add("2");
		//a4.add("3");
		
		
		Attribute a5 = new NumericAttribute();
		a5.setName("X5");
		a5.add("0");
		a5.add("1");
		//a5.add("2");
		//a5.add("3");
		
		
		
		Attribute a6 = new NumericAttribute();
		a6.setName("X6");
		a6.add("0");
		a6.add("1");
		//a6.add("2");
		//a6.add("3");
		
		Attribute a7 = new NumericAttribute();
		a7.setName("X7");
		a7.add("0");
		a7.add("1");
		a7.add("2");
		//a7.add("3");
		
		Attribute a8 = new NumericAttribute();
		a8.setName("X8");
		a8.add("0");
		a8.add("1");
		a8.add("2");
		//a8.add("3");
		
		Attribute a9 = new NumericAttribute();
		a9.setName("X9");
		a9.add("0");
		a9.add("1");
		a9.add("2");
		//a9.add("3");
		
		Attribute a10 = new NumericAttribute();
		a10.setName("X10");
		a10.add("0");
		a10.add("1");
		a10.add("2");
		//a10.add("3");
		
		
		
		//a10.add("2");
		//a10.add("3");
		
		Attribute a11 = new NumericAttribute();
		a11.setName("X11");
		a11.add("0");
		a11.add("1");
		
		Attribute a12 = new NumericAttribute();
		a12.setName("X12");
		a12.add("0");
		a12.add("1");
		
		Attribute a13 = new NumericAttribute();
		a13.setName("X13");
		a13.add("0");
		a13.add("1");
		
		Attribute a14 = new NumericAttribute();
		a14.setName("X14");
		a14.add("0");
		a14.add("1");
		
		Attribute a15 = new NumericAttribute();
		a15.setName("X15");
		a15.add("0");
		a15.add("1");
		
		Attribute a16 = new NumericAttribute();
		a16.add("0");
		a16.add("1");
		
		Attribute a17 = new NumericAttribute();
		a17.add("0");
		a17.add("1");
		
		Attribute a18 = new NumericAttribute();
		a18.add("0");
		a18.add("1");
		
		Attribute a19 = new NumericAttribute();
		a19.add("0");
		a19.add("1");
		
		Attribute a20 = new NumericAttribute();
		a20.add("0");
		a20.add("1");

		//a6,a7,a8,a9,a10
		//,a6,a7,a8,a9,a10
		//,a11,a12,a13,a14,a15,a16,a17,a18,a19,a20
		
		//a6,a7,a8,a9,a10
		
		//,a6,a7,a8,a9,a10,a11,a12,a13,a14,a15,a16,a17,a18,a19,a20
		
		//,a6,a7,a8,a9,a10
		
		List<Attribute> a = Arrays.asList(a1,a2,a3,a4,a5);
		
		Edge e00 = new Edge(0,0);
		Edge e01 = new Edge(0,1);
		Edge e02 = new Edge(0,2);
		Edge e03 = new Edge(0,3);
		Edge e04 = new Edge(0,4);
		Edge e05 = new Edge(0,5);
		Edge e06 = new Edge(0,6);
		Edge e07 = new Edge(0,7);
		Edge e08 = new Edge(0,8);
		Edge e09 = new Edge(0,9);
		
		
		Edge e10 = new Edge(1,0);
		Edge e11 = new Edge(1,1);
		Edge e12 = new Edge(1,2);
		Edge e13 = new Edge(1,3);
		Edge e14 = new Edge(1,4);
		Edge e15 = new Edge(1,5);
		Edge e16 = new Edge(1,6);
		Edge e17 = new Edge(1,7);
		Edge e18 = new Edge(1,8);
		Edge e19 = new Edge(1,9);
		
		Edge e20 = new Edge(2,0);
		Edge e21 = new Edge(2,1);
		Edge e22 = new Edge(2,2);
		Edge e23 = new Edge(2,3);
		Edge e24 = new Edge(2,4);
		Edge e25 = new Edge(2,5);
		Edge e26 = new Edge(2,6);
		Edge e27 = new Edge(2,7);
		Edge e28 = new Edge(2,8);
		Edge e29 = new Edge(2,9);
		
		
		Edge e30 = new Edge(3,0);
		Edge e31 = new Edge(3,1);
		Edge e32 = new Edge(3,2);
		Edge e33 = new Edge(3,3);
		Edge e34 = new Edge(3,4);
		Edge e35 = new Edge(3,5);
		Edge e36 = new Edge(3,6);
		Edge e37 = new Edge(3,7);
		Edge e38 = new Edge(3,8);
		Edge e39 = new Edge(3,9);
		
		
		Edge e40 = new Edge(4,0);
		Edge e41 = new Edge(4,1);
		Edge e42 = new Edge(4,2);
		Edge e43 = new Edge(4,3);
		Edge e44 = new Edge(4,4);
		Edge e45 = new Edge(4,5);
		Edge e46 = new Edge(4,6);
		Edge e47 = new Edge(4,7);
		Edge e48 = new Edge(4,8);
		Edge e49 = new Edge(4,9);
		
		Edge e50 = new Edge(5,0);
		Edge e51 = new Edge(5,1);
		Edge e52 = new Edge(5,2);
		Edge e53 = new Edge(5,3);
		Edge e54 = new Edge(5,4);
		Edge e55 = new Edge(5,5);
		Edge e56 = new Edge(5,6);
		Edge e57 = new Edge(5,7);
		Edge e58 = new Edge(5,8);
		Edge e59 = new Edge(5,9);
		
		Edge e60 = new Edge(6,0);
		Edge e61 = new Edge(6,1);
		Edge e62 = new Edge(6,2);
		Edge e63 = new Edge(6,3);
		Edge e64 = new Edge(6,4);
		Edge e65 = new Edge(6,5);
		Edge e66 = new Edge(6,6);
		Edge e67 = new Edge(6,7);
		Edge e68 = new Edge(6,8);
		Edge e69 = new Edge(6,9);
		
		Edge e70 = new Edge(7,0);
		Edge e71 = new Edge(7,1);
		Edge e72 = new Edge(7,2);
		Edge e73 = new Edge(7,3);
		Edge e74 = new Edge(7,4);
		Edge e75 = new Edge(7,5);
		Edge e76 = new Edge(7,6);
		Edge e77 = new Edge(7,7);
		Edge e78 = new Edge(7,8);
		Edge e79 = new Edge(7,9);
		
		Edge e80 = new Edge(8,0);
		Edge e81 = new Edge(8,1);
		Edge e82 = new Edge(8,2);
		Edge e83 = new Edge(8,3);
		Edge e84 = new Edge(8,4);
		Edge e85 = new Edge(8,5);
		Edge e86 = new Edge(8,6);
		Edge e87 = new Edge(8,7);
		Edge e88 = new Edge(8,8);
		Edge e89 = new Edge(8,9);
		
		Edge e90 = new Edge(9,0);
		Edge e91 = new Edge(9,1);
		Edge e92 = new Edge(9,2);
		Edge e93 = new Edge(9,3);
		Edge e94 = new Edge(9,4);
		Edge e95 = new Edge(9,5);
		Edge e96 = new Edge(9,6);
		Edge e97 = new Edge(9,7);
		Edge e98 = new Edge(9,8);
		Edge e99 = new Edge(9,9);
		
		
		
		
		Edge e419= new Edge(4,19);
		Edge e519= new Edge(5,19);
		Edge e619= new Edge(6,19);
		Edge e719= new Edge(7,19);
		Edge e819= new Edge(8,19);
		Edge e919= new Edge(9,19);
		Edge e1019= new Edge(10,19);
		Edge e1119= new Edge(11,19);
		Edge e1219= new Edge(12,19);
		Edge e1319= new Edge(13,19);
		Edge e1419= new Edge(14,19);
		Edge e1519= new Edge(15,19);
		Edge e1619= new Edge(16,19);
		Edge e1719= new Edge(17,19);
		Edge e1819= new Edge(18,19);
		
		Edge e1414 = new Edge(14,14);
		Edge e1515 = new Edge(15,15);
		Edge e1616 = new Edge(16,16);
		Edge e1717 = new Edge(17,17);
		Edge e1818 = new Edge(18,18);
		Edge e1919 = new Edge(19,19);
		
		Edge e910 = new Edge(9,10);
		Edge e1011 = new Edge(10,11);
		Edge e1112 = new Edge(11,12);
		Edge e1213 = new Edge(12,13);
		Edge e1314 = new Edge(13,14);
		Edge e1415 = new Edge(14,15);
		Edge e1516 = new Edge(15,16);
		Edge e1617 = new Edge(16,17);
		Edge e1718 = new Edge(17,18);
		
		Edge e1010= new Edge(10,10);
		
		Edge e1313= new Edge(13,13);
		
		Edge e1111= new Edge(11,11);
		
		Edge e1212= new Edge(12,12);
		
		Edge e118= new Edge(1,18);
		Edge e218= new Edge(2,18);
		Edge e318= new Edge(3,18);
		Edge e418= new Edge(4,18);
		Edge e518= new Edge(5,18);
		Edge e618= new Edge(6,18);
		Edge e718= new Edge(7,18);
		Edge e818= new Edge(8,18);
		Edge e918= new Edge(0,18);
		Edge e1018= new Edge(10,18);
		
		Edge e019= new Edge(0,19);
		Edge e119= new Edge(1,19);
		
		Edge e1015= new Edge(10,15);
		Edge e1215= new Edge(12,15);
		
		Edge e810 = new Edge(8,10);
		Edge e911 = new Edge(9,11);
		Edge e1012=new Edge(10,12);
		Edge e1113= new Edge(11,13);
		Edge e1214= new Edge(12,14);
		Edge e1315=new Edge(13,15);
		Edge e1416=new Edge(14,16);
		Edge e1517=new Edge(15,17);
		Edge e1618=new Edge(16,18);
		
		
		Edge e710=new Edge(7,10);
		Edge e811= new Edge(8,11);
		Edge e912= new Edge(9,12);
		Edge e1013= new Edge(10,13);
		Edge e1114 = new Edge(11,14);
		
		Edge e812=new Edge(8,12);
		Edge e711=new Edge(7,11);
		Edge e610=new Edge(6,10);
		Edge e1014= new Edge(10,14);
		Edge e913= new Edge(9,13);
		
		Edge e512=new Edge(5,12);
		Edge e712=new Edge(7,12);
		Edge e612=new Edge(6,12);
		
		

		//e00, e11, e22, e33,e44,e55,e66,e77,e88,e99,
		//e55,e66,e77,e88,e99
		//e00,e10,e20,e11,e01,e22,e33,e44,e55,e66,e77,e88,e99,e1010,e1111,e1212,e1313,e1414,e1515,e1616,e1717,e1818,e1919
		
		//inter
		
		//e00,e10,e41,e81,e32,e62,e03,e53,e84,e54,e15,e85,e36,e86,e67,e77,e28,e58,e69,e99
		
		//e23,e44
		List<Edge> inter = Arrays.asList(e10,e02,e33);
		//List<Edge> inter = Arrays.asList(e20,e40,e31,e61,e42,e92,e43,e83,e64,e04,e25,e85,e46,e96,e57,e37,e68,e88,e09,e59);
		
		
		//e01,e12,e23,e34,e45,e56,e67,e78,e89,e79,e69,e59,e49,e08,e28,e58,e37,e47,e17,e27,e26,e35,e05,e15,e24,e03
		//Before   e01
		List<Edge> intra = Arrays.asList(e01,e12,e23,e34,e02,e24);
		//e45,e56,e67,e78,e89,e09,e19,e29,e39,e49,e59,e69,e79,
		//e1011,e1112,e1213,e1314,e1415,e1516,e1617,e1718,e1819,e019,e119,e218,e318,e418,e1015,e1215
		//e01,e12,e23,e34,e45,e56,e67,e78,e89,e49,e59,e69,e39,e02,e24,e46,e68,e37,e04,e06,e08,e03,e13,e15,e17
		
		//e01,e02,e12,e13,e23,e34,e24,e45,e35,e56,e46,e67,e57,e78,e68,e89,e79,e910,e810,e1011,e911,e1112,e1012,e1213,e1113,e1314,e1214,e1415,e1315,e1516,e1416,e1617,e1517,e1718,e1618,e1819,e1719
		
		//List<Edge> intra= Arrays.asList(e01,e12,e23,e34,e45,e56,e67,e78,e89);
		
		
		List<Edge> intra_start =Arrays.asList();
		
		BayesNet b0 = new BayesNet(a,intra_start);
		b0.generateParameters();
		BayesNet bt = new BayesNet(a, intra, inter);
		
		bt.generateParameters();

DynamicBayesNet dbn1 = new DynamicBayesNet(a, b0, Arrays.asList(bt));

Utils.writeToFile("/home/margarida/Documents/Grafos_cDBN/true_net" + ".dot", dbn1.toDot(false));

// Evolução


int[] n_obs = new int[5];
n_obs[0]=100;
n_obs[1]=200;
n_obs[2]=300;
n_obs[3]=400;
n_obs[4]=2000;

int p=1;

int k=2;



for(int i=0;i<30;i++)
{

Observations o = dbn1.generateObservations((i+1)*100);
	
	
	
	Scores sMDL = new Scores(o, p);
	Scores sLL = new Scores(o, p);
	
	
	sMDL.evaluate(new 	MDLScoringFunction());
	sLL.evaluate(new 	LLScoringFunction());
	
	
	ScoringFunction ll = new LLScoringFunction();
	ScoringFunction mdl = new MDLScoringFunction();

	

	DynamicBayesNet dbn_2 = sMDL.to_bcDBN(mdl,k);
	
	System.out.println("Done bcDBN");
	
	Utils.writeToFile("/home/margarida/Documents/Grafos_cDBN/" +(i+1)*100+".dot", dbn_2.toDot(false));

	/*

	DynamicBayesNet dbn_5 = sMDL.toDBN();

	System.out.println("Done tDBN");
	
	Utils.writeToFile("/home/margarida/Documents/Grafos_cDBN/net" +n_obs[i]+"_"+"tDBN"+ ".dot", dbn_5.toDot(false));*/






}


double[] n_needed = new double[]{800,600,1500,1000,1800};

double mean = Mean(n_needed);

double sd = StandDes(n_needed,mean);

System.out.println("Mean "+mean);

System.out.println("sd "+sd);





/*
Observations o = dbn1.generateObservations(2000);

int p=2;

Scores sMDL = new Scores(o, p);
Scores sLL = new Scores(o, p);


sLL.evaluate(new 	LLScoringFunction());
sMDL.evaluate(new 	MDLScoringFunction());



ScoringFunction ll = new LLScoringFunction();
ScoringFunction mdl = new MDLScoringFunction();

//ll.evaluate(o, new ArrayList<Integer>(),2,1);


DynamicBayesNet dbn_6 = sLL.to_bcDBN(ll,2);





System.out.println(dbn_6.toString());*/




	/*


		
		Utils.writeToFile("/home/margarida/Documents/Grafos_cDBN/net4" + ".dot", dbn1.toDot(false));
		
		
	
		

		
	
	
	int k=5;
	int num_N=4;
	
	int p=1;
		
long start_ckg_ll=0;
long final_ckg_ll=0;

long start_ckg_mdl=0;
long final_ckg_mdl=0;

long start_tan_ll=0;
long final_tan_ll=0;

long start_tan_mdl=0;
long final_tan_mdl=0;



int[] n_obs = new int[num_N];
n_obs[0]=100;
n_obs[1]=500;
n_obs[2]=1000;
n_obs[3]=2000;


StringBuilder sb = new StringBuilder();
String ls = System.getProperty("line.separator");




for(int i=0;i<4;i++)
{
	ArrayList<Long> res = new ArrayList<Long>();


	double[] per_ckg_mdl= new double[5];
	double[] per_ckg_ll= new double[5];
	double[] per_ckg_mdl_ll= new double[5];
	
	
	double[] rec_ckg_mdl= new double[5];
	double[] rec_ckg_ll= new double[5];
	double[] rec_ckg_mdl_ll= new double[5];
	
	double[] f1_ckg_mdl= new double[5];
	double[] f1_ckg_ll= new double[5];
	double[] f1_ckg_mdl_ll= new double[5];
	
	double[] per_tan_mdl= new double[5];
	double[] per_tan_ll= new double[5];
	
	double[] rec_tan_mdl= new double[5];
	double[] rec_tan_ll= new double[5];
	
	double[] f1_tan_mdl= new double[5];
	double[] f1_tan_ll= new double[5];
	
	double time_tan_mdl= 0;
	double time_tan_ll= 0;
	
	double time_ckg_mdl= 0;
	double time_ckg_ll= 0;
	
	
	
	for(int j=0; j<5; j++) {
	
		
	//System.out.println(n_obs[i]);
	
	Observations o = dbn1.generateObservations(n_obs[i]);
	
	
	
	Scores sMDL = new Scores(o, p);
	Scores sLL = new Scores(o, p);
	
	
	sMDL.evaluate(new 	MDLScoringFunction());
	sLL.evaluate(new 	LLScoringFunction());
	
	
	ScoringFunction ll = new LLScoringFunction();
	ScoringFunction mdl = new MDLScoringFunction();
	
	start_ckg_ll = System.currentTimeMillis();
	DynamicBayesNet dbn_1 = sLL.to_bcDBN(ll,k);
	final_ckg_ll= System.currentTimeMillis();
	per_ckg_ll[j]= compare(dbn1,dbn_1,false)[0];
	rec_ckg_ll[j]= compare(dbn1,dbn_1,false)[1];
	f1_ckg_ll[j]= compare(dbn1,dbn_1,false)[2];
	
	
	
	start_ckg_mdl = System.currentTimeMillis();
	DynamicBayesNet dbn_2 = sMDL.to_bcDBN(mdl,k);
	final_ckg_mdl= System.currentTimeMillis();
	per_ckg_mdl[j]= compare(dbn1,dbn_2,false)[0];
	rec_ckg_mdl[j]= compare(dbn1,dbn_2,false)[1];
	f1_ckg_mdl[j]= compare(dbn1,dbn_2,false)[2];
	
	//start_ckg_ll_mdl= System.currentTimeMillis();
	DynamicBayesNet dbn_3 = sMDL.to_bcDBN(ll,k);
	//final_ckg_ll_mdl= System.currentTimeMillis();
	per_ckg_mdl_ll[j]= compare(dbn1,dbn_3,false)[0];
	rec_ckg_mdl_ll[j]= compare(dbn1,dbn_3,false)[1];
	f1_ckg_mdl_ll[j]= compare(dbn1,dbn_3,false)[2];
	
	
	//System.out.println("LL");
	
	start_tan_ll= System.currentTimeMillis();
	DynamicBayesNet dbn_4 = sLL.toDBN();
	final_tan_ll= System.currentTimeMillis();
	per_tan_ll[j]= compare(dbn1,dbn_4,false)[0];
	rec_tan_ll[j]= compare(dbn1,dbn_4,false)[1];
	f1_tan_ll[j]= compare(dbn1,dbn_4,false)[2];
	

	
	//System.out.println("MDL");
	
	start_tan_mdl= System.currentTimeMillis();
	DynamicBayesNet dbn_5 = sMDL.toDBN();
	final_tan_mdl= System.currentTimeMillis();
	per_tan_mdl[j]= compare(dbn1,dbn_5,false)[0];
	rec_tan_mdl[j]= compare(dbn1,dbn_5,false)[1];
	f1_tan_mdl[j]= compare(dbn1,dbn_5,false)[2];

	
	time_tan_ll=final_tan_ll-start_tan_ll;
	time_tan_mdl= final_tan_mdl-start_tan_mdl;
	time_ckg_ll=final_ckg_ll-start_ckg_ll;
	time_ckg_mdl= final_ckg_mdl-start_ckg_mdl;
	

}
	

	
	double mean_per_ckg_ll = Mean(per_ckg_ll);
	double mean_rec_ckg_ll = Mean(rec_ckg_ll);
	double mean_f1_ckg_ll = Mean(f1_ckg_ll);
	
	
	double sd_per_ckg_ll = StandDes(per_ckg_ll,mean_per_ckg_ll);
	double sd_rec_ckg_ll = StandDes(rec_ckg_ll,mean_rec_ckg_ll);
	double sd_f1_ckg_ll = StandDes(f1_ckg_ll,mean_f1_ckg_ll);
	
	
	double mean_per_ckg_mdl = Mean(per_ckg_mdl);
	double mean_rec_ckg_mdl = Mean(rec_ckg_mdl);
	double mean_f1_ckg_mdl = Mean(f1_ckg_mdl);
	
	double sd_per_ckg_mdl = StandDes(per_ckg_mdl,mean_per_ckg_mdl);
	double sd_rec_ckg_mdl = StandDes(rec_ckg_mdl,mean_rec_ckg_mdl);
	double sd_f1_ckg_mdl = StandDes(f1_ckg_mdl,mean_f1_ckg_mdl);
	
	
	double mean_per_ckg_mdl_ll = Mean(per_ckg_mdl_ll);
	double mean_rec_ckg_mdl_ll = Mean(rec_ckg_mdl_ll);
	double mean_f1_ckg_mdl_ll = Mean(f1_ckg_mdl_ll);
	
	double sd_per_ckg_mdl_ll = StandDes(per_ckg_mdl_ll,mean_per_ckg_mdl_ll);
	double sd_rec_ckg_mdl_ll = StandDes(rec_ckg_mdl_ll,mean_rec_ckg_mdl_ll);
	double sd_f1_ckg_mdl_ll = StandDes(f1_ckg_mdl_ll,mean_f1_ckg_mdl_ll);
	
	
	double mean_per_tan_ll = Mean(per_tan_ll);
	double mean_rec_tan_ll = Mean(rec_tan_ll);
	double mean_f1_tan_ll = Mean(f1_tan_ll);
	
	double sd_per_tan_ll = StandDes(per_tan_ll,mean_per_tan_ll);
	double sd_rec_tan_ll = StandDes(rec_tan_ll,mean_rec_tan_ll);
	double sd_f1_tan_ll = StandDes(f1_tan_ll,mean_f1_tan_ll);
	
	double mean_per_tan_mdl = Mean(per_tan_mdl);
	double mean_rec_tan_mdl = Mean(rec_tan_mdl);
	double mean_f1_tan_mdl = Mean(f1_tan_mdl);
	
	double sd_per_tan_mdl = StandDes(per_tan_mdl,mean_per_tan_mdl);
	double sd_rec_tan_mdl = StandDes(rec_tan_mdl,mean_rec_tan_mdl);
	double sd_f1_tan_mdl = StandDes(f1_tan_mdl,mean_f1_tan_mdl);
	
	
	
	sb.append(" "+n_obs[i]+" & "+ " $"+ Math.round(mean_per_tan_ll*100)+"\\pm"+Math.round(sd_per_tan_ll*100)+"$ ");
	sb.append(" & ");
	
	sb.append(" $"+ Math.round(mean_rec_tan_ll*100)+"\\pm"+Math.round(sd_rec_tan_ll*100)+"$ ");
	sb.append(" & ");
	
	sb.append(" $"+ Math.round(mean_f1_tan_ll*100)+"\\pm"+Math.round(sd_f1_tan_ll*100)+"$ ");
	sb.append(" & ");
	sb.append(" $"+ (int)time_tan_ll/1000+"$ ");
	sb.append(" & ");
	
	sb.append( " $"+ Math.round(mean_per_tan_mdl*100)+"\\pm"+Math.round(sd_per_tan_mdl*100)+"$ ");
	sb.append(" & ");
	
	sb.append(" $"+ Math.round(mean_rec_tan_mdl*100)+"\\pm"+Math.round(sd_rec_tan_mdl*100)+"$ ");
	sb.append(" & ");
	
	sb.append(" $"+ Math.round(mean_f1_tan_mdl*100)+"\\pm"+Math.round(sd_f1_tan_mdl*100)+"$ ");
	sb.append(" & ");
	sb.append(" $"+ (int)time_tan_mdl/1000+"$ ");
	sb.append(" & ");
	

	sb.append(" $"+ Math.round(mean_per_ckg_ll*100)+"\\pm"+Math.round(sd_per_ckg_ll*100)+"$ ");
	sb.append(" & ");
	
	sb.append(" $"+ Math.round(mean_rec_ckg_ll*100)+"\\pm"+Math.round(sd_rec_ckg_ll*100)+"$ ");
	sb.append(" & ");
	
	sb.append(" $"+ Math.round(mean_f1_ckg_ll*100)+"\\pm"+Math.round(sd_f1_ckg_ll*100)+"$ ");
	sb.append(" & ");
	sb.append(" $"+ (int) time_ckg_ll/1000+"$ ");
	sb.append(" & ");
	
	
	sb.append(" $"+ Math.round(mean_per_ckg_mdl*100)+"\\pm"+Math.round(sd_per_ckg_mdl*100)+"$ ");
	sb.append(" & ");
	
	sb.append(" $"+ Math.round(mean_rec_ckg_mdl*100)+"\\pm"+Math.round(sd_rec_ckg_mdl*100)+"$ ");
	sb.append(" & ");
	
	sb.append(" $"+ Math.round(mean_f1_ckg_mdl*100)+"\\pm"+Math.round(sd_f1_ckg_mdl*100)+"$ ");
	sb.append(" & ");
	sb.append(" $"+ (int) time_ckg_mdl/1000+"$ ");
	sb.append(" \\\\ ");
	
	sb.append(ls);
	
	
	System.out.println(sb.toString());
	


}
}*/
	

	}
}
	

	