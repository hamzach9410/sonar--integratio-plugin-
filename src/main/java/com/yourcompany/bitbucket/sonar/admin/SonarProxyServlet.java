package com.yourcompany.bitbucket.sonar.admin;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yourcompany.bitbucket.sonar.service.SonarClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Proxy servlet to handle status fetching and interactive actions (Assign, Resolve, Comment) on SonarQube issues.
 */
@Named("sonarProxyServlet")
public class SonarProxyServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(SonarProxyServlet.class);
    private static final String SETTINGS_PREFIX = "com.yourcompany.bitbucket.sonar.settings.";
    private static final String GLOBAL_SETTINGS_KEY = "com.yourcompany.bitbucket.sonar.global.settings";

    private final RepositoryService repositoryService;
    private final PullRequestService pullRequestService;
    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public SonarProxyServlet(RepositoryService repositoryService,
                             PullRequestService pullRequestService,
                             PluginSettingsFactory pluginSettingsFactory) {
        this.repositoryService = repositoryService;
        this.pullRequestService = pullRequestService;
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo(); // e.g. /123/status/pr/456 or /123/status/repo
        if (pathInfo == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length < 4) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        int repositoryId;
        try {
            repositoryId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid repository ID.");
            return;
        }
        String type = parts[3]; // pr, branch, or repo

        String[] sonarSettings = resolveSettings(repositoryId);
        String sonarUrl  = sonarSettings[0];
        String apiToken  = sonarSettings[1];
        String projectKey = sonarSettings[2];

        if (sonarUrl == null || apiToken == null) {
            resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED, "SonarQube not configured.");
            return;
        }

        if (projectKey == null || projectKey.isEmpty()) {
            // fall back to the repository slug
            if (repositoryService.getById(repositoryId) != null) {
                projectKey = repositoryService.getById(repositoryId).getSlug();
            }
        }

        SonarClient client = new SonarClient(sonarUrl, apiToken);
        try {
            resp.setContentType("application/json");
            JsonObject result;
            if ("pr".equals(type) && parts.length >= 5) {
                String prId = parts[4];
                PullRequest pr = pullRequestService.getById(repositoryId, Long.parseLong(prId));
                if (pr == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                result = buildPrStatusResponse(client, sonarUrl, projectKey, prId);
            } else if ("branch".equals(type) && parts.length >= 5) {
                String branchName = URLDecoder.decode(parts[4], "UTF-8");
                JsonObject status = client.getQualityGateStatus(projectKey, branchName, null);
                result = new JsonObject();
                if (status.has("projectStatus")) {
                    result.addProperty("status", status.getAsJsonObject("projectStatus").get("status").getAsString());
                }
                result.addProperty("link", sonarUrl + "/dashboard?id=" + projectKey + "&branch=" + URLEncoder.encode(branchName, "UTF-8"));
            } else if ("repo".equals(type)) {
                result = buildRepoStatusResponse(client, sonarUrl, projectKey);
            } else {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown status type.");
                return;
            }
            resp.getWriter().write(result.toString());
        } catch (Exception e) {
            log.error("Failed to fetch status from SonarQube", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo(); // e.g. /123/assign
        if (pathInfo == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String[] parts = pathInfo.split("/");
        if (parts.length < 3) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        int repositoryId;
        try {
            repositoryId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid repository ID.");
            return;
        }

        String action = parts[2]; // assign, comment, transition
        String[] sonarSettings = resolveSettings(repositoryId);
        String sonarUrl = sonarSettings[0];
        String apiToken = sonarSettings[1];

        if (sonarUrl == null || apiToken == null) {
            resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED, "SonarQube not configured.");
            return;
        }

        String targetUrl;
        switch (action) {
            case "assign":     targetUrl = sonarUrl + "/api/issues/assign"; break;
            case "comment":    targetUrl = sonarUrl + "/api/issues/add_comment"; break;
            case "transition": targetUrl = sonarUrl + "/api/issues/do_transition"; break;
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action: " + action);
                return;
        }

        forwardRequest(req, resp, targetUrl, apiToken);
    }

    // --- Helpers ---

    private String[] resolveSettings(int repositoryId) {
        PluginSettings settings = pluginSettingsFactory.createSettingsForKey(SETTINGS_PREFIX + repositoryId);
        String sonarUrl = (String) settings.get("url");
        String apiToken = (String) settings.get("token");
        String projectKey = (String) settings.get("projectKey");

        if (sonarUrl == null || apiToken == null) {
            PluginSettings global = pluginSettingsFactory.createGlobalSettings();
            if (sonarUrl == null)   sonarUrl  = (String) global.get(GLOBAL_SETTINGS_KEY + ".url");
            if (apiToken == null)   apiToken  = (String) global.get(GLOBAL_SETTINGS_KEY + ".token");
        }
        return new String[]{sonarUrl, apiToken, projectKey};
    }

    private JsonObject buildPrStatusResponse(SonarClient client, String sonarUrl, String projectKey, String prId) throws IOException {
        JsonObject status = client.getQualityGateStatus(projectKey, null, prId);
        JsonObject metrics = client.getMetrics(projectKey);
        JsonObject result = new JsonObject();
        if (status.has("projectStatus")) {
            JsonObject ps = status.getAsJsonObject("projectStatus");
            result.addProperty("status", ps.get("status").getAsString());
            if (ps.has("conditions")) result.add("conditions", ps.getAsJsonArray("conditions"));
        }
        result.addProperty("link", sonarUrl + "/dashboard?id=" + projectKey + "&pullRequest=" + prId);
        addMeasures(result, metrics);
        return result;
    }

    private JsonObject buildRepoStatusResponse(SonarClient client, String sonarUrl, String projectKey) throws IOException {
        JsonObject status = client.getQualityGateStatus(projectKey, null, null);
        JsonObject metrics = client.getMetrics(projectKey);
        JsonObject result = new JsonObject();
        if (status.has("projectStatus")) {
            result.addProperty("status", status.getAsJsonObject("projectStatus").get("status").getAsString());
        }
        result.addProperty("link", sonarUrl + "/dashboard?id=" + projectKey);
        addMeasures(result, metrics);
        return result;
    }

    private void addMeasures(JsonObject result, JsonObject metrics) {
        if (metrics.has("component")) {
            JsonArray measures = metrics.getAsJsonObject("component").getAsJsonArray("measures");
            for (JsonElement m : measures) {
                JsonObject mo = m.getAsJsonObject();
                result.addProperty(mo.get("metric").getAsString(), mo.get("value").getAsString());
            }
        }
    }

    private void forwardRequest(HttpServletRequest req, HttpServletResponse resp, String targetUrl, String apiToken) throws IOException {
        StringBuilder body = new StringBuilder();
        req.getParameterMap().forEach((key, values) -> {
            try {
                if (body.length() > 0) body.append("&");
                body.append(URLEncoder.encode(key, "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(values.length > 0 ? values[0] : "", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                log.error("Encoding error building proxy body", e);
            }
        });

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(targetUrl);
            post.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8));
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            String encoded = Base64.getEncoder().encodeToString((apiToken + ":").getBytes(StandardCharsets.UTF_8));
            post.setHeader("Authorization", "Basic " + encoded);

            log.info("Proxying to SonarQube: {}", targetUrl);
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                resp.setStatus(response.getStatusLine().getStatusCode());
                resp.setContentType("application/json");
                if (response.getEntity() != null) {
                    response.getEntity().writeTo(resp.getOutputStream());
                }
            }
        } catch (Exception e) {
            log.error("Failed to proxy request to SonarQube", e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
