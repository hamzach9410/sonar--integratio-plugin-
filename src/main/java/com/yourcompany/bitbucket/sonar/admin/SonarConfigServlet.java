package com.yourcompany.bitbucket.sonar.admin;

import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
import com.atlassian.bitbucket.i18n.I18nService;
import com.google.common.collect.ImmutableMap;
import com.yourcompany.bitbucket.sonar.service.SonarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for repository-level SonarQube configuration.
 */
public class SonarConfigServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(SonarConfigServlet.class);
    private static final String SETTINGS_PREFIX = "com.yourcompany.bitbucket.sonar.settings.";

    private final RepositoryService repositoryService;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final SoyTemplateRenderer soyTemplateRenderer;

    public SonarConfigServlet(RepositoryService repositoryService, 
                              PluginSettingsFactory pluginSettingsFactory,
                              SoyTemplateRenderer soyTemplateRenderer) {
        this.repositoryService = repositoryService;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.soyTemplateRenderer = soyTemplateRenderer;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // URL context is usually /repos/{id}/admin/sonar-config
        // However, the link in atlassian-plugin.xml was /repos/${repo.id}/admin/sonar-config
        // Bitbucket will pass the ID in the path or as a parameter
        
        Repository repository = getRepository(req);
        if (repository == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        PluginSettings settings = pluginSettingsFactory.createSettingsForKey(SETTINGS_PREFIX + repository.getId());
        
        PluginSettings globalSettings = pluginSettingsFactory.createGlobalSettings();
        String globalPrefix = "com.yourcompany.bitbucket.sonar.global.settings";

        Map<String, Object> data = new HashMap<>();
        data.put("repository", repository);
        data.put("sonarUrl", settings.get("url"));
        data.put("sonarToken", settings.get("token"));
        data.put("projectKey", settings.get("projectKey"));
        data.put("globalUrl", globalSettings.get(globalPrefix + ".url"));
        data.put("globalToken", globalSettings.get(globalPrefix + ".token"));
        data.put("saved", "true".equals(req.getParameter("saved")));

        render(resp, "com.yourcompany.bitbucket.sonar:sonar-admin-soy", "com.yourcompany.bitbucket.sonar.configPage", data);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Repository repository = getRepository(req);
        if (repository == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String sonarUrl = req.getParameter("sonarUrl");
        String sonarToken = req.getParameter("sonarToken");
        String projectKey = req.getParameter("projectKey");
        String action = req.getParameter("action");

        if ("test".equals(action)) {
            testConnection(resp, sonarUrl, sonarToken, projectKey);
            return;
        }

        PluginSettings settings = pluginSettingsFactory.createSettingsForKey(SETTINGS_PREFIX + repository.getId());
        settings.put("url", sonarUrl);
        settings.put("token", sonarToken);
        settings.put("projectKey", projectKey);

        resp.sendRedirect(req.getRequestURI() + "?saved=true");
    }

    private void testConnection(HttpServletResponse resp, String url, String token, String projectKey) throws IOException {
        SonarClient client = new SonarClient(url, token);
        try {
            client.getQualityGateStatus(projectKey, null, null);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\": true, \"message\": \"Successfully connected to SonarQube!\"}");
        } catch (Exception e) {
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\": false, \"message\": \"Failed to connect: " + e.getMessage() + "\"}");
        } finally {
            client.close();
        }
    }

    private Repository getRepository(HttpServletRequest req) {
        // Path should contain repo ID or slug depending on how the servlet is mapped
        // In Bitbucket 6.x, the repository is often available via the request context or path analysis
        String pathInfo = req.getPathInfo();
        // Assuming path like /repos/123/admin/sonar-config
        // This is a simplified extraction
        try {
            String[] parts = pathInfo.split("/");
            if (parts.length >= 2) {
                int repoId = Integer.parseInt(parts[1]);
                return repositoryService.getById(repoId);
            }
        } catch (Exception e) {
            log.error("Failed to extract repository ID from path: {}", pathInfo);
        }
        return null;
    }

    private void render(HttpServletResponse resp, String completeModuleKey, String templateName, Map<String, Object> data) throws IOException, ServletException {
        resp.setContentType("text/html;charset=UTF-8");
        try {
            soyTemplateRenderer.render(resp.getWriter(), completeModuleKey, templateName, data);
        } catch (SoyException e) {
            throw new ServletException(e);
        }
    }
}
