package com.ytjojo.lintjar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

import static com.android.SdkConstants.DOT_JAR;

/**
 * Created by Administrator on 2018/1/31 0031.
 */

public class AsmUtill {

    public static  byte[] toByties(File file) throws IOException {
        return Files.toByteArray(file);
    }

    /**
     * Returns true if the given class node represents a static inner class.
     *
     * @param classNode the inner class to be checked
     * @return true if the class node represents an inner class that is static
     */
    public static boolean isStaticInnerClass(@NonNull ClassNode classNode) {
        // Note: We can't just filter out static inner classes like this:
        //     (classNode.access & Opcodes.ACC_STATIC) != 0
        // because the static flag only appears on methods and fields in the class
        // file. Instead, look for the synthetic this pointer.

        @SuppressWarnings("rawtypes") // ASM API
                List fieldList = classNode.fields;
        for (Object f : fieldList) {
            FieldNode field = (FieldNode) f;
            if (field.name.startsWith("this$") && (field.access & Opcodes.ACC_SYNTHETIC) != 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true if the given class node represents an anonymous inner class
     *
     * @param classNode the class to be checked
     * @return true if the class appears to be an anonymous class
     */
    public static boolean isAnonymousClass(@NonNull ClassNode classNode) {
        if (classNode.outerClass == null) {
            return false;
        }

        String name = classNode.name;
        int index = name.lastIndexOf('$');
        if (index == -1 || index == name.length() - 1) {
            return false;
        }

        return Character.isDigit(name.charAt(index + 1));
    }

    /**
     * Returns the previous opcode prior to the given node, ignoring label and
     * line number nodes
     *
     * @param node the node to look up the previous opcode for
     * @return the previous opcode, or {@link Opcodes#NOP} if no previous node
     *         was found
     */
    public static int getPrevOpcode(@NonNull AbstractInsnNode node) {
        AbstractInsnNode prev = getPrevInstruction(node);
        if (prev != null) {
            return prev.getOpcode();
        } else {
            return Opcodes.NOP;
        }
    }

    /**
     * Returns the previous instruction prior to the given node, ignoring label
     * and line number nodes.
     *
     * @param node the node to look up the previous instruction for
     * @return the previous instruction, or null if no previous node was found
     */
    @Nullable
    public static AbstractInsnNode getPrevInstruction(@NonNull AbstractInsnNode node) {
        AbstractInsnNode prev = node;
        while (true) {
            prev = prev.getPrevious();
            if (prev == null) {
                return null;
            } else {
                int type = prev.getType();
                if (type != AbstractInsnNode.LINE && type != AbstractInsnNode.LABEL
                        && type != AbstractInsnNode.FRAME) {
                    return prev;
                }
            }
        }
    }

    /**
     * Returns the next opcode after to the given node, ignoring label and line
     * number nodes
     *
     * @param node the node to look up the next opcode for
     * @return the next opcode, or {@link Opcodes#NOP} if no next node was found
     */
    public static int getNextOpcode(@NonNull AbstractInsnNode node) {
        AbstractInsnNode next = getNextInstruction(node);
        if (next != null) {
            return next.getOpcode();
        } else {
            return Opcodes.NOP;
        }
    }

    /**
     * Returns the next instruction after to the given node, ignoring label and
     * line number nodes.
     *
     * @param node the node to look up the next node for
     * @return the next instruction, or null if no next node was found
     */
    @Nullable
    public static AbstractInsnNode getNextInstruction(@NonNull AbstractInsnNode node) {
        AbstractInsnNode next = node;
        while (true) {
            next = next.getNext();
            if (next == null) {
                return null;
            } else {
                int type = next.getType();
                if (type != AbstractInsnNode.LINE && type != AbstractInsnNode.LABEL
                        && type != AbstractInsnNode.FRAME) {
                    return next;
                }
            }
        }
    }


    @Nullable
    private static MethodInsnNode findConstructorInvocation(
            @NonNull MethodNode method,
            @NonNull String className) {
        InsnList nodes = method.instructions;
        for (int i = 0, n = nodes.size(); i < n; i++) {
            AbstractInsnNode instruction = nodes.get(i);
            if (instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (className.equals(call.owner)) {
                    return call;
                }
            }
        }

        return null;
    }


    /** Returns the outer class node of the given class node
     * @param classNode the inner class node
     * @return the outer class node */
    public ClassNode getOuterClassNode(ArrayDeque<ClassNode> mOuterClasses, @NonNull ClassNode classNode) {
        String outerName = classNode.outerClass;
        Iterator<ClassNode> iterator = mOuterClasses.iterator();
        while (iterator.hasNext()) {
            ClassNode node = iterator.next();
            if (outerName != null) {
                if (node.name.equals(outerName)) {
                    return node;
                }
            } else if (node == classNode) {
                return iterator.hasNext() ? iterator.next() : null;
            }
        }

        return null;
    }

    /**
     * Case insensitive ends with
     *
     * @param string the string to be tested whether it ends with the given
     *            suffix
     * @param suffix the suffix to check
     * @return true if {@code string} ends with {@code suffix},
     *         case-insensitively.
     */
    public static boolean endsWith(@NonNull String string, @NonNull String suffix) {
        return string.regionMatches(true /* ignoreCase */, string.length() - suffix.length(),
                suffix, 0, suffix.length());
    }

    public static boolean isJarFile(File jar){
        return endsWith(jar.getPath(), DOT_JAR);
    }

    public static boolean verify(ClassWriter cw){
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        CheckClassAdapter.verify(new ClassReader(cw.toByteArray()), false, pw);
        return sw.toString().length()==0;
    }


    /**
     * @return Linternal/class/name;
     */
    public static String getDescriptor(String className)
    {
        return "L" + className.replace('.', '/') + ";";
    }

    /**
     */
    public static String desc(String deobfDesc)
    {
        // for each internal name, replace with the obfuscated version
        Matcher classNameMatcher = Pattern.compile("L([^;]+);").matcher(deobfDesc);
        StringBuffer obfDescBuffer = new StringBuffer(deobfDesc.length());
        while (classNameMatcher.find())
        {
            classNameMatcher.appendReplacement(obfDescBuffer, getDescriptor(classNameMatcher.group(1).replace('/', '.')));
        }
        classNameMatcher.appendTail(obfDescBuffer);
        return obfDescBuffer.toString();
    }
}
