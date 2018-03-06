package com.ytjojo.lintjar.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;

/**
 * Created by Administrator on 2018/2/5 0005.
 */

public class DeleteFiled extends AbstractClassVisitor {
    String fieldName;
    boolean isPresent;
    public DeleteFiled(ClassVisitor delegate,String name) {
        super(delegate);
        fieldName = name;
    }
    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if (name.equals(this.fieldName)) {
            isPresent = true;
            return null;// 返回 null 即可删除此属性
        }
        return super.visitField(access, name, desc, signature, value);
    }
}
