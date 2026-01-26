package com.example.jutjubic.utils;

import com.example.jutjubic.services.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class IpLocationExtractor {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    public static String extractJsonValue(String json, String key) {
        String look = "\"" + key + "\":";
        int idx = json.indexOf(look);
        if (idx < 0) return null;
        int start = idx + look.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        boolean isString = json.charAt(start) == '"';
        if (isString) {
            start++;
            end = json.indexOf('"', start);
            if (end < 0) return null;
            return json.substring(start, end);
        } else {
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
            return json.substring(start, end);
        }
    }

    public static String getUrlContent(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        int code = conn.getResponseCode();
        if (code != 200) return null;

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) sb.append(line);
        in.close();
        return sb.toString();
    }

    public static GeoResult getGeoLocation() {
        String ip = System.getenv("IP_ADDRESS");
        try {
            ip = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            URL url = URI.create("http://ip-api.com/json/" + ip)
                    .resolve("?fields=status,message,lat,lon")
                    .toURL();
            String body = getUrlContent(url);

            assert body != null;
            if (body.contains("\"status\":\"success\"")) {
                String latStr = extractJsonValue(body, "lat");
                String lonStr = extractJsonValue(body, "lon");
                if (latStr != null && lonStr != null) {
                    GeoResult r = new GeoResult();
                    r.lat = Double.parseDouble(latStr);
                    r.lon = Double.parseDouble(lonStr);
                    return r;
                }
            }
        } catch (Exception e) {
            logger.warn("IP geolocation failed for {}: {}", ip, e.getMessage());
        }
        return null;
    }
}
