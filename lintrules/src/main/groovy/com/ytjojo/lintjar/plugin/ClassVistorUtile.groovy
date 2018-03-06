package com.ytjojo.lintjar.plugin

import com.android.SdkConstants
import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

import java.util.jar.JarEntry
import java.util.jar.JarFile
/**
 * Created by Administrator on 2018/1/19 0019.
 */

public class ClassVistorUtile {

    public static void jar(File file){
        def jarFile = new JarFile(file)
        def entries = jarFile.entries()
        boolean logged = false;
        // 如果jar中的class中的类包含注解则先收集起来
        while (entries.hasMoreElements()) {
            JarEntry jarEntry = entries.nextElement()
            if (jarEntry.isDirectory()) {
                continue;
            }
            String className = jarEntry.getName();
            if(className.endsWith(SdkConstants.DOT_CLASS)){
                className.replace('/','.')
                byte[] bytes= IOUtils.toByteArray( jarFile.getInputStream(jarEntry))
                find(bytes)
            }
        }
    }
    public static boolean find(byte[] bytes){
        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode,0)
        if(classNode.superName.contains("IssueRegistry")){
            classNode.methods.each {
                MethodNode method = (MethodNode) it;
                method.accept(new MethodVisitor() {
                    @Override
                    void visitParameter(String name, int access) {
                        super.visitParameter(name, access)
                    }
                })
                if(method.name.equals("getIssues")){
                    for(String s: method.exceptions){
                       println(s)
                    }
                }
            }
            return true;
        }
        return false;
    }
}
