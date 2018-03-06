//package com.ytjojo.lintjar;
//
//import org.objectweb.asm.ClassVisitor;
//import org.objectweb.asm.FieldVisitor;
//import org.objectweb.asm.MethodVisitor;
//import org.objectweb.asm.Opcodes;
//
///**
// * KeepResUsageVisitor会把methodNode、constantNode、fieldNode、classNode调用关系转换成有向图
// */
//class KeepResUsageVisitor extends ClassVisitor {
//
//    private String className;
//
//    public KeepResUsageVisitor() {
//        super(Opcodes.ASM5);
//    }
//
//    @Override
//    public void visit(int version, int access, String name, String signature,
//                      String superName, String[] interfaces) {
//        super.visit(version, access, name, signature, superName, interfaces);
//        className = name;
//    }
//
//    @Override
//    public MethodVisitor visitMethod(int access, final String name,
//                                     String desc, String signature, String[] exceptions) {
//        String methodName = name;
//
//        return new MethodVisitor(Opcodes.ASM5) {
//
//            @Override
//            public void visitLdcInsn(Object cst) {
//                super.visitLdcInsn(cst);
//                if (cst instanceof String) {//常量节点
//                    String constant = (String) cst;
//                      GraphNode caller = new GraphNode();
//                        caller.putClass(className);
//                        caller.putMethod(methodName);
//                        caller.putConstant(constant);
//                        GraphNode called = new GraphNode();
//                        called.putClass(className);
//                        called.putMethod(methodName);
//                        GraphHolder.addNode(caller, called);
//                }
//            }
//
//            @Override
//            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
//                super.visitFieldInsn(opcode, owner, name, desc);//变量节点
//
//                GraphNode caller = new GraphNode();
//                caller.putClass(owner);
//                caller.putField(name);
//                GraphNode called = new GraphNode();
//                called.putClass(className);
//                called.putMethod(methodName);
//                GraphHolder.addNode(caller, called);
//
//            }
//
//            @Override
//            public void visitMethodInsn(int opcode, String owner, String name,
//                                        String desc, boolean itf) {//方法节点
//                super.visitMethodInsn(opcode, owner, name, desc, itf);
//                GraphNode caller = new GraphNode();
//                caller.putClass(className);
//                caller.putMethod(methodName);
//                GraphNode called = new GraphNode();
//                called.putClass(owner);
//                called.putMethod(name);
//                GraphHolder.addNode(caller, called);
//            }
//
//        };
//    }
//
//    @Override
//    public FieldVisitor visitField(int access, String name, String desc, String signature,
//                                   Object value) {
//        final String field = name;
//        if (value instanceof String) {//变量节点
//            String constant = (String) value;
//             GraphNode caller = new GraphNode();
//                caller.putClass(className);
//                caller.putField(field);
//                caller.putConstant(constant);
//                GraphNode called = new GraphNode();
//                called.putClass(className);
//                called.putField(field);
//                GraphHolder.addNode(caller, called);
//        }
//        return new FieldVisitor(Opcodes.ASM5) ;
//    }
//
//
//}
