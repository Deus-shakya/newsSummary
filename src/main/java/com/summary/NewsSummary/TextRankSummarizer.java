package com.summary.NewsSummary;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class TextRankSummarizer {

    @Autowired
    private Preprocessor preprocessor;

    private static final double DAMPING_FACTOR = 0.85;
    private static final int MAX_ITERATIONS = 100;
    private static final double MIN_DIFF = 0.001;

    // Main summarization method
    public SummaryInfo summarize(String text) {
        List<String> sentences = preprocessor.tokenizeSentences(text);
        int originalSentenceCount = sentences.size();
        int originalWordCount = countWords(text);

        int numSentences = determineSummaryLength(sentences.size());
        Map<String, Set<String>> graph = buildGraph(sentences);
        Map<String, Double> scores = rankSentences(sentences, graph);
        List<String> summarizedSentences = selectTopSentences(scores, numSentences);

        String summarizedText = String.join(" ", summarizedSentences);
        int summarizedWordCount = countWords(summarizedText);
        double reductionRate = 1 - ((double) summarizedWordCount / originalWordCount);

        return new SummaryInfo(
                summarizedSentences,
                originalSentenceCount,
                summarizedSentences.size(),
                originalWordCount,
                summarizedWordCount,
                reductionRate
        );
    }

    // Count words in a given text
    private int countWords(String text) {
        return text.split("\\s+").length;
    }

    // Determine the length of the summary
    private int determineSummaryLength(int numSentencesInText) {
        return (int) Math.ceil(numSentencesInText * 0.5); // 50% of original length
    }

    // Build the graph of sentence similarities
    private Map<String, Set<String>> buildGraph(List<String> sentences) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (String sentence : sentences) {
            graph.put(sentence, new HashSet<>());
        }
        Map<String, Map<String, Double>> tfidfVectors = calculateTFIDFVectors(sentences);
        for (int i = 0; i < sentences.size(); i++) {
            String sentence1 = sentences.get(i);
            for (int j = i + 1; j < sentences.size(); j++) {
                String sentence2 = sentences.get(j);
                double similarity = cosineSimilarity(tfidfVectors.get(sentence1), tfidfVectors.get(sentence2));
                if (similarity > 0.0) {
                    graph.get(sentence1).add(sentence2);
                    graph.get(sentence2).add(sentence1);
                }
            }
        }
        return graph;
    }

    // Calculate TF-IDF vectors for sentences
    private Map<String, Map<String, Double>> calculateTFIDFVectors(List<String> sentences) {
        Set<String> vocabulary = new HashSet<>();
        for (String sentence : sentences) {
            String[] words = sentence.split("\\s+");
            Collections.addAll(vocabulary, words);
        }
        Map<String, Map<String, Integer>> tf = new HashMap<>();
        for (String sentence : sentences) {
            tf.put(sentence, new HashMap<>());
            String[] words = sentence.split("\\s+");
            for (String word : words) {
                tf.get(sentence).put(word, tf.get(sentence).getOrDefault(word, 0) + 1);
            }
        }
        Map<String, Double> idf = new HashMap<>();
        for (String word : vocabulary) {
            int docCount = 0;
            for (String sentence : sentences) {
                if (sentence.contains(word)) {
                    docCount++;
                }
            }
            idf.put(word, Math.log((double) sentences.size() / (docCount + 1)));
        }
        Map<String, Map<String, Double>> tfidfVectors = new HashMap<>();
        for (String sentence : sentences) {
            tfidfVectors.put(sentence, new HashMap<>());
            for (String word : vocabulary) {
                double tfidf = tf.get(sentence).getOrDefault(word, 0) * idf.get(word);
                tfidfVectors.get(sentence).put(word, tfidf);
            }
        }
        return tfidfVectors;
    }

    // Calculate cosine similarity between two vectors
    private double cosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : vector1.entrySet()) {
            dotProduct += entry.getValue() * vector2.getOrDefault(entry.getKey(), 0.0);
        }
        double magnitude1 = calculateMagnitude(vector1);
        double magnitude2 = calculateMagnitude(vector2);
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        }
        return dotProduct / (magnitude1 * magnitude2);
    }

    // Calculate magnitude of a vector
    private double calculateMagnitude(Map<String, Double> vector) {
        double magnitude = 0.0;
        for (double value : vector.values()) {
            magnitude += Math.pow(value, 2);
        }
        return Math.sqrt(magnitude);
    }

    // Rank sentences using TextRank algorithm
    private Map<String, Double> rankSentences(List<String> sentences, Map<String, Set<String>> graph) {
        Map<String, Double> scores = initializeScores(sentences);
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            Map<String, Double> newScores = new HashMap<>();
            double maxChange = 0;
            for (String sentence : sentences) {
                double rank = (1 - DAMPING_FACTOR);
                for (String neighbor : graph.get(sentence)) {
                    rank += DAMPING_FACTOR * (scores.get(neighbor) / graph.get(neighbor).size());
                }
                newScores.put(sentence, rank);
                maxChange = Math.max(maxChange, Math.abs(rank - scores.get(sentence)));
            }
            scores = newScores;
            if (maxChange < MIN_DIFF) break;
        }
        return scores;
    }

    // Initialize scores for TextRank algorithm
    private Map<String, Double> initializeScores(List<String> sentences) {
        Map<String, Double> scores = new HashMap<>();
        double initialScore = 1.0 / sentences.size();
        for (String sentence : sentences) {
            scores.put(sentence, initialScore);
        }
        return scores;
    }

    // Select top sentences based on their scores
    private List<String> selectTopSentences(Map<String, Double> scores, int numSentences) {
        List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(scores.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        List<String> topSentences = new ArrayList<>();
        for (int i = 0; i < Math.min(numSentences, sortedEntries.size()); i++) {
            topSentences.add(sortedEntries.get(i).getKey());
        }
        return topSentences;
    }
}

// Class to hold summary information
class SummaryInfo {
    private List<String> summarizedText;
    private int originalSentenceCount;
    private int summarizedSentenceCount;
    private int originalWordCount;
    private int summarizedWordCount;
    private double reductionRate;

    public SummaryInfo(List<String> summarizedText, int originalSentenceCount, int summarizedSentenceCount,
                       int originalWordCount, int summarizedWordCount, double reductionRate) {
        this.summarizedText = summarizedText;
        this.originalSentenceCount = originalSentenceCount;
        this.summarizedSentenceCount = summarizedSentenceCount;
        this.originalWordCount = originalWordCount;
        this.summarizedWordCount = summarizedWordCount;
        this.reductionRate = reductionRate;
    }

    // Getters
    public List<String> getSummarizedText() { return summarizedText; }
    public int getOriginalSentenceCount() { return originalSentenceCount; }
    public int getSummarizedSentenceCount() { return summarizedSentenceCount; }
    public int getOriginalWordCount() { return originalWordCount; }
    public int getSummarizedWordCount() { return summarizedWordCount; }
    public double getReductionRate() { return reductionRate; }
}