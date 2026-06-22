package com.yourcompany.bitbucket.sonar.check;

import com.atlassian.bitbucket.insight.InsightReport;
import com.atlassian.bitbucket.insight.InsightService;
import com.atlassian.bitbucket.insight.Result;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestRef;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.scm.pull.RepositoryMergeCheckContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class SonarMergeCheckTest {

    @Mock private InsightService insightService;
    @Mock private RepositoryMergeCheckContext context;
    @Mock private MergeRequest mergeRequest;
    @Mock private PullRequest pullRequest;
    @Mock private PullRequestRef fromRef;
    @Mock private Repository repository;
    @Mock private InsightReport report;

    private SonarMergeCheck mergeCheck;

    private static final String COMMIT = "abc123";
    private static final String REPORT_KEY = "sonarqube.quality.gate";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mergeCheck = new SonarMergeCheck(insightService);

        when(context.getMergeRequest()).thenReturn(mergeRequest);
        when(mergeRequest.getPullRequest()).thenReturn(pullRequest);
        when(pullRequest.getFromRef()).thenReturn(fromRef);
        when(fromRef.getLatestCommit()).thenReturn(COMMIT);
        when(fromRef.getRepository()).thenReturn(repository);
    }

    @Test
    public void testVetoWhenReportIsNull() {
        when(insightService.getReport(repository, COMMIT, REPORT_KEY)).thenReturn(null);

        mergeCheck.check(context);

        verify(mergeRequest).veto(
                eq("SonarQube Analysis Pending"),
                anyString()
        );
    }

    @Test
    public void testVetoWhenReportFails() {
        when(insightService.getReport(repository, COMMIT, REPORT_KEY)).thenReturn(report);
        when(report.getResult()).thenReturn(Result.FAIL);

        mergeCheck.check(context);

        verify(mergeRequest).veto(
                eq("SonarQube Quality Gate Failed"),
                anyString()
        );
    }

    @Test
    public void testAllowMergeWhenReportPasses() {
        when(insightService.getReport(repository, COMMIT, REPORT_KEY)).thenReturn(report);
        when(report.getResult()).thenReturn(Result.PASS);

        mergeCheck.check(context);

        verify(mergeRequest, never()).veto(anyString(), anyString());
    }
}
