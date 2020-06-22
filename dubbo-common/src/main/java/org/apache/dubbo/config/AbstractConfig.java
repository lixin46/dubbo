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
package org.apache.dubbo.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.CompositeConfiguration;
import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.MethodUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.AsyncMethodInfo;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.utils.ReflectUtils.findMethodByMethodSignature;

/**
 * Utility methods and public methods for parsing configuration
 * 配置的基类
 * <p>
 * 包含的配置项:id,prefix,valid
 *
 * @export
 */
public abstract class AbstractConfig implements Serializable {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractConfig.class);
    private static final long serialVersionUID = 4267533505537413570L;

    /**
     * The legacy properties container
     */
    private static final Map<String, String> LEGACY_PROPERTIES = new HashMap<String, String>();

    /**
     * The suffix container
     */
    private static final String[] SUFFIXES = new String[]{"Config", "Bean", "ConfigBase"};

    static {
        LEGACY_PROPERTIES.put("dubbo.protocol.name", "dubbo.service.protocol");
        LEGACY_PROPERTIES.put("dubbo.protocol.host", "dubbo.service.server.host");
        LEGACY_PROPERTIES.put("dubbo.protocol.port", "dubbo.service.server.port");
        LEGACY_PROPERTIES.put("dubbo.protocol.threads", "dubbo.service.max.thread.pool.size");
        LEGACY_PROPERTIES.put("dubbo.consumer.timeout", "dubbo.service.invoke.timeout");
        LEGACY_PROPERTIES.put("dubbo.consumer.retries", "dubbo.service.max.retry.providers");
        LEGACY_PROPERTIES.put("dubbo.consumer.check", "dubbo.service.allow.no.provider");
        LEGACY_PROPERTIES.put("dubbo.service.url", "dubbo.service.address");
    }

    private static String convertLegacyValue(String key, String value) {
        if (value != null && value.length() > 0) {
            if ("dubbo.service.max.retry.providers".equals(key)) {
                return String.valueOf(Integer.parseInt(value) - 1);
            } else if ("dubbo.service.allow.no.provider".equals(key)) {
                return String.valueOf(!Boolean.parseBoolean(value));
            }
        }
        return value;
    }

    public static String getTagName(Class<?> cls) {
        // 简单类名
        String tag = cls.getSimpleName();
        // 遍历后缀,包括:Config,Bean,ConfigBase
        for (String suffix : SUFFIXES) {
            // 截掉后缀
            if (tag.endsWith(suffix)) {
                tag = tag.substring(0, tag.length() - suffix.length());
                break;
            }
        }
        // 驼峰转
        return StringUtils.camelToSplitName(tag, "-");
    }

    /**
     * 把指定的配置对象中的配置,追加到指定的参数Map中
     * 没有key前缀
     *
     * @param parameters 保存参数的Map
     * @param config     指定的配置对象
     */
    public static void appendParameters(Map<String, String> parameters, Object config) {
        appendParameters(parameters, config, null);
    }

    /**
     * 把指定的配置对象中的配置,追加到指定的参数Map中
     * 参数名全部采用点分隔格式
     *
     * @param parameters 保存参数的Map
     * @param config     指定的配置对象
     * @param prefix     key的前缀
     */
    @SuppressWarnings("unchecked")
    public static void appendParameters(Map<String, String> parameters, Object config, String prefix) {
        // 配置对象为null,则无法追加
        if (config == null) {
            return;
        }
        // public方法
        Method[] methods = config.getClass().getMethods();
        // 遍历
        for (Method method : methods) {
            try {
                // 方法名
                String name = method.getName();
                // getter方法
                if (MethodUtils.isGetter(method)) {
                    Parameter parameter = method.getAnnotation(Parameter.class);
                    // 返回值为Object则跳过
                    // 存在注解且显示排除则跳过
                    if (method.getReturnType() == Object.class || parameter != null && parameter.excluded()) {
                        continue;
                    }
                    String key;
                    // 存在注解且显示指定了key.则使用
                    if (parameter != null && parameter.key().length() > 0) {
                        key = parameter.key();
                    }
                    // 不存在注解,或没有显示指定key,则计算
                    else {
                        // 方法名按单词切分,点分隔
                        key = calculatePropertyFromGetter(name);
                    }
                    // 调用方法获取值
                    Object value = method.invoke(config);
                    // 转字符串
                    String str = String.valueOf(value).trim();
                    // 值非null且非空
                    if (value != null && str.length() > 0) {
                        // 转义,url编码
                        if (parameter != null && parameter.escaped()) {
                            str = URL.encode(str);
                        }
                        // 追加
                        if (parameter != null && parameter.append()) {
                            String pre = parameters.get(key);
                            if (pre != null && pre.length() > 0) {
                                str = pre + "," + str;
                            }
                        }
                        // 指定了key的前缀,则追加,点分隔
                        if (prefix != null && prefix.length() > 0) {
                            key = prefix + "." + key;
                        }
                        // 保存参数
                        parameters.put(key, str);
                    }
                    // 值为null或为空
                    // 注解配置了require=true,则报错
                    else if (parameter != null && parameter.required()) {
                        throw new IllegalStateException(config.getClass().getSimpleName() + "." + key + " == null");
                    }
                }
                // getParameters()方法
                else if (isParametersGetter(method)) {
                    Map<String, String> map = (Map<String, String>) method.invoke(config, new Object[0]);
                    parameters.putAll(convert(map, prefix));
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }


    protected static AsyncMethodInfo convertMethodConfig2AsyncInfo(MethodConfig methodConfig) {
        if (methodConfig == null || (methodConfig.getOninvoke() == null && methodConfig.getOnreturn() == null && methodConfig.getOnthrow() == null)) {
            return null;
        }

        //check config conflict
        if (Boolean.FALSE.equals(methodConfig.isReturn()) && (methodConfig.getOnreturn() != null || methodConfig.getOnthrow() != null)) {
            throw new IllegalStateException("method config error : return attribute must be set true when onreturn or onthrow has been set.");
        }

        AsyncMethodInfo asyncMethodInfo = new AsyncMethodInfo();

        asyncMethodInfo.setOninvokeInstance(methodConfig.getOninvoke());
        asyncMethodInfo.setOnreturnInstance(methodConfig.getOnreturn());
        asyncMethodInfo.setOnthrowInstance(methodConfig.getOnthrow());

        try {
            String oninvokeMethod = methodConfig.getOninvokeMethod();
            if (StringUtils.isNotEmpty(oninvokeMethod)) {
                asyncMethodInfo.setOninvokeMethod(getMethodByName(methodConfig.getOninvoke().getClass(), oninvokeMethod));
            }

            String onreturnMethod = methodConfig.getOnreturnMethod();
            if (StringUtils.isNotEmpty(onreturnMethod)) {
                asyncMethodInfo.setOnreturnMethod(getMethodByName(methodConfig.getOnreturn().getClass(), onreturnMethod));
            }

            String onthrowMethod = methodConfig.getOnthrowMethod();
            if (StringUtils.isNotEmpty(onthrowMethod)) {
                asyncMethodInfo.setOnthrowMethod(getMethodByName(methodConfig.getOnthrow().getClass(), onthrowMethod));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

        return asyncMethodInfo;
    }

    private static Method getMethodByName(Class<?> clazz, String methodName) {
        try {
            return ReflectUtils.findMethodByMethodName(clazz, methodName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected static Set<String> getSubProperties(Map<String, String> properties, String prefix) {
        return properties.keySet().stream().filter(k -> k.contains(prefix)).map(k -> {
            k = k.substring(prefix.length());
            return k.substring(0, k.indexOf("."));
        }).collect(Collectors.toSet());
    }

    private static String extractPropertyName(Class<?> clazz, Method setter) throws Exception {
        // 属性名,驼峰
        String propertyName = setter.getName().substring("set".length());
        // getter方法
        Method getter = null;
        try {
            getter = clazz.getMethod("get" + propertyName);
        } catch (NoSuchMethodException e) {
            getter = clazz.getMethod("is" + propertyName);
        }
        Parameter parameter = getter.getAnnotation(Parameter.class);
        // 使用key作为属性名
        if (parameter != null && StringUtils.isNotEmpty(parameter.key()) && parameter.useKeyAsProperty()) {
            propertyName = parameter.key();
        }
        // 不存在配置,或为空,或不使用key作为属性名
        // 则默认驼峰名称
        else {
            propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
        }
        return propertyName;
    }

    private static String calculatePropertyFromGetter(String name) {
        int i = name.startsWith("get") ? 3 : 2;
        return StringUtils.camelToSplitName(name.substring(i, i + 1).toLowerCase() + name.substring(i + 1), ".");
    }

    private static String calculateAttributeFromGetter(String getter) {
        int i = getter.startsWith("get") ? 3 : 2;
        return getter.substring(i, i + 1).toLowerCase() + getter.substring(i + 1);
    }

    private static void invokeSetParameters(Class c, Object o, Map map) {
        try {
            Method method = findMethodByMethodSignature(c, "setParameters", new String[]{Map.class.getName()});
            if (method != null && isParametersSetter(method)) {
                method.invoke(o, map);
            }
        } catch (Throwable t) {
            // ignore
        }
    }

    private static Map<String, String> invokeGetParameters(Class c, Object o) {
        try {
            Method method = findMethodByMethodSignature(c, "getParameters", null);
            if (method != null && isParametersGetter(method)) {
                return (Map<String, String>) method.invoke(o);
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private static boolean isParametersGetter(Method method) {
        String name = method.getName();
        return ("getParameters".equals(name)
                && Modifier.isPublic(method.getModifiers())
                && method.getParameterTypes().length == 0
                && method.getReturnType() == Map.class);
    }

    private static boolean isParametersSetter(Method method) {
        return ("setParameters".equals(method.getName())
                && Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 1
                && Map.class == method.getParameterTypes()[0]
                && method.getReturnType() == void.class);
    }

    private static Map<String, String> convert(Map<String, String> parameters, String prefix) {
        if (parameters == null || parameters.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new HashMap<>();
        String pre = (prefix != null && prefix.length() > 0 ? prefix + "." : "");
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            result.put(pre + key, value);
            // For compatibility, key like "registry-type" will has a duplicate key "registry.type"
            if (key.contains("-")) {
                result.put(pre + key.replace('-', '.'), value);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------

    /**
     * The config id
     * 配置的id,唯一标识一个配置对象
     */
    protected String id;
    /**
     * 当前对象所有参数key的前缀,
     * 默认为dubbo.类名的中划线命名,
     * 如:ConfigCenterConfig为dubbo.config-center
     */
    protected String prefix;

    /**
     * 是否已刷新
     */
    protected final AtomicBoolean refreshed = new AtomicBoolean(false);


    /**
     * 当前对象把自己追加到配置管理器中
     * 当前方法将会被spring或javaEE容器自动调用,
     * 或者需要手动调用触发.
     *
     * @since 2.7.5
     */
    @PostConstruct
    public void addIntoConfigManager() {
        ConfigManager configManager = ApplicationModel.getConfigManager();
        // 把当前对象追加到单例的配置管理器中
        configManager.addConfig(this);
    }


    /**
     * 不作为参数导出
     *
     * @return id, 也是beanName
     */
    @Parameter(excluded = true)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * 不作为参数导出
     *
     * @return 参数前缀, 默认为dubbo.类名(spinal - case)
     */
    @Parameter(excluded = true)
    public String getPrefix() {
        return StringUtils.isNotEmpty(prefix) ? prefix : (CommonConstants.DUBBO + "." + getTagName(this.getClass()));
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    /**
     * 不作为参数导出
     *
     * @return 是否有效
     */
    @Parameter(excluded = true)
    public boolean isValid() {
        return true;
    }

    public void updateIdIfAbsent(String value) {
        if (StringUtils.isNotEmpty(value) && StringUtils.isEmpty(id)) {
            this.id = value;
        }
    }

    protected void appendAnnotation(Class<?> annotationClass, Object annotation) {
        Method[] methods = annotationClass.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() != Object.class
                    && method.getReturnType() != void.class
                    && method.getParameterTypes().length == 0
                    && Modifier.isPublic(method.getModifiers())
                    && !Modifier.isStatic(method.getModifiers())) {
                try {
                    String property = method.getName();
                    if ("interfaceClass".equals(property) || "interfaceName".equals(property)) {
                        property = "interface";
                    }
                    String setter = "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
                    Object value = method.invoke(annotation);
                    if (value != null && !value.equals(method.getDefaultValue())) {
                        Class<?> parameterType = ReflectUtils.getBoxedClass(method.getReturnType());
                        if ("filter".equals(property) || "listener".equals(property)) {
                            parameterType = String.class;
                            value = StringUtils.join((String[]) value, ",");
                        } else if ("parameters".equals(property)) {
                            parameterType = Map.class;
                            value = CollectionUtils.toStringMap((String[]) value);
                        }
                        try {
                            Method setterMethod = getClass().getMethod(setter, parameterType);
                            setterMethod.invoke(this, value);
                        } catch (NoSuchMethodException e) {
                            // ignore
                        }
                    }
                } catch (Throwable e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 应该在配置完全初始化之后被调用
     * 获取元数据
     * 注意:这个方法应该包含所有属性,@Parameter与追加的参数不同对待
     *
     * @return 元数据, 包含配置对象的所有参数
     */
    public Map<String, String> getMetaData() {
        Map<String, String> metaData = new HashMap<>();
        // public方法
        Method[] methods = this.getClass().getMethods();
        // 遍历方法
        for (Method method : methods) {
            try {
                // 方法名
                String name = method.getName();
                // getter方法
                if (MethodUtils.isMetaMethod(method)) {
                    String key;
                    // 获取方法上的注解
                    Parameter parameter = method.getAnnotation(Parameter.class);
                    // 显示指定了key
                    if (parameter != null && parameter.key().length() > 0 && parameter.useKeyAsProperty()) {
                        key = parameter.key();
                    }
                    // 计算key
                    else {
                        key = calculateAttributeFromGetter(name);
                    }
                    // treat url and configuration differently, the value should always present in configuration though it may not need to present in url.
                    //if (method.getReturnType() == Object.class || parameter != null && parameter.excluded()) {
                    if (method.getReturnType() == Object.class) {
                        metaData.put(key, null);
                        continue;
                    }

                    /**
                     * Attributes annotated as deprecated should not override newly added replacement.
                     */
                    if (MethodUtils.isDeprecated(method) && metaData.get(key) != null) {
                        continue;
                    }

                    // 获取值
                    Object value = method.invoke(this);
                    String str = String.valueOf(value).trim();
                    if (value != null && str.length() > 0) {
                        metaData.put(key, str);
                    } else {
                        metaData.put(key, null);
                    }
                }
                // public Map getParameters()方法
                else if (isParametersGetter(method)) {
                    Map<String, String> map = (Map<String, String>) method.invoke(this, new Object[0]);
                    metaData.putAll(convert(map, ""));
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        return metaData;
    }

    /**
     * 刷新
     * 就是根据当前实例的setter,从全局环境中获取Configuration对象,并注入属性
     */
    public void
    refresh() {
        // 获取环境
        Environment env = ApplicationModel.getEnvironment();
        try {
            // 组合配置
            // dubbo.config-center
            CompositeConfiguration compositeConfiguration = env.getPrefixedConfiguration(this);
            // loop methods, get override value and set the new value back to method
            // 方法
            Method[] methods = getClass().getMethods();
            // 遍历方法
            for (Method method : methods) {
                // 是setter
                if (MethodUtils.isSetter(method)) {
                    try {
                        // 属性名
                        String propertyName = extractPropertyName(getClass(), method);
                        // 获取配置值
                        String value = compositeConfiguration.getString(propertyName);
                        value = StringUtils.trim(value);
                        // isTypeMatch() is called to avoid duplicate and incorrect update, for example, we have two 'setGeneric' methods in ReferenceConfig.
                        // value非空,且与setter参数类型匹配
                        if (StringUtils.isNotEmpty(value) && ClassUtils.isTypeMatch(method.getParameterTypes()[0], value)) {
                            // 调用setter进行属性注入
                            method.invoke(this, ClassUtils.convertPrimitive(method.getParameterTypes()[0], value));
                        }
                    } catch (NoSuchMethodException e) {
                        logger.info("Failed to override the property " + method.getName() + " in " +
                                this.getClass().getSimpleName() +
                                ", please make sure every property has getter/setter method provided.");
                    }
                } else if (isParametersSetter(method)) {
                    String value = StringUtils.trim(compositeConfiguration.getString(extractPropertyName(getClass(), method)));
                    if (StringUtils.isNotEmpty(value)) {
                        Map<String, String> map = invokeGetParameters(getClass(), this);
                        map = map == null ? new HashMap<>() : map;
                        map.putAll(convert(StringUtils.parseParameters(value), ""));
                        invokeSetParameters(getClass(), this, map);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to override ", e);
        }
    }

    @Override
    public int hashCode() {
        int hashCode = 1;

        Method[] methods = this.getClass().getMethods();
        for (Method method : methods) {
            if (MethodUtils.isGetter(method)) {
                Parameter parameter = method.getAnnotation(Parameter.class);
                if (parameter != null && parameter.excluded()) {
                    continue;
                }
                try {
                    Object value = method.invoke(this, new Object[]{});
                    hashCode = 31 * hashCode + value.hashCode();
                } catch (Exception ignored) {
                    //ignored
                }
            }
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj.getClass().getName().equals(this.getClass().getName()))) {
            return false;
        }

        Method[] methods = this.getClass().getMethods();
        for (Method method1 : methods) {
            if (MethodUtils.isGetter(method1)) {
                Parameter parameter = method1.getAnnotation(Parameter.class);
                if (parameter != null && parameter.excluded()) {
                    continue;
                }
                try {
                    Method method2 = obj.getClass().getMethod(method1.getName(), method1.getParameterTypes());
                    Object value1 = method1.invoke(this, new Object[]{});
                    Object value2 = method2.invoke(obj, new Object[]{});
                    if (!Objects.equals(value1, value2)) {
                        return false;
                    }
                } catch (Exception e) {
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("<dubbo:");
            buf.append(getTagName(getClass()));
            Method[] methods = getClass().getMethods();
            for (Method method : methods) {
                try {
                    if (MethodUtils.isGetter(method)) {
                        String name = method.getName();
                        String key = calculateAttributeFromGetter(name);

                        try {
                            getClass().getDeclaredField(key);
                        } catch (NoSuchFieldException e) {
                            // ignore
                            continue;
                        }

                        Object value = method.invoke(this);
                        if (value != null) {
                            buf.append(" ");
                            buf.append(key);
                            buf.append("=\"");
                            buf.append(value);
                            buf.append("\"");
                        }
                    }
                } catch (Exception e) {
                    logger.warn(e.getMessage(), e);
                }
            }
            buf.append(" />");
            return buf.toString();
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
            return super.toString();
        }
    }
}
