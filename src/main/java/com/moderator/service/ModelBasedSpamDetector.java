package com.moderator.service;

import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.word2vec.Word2Vec;
import org.deeplearning4j.text.sentenceiterator.CollectionSentenceIterator;
import org.deeplearning4j.text.tokenization.tokenizer.preprocessor.CommonPreprocessor;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for detecting spam in comments using a pre-trained model.
 * Uses Word2Vec embeddings and a simple neural network for classification.
 */
@Service
public class ModelBasedSpamDetector {

    private static final Logger logger = LoggerFactory.getLogger(ModelBasedSpamDetector.class);

    @Value("${spam.detection.threshold:0.5}")
    private double spamThreshold;
    
    @Value("${spam.detection.model.path:src/main/resources/spam-model.bin}")
    private String modelPath;
    
    @Value("${spam.detection.vector.size:100}")
    private int vectorSize;
    
    @Value("${spam.detection.window.size:5}")
    private int windowSize;
    
    @Value("${spam.detection.min.word.frequency:1}")
    private int minWordFrequency;

    private Word2Vec word2Vec;
    private TokenizerFactory tokenizerFactory;
    private static final Pattern URL_PATTERN = Pattern.compile(
        "\\b(https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|]"
    );
    
    // Spam and non-spam training examples
    private final List<String> spamExamples = Arrays.asList(
        "Buy cheap products now! Click here for amazing deals!",
        "Make money fast! Work from home and earn thousands!",
        "Free giveaway! Enter now to win a prize!",
        "Check out my channel and subscribe for more content!",
        "Like and share this video for a chance to win!",
        "Follow me on social media for exclusive content!",
        "Limited time offer! Don't miss out on this opportunity!",
        "Investment opportunity! Guaranteed returns!",
        "Click the link in my bio for special access!",
        "Subscribe to my channel for daily uploads!"
    );
    
    private final List<String> nonSpamExamples = Arrays.asList(
        "Great video! Really enjoyed watching it.",
        "Thanks for sharing this information.",
        "I learned a lot from this content.",
        "This is one of the best videos on this topic.",
        "The explanation was very clear and helpful.",
        "I've been looking for this information for a while.",
        "This video helped me solve my problem.",
        "I appreciate the effort you put into making this.",
        "Looking forward to more content like this.",
        "This is exactly what I needed, thank you!"
    );

    /**
     * Constructor for ModelBasedSpamDetector.
     */
    public ModelBasedSpamDetector() {
        logger.info("Initializing ModelBasedSpamDetector");
        this.tokenizerFactory = new DefaultTokenizerFactory();
        this.tokenizerFactory.setTokenPreProcessor(new CommonPreprocessor());
    }

    /**
     * Initializes the ModelBasedSpamDetector.
     * This method is called after dependency injection is complete.
     */
    @PostConstruct
    public void initialize() {
        logger.info("ModelBasedSpamDetector initializing with threshold: {}", spamThreshold);
        
        try {
            // Try to load existing model
            File modelFile = new File(modelPath);
            if (modelFile.exists()) {
                logger.info("Loading pre-trained model from {}", modelPath);
                word2Vec = WordVectorSerializer.readWord2VecModel(modelFile);
            } else {
                logger.info("No pre-trained model found. Training new model...");
                trainModel();
                // Save the model for future use
                WordVectorSerializer.writeWord2VecModel(word2Vec, modelFile);
                logger.info("Model saved to {}", modelPath);
            }
        } catch (Exception e) {
            logger.error("Error initializing model", e);
            // Fall back to training a new model
            trainModel();
        }
    }
    
    /**
     * Trains a new Word2Vec model on spam and non-spam examples.
     */
    private void trainModel() {
        logger.info("Training new Word2Vec model");
        
        // Combine spam and non-spam examples
        List<String> allExamples = new ArrayList<>();
        allExamples.addAll(spamExamples);
        allExamples.addAll(nonSpamExamples);
        
        // Create sentence iterator
        CollectionSentenceIterator sentenceIterator = new CollectionSentenceIterator(allExamples);
        
        // Build and train the model
        word2Vec = new Word2Vec.Builder()
                .minWordFrequency(minWordFrequency)
                .iterations(5)
                .layerSize(vectorSize)
                .seed(42)
                .windowSize(windowSize)
                .tokenizerFactory(tokenizerFactory)
                .iterate(sentenceIterator)
                .build();
        
        word2Vec.fit();
        logger.info("Word2Vec model training complete");
    }
    
    /**
     * Converts a text into a vector representation.
     *
     * @param text The text to convert
     * @return A vector representation of the text
     */
    private INDArray textToVector(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Nd4j.zeros(vectorSize);
        }
        
        // Tokenize the text
        List<String> tokens = tokenizerFactory.create(text).getTokens();
        
        // Initialize the vector
        INDArray vector = Nd4j.zeros(vectorSize);
        int count = 0;
        
        // Sum the vectors of all words
        for (String token : tokens) {
            if (word2Vec.hasWord(token)) {
                vector.addi(word2Vec.getWordVectorMatrix(token));
                count++;
            }
        }
        
        // Average the vectors if we found any words
        if (count > 0) {
            vector.divi(count);
        }
        
        return vector;
    }
    
    /**
     * Calculates the similarity between two vectors.
     *
     * @param vec1 The first vector
     * @param vec2 The second vector
     * @return The cosine similarity between the vectors
     */
    private double cosineSimilarity(INDArray vec1, INDArray vec2) {
        double dotProduct = vec1.mul(vec2).sumNumber().doubleValue();
        double norm1 = Math.sqrt(vec1.mul(vec1).sumNumber().doubleValue());
        double norm2 = Math.sqrt(vec2.mul(vec2).sumNumber().doubleValue());
        
        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }
        
        return dotProduct / (norm1 * norm2);
    }
    
    /**
     * Determines if a comment is spam using the pre-trained model.
     *
     * @param comment The comment to check
     * @return true if the comment is spam, false otherwise
     */
    public boolean isSpam(String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            return false;
        }

        String lowerComment = comment.toLowerCase();
        
        // Check for URLs (still a strong indicator of spam)
        if (URL_PATTERN.matcher(lowerComment).find()) {
            logger.debug("Comment contains URL, classified as spam");
            return true;
        }
        
        try {
            // Convert the comment to a vector
            INDArray commentVector = textToVector(comment);
            
            // Calculate similarity with spam examples
            double maxSpamSimilarity = 0.0;
            for (String spamExample : spamExamples) {
                INDArray spamVector = textToVector(spamExample);
                double similarity = cosineSimilarity(commentVector, spamVector);
                maxSpamSimilarity = Math.max(maxSpamSimilarity, similarity);
            }
            
            // Calculate similarity with non-spam examples
            double maxNonSpamSimilarity = 0.0;
            for (String nonSpamExample : nonSpamExamples) {
                INDArray nonSpamVector = textToVector(nonSpamExample);
                double similarity = cosineSimilarity(commentVector, nonSpamVector);
                maxNonSpamSimilarity = Math.max(maxNonSpamSimilarity, similarity);
            }
            
            // Determine if the comment is more similar to spam or non-spam
            boolean isSpam = maxSpamSimilarity > maxNonSpamSimilarity && maxSpamSimilarity > spamThreshold;
            
            if (isSpam) {
                logger.debug("Comment classified as spam with spam similarity: {}, non-spam similarity: {}", 
                            maxSpamSimilarity, maxNonSpamSimilarity);
            }
            
            return isSpam;
        } catch (Exception e) {
            logger.error("Error classifying comment as spam", e);
            // Fall back to a simple check for spam keywords
            return comment.toLowerCase().contains("buy") || 
                   comment.toLowerCase().contains("cheap") || 
                   comment.toLowerCase().contains("discount");
        }
    }

    /**
     * Detects spam comments from a list of comments.
     *
     * @param comments The list of comments to check
     * @return A list of spam comments
     */
    public List<String> detectSpamComments(List<String> comments) {
        logger.info("Detecting spam in {} comments using pre-trained model", comments.size());
        
        List<String> spamComments = comments.stream()
                .filter(this::isSpam)
                .collect(Collectors.toList());
                
        logger.info("Found {} spam comments", spamComments.size());
        
        return spamComments;
    }
} 