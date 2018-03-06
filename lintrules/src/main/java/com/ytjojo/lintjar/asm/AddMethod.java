package com.ytjojo.lintjar.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class AddMethod extends AbstractClassVisitor {
    private boolean isInterface;
    private boolean isPresent;
    private String name;
    private String desc;
    public AddMethod(String name, String desc, ClassVisitor delegate) {
        super(delegate);
        this.name = name;
        this.desc = desc;
    }
    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        isInterface = (access & ACC_INTERFACE) != 0;
        super.visit(version, access, name, signature, superName, interfaces);
    }
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (name.equals(this.name) && desc.equals(this.desc)) {
            isPresent = true;
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
    public void visitEnd() {
        if (!isInterface && !isPresent) {
            MethodVisitor mv = super.visitMethod(ACC_PUBLIC + ACC_STATIC, name, desc, null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitLineNumber(17, l0);
            mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            mv.visitLdcInsn("liming");
            mv.visitMethodInsn(INVOKESTATIC, "asm/TestBean", "sayHello", "(Ljava/lang/String;)Ljava/lang/String;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitLineNumber(18, l1);
            mv.visitInsn(RETURN);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLocalVariable("args", "[Ljava/lang/String;", null, l0, l2, 0);
            mv.visitMaxs(1, 1);//设置ClassWriter.COMPUTE_MAXS 或 ClassWriter.COMPUTE_FRAMES，此处的值会被忽略，但此方法必需显示调用一下！！！
            mv.visitEnd();
        }
        super.visitEnd();
    }

    public static void main(String[] args) throws Exception {
        ClassReader classReader = new ClassReader(new FileInputStream("TestBean.class"));
        ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
        String methodDescriptor = Type.getMethodDescriptor(Type.getType(Void.class), Type.getType(Integer.class), Type.getType(String.class));
        System.out.println("方法描述符：" + methodDescriptor);
        AddMethod addMethod = new AddMethod("newMethod", methodDescriptor, classWriter);
        classReader.accept(addMethod, ClassReader.SKIP_DEBUG);
        byte[] newClass = classWriter.toByteArray();
        File newFile = new File("TestBean.class");
        new FileOutputStream(newFile).write(newClass);
    }
}