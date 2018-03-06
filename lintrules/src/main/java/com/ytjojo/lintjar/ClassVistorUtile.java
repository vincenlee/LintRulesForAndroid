package com.ytjojo.lintjar;

import com.android.SdkConstants;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by Administrator on 2018/1/19 0019.
 */

public class ClassVistorUtile {

    public static void jar(File file) {
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(file);
            Enumeration<JarEntry> entries = jarFile.entries();
            boolean logged = false;
            // 如果jar中的class中的类包含注解则先收集起来
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.isDirectory()) {
                    continue;
                }
                String className = jarEntry.getName();
                if (className.endsWith(SdkConstants.DOT_CLASS)) {
                    className.replace('/', '.');
                    byte[] bytes = IOUtils.toByteArray(jarFile.getInputStream(jarEntry));
                    find(bytes);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static boolean find(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        if (classNode.superName.contains("IssueRegistry")) {
            for (Object it : classNode.methods) {
                MethodNode method = (MethodNode) it;
                if (method.name.equals("getIssues")) {
                    InsnList nodes = method.instructions;
                    for (int i = 0, n = nodes.size(); i < n; i++) {
                        AbstractInsnNode instruction = nodes.get(i);
                        int type = instruction.getType();
                        if (type == AbstractInsnNode.METHOD_INSN) {
                            MethodInsnNode call = (MethodInsnNode) instruction;
                            String owner = call.owner;//方法所在的类java/util/Arrays
                            String name = call.name;//方法名asList
                            String desc = call.desc;//方法返回值([Ljava/lang/Object;)Ljava/util/List;
                        }
                        if(type == AbstractInsnNode.FIELD_INSN){
                            FieldInsnNode fieldInsnNode = (FieldInsnNode) instruction;
                        }
                    }
                    method.accept(new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                            super.visitMethodInsn(opcode, owner, name, desc, itf);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                            super.visitFieldInsn(opcode, owner, name, desc);
                        }

                        @Override
                        public void visitLdcInsn(Object cst) {
                            super.visitLdcInsn(cst);
                        }
                    });
                    System.out.print(method);
                    return true;
                }
            }
        }
        return false;
    }
}