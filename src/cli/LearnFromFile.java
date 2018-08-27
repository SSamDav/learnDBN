package cli;

import java.io.FileNotFoundException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
//import org.apache.commons.cli.GnuParser;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
//import org.apache.commons.cli.DefaultOptionBuilder;
//import org.apache.commons.cli.OptionBuilder;
//import org.apache.commons.cli.Option.Builder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import dbn.DynamicBayesNet;
import dbn.LLScoringFunction;
import dbn.MDLScoringFunction;
import dbn.Observations;
import dbn.Scores;
import dbn.ScoringFunction;
import utils.Utils;

public class LearnFromFile {

	@SuppressWarnings({ "static-access" })
	public static void main(String[] args) {


		// create Options object
		Options options = new Options();


		Option inputFile = Option.builder("i")
				.longOpt("file")
				.desc("Input CSV file to be used for network learning.")
				.hasArg()
				.argName("file")
				.build();

		Option numParents = Option.builder("p")
				.longOpt("numParents")
				.desc("Maximum number of parents from preceding time-slice(s).")
				.hasArg()
				.argName("int")
				.build();

		Option outputFile = Option.builder("o")
				.longOpt("outputFile")
				.desc("Writes output to <file>. If not supplied, output is written to terminal.")
				.hasArg()
				.argName("file")
				.build();

		Option rootNode = Option.builder("r")
				.longOpt("root")
				.desc("Root node of the intra-slice tree. By default, root is arbitrary.")
				.hasArg()
				.argName("int")
				.build();

		Option scoringFunction = Option.builder("s")
				.longOpt("scoringFunction")
				.desc("Scoring function to be used, either MDL or LL. MDL is used by default.")
				.hasArg()
				.build();

		Option dotFormat = Option.builder("d")
				.longOpt("dotFormat")
				.desc("Outputs network in dot format, allowing direct redirection into Graphviz to visualize the graph.")
				.build();


		Option compact = Option.builder("c")
				.longOpt("compact")
				.desc("Outputs network in compact format, omitting intra-slice edges. Only works if specified together with -d and with --markovLag 1.")
				.build();

		Option maxMarkovLag = Option.builder("m")
				.longOpt("markovLag")
				.desc("Maximum Markov lag to be considered, which is the longest distance between connected time-slices. Default is 1, allowing edges from one preceding slice.")
				.hasArg()
				.argName("int")
				.build();

		Option spanningTree = Option.builder("sp")
				.longOpt("nonStationary")
				.desc("Forces intra-slice connectivity to be a tree instead of a forest, eventually producing a structure with a lower score.")
				.build();

		Option nonStationary = Option.builder("ns")
				.longOpt("nonStationary")
				.desc("Learns a non-stationary network (one transition network per time transition). By default, a stationary DBN is learnt.")
				.build();

		Option parameters= Option.builder("pm")
				.longOpt("parameters")
				.desc("Learns and outputs the network parameters.")
				.build();


		Option bcDBN= Option.builder("bcDBN")
				.longOpt("bcDBN")
				.desc("Learns a bcDBN structure.")
				.build();
		
		Option cDBN= Option.builder("cDBN")
				.longOpt("cDBN")
				.desc("Learns a cDBN structure.")
				.build();

		Option intra_in= Option.builder("ind")
				.longOpt("intra_in")
				.desc("In-degree of the intra-slice network")
				.hasArg()
				.argName("int")
				.build();

		options.addOption(inputFile);
		options.addOption(numParents);
		options.addOption(outputFile);
		options.addOption(rootNode);
		options.addOption(scoringFunction);
		options.addOption(dotFormat);
		options.addOption(compact);
		options.addOption(maxMarkovLag);
		options.addOption(spanningTree);
		options.addOption(nonStationary);
		options.addOption(parameters);
		options.addOption(bcDBN);
		options.addOption(cDBN);
		options.addOption(intra_in);

		CommandLineParser parser = new DefaultParser();
		try {

			
			
			
			CommandLine cmd = parser.parse(options, args);



			boolean verbose = !cmd.hasOption("d");
			boolean stationary = !cmd.hasOption("nonStationary");
			boolean spanning = cmd.hasOption("spanning");
			boolean printParameters = cmd.hasOption("parameters");
			boolean is_bcDBN = cmd.hasOption("bcDBN");
			boolean is_cDBN = cmd.hasOption("cDBN");
			int intra_ind = Integer.parseInt(cmd.getOptionValue("ind","2"));

			// TODO: check sanity
			int markovLag = Integer.parseInt(cmd.getOptionValue("m", "1"));
			int root = Integer.parseInt(cmd.getOptionValue("r", "-1"));

			Observations o = new Observations(cmd.getOptionValue("i"), markovLag);

			Scores s = new Scores(o, Integer.parseInt(cmd.getOptionValue("p")), stationary, verbose);



			ScoringFunction sf;

		
			if (cmd.hasOption("s") && cmd.getOptionValue("s").equalsIgnoreCase("ll")) {
				sf = new LLScoringFunction();
				//System.out.println(cmd.getOptionValue("s"));
				if (verbose)
					System.out.println("Evaluating network with LL score.");
				s.evaluate(new LLScoringFunction());
			} else {
				sf = new MDLScoringFunction();
				//System.out.println(cmd.getOptionValue("s"));
				if (verbose)
					System.out.println("Evaluating network with MDL score.");
				s.evaluate(new MDLScoringFunction());
			}






			// if (verbose)
			// System.out.println(s);

			DynamicBayesNet dbn;

			if (verbose) {
				if (cmd.hasOption("r"))
					System.out.println("Root node specified: " + root);
				if (spanning)
					System.out.println("Finding a maximum spanning tree.");
				else
					System.out.println("Finding a maximum branching.");
			}


			if(is_bcDBN) {
				System.out.println("Learning bcDBN networks.");
				dbn=s.to_bcDBN(sf,intra_ind);

			}
			
			else {
				
				if(is_bcDBN) {
					System.out.println("Learning cDBN networks.");
					dbn=s.to_cDBN(sf,intra_ind);}
				
				
			else {
				System.out.println("Learning tDBN networks.");
				dbn = s.toDBN(root, spanning);

			}

				
			}


			if (printParameters)
				dbn.learnParameters(o);

			String output;

			if (cmd.hasOption("d")) {
				if (cmd.hasOption("c") && markovLag == 1)
					output = dbn.toDot(true);
				else
					output = dbn.toDot(false);
			} else
				output = dbn.toString(printParameters);

			if (cmd.hasOption("o")) {
				try {
					Utils.writeToFile(cmd.getOptionValue("o"), output);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			} else {
				if (verbose) {
					System.out.println();
					System.out.println("-----------------");
					System.out.println();
				}
				System.out.println(output);
			}

		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("cDBN", options);
			System.out.println(e);
		}

}
}





