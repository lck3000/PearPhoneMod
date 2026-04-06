package com.pearphone.mod;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YouTubeParser {
    private static final String[] VIDEO_ID_PATTERNS = {
            "(?:youtube\\.com\\/watch\\?v=|youtu\\.be\\/|youtube\\.com\\/embed\\/)([^&\\n?#]+)"
    };

    public static String extractVideoId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        for (String pattern : VIDEO_ID_PATTERNS) {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(url);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    public static String extractVideoTitle(String url) {
        try {
            String videoId = extractVideoId(url);
            if (videoId == null) {
                return "Unknown Track";
            }

            // Try to fetch video metadata
            // Note: This requires a server-side solution or an API key
            // For now, return a placeholder
            return "YouTube Video (" + videoId.substring(0, 5) + "...)";

        } catch (Exception e) {
            return "Unknown Track";
        }
    }

    public static boolean isValidYoutubeUrl(String url) {
        return extractVideoId(url) != null;
    }
}
