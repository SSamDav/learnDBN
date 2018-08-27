package cli;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import dbn.CrossValidation;
import dbn.LLScoringFunction;
import dbn.MDLScoringFunction;
import dbn.Observations;
import dbn.ScoringFunction;
import utils.Utils;

public class Prediction {

	@SuppressWarnings({ "static-access" })
	public static void main(String[] args) {

		// create Options object
		Options options = new Options();
		
		
		Option observationsFile  = Option.builder("i")
				.longOpt("file")
				.desc("Input CSV file to be used for network learning.")
			    .hasArg()
			    .argName("file")
			    .build();
		
		Option passiveFile  = Option.builder("j")
				.longOpt("inputPassiveFile")
				.desc("Input CSV file to be used for network learning.")
			    .hasArg()
			    .argName("file")
			    .build();
		
		Option numParents  = Option.builder("p")
				.longOpt("numParents")
				.desc("Maximum number of parents from preceding time-slice(s).")
			    .hasArg()
			    .argName("int")
			    .build();
		
		Option markovLag = Option.builder("m")
				.longOpt("markovLag")
				.desc("Maximum Markov lag to be considered, which is the longest distance between connected time-slices. Default is 1, allowing edges from one preceding slice.")
			    .hasArg()
			    .argName("int")
			    .build();
		
		Option numFolds = Option.builder("k")
				.longOpt("numFolds")
				.desc("Number of folds for cross-validation (default 10).")
			    .hasArg()
			    .argName("int")
			    .build();
		

		Option forecastAttributes = Option.builder("k")
				.longOpt("forecastAttributes")
				.desc("Attributes that are forecast for the following time-slice.")
			    .hasArg()
			    .argName("int,...")
			    .build();
		

		Option classAttribute = Option.builder("c")
				.longOpt("classAttribute")
				.desc("Class attribute used for stratifying data.")
			    .hasArg()
			    .argName("int,...")
			    .build();
		
		Option scoringFunction = Option.builder("s")
				.longOpt("scoringFunction")
				.desc("Scoring function to be used, either MDL or LL (default LL).")
			    .hasArg()
			    .build();


		Option ckg= Option.builder("ckg")
				.longOpt("ckg")
				.desc("Learns a ckg DBN structure.")
			    .hasArg()
			    .build();
		
		Option intra_in= Option.builder("ind")
				.longOpt("intra_in")
				.desc("In-degree of the intra-slice network")
			    .hasArg()
			    .argName("int")
			    .build();
		


		options.addOption(observationsFile);
		options.addOption(passiveFile);
		options.addOption(numParents);
		options.addOption(markovLag);
		options.addOption(numFolds);
		options.addOption(classAttribute);
		options.addOption(forecastAttributes);
		options.addOption(scoringFunction);
		options.addOption(ckg);
		options.addOption(intra_in);

		CommandLineParser parser = new DefaultParser();
		try {

			CommandLine cmd = parser.parse(options, args);

			int m = Integer.parseInt(cmd.getOptionValue("m"));
			int p = Integer.parseInt(cmd.getOptionValue("p"));
			int kFolds = Integer.parseInt(cmd.getOptionValue("k", "10"));
			
			int intra_ind = Integer.parseInt(cmd.getOptionValue("ind"));
			

			Integer cAttribute = cmd.hasOption("c") ? Integer.parseInt(cmd.getOptionValue("c")) : null;

			List<Integer> fAttributes = new ArrayList<Integer>();
			for (String attribute : cmd.getOptionValues("f"))
				fAttributes.add(Integer.valueOf(attribute));

			String s = cmd.getOptionValue("s", "ll");

			ScoringFunction sf = s.equalsIgnoreCase("ll") ? new LLScoringFunction() : new MDLScoringFunction();

			System.out.println("m = " + m + ", p = " + p + ", k = " + kFolds + ", c = " + cAttribute + ", f = "
					+ fAttributes + ", s = " + s);

			String fileName = cmd.getOptionValue("i");
			String outFileName = fileName.replace(".csv", "");
			outFileName = outFileName + "-" + s + "-m" + m + "-p" + p;

			Observations o = new Observations(fileName, cmd.getOptionValue("j"), m);

			CrossValidation cv = new CrossValidation(o, kFolds, cAttribute);
			
			
			String result = cv.evaluate(p, sf, outFileName, fAttributes,true,true,intra_ind);
			try {
				Utils.writeToFile(outFileName + ".txt", result);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("Prediction", options);
		}

	}

}
