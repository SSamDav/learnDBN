package dbn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import au.com.bytecode.opencsv.CSVWriter;

public class MultiNet{
	
	private List<DynamicBayesNet> networks;
	private Observations o;
	private double[][][] clustering;
	private  boolean is_bcDBN;
	private boolean is_cDBN;
	private boolean spanning;
	private int intra_ind;
	private int root;
	private int maxParents;
	
	
	/**
	 * @param o Observations to cluster
	 * @param numClusters Number of clusters 
	 */
	public MultiNet(Observations o, int numClusters, boolean is_bcDBN, boolean is_cDBN, boolean spanning, int intra_ind, int root, int maxParents) {
		super();
		this.o = o;
		int numSubjects = o.getNumSubjects();
		int numTransitions = o.getNumTransitions();
		this.is_bcDBN = is_bcDBN;
		this.is_cDBN = is_cDBN;
		this.spanning = spanning;
		this.intra_ind = intra_ind;
		this.root = root;
		this.maxParents = maxParents;
		
		Scores s;
		DynamicBayesNet dbn;
		
		networks = new ArrayList<DynamicBayesNet>(numClusters);
		clustering = new double[numTransitions][numSubjects][numClusters];
		
		for(int i = 0; i < numClusters; i++) {
			s = new Scores(o, this.maxParents, true, true);
			s.evaluate(new RandomScoringFunction());
			
			if(this.is_bcDBN) {
				System.out.println("Learning bcDBN networks.");
				dbn=s.to_bcDBN(new RandomScoringFunction(), this.intra_ind);

			}else if(this.is_cDBN) {
				System.out.println("Learning cDBN networks.");
				dbn=s.to_cDBN(new RandomScoringFunction(), this.intra_ind);
			}else {
				System.out.println("Learning tDBN networks.");
				dbn = s.toDBN(this.root, this.spanning);
			}
			
			dbn.generateParameters();
			networks.add(dbn);
		}
 		clustering = computeClusters(networks, true, false);
	}
	
	private double[][][] computeClusters(List<DynamicBayesNet> net, boolean stationaryProcess, boolean mostProbable) {
		int[][][] observations = this.o.getObservationsMatrix();
		int numSubjects = this.o.getNumSubjects();
		int numTransitions = this.o.getNumTransitions();
		int numAttributes = this.o.getAttributes().size();
		int numClusters = net.size();
		double probabilityAux;
		double probabilitySum;
		double probabilityMax;
		double[][][] newClustering = new double[numTransitions][numSubjects][numClusters];
		int cluster;
		double decimal_places = 5;
		double epsilon = Math.pow(10, -decimal_places);
		int max_cluster;
		double[] alpha = getAlpha(clustering);

		
		for(int s = 0; s < numSubjects; s++) {
			probabilityMax = Double.NEGATIVE_INFINITY;
			cluster = 0;
			max_cluster = 0;
			for(DynamicBayesNet dbn : net) {
				probabilityAux = 0;
				for(int t = 0; t < numTransitions; t++) {
					for(int n = 0; n < numAttributes; n++) {
						if(stationaryProcess) {
							probabilityAux += Math.log(dbn.transitionNets.get(0).getParameters(n, observations[t][s]).get(0));
						}else{
							probabilityAux += Math.log(dbn.transitionNets.get(t).getParameters(n, observations[t][s]).get(0));
						}
					}
				}
				
				for(int t = 0; t < numTransitions; t++) {
					newClustering[t][s][cluster] = probabilityAux;
				}
				if(probabilityMax < probabilityAux) {
					probabilityMax = probabilityAux;
					max_cluster = cluster;
				}
				cluster ++;
			}
			
			for(int c = 0; c < numClusters; c++) {
				for(int t = 0; t < numTransitions; t++) {
					if(newClustering[t][s][c] - probabilityMax >= Math.log(epsilon) - Math.log(numClusters)){
						newClustering[t][s][c] = Math.exp(newClustering[t][s][c] - probabilityMax);
					}else {
						newClustering[t][s][c] = 0;
					}
				}
			}
			
			probabilitySum = 0;
			for(int c = 0; c < numClusters; c++) {
				probabilitySum += alpha[c] * (double)Math.ceil(newClustering[0][s][c] * 1000000000000000d) / 1000000000000000d;
				
			}
			for(int c = 0; c < numClusters; c++) {
				for(int t = 0; t < numTransitions; t++) {
					newClustering[t][s][c] *= alpha[c]/probabilitySum;
				}
			}
			
			if(mostProbable) {
				for(int c = 0; c < numClusters; c++) {
					for(int t = 0; t < numTransitions; t++) {
						newClustering[t][s][c] = 0;
						if(c == max_cluster)
							newClustering[t][s][c] = 1;
					}
				}
			}
		}
		return newClustering;
	}
	
	private double[] getAlpha(double[][][] counts) {
		double[] alpha;
		int numSubjects = o.getNumSubjects();
		int numClusters = networks.size();
		double subSum;
		double sum = 0;
		
		alpha = new double[numClusters];
		for(int c = 0; c < numClusters; c++) {
			subSum = 0;
			for(int s = 0; s < numSubjects; s++) {
				subSum += counts[0][s][c];
			}
			alpha[c] =  subSum/numSubjects;
			sum += alpha[c];
		}
		if(sum == 0) {
			for(int c = 0; c < numClusters; c++) {
				alpha[c] = 1.0/numClusters;
			}
		}
		
		return alpha;
	}
	
	private double[][] selectCluster(double[][][] counts, int cluster){
		int numSubjects = o.getNumSubjects();
		int numTransitions = o.getNumTransitions();
		double[][] clusteringNew = new double[numTransitions][numSubjects];
		
		for(int t = 0; t < numTransitions; t++) {
			for(int s = 0; s < numSubjects; s++) {
				clusteringNew[t][s] = counts[t][s][cluster];
			}
		}
		return clusteringNew;
	}
	
	
	
	public List<DynamicBayesNet> getNetworks() {
		return networks;
	}

	private List<DynamicBayesNet> trainNetworks(double[][][] counts ) {
		Scores s;
		DynamicBayesNet dbn;
		Observations oNew;
		double[][] clust;
		int numClusters = networks.size();
		int[][][] obs = o.getObservationsMatrix();
		List<Attribute> attributes = o.getAttributes();
		List<DynamicBayesNet> networksNew = new ArrayList<DynamicBayesNet>(numClusters);
		
		
		for(int c = 0; c < numClusters; c++) {
			clust = selectCluster(counts, c);
			oNew = new Observations(attributes, obs, clust);
			s =  new Scores(oNew, this.maxParents, true, true);
			s.evaluate(new LLScoringFunction());
			if(this.is_bcDBN) {
				dbn=s.to_bcDBN(new LLScoringFunction(), this.intra_ind);

			}else if(this.is_cDBN) {
				dbn=s.to_cDBN(new LLScoringFunction(), this.intra_ind);
			}else {;
				dbn = s.toDBN(this.root, this.spanning);
			}
			dbn.learnParameters(oNew, true);
			networksNew.add(dbn);
		}
		return networksNew;	
	}
	
	public void clust() {
		int numClusters = networks.size();
		List<DynamicBayesNet> networkPrev = networks;
		List<DynamicBayesNet> networkNew = networks;
		double[][][] counts = clustering;
		double score = getScore(networkNew, counts);
		double score_prev = Double.NEGATIVE_INFINITY;
		boolean mostprobable = false;
		int it = 0;
		//System.out.println("Score: " + score);
		while(score > score_prev){
			if(it >= 100 & !mostprobable)
				mostprobable = true;
			networkPrev = networkNew;
			score_prev = score;
			networkNew = trainNetworks(counts);
			counts = computeClusters(networkNew, true, mostprobable);
			score = getScore(networkNew, counts);
			it += 1;
			//System.out.println("Score: " + score);
		}
		this.networks = networkPrev;
		this.clustering = computeClusters(networkPrev, true, false);
	}
	
	public double getScore(List<DynamicBayesNet> net, double[][][] clustering){
		Observations oNew;
		int numSubjects = o.getNumSubjects();
		int[][][] obs = o.getObservationsMatrix();
		List<Attribute> attributes = o.getAttributes();
		int numClusters = networks.size();
		double[] alpha;
		double[][] clust;
		double netscore1 = 0;
		double netscore2 = 0;
		int c = 0;
		double count;
		
		alpha = getAlpha(clustering);
		for(DynamicBayesNet dbn : net) {
			clust = selectCluster(clustering, c);
			oNew = new Observations(attributes, obs, clust);
			netscore1 += dbn.getScore(oNew, new LLScoringFunction(), true);
			c += 1;
		}
		
		for(c = 0; c < numClusters; c++) {
			count = 0;
			for(int s = 0; s < numSubjects; s++) {
				count += clustering[0][s][c];
			}
			netscore2 += count * Math.log(alpha[c]);
		}
		
		return netscore1 + netscore2;
	}
	
	public void writeToFile(String outFileName) {
		CSVWriter writer;
		int cluster = 0;
		double prob;
		double[] probs;
		int numCluster = this.networks.size();
		Map<String, boolean[]> subjectIsPresent = this.o.getSubjectIsPresent();

		try {
			
			File outputFile = new File(outFileName);
			outputFile.createNewFile();
			
			writer = new CSVWriter(new FileWriter(outputFile));

			
			int numSubjects = this.o.getNumSubjects();

			// compose header line
			List<String> headerEntries = new ArrayList<String>(2);
			headerEntries.add("subject_id");
			headerEntries.add("Class");

			// write header line to file
			writer.writeNext(headerEntries.toArray(new String[0]));

			// iterator over subject ids
			Iterator<String> subjectIterator = subjectIsPresent.keySet().iterator();

			int passiveSubject = -1;
			for (int s = 0; s < numSubjects; s++) {
				prob = Double.NEGATIVE_INFINITY;
				probs = this.clustering[0][s];
				for( int c = 0; c < numCluster; c++) {
					if(prob < probs[c]) {
						cluster = c;
						prob = probs[c];
					}
				}
				
				List<String> subjectEntries = new ArrayList<String>(2);

				// add subject id
				while (subjectIterator.hasNext()) {
					String subject = subjectIterator.next();
					passiveSubject++;
					if (subjectIsPresent.get(subject)[0]) {
						subjectEntries.add(subject);
						break;
					}
				}

				subjectEntries.add(Integer.toString(cluster));


				// write subject line to file
				writer.writeNext(subjectEntries.toArray(new String[0]));

			}

			writer.close();

		} catch (IOException e) {
			System.err.println("Could not write to " + outFileName + ".");
			e.printStackTrace();
			System.exit(1);
		}

	}
	
	@Override
	public String toString() {
		int numClusters = networks.size();
		int numSubjects = o.getNumSubjects();
		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");
		
		sb.append("Number of clusters : " + numClusters + ls);
		sb.append("Number of Observations : " + numSubjects + ls + ls);
		

		double[] alpha = getAlpha(clustering);
		for(int c = 0; c < numClusters; c++) {
			sb.append("--- Cluster " + c + " ---" + ls);
//			for(int s = 0; s < numSubjects; s++) {
//				sb.append(clustering[0][s][c] + ls);
//			}
			sb.append(networks.get(c).toString());
			sb.append(ls + "Alpha: " + alpha[c] + ls);
		}
		
//		double score = getScore(networks, clustering);
//		sb.append(ls + "Final Score: " + score + ls);
		
		return sb.toString();	
	}
	
	public static void main(String args[]) {
		Options options = new Options();

		Option inputFile = Option.builder("i")
				.hasArg()
				.required(true)
				.desc("Folder of the dataset.")
				.argName("path")
				.longOpt("inputFile")
				.build();
		Option numParents = Option.builder("n")
				.longOpt("numClusters")
				.desc("Number of clusters.")
				.required(true)
				.hasArg()
				.argName("int")
				.build();
		
		
		options.addOption(inputFile);
		options.addOption(numParents);
		
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd;
//		try {
//			cmd = parser.parse(options, args);
//			int numCluster = Integer.parseInt(cmd.getOptionValue("n"));
//			File destinationLocation = new File(cmd.getOptionValue("i"));
//			
//			String path = destinationLocation.getPath();
//			path += File.separator + "combinedDataset.csv";
//			Observations o = new Observations(path, null, 1);
//			//System.out.print(o);
//			//MultiNet m = new MultiNet(o, numCluster);
//			long startTime = System.nanoTime();
//			m.clust();
//			long endTime = System.nanoTime();
//			long duration = (endTime - startTime)/1000000;
//			System.out.println(duration);
//			//System.out.print(m);
//			path = destinationLocation.getPath();
//			path += File.separator + "output.csv";
//			m.writeToFile(path);
//		} catch (ParseException e) {
//			// TODO Auto-generated catch block
//			HelpFormatter formatter = new HelpFormatter();
//			formatter.printHelp("Multinet", options);
//			System.out.println(e);
//		}
		
	}
	
}