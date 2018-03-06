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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Throws lint errors if methods returning Rx primitives (Observable, Single, etc.) are found without @CheckResult annotation.
 */
public class NamedForPrimitiveTypesOfProvidersEnforcer extends Detector implements Detector.JavaPsiScanner {

  static final Severity SEVERITY = Severity.ERROR;
  private static final String ISSUE_ID = NamedForPrimitiveTypesOfProvidersEnforcer.class.getSimpleName();
  private static final String ISSUE_TITLE = "@Named for primitive types";
  private static final String ISSUE_DESCRIPTION = "@Named for primitive types";
  private static final int ISSUE_PRIORITY = 10;   // Highest.
  static final Issue ISSUE = Issue.create(ISSUE_ID,
      ISSUE_TITLE,
      ISSUE_DESCRIPTION,
      Category.CORRECTNESS,
      ISSUE_PRIORITY,
      SEVERITY,
      new Implementation(NamedForPrimitiveTypesOfProvidersEnforcer.class, Scope.JAVA_FILE_SCOPE)
  );
  private final List<String> primitiveTypes = Arrays.asList("byte",
      "short",
      "int",
      "float",
      "double",
      "boolean",
      "java.lang.String"
  );

  @Override
  public EnumSet<Scope> getApplicableFiles() {
    return Scope.JAVA_FILE_SCOPE;
  }

  @Override
  public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
    return Collections.singletonList(PsiClass.class);
  }


  @Nullable
  @Override
  public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
    return new AnnotationChecker(context);
  }

  private class AnnotationChecker extends JavaElementVisitor {
    private final JavaContext mContext;

    public AnnotationChecker(JavaContext context) {
      mContext = context;
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      String type = annotation.getQualifiedName();
      if (type == null || !type.startsWith("retrofit2.http.")) {
        return;
      }
      if (annotation.getParent() instanceof PsiModifierList && annotation.getParent().getParent() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) annotation.getParent().getParent();
        if (!method.isConstructor() && PsiType.VOID.equals(method.getReturnType())) {

        }
      }
      if(annotation.getParent() instanceof  PsiClass){
        String p = "dagger.Provides";
        String n = "javax.inject.Named";

        if(annotation.getQualifiedName().equals("dagger.Module")){

        }
      }
    }

    @Override public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
    }

    @Override public void visitAnnotationMethod(PsiAnnotationMethod method) {
      super.visitAnnotationMethod(method);
    }

    @Override public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
    }

    @Override public void visitModifierList(PsiModifierList list) {
      super.visitModifierList(list);
    }
  }

}