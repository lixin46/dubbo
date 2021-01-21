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
package org.apache.dubbo.registry.integration;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.AddressListener;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.Configurator;
import org.apache.dubbo.rpc.cluster.Router;
import org.apache.dubbo.rpc.cluster.RouterChain;
import org.apache.dubbo.rpc.cluster.RouterFactory;
import org.apache.dubbo.rpc.cluster.directory.AbstractDirectory;
import org.apache.dubbo.rpc.cluster.directory.StaticDirectory;
import org.apache.dubbo.rpc.cluster.governance.GovernanceRuleRepository;
import org.apache.dubbo.rpc.cluster.support.ClusterUtils;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.protocol.InvokerWrapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.DISABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_PROTOCOL;
import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.APP_DYNAMIC_CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.COMPATIBLE_CONFIG_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONSUMERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DEFAULT_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTE_PROTOCOL;
import static org.apache.dubbo.registry.Constants.CONFIGURATORS_SUFFIX;
import static org.apache.dubbo.registry.Constants.REGISTER_KEY;
import static org.apache.dubbo.registry.Constants.SIMPLIFIED_KEY;
import static org.apache.dubbo.registry.integration.RegistryProtocol.DEFAULT_REGISTER_CONSUMER_KEYS;
import static org.apache.dubbo.remoting.Constants.CHECK_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.ROUTER_KEY;


/**
 * RegistryDirectory
 */
public class RegistryDirectory<T> extends AbstractDirectory<T> implements NotifyListener {

    private static final Logger logger = LoggerFactory.getLogger(RegistryDirectory.class);
    /**
     * 集群适配器,负责产生集群调用器
     */
    private static final Cluster CLUSTER = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();
    /**
     * 路由器工厂适配器,负责创建路由器
     */
    private static final RouterFactory ROUTER_FACTORY = ExtensionLoader.getExtensionLoader(RouterFactory.class).getAdaptiveExtension();
    /**
     * 消费者配置监听器
     * ConfigurationListener实现,构造方法初始化时自动追加到DynamicConfiguration中.
     * 之后进行首次规则拉取,并解析为List<Configurator>.
     * 每次配置变更,都会重新解析配置器列表.
     */
    private static final ConsumerConfigurationListener CONSUMER_CONFIGURATION_LISTENER = new ConsumerConfigurationListener();


    // -----------------------------------------------------------------------------------------------------------------
    /**
     * 服务键,构造方法初始化,不能为null
     * 默认为分组/接口:版本
     */
    private final String serviceKey; // Initialization at construction time, assertion not null
    /**
     * 服务接口,构造方法初始化
     */
    private final Class<T> serviceType; // Initialization at construction time, assertion not null
    /**
     * url中的refer参数值,构造方法初始化
     */
    private final Map<String, String> queryMap; // Initialization at construction time, assertion not null
    /**
     * 消费者目录???构造方法初始化
     */
    private final URL directoryUrl; // Initialization at construction time, assertion not null, and always assign non null value
    /**
     * 是否存在多个分组,构造方法初始化
     */
    private final boolean multiGroup;
    /**
     * 是否应该注册,读取url中的register参数,
     * 有什么用???
     */
    private boolean shouldRegister;
    /**
     * 是否应该简化注册,读取url中的simplified参数,
     * 有什么用?
     */
    private boolean shouldSimplified;
    /**
     * 重写的目录url???构造方法初始化
     */
    private volatile URL overrideDirectoryUrl; // Initialization at construction time, assertion not null, and always assign non null value

    /**
     * setter注入
     */
    private Protocol protocol; // Initialization at the time of injection, the assertion is not null
    /**
     * setter注入
     */
    private Registry registry; // Initialization at the time of injection, the assertion is not null

    private volatile boolean forbidden = false;


    private volatile URL registeredConsumerUrl;

    /**
     * override rules
     * Priority: override>-D>consumer>provider
     * Rule one: for a certain provider <ip:port,timeout=100>
     * Rule two: for all providers <* ,timeout=5000>
     *
     * 配置器,用于重写url的参数
     */
    private volatile List<Configurator> configurators; // The initial value is null and the midway may be assigned to null, please use the local variable reference

    // Map<url, Invoker> cache service url to invoker mapping.
    private volatile Map<String, Invoker<T>> urlInvokerMap; // The initial value is null and the midway may be assigned to null, please use the local variable reference
    private volatile List<Invoker<T>> invokers;

    // Set<invokerUrls> cache invokeUrls to invokers mapping.
    private volatile Set<URL> cachedInvokerUrls; // The initial value is null and the midway may be assigned to null, please use the local variable reference

    /**
     * 当前引用对应的配置监听器,监听配置中心关于当前引用key的变化,并同步变更内存引用的Configurator列表
     * ConfigurationListener实现
     */
    private ReferenceConfigurationListener serviceConfigurationListener;


    /**
     * 唯一构造方法
     *
     * @param serviceType 服务接口类型
     * @param url         信息,比如zookeeper://
     */
    public RegistryDirectory(Class<T> serviceType, URL url) {
        super(url);
        if (serviceType == null) {
            throw new IllegalArgumentException("service type is null.");
        }
        // 接口不为'*'且register为true,则应该注册
        shouldRegister = !ANY_VALUE.equals(url.getServiceInterface()) && url.getParameter(REGISTER_KEY, true);
        // simplified参数
        shouldSimplified = url.getParameter(SIMPLIFIED_KEY, false);
        // group/interface:version
        if (url.getServiceKey() == null || url.getServiceKey().length() == 0) {
            throw new IllegalArgumentException("registry serviceKey is null.");
        }
        this.serviceType = serviceType;
        this.serviceKey = url.getServiceKey();
        this.queryMap = StringUtils.parseQueryString(url.getParameterAndDecoded(REFER_KEY));
        // 把注册中心url转换成消费者url
        this.overrideDirectoryUrl = this.directoryUrl = turnRegistryUrlToConsumerUrl(url);
        // group参数
        String group = directoryUrl.getParameter(GROUP_KEY, "");
        // group为'*'或存在多个分组,则为true
        this.multiGroup = group != null && (ANY_VALUE.equals(group) || group.contains(","));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Node接口实现
    @Override
    public void destroy() {
        if (isDestroyed()) {
            return;
        }

        // unregister.
        try {
            if (getRegisteredConsumerUrl() != null && registry != null && registry.isAvailable()) {
                registry.unregister(getRegisteredConsumerUrl());
            }
        } catch (Throwable t) {
            logger.warn("unexpected error when unregister service " + serviceKey + "from registry" + registry.getUrl(), t);
        }
        // unsubscribe.
        try {
            if (getConsumerUrl() != null && registry != null && registry.isAvailable()) {
                registry.unsubscribe(getConsumerUrl(), this);
            }
            ExtensionLoader.getExtensionLoader(GovernanceRuleRepository.class).getDefaultExtension()
                    .removeListener(ApplicationModel.getApplication(), CONSUMER_CONFIGURATION_LISTENER);
        } catch (Throwable t) {
            logger.warn("unexpected error when unsubscribe service " + serviceKey + "from registry" + registry.getUrl(), t);
        }
        super.destroy(); // must be executed after unsubscribing
        try {
            destroyAllInvokers();
        } catch (Throwable t) {
            logger.warn("Failed to destroy service " + serviceKey, t);
        }
    }

    @Override
    public boolean isAvailable() {
        if (isDestroyed()) {
            return false;
        }
        Map<String, Invoker<T>> localUrlInvokerMap = urlInvokerMap;
        if (localUrlInvokerMap != null && localUrlInvokerMap.size() > 0) {
            for (Invoker<T> invoker : new ArrayList<>(localUrlInvokerMap.values())) {
                if (invoker.isAvailable()) {
                    return true;
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // AbstractDirectory抽象父类实现
    @Override
    public List<Invoker<T>> doList(Invocation invocation) {
        if (forbidden) {
            // 1. No service provider 2. Service providers are disabled
            throw new RpcException(RpcException.FORBIDDEN_EXCEPTION, "No provider available from registry " +
                    getUrl().getAddress() + " for service " + getConsumerUrl().getServiceKey() + " on consumer " +
                    NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() +
                    ", please check status of providers(disabled, not registered or in blacklist).");
        }

        if (multiGroup) {
            return this.invokers == null ? Collections.emptyList() : this.invokers;
        }

        List<Invoker<T>> invokers = null;
        try {
            // Get invokers from cache, only runtime routers will be executed.
            invokers = routerChain.route(getConsumerUrl(), invocation);
        } catch (Throwable t) {
            logger.error("Failed to execute router: " + getUrl() + ", cause: " + t.getMessage(), t);
        }

        return invokers == null ? Collections.emptyList() : invokers;
    }
    // -----------------------------------------------------------------------------------------------------------------
    // Directory接口实现

    @Override
    public Class<T> getInterface() {
        return serviceType;
    }

    @Override
    public List<Invoker<T>> getAllInvokers() {
        return invokers;
    }

    @Override
    public URL getConsumerUrl() {
        return this.overrideDirectoryUrl;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // NotifyListener接口实现

    /**
     * 接收注册中心变化通知,被谁调???
     *
     * @param urlsForCategory 某一个类别对应的url列表
     */
    @Override
    public synchronized void notify(List<URL> urlsForCategory) {
        // key为category,value为url列表
        // category共3种:providers,routers,configurators
        Map<String, List<URL>> categoryUrls = urlsForCategory.stream()
                .filter(Objects::nonNull)
                .filter(this::isValidCategory)// 保留有效类别url
                .filter(this::isNotCompatibleFor26x)// 保留不兼容的url
                .collect(Collectors.groupingBy(this::judgeCategory));

        // 通知配置器变化
        notifyConfiguratorsChanged(categoryUrls);
        // 通知路由器变化
        notifyRoutersChanged(categoryUrls);
        // 通知服务变化
        notifyProvidersChanged(categoryUrls);
    }

    private void notifyProvidersChanged(Map<String, List<URL>> categoryUrls) {
        List<URL> providerURLs = categoryUrls.getOrDefault(PROVIDERS_CATEGORY, Collections.emptyList());
        /**
         * 3.x added for extend URL address
         */
        ExtensionLoader<AddressListener> addressListenerExtensionLoader = ExtensionLoader.getExtensionLoader(AddressListener.class);
        // 支持的地址监听器
        List<AddressListener> supportedListeners = addressListenerExtensionLoader.getActivateExtension(getUrl(), (String[]) null);
        // 地址监听器非空
        if (supportedListeners != null && !supportedListeners.isEmpty()) {
            // 遍历监听器
            for (AddressListener addressListener : supportedListeners) {
                // 通知,可进行扩展,对地址列表进行处理
                providerURLs = addressListener.notify(providerURLs, getConsumerUrl(), this);
            }
        }
        // 刷新和重写调用器
        refreshOverrideAndInvoker(providerURLs);
    }

    private void notifyRoutersChanged(Map<String, List<URL>> categoryUrls) {
        // 获取路由规则列表
        List<URL> routerURLs = categoryUrls.getOrDefault(ROUTERS_CATEGORY, Collections.emptyList());
        // 转换成路由器,如果存在,则追加路由器
        toRouters(routerURLs).ifPresent(this::addRouters);
    }

    private void notifyConfiguratorsChanged(Map<String, List<URL>> categoryUrls) {
        // 获取配置器列表
        List<URL> configuratorURLs = categoryUrls.getOrDefault(CONFIGURATORS_CATEGORY, Collections.emptyList());
        // 转换成配置器,为null则不替换
        this.configurators = Configurator.toConfigurators(configuratorURLs).orElse(this.configurators);
    }
    // -----------------------------------------------------------------------------------------------------------------

    private URL turnRegistryUrlToConsumerUrl(URL url) {
        return URLBuilder.from(url)
                .setPath(url.getServiceInterface())// 把路径设置为接口(原来是RegistryService)
                .clearParameters()// 删除所有参数
                .addParameters(queryMap)// 追加refer展开的参数
                .removeParameter(MONITOR_KEY)// 删除monitor参数
                .build();
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public void setRegistry(Registry registry) {
        this.registry = registry;
    }

    public Registry getRegistry() {
        return registry;
    }

    public boolean isShouldRegister() {
        return shouldRegister;
    }

    /**
     * 订阅
     *
     * @param consumerUrl consumer://
     */
    public void subscribe(URL consumerUrl) {
        setConsumerUrl(consumerUrl);
        /*
         * 配置中心相关
         */
        // 用于调用refreshInvokers
        // ConfigurationListener接口实现,内部引用List<Directory>
        // 追加到DynamicConfiguration,访问配置中心
        // 给此监听器追加要通知的目录,用于刷新当前目录实例的调用器
        CONSUMER_CONFIGURATION_LISTENER.addNotifyListener(this);
        // 引用配置监听器,ConfigurationListener接口实现
        // 监听key,调用refreshInvoker
        serviceConfigurationListener = new ReferenceConfigurationListener(this, consumerUrl);
        /*
         * 注册中心相关
         */
        // 当前对象实现了NotifyListener接口
        // 注册中心通知NotifyListener接口,当前对象转发到ConfigurationListener
        registry.subscribe(consumerUrl, this);
    }

    public void unSubscribe(URL url) {
        setConsumerUrl(null);
        CONSUMER_CONFIGURATION_LISTENER.removeNotifyListener(this);
        serviceConfigurationListener.stop();
        registry.unsubscribe(url, this);
    }

    /**
     * 根据协议或category参数,判断最终的category值
     * @param url 指定的url
     * @return
     */
    private String judgeCategory(URL url) {
        // 配置器
        if (UrlUtils.isConfigurator(url)) {
            return CONFIGURATORS_CATEGORY;
        }
        // 路由规则
        else if (UrlUtils.isRoute(url)) {
            return ROUTERS_CATEGORY;
        }
        // 提供者
        else if (UrlUtils.isProvider(url)) {
            return PROVIDERS_CATEGORY;
        }
        return "";
    }

    private void refreshOverrideAndInvoker(List<URL> providerURLs) {
        // mock zookeeper://xxx?mock=return null
        // 重写目录url的参数
        overrideDirectoryUrl();
        //
        refreshInvoker(providerURLs);
    }


    /**
     * 注册中心和配合中心的变化,都会触发此方法的调用
     * 因为注册中心配置的变化也要重新生成调用器
     *
     * 刷新调用器列表
     * @param providerURLs 真实的通信协议
     */
    private void refreshInvoker(List<URL> providerURLs) {
        Assert.notNull(providerURLs, "invokerUrls should not be null");
        // 没有可用的服务提供者
        if (providerURLs.size() == 1 && providerURLs.get(0) != null && EMPTY_PROTOCOL.equals(providerURLs.get(0).getProtocol())) {
            // 拒绝访问
            this.forbidden = true; // Forbid to access
            // 空调用器
            this.invokers = Collections.emptyList();
            // 设置
            routerChain.setInvokers(this.invokers);
            // 销毁所有调用器
            destroyAllInvokers(); // Close all invokers
        }
        // 列表个数不为1,或者首个url协议不为empty
        else {
            // 允许访问
            this.forbidden = false;
            //
            Map<String, Invoker<T>> oldUrlInvokerMap = this.urlInvokerMap; // local reference
            if (providerURLs == Collections.<URL>emptyList()) {
                providerURLs = new ArrayList<>();
            }
            // 给定列表为空,且当前存在可用调用器,追加到给定列表
            if (providerURLs.isEmpty() && this.cachedInvokerUrls != null) {
                providerURLs.addAll(this.cachedInvokerUrls);
            }
            // 给定列表非空,或缓存调用器为空,则追加缓存
            else {
                this.cachedInvokerUrls = new HashSet<>();
                this.cachedInvokerUrls.addAll(providerURLs);//Cached invoker urls, convenient for comparison
            }
            // 给定列表为空,则返回
            if (providerURLs.isEmpty()) {
                return;
            }
            // 转换成调用器
            Map<String, Invoker<T>> newUrlInvokerMap = toInvokers(providerURLs);// Translate url list to Invoker map

            /**
             * If the calculation is wrong, it is not processed.
             *
             * 1. The protocol configured by the client is inconsistent with the protocol of the server.
             *    eg: consumer protocol = dubbo, provider only has other protocol services(rest).
             * 2. The registration center is not robust and pushes illegal specification data.
             *
             */
            if (CollectionUtils.isEmptyMap(newUrlInvokerMap)) {
                logger.error(new IllegalStateException("urls to invokers error .invokerUrls.size :" + providerURLs.size() + ", invoker.size :0. urls :" + providerURLs
                        .toString()));
                return;
            }

            List<Invoker<T>> newInvokers = Collections.unmodifiableList(new ArrayList<>(newUrlInvokerMap.values()));
            // pre-route and build cache, notice that route cache should build on original Invoker list.
            // toMergeMethodInvokerMap() will wrap some invokers having different groups, those wrapped invokers not should be routed.
            routerChain.setInvokers(newInvokers);
            this.invokers = multiGroup ? toMergeInvokerList(newInvokers) : newInvokers;
            this.urlInvokerMap = newUrlInvokerMap;

            try {
                destroyUnusedInvokers(oldUrlInvokerMap, newUrlInvokerMap); // Close the unused Invoker
            } catch (Exception e) {
                logger.warn("destroyUnusedInvokers error. ", e);
            }
        }
    }

    private List<Invoker<T>> toMergeInvokerList(List<Invoker<T>> invokers) {
        List<Invoker<T>> mergedInvokers = new ArrayList<>();
        Map<String, List<Invoker<T>>> groupMap = new HashMap<>();
        for (Invoker<T> invoker : invokers) {
            String group = invoker.getUrl().getParameter(GROUP_KEY, "");
            groupMap.computeIfAbsent(group, k -> new ArrayList<>());
            groupMap.get(group).add(invoker);
        }

        if (groupMap.size() == 1) {
            mergedInvokers.addAll(groupMap.values().iterator().next());
        } else if (groupMap.size() > 1) {
            for (List<Invoker<T>> groupList : groupMap.values()) {
                StaticDirectory<T> staticDirectory = new StaticDirectory<>(groupList);
                staticDirectory.buildRouterChain();
                mergedInvokers.add(CLUSTER.join(staticDirectory));
            }
        } else {
            mergedInvokers = invokers;
        }
        return mergedInvokers;
    }

    /**
     * @param routerUrls
     * @return null : no routers ,do nothing
     * else :routers list
     */
    private Optional<List<Router>> toRouters(List<URL> routerUrls) {
        if (routerUrls == null || routerUrls.isEmpty()) {
            return Optional.empty();
        }

        List<Router> routers = new ArrayList<>();
        for (URL url : routerUrls) {
            // 跳过空协议,empty://
            if (EMPTY_PROTOCOL.equals(url.getProtocol())) {
                continue;
            }
            // 获取router参数,作为路由器类型
            String routerType = url.getParameter(ROUTER_KEY);
            // 变更协议
            // 如:route://host:port/path?router=app,变为:app//host:port/path?router=app
            if (routerType != null && routerType.length() > 0) {
                url = url.setProtocol(routerType);
            }
            try {
                Router router = ROUTER_FACTORY.getRouter(url);
                // 不包含,则添加
                if (!routers.contains(router)) {
                    routers.add(router);
                }
            } catch (Throwable t) {
                logger.error("convert router url to router error, url: " + url, t);
            }
        }

        return Optional.of(routers);
    }

    /**
     * Turn urls into invokers, and if url has been refer, will not re-reference.
     *
     * @param urls
     * @return invokers
     */
    private Map<String, Invoker<T>> toInvokers(List<URL> urls) {
        Map<String, Invoker<T>> newUrlInvokerMap = new HashMap<>();
        if (urls == null || urls.isEmpty()) {
            return newUrlInvokerMap;
        }
        Set<String> keys = new HashSet<>();
        String queryProtocols = this.queryMap.get(PROTOCOL_KEY);
        for (URL providerUrl : urls) {
            // If protocol is configured at the reference side, only the matching protocol is selected
            if (queryProtocols != null && queryProtocols.length() > 0) {
                boolean accept = false;
                String[] acceptProtocols = queryProtocols.split(",");
                for (String acceptProtocol : acceptProtocols) {
                    if (providerUrl.getProtocol().equals(acceptProtocol)) {
                        accept = true;
                        break;
                    }
                }
                if (!accept) {
                    continue;
                }
            }
            if (EMPTY_PROTOCOL.equals(providerUrl.getProtocol())) {
                continue;
            }
            if (!ExtensionLoader.getExtensionLoader(Protocol.class).hasExtension(providerUrl.getProtocol())) {
                logger.error(new IllegalStateException("Unsupported protocol " + providerUrl.getProtocol() +
                        " in notified url: " + providerUrl + " from registry " + getUrl().getAddress() +
                        " to consumer " + NetUtils.getLocalHost() + ", supported protocol: " +
                        ExtensionLoader.getExtensionLoader(Protocol.class).getSupportedExtensions()));
                continue;
            }
            URL url = mergeUrl(providerUrl);

            String key = url.toFullString(); // The parameter urls are sorted
            if (keys.contains(key)) { // Repeated url
                continue;
            }
            keys.add(key);
            // Cache key is url that does not merge with consumer side parameters, regardless of how the consumer combines parameters, if the server url changes, then refer again
            Map<String, Invoker<T>> localUrlInvokerMap = this.urlInvokerMap; // local reference
            Invoker<T> invoker = localUrlInvokerMap == null ? null : localUrlInvokerMap.get(key);
            if (invoker == null) { // Not in the cache, refer again
                try {
                    boolean enabled = true;
                    if (url.hasParameter(DISABLED_KEY)) {
                        enabled = !url.getParameter(DISABLED_KEY, false);
                    } else {
                        enabled = url.getParameter(ENABLED_KEY, true);
                    }
                    if (enabled) {
                        Invoker<T> refer = protocol.refer(serviceType, url);
                        invoker = new InvokerDelegate<>(refer, url, providerUrl);
                    }
                } catch (Throwable t) {
                    logger.error("Failed to refer invoker for interface:" + serviceType + ",url:(" + url + ")" + t.getMessage(), t);
                }
                if (invoker != null) { // Put new invoker in cache
                    newUrlInvokerMap.put(key, invoker);
                }
            } else {
                newUrlInvokerMap.put(key, invoker);
            }
        }
        keys.clear();
        return newUrlInvokerMap;
    }

    /**
     * Merge url parameters. the order is: override > -D >Consumer > Provider
     *
     * @param providerUrl
     * @return
     */
    private URL mergeUrl(URL providerUrl) {
        providerUrl = ClusterUtils.mergeUrl(providerUrl, queryMap); // Merge the consumer side parameters

        providerUrl = overrideWithConfigurator(providerUrl);

        providerUrl = providerUrl.addParameter(Constants.CHECK_KEY, String.valueOf(false)); // Do not check whether the connection is successful or not, always create Invoker!

        // The combination of directoryUrl and override is at the end of notify, which can't be handled here
        this.overrideDirectoryUrl = this.overrideDirectoryUrl.addParametersIfAbsent(providerUrl.getParameters()); // Merge the provider side parameters

        if ((providerUrl.getPath() == null || providerUrl.getPath()
                .length() == 0) && DUBBO_PROTOCOL.equals(providerUrl.getProtocol())) { // Compatible version 1.0
            //fix by tony.chenl DUBBO-44
            String path = directoryUrl.getParameter(INTERFACE_KEY);
            if (path != null) {
                int i = path.indexOf('/');
                if (i >= 0) {
                    path = path.substring(i + 1);
                }
                i = path.lastIndexOf(':');
                if (i >= 0) {
                    path = path.substring(0, i);
                }
                providerUrl = providerUrl.setPath(path);
            }
        }
        return providerUrl;
    }

    /**
     * 使用配置器重写???
     *
     * @param providerUrl
     * @return
     */
    private URL overrideWithConfigurator(URL providerUrl) {
        // override url with configurator from "override://" URL for dubbo 2.6 and before
        // 使用当前引用的配置器,重写提供者url
        // 这是注册中心配置的配置器
        providerUrl = overrideWithConfigurators(this.configurators, providerUrl);

        // override url with configurator from configurator from "app-name.configurators"
        // 消费者配置监听器,这是配置中心配置的配置器
        List<Configurator> configurators = CONSUMER_CONFIGURATION_LISTENER.getConfigurators();
        // 使用配置器重写
        providerUrl = overrideWithConfigurators(configurators, providerUrl);

        // override url with configurator from configurators from "service-name.configurators"
        // 使用配置器重写,这是配置中心配置的配置器
        if (serviceConfigurationListener != null) {
            providerUrl = overrideWithConfigurators(serviceConfigurationListener.getConfigurators(), providerUrl);
        }

        return providerUrl;
    }

    /**
     * 使用配置器重写???
     *
     * @param configurators
     * @param url
     * @return
     */
    private URL overrideWithConfigurators(List<Configurator> configurators, URL url) {
        // 列表非空
        if (CollectionUtils.isNotEmpty(configurators)) {
            // 遍历配置器
            for (Configurator configurator : configurators) {
                // 使用配置器配置url
                url = configurator.configure(url);
            }
        }
        return url;
    }

    /**
     * Close all invokers
     */
    private void destroyAllInvokers() {
        Map<String, Invoker<T>> localUrlInvokerMap = this.urlInvokerMap; // local reference
        if (localUrlInvokerMap != null) {
            for (Invoker<T> invoker : new ArrayList<>(localUrlInvokerMap.values())) {
                try {
                    invoker.destroy();
                } catch (Throwable t) {
                    logger.warn("Failed to destroy service " + serviceKey + " to provider " + invoker.getUrl(), t);
                }
            }
            localUrlInvokerMap.clear();
        }
        invokers = null;
    }

    /**
     * Check whether the invoker in the cache needs to be destroyed
     * If set attribute of url: refer.autodestroy=false, the invokers will only increase without decreasing,there may be a refer leak
     *
     * @param oldUrlInvokerMap
     * @param newUrlInvokerMap
     */
    private void destroyUnusedInvokers(Map<String, Invoker<T>> oldUrlInvokerMap, Map<String, Invoker<T>> newUrlInvokerMap) {
        if (newUrlInvokerMap == null || newUrlInvokerMap.size() == 0) {
            destroyAllInvokers();
            return;
        }
        // check deleted invoker
        List<String> deleted = null;
        if (oldUrlInvokerMap != null) {
            Collection<Invoker<T>> newInvokers = newUrlInvokerMap.values();
            for (Map.Entry<String, Invoker<T>> entry : oldUrlInvokerMap.entrySet()) {
                if (!newInvokers.contains(entry.getValue())) {
                    if (deleted == null) {
                        deleted = new ArrayList<>();
                    }
                    deleted.add(entry.getKey());
                }
            }
        }

        if (deleted != null) {
            for (String url : deleted) {
                if (url != null) {
                    Invoker<T> invoker = oldUrlInvokerMap.remove(url);
                    if (invoker != null) {
                        try {
                            invoker.destroy();
                            if (logger.isDebugEnabled()) {
                                logger.debug("destroy invoker[" + invoker.getUrl() + "] success. ");
                            }
                        } catch (Exception e) {
                            logger.warn("destroy invoker[" + invoker.getUrl() + "] failed. " + e.getMessage(), e);
                        }
                    }
                }
            }
        }
    }


    public URL getRegisteredConsumerUrl() {
        return registeredConsumerUrl;
    }

    public void setRegisteredConsumerUrl(URL url) {
        if (!shouldSimplified) {
            this.registeredConsumerUrl = url.addParameters(CATEGORY_KEY, CONSUMERS_CATEGORY, CHECK_KEY,
                    String.valueOf(false));
        } else {
            this.registeredConsumerUrl = URL.valueOf(url, DEFAULT_REGISTER_CONSUMER_KEYS, null).addParameters(
                    CATEGORY_KEY, CONSUMERS_CATEGORY, CHECK_KEY, String.valueOf(false));
        }
    }


    public void buildRouterChain(URL url) {
        RouterChain<T> chain = RouterChain.buildChain(url);
        this.setRouterChain(chain);
    }

    /**
     * Haomin: added for test purpose
     */
    public Map<String, Invoker<T>> getUrlInvokerMap() {
        return urlInvokerMap;
    }

    public List<Invoker<T>> getInvokers() {
        return invokers;
    }

    private boolean isValidCategory(URL url) {
        // 获取category,默认为providers
        String category = url.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);
        // 提供者,路由器,配置器,动态配置器,应用动态配置器
        if ((ROUTERS_CATEGORY.equals(category) || ROUTE_PROTOCOL.equals(url.getProtocol()))// routers类别或route协议
                || PROVIDERS_CATEGORY.equals(category)// providers类别
                || CONFIGURATORS_CATEGORY.equals(category)// configurators类别
                || DYNAMIC_CONFIGURATORS_CATEGORY.equals(category)// dynamicconfigurators类别
                || APP_DYNAMIC_CONFIGURATORS_CATEGORY.equals(category))// appdynamicconfigrators类别
        {
            return true;
        }
        logger.warn("Unsupported category " + category + " in notified url: " + url + " from registry " +
                getUrl().getAddress() + " to consumer " + NetUtils.getLocalHost());
        return false;
    }

    private boolean isNotCompatibleFor26x(URL url) {
        return StringUtils.isEmpty(url.getParameter(COMPATIBLE_CONFIG_KEY));
    }

    private void overrideDirectoryUrl() {
        // merge override parameters
        // 重写后的目录url
        this.overrideDirectoryUrl = directoryUrl;
        // 保存配置器
        List<Configurator> localConfigurators = this.configurators; // local reference
        // 调用配置器,对目录url进行重写
        doOverrideUrl(localConfigurators);
        //
        List<Configurator> localAppDynamicConfigurators = CONSUMER_CONFIGURATION_LISTENER.getConfigurators(); // local reference
        // 调用配置器,对目录url进行重写
        doOverrideUrl(localAppDynamicConfigurators);
        // 存在服务配置监听器
        if (serviceConfigurationListener != null) {
            List<Configurator> localDynamicConfigurators = serviceConfigurationListener.getConfigurators(); // local reference
            // 重写
            doOverrideUrl(localDynamicConfigurators);
        }
    }

    private void doOverrideUrl(List<Configurator> configurators) {
        if (CollectionUtils.isNotEmpty(configurators)) {
            for (Configurator configurator : configurators) {
                this.overrideDirectoryUrl = configurator.configure(overrideDirectoryUrl);
            }
        }
    }

    /**
     * 注册中心目录的调用器代理
     * The delegate class, which is mainly used to store the URL address sent by the registry,and can be reassembled on the basis of providerURL queryMap overrideMap for re-refer.
     *
     * @param <T>
     */
    private static class InvokerDelegate<T> extends InvokerWrapper<T> {
        private URL providerUrl;

        /**
         * 构造方法
         *
         * @param invoker
         * @param url
         * @param providerUrl
         */
        public InvokerDelegate(Invoker<T> invoker, URL url, URL providerUrl) {
            super(invoker, url);
            this.providerUrl = providerUrl;
        }

        public URL getProviderUrl() {
            return providerUrl;
        }
    }

    /**
     * 某个引用的配置监听器
     */
    private static class ReferenceConfigurationListener extends AbstractConfiguratorListener {
        /**
         * 目录
         */
        private RegistryDirectory directory;
        /**
         * 信息
         */
        private URL url;

        /**
         * 构造方法
         *
         * @param directory 目录
         * @param url       信息???
         */
        ReferenceConfigurationListener(RegistryDirectory directory, URL url) {
            this.directory = directory;
            this.url = url;
            // {interface}:[version]:[group].configurators
            String key = DynamicConfiguration.getRuleKey(url) + CONFIGURATORS_SUFFIX;
            // 追加监听器,同时拉取规则,解析为配置器列表
            this.initWith(key);
        }

        void stop() {
            this.stopListen(DynamicConfiguration.getRuleKey(url) + CONFIGURATORS_SUFFIX);
        }

        /**
         * 配置器列表变化后,调用此方法,以空url列表刷新调用器
         */
        @Override
        protected void notifyOverrides() {
            // to notify configurator/router changes
            directory.refreshInvoker(Collections.emptyList());
        }
    }

    /**
     * 全局消费者配置监听器
     */
    private static class ConsumerConfigurationListener extends AbstractConfiguratorListener {

        /**
         * 注册中心目录列表
         */
        List<RegistryDirectory> directories = new ArrayList<>();

        /**
         * 构造方法
         */
        ConsumerConfigurationListener() {
            // 监听指定key的变化,同时进行规则的首次获取(会更新配置器列表)
            // 应用名称.configurators
            this.initWith(ApplicationModel.getName() + CONFIGURATORS_SUFFIX);
        }

        void addNotifyListener(RegistryDirectory listener) {
            this.directories.add(listener);
        }

        void removeNotifyListener(RegistryDirectory listener) {
            this.directories.remove(listener);
        }

        /**
         * 配置器列表变化后,调用此方法,以空列表刷新调用器
         */
        @Override
        protected void notifyOverrides() {
            // 每次配置变更引发的配置器列表变化,
            // 都会导致注册中心目录以空列表刷新调用器
            for (RegistryDirectory directory : this.directories) {
                directory.refreshInvoker(Collections.emptyList());
            }
        }
    }

}
