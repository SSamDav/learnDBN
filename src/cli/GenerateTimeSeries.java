package cli;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import dbn.*;
import utils.GraphViz;

public class GenerateTimeSeries{
	public static void main(String args[]) {
		
		Options options = new Options();
		
		Option numAttri = Option.builder("a")
				.hasArg()
				.required(true)
				.desc("Number of attributes of the network.")
				.argName("numAttri")
				.longOpt("numAttributes")
				.build();
		Option numTime = Option.builder("t")
				.hasArg()
				.required(true)
				.desc("Number of time points of the timeseries.")
				.argName("numTime")
				.longOpt("numTimepoints")
				.build();
		
		Option numObs = Option.builder("s")
				.hasArg()
				.required(true)
				.desc("Number of timeseries to be generated.")
				.argName("numObs")
				.longOpt("numObservations")
				.build();
		
		Option numNet = Option.builder("n")
				.hasArg()
				.desc("Number of DBN to be generated.  If not supplied, it will be generated just one DBN.")
				.argName("numNet")
				.longOpt("numNetworks")
				.build();
		
		Option alphaSize = Option.builder("v")
				.hasArg()
				.desc("Cardinality of the attributes.  If not supplied, it will be 2.")
				.argName("alphaSize")
				.longOpt("alphabetSize")
				.build();
		
		
		Option dotFormat = Option.builder("d")
				.longOpt("dotFormat")
				.desc("Outputs network in dot format, allowing direct redirection into Graphviz to visualize the graph.")
				.build();


		Option compact = Option.builder("c")
				.longOpt("compact")
				.desc("Outputs network in compact format, omitting intra-slice edges. Only works if specified together with -d and with --markovLag 1.")
				.build();


		Option folder = Option.builder("f")
				.hasArg()
				.desc("Writes the output file in <folder>. If not supplied, the output file will be writted in the current folder.")
				.argName("folder")
				.longOpt("outputFolder")
				.build();
		
		Option outputFile = Option.builder("o")
				.longOpt("outputFile")
				.desc("Writes output to <file>. If not supplied, the output file will have the name GeneratedObs[<numNet>].csv.")
				.hasArg()
				.argName("file")
				.build();
		
		
		
		
		
		
		options.addOption(numAttri);
		options.addOption(numTime);
		options.addOption(numObs);
		options.addOption(numNet);
		options.addOption(alphaSize);
		options.addOption(dotFormat);
		options.addOption(compact);
		options.addOption(folder);
		options.addOption(outputFile);
		
		CommandLineParser parser = new DefaultParser();
		
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
			String fileName ;
			String outputFolder = cmd.getOptionValue("f","");
			int numberNet = Integer.parseInt(cmd.getOptionValue("n","1"));
			int alpha = Integer.parseInt(cmd.getOptionValue("v","2"));
			int numAttributes = Integer.parseInt(cmd.getOptionValue("a"));
			int numObservations = Integer.parseInt(cmd.getOptionValue("s"));
			int numTimepoints = Integer.parseInt(cmd.getOptionValue("t"));
			int markovLag = 1;
			
			Observations o;
			Scores s;
			DynamicBayesNet dbn;
			String output;
			String path;
			int[][][] obs;
			double[][] counts;
			Random rand = new Random();
			BufferedWriter writer = null;
			File directory;
			
			if(cmd.hasOption("f")) {
				directory = new File(outputFolder);
				directory.mkdir();
			}
			
			
			List<Attribute> a = new ArrayList<Attribute>();
			int randomNum;
			for(int i = 1; i <= numAttributes; i++) {
				Attribute a_aux = new NumericAttribute();
				Set<Integer> val = new HashSet<Integer>();
				a_aux.setName("X"+i);
				for(int t = 1; t <= alpha; t++) {
					do {
						randomNum = rand.nextInt((100 - 1) + 1) + 1;	
					}while(!val.add(randomNum));
					a_aux.add("" + randomNum);
				}
				a.add(a_aux);
				
			}
			for(int numDBN = 0; numDBN < numberNet; numDBN++) {
					
				obs = new int[1][1][1];
				counts = new double[1][1];
				o = new Observations(a, obs, counts);
				s = new Scores(o, 1, true, true);
				s.evaluate(new RandomScoringFunction());
				dbn = s.toDBN(-1, false, true);
				dbn.generateParameters();
				
				if (cmd.hasOption("d")) {
					if (cmd.hasOption("c") && markovLag == 1)
						output = dbn.toDot(true);
					else
						output = dbn.toDot(false);
				
					GraphViz gv = new GraphViz();
					String type = "png";
					String repesentationType= "dot";
					path = String.format("\\DBN[%d].",numDBN);
					fileName = cmd.getOptionValue("o", path);
					File out = new File(outputFolder + "//" + fileName + "." + type);
					gv.writeGraphToFile( gv.getGraph(output, type, repesentationType), out );
					try {
						path = String.format("\\DBN[%d]",numDBN);
						fileName = cmd.getOptionValue("o", path);
						File logFile = new File(outputFolder + "//" + fileName + ".txt");
						writer = new BufferedWriter(new FileWriter(logFile));
						writer.write(output); 
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}finally {
						try {
			                // Close the writer regardless of what happens...
			                writer.close();
			            } catch (Exception e) {
			            }
		
					}
				}
				o = dbn.generateObservations(numObservations, numTimepoints, true);
				System.out.println("Generated Network:");
				System.out.println("---Attributes--");
				for(Attribute at : a) {
					System.out.println(at.getName() + ": " + at);
				}
				System.out.println(dbn);
				path = String.format("GeneratedObs[%d]", numDBN);
				fileName = cmd.getOptionValue("o", path);
				o.writeToFile(outputFolder + "//" + fileName + ".csv");
//				for(int numMissing : n_missing) {
//					for(int numVariable : n_variable) {
//						oNew = o.generateMissingValues(numMissing, numVariable);
//						path = String.format("\\GeneratedObs[%d,%d,%d,%d,%d].csv", n_attributes, numObs, time, numMissing, numVariable);
//						oNew.writeToFile(args[0] + path);
//					}
//				}
				}
			
			
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("GenerateTimeSeries", options);
			System.out.println(e);
		}
		
		
		
		
		
////		Network: 
////			X1[0] -> X1[1]
////			X2[0] -> X2[1]
////			X3[0] -> X3[1]
////			X1[1] -> X2[1]
////			X1[1] -> X3[1]
//		
//		Attribute a1  = new NumericAttribute();
//		a1.setName("X1");
//		a1.add("4");
//		a1.add("7");
//		
//		Attribute a2 = new NumericAttribute();
//		a2.setName("X2");
//		a2.add("20");
//		a2.add("40");
//		
//		Attribute a3 = new NumericAttribute();
//		a3.setName("X3");
//		a3.add("5");
//		a3.add("9");
//		
//		List<Attribute> a = Arrays.asList(a1,a2,a3);
//		
//		Edge e12 = new Edge(0,1);
//		Edge e13 = new Edge(0,2);
//		Edge e11 = new Edge(1,0);
//		Edge e22 = new Edge(2,1);
//		Edge e33 = new Edge(0,2);
//		
//		List<Edge> prior = new ArrayList<Edge>();
//		
//		BayesNet b0 = new BayesNet(a, prior);
//		b0.generateParameters();
//		
//		List<Edge> intra = Arrays.asList(e12, e13);
//		List<Edge> inter = Arrays.asList(e11, e22, e33);
//		
//		BayesNet bt = new BayesNet(a, intra, inter);
//		bt.generateParameters();
//		DynamicBayesNet dbn = new DynamicBayesNet(a, b0, Arrays.asList(bt, bt));
//		String output = dbn.toString(true);
//		System.out.println(output);
//		
//		Observations o = dbn.generateObservations(500);
//		try {
//			CommandLine cmd = parser.parse(options, args);
//			o.writeToFile( cmd.getOptionValue("i") + "\\teste.csv");
//		} catch (ParseException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		
		

		
		
		
//		Observations o, oNew;
//		Scores s;
//		DynamicBayesNet dbn;
//		String output;
//		String path, dir;
//		int[][][] obs;
//		double[][] counts;
////		int[] numAttributes = {5, 10, 15, 20};
////		int[] n_obs = {1000, 2500, 5000, 7500, 10000, 12500};
////		int[] n_missing = {5, 10, 20, 30, 40};
////		int[] n_variable = {20, 40};
////		int[] time_points = {6};
//		
//		
//		int[] numAttributes = {50};
//		int[] n_obs = {1000};
//		//int[] n_missing = {20};
//		//int[] n_variable = {20};
//		int[] time_points = {10};
//		Random rand = new Random();
//		
//		BufferedWriter writer = null;
//		StringBuilder sb = new StringBuilder();
//		File directory;
//		
		
//		try {
//			CommandLine cmd = parser.parse(options, args);
//			if(cmd.getOptionValue("i") != null) {
//				path = cmd.getOptionValue("i");
//				String[] parts = path.split("\\.");
//				String file_name = parts[0];
//				o = new Observations(path, null, 1);
//				s = new Scores(o, 1, true, true);
//				double score;
//				double scorePrev;
//				Scores sNew;
//				int[][][]teste = o.getObservationsMatrix();
//				
//				if(o.numMissings(-1) > 0) {
//					s.evaluate(new RandomScoringFunction());
//					dbn = s.toDBN(-1, false);
//					dbn.generateParameters();
//					int i = 0;
//					do {
//						dbn = dbn.parameterEM(o, true);
//						oNew = o.fillMissingValues(dbn, true);
//						scorePrev = dbn.getScore(oNew, new MDLScoringFunction(), true);
//						sNew = new Scores(oNew, 1, true, true);
//						sNew.evaluate(new MDLScoringFunction());
//						dbn = sNew.toDBN(1, false);
//						dbn.learnParameters(oNew, true);
//						score = dbn.getScore(oNew, new MDLScoringFunction(), true);
//						i++;
//						System.out.println("Strutural EM step: " + i);
//					}while(score>scorePrev);
//				}else {
//					s.evaluate(new MDLScoringFunction());
//					dbn = s.toDBN(-1, true);
//					dbn.learnParameters(o, true);
//				}
//				System.out.println(o);
//
//			oNew = o.imputeMissingValues(dbn, true, true);
//			String path2 = file_name + "_imputed.csv";
//			oNew.writeToFile(path2);
//			}else {
//				System.exit(-1);
//			}
//		}catch (ParseException e) {
//			HelpFormatter formatter = new HelpFormatter();
//			formatter.printHelp("tDBN", options);
//		}
		

			
			
//			for(int numObs : n_obs) {
//				for(int time : time_points) {
//					for(int numMissing : n_missing) {
//						for(int numVariable : n_variable) {
//							path = String.format("\\GeneratedObs[%d,%d,%d,%d,%d].csv", n_attributes, numObs, time, numMissing, numVariable);
//							o = new Observations( args[0] + path, null, 1);
//							s = new Scores(o, 1, true, true);
//							double score;
//							double scorePrev;
//							Scores sNew;
//							int[][][]teste = o.getObservationsMatrix();
//							
//							if(o.numMissings(-1) > 0) {
////								long startTime = System.nanoTime();  
//								s.evaluate(new RandomScoringFunction());
//								dbn = s.toDBN(-1, false);
//								dbn.generateParameters();
//								int i = 0;
//								do {
//									dbn = dbn.parameterEM(o, true);
//									oNew = o.fillMissingValues(dbn, true);
//									scorePrev = dbn.getScore(oNew, new MDLScoringFunction(), true);
//									sNew = new Scores(oNew, 1, true, true);
//									sNew.evaluate(new MDLScoringFunction());
//									dbn = sNew.toDBN(-1, false);
//									dbn.learnParameters(oNew, true);
//									score = dbn.getScore(oNew, new MDLScoringFunction(), true);
//									i++;
//									System.out.println("Strutural EM step: " + i);
//								}while(score>scorePrev);
////								long estimatedTime = System.nanoTime() - startTime;
////								sb.append(n_attributes);
////								sb.append(';');
////								sb.append(estimatedTime);
////								sb.append('\n');
//							}else {
//								s.evaluate(new MDLScoringFunction());
//								dbn = s.toDBN(-1, true);
//								dbn.learnParameters(o, true);
//							}
////							System.out.println(o);
////							String output = dbn.toString(true);
////							System.out.println(output);
////							dbn = dbn.parameterEM(o, true);
////							output = dbn.toString(true);
////							System.out.println(output);
//							
////							Scores s = new Scores(o, 1, true, true);
////							s.evaluate(new RandomScoringFunction());
////							dbn = s.toDBN(-1, false);
////							dbn.generateParameters();
////							dbn.learnParameters(o_new);
////							System.out.println();
////							dbn.getScore(o_new, new LLScoringFunction(), true);
//							oNew = o.imputeMissingValues(dbn, true, true);
////							String path2 = String.format("\\test_%d_imputed_%d.csv", n, it);
//							String path2 = String.format("\\ImputedObs[%d,%d,%d,%d,%d].csv", n_attributes, numObs, time, numMissing, numVariable);
//							oNew.writeToFile(args[0] + path2);
////							String output = dbn.toString(true);
////							System.out.println(output);
//						}
//					}
//				}
//			}
//		
//		}
//		try {
//			writer.write(sb.toString());
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}finally {
//			try {
//				writer.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
	}
}