package com.ytjojo.lintjar.asm;

import com.ytjojo.lintjar.AsmUtill;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * 方法耗时 visitor
 * Author: lwh
 * Date: 4/28/17 15:37.
 */

public class CostMethodClassVisitor extends ClassVisitor {

    private String className;
    //com.github.InjectAnnotation
    private String mAnnotationClassName;

	public CostMethodClassVisitor(String className,String annotationClassName,ClassVisitor classVisitor) {
        super(Opcodes.ASM5,classVisitor);
        this.className = className;
        this.mAnnotationClassName = annotationClassName;
    }
	public CostMethodClassVisitor(String className,ClassVisitor classVisitor) {
        this(className,null,classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature,
                                     String[] exceptions) {

        MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
        methodVisitor = new AdviceAdapter(Opcodes.ASM5, methodVisitor, access, name, desc) {

            boolean mNeedInject = false;
            private boolean isNeedInject(){

               return mNeedInject;
            }
            @Override
            public void visitCode() {
                super.visitCode();

            }

            @Override
            public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (AsmUtill.getDescriptor(mAnnotationClassName).equals(desc)) {
                    mNeedInject = true;

                }

                return super.visitAnnotation(desc, visible);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                super.visitFieldInsn(opcode, owner, name, desc);
            }



            @Override
            protected void onMethodEnter() {
                //super.onMethodEnter();
                //统计public static类方法
                if(access==Opcodes.ACC_STATIC+Opcodes.ACC_PUBLIC
                        && !name.equals("countStaticClass")
                        && !className.equals("com/meiyou/meetyoucost/CostLog")){
                    StringBuilder sb = new StringBuilder();
                    sb.append("Usopp MeetyouCost Statics :").append(className).append(":").append(name);
                    mv.visitLdcInsn(className);
                    mv.visitLdcInsn(name);
                    String log = sb.toString();
                    mv.visitLdcInsn(log);
                    mv.visitMethodInsn(INVOKESTATIC, "com/meiyou/meetyoucost/CostLog", "countStaticClass",
                            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false);

                }
                //统计方法耗时
                if(isNeedInject()){
                    mv.visitLdcInsn(className+":"+name+desc);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                    mv.visitMethodInsn(INVOKESTATIC, "com/meiyou/meetyoucost/CostLog", "setStartTime",
                            "(Ljava/lang/String;J)V", false);
                }
            }

            @Override
            protected void onMethodExit(int i) {
                //super.onMethodExit(i);
                if(isNeedInject()){
                    mv.visitLdcInsn(className+":"+name+desc);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false);
                    mv.visitMethodInsn(INVOKESTATIC, "com/meiyou/meetyoucost/CostLog", "setEndTime",
                            "(Ljava/lang/String;J)V", false);

                }
            }
        };
        return methodVisitor;
        //return super.visitMethod(i, s, s1, s2, strings);

    }
}