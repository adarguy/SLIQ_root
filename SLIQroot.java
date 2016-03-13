import java.io.*;
import java.util.*;

public class SLIQroot {

	String[] attributes;
	Boolean[] isNumeric;
	Set<String> ClassValues = new HashSet<String>();
	Integer yIndex; //this is index of the class attribute
	
	class SplitTest {
		String attribute;
		Boolean numeric; //The program only handles numeric attributes for now.
		Object splitval; //Numeric: A<=v. Categorical: A==v
		Double entropy;
		
		public String toString() {
			if (numeric) return attribute + "<=" + splitval;
			else return attribute + "==" + splitval;
		}
	}
	
	//rid-classlabel structure. 
	//The structure is a Map. Given an rid, we can quickly find the class value for the record.
	Map<Integer, String> ClassList = new HashMap<Integer, String>(); 
	
	//A histogram is nothing else, but a list of classvalue-count pairs. 
	//For example, ["yes":5, "no":7] is a histogram, saying that 
	//there are 5 tuples with "yes" as class value, and 7 tuples with "no" as class value.
	//We organize a histogram as a map indexed by class values. 
	//Given a class value, we can quickly find the corresponding count.
	//Here we are creating the root histogram that is the overall histogram of
	//class values and their counts for the dataset as a whole.
	Map<String, Integer> roothistogram = new HashMap<String, Integer>(); 
	
	//We construct a histogram pair for each numeric attribute.
	//Why a pair of histograms? 
	//Well, given a threshold for the attribute, the tuples will be split
	//into a left bag and a right bag. 
	//Each resulting bag needs a histogram of classvalue-count pairs.
	//These histograms will be use to compute the entropy of the split. 
	class HistPair {
		Map<String, Integer> histogram0;
		Map<String, Integer> histogram1;		
	}
	
	//Histogram pairs are stored in a map indexed by the attribute names.
	//For each attribute, we want to record the histogram of the best split, in terms of entropy.
	Map<String, HistPair> att_histpair_map = new HashMap<String, HistPair>();
	
	//This is the separator for the text file we read.
	String sep = "[\t, ]"; 
	
	
	public SLIQroot(String filename, int yIndex) throws Exception {
		
	    this.yIndex = yIndex;
		
	    System.out.println("Reading input file and writing attribute files...");
	    long startTime = System.nanoTime();
	    
		BufferedReader datafile = new BufferedReader( new FileReader(filename) );
		
		//read first line of attribute names, and set the "attributes" and "isNumeric" arrays.
        attributes = datafile.readLine().split(sep);
        isNumeric = new Boolean[attributes.length];
        
        //create a file for each (non-class) attribute
        BufferedWriter[] attributefiles = new BufferedWriter[attributes.length];
        for(int i=0; i<attributes.length; i++)
        	if(i != yIndex) 
        		attributefiles[i] = new BufferedWriter( new FileWriter(attributes[i]+".txt") );
        
        //read second line of data and determine numeric or not for attributes
        String[] strArray = datafile.readLine().split(sep);
        for(int i=0; i<attributes.length; i++) {
        	if(i == yIndex) {
        		isNumeric[i] = false;
        		continue;
        	}
        	
        	try { 
        		Double.valueOf(strArray[i]);
        		isNumeric[i] = true;
        	}
        	catch(NumberFormatException nfe) { 
        		isNumeric[i] = false;
        	}
        }
        
        //Write a separate text file for each attribute.
        //Introduce rid for each record. 
        //If the attribute is the class one, add to ClassList and update roothistogram.
        Integer rid=0;
        do {
        	//if(rid % 1000 == 0) System.out.println(rid);
        	for(int i=0; i<attributes.length; i++) {
        		if(i != yIndex) {
        			attributefiles[i].write(rid + " " + strArray[i] + "\n");
        		}
        		else {
        			String classval = strArray[i];
        			ClassValues.add(classval);
        			ClassList.put(rid, classval);
        			
        			int cnt = 0;
        			if (roothistogram.containsKey(classval)) 
        				cnt = roothistogram.get(classval);
        			roothistogram.put(classval, cnt+1);
        		}
        	}
        	rid++;
        	
        	String line = datafile.readLine();
        	if(line == null) break;
        	strArray = line.split(sep);
        	
        } while ( true );
               
        //closing files
        for(int i=0; i<attributes.length; i++)
        	if(i != yIndex) attributefiles[i].close();
        datafile.close();
        
        System.out.println("Finished reading input file and writing attribute files. ");
		long endTime = System.nanoTime();
		long duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
		System.out.println("Time: " + duration/(1000000*1000.0) + " sec");
        
        
		System.out.println("Sorting attribute files... ");
		startTime = System.nanoTime();
		
        //sort the attribute files; in windows install first gnu coreutils
        //sort performs a disk-based sort based on two-phase multiway merge sort.  
        //adjust the path for your installation
        String sortpath = "\"C:/Program Files (x86)/GnuWin32/bin/sort.exe\"";
        for(int i=0; i<attributes.length; i++)
        	if(i != yIndex && isNumeric[i]) {
        		String sortcmd = sortpath + " -nk 2 " + attributes[i] + ".txt -o " + attributes[i] + ".txt";
        		System.out.println(sortcmd);
        		Runtime.getRuntime().exec(sortcmd).waitFor();
        	}
        
        
        System.out.println("Finished sorting attribute files. ");
		endTime = System.nanoTime();
		duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
		System.out.println("Time: " + duration/(1000000*1000.0) + " sec");
	}
		
			
	//i specifies the index of an attribute.
	//The return value is the best SplitTest for the specified attribute.
	SplitTest EvaluateSplits(int i) throws Exception {
		if(i==this.yIndex || isNumeric[i]==false) return null;
		
		String A = this.attributes[i]; 
		//System.out.println("Processing file "+A+".txt");
		BufferedReader Afile = new BufferedReader( new FileReader(A+".txt") );
		String line;
		SplitTest stest = null;
		double entropy_split_min = Double.MAX_VALUE;
		
		//Initialize the histogram pair for attribute A. 
		att_histpair_map.put(A, createHistPair(roothistogram));
		
		Double prev = Double.MIN_VALUE;
		
		while( (line=Afile.readLine()) != null ) {	
		
			String[] strArray = line.split(sep);
			Integer rid = Integer.parseInt(strArray[0]);
			Double v = Double.parseDouble(strArray[1]);
			
			if(prev < v) {
				double entropy_split = entropySplit(
							att_histpair_map.get(A).histogram0, 
							att_histpair_map.get(A).histogram1);
				
				//System.out.println("Entropy for "+prev + " is " + entropy_split);

				if ( entropy_split < entropy_split_min ) {
					stest = new SplitTest();
					stest.attribute = A; stest.numeric = true; stest.splitval = prev;
					stest.entropy = entropy_split;
					entropy_split_min = entropy_split; 
				}
				
				prev=v;
			}
			
			String classval = ClassList.get(rid);
			
			updateHistograms(att_histpair_map.get(A).histogram0, 
							 att_histpair_map.get(A).histogram1, 
							 classval, 1);
			
			
		}
		Afile.close();
		
		return stest;
	}
	
	
	SplitTest BestAttributeSplitTest() throws Exception {
		System.out.println("Finding best attribute split test...");
	    long startTime = System.nanoTime();
		
		Double minentropy = Double.MAX_VALUE;
		SplitTest minstest = null;
		
		for(int i=0; i<this.attributes.length; i++) {
			SplitTest stest = this.EvaluateSplits(i);
			if (stest == null) 
				continue;
			if(stest.entropy < minentropy) {
				minentropy = stest.entropy;
				minstest = stest;
			}
		}
		
		
        System.out.println("Finished finding best attribute split test. ");
		long endTime = System.nanoTime();
		long duration = (endTime - startTime);  //divide by 1000000 to get milliseconds.
		System.out.println("Time: " + duration/(1000000*1000.0) + " sec");
		
		return minstest;
	}
	
	
	void updateHistograms(Map<String, Integer> h0, Map<String, Integer> h1, String classval, Integer delta) {
		h0.put(classval, h0.get(classval)+delta);
		h1.put(classval, h1.get(classval)-delta);
	}

	/*
	 * ************************************************************
	 * Auxiliary methods
	 * ************************************************************
	 */
	
	Map<String, Integer> zeroHist() {
		Map<String, Integer> result = new HashMap<String, Integer>();
		for(String classval : ClassValues) 
			result.put(classval, 0);
		return result;
	}
	
	HistPair createHistPair (Map<String,Integer> histogram) {
		HistPair hp = new HistPair();
		hp.histogram1 = new HashMap<String,Integer>(histogram);
		hp.histogram0 = zeroHist();
		return hp;
	}
	
	Double entropyHistogram(Map<String, Integer> histogram) {
		Double result = 0.0;
		Integer sum = 0;
		
		for(String classval : histogram.keySet()) 
			sum += histogram.get(classval);
		
		for(String classval : histogram.keySet()) {
			Double r = histogram.get(classval)/(double)sum;
			if(r>0) result -= r*Math.log(r)/Math.log(2);
		}
		
		return result;
	}
	
	Double entropySplit(List< Map<String, Integer> > histograms) {
		Double result = 0.0;
		Integer sumall = 0;

		List<Integer> sumList = new ArrayList<Integer>();
		List<Double> entropyList = new ArrayList<Double>();
		
		for(Map<String, Integer> h : histograms) {
			Integer sum = 0;
			for(String classval : h.keySet()) 
				sum += h.get(classval);
			sumall += sum;
			sumList.add(sum);
			entropyList.add(entropyHistogram(h));
		}
		
		for(int i=0; i<sumList.size(); i++) 
			result += entropyList.get(i) * sumList.get(i)/(double)sumall;
		
		return result;
	}
	
	Double entropySplit(Map<String, Integer> histogram0, Map<String, Integer> histogram1) {
		List< Map<String, Integer> > hlist = new ArrayList< Map<String, Integer> >();
		hlist.add(histogram0); hlist.add(histogram1);
		return entropySplit(hlist);
	}
	

	public static void main(String[] args) throws Exception {
		//Specify filename, class attribute index, and minNumInstancesToSplit
		SLIQroot sliq = new SLIQroot("car.txt", 3);
		//SLIQroot sliq = new SLIQroot("intrusion10pct.csv", 3);
		System.out.println("Class values are: " + sliq.ClassValues);
		System.out.println(sliq.BestAttributeSplitTest());
	}
}