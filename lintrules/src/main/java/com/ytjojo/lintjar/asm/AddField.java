package com.ytjojo.lintjar.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class AddField extends AbstractClassVisitor {
    private boolean isPresent;
    private int access;
    private String name;
    private String desc;
    private Object value;
    public AddField(int access, String name, String desc, Object value, ClassVisitor delegate) {
        super(delegate);
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.value = value;
    }
    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if (name.equals(this.name)) {
            isPresent = true;
        }
        return super.visitField(access, name, desc, signature, value);
    }
    @Override
    public void visitEnd() {
        if (!isPresent) {
            FieldVisitor fv = super.visitField(access, name, desc, null, value);
            if (fv != null) {
                fv.visitEnd();//不是原有的属性，故不会有事件发出的，自己 end 掉。
            }
        }
        super.visitEnd();
    }


    public static void main(String[] args) throws Exception {
        ClassReader classReader = new ClassReader(new FileInputStream("TestBean.class"));

        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor addField = new AddField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL,
                "newField2",
                Type.getDescriptor(String.class),
                "newValue2",
                classWriter);
        classReader.accept(addField, ClassReader.SKIP_DEBUG);
        byte[] newClass = classWriter.toByteArray();
        File newFile = new File("TestBean.class");
        new FileOutputStream(newFile).write(newClass);
    }
}
