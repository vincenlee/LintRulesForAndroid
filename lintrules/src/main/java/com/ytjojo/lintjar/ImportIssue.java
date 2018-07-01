package com.ytjojo.lintjar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Created by Administrator on 2018/5/13 0013.
 */

public class ImportIssue extends Detector implements Detector.JavaPsiScanner {
    static final Severity SEVERITY = Severity.ERROR;
    private static final String ISSUE_ID = ImportIssue.class.getSimpleName();
    private static final String ISSUE_TITLE = "should not import this package";
    private static final String ISSUE_DESCRIPTION = "should not import this package";
    private static final int ISSUE_PRIORITY = 10;   // Highest.
    static final Issue ISSUE = Issue.create(ISSUE_ID,
            ISSUE_TITLE,
            ISSUE_DESCRIPTION,
            Category.CORRECTNESS,
            ISSUE_PRIORITY,
            SEVERITY,
            new Implementation(ImportIssue.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.JAVA_FILE_SCOPE;
    }

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        List<Class<? extends PsiElement>> types = new ArrayList<>(4);
        types.add(PsiJavaCodeReferenceElement.class);
        types.add(PsiImportStatement.class);
        types.add(PsiMethodCallExpression.class);
//        types.add(PsiMethod.class);
        //types.add(PsiClass.class);
        return types;
    }

    @Nullable
    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        return new ImportChecker(context);
    }
    ArrayList<String> mPackages= new ArrayList<>();
    private class ImportChecker extends JavaElementVisitor {
        private final JavaContext mContext;

        public ImportChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitImportStatement(PsiImportStatement statement) {
           String name = statement.getQualifiedName();
            if(mPackages.contains(name)){

            }
        }

        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
//                String name = reference.getQualifiedName();
            PsiElement ss = reference.getParent().getParent().getParent();
            PsiJavaFile file = (PsiJavaFile) reference.getContainingFile();
            String packageName = file.getPackageName();
            String  name = reference.getReferenceName();
            String text = reference.getText();
//                text = reference.getCanonicalText();
            if(mPackages.contains(name)){

            }

        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
           PsiReferenceExpression reference = expression.getMethodExpression();
            if(reference.getReferenceName().contains("equals")){
                if(expression.resolveMethod().getContainingClass().getQualifiedName().equals("java.lang.String")){
                    PsiElement[] children = reference.getChildren();
                    if(children[0] instanceof PsiReferenceExpression){
                        if(expression.getArgumentList().getChildren()[0] instanceof PsiLiteralExpression){
                            String a= "";
                        }
                    }
                }
            }

        }

        @Override
        public void visitMethod(PsiMethod method) {
            PsiCodeBlock body = method.getBody();
            if(body == null){
                return;
            }
            PsiStatement[] statements  = body.getStatements();
            if(statements != null){
                for (PsiStatement item:statements){
                    PsiElement child = item.getChildren()[0];
                    if(item.getChildren()[0] instanceof PsiMethodCallExpression){
                        visitMethodCallExpression((PsiMethodCallExpression) child);
                    }
                }
            }

        }

        @Override
        public void visitLiteralExpression(PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
        }
    }
}
