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
package org.apache.dubbo.config.spring.schema;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.AbstractServiceConfig;
import org.apache.dubbo.config.ArgumentConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.ServiceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.DubboConfigAliasPostProcessor;

import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.env.Environment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.alibaba.spring.util.BeanRegistrar.registerInfrastructureBean;
import static org.apache.dubbo.common.constants.CommonConstants.HIDE_KEY_PREFIX;

/**
 * dubbo通用的bean定义解析器
 *
 * 解析dubbo名称空间下的所有元素
 */
public class DubboBeanDefinitionParser implements BeanDefinitionParser {

    private static final Logger logger = LoggerFactory.getLogger(DubboBeanDefinitionParser.class);
    private static final Pattern GROUP_AND_VERSION = Pattern.compile("^[\\-.0-9_a-zA-Z]+(\\:[\\-.0-9_a-zA-Z]+)?$");
    private static final String ONRETURN = "onreturn";
    private static final String ONTHROW = "onthrow";
    private static final String ONINVOKE = "oninvoke";
    private static final String METHOD = "Method";

    /**
     * id为空会导致跳过bean定义注册
     *
     * @param element       根元素
     * @param parserContext 解析上下文
     * @param beanClass     解析生成的bean定义类型
     * @param required      是否必须,如果为true,则一定会按照一定规则生成id,且将其作为配置对象的id
     * @return
     */
    @SuppressWarnings("unchecked")
    private static BeanDefinition parse(Element element, ParserContext parserContext, Class<?> beanClass, boolean required) {
        // 根定义,不可继承配置
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClass(beanClass);
        beanDefinition.setLazyInit(false);
        // 获取id属性,可以解析占位符
        String id = resolveAttribute(element, "id", parserContext);
        /*
         * id值处理
         * 显示指定了id属性,则直接使用
         * 没有显示指定,则看require是否为true,
         * 为true则生成(依次取name属性,interface属性,beanClass),
         * 否则不处理.
         */
        id = generateIdIfNecessary(id, required, beanClass, element, parserContext);


        // id非空
        if (StringUtils.isNotEmpty(id)) {
            // 已注册,则报错
            if (parserContext.getRegistry().containsBeanDefinition(id)) {
                throw new IllegalStateException("Duplicate spring bean id " + id);
            }
            // 注册bean定义
            parserContext.getRegistry().registerBeanDefinition(id, beanDefinition);
            // setId(id)
            beanDefinition.getPropertyValues().addPropertyValue("id", id);
        }


        /*
         * 对ProtocolConfig,ServiceBean,ProviderConfig和ConsumerConfig
         * 存在特殊解析逻辑
         * ProtocolConfig需要统一其他bean对它的依赖引用,根据id识别
         */
        // 当前bean类型为协议配置,则让所有注入了ProtocolConfig对象的其他bean,引用当前bean的运行时引用
        // 这样可以使所有bean依赖同一个实例
        if (ProtocolConfig.class.equals(beanClass)) {
            // 遍历所有bean定义名称
            for (String name : parserContext.getRegistry().getBeanDefinitionNames()) {
                // 当前bean定义
                BeanDefinition definition = parserContext.getRegistry().getBeanDefinition(name);
                // getProtocol()
                PropertyValue property = definition.getPropertyValues().getPropertyValue("protocol");
                // 当前bean定义存在protocol属性
                if (property != null) {
                    // 获取属性值
                    Object value = property.getValue();
                    /*
                    * 避免相同id引用的ProtocolConfig实例不同
                     */
                    // bean定义的协议属性类型为协议配置,且id一致,则把bean定义替换为bean引用,避免同id实例化多次
                    if (value instanceof ProtocolConfig && id.equals(((ProtocolConfig) value).getName())) {
                        definition.getPropertyValues().addPropertyValue("protocol", new RuntimeBeanReference(id));
                    }
                }
            }
        }
        // 服务bean
        // <dubbo:service>
        // 如果配置了class属性,则把<property>子元素当做是对引用实现的依赖注入
        else if (ServiceBean.class.equals(beanClass)) {
            // class属性
            String className = resolveAttribute(element, "class", parserContext);
            // 非空
            if (StringUtils.isNotEmpty(className)) {
                RootBeanDefinition serviceImplDefinition = new RootBeanDefinition();
                serviceImplDefinition.setBeanClass(ReflectUtils.forName(className));
                serviceImplDefinition.setLazyInit(false);
                // 解析property子元素,作为服务实现bean的注入属性
                // 把子元素配置,当做是服务实现类的属性注入
                parseProperties(element.getChildNodes(), serviceImplDefinition, parserContext);
                // ServiceBean.setRef()
                beanDefinition.getPropertyValues().addPropertyValue("ref", new BeanDefinitionHolder(serviceImplDefinition, id + "Impl"));
            }
        }
        // 提供者配置,ProviderConfig
        // <dubbo:provider>
        else if (ProviderConfig.class.equals(beanClass)) {
            // 递归解析<dubbo:service>子元素,引用当前ProviderConfig
            parseNested(element, parserContext, ServiceBean.class, true, "service", "provider", id, beanDefinition);
        }
        // 消费者配置,ConsumerConfig
        // <dubbo:consumer>
        else if (ConsumerConfig.class.equals(beanClass)) {
            // 递归解析<dubbo:reference>子元素,生成ReferenceBean的定义,引用当前ConsumerConfig
            parseNested(element, parserContext, ReferenceBean.class, false, "reference", "consumer", id, beanDefinition);
        }

        /*
         * 下面要解析bean定义的依赖注入
         * 通用的依赖注入逻辑
         */
        // 根据bean类中的setter方法,收集xml中要识别的属性名
        Set<String> props = new HashSet<>();

        // 在xml元素属性中定义,但配置bean中不存在的属性名,会被当做自定义参数,
        // 追加到配置bean的parameters(Map<String,String>)中
        ManagedMap parameters = null;
        // 获取所有public方法
        for (Method setter : beanClass.getMethods()) {
            String setterName = setter.getName();
            // 是setter方法
            if (setterName.length() > 3 && setterName.startsWith("set")
                    && Modifier.isPublic(setter.getModifiers())
                    && setter.getParameterTypes().length == 1) {
                // 形参类型
                Class<?> propertyType = setter.getParameterTypes()[0];
                // 转换成驼峰属性名
                String camelPropertyName = setterName.substring(3, 4).toLowerCase() + setterName.substring(4);
                // 驼峰转中划线格式,作为xml中元素的属性名(约定)
                String spinalProperty = StringUtils.camelToSplitName(camelPropertyName, "-");
                //
                props.add(spinalProperty);
                // check the setter/getter whether match
                Method getter = null;
                try {
                    // set变get
                    getter = beanClass.getMethod("get" + setterName.substring(3), new Class<?>[0]);
                } catch (NoSuchMethodException e) {
                    try {
                        // set变is
                        getter = beanClass.getMethod("is" + setterName.substring(3), new Class<?>[0]);
                    } catch (NoSuchMethodException e2) {
                        // ignore, there is no need any log here since some class implement the interface: EnvironmentAware,
                        // ApplicationAware, etc. They only have setter method, otherwise will cause the error log during application start up.
                    }
                }
                // 不存在getter,或非pubic,或与setter的注入类型不一致
                // 则跳过
                if (getter == null
                        || !Modifier.isPublic(getter.getModifiers())
                        || !propertyType.equals(getter.getReturnType())) {
                    continue;
                }
                // bean的parameters属性要解析子元素
                if ("parameters".equals(spinalProperty)) {
                    // 解析<parameter key="" value=""/>子元素
                    parameters = parseParameters(element.getChildNodes(), beanDefinition, parserContext);
                }
                // bean的methods方法要解析子元素
                else if ("methods".equals(spinalProperty)) {
                    // 解析<method name="">子元素,生成MethodConfig定义,注入当前bean定义
                    parseMethods(id, element.getChildNodes(), beanDefinition, parserContext);
                }
                // arguments
                else if ("arguments".equals(spinalProperty)) {
                    // 解析<argument index="">子元素
                    // 生成ArgumentConfig定义,注入当前bean定义
                    parseArguments(id, element.getChildNodes(), beanDefinition, parserContext);
                }
                // 其他的属性(除parameters,methods,arguments以外),
                // 从xml元素属性中读取
                else {
                    parseDependenciesFromAttributes(element, parserContext, beanDefinition, spinalProperty, camelPropertyName, beanClass, propertyType);
                }
            }
        }

        // 获取元素的所有属性
        NamedNodeMap attributes = element.getAttributes();
        int len = attributes.getLength();
        // 遍历属性
        for (int i = 0; i < len; i++) {
            // 当前属性
            Node node = attributes.item(i);
            // 属性名称
            String name = node.getLocalName();
            // 配置bean中不存在setter
            if (!props.contains(name)) {
                if (parameters == null) {
                    parameters = new ManagedMap();
                }
                String value = node.getNodeValue();
                parameters.put(name, new TypedStringValue(value, String.class));
            }
        }
        if (parameters != null) {
            beanDefinition.getPropertyValues().addPropertyValue("parameters", parameters);
        }
        return beanDefinition;
    }

    private static void parseDependenciesFromAttributes(Element element, ParserContext parserContext, BeanDefinition beanDefinition, String spinalProperty, String camelPropertyName, Class<?> beanClass, Class<?> propertyType) {
        // 获取对应名称的属性
        String value = resolveAttribute(element, spinalProperty, parserContext);
        // 存在
        if (value != null) {
            value = value.trim();
            // 非空
            if (value.length() > 0) {
                // registry="N/A",则创建bean定义注入
                if ("registry".equals(spinalProperty) && RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(value)) {
                    RegistryConfig registryConfig = new RegistryConfig();
                    // 地址为N/A
                    registryConfig.setAddress(RegistryConfig.NO_AVAILABLE);
                    // 给bean定义直接注入实例
                    beanDefinition.getPropertyValues().addPropertyValue(camelPropertyName, registryConfig);
                }
                // provider属性或registry属性或protocol属性,且bean类型为服务配置
                // 这里注入的是id
                else if ("provider".equals(spinalProperty) || "registry".equals(spinalProperty) || ("protocol".equals(spinalProperty) && AbstractServiceConfig.class.isAssignableFrom(beanClass))) {
                    /**
                     * For 'provider' 'protocol' 'registry', keep literal value (should be id/name) and set the value to 'registryIds' 'providerIds' protocolIds'
                     * The following process should make sure each id refers to the corresponding instance, here's how to find the instance for different use cases:
                     * 1. Spring, check existing bean by id, see{@link ServiceBean#afterPropertiesSet()}; then try to use id to find configs defined in remote Config Center
                     * 2. API, directly use id to find configs defined in remote Config Center; if all config instances are defined locally, please use {@link ServiceConfig#setRegistries(List)}
                     */
                    // 服务端支持idsString注入
                    // 如:providerIds,registryIds,protocolIds
                    beanDefinition.getPropertyValues().addPropertyValue(camelPropertyName + "Ids", value);
                }
                // 其他
                else {
                    Object reference;
                    // 基本类型及其包装类,或String,Date,Class
                    // 直接作为依赖注入
                    if (isPrimitive(propertyType)) {
                        // 这里是为了兼容,如果发现属性值为老版本的默认值,则清除,这样可以自动使用新版本的默认值
                        if ("async".equals(spinalProperty) && "false".equals(value)
                                || "timeout".equals(spinalProperty) && "0".equals(value)
                                || "delay".equals(spinalProperty) && "0".equals(value)
                                || "version".equals(spinalProperty) && "0.0.0".equals(value)
                                || "stat".equals(spinalProperty) && "-1".equals(value)
                                || "reliable".equals(spinalProperty) && "false".equals(value)) {
                            // backward compatibility for the default value in old version's xsd
                            value = null;
                        }
                        reference = value;
                    }
                    // onreturn或onthrow或oninvoke
                    // ref.method
                    else if (ONRETURN.equals(spinalProperty) || ONTHROW.equals(spinalProperty) || ONINVOKE.equals(spinalProperty)) {
                        int index = value.lastIndexOf(".");
                        String ref = value.substring(0, index);
                        String method = value.substring(index + 1);
                        reference = new RuntimeBeanReference(ref);
                        // 属性注入
                        beanDefinition.getPropertyValues().addPropertyValue(spinalProperty + METHOD, method);
                    }
                    //
                    else {
                        // ref属性且存在对应bean定义
                        if ("ref".equals(spinalProperty) && parserContext.getRegistry().containsBeanDefinition(value)) {
                            BeanDefinition refBean = parserContext.getRegistry().getBeanDefinition(value);
                            if (!refBean.isSingleton()) {
                                throw new IllegalStateException("The exported service ref " + value + " must be singleton! Please set the " + value + " bean scope to singleton, eg: <bean id=\"" + value + "\" scope=\"singleton\" ...>");
                            }
                        }
                        // 运行时bean引用
                        reference = new RuntimeBeanReference(value);
                    }
                    beanDefinition.getPropertyValues().addPropertyValue(camelPropertyName, reference);
                }
            }
        }

    }

    private static String generateIdIfNecessary(String id, boolean required, Class<?> beanClass, Element element, ParserContext parserContext) {
        // id为空且require为true
        if (StringUtils.isEmpty(id) && required) {
            // 获取name属性,可以解析占位符
            String generatedBeanName = resolveAttribute(element, "name", parserContext);
            // 为空
            if (StringUtils.isEmpty(generatedBeanName)) {
                // 协议配置,则bean名称为dubbo
                if (ProtocolConfig.class.equals(beanClass)) {
                    generatedBeanName = "dubbo";
                }
                // 其他配置,则获取interface属性,以接口作为名称
                else {
                    generatedBeanName = resolveAttribute(element, "interface", parserContext);
                }
            }
            // 还为空,则使用配置bean的实际类型,如:ApplicationConfig
            if (StringUtils.isEmpty(generatedBeanName)) {
                generatedBeanName = beanClass.getName();
            }
            // 使用最终名称作为id
            id = generatedBeanName;
            int counter = 2;
            // 找到唯一的还未注册的id
            // 原始id无编号,重复后从2开始
            while (parserContext.getRegistry().containsBeanDefinition(id)) {
                id = generatedBeanName + (counter++);
            }
        }
        return id;
    }

    private static boolean isPrimitive(Class<?> cls) {
        return cls.isPrimitive() || cls == Boolean.class || cls == Byte.class
                || cls == Character.class || cls == Short.class || cls == Integer.class
                || cls == Long.class || cls == Float.class || cls == Double.class
                || cls == String.class || cls == Date.class || cls == Class.class;
    }

    /**
     * ???
     *
     * @param element        当前元素
     * @param parserContext  解析上下文
     * @param beanClass      嵌套bean的类型
     * @param required       是否必须
     * @param tag            要解析的目标标签名
     * @param property       要把ref注入到子定义的哪个属性
     * @param ref            引用的beanName
     * @param beanDefinition 引用beanName对应的bean定义
     */
    private static void parseNested(Element element, ParserContext parserContext, Class<?> beanClass, boolean required, String tag, String property, String ref, BeanDefinition beanDefinition) {
        // 子元素
        NodeList nodeList = element.getChildNodes();
        if (nodeList == null) {
            return;
        }
        boolean first = true;
        // 遍历子元素
        for (int i = 0; i < nodeList.getLength(); i++) {
            // 当前子元素
            Node node = nodeList.item(i);
            // 跳过非元素
            if (!(node instanceof Element)) {
                continue;
            }
            // nodeName为dubbo:reference,localName为reference
            if (tag.equals(node.getNodeName()) || tag.equals(node.getLocalName())) {
                // 第一个元素,把第一个元素的default属性,作为外部定义的default属性
                if (first) {
                    first = false;
                    // default属性
                    String isDefault = resolveAttribute(element, "default", parserContext);
                    // 为空
                    if (StringUtils.isEmpty(isDefault)) {
                        beanDefinition.getPropertyValues().addPropertyValue("default", "false");
                    }
                }
                // 解析元素
                BeanDefinition subDefinition = parse((Element) node, parserContext, beanClass, required);
                // 子定义,依赖当前定义
                if (subDefinition != null && StringUtils.isNotEmpty(ref)) {
                    subDefinition.getPropertyValues().addPropertyValue(property, new RuntimeBeanReference(ref));
                }
            }
        }
    }

    private static void parseProperties(NodeList nodeList, RootBeanDefinition beanDefinition, ParserContext parserContext) {
        if (nodeList == null) {
            return;
        }
        for (int i = 0; i < nodeList.getLength(); i++) {
            // 跳过非元素
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }
            Element element = (Element) nodeList.item(i);
            // property元素
            if ("property".equals(element.getNodeName())
                    || "property".equals(element.getLocalName())) {
                // name属性
                String name = resolveAttribute(element, "name", parserContext);
                // 非空
                if (StringUtils.isNotEmpty(name)) {
                    // value属性
                    String value = resolveAttribute(element, "value", parserContext);
                    // ref属性
                    String ref = resolveAttribute(element, "ref", parserContext);
                    // value属性值非空
                    if (StringUtils.isNotEmpty(value)) {
                        beanDefinition.getPropertyValues().addPropertyValue(name, value);
                    }
                    // ref属性值非空
                    else if (StringUtils.isNotEmpty(ref)) {
                        beanDefinition.getPropertyValues().addPropertyValue(name, new RuntimeBeanReference(ref));
                    }
                    // 其他报错
                    else {
                        throw new UnsupportedOperationException("Unsupported <property name=\"" + name + "\"> sub tag, Only supported <property name=\"" + name + "\" ref=\"...\" /> or <property name=\"" + name + "\" value=\"...\" />");
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ManagedMap parseParameters(NodeList nodeList, RootBeanDefinition beanDefinition, ParserContext parserContext) {
        if (nodeList == null) {
            return null;
        }
        ManagedMap parameters = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("parameter".equals(element.getNodeName())
                    || "parameter".equals(element.getLocalName())) {
                if (parameters == null) {
                    parameters = new ManagedMap();
                }
                String key = resolveAttribute(element, "key", parserContext);
                String value = resolveAttribute(element, "value", parserContext);
                boolean hide = "true".equals(resolveAttribute(element, "hide", parserContext));
                if (hide) {
                    key = HIDE_KEY_PREFIX + key;
                }
                parameters.put(key, new TypedStringValue(value, String.class));
            }
        }
        return parameters;
    }

    /**
     * @param id             beanName
     * @param nodeList       子节点
     * @param beanDefinition bean定义
     * @param parserContext  上下文
     */
    @SuppressWarnings("unchecked")
    private static void parseMethods(String id, NodeList nodeList, RootBeanDefinition beanDefinition,
                                     ParserContext parserContext) {
        if (nodeList == null) {
            return;
        }
        ManagedList methods = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            // 跳过非元素
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }
            Element element = (Element) nodeList.item(i);
            // method元素
            if ("method".equals(element.getNodeName()) || "method".equals(element.getLocalName())) {
                // name属性
                String methodName = resolveAttribute(element, "name", parserContext);
                // 属性为空则报错
                if (StringUtils.isEmpty(methodName)) {
                    throw new IllegalStateException("<dubbo:method> name attribute == null");
                }
                if (methods == null) {
                    methods = new ManagedList();
                }
                // 解析方法配置bean
                BeanDefinition methodBeanDefinition = parse(element, parserContext, MethodConfig.class, false);
                // 生成beanName
                String beanName = id + "." + methodName;
                BeanDefinitionHolder methodBeanDefinitionHolder = new BeanDefinitionHolder(methodBeanDefinition, beanName);
                methods.add(methodBeanDefinitionHolder);
            }
        }
        if (methods != null) {
            beanDefinition.getPropertyValues().addPropertyValue("methods", methods);
        }
    }

    @SuppressWarnings("unchecked")
    private static void parseArguments(String id, NodeList nodeList, RootBeanDefinition beanDefinition,
                                       ParserContext parserContext) {
        if (nodeList == null) {
            return;
        }
        ManagedList arguments = null;
        for (int i = 0; i < nodeList.getLength(); i++) {
            if (!(nodeList.item(i) instanceof Element)) {
                continue;
            }
            Element element = (Element) nodeList.item(i);
            if ("argument".equals(element.getNodeName()) || "argument".equals(element.getLocalName())) {
                // index
                String argumentIndex = resolveAttribute(element, "index", parserContext);
                if (arguments == null) {
                    arguments = new ManagedList();
                }
                // ArgumentConfig定义
                BeanDefinition argumentBeanDefinition = parse(element, parserContext, ArgumentConfig.class, false);
                //
                String beanName = id + "." + argumentIndex;
                BeanDefinitionHolder argumentBeanDefinitionHolder = new BeanDefinitionHolder(argumentBeanDefinition, beanName);
                arguments.add(argumentBeanDefinitionHolder);
            }
        }
        if (arguments != null) {
            beanDefinition.getPropertyValues().addPropertyValue("arguments", arguments);
        }
    }

    private static String resolveAttribute(Element element, String attributeName, ParserContext parserContext) {
        String attributeValue = element.getAttribute(attributeName);
        Environment environment = parserContext.getReaderContext().getEnvironment();
        return environment.resolvePlaceholders(attributeValue);
    }
    // -----------------------------------------------------------------
    /**
     * bean的实际类型,构造方法初始化
     */
    private final Class<?> beanClass;
    /**
     * 是否必要的bean(构造方法初始化)
     * 如果为true,则必须存在id(也就是beanName),不存在则使用默认策略生成,
     * 这样可以保证bean定义被注册(id非空才会被注册).
     * 如果为false,则配置了id则注入,否则不注入.
     */
    private final boolean required;

    /**
     * 构造方法
     *
     * @param beanClass
     * @param required
     */
    public DubboBeanDefinitionParser(Class<?> beanClass, boolean required) {
        this.beanClass = beanClass;
        this.required = required;
    }


    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        // 注册配置别名处理器 DubboConfigAliasPostProcessor
        // 这一步与解析无关
        // 处理器职责是把配置对象的id属性值,注册为beanName的别名
        registerDubboConfigAliasPostProcessor(parserContext.getRegistry());

        return parse(element, parserContext, beanClass, required);
    }

    /**
     * Register {@link DubboConfigAliasPostProcessor}
     *
     * @param registry {@link BeanDefinitionRegistry}
     * @since 2.7.5 [Feature] https://github.com/apache/dubbo/issues/5093
     */
    private void registerDubboConfigAliasPostProcessor(BeanDefinitionRegistry registry) {
        registerInfrastructureBean(
                registry,
                DubboConfigAliasPostProcessor.BEAN_NAME,
                DubboConfigAliasPostProcessor.class
        );
    }


}
