package com.ytjojo.lintjar.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AddInterfaces extends AbstractClassVisitor {
    private Set newInterfaces;
    public AddInterfaces(ClassVisitor cv, Set newInterfaces) {
        super(cv);
        this.newInterfaces = newInterfaces;
    }
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        Set<String> ints = new HashSet(newInterfaces);
        ints.addAll(Arrays.asList(interfaces));
        super.visit(version, access, name, signature, superName, ints.toArray(new String[ints.size()]));
    }

    public static void main(String[] args) throws Exception {
        ClassReader cr = new ClassReader(new FileInputStream("TestBean.class"));
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);

        Set set = new HashSet();
        set.add(Type.getInternalName(Runnable.class));
        set.add(Type.getInternalName(Comparable.class));

        AddInterfaces cv = new AddInterfaces(cw, set);

        cr.accept(cv, ClassReader.SKIP_DEBUG);

        byte[] bytes = cw.toByteArray();
        // save to disk
        new FileOutputStream(new File("TestBean.class")).write(bytes);
    }
}