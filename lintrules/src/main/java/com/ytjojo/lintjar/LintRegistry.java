package com.ytjojo.lintjar;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;

import java.util.Arrays;
import java.util.List;

public class LintRegistry extends IssueRegistry {
    @Override
    public List<Issue> getIssues() {
//        return Arrays.asList(InitCallDetector.ISSUE, LogDetector.ISSUE);
        return Arrays.asList(ProjectResouceDetector.ISSUE,InitCallDetector.ISSUE,RetrofitObserverCheck.ISSUE
                ,NamedForPrimitiveTypesOfProvidersEnforcer.ISSUE
        ,ProjectResouceDetector.ISSUE_DUPLICATE);
    }
}
