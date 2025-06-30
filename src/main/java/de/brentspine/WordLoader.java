package de.brentspine;

import org.json.JSONObject;

import java.io.*;
import java.util.*;

// Loads the words from a specified file and provides methods to access them.
public class WordLoader {

    private static final int MIN_LENGTH = 3; // Minimum length of words to be considered

    private String fileName;
    private Set<String> allWords;
    private Set<String> usedWords;

    private Set<String> newWords;

    private Map<Character, Integer> alphabet;
    private Map<Character, Integer> alphabetRules;


    public WordLoader(String fileName, Map<Character, Integer> alphabetRules) {
        this.fileName = fileName;
        this.alphabetRules = alphabetRules;
        this.newWords = new HashSet<>();
        this.allWords = new HashSet<>();
        this.usedWords = new HashSet<>();
        loadWords();
        reset();
    }

    public void reset() {
        resetAlphabet();
        usedWords.clear();
        saveWords();
        newWords.clear();
    }

    private void resetAlphabet() {
        this.alphabet = new HashMap<>();
        for (char c : alphabetRules.keySet()) {
            alphabet.put(c, alphabetRules.get(c));
        }
    }

    public String findWord(String syllable) {
        String word = "/suicide";
        int maxScore = -1;
        for(String w : allWords) {
            if(!w.contains(syllable)) continue;
            if(usedWords.contains(w)) continue; // Skip already used words
            int score = getAlphabetScore(w);
            if(score > maxScore) {
                maxScore = score;
                word = w;
            }
        }
        usedWords.add(word);
        return word;
    }

    /*
    Suppose the alphabet is a map<Character, Integer>. The Integer stands for the amount of times a certain character can give a point. We return the amount of points. Example:

    [<a, 2>, <b, 1>, <c, 3>, <d, 1>, <e, 0> ...]
    Word: "aabbc"
    Score: 11101 = 4 points (2 for 'a', 1 for first 'b' (0 for second 'b', since map->b = 1), 1 for 'c')
    Word: "aaaa"
    Score: 1100 = 2 points (2 for 'a', 0 for second 'a' and third 'a')
    Word: "abcde"
    Score: 11110 = 4 points (1 for 'a', 1 for 'b', 1 for 'c', 1 for 'd', 0 for 'e')
    Word: "abcdeabcde"
    Score: 1111010100 = 6 points (1 for 'a', 1 for 'b', 1 for 'c', 1 for 'd', 0 for 'e' in first half. Then only points for 'a' and 'c' since they have still possible score left)
     */
    private int getAlphabetScore(String word) {
        int score = 0;

        for (char c : word.toCharArray()) {
            if (alphabet.containsKey(c) && alphabet.get(c) > 0) {
                score++;
                alphabet.put(c, alphabet.get(c) - 1);
            }
        }

        return score;
    }

    private void loadWords() {
        System.out.println("Loading words from external file...");

        this.allWords = new HashSet<>();
        this.usedWords = new HashSet<>();

        File externalFile = new File(fileName);

        if (!externalFile.exists()) {
            System.out.println("External file does not exist: " + externalFile.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(externalFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();
                if (word.startsWith(";")) continue;
                if (word.length() >= MIN_LENGTH) allWords.add(word.toLowerCase());
            }
            System.out.println("Loaded " + allWords.size() + " words from external file: " + externalFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error reading from external file: " + externalFile.getAbsolutePath());
            e.printStackTrace();
        }
    }

    public int saveWords() {
        System.out.println("Saving " + newWords.size() + " new words...");

        File externalFile = new File(fileName);

        File parentDir = externalFile.getParentFile();
        if (!parentDir.exists()) parentDir.mkdirs();

        try (FileWriter writer = new FileWriter(externalFile, true)) {
            for (String word : newWords) {
                writer.write(word + "\n");
            }
            writer.flush();
            System.out.println("Saved new words to external file: " + externalFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error writing to external file: " + externalFile.getAbsolutePath());
            e.printStackTrace();
        }
        int wordsAdded = newWords.size();
        newWords.clear();
        return wordsAdded;
    }


    public boolean addNewWord(String word) {
        word = word.replace(" ", "");
        word = word.replaceAll("\\d+", ""); // Remove digits
        if (allWords.contains(word.toLowerCase())) {
            return false;
        }
        System.out.println("New Word: " + word);
        newWords.add(word.toLowerCase());
        allWords.add(word.toLowerCase());
        usedWords.add(word.toLowerCase());
        return true;
    }


    public Map<Character, Integer> getAlphabetRules() {
        return alphabetRules;
    }

    public void setAlphabetRules(Map<Character, Integer> alphabetRules) {
        this.alphabetRules = alphabetRules;
    }

    public int getWordCount() {
        return allWords.size();
    }

    public void setAlphabet(Map<Character, Integer> alphabet) {
        this.alphabet = new HashMap<>(alphabet);
    }

    public Map<Character, Integer> getAlphabet() {
        return alphabet;
    }

    public void setAlphabetFromJson(JSONObject jsonObject) {
        this.alphabet = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            char character = key.charAt(0);
            int value = jsonObject.getInt(key);
            alphabet.put(character, value);
        }
    }

}
