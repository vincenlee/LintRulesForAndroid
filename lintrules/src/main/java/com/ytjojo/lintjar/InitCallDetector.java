package com.ytjojo.lintjar;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStaticReferenceElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSuperExpression;

import java.util.Collections;
import java.util.List;


/**
 * Created by ytjojo on 21/12/2017.
 */

public class InitCallDetector extends Detector implements Detector.JavaPsiScanner {
    public static Issue ISSUE = Issue.create(
            "InitNotCall",
            "init not call in onCreate",
            "please call init method in onCreate method",
            // 这个主要是用于对问题的分类，不同的问题就可以集中在一起显示。
            Category.MESSAGES,
            // 优先级
            9,
            // 定义查找问题的严重级别
            Severity.WARNING,
            // 提供处理该问题的Detector和该Detector所关心的资源范围。当系统生成了抽象语法树（Abstract syntax tree，简称AST），或者遍历xml资源时，就会调用对应Issue的处理器Detector。
            new Implementation(InitCallDetector.class,
                    Scope.JAVA_FILE_SCOPE)
    );


    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.singletonList(PsiMethod.class);
    }

    @Override
    public JavaElementVisitor createPsiVisitor(JavaContext context) {
        return new JavaElementVisitor(){
            @Override
            public void visitClass(PsiClass aClass) {
                super.visitClass(aClass);
            }

            @Override
            public void visitCallExpression(PsiCallExpression callExpression) {
                super.visitCallExpression(callExpression);
            }

            @Override
            public void visitMethod(PsiMethod method) {
                if(method.getName().equals("onCreate")){
                    PsiClass parent = method.getContainingClass().getSuperClass();
                    if(parent.getName().equals("BaseActivity")){
                        checkCallInit(context,method);
                    }else {
                        outloop:
                        while (!parent.getName().equals("BaseActivity") &&parent.getQualifiedName() !=Object.class.getName()){
                            parent = method.getContainingClass().getSuperClass();
                            PsiMethod[] superMettheds = parent.getAllMethods();
                            boolean hasInit = false;
                            for(PsiMethod m: superMettheds){
                                if(m.getName().equals("init")){
                                    hasInit = true;
                                }
                            }
                            if(!hasInit){
                                break outloop;
                            }
                            if(parent.getName().equals("BaseActivity")){
                                checkCallInit(context,method);
                                break;
                            }
                        }
                    }


                }
                super.visitMethod(method);
            }

            @Override
            public void visitMethodCallExpression(PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
            }

            @Override
            public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
                super.visitMethodReferenceExpression(expression);
            }

            @Override
            public void visitImportStaticStatement(PsiImportStaticStatement statement) {
                if (mScannedForStaticImports) {
                    return;
                }
                if (statement.isOnDemand()) {
                    // Wildcard import of whole type:
                    // import static pkg.R.type.*;
                    // We have to do a more expensive analysis here to
                    // for example recognize "x" as a reference to R.string.x
                    mScannedForStaticImports = true;
                    statement.getContainingFile().accept(new JavaRecursiveElementVisitor() {
                        @Override
                        public void visitReferenceExpression(PsiReferenceExpression expression) {
                            PsiElement resolved = expression.resolve();
                            if (resolved instanceof PsiField) {
                                ResourceUrl url = ResourceEvaluator.getResourceConstant(resolved);
                                if (url != null && !url.framework) {

                                }
                            }
                            super.visitReferenceExpression(expression);
                        }
                    });
                } else {
                    PsiElement resolved = statement.resolve();
                    if (resolved instanceof PsiField) {

                    }
                }
            }

            @Override
            public void visitImportStatement(PsiImportStatement statement) {
                super.visitImportStatement(statement);
            }

            @Override
            public void visitImportStaticReferenceElement(PsiImportStaticReferenceElement reference) {
                super.visitImportStaticReferenceElement(reference);
            }
        };
    }
    public static boolean mScannedForStaticImports;


    private void checkCallInit(@NonNull JavaContext context,@NonNull PsiMethod method){
        if (!CallInitVisitor.callInit( method)) {
            String message = "onCreate method should call init method";
            Location location = context.getLocation(method);
            context.report(ISSUE, method, location, message);
        }
    }



    /** Visits a method and determines whether the method calls its super method */
    private static class CallInitVisitor extends JavaRecursiveElementVisitor {
        private final PsiMethod mMethod;
        private boolean mCalled;
        JavaContext context;

        public static boolean callInit(@NonNull PsiMethod method) {
            CallInitVisitor visitor = new CallInitVisitor(method);
            method.accept(visitor);
            return visitor.mCalled;
        }

        private CallInitVisitor(@NonNull PsiMethod method) {
            mMethod = method;
        }

        @Override
        public void visitSuperExpression(PsiSuperExpression node) {
            super.visitSuperExpression(node);
        }

        @Override
        public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {

            super.visitMethodReferenceExpression(expression);
        }

        @Override
        public void visitMethod(PsiMethod method) {

            super.visitMethod(method);
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {

            if(expression.getMethodExpression().getReferenceName().equals("init")){
                mCalled = true;
            }
            super.visitMethodCallExpression(expression);
        }
    }
}
