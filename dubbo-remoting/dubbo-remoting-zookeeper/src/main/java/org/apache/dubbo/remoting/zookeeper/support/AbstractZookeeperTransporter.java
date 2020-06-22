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
package org.apache.dubbo.remoting.zookeeper.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.RemotingConstants;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * AbstractZookeeperTransporter is abstract implements of ZookeeperTransporter.
 * <p>
 * If you want to extends this, implements createZookeeperClient.
 */

/**
 * zk传输器抽象实现
 */
public abstract class AbstractZookeeperTransporter implements ZookeeperTransporter {

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperTransporter.class);
    /**
     * 客户端缓存
     * key为ip:port,value为客户端实例
     */
    private final Map<String, ZookeeperClient> zookeeperClientMap = new ConcurrentHashMap<>();

    /**
     * ZookeeperTransporter接口实现
     * share connnect for registry, metadata, etc..
     * <p>
     * Make sure the connection is connected.
     *
     * @param registryUrl 注册中心信息
     * @return
     */
    @Override
    public ZookeeperClient connect(URL registryUrl) {
        ZookeeperClient zookeeperClient;
        // address format: {[username:password@]address}
        // 获取备用地址,包含主要地址
        List<String> addressList = getURLBackupAddress(registryUrl);
        // The field define the zookeeper server , including protocol, host, port, username, password
        // 抓取和更新客户端缓存,且连接完毕,则返回
        // 查找任意可用客户端,并把列表全部映射到这个客户端
        if ((zookeeperClient = fetchAndUpdateZookeeperClientCache(addressList)) != null && zookeeperClient.isConnected()) {
            logger.info("find valid zookeeper client from the cache for address: " + registryUrl);
            return zookeeperClient;
        }
        // avoid creating too many connections， so add lock
        synchronized (zookeeperClientMap) {
            // 加锁读
            if ((zookeeperClient = fetchAndUpdateZookeeperClientCache(addressList)) != null && zookeeperClient.isConnected()) {
                logger.info("find valid zookeeper client from the cache for address: " + registryUrl);
                return zookeeperClient;
            }
            // 创建新的客户端
            zookeeperClient = createZookeeperClient(registryUrl);
            logger.info("No valid zookeeper client found from cache, therefore create a new client for url. " + registryUrl);
            // 映射全部ip:port
            writeToClientMap(addressList, zookeeperClient);
        }
        return zookeeperClient;
    }

    /**
     * 创建zk客户端
     * @param url 指定的信息,比如:zookeeper://127.0.0.1:2181/org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter
     * @return
     */
    protected abstract ZookeeperClient createZookeeperClient(URL url);

    /**
     * get the ZookeeperClient from cache, the ZookeeperClient must be connected.
     * <p>
     * It is not private method for unit test.
     *
     * @param addressList
     * @return
     */
    ZookeeperClient fetchAndUpdateZookeeperClientCache(List<String> addressList) {
        ZookeeperClient zookeeperClient = null;
        for (String address : addressList) {
            // 缓存存在且已连接,则退出循环
            if ((zookeeperClient = zookeeperClientMap.get(address)) != null && zookeeperClient.isConnected()) {
                break;
            }
        }
        if (zookeeperClient != null && zookeeperClient.isConnected()) {
            // 写入缓存
            // 把整个列表都映射到这个客户端
            writeToClientMap(addressList, zookeeperClient);
        }
        return zookeeperClient;
    }

    /**
     * 获取备用列表
     * @param url 指定的url,比如:zookeeper://127.0.0.1:2181?127.0.0.1:8989,127.0.0.1:9999
     * @return 备用列表
     */
    List<String> getURLBackupAddress(URL url) {
        List<String> addressList = new ArrayList<String>();
        // ip:port
        addressList.add(url.getAddress());
        // backup参数值,逗号拆分
        addressList.addAll(url.getParameter(RemotingConstants.BACKUP_KEY, Collections.EMPTY_LIST));

        // 认证前缀,包含用户名:密码
        String authPrefix = null;
        // 用户名非空
        if (StringUtils.isNotEmpty(url.getUsername())) {
            StringBuilder buf = new StringBuilder();
            buf.append(url.getUsername());
            // 密码非空
            if (StringUtils.isNotEmpty(url.getPassword())) {
                buf.append(":");
                buf.append(url.getPassword());
            }
            buf.append("@");
            // 认证前缀
            authPrefix = buf.toString();
        }

        // 认证前缀非空
        if (StringUtils.isNotEmpty(authPrefix)) {
            List<String> authedAddressList = new ArrayList<>(addressList.size());
            // 遍历地址
            for (String addr : addressList) {
                // 追加认证前缀后保存
                authedAddressList.add(authPrefix + addr);
            }
            // 返回
            return authedAddressList;
        }
        return addressList;
    }

    /**
     * write address-ZookeeperClient relationship to Map
     *
     * @param addressList
     * @param zookeeperClient
     */
    void writeToClientMap(List<String> addressList, ZookeeperClient zookeeperClient) {
        for (String address : addressList) {
            zookeeperClientMap.put(address, zookeeperClient);
        }
    }

    /**
     * redefine the url for zookeeper. just keep protocol, username, password, host, port, and individual parameter.
     *
     * @param url
     * @return
     */
    URL toClientURL(URL url) {
        Map<String, String> parameterMap = new HashMap<>();
        // for CuratorZookeeperClient
        if (url.getParameter(TIMEOUT_KEY) != null) {
            parameterMap.put(TIMEOUT_KEY, url.getParameter(TIMEOUT_KEY));
        }
        if (url.getParameter(RemotingConstants.BACKUP_KEY) != null) {
            parameterMap.put(RemotingConstants.BACKUP_KEY, url.getParameter(RemotingConstants.BACKUP_KEY));
        }

        return new URL(url.getProtocol(), url.getUsername(), url.getPassword(), url.getHost(), url.getPort(),
                ZookeeperTransporter.class.getName(), parameterMap);
    }

    /**
     * for unit test
     *
     * @return
     */
    Map<String, ZookeeperClient> getZookeeperClientMap() {
        return zookeeperClientMap;
    }
}
