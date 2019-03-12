package com.ytjojo.lintjar.asm;


import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MyMethodVisitor extends MethodVisitor {
    int stackSize = 0;

    MyMethodVisitor(MethodVisitor mv) {
        super(Opcodes.ASM5, mv);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            if ("Landroid/content/res/Resources$Theme;".equals(desc)) {
                stackSize = 1;
                visitInsn(Opcodes.DUP);
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/meituan/hydra/runtime/Transformer",
                        "collectTheme",
                        "(Landroid/content/res/Resources$Theme;)V",
                        false);
            }
        }
        super.visitFieldInsn(opcode, owner, name, desc);
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        super.visitMaxs(maxStack + stackSize, maxLocals);
        stackSize = 0;
    }
}