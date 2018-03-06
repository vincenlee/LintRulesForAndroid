package com.ytjojo.lintjar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Created by Administrator on 2018/2/27 0027.
 */

public class RetrofitObserverCheck  extends Detector implements Detector.JavaPsiScanner {
	public static final Implementation IMPLEMENTATION = new Implementation(
			RetrofitObserverCheck.class,
			Scope.JAVA_FILE_SCOPE);
	/** Flags should typically be specified as bit shifts */
	public static final Issue ISSUE = Issue.create(
			"implementsSerializable",
			"not implements Serializable",
			"not implements Serializable" ,
			Category.CORRECTNESS,
			3,
			Severity.WARNING,
			IMPLEMENTATION);

	@Override
	public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
		List<Class<? extends PsiElement>> types = new ArrayList<>(2);
		types.add(PsiAnnotation.class);
		//types.add(PsiClass.class);
		return types;
	}
	@Nullable
	@Override
	public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
		return new AnnotationChecker(context);
	}

	HashSet<PsiClass> checked= new HashSet<>();
	private class AnnotationChecker extends JavaElementVisitor {
		private final JavaContext mContext;
		boolean isInterface;

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
				PsiTypeElement psiTypeElement= method.getReturnTypeElement();

				if(psiTypeElement.getInnermostComponentReferenceElement().getParameterList().getTypeParameterElements()[0]!=null) {
					psiTypeElement = psiTypeElement.getInnermostComponentReferenceElement()
							.getParameterList()
							.getTypeParameterElements()[0];
					String name =analyse(checked,psiTypeElement);
					if(name != null){
						String message = "class file "+ name +" should @Keep or implements Serializable ";
						Location location = mContext.getLocation(method);
						mContext.report(ISSUE, method, location, message);
					}
				}

			}
		}

		@Override public void visitClass(PsiClass aClass) {
			super.visitClass(aClass);
			isInterface = aClass.isInterface();

		}
	}
	public static String analyse(HashSet<PsiClass> checked,PsiTypeElement psiTypeElement){

		PsiClass psiClass= ((PsiClassType)psiTypeElement.getType()).resolve();
		if(checked.contains(psiClass)){
			return null;
		}
		String name = isKeep(checked,psiClass);
		if(name !=null) {
			return name;
		}
		if( psiTypeElement.getInnermostComponentReferenceElement().getParameterList() ==null){
			return null;
		}
		PsiTypeElement[] typeParameters = psiTypeElement.getInnermostComponentReferenceElement().getParameterList().getTypeParameterElements();
		if (typeParameters != null && typeParameters.length > 0) {
			for(PsiTypeElement itemType:  typeParameters) {
				if(!checked.contains(((PsiClassType) itemType.getType()).resolve())){
					name = analyse(checked,itemType);
					if(name !=null){
						return name;
					}
				}
			}

		}
		return null;

	}
	public static String isKeep(HashSet<PsiClass> checked,PsiClass psiClass){
		if(checked.contains(psiClass)){
			return null;
		}
		String  qualifiedName= psiClass.getQualifiedName();
		if(qualifiedName.startsWith("java.lang")||qualifiedName.startsWith("java.util")||qualifiedName.startsWith("java.math")){
			return null;
		}

		checked.add(psiClass);
		PsiClass superPsiClass = psiClass;
		boolean isKeep = false;
		outloop:
		while (!superPsiClass.getQualifiedName().equals("java.lang.Object")){
			PsiClass[] interfaces = superPsiClass.getInterfaces();
			if(interfaces !=null && interfaces.length>0){
				for(PsiClass interfaceitem:interfaces){
					if(interfaceitem.getQualifiedName().contains("Serializable")){
						isKeep = true;
						break outloop;
					}
				}
			}else {

			}
			superPsiClass = superPsiClass.getSuperClass();
		}
		PsiModifierList psiModifierList = psiClass.getModifierList();
		if(psiModifierList.getAnnotations() != null){
			for(PsiAnnotation psiAnnotation: psiModifierList.getAnnotations()){
				if(psiAnnotation.getQualifiedName().equals("android.support.annotation.Keep")){
					isKeep = true;
				}
			}
		}
		if(!isKeep){
			return psiClass.getQualifiedName();
		}
		PsiField[] psiFields =  psiClass.getFields();
		if(psiFields !=null && psiFields.length> 0){
			for(PsiField field :psiFields){
				if(field.getType() instanceof PsiClassType && !psiModifierList.hasModifierProperty("static")){
					PsiClassType psiClassType= (PsiClassType) field.getType();
					PsiClass psiFieldClass =psiClassType.resolve();
					for(PsiType type :psiClassType.getParameters()){
						if(type instanceof PsiClassType){
							PsiClass parameterType = ((PsiClassType) type).resolve();
							if(isKeep(checked,parameterType)!=null){
								return parameterType.getQualifiedName();
							}
						}
					}
					if(isKeep(checked,psiFieldClass)!=null){
						return psiFieldClass.getQualifiedName();
					}

				}
			}
		}

		return null;

	}
}
