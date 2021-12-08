package categorizer;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

public class SemanticCategorizer {
	// Make hashmap mapping from each dictionary word to list of their categories (since 1 word can have multiple categories)
	// Iterate over txt file, tokenizing, lemmatizing, and lowercasing
	// Make freqlist from words in txt file
	// For each word in freq list, look it up in the hashmap 
	// Build output.txt by listing word, its frequency in the input file, and its semantic categories
	// Get totals by category
	
	private HashMap<String, ArrayList<String>> categorizationDict;
	private ArrayList<String> categories;
	private HashMap<String, Integer> freqList;
	private HashMap<String, Integer> categoryFrequencies;
	private float totalCatFreq = 0;
	
	/**
	 * Constructs a SemanticCategorizer, which categories
	 * all the words in the given file according to the
	 * Harvard Inquirer Categories
	 * 
	 * @param fileToCategorize
	 */
	public SemanticCategorizer(String fileToCategorize) {
		categorizationDict = new HashMap<String, ArrayList<String>>();
		
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
			
			line = br.readLine(); // Second line is counts each category
			storeCatCounts(line);
			
			// Dictionary entries begin on 3rd line
			line = br.readLine();
			
			while (line != null) {
				// TODO: Handle things like "About#1" and "About#2"
				String[] row = line.split(",");
				String word = row[0].toLowerCase();
				ArrayList<String> categorizedAs = new ArrayList<>(Arrays.asList(row).subList(2, row.length));
				categorizedAs.removeAll(Arrays.asList("", null)); // Remove empty entries
				// System.out.println(word);
				// System.out.println(categorizedAs);
				categorizationDict.put(word, categorizedAs);
				line = br.readLine();
			}
			
			br.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
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
		// Note that last two entries are "Other tags" and "Defined," not real categories
	}
	
	private void storeCatCounts(String line) {
		// TODO if needed, store counts for overall rarity
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
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			
			String line = br.readLine();
			
			while (line != null) {
				Properties props = new Properties();
				props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
				StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
				CoreDocument document = pipeline.processToCoreDocument(line);
		
				for (CoreLabel token : document.tokens()) {
					String word = token.lemma();
					if (freqList.containsKey(word)) {
						freqList.put(word, freqList.get(word) + 1);
					} else {
						freqList.put(word, 1);
					}
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
		File output = new File("data/category_by_word.txt");
		categoryFrequencies = new HashMap<String, Integer>();
		for (String category : categories) {
			categoryFrequencies.put(category, 0);
		}
		
		try {
			PrintWriter writer = new PrintWriter(output);
			
			for (String word : freqList.keySet()) {
				ArrayList<String> cats = categorizationDict.get(word);
				if (cats != null) {
					for (String cat : cats) {
						if (categories.contains(cat)) {
							categoryFrequencies.put(cat, categoryFrequencies.get(cat) + 1);
							totalCatFreq = totalCatFreq + 1;
						}
					}
					writer.println(word + " is categorized as: " + cats);
				} else {
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
	 * TODO: Some categories are subcategories. How could I deal with this?
	 */
	private void getCategoryPercentages() {
		File output = new File("data/percentages.txt");
		
		try {
			PrintWriter writer = new PrintWriter(output);
		
			for (String cat : categories) {
				float decimal = (float) categoryFrequencies.get(cat) / totalCatFreq;
				float percentage = 100 * decimal;
				writer.println(percentage + "% " + cat);
			}
		
			writer.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
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
	 * Prints frequency list of lemmas
	 */
	public void printFreqList() {
		System.out.println(freqList);
	}
	
	/**
	 * Prints frequencies per category
	 */
	public void printCategoryFrequencies() {
		System.out.println(categoryFrequencies);
	}
	
	public static void main(String[] args) {
		SemanticCategorizer tester = new SemanticCategorizer("data/test.txt");
		// tester.printCategorizations();
		tester.printFreqList();
		// tester.printCategoryFrequencies();
	}

}
