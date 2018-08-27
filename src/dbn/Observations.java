package dbn;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import utils.Utils;

/**
 * @author ssam_
 *
 */
public class Observations {

	/**
	 * Three-dimensional matrix of coded observation data which will be used for
	 * learning a dynamic Bayesian network.
	 * <ul>
	 * <li>the 1st index refers to the transition {t - markovLag + 1, ...
	 * ,t}->t+1;
	 * <li>the 2nd index refers to the the subject (set of observed attributes);
	 * <li>the 3rd index refers to the attribute and lies within the range [0,
	 * (1 + markovLag)*n[, where [0, markovLag*n[ refers to attributes in the
	 * past and [markovLag*n, (1 + markovLag)*n[ refers to attributes in time
	 * t+1.
	 * </ul>
	 */
	private int[][][] usefulObservations;
	

	/**
	 * Three-dimensional matrix of non-coded observation data that will be
	 * present in the output, but not used for network learning.
	 * <ul>
	 * <li>the 1st index refers to the transition {t - markovLag + 1, ...
	 * ,t}->t+1;
	 * <li>the 2nd index refers to the the subject (set of observed attributes);
	 * <li>the 3rd index refers to the (not for learning) attribute.
	 * </ul>
	 */
	private String[][][] passiveObservations = null;

	/**
	 * Indicates, for each subject, what observations are present. Subject ID is
	 * the key, a boolean array of size equal to the number of transitions is
	 * the value.
	 */
	private Map<String, boolean[]> subjectIsPresent;

	/**
	 * Each column of the useful observation data refers to an attribute.
	 */
	private List<Attribute> attributes;

	/**
	 * Number of subjects per transition. Only those who have complete data for
	 * a transition are stored.
	 */
	private int[] numSubjects;
	
	/**
	 * Number of subjects per transition that are missing.
	 */
	private int[] numMissing;

	/**
	 * File that contains observations that will be converted to attributes and
	 * from which one can learn a DBN.
	 */
	private String usefulObservationsFileName;

	/**
	 * File that contains observations that will be included unchanged in the
	 * output. These are ignored when learning a DBN.
	 */
	private String passiveObservationsFileName;

	/**
	 * Header line of input useful observations CSV file.
	 */
	private String[] usefulObservationsHeader;

	/**
	 * Header line of input passive observations CSV file.
	 */
	private String[] passiveObservationsHeader = new String[0];

	/**
	 * Order of the Markov process, which is the number of previous time slices
	 * that influence the values in the following slice. Default is first-order
	 * Markov.
	 */
	private int markovLag = 1;
	
	/**
	 * Number of subjects with a certain observation
	 */
	private double[][] counts;

	/**
	 * Default constructor when reading observations from a file.
	 * 
	 * @see #Observations(String usefulObsFileName, String passiveObsFileName)
	 */
	public Observations(String usefulObsFileName) {
		this(usefulObsFileName, null);
	}

	public Observations(String usefulObsFileName, int markovLag) {
		this(usefulObsFileName, null, markovLag);
	}
	
	
	public void change0() {
		int n=attributes.size();
		attributes.remove(n-1);
	}
	
	
	
	

	/**
	 * Default constructor when reading observations from a file.
	 * <p>
	 * Input files format is be the following:
	 * <ul>
	 * <li>First row is the header
	 * <li>Each header entry, except the first, is in the form
	 * "attributeName__t", where t is the time slice
	 * <li>First column is the subject ID
	 * <li>One subject per line
	 * <li>No incomplete observations, a subject can only miss entire time
	 * slices.
	 * </ul>
	 * Input file example: <br>
	 * <code>subject_id,"resp__1","age__1","resp__2","age__2","resp__3","age__3"<br>
	 * 121013,0,65.0,0,67.0,0,67.0<br>
	 * 121113,0,24.0,0,29.0,0,29.0<br>
	 * 121114,0,9.0,0,7.0,0,0,7.0<br></code>
	 * 
	 * @param usefulObsFileName
	 *            File which contains observations that will be converted to
	 *            attributes and from which a DBN is learnt.
	 * @param passiveObsFileName
	 *            File which contains observations that will be included
	 *            unchanged in the output. These are ignored when learning a
	 *            DBN.
	 */
	public Observations(String usefulObsFileName, String passiveObsFileName, Integer markovLag) {
		this.usefulObservationsFileName = usefulObsFileName;
		this.passiveObservationsFileName = passiveObsFileName;
		this.markovLag = markovLag != null ? markovLag : 1;
		readFromFiles();
	}

	public Observations(String usefulObsFileName, String passiveObsFileName) {
		this(usefulObsFileName, passiveObsFileName, null);
	}

	/**
	 * This constructor is used when generating observations from a user
	 * specified DBN.
	 * 
	 * @see DynamicBayesNet#generateObservations(int)
	 */
	public Observations(List<Attribute> attributes, int[][][] observationsMatrix, double[][] counts) {
		this.attributes = attributes;
		this.usefulObservations = observationsMatrix;
		this.counts = counts;
		numSubjects = new int[observationsMatrix.length];

		// assume constant number of observations per transition
		int totalNumSubjects = observationsMatrix[0].length;
		Arrays.fill(numSubjects, totalNumSubjects);

		// generate header
		int n = numAttributes();
		this.usefulObservationsHeader = new String[n];
		for (int i = 0; i < n; i++)
			usefulObservationsHeader[i] = attributes.get(i).getName();

		// assume same subjects over all transitions
		this.subjectIsPresent = new LinkedHashMap<String, boolean[]>((int) Math.ceil(totalNumSubjects / 0.75));
		boolean[] allTrue = new boolean[numTransitions()];
		Arrays.fill(allTrue, true);
		for (int i = 0; i < totalNumSubjects; i++)
			subjectIsPresent.put("" + i, allTrue);
	}

	/**
	 * This constructor is used when forecasting from existing observations.
	 * 
	 * @see DynamicBayesNet#forecast(Observations)
	 */
	public Observations(Observations originalObservations, int[][][] newObservationsMatrix) {
		this.attributes = originalObservations.attributes;
		this.markovLag = originalObservations.markovLag;
		this.passiveObservations = originalObservations.passiveObservations;
		this.passiveObservationsHeader = originalObservations.passiveObservationsHeader;
		this.passiveObservationsFileName = originalObservations.passiveObservationsFileName;
		this.subjectIsPresent = originalObservations.subjectIsPresent;
		this.usefulObservations = newObservationsMatrix;
		this.usefulObservationsHeader = originalObservations.usefulObservationsHeader;
		this.usefulObservationsFileName = originalObservations.usefulObservationsFileName;

		this.numSubjects = new int[usefulObservations.length];

		// assume constant number of observations per transition
		Arrays.fill(numSubjects, usefulObservations[0].length);
	}
	
	

	public int getNumSubjects() {
		return this.numSubjects[0];
	}
	
	public int getNumTransitions() {
		return this.usefulObservations.length;
	}

	/**
	 * Reads the second and last column of the header, parses the integer time
	 * value and returns the difference between the two, plus one. If parsing is
	 * not possible, exits. Also performs error checking on the number of
	 * columns.
	 * 
	 * @return the number of time slices in input file
	 */
	private static int parseNumTimeSlices(String[] header) {

		int timeFirstColumn = 0, timeLastColumn = 0;

		try {
			// get first and last column time identifier
			timeFirstColumn = Integer.parseInt(header[1].split("__")[1]);
			timeLastColumn = Integer.parseInt(header[header.length - 1].split("__")[1]);

		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println(Arrays.deepToString(header));
			System.err.println("Input file header does not comply to the 'attribute__t' format.");
			System.exit(1);
		} catch (NumberFormatException e) {
			System.err.println(Arrays.deepToString(header));
			System.err.println("Input file header does not comply to the 'attribute__t' format.");
			System.exit(1);
		}

		int numTimeSlices = timeLastColumn - timeFirstColumn + 1;

		// the number of columns per time slice must be constant
		// header contains an extra column with subject id
		if ((header.length - 1) % numTimeSlices != 0) {
			System.err.println(Arrays.deepToString(header));
			System.err.println("Input file header does not have a number of columns"
					+ " compatible with the number of time slices.");
			System.err.println("Header length: " + header.length);
			System.err.println("Number of time slices: " + numTimeSlices);
			System.exit(1);
		}

		return numTimeSlices;
	}
	
	/**
	 * Counts the missing values in one string
	 */
	private static int countMissingValues(String[] dataLine) {

		int missing = 0;

		for (String value : dataLine)
			if (value.length() == 0 || value.equals("?"))
				missing++;

		return missing;
	}

	/**
	 * Checks for errors in an array of observed values, in order to decide if
	 * they will be stored in the observations matrix. If all the values are
	 * missing, returns false. If there are some missing values, exits. If no
	 * values are missing, returns true.
	 */
	private boolean observationIsOk(String[] observation) {

		int missingValues = countMissingValues(observation);
		int n = numAttributes();

		if (missingValues == n) {
			// missing observation (all values missing), skip
			return false;
		}
		
		//System.out.println("number of missing values:"+missingValues);

		if (missingValues > 0) {
			// some missing values, can't work like that
			//System.err.println(Arrays.deepToString(observation));
			//System.err.println("Observation contains missing values.");
			//System.exit(1);
			return false;
		}

		return true;
	}
	
	

	public Map<String, boolean[]> getSubjectIsPresent() {
		return subjectIsPresent;
	}

	private void readFromFiles() {

		try {

			// open and parse the useful observations csv file
			CSVReader reader = new CSVReader(new FileReader(usefulObservationsFileName));
			List<String[]> lines = reader.readAll();
			reader.close();

			ListIterator<String[]> li = lines.listIterator();

			// get first line
			String[] header = li.next();
			
			//System.out.println("number of time slices: ");

			int numTimeSlices = parseNumTimeSlices(header);
			int numTransitions = numTimeSlices - markovLag;
			
			//System.out.println("number of time slices 1: "+numTimeSlices);
			

			int numAttributes = (header.length - 1) / numTimeSlices;
			attributes = new ArrayList<Attribute>(numAttributes);
			
			//System.out.println("number of attributes 1: "+numAttributes);

			usefulObservationsHeader = processHeader(header, numAttributes);

			// allocate observations matrix
			int totalNumSubjects = lines.size()-1;
			usefulObservations = new int[numTransitions][totalNumSubjects][(markovLag + 1) * numAttributes];
			numSubjects = new int[numTransitions];
			numMissing = new int[numTransitions];
			this.counts = new double[numTransitions][totalNumSubjects];
			subjectIsPresent = new LinkedHashMap<String, boolean[]>((int) Math.ceil(totalNumSubjects / 0.75));
			
//			Initialization of counts
			for(int i = 0; i < numTransitions;i++) {
				Arrays.fill(this.counts[i], 1);
			}

			String[] dataLine = li.next();
			
			//System.out.println("totalNumSubjects 1:"+totalNumSubjects);

			// fill attributes from first observation (get their type)
			// it must not have missing values
			String[] firstObservation = Arrays.copyOfRange(dataLine, 1, numAttributes + 1);
			if (countMissingValues(firstObservation) > 0) {
				System.err.println(firstObservation);
				System.err.println("First observation contains missing values.");
				System.exit(1);
			}
			int i = 0;
			for (String value : firstObservation) {
				Attribute attribute;
				// numeric attribute
				if (Utils.isNumeric(value))
					attribute = new NumericAttribute();
				// nominal attribute
				else
					attribute = new NominalAttribute();
				attribute.setName(usefulObservationsHeader[i++]);
				attributes.add(attribute);
			}

			// rewind one line
			li.previous();

			// auxiliary variable
			String[][] observations = new String[markovLag + 1][numAttributes];

			while (li.hasNext()) {

				dataLine = li.next();

				// check for line sanity
				if (dataLine.length != numTimeSlices * numAttributes + 1) {
					System.err.println(Arrays.deepToString(dataLine));
					System.err
							.println("Observations file: input data line does not have the correct number of columns.");
					System.err.println("Line length: " + dataLine.length);
					System.err.println("Number of time slices: " + numTimeSlices);
					System.err.println("Number of attributes: " + numAttributes);
					System.exit(1);
				}

				// record subject id
				String subject = dataLine[0];
				subjectIsPresent.put(subject, new boolean[numTransitions]);
				
				for (int t = 0; t < numTransitions; t++) {

					boolean observationsOk = true;
	
					// obtain and check observations for each slice
					for (int ts = 0; ts < markovLag + 1; ts++) {
						observations[ts] = Arrays.copyOfRange(dataLine, 1 + (t + ts) * numAttributes, 1 + (t + ts + 1)
								* numAttributes);
						if (!observationIsOk(observations[ts])) {
							observationsOk = false;
						}
					}

					if (observationsOk) {

						// observations are sane, store them
						subjectIsPresent.get(subject)[t] = true;
						String[] transition = Arrays.copyOfRange(dataLine, 1 + t * numAttributes, 1
								+ (t + markovLag + 1) * numAttributes);
						for (int j = 0; j < (markovLag + 1) * numAttributes; j++) {
							String value = transition[j];
							int attributeId = j % numAttributes;
							Attribute attribute = attributes.get(attributeId);
							attribute.add(value);
							usefulObservations[t][numSubjects[t]][j] = attribute.getIndex(value);
						}
						numSubjects[t]++;

					} else {
						// if this observations has missing values, fill the missing values with the values -1
						subjectIsPresent.get(subject)[t] = true;
						String[] transition = Arrays.copyOfRange(dataLine, 1 + t * numAttributes, 1
								+ (t + markovLag + 1) * numAttributes);
						for (int j = 0; j < (markovLag + 1) * numAttributes; j++) {
							String value = transition[j];
							int attributeId = j % numAttributes;
							if(!(value.length() == 0 || value.equals("?"))) {
								Attribute attribute = attributes.get(attributeId);
								attribute.add(value);
								usefulObservations[t][numSubjects[t]][j] = attribute.getIndex(value);
							}else{
								usefulObservations[t][numSubjects[t]][j] = -1;
							}
						}
//TODO: Verify if we need numMissing in our code
						numMissing[t]++;
						numSubjects[t]++;
					}

				}
			}

		} catch (IOException e) {
			System.err.println("File " + usefulObservationsFileName + " could not be opened.");
			e.printStackTrace();
			System.exit(1);
		}
		
		
		
		
		

		if (passiveObservationsFileName != null) {

			try {
				// open and parse the passive observations csv file
				CSVReader reader = new CSVReader(new FileReader(passiveObservationsFileName));
				List<String[]> lines = reader.readAll();
				reader.close();

				ListIterator<String[]> li = lines.listIterator();

				// get first line
				String[] header = li.next();

				int numTransitions = numTransitions();
				int numTimeSlices = numTransitions + markovLag;
				
				//subjectIsPresent.size()
				int totalNumSubjects =711;
				int numPassiveAttributes = (header.length - 1) / numTimeSlices;
				
				//System.out.println("numTransitions "+numTransitions);
				
				//System.out.println("numTimeSlices "+numTimeSlices);
				
				//System.out.println("totalNumSubjects "+totalNumSubjects);
				
				//System.out.println("numPassiveAttributes "+numPassiveAttributes);
				
				
				passiveObservationsHeader = processHeader(header, numPassiveAttributes);

				// allocate observations matrix
				passiveObservations = new String[numTransitions][totalNumSubjects][(markovLag + 1)
						* numPassiveAttributes];
				

				int[] tempNumSubjects = new int[numTransitions];
				
				//System.out.println(Arrays.toString(tempNumSubjects));

				while (li.hasNext()) {
					String[] dataLine = li.next();
					if (dataLine.length != numTimeSlices * numPassiveAttributes + 1) {
						System.err.println(Arrays.deepToString(dataLine));
						System.err
								.println("Passive observations file: input data line does not have the correct number of columns.");
						System.err.println("Line length: " + dataLine.length);
						System.err.println("Number of time slices: " + numTimeSlices);
						System.err.println("Number of attributes: " + numPassiveAttributes);
						System.exit(1);
					}
					
					//System.out.println(Arrays.toString(tempNumSubjects));

					String subject = dataLine[0];
					//System.out.println("subject"+subject);
					if (subjectIsPresent.containsKey(subject)) {
						for (int t = 0; t < numTransitions; t++) {
							//System.out.println(subjectIsPresent.get(subject)[t]);
							if (subjectIsPresent.get(subject)[t]) {
								//System.out.println("t "+t);
								//System.out.println("try :"+tempNumSubjects[t]);
								//System.out.println("try 2:"+Arrays.copyOfRange(dataLine, 1 + t
										//* numPassiveAttributes, 1 + (t + markovLag + 1) * numPassiveAttributes).toString());
								
								passiveObservations[t][tempNumSubjects[t]] = Arrays.copyOfRange(dataLine, 1 + t
										* numPassiveAttributes, 1 + (t + markovLag + 1) * numPassiveAttributes);
								tempNumSubjects[t]++;
							}
						}
					}
					 /*else
					 System.out.println("Skipping subject " + subject +
					 " on passive observations file.");*/
				}

			} catch (IOException e) {
				System.err.println("File " + passiveObservationsFileName + " could not be opened.");
				e.printStackTrace();
				System.exit(1);
			}
		}

	}

	/**
	 * Gets the name of the attributes from an input header line and the number
	 * of attributes.
	 */
	private String[] processHeader(String[] header, int numAttributes) {
		String[] newHeader = new String[numAttributes];
		String stripFirstHeader[] = Arrays.copyOfRange(header, 1, numAttributes + 1);
		int i = 0;
		for (String column : stripFirstHeader) {
			String[] columnParts = column.split("__");
			newHeader[i++] = columnParts[0];
		}
		return newHeader;
	}
	
	private static <T> Set<List<T>> getCombinations(List<List<T>> lists) {
	    Set<List<T>> combinations = new HashSet<List<T>>();
	    Set<List<T>> newCombinations;

	    int index = 0;

	    // extract each of the integers in the first list
	    // and add each to ints as a new list
	    for(T i: lists.get(0)) {
	        List<T> newList = new ArrayList<T>();
	        newList.add(i);
	        combinations.add(newList);
	    }
	    index++;
	    while(index < lists.size()) {
	        List<T> nextList = lists.get(index);
	        newCombinations = new HashSet<List<T>>();
	        for(List<T> first: combinations) {
	            for(T second: nextList) {
	                List<T> newList = new ArrayList<T>();
	                newList.addAll(first);
	                newList.add(second);
	                newCombinations.add(newList);
	            }
	        }
	        combinations = newCombinations;

	        index++;
	    }

	    return combinations;
	}
	
	/**
	 * Function that given an observation generates missing values
	 * @param missingObservations Percentage of observations with missing values
	 * @param missingVariables	Percentage of attributes, for each observation, with missing values
	 */
	public Observations generateMissingValues(int missingObservations, int missingVariables) {
		int numSubjects = this.numObservations(0, true);
		int numMissingObservations = (int) Math.ceil(((double)missingObservations)/100 * (double) numSubjects);
		int numMissingVariables = (int) Math.ceil(((double)missingVariables)/100 * ((double)attributes.size()*(markovLag + 1)));
		Set<Integer> indicesObservation = new HashSet<Integer>();
		boolean add;
		Random random = new Random();
		int value;
		int[][][] new_obs = new int[this.numTransitions()][numSubjects][attributes.size()*(markovLag + 1)];
		
		for(int t = 0; t < this.numTransitions(); t++) {
			for(int s = 0; s < numSubjects; s++) {
				System.arraycopy( usefulObservations[t][s], 0, new_obs[t][s], 0, usefulObservations[t][s].length );
			}
		}
		
	
	
		for(int i = 0; i < numMissingObservations; i++) {
			do {
				value = random.nextInt((numSubjects-1) + 1 - 1) + 1;
				add = indicesObservation.add(value);
			}while(!add);
		}
		
		for(int indice : indicesObservation) {
			Set<Integer> indicesVariables = new HashSet<Integer>();
			for(int i = 0; i < numMissingVariables; i++) {
				do {
					value = random.nextInt((attributes.size()*(markovLag + 1) - 1)*this.numTransitions()   + 1 - 0) + 0;
					add = indicesVariables.add(value);
				}while(!add);
			}
			for(int indice2 : indicesVariables) {
				new_obs[indice2%this.numTransitions()][indice][indice2/this.numTransitions()] = -1;
			}
		}
		
		return new Observations(attributes, new_obs, counts);
	
	}
	
	public Observations imputeMissingValues(DynamicBayesNet dbn, boolean stationaryProcess, boolean mostProbable) {
		List<Attribute> Attributes = dbn.getAttributes();
		int numAttributes = Attributes.size();
		int numSubjects = this.numSubjects[0];
		int numTransitions = this.usefulObservations.length;
		int[][][] newObservations;
		int[][][] observationAux;
		double[] probability;
		double[][] newCounts;
		double probabilitySum;
		double probabilityAux;
		List<Integer> attributeValues;
		List<List<Integer>> missingCombinations;
		Set<List<Integer>> combinationSet;
		List<List<Integer>> combinationList;
		int nodeAux;
		int subjectCombinations;
		
		newObservations = new int[numTransitions][numSubjects][numAttributes * (markovLag + 1)] ;
		newCounts = new double[numTransitions][numSubjects];
		
		for(int t = 0; t < numTransitions; t++) {
			Arrays.fill(newCounts[t], 1);
		}
		
		for(int subject = 0; subject < numSubjects; subject++) {
			subjectCombinations = 1;
			for(int t = 0; t < numTransitions; t++) {
				if(t == 0) {
					for(int n = 0;  n < numAttributes * (markovLag + 1); n++) {
						if(this.usefulObservations[t][subject][n] == -1) {
							subjectCombinations *= Attributes.get(n%numAttributes).size();
						}
					}
				}else {
					for(int n = numAttributes;  n < numAttributes * (markovLag + 1); n++) {
						if(this.usefulObservations[t][subject][n] == -1) {
							subjectCombinations *= Attributes.get(n%numAttributes).size();
						}
					}
				}
				
			}
			
			if(subjectCombinations == 1) {
				for(int t = 0; t < numTransitions; t++) {
					System.arraycopy(this.usefulObservations[t][subject], 0, newObservations[t][subject], 0, numAttributes * (markovLag + 1));
				}
			}else {
				observationAux = new int[numTransitions][subjectCombinations][numAttributes * (markovLag + 1)];
				probability = new double[subjectCombinations];
				missingCombinations = new ArrayList<List<Integer>>();
				for(int t = 0; t < numTransitions; t++) {
					if(t == 0) {
						for(int n = 0; n < numAttributes * (markovLag + 1); n++) {
							if(this.usefulObservations[t][subject][n] == -1) {
								attributeValues = new ArrayList<Integer>();
								for(int i = 0 ; i < attributes.get(n%numAttributes).size(); i++) {
									attributeValues.add(i);
								}
								missingCombinations.add(attributeValues);
							}
						}
					}else {
						for(int n = numAttributes; n < numAttributes * (markovLag + 1); n++) {
							if(this.usefulObservations[t][subject][n] == -1) {
								attributeValues = new ArrayList<Integer>();
								for(int i = 0 ; i < attributes.get(n%numAttributes).size(); i++) {
									attributeValues.add(i);
								}
								missingCombinations.add(attributeValues);
							}
						}
					}
					
				}
				combinationSet = getCombinations(missingCombinations);
				combinationList = new ArrayList<List<Integer>>(combinationSet);
				probabilitySum = 0;
				for(int i = 0; i < subjectCombinations; i++) {
					probabilityAux = 1;
					nodeAux = 0;
					for(int t = 0; t < numTransitions; t++) {
						if(t == 0) {
							for(int n = 0; n < numAttributes * (markovLag + 1); n++) {
								if(this.usefulObservations[t][subject][n] == -1) {
									observationAux[t][i][n] = combinationList.get(i).get(nodeAux);
									if(n>=numAttributes & n/numAttributes + t < numTransitions) {
										observationAux[n/numAttributes + t][i][n%numAttributes] = observationAux[t][i][n];
									}
									nodeAux++;
								}else{
									observationAux[t][i][n] = this.usefulObservations[t][subject][n];
									if(n>=numAttributes & n/numAttributes + t < numTransitions) {
										observationAux[n/numAttributes + t][i][n%numAttributes] = observationAux[t][i][n];
									}
								}
							}
						}else {
							for(int n = numAttributes; n < numAttributes * (markovLag + 1); n++) {
								if(this.usefulObservations[t][subject][n] == -1) {
									observationAux[t][i][n] = combinationList.get(i).get(nodeAux);
									if(n>=numAttributes & n/numAttributes + t < numTransitions) {
										observationAux[n/numAttributes + t][i][n%numAttributes] = observationAux[t][i][n];
									}
									nodeAux++;
								}else{
									observationAux[t][i][n] = this.usefulObservations[t][subject][n];
									if(n>=numAttributes & n/numAttributes + t < numTransitions) {
										observationAux[n/numAttributes + t][i][n%numAttributes] = observationAux[t][i][n];
									}
								}
							}
						}
						
					}

					
					
					if(stationaryProcess) {
						for(int t = 0; t < numTransitions; t++) {
							for(int n = 0; n < numAttributes; n++) {
								probabilityAux *= dbn.transitionNets.get(0).getParameters(n, observationAux[t][i]).get(0);
							}
						}
					}else {
						for(int t = 0; t < numTransitions; t++) {
							for(int n = 0; n < numAttributes; n++) {
								probabilityAux *= dbn.transitionNets.get(t).getParameters(n, observationAux[t][i]).get(0);
							}
						}
					}
					probability[i] = probabilityAux;
					probabilitySum += probabilityAux;
				}
				for(int i = 0; i < subjectCombinations; i++) {					
						probability[i] /= probabilitySum;
				}
				if(mostProbable) {
					double largest = probability[0];
					int index = 0;
					for(int i = 1; i < subjectCombinations; i++) {
						if(probability[i] > largest) {
							largest = probability[i];
							index = i;
						}
					}
					for(int t = 0; t < numTransitions; t++) {
						System.arraycopy(observationAux[t][index] , 0, newObservations[t][subject], 0, numAttributes * (markovLag + 1));
					}
				}else {
					Random r = new Random();
					double sample = r.nextDouble();
					int index = 0;
					double accum = probability[index];
					while(sample > accum) {
						if(!(index < subjectCombinations - 1)) {
							index++;
							break;
						}
						accum += probability[++index];
					}
					for(int t = 0; t < numTransitions; t++) {
						System.arraycopy(observationAux[t][index] , 0, newObservations[t][subject], 0, numAttributes * (markovLag + 1));
					}
				}
			}
		}
		return new Observations(attributes, newObservations, newCounts);
	}
	
	/**
	 * Do the Expected Sufficient Statistics of an set of observations with missing values.
	 * @param dbn DBN to which it will be generated the ESS
	 * @return Observations Observations without missing values and with ESS.1
	 */
	public Observations fillMissingValues(DynamicBayesNet dbn, boolean stationaryProcess){
		List<Attribute> Attributes = dbn.getAttributes();
		int numAttributes = Attributes.size();
		int numSubjects = this.numSubjects[0];
		int numTransitions = this.usefulObservations.length;
		int totalCombinations = 0;
		int subjectCombinations;
		int[][][] newObservations;
		double[][] newCounts;
		int[] observation;
		double probabilitySum;
		double probabilityAux;
		List<Integer> attributeValues;
		List<List<Integer>> missingCombinations;
		Set<List<Integer>> combinationsSet;
		List<List<Integer>> combinationsList;
		int subjectAux;
		int subjectAux2;
		int nodeAux;
		double probabilityMax;
		
		double decimal_places = 5;
		double epsilon = Math.pow(10, -decimal_places);
		
		subjectAux = 0;
		for(int subject = 0; subject < numSubjects; subject++) {
			subjectCombinations = 1;
			for(int t = 0; t < numTransitions; t++) {
				if(t == 0) {
					for(int n = 0;  n < numAttributes * (markovLag + 1); n++) {
						if(this.usefulObservations[t][subject][n] == -1) {
							subjectCombinations *= Attributes.get(n%numAttributes).size();
						}
					}
				}else {
					for(int n = numAttributes;  n < numAttributes * (markovLag + 1); n++) {
						if(this.usefulObservations[t][subject][n] == -1) {
							subjectCombinations *= Attributes.get(n%numAttributes).size();
						}
					}
				}
				
			}
			totalCombinations += subjectCombinations;
		}

		newObservations = new int[numTransitions][totalCombinations][numAttributes * (markovLag + 1)];
		newCounts = new double[numTransitions][totalCombinations];
		
		for(int subject = 0; subject < numSubjects; subject++) {
			missingCombinations = new ArrayList<List<Integer>>();
			for(int t = 0; t < numTransitions; t++) {
				if(t == 0) {
					for(int n = 0; n < numAttributes * (markovLag + 1); n++) {
						if(this.usefulObservations[t][subject][n] == -1) {
							attributeValues = new ArrayList<Integer>();
							for(int i = 0 ; i < attributes.get(n%numAttributes).size(); i++) {
								attributeValues.add(i);
							}
							missingCombinations.add(attributeValues);
						}
					}
				}else {
					for(int n = numAttributes; n < numAttributes * (markovLag + 1); n++) {
						if(this.usefulObservations[t][subject][n] == -1) {
							attributeValues = new ArrayList<Integer>();
							for(int i = 0 ; i < attributes.get(n%numAttributes).size(); i++) {
								attributeValues.add(i);
							}
							missingCombinations.add(attributeValues);
						}
					}
				}
				
			}
			if(missingCombinations.size() == 0) {
				for(int t = 0; t < numTransitions; t++) {
					System.arraycopy(this.usefulObservations[t][subject], 0, newObservations[t][subjectAux], 0, this.usefulObservations[t][subject].length);
					newCounts[t][subjectAux] = 1;
				}
				subjectAux++;
			}else {
				combinationsSet = getCombinations(missingCombinations);
				combinationsList = new ArrayList<List<Integer>>(combinationsSet);
				subjectAux2 = subjectAux;
				probabilityMax = Double.NEGATIVE_INFINITY;
				for(int i = 0; i < combinationsList.size(); i++) {
//					probabilityAux = 1;
					probabilityAux = 0;
					nodeAux = 0;
					for(int t = 0; t < numTransitions; t++) {
						if(t == 0) {
							for(int n = 0; n < numAttributes * (markovLag + 1); n++) {
								if(this.usefulObservations[t][subject][n] == -1) {
									newObservations[t][subjectAux][n] = combinationsList.get(i).get(nodeAux);
									if(n>=numAttributes & n/numAttributes + t < numTransitions) {
										newObservations[n/numAttributes + t][subjectAux][n%numAttributes] = newObservations[t][subjectAux][n];
									}										
									nodeAux++;
								}else {
									newObservations[t][subjectAux][n] = this.usefulObservations[t][subject][n];
									if(n>=numAttributes & n/numAttributes + t < numTransitions) {
										newObservations[n/numAttributes + t][subjectAux][n%numAttributes] = this.usefulObservations[t][subject][n];
									}
								}
							}
							
						}else {
							for(int n = numAttributes; n < numAttributes * (markovLag + 1); n++) {
								if(this.usefulObservations[t][subject][n] == -1) {
									newObservations[t][subjectAux][n] = combinationsList.get(i).get(nodeAux);
									if(n>=numAttributes & n/numAttributes + t < numTransitions)
										newObservations[n/numAttributes + t][subjectAux][n%numAttributes] = combinationsList.get(i).get(nodeAux);
									nodeAux++;
								}else {
									newObservations[t][subjectAux][n] = this.usefulObservations[t][subject][n];
									if(n>=numAttributes & n/numAttributes + t < numTransitions) {
										newObservations[n/numAttributes + t][subjectAux][n%numAttributes] = this.usefulObservations[t][subject][n];
									}
								}
							}
						}
					}
				
					if(stationaryProcess) {
						for(int t = 0; t < numTransitions; t++) {
							for(int n = 0; n < numAttributes; n++) {
//								probabilityAux *= dbn.transitionNets.get(0).getParameters(n, newObservations[t][subjectAux]).get(0);
								probabilityAux += Math.log(dbn.transitionNets.get(0).getParameters(n, newObservations[t][subjectAux]).get(0));
							}
						}
					}else {
						for(int t = 0; t < numTransitions; t++) {
							for(int n = 0; n < numAttributes; n++) {
//								probabilityAux *= dbn.transitionNets.get(t).getParameters(n, newObservations[t][subjectAux]).get(0);
								probabilityAux += Math.log(dbn.transitionNets.get(t).getParameters(n, newObservations[t][subjectAux]).get(0));
								
							}
						}
					}
					for(int t = 0; t < numTransitions; t++) {
						newCounts[t][subjectAux] = probabilityAux;
					}
//					probabilitySum += probabilityAux;
					if(probabilityMax < probabilityAux)
						probabilityMax = probabilityAux;
					
					subjectAux++;
				}
				
				
				
				for(int i = subjectAux2; i < subjectAux; i++) {
					for(int t = 0; t < numTransitions; t++) {
						if(newCounts[t][i] - probabilityMax >= Math.log(epsilon) - Math.log(combinationsList.size())){
							newCounts[t][i] = Math.exp(newCounts[t][i] - probabilityMax);
						}else {
							newCounts[t][i] = 0;
						}
					}
				}
				probabilitySum = 0;
				for(int i = subjectAux2; i < subjectAux; i++) {
					probabilitySum += (double)Math.ceil(newCounts[0][i] * 1000000000000000d) / 1000000000000000d;
					
				}
				for(int i = subjectAux2; i < subjectAux; i++) {
					for(int t = 0; t < numTransitions; t++) {
						newCounts[t][i] /= probabilitySum;
					}
				}
//				for(int i = subjectAux2; i < subjectAux; i++) {
//					for(int t = 0; t < numTransitions; t++) {
//						newCounts[t][i] /= probabilitySum;
//					}
//				}
			}
		}
		
//		System.out.println("--------- antes da geração ---------");
//		for(int t = 0; t < numTransitions; t++) {
//			System.out.println("---Transition " + t + " ---");
//			for(int s = 0; s < numSubjects; s++) {
//				System.out.print("[ ");
//				for(int n = 0; n < numAttributes * (markovLag + 1); n++) {
//					if(this.usefulObservations[t][s][n] == -1) {
//						System.out.print("? ");
//					}else {
//						System.out.print(attributes.get(n%numAttributes).get(this.usefulObservations[t][s][n]) + " ");
//					}
//				}
//				System.out.println("]  count: " + this.counts[t][s]);
//			}
//		}
////		
//		System.out.println("--------- depois da geração ---------");
//		for(int t = 0; t < numTransitions; t++) {
//			System.out.println("---Transition " + t + " ---");
//			for(int s = 0; s < totalCombinations; s++) {
//				System.out.print("[ ");
//				for(int n = 0; n < numAttributes * (markovLag + 1); n++) {
//					System.out.print(attributes.get(n%numAttributes).get(newObservations[t][s][n]) + " " );
//				}
//				System.out.println("]  count: " + newCounts[t][s]);
//			}
//		}
		return new Observations(attributes, newObservations, newCounts);
	}

	/**
	 * Gets the number of transitions of the observations.
	 */
	public int numTransitions() {
		return usefulObservations.length;
	}
	
	/**
	 * Gets the number of observations in one transition.
	 */
	public int numObservations(int transition) {
		
		return numObservations(transition, false);
	}
	
	/**
	 * Gets the number of observations in one transition utilizing the expected sufficient statistics.
	 */
	public int numObservations(int transition, boolean withoutcounts) {

		// stationary process
		if (transition < 0) {
			int numObs = 0;
			int T = numTransitions();
			for (int t = 0; t < T; t++) {
				if(withoutcounts) {
					numObs += this.counts[t].length;
				}else {
					numObs += numSubjects[t];
				}
			}	
			return numObs;
		}

		// time-varying process
		if(withoutcounts) {
			return this.counts[transition].length;
		}else {
			return numSubjects[transition];
		}
	}
	
	/**
	 * Gets the number of observations with missing values.
	 */
	public int numMissings(int transition) {

		// stationary process
		if (transition < 0) {
			int numObs = 0;
			int T = numTransitions();
			for (int t = 0; t < T; t++)
				numObs += numMissing[t];
			return numObs;
		}

		// time-varying process
		return numMissing[transition];
	}

	public int numAttributes() {
		return attributes.size();
	}

	public List<Attribute> getAttributes() {
		return attributes;
	}

	/**
	 * Returns a representation of the first observations (#markovLag time
	 * slices) of all subjects.
	 */
	public List<int[]> getFirst() {
		int numSubjects = this.numSubjects[0];
		List<int[]> initialObservations = new ArrayList<int[]>(numSubjects);
		for (int s = 0; s < numSubjects; s++)
			initialObservations.add(Arrays.copyOfRange(usefulObservations[0][s], 0, markovLag * numAttributes()));
		return initialObservations;
	}

	public int[][][] getObservationsMatrix() {
		return usefulObservations;
	}

	public String[][][] getPassiveObservationsMatrix() {
		return passiveObservations;
	}

	/**
	 * Given a network configuration (parents and child values), counts all
	 * observations in some transition that are compatible with it. If
	 * transition is negative, counts matches in all transitions.
	 */
	public double count(LocalConfiguration c, int transition) {

		// stationary process
		if (transition < 0) {
			double allMatches = 0;
			int T = numTransitions();
			for (int t = 0; t < T; t++)
				allMatches += count(c, t);
			return allMatches;
		}

		// time-varying process
		double matches = 0;
		int N = numObservations(transition, true);
		for (int i = 0; i < N; i++)
			if (c.matches(usefulObservations[transition][i]))
				matches+= this.counts[transition][i];
		return matches;
	}

	public void writeToFile() {
		String outFileName = this.usefulObservationsFileName.replace(".csv", "-out.csv");

		// test for file name without extension
		if (outFileName.equals(this.usefulObservationsFileName))
			outFileName = this.usefulObservationsFileName + "-out";

		writeToFile(outFileName);
	}

	public void writeToFile(String outFileName) {

		CSVWriter writer;

		try {
			
			File outputFile = new File(outFileName);
			outputFile.createNewFile();
			
			writer = new CSVWriter(new FileWriter(outputFile));

			int numTransitions = numTransitions();
			int numTimeSlices = numTransitions + 1;
			int numAttributes = numAttributes();
			int numPassiveAttributes = numPassiveAttributes();
			int totalNumAttributes = numAttributes + numPassiveAttributes;
			int numSubjects = this.numSubjects[0];

			boolean thereArePassiveObservations = passiveObservations != null ? true : false;

			int interSliceSpace = 5;

			// compose header line
			List<String> headerEntries = new ArrayList<String>(totalNumAttributes * numTimeSlices + 2 + interSliceSpace);
			headerEntries.add("subject_id");
			for (int t = 0; t < numTimeSlices; t++) {
				for (String columnName : usefulObservationsHeader) {
					headerEntries.add(columnName + "__" + t);
				}
				if (thereArePassiveObservations) {
					// separator between useful (predicted) and passive
					// (unchanged)
					// observations
					headerEntries.add("");
					for (String columnName : passiveObservationsHeader) {
						headerEntries.add(columnName + "__" + t);
					}
					// separator between time slices;
					for (int i = interSliceSpace; i-- > 0;)
						headerEntries.add("");
				}
			}

			// write header line to file
			writer.writeNext(headerEntries.toArray(new String[0]));

			// iterator over subject ids
			Iterator<String> subjectIterator = subjectIsPresent.keySet().iterator();

			int passiveSubject = -1;
			for (int s = 0; s < numSubjects; s++) {

				List<String> subjectEntries = new ArrayList<String>(totalNumAttributes * numTimeSlices + 2
						+ interSliceSpace);

				// add subject id
				while (subjectIterator.hasNext()) {
					String subject = subjectIterator.next();
					passiveSubject++;
					if (subjectIsPresent.get(subject)[0]) {
						subjectEntries.add(subject);
						break;
					}
				}

				// add observations from all except the last time slice
				for (int t = 0; t < numTransitions; t++) {
					for (int i = 0; i < numAttributes; i++) {
						if(usefulObservations[t][s][i]==-1) {
							subjectEntries.add("?");
						}else {
							subjectEntries.add(attributes.get(i).get(usefulObservations[t][s][i]));
						}	
					}

					if (thereArePassiveObservations) {
						subjectEntries.add("");
						for (int i = 0; i < numPassiveAttributes; i++) {
							subjectEntries.add(passiveObservations[t][passiveSubject][i]);
						}
						for (int i = interSliceSpace; i-- > 0;)
							subjectEntries.add("");
					}
				}

				// add observations from the last time slice
				for (int i = numAttributes; i < 2 * numAttributes; i++) {
					if(usefulObservations[numTransitions - 1][s][i]==-1) {
						subjectEntries.add("?");
					}else {
						subjectEntries.add(attributes.get(i % numAttributes).get(
								usefulObservations[numTransitions - 1][s][i]));
					}	
				}

				if (thereArePassiveObservations) {
					subjectEntries.add("");
					for (int i = 0; i < numPassiveAttributes; i++) {
						subjectEntries.add(passiveObservations[numTimeSlices - 1][passiveSubject][i]);
					}
					for (int i = interSliceSpace; i-- > 0;)
						subjectEntries.add("");
				}
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

	public int numPassiveAttributes() {
		return passiveObservations != null ? passiveObservations[0][0].length / (markovLag + 1) : 0;
	}

	public int getMarkovLag() {
		return markovLag;
	}

	public String toTimeSeriesHorizontal() {
		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");
		int numTransitions = numTransitions();
		int numAttributes = numAttributes();

		sb.append("Attribute_ID" + "\t");
		for (int t = 0; t < numTransitions; t++) {
			sb.append("OBS" + t + "\t");
		}
		sb.append("OBS" + numTransitions + ls);
		for (int j = 0; j < numAttributes; j++) {
			sb.append("A" + j + "\t");
			for (int t = 0; t < numTransitions; t++) {
				sb.append(usefulObservations[t][0][j] + "\t");
			}
			sb.append(usefulObservations[numTransitions - 1][0][j + numAttributes] + ls);

		}
		sb.append(ls);
		return sb.toString();
	}

	public String toTimeSeriesVertical() {
		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");
		int numTransitions = numTransitions();
		int numAttributes = numAttributes();

		for (int t = 0; t < numTransitions; t++) {
			for (int j = 0; j < numAttributes; j++)
				sb.append(usefulObservations[t][0][j] + "\t");
			sb.append(ls);
		}
		for (int j = 0; j < numAttributes; j++)
			sb.append(usefulObservations[numTransitions - 1][0][j + numAttributes] + "\t");
		sb.append(ls);

		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		String ls = System.getProperty("line.separator");
		int numTransitions = numTransitions();
		int numAttributes = numAttributes();
		// int numColumns = numAttributes*2;

		sb.append("Input file: " + usefulObservationsFileName + ls + ls);

		sb.append("Number of transitions: " + numTransitions + ls);
		sb.append("Number of attributes: " + numAttributes + ls);

		sb.append(ls);

		for (int t = 0; t < numTransitions; t++) {

			sb.append("--- Transition " + t + " ---" + ls);
			int numObservations = numObservations(t);

			sb.append(numObservations + " observations." + ls);
			if(numMissing != null) {
				int numMissing = numMissings(t);
				sb.append(numMissing + " have missing values." + ls);
			}

			// sb.append("Observations matrix:"+ls);
			// for (int i=0; i<numObservations; i++) {
			// for(int j=0; j<numColumns; j++) {
			// int attributeId = j%numAttributes;
			// sb.append(attributes.get(attributeId).get(observationsMatrix[t][i][j])+" ");
			// }
			// sb.append(ls);
			// }
			//
			// sb.append(ls);
			//
			// sb.append("Coded observations matrix:"+ls);
			// for (int i=0; i<numObservations; i++) {
			// for(int j=0; j<numColumns; j++) {
			// sb.append(observationsMatrix[t][i][j]+" ");
			// }
			// sb.append(ls);
			// }
			// sb.append(ls);
		}

		sb.append(ls);

		sb.append("Attributes:" + ls);
		for (int i = 0; i < numAttributes; i++) {
			sb.append(attributes.get(i) + ls);
		}

		return sb.toString();

	}

	public static void main(String args[]) {
		System.out.println(new Observations(args[0], null, 0));
	}

}
