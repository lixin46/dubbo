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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Code generator for Adaptive class
 * 自适应类代码生成器
 */
public class AdaptiveClassCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveClassCodeGenerator.class);

    private static final String CLASSNAME_INVOCATION = "org.apache.dubbo.rpc.Invocation";

    private static final String CODE_PACKAGE = "package %s;\n";

    private static final String CODE_IMPORTS = "import %s;\n";

    private static final String CODE_CLASS_DECLARATION = "public class %s$Adaptive implements %s {\n";

    private static final String CODE_METHOD_DECLARATION = "public %s %s(%s) %s {\n%s}\n";

    private static final String CODE_METHOD_ARGUMENT = "%s arg%d";

    private static final String CODE_METHOD_THROWS = "throws %s";

    private static final String CODE_UNSUPPORTED = "throw new UnsupportedOperationException(\"The method %s of interface %s is not adaptive method!\");\n";

    private static final String CODE_URL_NULL_CHECK = "if (arg%d == null) throw new IllegalArgumentException(\"url == null\");\n%s url = arg%d;\n";

    private static final String CODE_EXT_NAME_ASSIGNMENT = "String extName = %s;\n";

    private static final String CODE_EXT_NAME_NULL_CHECK = "if(extName == null) "
                    + "throw new IllegalStateException(\"Failed to get extension (%s) name from url (\" + url.toString() + \") use keys(%s)\");\n";

    private static final String CODE_INVOCATION_ARGUMENT_NULL_CHECK = "if (arg%d == null) throw new IllegalArgumentException(\"invocation == null\"); "
                    + "String methodName = arg%d.getMethodName();\n";


    private static final String CODE_EXTENSION_ASSIGNMENT = "%s extension = (%<s)%s.getExtensionLoader(%s.class).getExtension(extName);\n";

    private static final String CODE_EXTENSION_METHOD_INVOKE_ARGUMENT = "arg%d";

    /**
     * 扩展接口
     */
    private final Class<?> type;

    /**
     * 默认的扩展实现的名称
     */
    private String defaultExtName;

    /**
     * 唯一构造方法
     * @param type 扩展接口
     * @param defaultExtName 默认的扩展实现的名称
     */
    public AdaptiveClassCodeGenerator(Class<?> type, String defaultExtName) {
        this.type = type;
        this.defaultExtName = defaultExtName;
    }

    /**
     * generate and return class code
     * 生成类的源代码
     */
    public String generate() {
        // no need to generate adaptive class since there's no adaptive method found.
        // 不存在适配方法,则报错
        // 适配方法需要带有@Adaptive注解,且为public
        if (!hasAdaptiveMethod()) {
            throw new IllegalStateException("No adaptive method exist on extension " + type.getName() + ", refuse to create the adaptive class!");
        }

        StringBuilder code = new StringBuilder();
        // 包信息,与接口同包
        code.append(generatePackageInfo());
        // import ExtensionLoader;
        code.append(generateImports());
        // public class 接口名$Adaptive implements 接口名 {
        code.append(generateClassDeclaration());
        // public方法
        Method[] methods = type.getMethods();
        // 遍历public方法
        for (Method method : methods) {
            //
            code.append(generateMethod(method));
        }
        code.append("}");

        if (logger.isDebugEnabled()) {
            logger.debug(code.toString());
        }
        return code.toString();
    }

    /**
     * test if given type has at least one method annotated with <code>Adaptive</code>
     */
    private boolean hasAdaptiveMethod() {
        return Arrays.stream(type.getMethods()).anyMatch(m -> m.isAnnotationPresent(Adaptive.class));
    }



    /**
     * generate package info
     */
    private String generatePackageInfo() {
        return String.format(CODE_PACKAGE, type.getPackage().getName());
    }

    /**
     * generate imports
     */
    private String generateImports() {
        return String.format(CODE_IMPORTS, ExtensionLoader.class.getName());
    }

    /**
     * generate class declaration
     */
    private String generateClassDeclaration() {
        return String.format(CODE_CLASS_DECLARATION, type.getSimpleName(), type.getCanonicalName());
    }

    /**
     * generate method not annotated with Adaptive with throwing unsupported exception
     */
    private String generateUnsupported(Method method) {
        return String.format(CODE_UNSUPPORTED, method, type.getName());
    }

    /**
     * get index of parameter with type URL
     */
    private int getUrlTypeIndex(Method method) {
        int urlTypeIndex = -1;
        Class<?>[] pts = method.getParameterTypes();
        for (int i = 0; i < pts.length; ++i) {
            // 参数类型为URL
            if (pts[i].equals(URL.class)) {
                urlTypeIndex = i;
                break;
            }
        }
        return urlTypeIndex;
    }

    /**
     * generate method declaration
     * 如: public java.lang.String methodName(java.lang.String arg0) throws java.lang.Exception {...}
     */
    private String generateMethod(Method method) {
        // 全限定名
        String methodReturnType = method.getReturnType().getCanonicalName();
        // 方法名
        String methodName = method.getName();
        // 生成方法形参声明,
        String methodArgs = generateMethodArguments(method);
        // 生成异常声明
        String methodThrows = generateMethodThrows(method);
        // 生成方法的代码
        String methodContent = generateMethodContent(method);
        //
        return String.format(CODE_METHOD_DECLARATION, methodReturnType, methodName, methodArgs, methodThrows, methodContent);
    }

    /**
     * generate method arguments
     */
    private String generateMethodArguments(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length)
                        .mapToObj(i -> String.format(CODE_METHOD_ARGUMENT, pts[i].getCanonicalName(), i))
                        .collect(Collectors.joining(", "));
    }

    /**
     * generate method throws
     */
    private String generateMethodThrows(Method method) {
        Class<?>[] ets = method.getExceptionTypes();
        if (ets.length > 0) {
            String list = Arrays.stream(ets).map(Class::getCanonicalName).collect(Collectors.joining(", "));
            return String.format(CODE_METHOD_THROWS, list);
        } else {
            return "";
        }
    }

    /**
     * generate method URL argument null check
     */
    private String generateUrlNullCheck(int index) {
        return String.format(CODE_URL_NULL_CHECK, index, URL.class.getName(), index);
    }

    /**
     * generate method content
     * 方法体代码
     */
    private String generateMethodContent(Method method) {
        // 获取注解
        Adaptive adaptiveAnnotation = method.getAnnotation(Adaptive.class);
        StringBuilder code = new StringBuilder(512);
        // 不存在注解,则生成抛UnsupportedOperationException异常的代码
        if (adaptiveAnnotation == null) {
            return generateUnsupported(method);
        }
        // 存在注解
        else {
            // 查找第一个类型为URL的参数对应的索引
            int urlTypeIndex = getUrlTypeIndex(method);

            // found parameter in URL type
            // 存在URL类型参数
            if (urlTypeIndex != -1) {
                // Null Point check
                // 参数non-null校验和局部变量赋值
                code.append(generateUrlNullCheck(urlTypeIndex));
            }
            // 不存在
            else {
                // did not find parameter in URL type
                // 间接赋值,看看形参定义的类型中有没有返回值类型为URL的方法,
                // 可以通过调用方法获取url并赋值
                code.append(generateUrlAssignmentIndirectly(method));
            }

            // 获取注解的value属性值,默认为接口名
            String[] value = getMethodAdaptiveValue(adaptiveAnnotation);
            // 是否存在org.apache.dubbo.rpc.Invocation类型的形参
            boolean hasInvocation = hasInvocationArgument(method);
            // 对org.apache.dubbo.rpc.Invocation形参进行非空校验的代码
            code.append(generateInvocationArgumentNullCheck(method));
            // 扩展名赋值代码
            // String extName = url.getXXX
            code.append(generateExtNameAssignment(value, hasInvocation));
            // check extName == null?
            // extName的non-null校验
            code.append(generateExtNameNullCheck(value));
            // 扩展实例赋值代码
            code.append(generateExtensionAssignment());
            // return语句
            // return statement
            code.append(generateReturnAndInvocation(method));
        }

        return code.toString();
    }

    /**
     * generate code for variable extName null check
     */
    private String generateExtNameNullCheck(String[] value) {
        return String.format(CODE_EXT_NAME_NULL_CHECK, type.getName(), Arrays.toString(value));
    }

    /**
     * generate extName assigment code
     */
    private String generateExtNameAssignment(String[] value, boolean hasInvocation) {
        // TODO: refactor it
        String getNameCode = null;
        // 倒着遍历
        for (int i = value.length - 1; i >= 0; --i) {
            // 最后一个
            if (i == value.length - 1) {
                // 存在默认扩展名
                if (null != defaultExtName) {
                    // 非协议
                    if (!"protocol".equals(value[i])) {
                        // 存在Invocation参数
                        if (hasInvocation) {
                            getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        }
                        // 不存在
                        else {
                            getNameCode = String.format("url.getParameter(\"%s\", \"%s\")", value[i], defaultExtName);
                        }
                    }
                    // 协议
                    else {
                        getNameCode = String.format("( url.getProtocol() == null ? \"%s\" : url.getProtocol() )", defaultExtName);
                    }
                }
                // 不存在默认扩展名
                else {
                    // 非协议
                    if (!"protocol".equals(value[i])) {
                        if (hasInvocation) {
                            getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                        } else {
                            getNameCode = String.format("url.getParameter(\"%s\")", value[i]);
                        }
                    }
                    // 协议
                    else {
                        getNameCode = "url.getProtocol()";
                    }
                }
            }
            // 其他
            else {
                // 非协议
                if (!"protocol".equals(value[i])) {
                    if (hasInvocation) {
                        getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                    } else {
                        getNameCode = String.format("url.getParameter(\"%s\", %s)", value[i], getNameCode);
                    }
                }
                // 协议
                else {
                    getNameCode = String.format("url.getProtocol() == null ? (%s) : url.getProtocol()", getNameCode);
                }
            }
        }

        return String.format(CODE_EXT_NAME_ASSIGNMENT, getNameCode);
    }

    /**
     * @return
     */
    private String generateExtensionAssignment() {
        return String.format(CODE_EXTENSION_ASSIGNMENT, type.getName(), ExtensionLoader.class.getSimpleName(), type.getName());
    }

    /**
     * generate method invocation statement and return it if necessary
     */
    private String generateReturnAndInvocation(Method method) {
        String returnStatement = method.getReturnType().equals(void.class) ? "" : "return ";

        String args = IntStream.range(0, method.getParameters().length)
                .mapToObj(i -> String.format(CODE_EXTENSION_METHOD_INVOKE_ARGUMENT, i))
                .collect(Collectors.joining(", "));

        return returnStatement + String.format("extension.%s(%s);\n", method.getName(), args);
    }

    /**
     * test if method has argument of type <code>Invocation</code>
     */
    private boolean hasInvocationArgument(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return Arrays.stream(pts).anyMatch(p -> CLASSNAME_INVOCATION.equals(p.getName()));
    }

    /**
     * generate code to test argument of type <code>Invocation</code> is null
     */
    private String generateInvocationArgumentNullCheck(Method method) {
        Class<?>[] pts = method.getParameterTypes();
        return IntStream.range(0, pts.length)
                .filter(i -> CLASSNAME_INVOCATION.equals(pts[i].getName()))
                        .mapToObj(i -> String.format(CODE_INVOCATION_ARGUMENT_NULL_CHECK, i, i))
                        .findFirst().orElse("");
    }

    /**
     * get value of adaptive annotation or if empty return splitted simple name
     */
    private String[] getMethodAdaptiveValue(Adaptive adaptiveAnnotation) {
        String[] value = adaptiveAnnotation.value();
        // value is not set, use the value generated from class name as the key
        // value为空
        if (value.length == 0) {
            // 按照单词拆分(大写字母开头后跟小写为一个单词),用点分隔
            // 如:HellWorld转换为hello.world
            String splitName = StringUtils.camelToSplitName(type.getSimpleName(), ".");
            value = new String[]{splitName};
        }
        return value;
    }

    /**
     * get parameter with type <code>URL</code> from method parameter:
     * <p>
     * test if parameter has method which returns type <code>URL</code>
     * <p>
     * if not found, throws IllegalStateException
     */
    private String generateUrlAssignmentIndirectly(Method method) {
        // 形参类型
        Class<?>[] parameterTypes = method.getParameterTypes();

        // 参数类型中的方法名,参数索引
        Map<String, Integer> getterReturnUrl = new HashMap<>();
        // find URL getter method
        // 遍历
        for (int i = 0; i < parameterTypes.length; ++i) {
            // 参数类型中,定义的public方法
            for (Method m : parameterTypes[i].getMethods()) {
                String name = m.getName();
                // get开头或长度大于3,且public,且非static,且无参数,且返回值为URL
                if ((name.startsWith("get") || name.length() > 3)
                        && Modifier.isPublic(m.getModifiers())
                        && !Modifier.isStatic(m.getModifiers())
                        && m.getParameterTypes().length == 0
                        && m.getReturnType() == URL.class) {
                    getterReturnUrl.put(name, i);
                }
            }
        }

        // 所有参数中的方法都不返回URL,则报错
        if (getterReturnUrl.size() <= 0) {
            // getter method not found, throw
            throw new IllegalStateException("Failed to create adaptive class for interface " + type.getName()
                    + ": not found url parameter or url attribute in parameters of method " + method.getName());
        }

        // 先找getUrl方法
        Integer index = getterReturnUrl.get("getUrl");
        // 存在
        if (index != null) {
            // 通过方法调用代码获取url,局部变量持有
            return generateGetUrlNullCheck(index, parameterTypes[index], "getUrl");
        }
        // 不存在
        else {
            // 使用第一个
            Map.Entry<String, Integer> entry = getterReturnUrl.entrySet().iterator().next();
            return generateGetUrlNullCheck(entry.getValue(), parameterTypes[entry.getValue()], entry.getKey());
        }
    }

    /**
     * 1, test if argi is null
     * 2, test if argi.getXX() returns null
     * 3, assign url with argi.getXX()
     */
    private String generateGetUrlNullCheck(int index, Class<?> type, String method) {
        // Null point check
        StringBuilder code = new StringBuilder();
        code.append(String.format("if (arg%d == null) throw new IllegalArgumentException(\"%s argument == null\");\n",
                index, type.getName()));
        code.append(String.format("if (arg%d.%s() == null) throw new IllegalArgumentException(\"%s argument %s() == null\");\n",
                index, method, type.getName(), method));

        code.append(String.format("%s url = arg%d.%s();\n", URL.class.getName(), index, method));
        return code.toString();
    }

}
