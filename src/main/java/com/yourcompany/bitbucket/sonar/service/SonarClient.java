package com.yourcompany.bitbucket.sonar.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Client for interacting with the SonarQube REST API.
 */
public class SonarClient {
    private static final Logger log = LoggerFactory.getLogger(SonarClient.class);
    private static final int TIMEOUT_MS = 5000;

    private final String sonarUrl;
    private final String apiToken;
    private final CloseableHttpClient httpClient;

    public SonarClient(String sonarUrl, String apiToken) {
        this.sonarUrl = sonarUrl.endsWith("/") ? sonarUrl.substring(0, sonarUrl.length() - 1) : sonarUrl;
        this.apiToken = apiToken;
        
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MS)
                .setConnectionRequestTimeout(TIMEOUT_MS)
                .setSocketTimeout(TIMEOUT_MS)
                .build();
        
        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build();
    }

    /**
     * Fetches the Quality Gate status for a project, optionally for a specific branch or pull request.
     * @param projectKey The SonarQube project key
     * @param branch Optional branch name
     * @param pullRequestId Optional pull request ID
     * @return JsonObject containing the project status and conditions
     */
    public JsonObject getQualityGateStatus(String projectKey, String branch, String pullRequestId) throws IOException {
        StringBuilder urlBuilder = new StringBuilder(sonarUrl).append("/api/qualitygates/project_status?projectKey=").append(projectKey);
        if (pullRequestId != null && !pullRequestId.isEmpty()) {
            urlBuilder.append("&pullRequest=").append(pullRequestId);
        } else if (branch != null && !branch.isEmpty()) {
            urlBuilder.append("&branch=").append(branch);
        }

        JsonObject response = executeGet(urlBuilder.toString());
        
        if (response.has("projectStatus")) {
            JsonObject status = response.getAsJsonObject("projectStatus");
            log.debug("Quality Gate status for {} (branch={}, pr={}): {}", 
                      projectKey, branch, pullRequestId, status.get("status").getAsString());
        }
        
        return response;
    }

    /**
     * Fetches issues (bugs, vulnerabilities, code smells) for a specific project.
     * @param projectKey The SonarQube project key
     * @return JsonObject containing the list of issues
     */
    public JsonObject getIssues(String projectKey) throws IOException {
        // ps=500 to get a large batch of issues
        String url = sonarUrl + "/api/issues/search?componentKeys=" + projectKey + "&ps=500";
        return executeGet(url);
    }

    /**
     * Fetches specific metrics for a project.
     * @param projectKey The SonarQube project key
     * @return JsonObject containing the measures
     */
    public JsonObject getMetrics(String projectKey) throws IOException {
        String url = sonarUrl + "/api/measures/component?component=" + projectKey + 
                     "&metricKeys=coverage,duplicated_lines_density,bugs,vulnerabilities,code_smells";
        return executeGet(url);
    }

    private JsonObject executeGet(String url) throws IOException {
        HttpGet request = new HttpGet(url);
        String auth = apiToken + ":";
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        request.setHeader("Authorization", "Basic " + encodedAuth);

        log.debug("Executing GET request to SonarQube: {}", url);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode >= 200 && statusCode < 300) {
                return JsonParser.parseString(responseBody).getAsJsonObject();
            } else {
                log.error("SonarQube API error: {} - {}", statusCode, responseBody);
                throw new IOException("Unexpected response code: " + statusCode);
            }
        }
    }

    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.warn("Failed to close HttpClient", e);
        }
    }
}
