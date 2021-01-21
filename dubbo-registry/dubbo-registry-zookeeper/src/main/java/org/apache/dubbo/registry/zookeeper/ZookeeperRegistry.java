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
package org.apache.dubbo.registry.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.URLStrParser;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.zookeeper.ChildListener;
import org.apache.dubbo.remoting.zookeeper.StateListener;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;
import org.apache.dubbo.rpc.RpcException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_SEPARATOR_ENCODED;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONSUMERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DEFAULT_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTERS_CATEGORY;

/**
 * ZookeeperRegistry
 * 基于zk实现的注册中心服务
 */
public class ZookeeperRegistry extends FailbackRegistry {

    private final static Logger logger = LoggerFactory.getLogger(ZookeeperRegistry.class);

    private final static String DEFAULT_ROOT = "dubbo";

    /**
     * 根路径,也就是分组
     * 默认为/dubbo
     */
    private final String root;

    private final Set<String> anyServices = new ConcurrentHashSet<>();

    /**
     * zk的监听器映射
     * 一级key为订阅的url,二级key为订阅的监听器,value为zk的子节点变化监听器
     */
    private final ConcurrentMap<URL, ConcurrentMap<NotifyListener, ChildListener>> zkListeners = new ConcurrentHashMap<>();

    private final ZookeeperClient zkClient;

    /**
     * 构造方法
     * 创建zk客户端并持有
     *
     * @param registryUrl          指定的注册中心url
     * @param zookeeperTransporter 传输器
     */
    public ZookeeperRegistry(URL registryUrl, ZookeeperTransporter zookeeperTransporter) {
        super(registryUrl);
        // 任意主机
        if (registryUrl.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        // 获取group参数,默认为dubbo
        String group = registryUrl.getParameter(GROUP_KEY, DEFAULT_ROOT);
        // 分组不以/开头,则追加
        if (!group.startsWith(PATH_SEPARATOR)) {
            group = PATH_SEPARATOR + group;
        }
        this.root = group;
        // 连接zk,获取客户端
        zkClient = zookeeperTransporter.connect(registryUrl);
        // 追加状态监听器
        // 状态发生变化时,调用监听器的stateChanged()方法通知变更后的状态
        StateListener stateListener = (state) -> {
            // 重新连接完成
            if (state == StateListener.RECONNECTED) {
                logger.warn("Trying to fetch the latest urls, in case there're provider changes during connection loss.\n" +
                        " Since ephemeral ZNode will not get deleted for a connection lose, " +
                        "there's no need to re-register url of this instance.");

                // 抓取最新地址
                ZookeeperRegistry.this.fetchLatestAddresses();
            }
            // 新会话创建
            else if (state == StateListener.NEW_SESSION_CREATED) {
                logger.warn("Trying to re-register urls and re-subscribe listeners of this instance to registry...");
                try {
                    // 发现???
                    ZookeeperRegistry.this.recover();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
            // 会话丢失,则警告日志
            else if (state == StateListener.SESSION_LOST) {
                logger.warn("Url of this instance will be deleted from registry soon. " +
                        "Dubbo client will try to re-register once a new session is created.");
            }
            // 挂起???
            else if (state == StateListener.SUSPENDED) {

            }
            // 首次连接完成???
            else if (state == StateListener.CONNECTED) {

            }
        };
        // 追加状态监听器
        zkClient.addStateListener(stateListener);
    }
    // -----------------------------------------------------------------------------------------------------------------
    // Node接口实现

    @Override
    public boolean isAvailable() {
        return zkClient.isConnected();
    }

    @Override
    public void destroy() {
        super.destroy();
        try {
            zkClient.close();
        } catch (Exception e) {
            logger.warn("Failed to close zookeeper client " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
    // -----------------------------------------------------------------------------------------------------------------
    // FailbackRegistry抽象类实现

    @Override
    public void doRegister(URL url) {
        try {
            // /group/interface/category/url
            String urlPath = toUrlPath(url);
            // 获取dynamic,默认为true
            // 代表zk节点是否为临时
            boolean dynamic = url.getParameter(DYNAMIC_KEY, true);
            // 创建zk节点
            zkClient.create(urlPath, dynamic);
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doUnregister(URL url) {
        try {
            String urlPath = toUrlPath(url);
            zkClient.delete(urlPath);
        } catch (Throwable e) {
            throw new RpcException("Failed to unregister " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * @param consumerUrl consumer://host:port/path?parameters,
     *                    协议不重要,重要的是url中的group,interface,category参数
     * @param listener
     */
    @Override
    public void doSubscribe(final URL consumerUrl, final NotifyListener listener) {
        try {
            // "*"
            if (ANY_VALUE.equals(consumerUrl.getServiceInterface())) {
                // 根路径,即分组
                String root = toRootPath();
                // 获取监听url对应的以你干涉
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.computeIfAbsent(consumerUrl, k -> new ConcurrentHashMap<>());
                // 获取NotifyListener对应的zk子节点变化监听器
                ChildListener zkListener = listeners.computeIfAbsent(listener, k -> (parentPath, currentChilds) -> {

                    // 子节点监听器的回调逻辑
                    for (String child : currentChilds) {
                        child = URL.decode(child);
                        // 不包含节点
                        if (!anyServices.contains(child)) {
                            // 添加
                            anyServices.add(child);
                            URL url = consumerUrl.setPath(child).addParameters(INTERFACE_KEY, child,
                                    Constants.CHECK_KEY, String.valueOf(false));
                            // 订阅url
                            subscribe(url, k);
                        }
                    }
                });
                zkClient.create(root, false);
                // 监听之后的最新子列表
                List<String> services = zkClient.addChildListener(root, zkListener);

                /*
                 * 执行一次与监听器相同的逻辑
                 */
                if (CollectionUtils.isNotEmpty(services)) {
                    for (String service : services) {
                        service = URL.decode(service);
                        anyServices.add(service);
                        subscribe(consumerUrl.setPath(service).addParameters(INTERFACE_KEY, service,
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            }
            // 特定接口
            else {
                List<URL> urls = new ArrayList<>();
                // 提取category参数,逗号拆分
                // 与group和interface一起,组成完整路径
                String[] categoriesPath = toCategoriesPath(consumerUrl);
                // 遍历路径
                for (String path : categoriesPath) {
                    // 获取url对应的监听器映射
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.computeIfAbsent(consumerUrl, k -> new ConcurrentHashMap<>());
                    // 获取监听器对应的子列表监听器
                    ChildListener zkListener = listeners.computeIfAbsent(listener, k -> (parentPath, currentChilds) -> ZookeeperRegistry.this.notify(consumerUrl, k, toUrlsWithEmpty(consumerUrl, parentPath, currentChilds)));
                    // 确保路径节点存在
                    zkClient.create(path, false);
                    // 追加监听器,获取监听之后的最新列表
                    // 获取到的是叶子节点名称,也就是url
                    List<String> children = zkClient.addChildListener(path, zkListener);
                    // 存在子节点
                    if (children != null) {
                        urls.addAll(toUrlsWithEmpty(consumerUrl, path, children));
                    }
                }
                // 整合所有类别的url后
                // 一次性通知
                notify(consumerUrl, listener, urls);
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + consumerUrl + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
        if (listeners != null) {
            ChildListener zkListener = listeners.get(listener);
            if (zkListener != null) {
                if (ANY_VALUE.equals(url.getServiceInterface())) {
                    String root = toRootPath();
                    zkClient.removeChildListener(root, zkListener);
                } else {
                    for (String path : toCategoriesPath(url)) {
                        zkClient.removeChildListener(path, zkListener);
                    }
                }
            }
        }
    }

    /**
     * 重写父类方法
     *
     * @param url 指定的url
     * @return
     */
    @Override
    public List<URL> lookup(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("lookup url == null");
        }
        try {
            List<String> providers = new ArrayList<>();
            // 分类路径
            String[] categoriesPath = toCategoriesPath(url);
            // 遍历
            for (String path : categoriesPath) {
                // 获取子节点名称
                List<String> children = zkClient.getChildren(path);
                // 存在,则追加
                if (children != null) {
                    providers.addAll(children);
                }
            }
            return toUrlsWithoutEmpty(url, providers);
        } catch (Throwable e) {
            throw new RpcException("Failed to lookup " + url + " from zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }
    // -----------------------------------------------------------------------------------------------------------------

    private String toRootDir() {
        if (root.equals(PATH_SEPARATOR)) {
            return root;
        }
        return root + PATH_SEPARATOR;
    }

    /**
     * @return /group
     */
    private String toRootPath() {
        return root;
    }

    /**
     * @param url
     * @return /group/interface
     */
    private String toServicePath(URL url) {
        // 接口名
        String interfaceName = url.getServiceInterface();
        // 接口名为*,则返回根路径,就是分组名称
        if (ANY_VALUE.equals(interfaceName)) {
            return toRootPath();
        }
        // 分组/接口名
        return toRootDir() + URL.encode(interfaceName);
    }

    private String[] toCategoriesPath(URL url) {
        String[] categories;
        // category="*"
        if (ANY_VALUE.equals(url.getParameter(CATEGORY_KEY))) {
            //
            categories = new String[]{
                    PROVIDERS_CATEGORY,// providers
                    CONSUMERS_CATEGORY,// consumers
                    ROUTERS_CATEGORY,// routers
                    CONFIGURATORS_CATEGORY// configurators
            };
        }
        // 其他
        else {
            // 默认为providers
            categories = url.getParameter(CATEGORY_KEY, new String[]{DEFAULT_CATEGORY});
        }
        String[] paths = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            String servicePath = toServicePath(url);
            // 服务分组(默认为dubbo)/服务接口/分类
            paths[i] = servicePath + PATH_SEPARATOR + categories[i];
        }
        return paths;
    }

    /**
     * @param url url
     * @return /group/interface/category
     */
    private String toCategoryPath(URL url) {
        String servicePath = toServicePath(url);
        return servicePath + PATH_SEPARATOR + url.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);
    }

    /**
     * @param url
     * @return /group/interface/category/url
     */
    private String toUrlPath(URL url) {
        // /group/interface/category
        String parentPath = toCategoryPath(url);
        // protocol://username:password@host:port/path
        String childNode = url.toFullString();
        // group/interface/side/url
        // /分组(可选)/接口名(可选)/providers/encode(protocol://username:password@host:port/path)
        // categoryPath/fullUrl
        return parentPath + PATH_SEPARATOR + URL.encode(childNode);
    }

    private List<URL> toUrlsWithoutEmpty(URL consumer, List<String> providers) {
        List<URL> urls = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(providers)) {
            // 遍历提供者
            for (String provider : providers) {
                // 包含协议
                if (provider.contains(PROTOCOL_SEPARATOR_ENCODED)) {
                    // 解析url
                    URL url = URLStrParser.parseEncodedStr(provider);
                    // 匹配则追加
                    if (UrlUtils.isMatch(consumer, url)) {
                        urls.add(url);
                    }
                }
            }
        }
        return urls;
    }

    /**
     * @param consumer  ???
     * @param path      zk节点路径
     * @param providers 子节点名称
     * @return
     */
    private List<URL> toUrlsWithEmpty(URL consumer, String path, List<String> providers) {
        // 转换成url
        List<URL> urls = toUrlsWithoutEmpty(consumer, providers);
        // 为空,则构建一个empty://开头的url,追加到列表中
        if (urls == null || urls.isEmpty()) {
            int i = path.lastIndexOf(PATH_SEPARATOR);
            String category = i < 0 ? path : path.substring(i + 1);
            URL empty = URLBuilder.from(consumer)
                    .setProtocol(EMPTY_PROTOCOL)// empty://
                    .addParameter(CATEGORY_KEY, category)
                    .build();
            urls.add(empty);
        }
        return urls;
    }

    /**
     * When zookeeper connection recovered from a connection loss, it need to fetch the latest provider list.
     * re-register watcher is only a side effect and is not mandate.
     */
    private void fetchLatestAddresses() {
        // subscribe
        // 复制订阅映射
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<URL, Set<NotifyListener>>(getSubscribed());
        // 非空
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Fetching the latest urls of " + recoverSubscribed.keySet());
            }
            // 遍历键值对
            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                // 监听的key
                URL url = entry.getKey();
                // 遍历监听器
                for (NotifyListener listener : entry.getValue()) {
                    // 添加失败订阅
                    addFailedSubscribed(url, listener);
                }
            }
        }
    }

}
