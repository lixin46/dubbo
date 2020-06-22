/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.bytecode;

import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.ReflectUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;

/**
 * Wrapper.
 * 包装器类
 * 当前为抽象父类,所有子类运行时生成,每个子类对应一个接口
 * $sw+id
 *
 * 包装器的设计是为了提升性能,避免代理的反射调用,
 * 改为运行时生成if-else逻辑的方式,直接调用
 */
public abstract class Wrapper {
    /**
     * key为实际类型,value为对应的wrapper实例
     */
    private static final Map<Class<?>, Wrapper> WRAPPER_MAP = new ConcurrentHashMap<Class<?>, Wrapper>(); //class wrapper map
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final String[] OBJECT_METHODS = new String[]{"getClass", "hashCode", "toString", "equals"};
    private static final Wrapper OBJECT_WRAPPER = new Wrapper() {
        @Override
        public String[] getMethodNames() {
            return OBJECT_METHODS;
        }

        @Override
        public String[] getDeclaredMethodNames() {
            return OBJECT_METHODS;
        }

        @Override
        public String[] getPropertyNames() {
            return EMPTY_STRING_ARRAY;
        }

        @Override
        public Class<?> getPropertyType(String pn) {
            return null;
        }

        @Override
        public Object getPropertyValue(Object instance, String pn) throws NoSuchPropertyException {
            throw new NoSuchPropertyException("Property [" + pn + "] not found.");
        }

        @Override
        public void setPropertyValue(Object instance, String pn, Object pv) throws NoSuchPropertyException {
            throw new NoSuchPropertyException("Property [" + pn + "] not found.");
        }

        @Override
        public boolean hasProperty(String name) {
            return false;
        }

        @Override
        public Object invokeMethod(Object instance, String methodName, Class<?>[] types, Object[] args) throws NoSuchMethodException {
            if ("getClass".equals(methodName)) {
                return instance.getClass();
            }
            if ("hashCode".equals(methodName)) {
                return instance.hashCode();
            }
            if ("toString".equals(methodName)) {
                return instance.toString();
            }
            if ("equals".equals(methodName)) {
                if (args.length == 1) {
                    return instance.equals(args[0]);
                }
                throw new IllegalArgumentException("Invoke method [" + methodName + "] argument number error.");
            }
            throw new NoSuchMethodException("Method [" + methodName + "] not found.");
        }
    };
    private static AtomicLong WRAPPER_CLASS_COUNTER = new AtomicLong(0);

    /**
     * get wrapper.
     *
     * @param c Class instance.
     * @return Wrapper instance(not null).
     */
    public static Wrapper getWrapper(Class<?> c) {
        // 动态类,则获取直接父类
        while (ClassGenerator.isDynamicClass(c)) // can not wrapper on dynamic class.
        {
            c = c.getSuperclass();
        }
        // 一个特殊的wrapper
        if (c == Object.class) {
            return OBJECT_WRAPPER;
        }
        // 获取该类对应的wrapper,不存在则调用makeWrapper创建
        return WRAPPER_MAP.computeIfAbsent(c, key -> makeWrapper(key));
    }

    private static Wrapper makeWrapper(Class<?> c) {
        // 基本类型
        if (c.isPrimitive()) {
            throw new IllegalArgumentException("Can not create wrapper for primitive type: " + c);
        }
        // 全限定类名
        String name = c.getName();
        ClassLoader cl = ClassUtils.getClassLoader(c);

        StringBuilder c1 = new StringBuilder("public void setPropertyValue(Object o, String n, Object v){ ");
        StringBuilder c2 = new StringBuilder("public Object getPropertyValue(Object o, String n){ ");
        StringBuilder c3 = new StringBuilder("public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws " + InvocationTargetException.class.getName() + "{ ");

        // 定义这个类型的变量,$1给w赋值
        c1.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");
        c2.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");
        c3.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");

        Map<String, Class<?>> pts = new HashMap<>(); // <property name, property types>
        Map<String, Method> ms = new LinkedHashMap<>(); // <method desc, Method instance>
        List<String> mns = new ArrayList<>(); // method names.
        List<String> dmns = new ArrayList<>(); // declaring method names.

        // get all public field.
        // public字段
        for (Field f : c.getFields()) {
            String fieldName = f.getName();
            Class<?> fieldType = f.getType();
            // static或transient则跳过
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers())) {
                continue;
            }

            c1.append(" if( $2.equals(\"").append(fieldName).append("\") ){ w.").append(fieldName).append("=").append(arg(fieldType, "$3")).append("; return; }");
            c2.append(" if( $2.equals(\"").append(fieldName).append("\") ){ return ($w)w.").append(fieldName).append("; }");
            pts.put(fieldName, fieldType);
        }

        Method[] methods = c.getMethods();
        // get all public method.
        boolean hasMethod = hasMethods(methods);
        if (hasMethod) {
            c3.append(" try{");
            for (Method m : methods) {
                //ignore Object's method.
                if (m.getDeclaringClass() == Object.class) {
                    continue;
                }

                String mn = m.getName();
                c3.append(" if( \"").append(mn).append("\".equals( $2 ) ");
                int len = m.getParameterTypes().length;
                c3.append(" && ").append(" $3.length == ").append(len);

                boolean override = false;
                for (Method m2 : methods) {
                    if (m != m2 && m.getName().equals(m2.getName())) {
                        override = true;
                        break;
                    }
                }
                if (override) {
                    if (len > 0) {
                        for (int l = 0; l < len; l++) {
                            c3.append(" && ").append(" $3[").append(l).append("].getName().equals(\"")
                                    .append(m.getParameterTypes()[l].getName()).append("\")");
                        }
                    }
                }

                c3.append(" ) { ");

                if (m.getReturnType() == Void.TYPE) {
                    c3.append(" w.").append(mn).append('(').append(args(m.getParameterTypes(), "$4")).append(");").append(" return null;");
                } else {
                    c3.append(" return ($w)w.").append(mn).append('(').append(args(m.getParameterTypes(), "$4")).append(");");
                }

                c3.append(" }");

                mns.add(mn);
                if (m.getDeclaringClass() == c) {
                    dmns.add(mn);
                }
                ms.put(ReflectUtils.getDesc(m), m);
            }
            c3.append(" } catch(Throwable e) { ");
            c3.append("     throw new java.lang.reflect.InvocationTargetException(e); ");
            c3.append(" }");
        }

        c3.append(" throw new " + NoSuchMethodException.class.getName() + "(\"Not found method \\\"\"+$2+\"\\\" in class " + c.getName() + ".\"); }");

        // deal with get/set method.
        Matcher matcher;
        for (Map.Entry<String, Method> entry : ms.entrySet()) {
            String md = entry.getKey();
            Method method = entry.getValue();
            if ((matcher = ReflectUtils.GETTER_METHOD_DESC_PATTERN.matcher(md)).matches()) {
                String pn = propertyName(matcher.group(1));
                c2.append(" if( $2.equals(\"").append(pn).append("\") ){ return ($w)w.").append(method.getName()).append("(); }");
                pts.put(pn, method.getReturnType());
            } else if ((matcher = ReflectUtils.IS_HAS_CAN_METHOD_DESC_PATTERN.matcher(md)).matches()) {
                String pn = propertyName(matcher.group(1));
                c2.append(" if( $2.equals(\"").append(pn).append("\") ){ return ($w)w.").append(method.getName()).append("(); }");
                pts.put(pn, method.getReturnType());
            } else if ((matcher = ReflectUtils.SETTER_METHOD_DESC_PATTERN.matcher(md)).matches()) {
                Class<?> pt = method.getParameterTypes()[0];
                String pn = propertyName(matcher.group(1));
                c1.append(" if( $2.equals(\"").append(pn).append("\") ){ w.").append(method.getName()).append("(").append(arg(pt, "$3")).append("); return; }");
                pts.put(pn, pt);
            }
        }
        c1.append(" throw new " + NoSuchPropertyException.class.getName() + "(\"Not found property \\\"\"+$2+\"\\\" field or setter method in class " + c.getName() + ".\"); }");
        c2.append(" throw new " + NoSuchPropertyException.class.getName() + "(\"Not found property \\\"\"+$2+\"\\\" field or setter method in class " + c.getName() + ".\"); }");

        // make class
        long id = WRAPPER_CLASS_COUNTER.getAndIncrement();
        // 创建类生成器
        ClassGenerator classGenerator = ClassGenerator.newInstance(cl);
        classGenerator.setClassName((Modifier.isPublic(c.getModifiers()) ? Wrapper.class.getName() : c.getName() + "$sw") + id);
        classGenerator.setSuperClass(Wrapper.class);

        classGenerator.addDefaultConstructor();
        classGenerator.addField("public static String[] pns;"); // property name array.
        classGenerator.addField("public static " + Map.class.getName() + " pts;"); // property type map.
        classGenerator.addField("public static String[] mns;"); // all method name array.
        classGenerator.addField("public static String[] dmns;"); // declared method name array.
        for (int i = 0, len = ms.size(); i < len; i++) {
            classGenerator.addField("public static Class[] mts" + i + ";");
        }

        classGenerator.addMethod("public String[] getPropertyNames(){ return pns; }");
        classGenerator.addMethod("public boolean hasProperty(String n){ return pts.containsKey($1); }");
        classGenerator.addMethod("public Class getPropertyType(String n){ return (Class)pts.get($1); }");
        classGenerator.addMethod("public String[] getMethodNames(){ return mns; }");
        classGenerator.addMethod("public String[] getDeclaredMethodNames(){ return dmns; }");
        classGenerator.addMethod(c1.toString());
        classGenerator.addMethod(c2.toString());
        classGenerator.addMethod(c3.toString());

        try {
            // 加载获取Class对象
            Class<?> wc = classGenerator.toClass();
            // setup static field.
            wc.getField("pts").set(null, pts);
            wc.getField("pns").set(null, pts.keySet().toArray(new String[0]));
            wc.getField("mns").set(null, mns.toArray(new String[0]));
            wc.getField("dmns").set(null, dmns.toArray(new String[0]));
            int ix = 0;
            for (Method m : ms.values()) {
                wc.getField("mts" + ix++).set(null, m.getParameterTypes());
            }
            // 无参构造实例化
            return (Wrapper) wc.newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            classGenerator.release();
            ms.clear();
            mns.clear();
            dmns.clear();
        }
    }

    private static String arg(Class<?> cl, String name) {
        if (cl.isPrimitive()) {
            if (cl == Boolean.TYPE) {
                return "((Boolean)" + name + ").booleanValue()";
            }
            if (cl == Byte.TYPE) {
                return "((Byte)" + name + ").byteValue()";
            }
            if (cl == Character.TYPE) {
                return "((Character)" + name + ").charValue()";
            }
            if (cl == Double.TYPE) {
                return "((Number)" + name + ").doubleValue()";
            }
            if (cl == Float.TYPE) {
                return "((Number)" + name + ").floatValue()";
            }
            if (cl == Integer.TYPE) {
                return "((Number)" + name + ").intValue()";
            }
            if (cl == Long.TYPE) {
                return "((Number)" + name + ").longValue()";
            }
            if (cl == Short.TYPE) {
                return "((Number)" + name + ").shortValue()";
            }
            throw new RuntimeException("Unknown primitive type: " + cl.getName());
        }
        return "(" + ReflectUtils.getName(cl) + ")" + name;
    }

    private static String args(Class<?>[] cs, String name) {
        int len = cs.length;
        if (len == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(arg(cs[i], name + "[" + i + "]"));
        }
        return sb.toString();
    }

    private static String propertyName(String pn) {
        return pn.length() == 1 || Character.isLowerCase(pn.charAt(1)) ? Character.toLowerCase(pn.charAt(0)) + pn.substring(1) : pn;
    }

    private static boolean hasMethods(Method[] methods) {
        if (methods == null || methods.length == 0) {
            return false;
        }
        for (Method m : methods) {
            if (m.getDeclaringClass() != Object.class) {
                return true;
            }
        }
        return false;
    }

    /**
     * get property name array.
     *
     * @return property name array.
     */
    public abstract String[] getPropertyNames();

    /**
     * get property type.
     *
     * @param pn property name.
     * @return Property type or nul.
     */
    public abstract Class<?> getPropertyType(String pn);

    /**
     * has property.
     *
     * @param name property name.
     * @return has or has not.
     */
    public abstract boolean hasProperty(String name);

    /**
     * get property value.
     *
     * @param instance instance.
     * @param pn       property name.
     * @return value.
     */
    public abstract Object getPropertyValue(Object instance, String pn) throws NoSuchPropertyException, IllegalArgumentException;

    /**
     * set property value.
     *
     * @param instance instance.
     * @param pn       property name.
     * @param pv       property value.
     */
    public abstract void setPropertyValue(Object instance, String pn, Object pv) throws NoSuchPropertyException, IllegalArgumentException;

    /**
     * get property value.
     *
     * @param instance instance.
     * @param pns      property name array.
     * @return value array.
     */
    public Object[] getPropertyValues(Object instance, String[] pns) throws NoSuchPropertyException, IllegalArgumentException {
        Object[] ret = new Object[pns.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = getPropertyValue(instance, pns[i]);
        }
        return ret;
    }

    /**
     * set property value.
     *
     * @param instance instance.
     * @param pns      property name array.
     * @param pvs      property value array.
     */
    public void setPropertyValues(Object instance, String[] pns, Object[] pvs) throws NoSuchPropertyException, IllegalArgumentException {
        if (pns.length != pvs.length) {
            throw new IllegalArgumentException("pns.length != pvs.length");
        }

        for (int i = 0; i < pns.length; i++) {
            setPropertyValue(instance, pns[i], pvs[i]);
        }
    }

    /**
     * get method name array.
     *
     * @return method name array.
     */
    public abstract String[] getMethodNames();

    /**
     * get method name array.
     *
     * @return method name array.
     */
    public abstract String[] getDeclaredMethodNames();

    /**
     * has method.
     *
     * @param name method name.
     * @return has or has not.
     */
    public boolean hasMethod(String name) {
        for (String mn : getMethodNames()) {
            if (mn.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param instance   实例
     * @param methodName 方法名
     * @param types      形参类型
     * @param args       实参
     * @return
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     */
    abstract public Object invokeMethod(Object instance, String methodName, Class<?>[] types, Object[] args) throws NoSuchMethodException, InvocationTargetException;
}
