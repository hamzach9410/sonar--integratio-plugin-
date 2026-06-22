package com.yourcompany.bitbucket.sonar.check;

import com.atlassian.bitbucket.insight.InsightReport;
import com.atlassian.bitbucket.insight.InsightService;
import com.atlassian.bitbucket.insight.Result;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.scm.pull.RepositoryMergeCheck;
import com.atlassian.bitbucket.scm.pull.RepositoryMergeCheckContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * Merge check that prevents merges if the SonarQube Quality Gate has failed or is still pending.
 */
public class SonarMergeCheck implements RepositoryMergeCheck {
    private static final Logger log = LoggerFactory.getLogger(SonarMergeCheck.class);
    private static final String REPORT_KEY = "sonarqube.quality.gate";

    private final InsightService insightService;

    public SonarMergeCheck(InsightService insightService) {
        this.insightService = insightService;
    }

    @Override
    public void check(@Nonnull RepositoryMergeCheckContext context) {
        PullRequest pr = context.getMergeRequest().getPullRequest();
        String commitId = pr.getFromRef().getLatestCommit();

        log.debug("Checking SonarQube quality gate for PR #{} at commit {}", pr.getId(), commitId);

        InsightReport report = insightService.getReport(
                pr.getFromRef().getRepository(),
                commitId,
                REPORT_KEY
        );

        if (report == null) {
            context.getMergeRequest().veto(
                    "SonarQube Analysis Pending",
                    "SonarQube analysis is still running or hasn't started yet. " +
                    "Please wait for the results to appear in the 'Quality' tab."
            );
        } else if (report.getResult() == Result.FAIL) {
            context.getMergeRequest().veto(
                    "SonarQube Quality Gate Failed",
                    "The SonarQube Quality Gate failed for this pull request. " +
                    "Check the 'Quality' tab for specific issues and metrics."
            );
        }
        
        // If report exists and result is PASS or indeterminate (and we don't block on indeterminate), 
        // the check passes by not calling veto().
    }
}
