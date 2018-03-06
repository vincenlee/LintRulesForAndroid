package com.ytjojo.lintjar.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.io.FileOutputStream;
import java.io.PrintStream;

/**
 * Created by Administrator on 2018/2/5 0005.
 */

public class Generator implements Opcodes{


    public static void main(String args[]) throws Exception {
        // 生成如下类文件
        // public class Example {
        //     public static void main (String[] args) {
        //         System.out.println("Hello world!");
        //     }
        // }

        // 为Example提供ClassWriter，要求Example继承自Object
        ClassWriter cw = new ClassWriter(0);
        cw.visit(V1_7, ACC_PUBLIC, "Example", null, "java/lang/Object", null);

        // 为隐含的默认构造器创建MethodWriter
        MethodVisitor mw = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null,
                null);
        // 将this变量放入局部变量表
        mw.visitVarInsn(ALOAD, 0);
        // 调用父类的默认构造函数
        mw.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V",false);
        mw.visitInsn(RETURN);
        // 这段代码使用了最大为1的栈元素，且只有一个局部变量
        mw.visitMaxs(1, 1);
        mw.visitEnd();

        // 为main方法创建MethodWriter
        mw = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "main",
                "([Ljava/lang/String;)V", null, null);
        // 将System的out域入栈
        mw.visitFieldInsn(GETSTATIC, "java/lang/System", "out",
                "Ljava/io/PrintStream;");
        // String类型的"Hello World!"常量入栈
        mw.visitLdcInsn("Hello world!");
        // 调用System.out的println方法
        mw.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println",
                "(Ljava/lang/String;)V", false);
        mw.visitInsn(RETURN);
        // 这段代码使用了最大为2的栈元素，包含两个局部变量
        mw.visitMaxs(2, 2);
        mw.visitEnd();

        // 获得Example类的字节码，并且动态加载它
        byte[] code = cw.toByteArray();

        FileOutputStream fos = new FileOutputStream("Example.class");
        fos.write(code);
        fos.close();

        MyClassLoader loader = new MyClassLoader();
        Class<?> exampleClass = loader.defineClass("Example", code);

        // 使用动态生成的类打印'Helloworld'
        exampleClass.getMethods()[0].invoke(null, new Object[] { null });

        // ------------------------------------------------------------------------
        // 使用GeneratorAdapter的示例(很方便但是更慢)
        // ------------------------------------------------------------------------

        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(V1_1, ACC_PUBLIC, "Example", null, "java/lang/Object", null);

        // 为隐含的默认构造器创建GeneratorAdapter
        Method m = Method.getMethod("void <init> ()");
        GeneratorAdapter mg = new GeneratorAdapter(ACC_PUBLIC, m, null, null, cw);
        mg.loadThis();
        mg.invokeConstructor(Type.getType(Object.class), m);
        mg.returnValue();
        mg.endMethod();

        // 为main方法创建GeneratorAdapter
        m = Method.getMethod("void main (String[])");
        mg = new GeneratorAdapter(ACC_PUBLIC + ACC_STATIC, m, null, null, cw);
        mg.getStatic(Type.getType(System.class), "out",
                Type.getType(PrintStream.class));
        mg.push("Hello world!");
        mg.invokeVirtual(Type.getType(PrintStream.class),
                Method.getMethod("void println (String)"));
        mg.returnValue();
        mg.endMethod();

        cw.visitEnd();

        code = cw.toByteArray();
        loader = new MyClassLoader();
        exampleClass = loader.defineClass("Example", code);

        // 使用动态生成的类打印'Helloworld'
        exampleClass.getMethods()[0].invoke(null, new Object[] { null });
    }
}
