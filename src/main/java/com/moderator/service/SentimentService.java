package com.moderator.service;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentThreadListResponse;
import com.moderator.exception.YouTubeApiException;
import com.moderator.exception.InvalidUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import com.moderator.service.ModelBasedSpamDetector;

/**
 * Service for analyzing sentiment in YouTube comments.
 * Provides functionality to fetch comments from YouTube and categorize them by sentiment.
 */
@Service
public class SentimentService {
    private static final Logger logger = LoggerFactory.getLogger(SentimentService.class);
    
    @Value("${youtube.api.key}")
    private String apiKey;

    @Value("${youtube.application.name}")
    private String applicationName;

    @Value("${comment.analysis.thread-pool-size:5}")
    private int threadPoolSize;

    @Value("${comment.analysis.default-language:en}")
    private String defaultLanguage;
    
    @Value("${sentiment.positive.words}")
    private String positiveWordsString;
    
    @Value("${sentiment.negative.words}")
    private String negativeWordsString;
    
    private List<String> positiveWords;
    private List<String> negativeWords;

    private YouTube youtubeService;
    private ExecutorService executorService;
    private final ModelBasedSpamDetector spamDetector;
    
    private static final Pattern YOUTUBE_URL_PATTERN = Pattern.compile(
        "^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11}).*$"
    );

    /**
     * Constructor for SentimentService.
     *
     * @param spamDetector Service for detecting spam in comments
     */
    public SentimentService(ModelBasedSpamDetector spamDetector) {
        this.spamDetector = spamDetector;
    }

    /**
     * Initializes the YouTube service and thread pool.
     */
    @PostConstruct
    private void initialize() {
        logger.info("Initializing SentimentService with thread pool size: {}", threadPoolSize);
        
        try {
            youtubeService = new YouTube.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                null)
                .setApplicationName(applicationName)
                .build();
                
            executorService = Executors.newFixedThreadPool(threadPoolSize);
            
            // Initialize sentiment word lists
            positiveWords = Arrays.asList(positiveWordsString.split(","));
            negativeWords = Arrays.asList(negativeWordsString.split(","));
            
            logger.info("YouTube service initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize YouTube service", e);
            throw new RuntimeException("Failed to initialize YouTube service", e);
        }
    }

    /**
     * Analyzes sentiment in comments from a YouTube video.
     *
     * @param youtubeUrl The URL of the YouTube video
     * @param commentCount The number of comments to analyze
     * @return A map of categorized comments
     * @throws YouTubeApiException if there's an error with the YouTube API
     * @throws InvalidUrlException if the URL is invalid
     */
    public Map<String, List<String>> analyzeSentiment(String youtubeUrl, int commentCount) {
        logger.info("Analyzing sentiment for URL: {}, comment count: {}", youtubeUrl, commentCount);
        
        try {
            // Extract video ID from URL
            String videoId = extractVideoIdFromUrl(youtubeUrl);
            if (videoId == null) {
                throw new InvalidUrlException("Invalid YouTube URL format");
            }
            
            // Get comments from YouTube
            List<String> comments = getCommentsFromYouTube(youtubeUrl, commentCount);
            logger.info("Retrieved {} comments from YouTube", comments.size());
            
            // Categorize comments
            Map<String, List<String>> categorizedComments = new HashMap<>();
            categorizedComments.put("positive", new ArrayList<>());
            categorizedComments.put("negative", new ArrayList<>());
            categorizedComments.put("neutral", new ArrayList<>());
            categorizedComments.put("spam", new ArrayList<>());
            
            // Process comments in parallel
            List<Future<Map.Entry<String, String>>> futures = new ArrayList<>();
            
            for (String comment : comments) {
                futures.add(executorService.submit(() -> {
                    String sentiment = analyzeSimpleSentiment(comment);
                    boolean isSpam = spamDetector.isSpam(comment);
                    
                    if (isSpam) {
                        return new AbstractMap.SimpleEntry<>("spam", comment);
                    } else {
                        return new AbstractMap.SimpleEntry<>(sentiment, comment);
                    }
                }));
            }
            
            // Collect results
            for (Future<Map.Entry<String, String>> future : futures) {
                try {
                    Map.Entry<String, String> entry = future.get(5, TimeUnit.SECONDS);
                    categorizedComments.get(entry.getKey()).add(entry.getValue());
                } catch (Exception e) {
                    logger.error("Error processing comment", e);
                }
            }
            
            logger.info("Sentiment analysis complete. Categorized {} comments", comments.size());
            return categorizedComments;
            
        } catch (InvalidUrlException e) {
            logger.error("Invalid URL: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error analyzing sentiment", e);
            throw new YouTubeApiException("Error analyzing comments: " + e.getMessage(), e);
        }
    }

    /**
     * Analyzes the sentiment of a comment using a simple keyword-based approach.
     *
     * @param comment The comment to analyze
     * @return The sentiment category ("positive", "negative", or "neutral")
     */
    private String analyzeSimpleSentiment(String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            return "neutral";
        }
        
        String lowerComment = comment.toLowerCase();
        int positiveCount = 0;
        int negativeCount = 0;
        
        // Count positive words
        for (String word : positiveWords) {
            if (lowerComment.contains(word)) {
                positiveCount++;
            }
        }
        
        // Count negative words
        for (String word : negativeWords) {
            if (lowerComment.contains(word)) {
                negativeCount++;
            }
        }
        
        // Determine sentiment
        if (positiveCount > negativeCount) {
            return "positive";
        } else if (negativeCount > positiveCount) {
            return "negative";
        } else {
            return "neutral";
        }
    }

    /**
     * Gets comments from a YouTube video.
     *
     * @param youtubeUrl The URL of the YouTube video
     * @param commentCount The number of comments to retrieve
     * @return A list of comments
     * @throws YouTubeApiException if there's an error with the YouTube API
     * @throws InvalidUrlException if the URL is invalid
     */
    public List<String> getCommentsFromYouTube(String youtubeUrl, int commentCount) {
        logger.info("Getting comments from YouTube for URL: {}, count: {}", youtubeUrl, commentCount);
        
        try {
            String videoId = extractVideoIdFromUrl(youtubeUrl);
            if (videoId == null) {
                throw new InvalidUrlException("Invalid YouTube URL format");
            }
            
            List<String> comments = new ArrayList<>();
            String pageToken = null;
            
            do {
                YouTube.CommentThreads.List request = youtubeService.commentThreads()
                    .list(Collections.singletonList("snippet"))
                    .setKey(apiKey)
                    .setVideoId(videoId)
                    .setMaxResults((long) Math.min(500, commentCount - comments.size()))
                    .setTextFormat("plainText");
                
                if (pageToken != null) {
                    request.setPageToken(pageToken);
                }
                
                CommentThreadListResponse response = request.execute();
                
                for (CommentThread thread : response.getItems()) {
                    if (thread.getSnippet().getTopLevelComment() != null) {
                        String commentText = thread.getSnippet().getTopLevelComment().getSnippet().getTextDisplay();
                        comments.add(commentText);
                        
                        if (comments.size() >= commentCount) {
                            break;
                        }
                    }
                }
                
                pageToken = response.getNextPageToken();
                
            } while (pageToken != null && comments.size() < commentCount);
            
            logger.info("Retrieved {} comments from YouTube", comments.size());
            return comments;
            
        } catch (IOException e) {
            logger.error("Error fetching comments from YouTube", e);
            throw new YouTubeApiException("Error fetching comments from YouTube: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the video ID from a YouTube URL.
     *
     * @param youtubeUrl The YouTube URL
     * @return The video ID, or null if the URL is invalid
     */
    private String extractVideoIdFromUrl(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
            return null;
        }
        
        var matcher = YOUTUBE_URL_PATTERN.matcher(youtubeUrl);
        if (matcher.matches()) {
            return matcher.group(4);
        }
        
        return null;
    }
}
