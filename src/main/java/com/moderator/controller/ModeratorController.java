package com.moderator.controller;
import com.moderator.service.SentimentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class ModeratorController {

    @Autowired
    private SentimentService sentimentService;
    @GetMapping("/home")
    public String home() {
        return "index";  // Returns the homepage
    }

//   @PostMapping("/analyze")
//    public String analyzeSentiment(@RequestParam("youtubeUrl") String youtubeUrl, Model model) {
//        // Call the service to get analysis results
//        List<String> results = sentimentService.analyzeSentiment(youtubeUrl);
//        model.addAttribute("comments", results);
//         return "result";  // Returns the results page
//    }

    @PostMapping("/analyze")
    public String process(@RequestParam("youtubeUrl") String youtubeUrl,@RequestParam("commentCount") int commentCount, Model model) {
        // Call the sentiment analysis service
        Map<String, List<String>> categorizedComments = sentimentService.analyzeSentiment(youtubeUrl,commentCount);

        // Extract comments from the map
        List<String> positiveComments = categorizedComments.get("positive");
        List<String> negativeComments = categorizedComments.get("negative");
        List<String> neutralComments = categorizedComments.get("neutral");

        // Add these lists to the model to send to the view
        model.addAttribute("positiveComments", positiveComments);
        model.addAttribute("negativeComments", negativeComments);
        model.addAttribute("neutralComments", neutralComments);

        return "result"; // Return the results template
    }

}
