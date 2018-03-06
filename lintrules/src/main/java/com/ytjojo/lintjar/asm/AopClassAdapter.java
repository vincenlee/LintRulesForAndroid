package com.ytjojo.lintjar.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;


public class AopClassAdapter extends ClassVisitor implements Opcodes {
    private boolean isInterface;

    public AopClassAdapter(int api, ClassVisitor cv) {
        super(api, cv);
    }

    String mTargetMethod;
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        //更改类名，并使新类继承原有的类。
        super.visit(version, access, name + "_Tmp", signature, name, interfaces);
        isInterface = (access & ACC_INTERFACE) != 0;

        {
            MethodVisitor mv = super.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, name, "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
    }
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if ("<init>".equals(name))
            return null;
        if (!name.equals(mTargetMethod))
            return super.visitMethod(access,name,desc,signature,exceptions);
        //
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return new AopMethod(this.api, mv);
    }

    class AopMethod extends MethodVisitor implements Opcodes {
        public AopMethod(int api, MethodVisitor mv) {
            super(api, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            this.visitMethodInsn(INVOKESTATIC, "asm/AopInterceptor", "beforeInvoke", "()V", false);
        }

        @Override
        public void visitInsn(int opcode) {
            if(opcode >= IRETURN && opcode <= RETURN){
                mv.visitMethodInsn(INVOKESTATIC, "asm/AopInterceptor", "afterInvoke", "()V", false);
            }
            super.visitInsn(opcode);
        }
    }
}
