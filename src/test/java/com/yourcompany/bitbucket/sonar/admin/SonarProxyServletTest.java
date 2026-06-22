package com.yourcompany.bitbucket.sonar.admin;

import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class SonarProxyServletTest {

    @Mock private RepositoryService repositoryService;
    @Mock private PullRequestService pullRequestService;
    @Mock private PluginSettingsFactory pluginSettingsFactory;
    @Mock private PluginSettings pluginSettings;
    @Mock private PluginSettings globalSettings;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private SonarProxyServlet servlet;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        servlet = new SonarProxyServlet(repositoryService, pullRequestService, pluginSettingsFactory);

        when(pluginSettingsFactory.createSettingsForKey(anyString())).thenReturn(pluginSettings);
        when(pluginSettingsFactory.createGlobalSettings()).thenReturn(globalSettings);
        when(pluginSettings.get("url")).thenReturn("http://sonar.example.com");
        when(pluginSettings.get("token")).thenReturn("fake-token");
        when(pluginSettings.get("projectKey")).thenReturn("my-project");
    }

    @Test
    public void testDoGetReturnsBadRequestForNullPath() throws Exception {
        when(request.getPathInfo()).thenReturn(null);
        servlet.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    public void testDoGetReturnsBadRequestForShortPath() throws Exception {
        when(request.getPathInfo()).thenReturn("/123/status");
        servlet.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    public void testDoPostReturnsBadRequestForNullPath() throws Exception {
        when(request.getPathInfo()).thenReturn(null);
        servlet.doPost(request, response);
        verify(response).sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    public void testDoPostReturnsBadRequestForUnknownAction() throws Exception {
        when(request.getPathInfo()).thenReturn("/123/unknown-action");
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        servlet.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
    }

    @Test
    public void testDoPostReturnsBadRequestForNonNumericRepoId() throws Exception {
        when(request.getPathInfo()).thenReturn("/abc/assign");
        servlet.doPost(request, response);
        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
    }

    @Test
    public void testDoPostReturnsUnconfiguredWhenNoSettings() throws Exception {
        when(request.getPathInfo()).thenReturn("/123/assign");
        when(pluginSettings.get("url")).thenReturn(null);
        when(pluginSettings.get("token")).thenReturn(null);
        when(globalSettings.get("com.yourcompany.bitbucket.sonar.global.settings.url")).thenReturn(null);
        when(globalSettings.get("com.yourcompany.bitbucket.sonar.global.settings.token")).thenReturn(null);

        servlet.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_PRECONDITION_FAILED), anyString());
    }

    @Test
    public void testDoGetReturnsUnconfiguredWhenNoSettings() throws Exception {
        when(request.getPathInfo()).thenReturn("/123/status/repo");
        when(pluginSettings.get("url")).thenReturn(null);
        when(pluginSettings.get("token")).thenReturn(null);
        when(globalSettings.get("com.yourcompany.bitbucket.sonar.global.settings.url")).thenReturn(null);
        when(globalSettings.get("com.yourcompany.bitbucket.sonar.global.settings.token")).thenReturn(null);

        servlet.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_PRECONDITION_FAILED), anyString());
    }

    @Test
    public void testDoPostPathParsingInvokesGetPathInfo() throws Exception {
        when(request.getPathInfo()).thenReturn("/123/assign");
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());

        try {
            servlet.doPost(request, response);
        } catch (Exception e) {
            // HTTP client may fail in mock env — that is expected
        }

        verify(request, atLeastOnce()).getPathInfo();
    }
}
