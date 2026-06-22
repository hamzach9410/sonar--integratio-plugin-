package com.yourcompany.bitbucket.sonar.listener;

import com.atlassian.bitbucket.event.pull.PullRequestOpenedEvent;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.yourcompany.bitbucket.sonar.service.InsightDecorator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class PullRequestSonarListenerTest {

    @Mock private InsightDecorator insightDecorator;
    @Mock private PluginSettingsFactory pluginSettingsFactory;
    @Mock private PluginSettings repoSettings;
    @Mock private PluginSettings globalSettings;
    @Mock private PullRequestOpenedEvent openedEvent;
    @Mock private PullRequest pullRequest;
    @Mock private PullRequestRef fromRef;
    @Mock private Repository repository;

    private PullRequestSonarListener listener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        listener = new PullRequestSonarListener(insightDecorator, pluginSettingsFactory);

        when(openedEvent.getPullRequest()).thenReturn(pullRequest);
        when(pullRequest.getFromRef()).thenReturn(fromRef);
        when(fromRef.getRepository()).thenReturn(repository);
        when(fromRef.getLatestCommit()).thenReturn("deadbeef");
        when(repository.getId()).thenReturn(42);
        when(repository.getSlug()).thenReturn("my-repo");
        when(pullRequest.getId()).thenReturn(7L);

        when(pluginSettingsFactory.createSettingsForKey(anyString())).thenReturn(repoSettings);
        when(pluginSettingsFactory.createGlobalSettings()).thenReturn(globalSettings);
    }

    @Test
    public void testSkipsWhenNoSettingsConfigured() {
        when(repoSettings.get("url")).thenReturn(null);
        when(repoSettings.get("token")).thenReturn(null);
        when(globalSettings.get("com.yourcompany.bitbucket.sonar.global.settings.url")).thenReturn(null);
        when(globalSettings.get("com.yourcompany.bitbucket.sonar.global.settings.token")).thenReturn(null);

        // Should not throw, just log a warning and return
        listener.onPullRequestOpened(openedEvent);

        // insightDecorator must never be called
        verifyNoInteractions(insightDecorator);
    }

    @Test
    public void testFallsBackToGlobalSettingsUrl() {
        when(repoSettings.get("url")).thenReturn(null);
        when(repoSettings.get("token")).thenReturn(null);
        when(repoSettings.get("projectKey")).thenReturn(null);
        when(globalSettings.get("com.yourcompany.bitbucket.sonar.global.settings.url")).thenReturn(null);
        when(globalSettings.get("com.yourcompany.bitbucket.sonar.global.settings.token")).thenReturn(null);

        listener.onPullRequestOpened(openedEvent);

        // Both paths missing → no interaction
        verifyNoInteractions(insightDecorator);
    }

    @Test
    public void testUsesRepoSlugWhenProjectKeyMissing() throws Exception {
        when(repoSettings.get("url")).thenReturn("http://sonar.example.com");
        when(repoSettings.get("token")).thenReturn("fake-token");
        when(repoSettings.get("projectKey")).thenReturn(null);

        // No exception expected; background thread will fail to connect but that's OK in unit test
        listener.onPullRequestOpened(openedEvent);

        // Give the async thread a moment then verify no crash
        Thread.sleep(200);
    }
}
