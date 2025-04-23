package com.moderator.controller;

import com.moderator.service.SentimentService;
import com.moderator.service.ModelBasedSpamDetector;
import com.moderator.exception.YouTubeApiException;
import com.moderator.exception.InvalidUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 * Controller for handling YouTube comment moderation requests.
 * Provides endpoints for analyzing comments for sentiment and spam.
 */
@Controller
@Validated
public class ModeratorController {

    private static final Logger logger = LoggerFactory.getLogger(ModeratorController.class);
    
    private final SentimentService sentimentService;
    private final ModelBasedSpamDetector spamDetector;

    /**
     * Constructor for ModeratorController.
     * 
     * @param sentimentService Service for analyzing comment sentiment
     * @param spamDetector Service for detecting spam in comments
     */
    public ModeratorController(SentimentService sentimentService, ModelBasedSpamDetector spamDetector) {
        this.sentimentService = sentimentService;
        this.spamDetector = spamDetector;
    }

    /**
     * Displays the home page with the comment analysis form.
     * 
     * @return The name of the view to render
     */
    @GetMapping("/")
    public String home() {
        logger.info("Accessing home page");
        return "index";
    }

    /**
     * Analyzes comments from a YouTube video for sentiment and spam.
     * 
     * @param youtubeUrl The URL of the YouTube video
     * @param commentCount The number of comments to analyze
     * @param model The model to add attributes to
     * @return The name of the view to render
     */
    @PostMapping("/spamanalyze")
    public String analyzeSpam(
            @RequestParam @Pattern(regexp = "^(https?://)?(www\\.)?(youtube\\.com|youtu\\.be)/.+$", 
                                 message = "Invalid YouTube URL format") String youtubeUrl,
            @RequestParam(defaultValue = "10") @Min(1) int commentCount,
            Model model) {
        
        logger.info("Analyzing comments for URL: {}, count: {}", youtubeUrl, commentCount);
        
        try {
            Map<String, List<String>> categorizedComments = sentimentService.analyzeSentiment(youtubeUrl, commentCount);
            
            // Calculate counts for statistics
            int totalComments = categorizedComments.values().stream()
                    .mapToInt(List::size)
                    .sum();
            int positiveCount = categorizedComments.get("positive").size();
            int negativeCount = categorizedComments.get("negative").size();
            int neutralCount = categorizedComments.get("neutral").size();
            int spamCount = categorizedComments.get("spam").size();

            logger.info("Analysis complete. Found {} total comments: {} positive, {} negative, {} neutral, {} spam",
                    totalComments, positiveCount, negativeCount, neutralCount, spamCount);

            // Add all data to the model
            model.addAttribute("categorizedComments", categorizedComments);
            model.addAttribute("totalComments", totalComments);
            model.addAttribute("positiveCount", positiveCount);
            model.addAttribute("negativeCount", negativeCount);
            model.addAttribute("neutralCount", neutralCount);
            model.addAttribute("spamCount", spamCount);
            model.addAttribute("youtubeUrl", youtubeUrl);
            
            return "result";
        } catch (YouTubeApiException e) {
            logger.error("YouTube API error: {}", e.getMessage(), e);
            model.addAttribute("error", "YouTube API error: " + e.getMessage());
            return "index";
        } catch (InvalidUrlException e) {
            logger.error("Invalid URL error: {}", e.getMessage(), e);
            model.addAttribute("error", "Invalid YouTube URL: " + e.getMessage());
            return "index";
        } catch (Exception e) {
            logger.error("Unexpected error during comment analysis", e);
            model.addAttribute("error", "An unexpected error occurred. Please try again later.");
            return "index";
        }
    }
}
