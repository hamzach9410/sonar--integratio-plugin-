package com.yourcompany.bitbucket.sonar.service;

import com.atlassian.bitbucket.insight.annotation.AnnotationSeverity;
import com.atlassian.bitbucket.insight.annotation.InsightAnnotation;
import com.atlassian.bitbucket.insight.report.InsightReport;
import com.atlassian.bitbucket.insight.report.InsightReportData;
import com.atlassian.bitbucket.insight.report.Result;
import com.atlassian.bitbucket.insight.InsightService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.util.Page;
import com.atlassian.bitbucket.util.PageRequest;
import com.atlassian.bitbucket.util.PageUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for decorating Bitbucket Pull Requests with SonarQube insights.
 */
@Component
@Named("insightDecorator")
public class InsightDecorator {
    private static final Logger log = LoggerFactory.getLogger(InsightDecorator.class);
    private static final String REPORT_KEY = "sonarqube.quality.gate";
    private static final String REPORT_TITLE = "SonarQube Analysis";

    private final InsightService insightService;

    @Inject
    public InsightDecorator(InsightService insightService) {
        this.insightService = insightService;
    }

    /**
     * Posts a SonarQube report and annotations to a pull request.
     */
    public void postToInsights(PullRequest pr, String commitId, String sonarUrl, String projectKey, 
                               JsonObject qualityGate, JsonObject issuesJson, JsonObject metrics) {
        Repository repository = pr.getFromRef().getRepository();
        
        // 1. Create and Post the Report
        String status = qualityGate.get("projectStatus").getAsJsonObject().get("status").getAsString();
        Result result = "OK".equals(status) ? Result.PASS : Result.FAIL;
        String dashboardUrl = String.format("%s/dashboard?id=%s", sonarUrl, projectKey);

        InsightReport.Builder reportBuilder = insightService.createReportBuilder()
                .key(REPORT_KEY)
                .title(REPORT_TITLE)
                .result(result)
                .link(toUri(dashboardUrl));

        // 1b. Add helpful summary if gate failed
        if (result == Result.FAIL && qualityGate.has("projectStatus")) {
            JsonObject status = qualityGate.getAsJsonObject("projectStatus");
            if (status.has("conditions")) {
                JsonArray conditions = status.getAsJsonArray("conditions");
                StringBuilder summary = new StringBuilder("Quality Gate failed: ");
                int count = 0;
                for (JsonElement c : conditions) {
                    JsonObject cond = c.getAsJsonObject();
                    if (!"OK".equals(cond.get("status").getAsString())) {
                        if (count++ > 0) summary.append(", ");
                        summary.append(cond.get("metricKey").getAsString());
                    }
                }
                // Bitbucket reports don't have a direct summary field in some versions, 
                // but we can add it as a data field or part of the title
                reportBuilder.data(new InsightReportData.Builder()
                        .name("Status Summary")
                        .value(summary.toString())
                        .type(InsightReportData.DataType.TEXT)
                        .build());
            }
        }

        // Add metrics as data fields
        if (metrics.has("component")) {
            JsonArray measures = metrics.getAsJsonObject("component").getAsJsonArray("measures");
            for (JsonElement element : measures) {
                JsonObject measure = element.getAsJsonObject();
                String metricName = measure.get("metric").getAsString();
                String metricValue = measure.get("value").getAsString();
                
                InsightReportData.DataType dataType = InsightReportData.DataType.NUMBER;
                if (metricName.contains("coverage") || metricName.contains("density")) {
                    dataType = InsightReportData.DataType.PERCENTAGE;
                }

                reportBuilder.data(new InsightReportData.Builder()
                        .name(metricName.replace('_', ' '))
                        .value(metricValue)
                        .type(dataType)
                        .build());
            }
        }

        insightService.setReport(repository, commitId, reportBuilder.build());

        // 2. Create and Post Annotations
        List<InsightAnnotation> annotations = new ArrayList<>();
        if (issuesJson.has("issues")) {
            JsonArray issues = issuesJson.getAsJsonArray("issues");
            for (JsonElement element : issues) {
                JsonObject issue = element.getAsJsonObject();
                if (issue.has("line") && issue.has("component")) {
                    String path = issue.get("component").getAsString();
                    // SonarQube component is often "projectKey:path/to/file"
                    int separatorIndex = path.indexOf(':');
                    if (separatorIndex != -1) {
                        path = path.substring(separatorIndex + 1);
                    }

                    annotations.add(new InsightAnnotation.Builder()
                            .key(issue.get("key").getAsString())
                            .message(issue.get("message").getAsString())
                            .severity(mapSeverity(issue.get("severity").getAsString()))
                            .path(path)
                            .line(issue.get("line").getAsInt())
                            .reportKey(REPORT_KEY)
                            .build());
                }
            }
        }

        if (!annotations.isEmpty()) {
            insightService.setAnnotations(repository, commitId, REPORT_KEY, annotations);
        }
        
        log.info("Posted SonarQube insights to PR #{} for commit {}", pr.getId(), commitId);
    }

    private AnnotationSeverity mapSeverity(String sonarSeverity) {
        switch (sonarSeverity.toUpperCase()) {
            case "BLOCKER":
            case "CRITICAL":
                return AnnotationSeverity.HIGH;
            case "MAJOR":
                return AnnotationSeverity.MEDIUM;
            case "MINOR":
            case "INFO":
            default:
                return AnnotationSeverity.LOW;
        }
    }

    private URI toUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            log.warn("Invalid SonarQube URL for link: {}", url);
            return null;
        }
    }
}
