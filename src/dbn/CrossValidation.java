package dbn;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import utils.Utils;

public class CrossValidation {

	long randomSeed = new Random().nextLong();
	private Random r = new Random(randomSeed);

	private Observations o;

	private int[][] allData;

	private String[][] allPassiveData;

	// contains an extra column for fold identification
	private List<int[][]> stratifiedData;

	private List<String[][]> stratifiedPassiveData;

	private int numFolds;

	private class Pair {
		int a, b;

		private Pair(int a, int b) {
			this.a = a;
			this.b = b;
		}
	}

	public CrossValidation setRandomSeed(long randomSeed) {
		this.randomSeed = randomSeed;
		r.setSeed(randomSeed);
		return this;
	}

	public long getRandomSeed() {
		return randomSeed;
	}

	private Pair countInstancesOfFold(int fold) {
		int n = o.numAttributes();
		int m = o.getMarkovLag();
		int countFold = 0;
		int countNonFold = 0;
		for (int c = 0; c < stratifiedData.size(); c++) {
			for (int[] row : stratifiedData.get(c)) {
				if (row[(m + 1) * n] == fold)
					countFold++;
				else
					countNonFold++;
			}
		}

		return new Pair(countFold, countNonFold);
	}

	private List<Integer> calculateFoldIds(int numInstances, int numFolds) {
		List<Integer> foldIds = new ArrayList<Integer>(numInstances);

		int minFoldSize = numInstances / numFolds;
		int rest = numInstances % numFolds;

		for (int i = 0; i < numFolds; i++)
			for (int j = 0; j < minFoldSize; j++)
				foldIds.add(i);

		for (int i = 0; i < rest; i++)
			foldIds.add(i);

		Collections.shuffle(foldIds, r);

		return foldIds;
	}

	private int countInstancesOfClass(int classAttribute, int value) {
		int n = o.numAttributes();
		int m = o.getMarkovLag();
		int count = 0;
		for (int i = 0; i < allData.length; i++)
			if (allData[i][m * n + classAttribute] == value)
				count++;
		return count;
	}

	public CrossValidation(Observations o, int numFolds, Integer classAttribute) {

		this.o = o;

		// initialize allData
		int N = o.numObservations(-1);
		int n = o.numAttributes();
		int T = o.numTransitions();
		int m = o.getMarkovLag();
		int nPassive = o.numPassiveAttributes();

		allData = new int[N][(m + 1) * n];

		allPassiveData = new String[N][(m + 1) * nPassive];

		int[][][] usefulObservations = o.getObservationsMatrix();
		String[][][] passiveObservations = o.getPassiveObservationsMatrix();
		int i = 0;
		for (int t = 0; t < T; t++)
			for (int j = 0; j < o.numObservations(t); j++) {
				allData[i] = usefulObservations[t][j];
				allPassiveData[i] = passiveObservations[t][j];
				i++;
			}

		// stratify data
		if (classAttribute != null) {

			int classRange = o.getAttributes().get(classAttribute).size();

			stratifiedData = new ArrayList<int[][]>(classRange);

			stratifiedPassiveData = new ArrayList<String[][]>(classRange);

			for (int c = 0; c < classRange; c++) {
				stratifiedData.add(new int[countInstancesOfClass(classAttribute, c)][(m + 1) * n + 1]);
				stratifiedPassiveData.add(new String[countInstancesOfClass(classAttribute, c)][(m + 1) * nPassive]);
				int[][] classData = stratifiedData.get(c);
				String[][] classPassiveData = stratifiedPassiveData.get(c);
				i = 0;
				for (int j = 0; j < allData.length; j++) {
					int[] row = allData[j];
					if (row[m * n + classAttribute] == c) {
						classData[i] = Arrays.copyOf(row, (m + 1) * n + 1);
						classPassiveData[i] = Arrays.copyOf(allPassiveData[j], (m + 1) * nPassive);
						i++;
					}
				}

			}
		}

		else {
			stratifiedData = new ArrayList<int[][]>(1);
			stratifiedPassiveData = new ArrayList<String[][]>(1);

			stratifiedData.add(new int[N][(m + 1) * n + 1]);
			int[][] data = stratifiedData.get(0);

			stratifiedPassiveData.add(allPassiveData);

			for (int j = 0; j < allData.length; j++)
				data[j] = Arrays.copyOf(allData[j], (m + 1) * n + 1);
		}

		// determining folds
		this.numFolds = numFolds;
		if (numFolds > 0) {
			List<Integer> foldIds = calculateFoldIds(N, numFolds);

			i = 0;
			for (int[][] classData : stratifiedData)
				for (int j = 0; j < classData.length; j++)
					classData[j][(m + 1) * n] = foldIds.get(i++);
		}

	}

	private Observations evaluateFold(Observations train, Observations test, int numParents, ScoringFunction s,
			boolean dotOutput, String dotFileName, boolean mostProbable, boolean ckg,int k) {
		// System.out.println("initializing scores");
		Scores s1 = new Scores(train, numParents, true, true);
		// System.out.println("evaluating scores");
		s1.evaluate(s);
		DynamicBayesNet dbn1;

		if(ckg) {	
			dbn1=s1.to_bcDBN(s,k);

		}

		else {
			dbn1 = s1.toDBN();
		}
		
		
		//System.out.println("Attributes "+dbn1.getAttributes());

		// System.out.println("converting to DBN");

		if (dotOutput) {
			try {
				Utils.writeToFile(dotFileName + ".dot", dbn1.toDot(false));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}

		// System.out.println("learning DBN parameters");
		@SuppressWarnings("unused")
		String params = dbn1.learnParameters(train, true);

		if (dotOutput) {
			// for (Attribute a : train.getAttributes()) {
			// System.out.print(a.getName() + ": ");
			// System.out.println(a);
			// }

			// System.out.println(params);
			return null;
		}

		else {
			//System.out.println("testing network");
			return dbn1.forecast(test, 1, true, mostProbable);
		}

	}
	
	

	
	
	public String evaluate2(int numParents, ScoringFunction s, String outputFileName, List<Integer> forecastAttributes,
			boolean mostProbable, boolean ckg,int k_ckg) {
		

		int n = o.numAttributes();
		int nPassive = o.numPassiveAttributes();
		int m = o.getMarkovLag();
		int[][][] trainingData_0;
		int[][][] trainingData_1;
		int[][][] testData;
		String[][] testPassiveData;
		

		StringBuilder output = new StringBuilder();
		String ls = System.getProperty("line.separator");

		output.append(randomSeed + ls);
		for (int predictor : forecastAttributes)
			output.append(o.getAttributes().get(predictor).getName() + "\t");
		output.append("\t" + "actual_value" + ls);

		double accu=0;
		double pre =0;
		double rec=0;
		double auc=0;

		for (int f = 0; f < numFolds; f++) {
			
			
		double accu_f=0;
		double pre_f =0;
		double rec_f=0;
		double auc_f=0;
		
			double[] clas= new double[4];
			

			int fold = f + 1;
			System.out.println("Fold " + fold);

			Pair counts = countInstancesOfFold(f);
			int testSize = counts.a;
			int trainingSize = counts.b;

			trainingData_0 = new int[1][trainingSize][(m + 1) * n];
			
			trainingData_1 = new int[1][trainingSize][(m + 1) * n];
			
			
			testData = new int[1][testSize][(m + 1) * n];
			testPassiveData = new String[testSize][(m + 1) * nPassive];

			System.out.println("Training size: " + trainingSize + "\t" + "Test size: " + testSize);
			
			
			System.out.println("size stratified data "+stratifiedData.size());

			int i = 0;
			int j = 0;
			for (int c = 0; c < stratifiedData.size(); c++) {
				int[][] classData = stratifiedData.get(c);
				String[][] classPassiveData = stratifiedPassiveData.get(c);
				for (int k = 0; k < classData.length; k++) {
					int[] row = classData[k];
					if (row[(m + 1) * n] == f) {
						testData[0][i] = Arrays.copyOf(row, (m + 1) * n);
						testPassiveData[i] = Arrays.copyOf(classPassiveData[k], (m + 1) * nPassive);
						i++;
					} else
						if(c==0) trainingData_0[0][j++] = Arrays.copyOf(row, (m + 1) * n);
						else trainingData_1[0][j++]=Arrays.copyOf(row, (m + 1) * n);
				}
			}
			
			o.change0();

			Observations train_0 = new Observations(o, trainingData_0);
			Observations train_1 = new Observations(o, trainingData_1);
			
			Observations test = new Observations(o, testData);

			//Observations forecast = evaluateFold(train, test, numParents, s,false, null, mostProbable,ckg,k_ckg);
			
			
			Scores s0 = new Scores(train_0, numParents, true, true);
			
			Scores s1 = new Scores(train_1, numParents, true, true);
			
			
			s0.evaluate(s);
			s1.evaluate(s);
			
			DynamicBayesNet dbn0;
			
			DynamicBayesNet dbn1;
			
			// System.out.println("evaluating scores");
			//s1.evaluate(s);
			

			if(ckg) {	
				
				// Criar duas redes: transição 0=>0 e 0=>1
				
				
				dbn0=s0.to_bcDBN(s,k_ckg);
				
				dbn1=s1.to_bcDBN(s,k_ckg);

			}

			else {
				dbn0 = s0.toDBN();
				dbn1 = s1.toDBN();
			}
			
			
			
			System.out.println(dbn0.toString());
			System.out.println(dbn1.toString());
			
			
			dbn0.learnParameters(train_0);
			dbn1.learnParameters(train_1);
			
			
			
			
			//System.out.println(dbn0.transitionNets.get(0).getParameters().get(0));
			
			
			
			System.out.println("-----------------------------------------------");
			
			
			//System.out.println(dbn1.transitionNets.get(0).getParameters().get(0));
			
			
			
			
		
		
		
			 output.append("---Fold-" + fold + "---" + ls);
			 
			 Double p0=(double)1;
			 
			 Double p1=(double)1;
			 
			 
			 
			for (i = 0; i < testSize; i++) {
				
	MutableConfiguration c0 = new MutableConfiguration(dbn0.getAttributes(),1,Arrays.copyOfRange(testData[0][i],0,18));

				
				//for(BayesNet BN:dbn0.getTrans()) {
					
					//System.out.println(BN.getTop());
	
	
	System.out.println(dbn0.getInit().toString());
	
	
									
					for(int node:dbn0.getInit().getTop()) {
						
						System.out.println("Node "+node);
						
						
						
						
						System.out.println("Attributes "+dbn0.getAttributes());
						
						
						
						Configuration indexParameters = c0.applyMask(dbn0.getInit().getParents().get(node), node);
						
						
						System.out.println("indexParameters "+indexParameters);
						
						
						System.out.println("One " + Arrays.toString(Arrays.copyOfRange(testData[0][i],0,18)));
						//System.out.println("Two " + Arrays.toString(Arrays.copyOfRange(testData[0][i],18,36)));
						//System.out.println("Three " + Arrays.toString(Arrays.copyOfRange(testData[0][i],18,34)));
						//System.out.println("Four " + Arrays.toString(Arrays.copyOfRange(testData[0][i],18,37)));
						System.out.println(dbn0.getInit().getParameters().get(node).get(indexParameters));
						
						
						//Double probability = BN.getParameters().get(node).get(indexParameters).get(testData[0][i][18+node]);
						
						//p0=p0*probability;
						
						
					}
				
				
				
				
				
				//}
				
				
				
			MutableConfiguration c1 = new MutableConfiguration(dbn1.getAttributes(), 1,testData[0][i]);

	
				
				for(BayesNet BN:dbn1.getTrans()) {
				
					for(int node:BN.getTop()) {
						
						
						Configuration indexParameters = c1.applyMask(BN.getParents().get(node), node);
						
						
						
						
						Double probability = BN.getParameters().get(node).get(indexParameters).get(testData[0][i][node]);
						
						p1=p1*probability;
						
						
					}
				
				
				
				
				
				}
				
				int p=-1;
				
				
				
				if(p0>=p1) p=0;
				
				else p=1;

				
				output.append(p + "\t");

					
				
					output.append("\t");
					int t=-1;
					//System.out.println("t "+testPassiveData[i][m * nPassive + 0]);
					output.append(testPassiveData[i][m * nPassive + 0] + "\t");
					
					t =Integer.parseInt(testPassiveData[i][m * nPassive + 0]);
		

				if(t==p && t==2) {
					clas[0]+=1;
				}
				
				if(t==p && t==1) {
					clas[1]+=1;
				}
				
				if(t!=p && t==1) {
					clas[2]+=1;
				}
				
				
				if(t!=p && t==2) {
					clas[3]+=1;
				}
				
				

				output.append(ls);
			}
				
			System.out.println("class  "+Arrays.toString(clas));
			
		
			
			
			accu_f= (clas[0]+clas[1])/(clas[0]+clas[1]+clas[2]+clas[3]);
			pre_f= clas[0]/(clas[0]+clas[2]);
			rec_f=clas[0]/(clas[0]+clas[3]);
			auc_f=0.5*(clas[0]/(clas[0]+clas[3])+clas[1]/(clas[1]+clas[2]));
			
			System.out.println("ACC "+accu_f);
			System.out.println("PRE "+pre_f);
			System.out.println("REC "+rec_f);
			System.out.println("AUC "+auc_f);
			
			
			accu+=accu_f;
			pre+=pre_f;	
			rec+=rec_f;
			auc+=auc_f;
				
				

		}
			accu=accu/10;
			pre=pre/10;
			rec=rec/10;
			auc=auc/10;
			
			System.out.println("Accuracy "+accu);
			System.out.println("Precision "+pre);
			System.out.println("Recall "+rec);
			System.out.println("AUC "+auc);
			
		
				
		// use all data for training and produce network graph

		/*System.out.println("---All-data---");
		Observations train = o;
		evaluateFold(train, null, numParents, s, true, outputFileName, mostProbable,ckg,k_ckg);
		output.append(ls);

		// output true values for baseline classifier
		for (int i = 0; i < allData.length; i++) {
			for (int j = 0; j < allPassiveData[0].length; j++)
				output.append(allPassiveData[i][j] + "\t");
			output.append(ls);
		}*/

		return output.toString();
			
			
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	public String evaluate(int numParents, ScoringFunction s, String outputFileName, List<Integer> forecastAttributes,
			boolean mostProbable, boolean ckg,int k_ckg) {
		

		int n = o.numAttributes();
		int nPassive = o.numPassiveAttributes();
		int m = o.getMarkovLag();
		int[][][] trainingData;
		int[][][] testData;
		String[][] testPassiveData;
		
		//double[] out = new double[3];
		

		//System.out.println("Random seed: " + randomSeed);
		//System.out.println("Number of observations: " + o.numObservations(-1));

		StringBuilder output = new StringBuilder();
		String ls = System.getProperty("line.separator");

		output.append(randomSeed + ls);
		for (int predictor : forecastAttributes)
			output.append(o.getAttributes().get(predictor).getName() + "\t");
		output.append("\t" + "actual_value" + ls);

		//System.out.println("Number of folds "+numFolds);
		
		
		
		

		
		double accu=0;
		double pre =0;
		double rec=0;
		double auc=0;

		for (int f = 0; f < numFolds; f++) {
			
			
		double accu_f=0;
		double pre_f =0;
		double rec_f=0;
		double auc_f=0;
		
			double[] clas= new double[4];
			

			int fold = f + 1;
			System.out.println("Fold " + fold);

			Pair counts = countInstancesOfFold(f);
			int testSize = counts.a;
			int trainingSize = counts.b;

			trainingData = new int[1][trainingSize][(m + 1) * n];
			testData = new int[1][testSize][(m + 1) * n];
			testPassiveData = new String[testSize][(m + 1) * nPassive];

			System.out.println("Training size: " + trainingSize + "\t" + "Test size: " + testSize);

			int i = 0;
			int j = 0;
			for (int c = 0; c < stratifiedData.size(); c++) {
				int[][] classData = stratifiedData.get(c);
				String[][] classPassiveData = stratifiedPassiveData.get(c);
				for (int k = 0; k < classData.length; k++) {
					int[] row = classData[k];
					if (row[(m + 1) * n] == f) {
						testData[0][i] = Arrays.copyOf(row, (m + 1) * n);
						testPassiveData[i] = Arrays.copyOf(classPassiveData[k], (m + 1) * nPassive);
						i++;
					} else
						trainingData[0][j++] = Arrays.copyOf(row, (m + 1) * n);
				}
			}
			
	

			Observations train = new Observations(o, trainingData);
			Observations test = new Observations(o, testData);

			Observations forecast = evaluateFold(train, test, numParents, s,false, null, mostProbable,ckg,k_ckg);

		
		
			 output.append("---Fold-" + fold + "---" + ls);
			 
			 
			 
			for (i = 0; i < testSize; i++) {
				
				boolean b =true;
				int[][][] fMatrix = forecast.getObservationsMatrix();
				int p=0;
				//for (int predictor : forecastAttributes) {
					//System.out.println("predictor "+predictor);
				
				
					output.append(o.getAttributes().get(17).get(fMatrix[0][i][m * n + 17]) + "\t");
					
					if(o.getAttributes().get(17).get(fMatrix[0][i][m * n + 17])==null) b=false;
										
					//System.out.println("p "+o.getAttributes().get(17).get(fMatrix[0][i][m * n + 17]));
					if(b) {
						p=(int)Double.parseDouble(o.getAttributes().get(17).get(fMatrix[0][i][m * n + 17]));
					
					}
					
					
					//System.out.println("p after "+p);
					
				
					output.append("\t");
					int t=0;
					//System.out.println("t "+testPassiveData[i][m * nPassive + 0]);
					output.append(testPassiveData[i][m * nPassive + 0] + "\t");
					
					
					if(testPassiveData[i][m * nPassive + 0]==null) b=false;
						
	
					
					
					
					
					
				//for (j = 0; j < nPassive; j++) {
					
					
					//if(testPassiveData[i][m * nPassive + 0]==null) b=false;
				if(b) {
					t =Integer.parseInt(testPassiveData[i][m * nPassive + 0]);//}
					
					
				}
				
				//System.out.println("t after "+t);
				
				//System.out.println("----------------------------------------");
				
				
				if(b) {
				if(t==p && t==2) {
					clas[0]+=1;
				}
				
				if(t==p && t==1) {
					clas[1]+=1;
				}
				
				if(t!=p && t==1) {
					clas[2]+=1;
				}
				
				
				if(t!=p && t==2) {
					clas[3]+=1;
				}
				
				}

				output.append(ls);
			}
				
			System.out.println("class  "+Arrays.toString(clas));
			
		
			
			
			accu_f= (clas[0]+clas[1])/(clas[0]+clas[1]+clas[2]+clas[3]);
			pre_f= clas[0]/(clas[0]+clas[2]);
			rec_f=clas[0]/(clas[0]+clas[3]);
			auc_f=0.5*(clas[0]/(clas[0]+clas[3])+clas[1]/(clas[1]+clas[2]));
			
			System.out.println("ACC "+accu_f);
			System.out.println("PRE "+pre_f);
			System.out.println("REC "+rec_f);
			System.out.println("AUC "+auc_f);
			
			
			accu+=accu_f;
			pre+=pre_f;	
			rec+=rec_f;
			auc+=auc_f;
				
				

		}
			accu=accu/10;
			pre=pre/10;
			rec=rec/10;
			auc=auc/10;
			
			System.out.println("Accuracy "+accu);
			System.out.println("Precision "+pre);
			System.out.println("Recall "+rec);
			System.out.println("AUC "+auc);
			
		
				
		// use all data for training and produce network graph

		/*System.out.println("---All-data---");
		Observations train = o;
		evaluateFold(train, null, numParents, s, true, outputFileName, mostProbable,ckg,k_ckg);
		output.append(ls);

		// output true values for baseline classifier
		for (int i = 0; i < allData.length; i++) {
			for (int j = 0; j < allPassiveData[0].length; j++)
				output.append(allPassiveData[i][j] + "\t");
			output.append(ls);
		}*/

		return output.toString();
			
			
	}
	
	public static void main(String[] args) {

		int p = 1;
		int m = 1;
		int k=4;
		ScoringFunction s = new LLScoringFunction();
		int folds = 10;
		Integer classAttribute =17;
		//0,1, 2, 4, 5,6,7,8,9,10,11,12,13,14,15,16
		List<Integer> forecastAttributes = Arrays.asList(17);
		
		//"trial5-horizontal.csv", "das-horizontal.csv", m
		
		//"/home/margarida/Documents/NEUROCLIMICS2/data_disc_attr.csv","/home/margarida/Documents/NEUROCLIMICS2/data_disc_class.csv",

		Observations o = new Observations("/home/margarida/Documents/NEUROCLIMICS2/data_disc.csv","/home/margarida/Documents/NEUROCLIMICS2/data_disc_class.csv", m);

		CrossValidation cv = new CrossValidation(o, folds, classAttribute);

		System.out.println(cv.evaluate2(p, s, "tDBN_p=2_ll", forecastAttributes, true,true,k));

	}
	
	
	
	
	
}