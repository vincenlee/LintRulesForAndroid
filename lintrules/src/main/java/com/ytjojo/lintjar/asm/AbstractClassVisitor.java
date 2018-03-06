package com.ytjojo.lintjar.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
public abstract class AbstractClassVisitor extends ClassVisitor implements Opcodes{
    public static final int ASM_API_VERSION = Opcodes.ASM5;
    public AbstractClassVisitor(ClassVisitor delegate) {
        super(ASM_API_VERSION, delegate);
    }
}