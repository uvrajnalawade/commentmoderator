package com.moderator.service;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentThreadListResponse;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SentimentService {
    private static final String API_KEY = "your_api_key"; // Replace with your actual YouTube API key
    private static final String APPLICATION_NAME = "YouTubeCommentAnalyzer";
    private final YouTube youtubeService;

    private final ExecutorService executorService;

    public SentimentService() {
        // Initialize the YouTube service
        youtubeService = new YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), request -> {})
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Initialize ExecutorService with a fixed thread pool
        int threadPoolSize = Runtime.getRuntime().availableProcessors(); // Adjust thread pool size based on available processors
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
    }

    public Map<String, List<String>> analyzeSentiment(String youtubeUrl, int commentCount) {
        List<String> comments = getCommentsFromYouTube(youtubeUrl, commentCount);

        List<Future<Map.Entry<String, String>>> futures = new ArrayList<>();

        // Submit tasks for each comment to the ExecutorService
        for (String comment : comments) {
            Future<Map.Entry<String, String>> future = executorService.submit(() -> {
                // Create a new StanfordCoreNLP pipeline for each task (thread-safe)
                Properties props = new Properties();
                props.setProperty("annotators", "tokenize,ssplit,pos,lemma,parse,sentiment");
                StanfordCoreNLP localPipeline = new StanfordCoreNLP(props);

                Annotation annotation = new Annotation(comment);
                localPipeline.annotate(annotation);
                String sentiment = "Neutral"; // Default to Neutral in case no sentences are found

                for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
                    sentiment = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
                }
                return new AbstractMap.SimpleEntry<>(sentiment, comment);
            });

            futures.add(future);
        }

        // Initialize lists for categorized comments
        List<String> positiveComments = new ArrayList<>();
        List<String> negativeComments = new ArrayList<>();
        List<String> neutralComments = new ArrayList<>();

        // Collect results from all tasks
        for (Future<Map.Entry<String, String>> future : futures) {
            try {
                Map.Entry<String, String> result = future.get();
                String sentiment = result.getKey();
                String comment = result.getValue();

                // Categorize the comments based on sentiment
                switch (sentiment) {
                    case "Positive":
                        positiveComments.add(comment);
                        break;
                    case "Negative":
                        negativeComments.add(comment);
                        break;
                    default:
                        neutralComments.add(comment);
                        break;
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace(); // Handle errors in production
            }
        }

        // Shutdown the executor service if you want to close it after processing (optional)
        // executorService.shutdown();

        // Create a map to hold categorized comments
        Map<String, List<String>> categorizedComments = new HashMap<>();
        categorizedComments.put("positive", positiveComments);
        categorizedComments.put("negative", negativeComments);
        categorizedComments.put("neutral", neutralComments);

        return categorizedComments;
    }

    // Fetch comments from YouTube API
    private List<String> getCommentsFromYouTube(String youtubeUrl, int commentCount) {
        List<String> comments = new ArrayList<>();
        try {
            // Extract video ID from YouTube URL
            String videoId = extractVideoIdFromUrl(youtubeUrl);

            // Request to get the comment threads for the video
            YouTube.CommentThreads.List request = youtubeService.commentThreads()
                    .list("snippet")
                    .setVideoId(videoId)
                    .setKey(API_KEY)
                    .setMaxResults((long) commentCount); // Get up to specified number of comments

            // Execute the request and get the response
            CommentThreadListResponse response = request.execute();

            // Extract comments from the response
            for (CommentThread commentThread : response.getItems()) {
                String commentText = commentThread.getSnippet().getTopLevelComment().getSnippet().getTextDisplay();
                comments.add(commentText);
            }
        } catch (IOException e) {
            e.printStackTrace(); // Handle error properly in production
        }

        return comments;
    }

    // Helper method to extract the video ID from a YouTube URL
    private String extractVideoIdFromUrl(String youtubeUrl) {
        String videoId = null;
        if (youtubeUrl.contains("v=")) {
            videoId = youtubeUrl.substring(youtubeUrl.indexOf("v=") + 2);
        } else if (youtubeUrl.contains("youtu.be/")) {
            videoId = youtubeUrl.substring(youtubeUrl.lastIndexOf("/") + 1);
        }
        return videoId;
    }
}
