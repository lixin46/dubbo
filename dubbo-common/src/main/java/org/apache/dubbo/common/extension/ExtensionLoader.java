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
import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.support.ActivateComparator;
import org.apache.dubbo.common.lang.Prioritized;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static java.util.ServiceLoader.load;
import static java.util.stream.StreamSupport.stream;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOVE_VALUE_PREFIX;

/**
 * 负责加载SPI实例,作用类似于JDK的ServiceLoader
 */

/**
 * 扩展实例加载器
 * 负责扩展接口对应配置文件的查找,解析,实现类加载.
 * <p>
 * 适配机制,包装器机制,自动注入机制.
 * <p>
 * dubbo框架定义了3个关键的注解:
 * 1.SPI注解,标记在接口上,表名该接口为扩展接口.框架会为每个扩展接口生成适配器类并创建实例,
 * 用于在扩展接口方法调用时,根据入参,映射到不同的扩展实现实例.
 * 2.Adaptive注解,用于指示扩展适配器类的映射行为,默认抛异常
 * 3.Activate注解,
 *
 * @param <T>
 */
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    /**
     * 缓存
     * key为组件接口类
     * value为对应的扩展加载器
     */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>(64);
    /**
     * 扩展实际类型,value为类型对应的实例
     */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>(64);

    /**
     * 加载加载策略,基于ServiceLoader加载,包含3个实现:
     * 1.ServicesLoadingStrategy: 定位 "META-INF/services/"目录
     * 2.DubboLoadingStrategy: 定位 "META-INF/dubbo/"目录
     * 3.DubboInternalLoadingStrategy: 定位 "META-INF/dubbo/internal/"目录
     */
    private static volatile LoadingStrategy[] strategies = loadLoadingStrategies();

    /**
     * Load all {@link Prioritized prioritized} {@link LoadingStrategy Loading Strategies} via {@link ServiceLoader}
     *
     * @return non-null
     * @since 2.7.7
     */
    private static LoadingStrategy[] loadLoadingStrategies() {
        // ServiceLoader加载,从META-INF/services/{interfaceName}文件中读取实现类
        ServiceLoader<LoadingStrategy> loader = load(LoadingStrategy.class);
        Spliterator<LoadingStrategy> spliterator = loader.spliterator();
        // 无参构造实例化
        return stream(spliterator, false)
                .sorted()
                .toArray(LoadingStrategy[]::new);
    }

    public static void setLoadingStrategies(LoadingStrategy... strategies) {
        if (ArrayUtils.isNotEmpty(strategies)) {
            ExtensionLoader.strategies = strategies;
        }
    }

    /**
     * Get all {@link LoadingStrategy Loading Strategies}
     *
     * @return non-null
     * @see LoadingStrategy
     * @see Prioritized
     * @since 2.7.7
     */
    public static List<LoadingStrategy> getLoadingStrategies() {
        return asList(strategies);
    }

    /**
     * @param type 指定的类型
     * @param <T>
     * @return 指定类型上是否带有SPI扩展注解
     */
    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    /**
     * 获取指定接口类型的SPI组件
     *
     * @param type 指定的SPI接口类型
     * @param <T>  组件类型
     * @return 组件对应的扩展加载器
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        // 接口类型为null,报错
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        // 非接口,报错
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        // 类上没有@SPI注解,则报错SPI
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type +
                    ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }

        // 查加载器缓存
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        // 不存在
        if (loader == null) {
            // 构造方法实例化,并添加到缓存中
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            // 再次从缓存获取
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    // For testing purposes only
    public static void resetExtensionLoader(Class type) {
        ExtensionLoader loader = EXTENSION_LOADERS.get(type);
        if (loader != null) {
            // Remove all instances associated with this loader as well
            Map<String, Class<?>> classes = loader.getExtensionClasses();
            for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
                EXTENSION_INSTANCES.remove(entry.getValue());
            }
            classes.clear();
            EXTENSION_LOADERS.remove(type);
        }
    }

    public static void destroyAll() {
        EXTENSION_INSTANCES.forEach((_type, instance) -> {
            if (instance instanceof Lifecycle) {
                Lifecycle lifecycle = (Lifecycle) instance;
                try {
                    lifecycle.destroy();
                } catch (Exception e) {
                    logger.error("Error destroying extension " + lifecycle, e);
                }
            }
        });
    }

    private static ClassLoader findClassLoader() {
        return ClassUtils.getClassLoader(ExtensionLoader.class);
    }


    // -----------------------------------------------------------------------------------------------------------------
    // 实例
    /**
     * 对象工厂(自身本身也是基于扩展组件加载)
     * 用于在依赖注入时,获取对象
     */
    private final ExtensionFactory extensionFactory;

    /**
     * 接口
     */
    private final Class<?> type;
    /**
     * 缓存的接口下的所有名称到实现类的映射
     * key为扩展组件名称,value为扩展组件实现类
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();
    /**
     * 接口的适配器类
     */
    private volatile Class<?> cachedAdaptiveClass = null;
    /**
     * 接口的所有包装类集合
     * 包装器,需要以当前加载器加载的接口为构造函数参数
     * 获取实例时,需要用包装器进行逐一代理
     * <p>
     * 包装器无法保证顺序,
     * 而且针对所有的接口扩展实例进行包装,无法定制.
     */
    private Set<Class<?>> cachedWrapperClasses;



    /**
     * 默认的组件名称,从@SPI注解的value属性获取
     */
    private String cachedDefaultName;

    /**
     * key为扩展实现类,value为该组件对应的名称
     */
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();


    /**
     * 缓存的实例
     * key为扩展组件名称,value为扩展组件实现类
     */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();
    /**
     * 激活的扩展实现映射
     * key为扩展组件名称,value为扩展实现类上的@Activate注解
     */
    private final Map<String, Activate> cachedActivates = new ConcurrentHashMap<>();
    /**
     * 加载resource出的错
     */
    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();

    /**
     * 接口适配器类的实例
     */
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();
    /**
     * 创建自适应实例产生的错误
     */
    private volatile Throwable createAdaptiveInstanceError;



    /**
     * 构造方法
     *
     * @param type 指定的扩展实例接口类型
     */
    private ExtensionLoader(Class<?> type) {
        this.type = type;
        // 扩展工厂对应的加载器,不需要扩展工厂(因为它不需要依赖注入)
        if (type == ExtensionFactory.class) {
            extensionFactory = null;
        }
        // 其他类型的加载器
        else {
            // 获取扩展工厂,对应的加载器
            // 扩展工厂本身也是SPI组件
            ExtensionLoader<ExtensionFactory> extensionLoader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);
            // 获取自适应扩展实例
            extensionFactory = extensionLoader.getAdaptiveExtension();
        }
    }

    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }

    public String getExtensionName(Class<?> extensionClass) {
        getExtensionClasses();// load class
        return cachedNames.get(extensionClass);
    }

    /**
     * 重载
     * 获取激活的扩展实例
     *
     * @param url 信息
     * @param key 键
     * @return
     */
    public List<T> getActivateExtension(URL url, String key) {
        return getActivateExtension(url, key, null);
    }

    /**
     * 重载
     * 获取激活的扩展实例
     *
     * @param url    配置
     * @param names 组件名称
     * @return
     */
    public List<T> getActivateExtension(URL url, String[] names) {
        return getActivateExtension(url, names, null);
    }

    /**
     * 重载
     * 获取激活的实例
     *
     * @param url
     * @param key
     * @param group 指定的分组
     * @return
     */
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        // 逗号拆分
        String[] names = StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value);
        return getActivateExtension(url, names, group);
    }

    /**
     * 获取激活的实例
     * @param url 信息,用于与@Activate注解value值进行比对
     * @param nameArr 组件名称列表,如果名称以'-'开头,则代表不包含该组件
     * @param group  指定的激活扩展分组,组匹配才会被选中
     * @return
     */
    public List<T> getActivateExtension(URL url, String[] nameArr, String group) {
        List<T> activateExtensions = new ArrayList<>();

        // 名称列表
        List<String> names = nameArr == null ? new ArrayList<>(0) : asList(nameArr);
        // 不包含"-default"
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            // 加载所有的扩展类,确保加载
            getExtensionClasses();
            // 遍历缓存的激活对象
            for (Map.Entry<String, Activate> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();
                // @Activate注解
                Activate activate = entry.getValue();

                // 组件激活组
                String[] activateGroup = activate.group();
                // 激活条件,需要与url进行对比
                String[] activateValue = activate.value();
                // 组一致,且指定名称没有排除该组件,且包含该组件
                //
                if (isMatchGroup(group, activateGroup)
                        && !names.contains(name)
                        && !names.contains(REMOVE_VALUE_PREFIX + name)
                        && isActive(activateValue, url)) {
                    // 获取扩展实例
                    T extension = getExtension(name);
                    // 添加到列表
                    activateExtensions.add(extension);
                }
            }
            // 排序
            activateExtensions.sort(ActivateComparator.COMPARATOR);
        }

        List<T> loadedExtensions = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            // 不以'-'开头,且不包含"-{name}"
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                // 名称为default
                if (DEFAULT_KEY.equals(name)) {
                    // 非空
                    if (!loadedExtensions.isEmpty()) {
                        activateExtensions.addAll(0, loadedExtensions);
                        loadedExtensions.clear();
                    }
                }
                // 放到加载组件中
                else {
                    loadedExtensions.add(getExtension(name));
                }
            }
        }
        // 加载的扩展非空,则追加到激活扩展实例中
        if (!loadedExtensions.isEmpty()) {
            activateExtensions.addAll(loadedExtensions);
        }
        //
        return activateExtensions;
    }

    private boolean isMatchGroup(String group, String[] groups) {
        if (StringUtils.isEmpty(group)) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActive(String[] keys, URL url) {
        if (keys.length == 0) {
            return true;
        }
        for (String key : keys) {
            // @Active(value="key1:value1, key2:value2")
            String keyValue = null;
            // 包含冒号
            if (key.contains(":")) {
                String[] arr = key.split(":");
                key = arr[0];
                keyValue = arr[1];
            }

            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                // key相等且value相等
                if ((k.equals(key) || k.endsWith("." + key))
                        && ((keyValue != null && keyValue.equals(v)) || (keyValue == null && ConfigUtils.isNotEmpty(v)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get extension's instance. Return <code>null</code> if extension is not found or is not initialized. Pls. note
     * that this method will not trigger extension load.
     * <p>
     * In order to trigger extension load, call {@link #getExtension(String)} instead.
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Holder<Object> holder = getOrCreateHolder(name);
        return (T) holder.get();
    }

    private Holder<Object> getOrCreateHolder(String name) {
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    /**
     * @return 当前已经加载的扩展组件的名称
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<>(cachedInstances.keySet()));
    }

    /**
     * @return 当前已经加载的扩展组件的实例
     */
    public List<T> getLoadedExtensionInstances() {
        List<T> instances = new ArrayList<>();
        cachedInstances.values().forEach(holder -> instances.add((T) holder.get()));
        return instances;
    }

    public Object getLoadedAdaptiveExtensionInstances() {
        return cachedAdaptiveInstance.get();
    }


    /**
     * 查找指定名称对应的扩展实例.
     * 如果给定的名称没有找到,则报IllegalStateException异常
     *
     * @param name 指定的名称
     * @return 扩展实例
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        // 名称为true,则获取默认扩展
        if ("true".equals(name)) {
            return getDefaultExtension();
        }
        // 获取或创建一个持有者,Holder持有的对象可能为null
        final Holder<Object> holder = getOrCreateHolder(name);
        // 持有者持有的实例
        Object instance = holder.get();
        // 实例不存在
        if (instance == null) {
            synchronized (holder) {
                // 加锁读
                instance = holder.get();
                // 不存在,则创建扩展实例
                if (instance == null) {
                    // 创建扩展实例
                    instance = createExtension(name);
                    // 持有者持有
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * 获取指定名称的扩展实例,如果不存在则返回默认扩展实例
     *
     * @param name 指定的名称
     * @return
     */
    public T getOrDefaultExtension(String name) {
        return containsExtension(name) ? getExtension(name) : getDefaultExtension();
    }

    /**
     * 获取默认的扩展实例
     *
     * @return 默认的扩展实例
     */
    public T getDefaultExtension() {
        // 加载缓存所有扩展类
        getExtensionClasses();
        // 默认名称为空或为true,则返回null
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    public boolean hasExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Class<?> c = this.getExtensionClass(name);
        return c != null;
    }

    public Set<String> getSupportedExtensions() {
        Map<String, Class<?>> clazzes = getExtensionClasses();
        return Collections.unmodifiableSet(new TreeSet<>(clazzes.keySet()));
    }

    public Set<T> getSupportedExtensionInstances() {
        List<T> instances = new LinkedList<>();
        Set<String> supportedExtensions = getSupportedExtensions();
        if (CollectionUtils.isNotEmpty(supportedExtensions)) {
            for (String name : supportedExtensions) {
                instances.add(getExtension(name));
            }
        }
        // sort the Prioritized instances
        sort(instances, Prioritized.COMPARATOR);
        return new LinkedHashSet<>(instances);
    }

    /**
     * Return default extension name, return <code>null</code> if not configured.
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * Register new extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement the Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already exists (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
            if (cachedAdaptiveClass != null) {
                throw new IllegalStateException("Adaptive Extension already exists (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
        }
    }


    /**
     * 获取适配器实例
     *
     * @return 适配器实例
     */
    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        // 查缓存
        Object adapter = cachedAdaptiveInstance.get();
        // 不存在
        if (adapter == null) {
            // 存在创建错误,报错
            if (createAdaptiveInstanceError != null) {
                throw new IllegalStateException("Failed to create adaptive instance: " +
                        createAdaptiveInstanceError.toString(),
                        createAdaptiveInstanceError);
            }

            synchronized (cachedAdaptiveInstance) {
                // 加锁读
                adapter = cachedAdaptiveInstance.get();
                // 还是不存在
                if (adapter == null) {
                    try {
                        // 创建自适应扩展组件实例
                        adapter = createAdaptiveExtension();
                        // 加缓存
                        cachedAdaptiveInstance.set(adapter);
                    } catch (Throwable t) {
                        createAdaptiveInstanceError = t;
                        throw new IllegalStateException("Failed to create adaptive instance: " + t.toString(), t);
                    }
                }
            }
        }

        return (T) adapter;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    /**
     * 创建扩展实例
     *
     * @param name 指定的扩展名称
     * @return 包装和依赖注入好的扩展实例
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        //
        Map<String, Class<?>> extensionClasses = getExtensionClasses();
        // 获取名称对应的实现类
        Class<?> clazz = extensionClasses.get(name);
        // 不存在,则查找存在的异常抛出
        if (clazz == null) {
            throw findException(name);
        }
        try {
            // 先查找类型对应的实例
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            // 不存在
            if (instance == null) {
                // 无参构造实例化,保存映射
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                // 获取
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            // 注入依赖
            injectExtension(instance);

            // 包装器类
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            // 非空
            if (CollectionUtils.isNotEmpty(wrapperClasses)) {
                // 遍历包装器类,这里没有顺序
                for (Class<?> wrapperClass : wrapperClasses) {
                    // 实例化包装器
                    T wrapper = (T) wrapperClass.getConstructor(type).newInstance(instance);
                    // 依赖注入后,持有包装器
                    instance = injectExtension(wrapper);
                }
            }
            // 触发生命周期接口的初始化
            initExtension(instance);
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                    type + ") couldn't be instantiated: " + t.getMessage(), t);
        }
    }

    private boolean containsExtension(String name) {
        return getExtensionClasses().containsKey(name);
    }

    private T injectExtension(T instance) {

        if (extensionFactory == null) {
            return instance;
        }

        try {
            // public方法
            for (Method method : instance.getClass().getMethods()) {
                // 非setter跳过
                if (!isSetter(method)) {
                    continue;
                }
                // 带有@DisableInject注解,跳过注入
                if (method.getAnnotation(DisableInject.class) != null) {
                    continue;
                }
                Class<?> propertyType = method.getParameterTypes()[0];
                // 基本类型跳过
                if (ReflectUtils.isPrimitives(propertyType)) {
                    continue;
                }

                try {
                    // 获取属性名
                    String property = getSetterProperty(method);
                    // 调用对象工厂,获取扩展实例
                    // 类型,属性名
                    // 通过接口类型加载实现映射,通过名称查找实现实例
                    Object object = extensionFactory.getExtension(propertyType, property);
                    // 非null,则调用setter注入
                    if (object != null) {
                        method.invoke(instance, object);
                    }
                } catch (Exception e) {
                    logger.error("Failed to inject via method " + method.getName()
                            + " of interface " + type.getName() + ": " + e.getMessage(), e);
                }

            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }

    private void initExtension(T instance) {
        if (instance instanceof Lifecycle) {
            Lifecycle lifecycle = (Lifecycle) instance;
            lifecycle.initialize();
        }
    }

    /**
     * get properties name for setter, for instance: setVersion, return "version"
     * <p>
     * return "", if setter name with length less than 3
     */
    private String getSetterProperty(Method method) {
        return method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
    }

    /**
     * return true if and only if:
     * <p>
     * 1, public
     * <p>
     * 2, name starts with "set"
     * <p>
     * 3, only has one parameter
     */
    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterTypes().length == 1
                && Modifier.isPublic(method.getModifiers());
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return getExtensionClasses().get(name);
    }

    /**
     * 从不同的目录加载指定的接口文件
     *
     * @return
     */
    private Map<String, Class<?>> getExtensionClasses() {
        //
        Map<String, Class<?>> classes = cachedClasses.get();
        // 不存在缓存
        if (classes == null) {
            synchronized (cachedClasses) {
                // 加锁读
                classes = cachedClasses.get();
                // 不存在
                if (classes == null) {
                    // 延迟加载
                    // 扫描classpath下所有可加载的properties文件,解析并加载类
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * synchronized in getExtensionClasses
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        // 读取@SPI注解的value属性,作为默认名称
        cacheDefaultExtensionName();

        Map<String, Class<?>> extensionClasses = new HashMap<>();
        // 遍历加载策略
        for (LoadingStrategy strategy : strategies) {
            // 加载新的实现,apache包
            loadDirectory(
                    extensionClasses,
                    strategy.directory(),
                    type.getName(),
                    strategy.preferExtensionClassLoader(),
                    strategy.overridden(),
                    strategy.excludedPackages()
            );
            // 加载老的实现,alibaba包
            loadDirectory(
                    extensionClasses,
                    strategy.directory(),
                    type.getName().replace("org.apache", "com.alibaba"),
                    strategy.preferExtensionClassLoader(),
                    strategy.overridden(),
                    strategy.excludedPackages()
            );
        }

        return extensionClasses;
    }

    /**
     * 缓存默认扩展名称,写操作
     */
    private void cacheDefaultExtensionName() {
        // 接口上的注解
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        // 为null 则不存在默认扩展实现
        if (defaultAnnotation == null) {
            return;
        }
        String value = defaultAnnotation.value();
        if ((value = value.trim()).length() > 0) {
            // 逗号拆分
            String[] names = NAME_SEPARATOR.split(value);
            // 名称大于1,报错
            if (names.length > 1) {
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            }
            // 只有一个,则作为默认名称
            if (names.length == 1) {
                cachedDefaultName = names[0];
            }
        }
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        loadDirectory(extensionClasses, dir, type, false, false);
    }

    /**
     * 加载目录下所有资源文件
     *
     * @param extensionClasses                用于保存
     * @param dir                             目录???
     * @param type                            接口名
     * @param extensionLoaderClassLoaderFirst 是否首先尝试使用ExtensionLoader的加载器加载
     * @param overridden                      ???
     * @param excludedPackages                ???
     */
    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type,
                               boolean extensionLoaderClassLoaderFirst, boolean overridden, String... excludedPackages) {
        // 目录+类型
        String fileName = dir + type;
        try {
            Enumeration<java.net.URL> urls = null;
            // 查找类加载器
            // 使用加载ExtensionLoader的加载器
            ClassLoader classLoader = findClassLoader();

            // try to load from ExtensionLoader's ClassLoader first
            //
            if (extensionLoaderClassLoaderFirst) {
                // 加载器
                ClassLoader extensionLoaderClassLoader = ExtensionLoader.class.getClassLoader();
                // 与系统加载器不一致
                if (ClassLoader.getSystemClassLoader() != extensionLoaderClassLoader) {
                    urls = extensionLoaderClassLoader.getResources(fileName);
                }
            }

            // 列表为空
            if (urls == null || !urls.hasMoreElements()) {
                if (classLoader != null) {
                    urls = classLoader.getResources(fileName);
                } else {
                    urls = ClassLoader.getSystemResources(fileName);
                }
            }

            // 列表非空
            if (urls != null) {
                // 遍历
                while (urls.hasMoreElements()) {
                    // 当前url
                    java.net.URL resourceURL = urls.nextElement();
                    // 加载资源文件内容
                    // 资源文件中保存的都是扩展组件名称到实现类的映射,以name=className格式存在
                    loadResource(extensionClasses, classLoader, resourceURL, overridden, excludedPackages);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    /**
     * 加载单个资源文件
     * @param extensionClasses 扩展实现类映射
     * @param classLoader 类加载器
     * @param resourceURL 资源文件位置
     * @param overridden 重名时是否允许覆盖
     * @param excludedPackages 不包含的包
     */
    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader,
                              java.net.URL resourceURL, boolean overridden, String... excludedPackages) {
        try {
            // 读取文件流
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                // 读取行
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');
                    // 存在井号,则忽略后面的内容
                    if (ci >= 0) {
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    // 非空
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');
                            // 存在等号,则提取name
                            if (i > 0) {
                                // 扩展实例名称
                                name = line.substring(0, i).trim();
                                // 剩余部分为实现类名
                                line = line.substring(i + 1).trim();
                            }
                            // 未被排除
                            if (line.length() > 0 && !isExcluded(line, excludedPackages)) {
                                Class<?> impl = Class.forName(line, true, classLoader);
                                // 加载类
                                loadClass(extensionClasses, resourceURL, impl, name, overridden);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    private boolean isExcluded(String className, String... excludedPackages) {
        if (excludedPackages != null) {
            for (String excludePackage : excludedPackages) {
                if (className.startsWith(excludePackage + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 加载资源文件中的单个类
     * @param extensionClasses 用于保存扩展类
     * @param resourceURL      资源properties文件路径
     * @param clazz            加载到的实现类
     * @param name             扩展实例名称
     * @param overridden       当存在重名类时,是否可以覆盖,false时报错
     * @throws NoSuchMethodException
     */
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name,
                           boolean overridden) throws NoSuchMethodException {
        // 实现类没有实现当前加载器加载的接口,则报错
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + " is not subtype of interface.");
        }
        // 类上存在@Adaptive注解,则缓存自适应类
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            // 缓存适配器类,说明当前类需要生成适配器代理子类
            // 一般接口会标记该注解
            cacheAdaptiveClass(clazz, overridden);
        }
        // 是包装类
        // 存在以当前接口为参数的构造方法
        else if (isWrapperClass(clazz)) {
            //
            cacheWrapperClass(clazz);
        }
        // 其他
        else {
            // 存在无参构造方法
            clazz.getConstructor();
            // 名称为空
            if (StringUtils.isEmpty(name)) {
                // 相对于接口名,取剩余部分
                name = findAnnotationName(clazz);
                if (name.length() == 0) {
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                }
            }
            // 逗号拆分
            String[] names = NAME_SEPARATOR.split(name);
            // 名称非空
            if (ArrayUtils.isNotEmpty(names)) {
                // 缓存激活的类
                cacheActivateClass(clazz, names[0]);
                //
                for (String n : names) {
                    // 缓存名称映射
                    cacheName(clazz, n);
                    //
                    saveInExtensionClass(extensionClasses, clazz, n, overridden);
                }
            }
        }
    }

    /**
     * cache name
     */
    private void cacheName(Class<?> clazz, String name) {
        if (!cachedNames.containsKey(clazz)) {
            cachedNames.put(clazz, name);
        }
    }

    /**
     * put clazz in extensionClasses
     */
    private void saveInExtensionClass(Map<String, Class<?>> extensionClasses, Class<?> clazz, String name, boolean overridden) {
        Class<?> c = extensionClasses.get(name);
        // 不存在或需要重写
        if (c == null || overridden) {
            extensionClasses.put(name, clazz);
        }
        // 存在,且不一致,则报错,说明出现重名组件了
        else if (c != clazz) {
            String duplicateMsg = "Duplicate extension " + type.getName() + " name " + name + " on " + c.getName() + " and " + clazz.getName();
            logger.error(duplicateMsg);
            throw new IllegalStateException(duplicateMsg);
        }
    }

    /**
     * cache Activate class which is annotated with <code>Activate</code>
     * <p>
     * for compatibility, also cache class with old alibaba Activate annotation
     */
    private void cacheActivateClass(Class<?> clazz, String name) {
        // 读取类上的注解
        Activate activate = clazz.getAnnotation(Activate.class);
        // 存在注解
        if (activate != null) {
            cachedActivates.put(name, activate);
        }
    }

    /**
     * cache Adaptive class which is annotated with <code>Adaptive</code>
     */
    private void cacheAdaptiveClass(Class<?> clazz, boolean overridden) {
        // 还不存在适配器类,或者需要覆盖,则强制赋值
        if (cachedAdaptiveClass == null || overridden) {
            cachedAdaptiveClass = clazz;
        }
        // 已经存在适配器类,且不允许覆盖
        // 如果两个类不一致,则报错,一致则幂等返回
        else if (!cachedAdaptiveClass.equals(clazz)) {
            throw new IllegalStateException("More than 1 adaptive class found: "
                    + cachedAdaptiveClass.getName()
                    + ", " + clazz.getName());
        }
    }

    /**
     * cache wrapper class
     * <p>
     * like: ProtocolFilterWrapper, ProtocolListenerWrapper
     */
    private void cacheWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    /**
     * test if clazz is a wrapper class
     * <p>
     * which has Constructor with given class type as its only argument
     */
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> impl) {
        // 简单类名
        String implName = impl.getSimpleName();
        if (implName.endsWith(type.getSimpleName())) {
            implName = implName.substring(0, implName.length() - type.getSimpleName().length());
        }
        return implName.toLowerCase();
    }

    /**
     * 创建自适应扩展实例
     *
     * @return 自适应扩展实例
     */
    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            // 获取适配器类
            Class<?> adaptiveExtensionClass = getAdaptiveExtensionClass();
            // 无参构造实例化
            T adapter = (T) adaptiveExtensionClass.newInstance();
            // 注入
            return injectExtension(adapter);
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * @return 适配的扩展类
     */
    private Class<?> getAdaptiveExtensionClass() {
        // 加载所有扩展实现类
        getExtensionClasses();
        // 首次获取,创建适配器类,通过生成源码的方式
        if (cachedAdaptiveClass == null) {
            this.cachedAdaptiveClass = createAdaptiveExtensionClass();
        }
        return this.cachedAdaptiveClass;
    }

    /**
     * 创建适配器类,通过运行时生成代码的方式
     * @return 适配的扩展类
     */
    private Class<?> createAdaptiveExtensionClass() {
        // 创建适配器类代码生成器
        AdaptiveClassCodeGenerator codeGenerator = new AdaptiveClassCodeGenerator(type, cachedDefaultName);
        // 生成适配器类的源代码
        String code = codeGenerator.generate();
        // 查找类加载器
        ClassLoader classLoader = findClassLoader();
        // 获取编译器
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        // 编译源代码,生成字节码,并使用指定的加载器加载
        return compiler.compile(code, classLoader);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
