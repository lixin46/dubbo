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
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.event.ReferenceConfigDestroyedEvent;
import org.apache.dubbo.config.event.ReferenceConfigInitializedEvent;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.event.Event;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.metadata.WritableMetadataService;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.apache.dubbo.rpc.cluster.support.ClusterUtils;
import org.apache.dubbo.rpc.cluster.support.registry.ZoneAwareCluster;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.AsyncMethodInfo;
import org.apache.dubbo.rpc.model.ConsumerModel;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.model.ServiceRepository;
import org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.CLUSTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.LOCALHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.METADATA_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROXY_CLASS_REF;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.CommonConstants.REVISION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SEMICOLON_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.utils.NetUtils.isInvalidLocalHost;
import static org.apache.dubbo.config.Constants.DUBBO_IP_TO_REGISTRY;
import static org.apache.dubbo.registry.Constants.CONSUMER_PROTOCOL;
import static org.apache.dubbo.registry.Constants.REGISTER_IP_KEY;
import static org.apache.dubbo.rpc.Constants.LOCAL_PROTOCOL;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

/**
 * 引用配置
 * Please avoid using this class for any new application,
 * use {@link ReferenceConfigBase} instead.
 */
public class ReferenceConfig<T> extends ReferenceConfigBase<T> {

    public static final Logger logger = LoggerFactory.getLogger(ReferenceConfig.class);

    /**
     * 协议负责创建Invoker调用器,调用器可以进行远程调用
     * Protocol.refer()可以识别url中的协议,进行适配映射
     */
    private static final Protocol REF_PROTOCOL = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

    /**
     * The {@link Cluster}'s implementation with adaptive functionality, and actually it will get a {@link Cluster}'s
     * specific implementation who is wrapped with <b>MockClusterInvoker</b>
     */
    private static final Cluster CLUSTER = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();

    /**
     * A {@link ProxyFactory} implementation that will generate a reference service's proxy,the JavassistProxyFactory is
     * its default implementation
     */
    private static final ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * 服务仓库
     */
    private final ServiceRepository repository;

    /**
     * 启动器
     */
    private DubboBootstrap bootstrap;

    /**
     * 接口的代理对象
     */
    private transient volatile T ref;

    /**
     * 客户端代理使用的调用器
     * 调用器可以处理Invocation
     */
    private transient volatile Invoker<?> invoker;

    /**
     * 是否已经初始化,只能一次
     */
    private transient volatile boolean initialized;

    /**
     * 是否已经销毁,只能一次,且不能再次初始化
     */
    private transient volatile boolean destroyed;

    /**
     * 构造方法
     */
    public ReferenceConfig() {
        super();
        this.repository = ApplicationModel.getServiceRepository();
    }


    /**
     * 生成代理对象,
     * 子类ReferenceBean集成了spring的FactoryBean接口,getObject()调用当前方法产生对象
     *
     * @return 代理对象
     */
    public synchronized T get() {
        // 已销毁则报错
        if (destroyed) {
            throw new IllegalStateException("The invoker of ReferenceConfig(" + url + ") has already destroyed!");
        }
        // 引用为null,则创建对象
        if (ref == null) {
            init();
        }
        return ref;
    }

    public synchronized void destroy() {
        if (ref == null) {
            return;
        }
        if (destroyed) {
            return;
        }
        destroyed = true;
        try {
            invoker.destroy();
        } catch (Throwable t) {
            logger.warn("Unexpected error occured when destroy invoker of ReferenceConfig(" + url + ").", t);
        }
        invoker = null;
        ref = null;

        // dispatch a ReferenceConfigDestroyedEvent since 2.7.4
        dispatch(new ReferenceConfigDestroyedEvent(this));
    }

    /**
     * 初始化,生成rpc代理对象
     */
    public synchronized void init() {
        // 已初始化则返回
        if (initialized) {
            return;
        }
        // 引导对象不存在,则获取单例
        if (bootstrap == null) {
            // 获取dubbo引导实例
            bootstrap = DubboBootstrap.getInstance();
            // 初始化,做了什么???
            bootstrap.init();
        }

        // 检查和更新子配置???
        checkAndUpdateSubConfigs();
        // ???
        checkStubAndLocal(interfaceClass);
        // 检查模拟???
        ConfigValidationUtils.checkMock(interfaceClass, this);

        //-----------------
        Map<String, String> referParameters = new HashMap<String, String>();
        // side=consumer
        referParameters.put(SIDE_KEY, CONSUMER_SIDE);
        // 追加运行时参数???
        // dubbo,release,timestamp,pid
        ReferenceConfigBase.appendRuntimeParameters(referParameters);
        // 非通用接口
        if (!ProtocolUtils.isGeneric(generic)) {
            // 首先根据接口类识别版本,识别不到则使用version作为默认版本
            String revision = Version.getVersion(interfaceClass, version);
            // revision
            if (revision != null && revision.length() > 0) {
                referParameters.put(REVISION_KEY, revision);
            }
            Wrapper wrapper = Wrapper.getWrapper(interfaceClass);
            // 接口类的方法名
            String[] methods = wrapper.getMethodNames();
            // 为空
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                // methods=*
                referParameters.put(METHODS_KEY, ANY_VALUE);
            }
            // methods=方法名逗号分隔
            else {
                referParameters.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), COMMA_SEPARATOR));
            }
        }
        // interface=接口名
        referParameters.put(INTERFACE_KEY, interfaceName);
        /*
         * 对象中带有@Parameter注解的getter获取到注入Map
         * 追加时都是无前缀的
         */
        // MetricsConfig
        AbstractConfig.appendParameters(referParameters, getMetrics());
        // ApplicationConfig
        AbstractConfig.appendParameters(referParameters, getApplication());
        // ModuleConfig
        AbstractConfig.appendParameters(referParameters, getModule());
        // remove 'default.' prefix for configs from ConsumerConfig
        // appendParameters(map, consumer, Constants.DEFAULT_KEY);
        // ConsumerConfig
        AbstractConfig.appendParameters(referParameters, consumer);
        // ???
        AbstractConfig.appendParameters(referParameters, this);
        //
        MetadataReportConfig metadataReportConfig = getMetadataReportConfig();
        if (metadataReportConfig != null && metadataReportConfig.isValid()) {
            // metadata-type=remote
            referParameters.putIfAbsent(METADATA_KEY, REMOTE_METADATA_STORAGE_TYPE);
        }
        Map<String, AsyncMethodInfo> attributes = null;
        // 方法非空
        if (CollectionUtils.isNotEmpty(getMethods())) {
            attributes = new HashMap<>();
            for (MethodConfig methodConfig : getMethods()) {
                // 以方法名为前缀,追加方法配置
                AbstractConfig.appendParameters(referParameters, methodConfig, methodConfig.getName());
                String retryKey = methodConfig.getName() + ".retry";
                if (referParameters.containsKey(retryKey)) {
                    String retryValue = referParameters.remove(retryKey);
                    if ("false".equals(retryValue)) {
                        referParameters.put(methodConfig.getName() + ".retries", "0");
                    }
                }
                // 转异步配置
                AsyncMethodInfo asyncMethodInfo = AbstractConfig.convertMethodConfig2AsyncInfo(methodConfig);
                // 存在
                if (asyncMethodInfo != null) {
                    // 保存属性
                    attributes.put(methodConfig.getName(), asyncMethodInfo);
                }
            }
        }

        String hostToRegistry = ConfigUtils.getSystemProperty(DUBBO_IP_TO_REGISTRY);
        // 为空,则注册到本地
        if (StringUtils.isEmpty(hostToRegistry)) {
            hostToRegistry = NetUtils.getLocalHost();
        }
        // 非空
        else if (isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        }
        // 添加注册中心ip
        referParameters.put(REGISTER_IP_KEY, hostToRegistry);

        serviceMetadata.getAttachments().putAll(referParameters);
        //-----------------

        // 创建代理
        ref = createProxy(referParameters);

        // 元数据保存目标对象
        serviceMetadata.setTarget(ref);
        // 添加属性refClass=代理对象
        serviceMetadata.addAttribute(PROXY_CLASS_REF, ref);
        // 查找延迟的服务
        ConsumerModel consumerModel = repository.lookupReferredService(serviceMetadata.getServiceKey());
        // 设置对象
        consumerModel.setProxyObject(ref);
        // 初始化???
        consumerModel.init(attributes);

        // 初始化完毕
        initialized = true;

        // dispatch a ReferenceConfigInitializedEvent since 2.7.4
        // 分发引用配置初始化完毕事件
        dispatch(new ReferenceConfigInitializedEvent(this, invoker));
    }

    /**
     * 创建代理
     * 通过协议对象,引用指定的接口类型和url,获取调用器
     * 分为3种引用方式:
     * 1.jvm进程内引用
     * 2.直连方式引用
     * 3.
     *
     * @param parameters 配置参数
     * @return
     */
    @SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
    private T createProxy(Map<String, String> parameters) {
        /*
         * 第1步:创建Invoker调用器
         */
        // 应该引用jvm内对象
        // 本地访问
        if (shouldJvmRefer(parameters)) {
            // injvm://127.0.0.1:0/{interfaceName}
            URL url = new URL(
                    LOCAL_PROTOCOL,
                    LOCALHOST_VALUE,
                    0,
                    interfaceClass.getName()
            )
                    .addParameters(parameters);
            // 协议创建
            invoker = REF_PROTOCOL.refer(interfaceClass, url);
            if (logger.isInfoEnabled()) {
                logger.info("Using injvm service " + interfaceClass.getName());
            }
        }
        // 需要走网络的引用
        else {
            urls.clear();
            // 直连方式,可以配置多个,url之间分隔分隔
            if (url != null && url.length() > 0) { // user specified URL, could be peer-to-peer address, or register center's address.
                // 分号分拆
                String[] directUrls = SEMICOLON_SPLIT_PATTERN.split(url);
                if (directUrls != null && directUrls.length > 0) {
                    // 遍历
                    for (String u : directUrls) {
                        URL directUrl = URL.valueOf(u);
                        // 路径为空,则默认接口名作为路径
                        // 也就是说,url可以不指定path
                        if (StringUtils.isEmpty(directUrl.getPath())) {
                            directUrl = directUrl.setPath(interfaceName);
                        }
                        // 协议为registry
                        // registry://  或  service-discovery-registry://
                        if (UrlUtils.isRegistry(directUrl)) {
                            // refer=查询字符串
                            urls.add(directUrl.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(parameters)));
                        }
                        // 非registry
                        else {
                            directUrl = ClusterUtils.mergeUrl(directUrl, parameters);
                            urls.add(directUrl);
                        }
                    }
                }
            }
            // url为空,非直连,则从注册中心获取
            else { // assemble URL from register center's configuration
                // if protocols not injvm checkRegistry
                // protocol不为injvm
                if (!LOCAL_PROTOCOL.equalsIgnoreCase(getProtocol())) {
                    // 检查注册表
                    // 根据ids的属性值,从配置管理器取配置对象,并判断配置是否有效
                    checkRegistry();

                    // 解析配置,获取注册中心的url列表
                    // 从当前对象的RegistryConfig中解析url列表
                    // 解析<dubbo:registry address="">得到url
                    List<URL> registryUrls = ConfigValidationUtils.loadRegistries(this, false);
                    // 非空
                    if (CollectionUtils.isNotEmpty(registryUrls)) {
                        // 遍历
                        for (URL registryUrl : registryUrls) {
                            // 加载监视地址
                            URL monitorUrl = ConfigValidationUtils.loadMonitor(this, registryUrl);
                            // 存在
                            if (monitorUrl != null) {
                                // monitor
                                parameters.put(MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                            }
                            // 参数转为查询字符串格式,作为refer参数的值
                            // 追加到注册中心url中
                            urls.add(registryUrl.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(parameters)));
                        }
                    }
                    // 为空,则报错
                    if (urls.isEmpty()) {
                        throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                    }
                }
            }

            // 只有一个url
            if (urls.size() == 1) {
                // 调用协议,根据接口类和url创建调用器
                invoker = REF_PROTOCOL.refer(interfaceClass, urls.get(0));
            }
            // 不止一个,则分别创建调用器,之后整合为一个调用器
            else {
                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                for (URL url : urls) {
                    Invoker<?> invoker = REF_PROTOCOL.refer(interfaceClass, url);
                    invokers.add(invoker);
                    if (UrlUtils.isRegistry(url)) {
                        registryURL = url; // use last registry url
                    }
                }


                if (registryURL != null) { // registry url is available
                    // for multi-subscription scenario, use 'zone-aware' policy by default
                    URL u = registryURL.addParameterIfAbsent(CLUSTER_KEY, ZoneAwareCluster.NAME);
                    // The invoker wrap relation would be like: ZoneAwareClusterInvoker(StaticDirectory) -> FailoverClusterInvoker(RegistryDirectory, routing happens here) -> Invoker
                    // 基于调用器封装目录
                    StaticDirectory staticDirectory = new StaticDirectory(u, invokers);
                    // 集群把目录封装为调用器
                    invoker = CLUSTER.join(staticDirectory);
                } else { // not a registry url, must be direct invoke.
                    // 基于调用器封装目录
                    StaticDirectory staticDirectory = new StaticDirectory(invokers);
                    invoker = CLUSTER.join(staticDirectory);
                }
            }
        }// 走网络的调用器创建完毕

        /*
         * 第2步:检查Invoker调用器可用性
         */
        // 启动时检查且调用器不可用
        // <dubbo:reference check="true">
        if (shouldCheck() && !invoker.isAvailable()) {
            // 销毁调用器
            invoker.destroy();
            // 报错
            throw new IllegalStateException("Failed to check the status of the service "
                    + interfaceName
                    + ". No provider available for the service "
                    + (group == null ? "" : group + "/")
                    + interfaceName +
                    (version == null ? "" : ":" + version)
                    + " from the url "
                    + invoker.getUrl()
                    + " to the consumer "
                    + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion());
        }
        // 服务引用日志
        if (logger.isInfoEnabled()) {
            logger.info("Refer dubbo service " + interfaceClass.getName() + " from url " + invoker.getUrl());
        }
        /*
         * 第3步:存储服务元数据
         * @since 2.7.0
         */
        // metadata-type
        String metadata = parameters.get(METADATA_KEY);
        // 可写的元数据服务
        WritableMetadataService metadataService = WritableMetadataService.getExtension(metadata == null ? DEFAULT_METADATA_STORAGE_TYPE : metadata);
        // 存在元数据服务
        if (metadataService != null) {
            // consumer://{register.ip}:0/{interface}?参数
            URL consumerURL = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, parameters.get(INTERFACE_KEY), parameters);
            // 发布服务定义,把FullServiceDefinition序列化成String存储
            metadataService.publishServiceDefinition(consumerURL);
        }
        /*
         * 第4步:代理工厂,根据调用器,创建代理
         */
        // create service proxy
        return (T) PROXY_FACTORY.getProxy(invoker, ProtocolUtils.isGeneric(generic));
    }

    /**
     * This method should be called right after the creation of this class's instance, before any property in other config modules is used.
     * Check each config modules are created properly and override their properties if necessary.
     */
    public void checkAndUpdateSubConfigs() {
        if (StringUtils.isEmpty(interfaceName)) {
            throw new IllegalStateException("<dubbo:reference interface=\"\" /> interface not allow null!");
        }
        // 先整合继承层级AbstractInterfaceConfig的配置
        // 完成符合消费者配置
        completeCompoundConfigs(consumer);
        //
        if (consumer != null) {
            if (StringUtils.isEmpty(registryIds)) {
                setRegistryIds(consumer.getRegistryIds());
            }
        }
        // get consumer's global configuration
        // 确保一定存在消费者配置
        checkDefault();
        // 获取Configuration中的键值对,注入当前对象
        this.refresh();

        // AbstractReferenceConfig中的配置
        if (getGeneric() == null && getConsumer() != null) {
            setGeneric(getConsumer().getGeneric());
        }
        // 通用引用
        if (ProtocolUtils.isGeneric(generic)) {
            interfaceClass = GenericService.class;
        }
        // 特定接口
        else {
            try {
                // 加载类
                interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            //
            checkInterfaceAndMethods(interfaceClass, getMethods());
        }

        //init serivceMetadata
        serviceMetadata.setVersion(version);
        serviceMetadata.setGroup(group);
        serviceMetadata.setDefaultGroup(group);
        serviceMetadata.setServiceType(getActualInterface());
        serviceMetadata.setServiceInterfaceName(interfaceName);
        // TODO, uncomment this line once service key is unified
        serviceMetadata.setServiceKey(URL.buildKey(interfaceName, group, version));

        ServiceRepository repository = ApplicationModel.getServiceRepository();
        ServiceDescriptor serviceDescriptor = repository.registerService(interfaceClass);
        repository.registerConsumer(
                serviceMetadata.getServiceKey(),
                serviceDescriptor,
                this,
                null,
                serviceMetadata);
        // 解析文件???
        resolveFile();
        // 校验引用配置
        ConfigValidationUtils.validateReferenceConfig(this);
        // 后置处理
        postProcessConfig();
    }


    /**
     * Figure out should refer the service in the same JVM from configurations. The default behavior is true
     * 1. if injvm is specified, then use it
     * 2. then if a url is specified, then assume it's a remote call
     * 3. otherwise, check scope parameter
     * 4. if scope is not specified but the target service is provided in the same JVM, then prefer to make the local
     * call, which is the default behavior
     */
    protected boolean shouldJvmRefer(Map<String, String> parameters) {
        // temp://localhost:0?parameters
        URL tmpUrl = new URL("temp", "localhost", 0, parameters);
        boolean isJvmRefer;
        // 直连方式肯定不是jvm
        if (url != null && url.length() > 0) {
            isJvmRefer = false;
        }
        // 非直连方式,则通过scope参数判断
        else {
            // by default, reference local service if there is
            InjvmProtocol injvmProtocol = InjvmProtocol.getInjvmProtocol();
            isJvmRefer = injvmProtocol.isInjvmRefer(tmpUrl);
        }
        return isJvmRefer;
    }

    /**
     * Dispatch an {@link Event event}
     *
     * @param event an {@link Event event}
     * @since 2.7.5
     */
    protected void dispatch(Event event) {
        EventDispatcher.getDefaultExtension().dispatch(event);
    }

    public DubboBootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(DubboBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    private void postProcessConfig() {
        ExtensionLoader<ConfigPostProcessor> extensionLoader = ExtensionLoader.getExtensionLoader(ConfigPostProcessor.class);
        List<ConfigPostProcessor> configPostProcessors = extensionLoader.getActivateExtension(URL.valueOf("configPostProcessor://"), (String[]) null);
        configPostProcessors.forEach(component -> component.postProcessReferConfig(this));
    }

    // just for test
    Invoker<?> getInvoker() {
        return invoker;
    }
}
