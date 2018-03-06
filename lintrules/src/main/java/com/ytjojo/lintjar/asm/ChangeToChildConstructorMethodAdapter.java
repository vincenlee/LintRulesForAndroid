package com.ytjojo.lintjar.asm;



import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ChangeToChildConstructorMethodAdapter extends MethodVisitor {
    private String superClassName; 
 
    public ChangeToChildConstructorMethodAdapter(MethodVisitor mv,
        String superClassName) { 
        super( Opcodes.ASM5,mv);
        this.superClassName = superClassName; 
    } 

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, 
        String desc,boolean itf) {

        // 调用父类的构造函数时
        if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>")) {
            owner = superClassName; 
        } 
        super.visitMethodInsn(opcode, owner, name, desc,itf);// 改写父类为 superClassName
    }
}