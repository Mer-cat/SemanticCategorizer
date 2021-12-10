package categorizer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

/**
 * Uses the Harvard Inquirer Semantic Categorization dictionary to characterize the words
 * of a given input file 
 * 
 * Outputs two files: category_by_word and percentages, which reflects percentage of words that fit a given category
 * (with overlap between categories)
 * 
 * @author Mercy Bickell
 *
 */
public class SemanticCategorizer {
	
	private HashMap<String, ArrayList<String>> categorizationDict; // Maps from dictionary words to list of its categorizations
	private ArrayList<String> categories; // List of Harvard categories
	private HashMap<String, Integer> freqList; // Maps from each unique word in data to how many times that word appears
	private HashMap<String, Integer> categoryFrequencies; // Maps from category to how many tokens are categorized as that category
	// private float totalCatFreq = 0; 
	private long totalTokens = 0; // Total number of tokens in dataset
	private ArrayList<String> uncategorizableWords;
	private String fileEnding;
	
	/**
	 * Constructs a SemanticCategorizer, which categories
	 * all the words in the given file according to the
	 * Harvard Inquirer Categories
	 * 
	 * @param fileToCategorize
	 */
	public SemanticCategorizer(String fileToCategorize, String fileEnding) {
		categorizationDict = new HashMap<String, ArrayList<String>>();
		this.fileEnding = fileEnding;
		
		createDictionary();
		buildFrequencyList(fileToCategorize);
		categorizeFrequencyList();
		getCategoryPercentages();
	}

	/**
	 * Creates a HashMap from the Harvard Semantic Categorization dictionary csv file
	 */
	private void createDictionary() {
		try {
			BufferedReader br = new BufferedReader(new FileReader("data/harvard.csv"));
			
			String line = br.readLine(); // First line is categories
			makeCategories(line);
			
			line = br.readLine(); // Second line is counts for each category
			
			// Dictionary entries begin on 3rd line
			line = br.readLine();
			String previousWord = "";
			
			while (line != null) {
				
				String[] row = line.split(",");
				String word = row[0].toLowerCase();
				// Check to see if word has multiple definitions
				if (word.matches(".*#[0-9]+$")) {
					word = word.split("#")[0];
				}
				
				List<String> wholeList = Arrays.asList(row);
				// Cut off at size of ca
				ArrayList<String> categorizedAs = new ArrayList<>(wholeList.subList(2, categories.size() + 2));
				// System.out.println(word + ": " + categorizedAs);
				categorizedAs.removeAll(Arrays.asList("", null)); // Remove empty entries

				// If this is not the first/only definition
				if (word.equals(previousWord)) {
					categorizationDict.put(word, getUnionOfLists(categorizationDict.get(word), categorizedAs));
				} else {
					categorizationDict.put(word, categorizedAs);
				}
				previousWord = word;
				line = br.readLine();
			}
			
			br.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private ArrayList<String> getUnionOfLists(List<String> list1, List<String> list2) {
		
		Set<String> set = new HashSet<>();
		set.addAll(list1);
		set.addAll(list2);
		
		return new ArrayList<String>(set);
	}
	
	/**
	 * Makes a list of categories from the top line of the Harvard csv file
	 * @param line Top line of the Harvard csv file
	 */
	private void makeCategories(String line) {
		String[] catArray = line.split(",");
		categories = new ArrayList<>(Arrays.asList(catArray));
		categories.remove(0); // Remove "Entry"
		categories.remove(0); // Remove "Source"
		// Remove "Other tags" and "Defined"
		categories.remove(categories.size()-1);
		categories.remove(categories.size()-1);
	}
	
	/**
	 * Lemmatizes words from file, then
	 * builds freqList of words from the given file
	 * 
	 * @param fileName name of the file
	 */
	private void buildFrequencyList(String fileName) {
		freqList = new HashMap<String, Integer>();
		
		try {
			Path path = FileSystems.getDefault().getPath(fileName);
			BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8);
			
			String line = br.readLine();
			
			while (line != null) {
				Properties props = new Properties();
				props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
				StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
				CoreDocument document = pipeline.processToCoreDocument(line);
		
				// Untokenizable words get dropped
				for (CoreLabel token : document.tokens()) {
					String word = token.lemma().toLowerCase();
					if (freqList.containsKey(word)) {
						freqList.put(word, freqList.get(word) + 1);
					} else {
						freqList.put(word, 1);
					}
					totalTokens = totalTokens + 1;
				}

				line = br.readLine();
			}
			
			br.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Once a frequency list of lemmas has been built, categorizes each unique lemma
	 * and outputs the categorization list for each lemma in category_by_word.txt
	 */
	private void categorizeFrequencyList() {
		File output = new File("data/category_by_word_" + fileEnding + ".txt");
		categoryFrequencies = new HashMap<String, Integer>();
		uncategorizableWords = new ArrayList<>();
		
		for (String category : categories) {
			categoryFrequencies.put(category, 0);
		}
		
		try {
			PrintWriter writer = new PrintWriter(output);
			
			// For every unique word from the data
			for (String word : freqList.keySet()) {
				// Grab the categorizations for that word
				ArrayList<String> cats = categorizationDict.get(word);
				if (cats != null) {
					// For each category in that list, go through and 
					// add increase that category's frequency by the 
					// number of times of the word appears
					for (String cat : cats) {
						if (categories.contains(cat)) {
							categoryFrequencies.put(cat, categoryFrequencies.get(cat) + (1*freqList.get(word)));
							// totalCatFreq = totalCatFreq + 1;
						}
					}
					writer.println(word + " is categorized as: " + cats);
				} else {
					uncategorizableWords.add(word);
					writer.println(word + " cannot be categorized");
				}
			}
			
			writer.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Calculates the percentage of the text that fit a certain category per category
	 * and outputs these percentages in percentages.txt
	 * Note that these percentages will not add up to 100% due to overlap between categories
	 */
	private void getCategoryPercentages() {
		File output = new File("data/percentages_" + fileEnding + ".txt");
		
		try {
			PrintWriter writer = new PrintWriter(output);
		
			for (String cat : categories) {
				// Number of words in sample matching the category / number of words in sample
				float decimal = (float) categoryFrequencies.get(cat) / totalTokens;
				float percentage = 100 * decimal;
				writer.println(percentage + "% " + cat);
			}
		
			writer.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public ArrayList<String> getUncategorizableWords() {
		return uncategorizableWords;
	}
	
	/**
	 * Prints out the categorization dictionary
	 */
	public void printCategorizationDict() {
		Iterator<Entry<String, ArrayList<String>>> iter = categorizationDict.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<String, ArrayList<String>> mapElement = (Map.Entry<String, ArrayList<String>>)iter.next();
			String word = mapElement.getKey();
			ArrayList<String> categorizedAs = mapElement.getValue();
			System.out.println(word + " : " + categorizedAs);
		}
		
	}
	
	/**
	 * Get frequency list of lemmas
	 * @return freqList
	 */
	public HashMap<String, Integer> getFreqList() {
		return freqList;
	}
	
	public long getTotalTokens() {
		return totalTokens;
	}
	
	/**
	 * Prints frequencies per category
	 */
	public void printCategoryFrequencies() {
		System.out.println(categoryFrequencies);
	}
	
	public static void main(String[] args) {
		SemanticCategorizer tester = new SemanticCategorizer("data/coca_text_fic_2008_first60.txt", "COCA");
		// tester.printCategorizations();
		// System.out.println(tester.getFreqList());
		// tester.printCategoryFrequencies();
		System.out.println("There were " + tester.getTotalTokens() + " total tokens in the given corpus");
		System.out.println("There are " + tester.getUncategorizableWords().size() + " uncategorizable lemmas");
		System.out.println("There are " + tester.getFreqList().size() + " total lemmas" );
	}

}
