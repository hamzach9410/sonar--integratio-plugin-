package com.yourcompany.bitbucket.sonar.listener;

import com.atlassian.bitbucket.event.pull.PullRequestOpenedEvent;
import com.atlassian.bitbucket.event.pull.PullRequestRescopedEvent;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.setting.RepositorySettingsService;
import com.atlassian.event.api.EventListener;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.JsonObject;
import com.yourcompany.bitbucket.sonar.service.InsightDecorator;
import com.yourcompany.bitbucket.sonar.service.SonarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Event listener for pull request events, triggering SonarQube analysis fetch.
 */
@Named("pullRequestSonarListener")
public class PullRequestSonarListener {
    private static final Logger log = LoggerFactory.getLogger(PullRequestSonarListener.class);
    private static final String SETTINGS_PREFIX = "com.yourcompany.bitbucket.sonar.settings.";

    private final InsightDecorator insightDecorator;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final ExecutorService executorService;

    @Inject
    public PullRequestSonarListener(InsightDecorator insightDecorator, 
                                   PluginSettingsFactory pluginSettingsFactory) {
        this.insightDecorator = insightDecorator;
        this.pluginSettingsFactory = pluginSettingsFactory;
        // Using a fixed thread pool to prevent unbounded growth
        this.executorService = Executors.newFixedThreadPool(4);
    }

    @EventListener
    public void onPullRequestOpened(PullRequestOpenedEvent event) {
        handlePullRequestEvent(event.getPullRequest());
    }

    @EventListener
    public void onPullRequestRescoped(PullRequestRescopedEvent event) {
        handlePullRequestEvent(event.getPullRequest());
    }

    private void handlePullRequestEvent(PullRequest pr) {
        int repoId = pr.getFromRef().getRepository().getId();
        String sonarUrl = getSetting(repoId, "url");
        String apiToken = getSetting(repoId, "token");
        String projectKey = getSetting(repoId, "projectKey");

        // Fallback to global settings
        if (sonarUrl == null || apiToken == null) {
            PluginSettings globalSettings = pluginSettingsFactory.createGlobalSettings();
            String globalPrefix = "com.yourcompany.bitbucket.sonar.global.settings";
            if (sonarUrl == null) sonarUrl = (String) globalSettings.get(globalPrefix + ".url");
            if (apiToken == null) apiToken = (String) globalSettings.get(globalPrefix + ".token");
        }

        if (sonarUrl == null || apiToken == null) {
            log.warn("SonarQube integration not configured for repository {}", pr.getFromRef().getRepository().getSlug());
            return;
        }

        if (projectKey == null || projectKey.isEmpty()) {
            projectKey = pr.getFromRef().getRepository().getSlug();
        }

        final String finalProjectKey = projectKey;
        final String finalSonarUrl = sonarUrl;
        final String finalApiToken = apiToken;

        executorService.submit(() -> {
            fetchAndPostSonarReport(pr, finalSonarUrl, finalApiToken, finalProjectKey);
        });
    }

    private void fetchAndPostSonarReport(PullRequest pr, String sonarUrl, String apiToken, String projectKey) {
        String commitId = pr.getFromRef().getLatestCommit();
        SonarClient sonarClient = new SonarClient(sonarUrl, apiToken);

        try {
            log.info("Fetching SonarQube data for project {} at commit {}", projectKey, commitId);
            
            JsonObject qualityGate = sonarClient.getQualityGateStatus(projectKey, null, String.valueOf(pr.getId()));
            JsonObject issues = sonarClient.getIssues(projectKey);
            JsonObject metrics = sonarClient.getMetrics(projectKey);

            insightDecorator.postToInsights(pr, commitId, sonarUrl, projectKey, qualityGate, issues, metrics);

        } catch (IOException e) {
            log.error("Failed to fetch SonarQube analysis for PR #{} : {}", pr.getId(), e.getMessage());
        } finally {
            sonarClient.close();
        }
    }

    private String getSetting(int repositoryId, String key) {
        PluginSettings settings = pluginSettingsFactory.createSettingsForKey(SETTINGS_PREFIX + repositoryId);
        Object value = settings.get(key);
        return value != null ? value.toString() : null;
    }
}
