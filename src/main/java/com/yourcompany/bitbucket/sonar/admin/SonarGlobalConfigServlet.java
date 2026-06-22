package com.yourcompany.bitbucket.sonar.admin;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.soy.renderer.SoyException;
import com.atlassian.soy.renderer.SoyTemplateRenderer;
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
 * Servlet for global SonarQube configuration (System-wide).
 */
public class SonarGlobalConfigServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(SonarGlobalConfigServlet.class);
    private static final String GLOBAL_SETTINGS_KEY = "com.yourcompany.bitbucket.sonar.global.settings";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final SoyTemplateRenderer soyTemplateRenderer;

    public SonarGlobalConfigServlet(PluginSettingsFactory pluginSettingsFactory,
                                    SoyTemplateRenderer soyTemplateRenderer) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.soyTemplateRenderer = soyTemplateRenderer;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        
        Map<String, Object> data = new HashMap<>();
        data.put("sonarUrl", settings.get(GLOBAL_SETTINGS_KEY + ".url"));
        data.put("sonarToken", settings.get(GLOBAL_SETTINGS_KEY + ".token"));

        render(resp, "com.yourcompany.bitbucket.sonar:sonar-admin-soy", "com.yourcompany.bitbucket.sonar.globalConfigPage", data);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String sonarUrl = req.getParameter("sonarUrl");
        String sonarToken = req.getParameter("sonarToken");

        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        settings.put(GLOBAL_SETTINGS_KEY + ".url", sonarUrl);
        settings.put(GLOBAL_SETTINGS_KEY + ".token", sonarToken);

        resp.sendRedirect(req.getContextPath() + "/plugins/servlet/sonar/global-config?saved=true");
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
