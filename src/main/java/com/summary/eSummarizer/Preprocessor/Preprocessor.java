package com.summary.eSummarizer.Preprocessor;

import com.summary.eSummarizer.Service.LemmatizationService;
import com.summary.eSummarizer.Service.POSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class Preprocessor {

    private static final Logger logger = LoggerFactory.getLogger(Preprocessor.class);

    private final Set<String> STOPWORDS;

    @Autowired
    private LemmatizationService lemmatizationService;

    @Autowired
    private POSService posService;

    public Preprocessor() {
        STOPWORDS = loadStopwordsFromCsv("stopwords.csv");
    }

    private Set<String> loadStopwordsFromCsv(String filename) {
        Set<String> stopwords = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(filename).getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] words = line.split(",");
                for (String word : words) {
                    if (!word.isEmpty()) {
                        stopwords.add(word.trim());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading stopwords: " + e.getMessage());
            // throw a custom exception here
        }
        return stopwords;
    }

    // Method to tokenize the input text into sentences
    public List<String> tokenizeSentences(String text) {
        return Arrays.asList(text.split("(?<!\\w\\.\\w.)(?<![A-Z][a-z]\\.)(?<=\\.|\\?|\\!)\\s"));
    }

    // Method to tokenize a single sentence into words
    public List<String> tokenizeWords(String sentence) {
        return Arrays.asList(sentence.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+"));
    }

    // Method to remove stopwords and apply lemmatization on a list of sentences
    public List<String> removeStopwordsAndLemmatize(List<String> sentences) {
        // Use an array to hold the counts
        final int[] stopwordCount = {0}; // Array to hold the number of stopwords removed
        final int[] lemmatizedCount = {0}; // Array to hold the total lemmatized words

        List<String> processed = sentences.stream().map(sentence -> {
            List<String> words = tokenizeWords(sentence);
            List<String> filteredWords = words.stream()
                    .filter(word -> {
                        boolean isStopword = STOPWORDS.contains(word);
                        if (isStopword) {
                            stopwordCount[0]++; // Increment stopword count
                        }
                        return !isStopword;  // Keep non-stopwords
                    }).collect(Collectors.toList()); // Collect filtered words

            List<String> lemmatizedWords = filteredWords.stream()
                    .map(lemmatizationService::lemmatize) // Lemmatization
                    .collect(Collectors.toList()); // Collect lemmatized words
            lemmatizedCount[0] += lemmatizedWords.size(); // Increment lemmatized words count

            return String.join(" ", lemmatizedWords); // Reconstruct the processed sentence
        }).collect(Collectors.toList());

        // Log the summary after processing all sentences
        logger.info("Removed stopwords: {}, Lemmatized words: {}", stopwordCount[0], lemmatizedCount[0]);
        return processed; // Return processed sentences
    }

    // New method to tag parts of speech (POS) for each word in the sentences
    public List<List<String>> tagPartsOfSpeech(List<String> sentences) {
        // Use an array to hold the count of tagged words
        final int[] taggedWordCount = {0}; // Array to hold the total tagged words

        List<List<String>> taggedSentences = sentences.stream().map(sentence -> {
            List<String> words = tokenizeWords(sentence);
            List<String> taggedWords = words.stream()
                    .map(word -> {
                        String taggedWord = word + "|" + posService.getPartOfSpeech(word);  // Tag word with POS
                        taggedWordCount[0]++; // Increment tagged words count
                        return taggedWord;
                    })
                    .collect(Collectors.toList());
            return taggedWords;
        }).collect(Collectors.toList());

        // Log the summary for POS tagging
        logger.info("POSTagged words: {}", taggedWordCount[0]);
        return taggedSentences;
    }
}
