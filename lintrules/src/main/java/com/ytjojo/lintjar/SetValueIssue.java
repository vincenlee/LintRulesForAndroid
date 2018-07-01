package com.ytjojo.lintjar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiReferenceExpression;

import org.gradle.tooling.events.OperationType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Created by Administrator on 2018/5/23 0023.
 */

public class SetValueIssue  extends Detector implements Detector.JavaPsiScanner {
    static final Severity SEVERITY = Severity.ERROR;
    private static final String ISSUE_ID = SetValueIssue.class.getSimpleName();
    private static final String ISSUE_TITLE = "should not set value from self";
    private static final String ISSUE_DESCRIPTION = "should not set value from self";
    private static final int ISSUE_PRIORITY = 10;   // Highest.
    static final Issue ISSUE = Issue.create(ISSUE_ID,
            ISSUE_TITLE,
            ISSUE_DESCRIPTION,
            Category.CORRECTNESS,
            ISSUE_PRIORITY,
            SEVERITY,
            new Implementation(SetValueIssue.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.JAVA_FILE_SCOPE;
    }

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        List<Class<? extends PsiElement>> types = new ArrayList<>(4);
//        types.add(PsiExpressionStatement.class);
        types.add(PsiAssignmentExpression.class);
        return types;
    }

    @Nullable
    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        return new SetValueChecker(context);
    }
    private class SetValueChecker extends JavaElementVisitor {
        private final JavaContext mContext;

        public SetValueChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
           if(expression.getOperationTokenType().equals(JavaTokenType.EQ)){
               if(expression.getLExpression() instanceof PsiReferenceExpression && expression.getRExpression()  instanceof PsiReferenceExpression){
                   PsiReferenceExpression le = (PsiReferenceExpression) expression.getLExpression();
                   PsiReferenceExpression re = (PsiReferenceExpression) expression.getRExpression();
                   if(le.getReferenceName().equals(re.getReferenceName())){
                        System.out.println(" ");
                   }

               }
           }
        }
    }
}
