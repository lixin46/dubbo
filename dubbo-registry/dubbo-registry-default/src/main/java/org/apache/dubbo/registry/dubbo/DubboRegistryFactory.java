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
package org.apache.dubbo.registry.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.registry.integration.RegistryDirectory;
import org.apache.dubbo.registry.support.AbstractRegistryFactory;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.RouterChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.CALLBACK_INSTANCES_LIMIT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.LAZY_CONNECT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.common.constants.RemotingConstants.BACKUP_KEY;
import static org.apache.dubbo.registry.Constants.CONSUMER_PROTOCOL;
import static org.apache.dubbo.remoting.Constants.CONNECT_TIMEOUT_KEY;
import static org.apache.dubbo.remoting.Constants.RECONNECT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.CLUSTER_STICKY_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

/**
 * 组件名称为dubbo
 * 注册中心工厂的默认实现
 */
public class DubboRegistryFactory extends AbstractRegistryFactory {

    private static URL getRegistryURL(URL url) {
        return URLBuilder.from(url)
                .setPath(RegistryService.class.getName())// 路径为注册中心服务类名
                .removeParameter(EXPORT_KEY)// 删除export
                .removeParameter(REFER_KEY)// 删除refer
                .addParameter(INTERFACE_KEY, RegistryService.class.getName())// 添加interface
                .addParameter(CLUSTER_STICKY_KEY, "true")// 添加sticky=true
                .addParameter(LAZY_CONNECT_KEY, "true")// 添加lazy=true
                .addParameter(RECONNECT_KEY, "false")// 添加reconnect=false
                .addParameterIfAbsent(TIMEOUT_KEY, "10000")// 不存在则添加timeout=10000
                .addParameterIfAbsent(CALLBACK_INSTANCES_LIMIT_KEY, "10000")// 不存在则添加callbacks=10000
                .addParameterIfAbsent(CONNECT_TIMEOUT_KEY, "10000")// 不存在则添加connect.timeout=10000
                .addParameter(METHODS_KEY, StringUtils.join(new HashSet<>(Arrays.asList(Wrapper.getWrapper(RegistryService.class).getDeclaredMethodNames())), ","))
                .addParameter("subscribe.1.callback", "true")
                .addParameter("unsubscribe.1.callback", "false")
                .build();
    }
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * 协议
     */
    private Protocol protocol;
    /**
     * 代理工厂
     */
    private ProxyFactory proxyFactory;
    /**
     * 集群
     */
    private Cluster cluster;

    /**
     * ExtensionLoader注入
     *
     * @param protocol 协议
     */
    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    /**
     * ExtensionLoader注入
     *
     * @param proxyFactory 代理工厂
     */
    public void setProxyFactory(ProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    /**
     * ExtensionLoader注入
     *
     * @param cluster 集群
     */
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public Registry createRegistry(URL registryUrl) {
        // 构建注册中心url
        registryUrl = getRegistryURL(registryUrl);
        List<URL> urls = new ArrayList<>();
        // url不带backup参数
        urls.add(registryUrl.removeParameter(BACKUP_KEY));
        // 获取backup参数值
        String backup = registryUrl.getParameter(BACKUP_KEY);
        // 存在
        if (backup != null && backup.length() > 0) {
            // 逗号拆分
            String[] addresses = COMMA_SPLIT_PATTERN.split(backup);
            for (String address : addresses) {
                // 变更地址后追加
                urls.add(registryUrl.setAddress(address));
            }
        }
        // 添加interface参数和refer参数,refer引用当前url的参数
        URL directoryUrl = registryUrl.addParameter(INTERFACE_KEY, RegistryService.class.getName())
                .addParameterAndEncoded(REFER_KEY, registryUrl.toParameterString());
        // 注册中心目录
        RegistryDirectory<RegistryService> directory = new RegistryDirectory<>(RegistryService.class, directoryUrl);
        // 集群加入???
        Invoker<RegistryService> registryInvoker = cluster.join(directory);
        // 获取代理对象
        RegistryService registryService = proxyFactory.getProxy(registryInvoker);
        DubboRegistry registry = new DubboRegistry(registryInvoker, registryService);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        directory.setRouterChain(RouterChain.buildChain(registryUrl));
        directory.notify(urls);
        URL consumerUrl = new URL(CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, RegistryService.class.getName(), registryUrl.getParameters());
        directory.subscribe(consumerUrl);
        return registry;
    }
}
