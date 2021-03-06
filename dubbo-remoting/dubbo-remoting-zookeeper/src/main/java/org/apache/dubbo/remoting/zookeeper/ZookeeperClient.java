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
package org.apache.dubbo.remoting.zookeeper;

import org.apache.dubbo.common.URL;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * zk客户端,对应zk操作
 */
public interface ZookeeperClient {

    /**
     * 创建节点
     * @param path 节点路径
     * @param ephemeral 是否临时节点
     */
    void create(String path, boolean ephemeral);

    /**
     * 创建节点
     * @param path 节点路径
     * @param content 节点内容
     * @param ephemeral 是否临时节点
     */
    void create(String path, String content, boolean ephemeral);

    /**
     * 删除节点
     * @param path 节点路径
     */
    void delete(String path);

    /**
     *
     * @param path 节点路径
     * @return 节点内容
     */
    String getContent(String path);

    /**
     * 获取指定节点的子节点名称
     * @param path 节点类路径
     * @return 子节点名称
     */
    List<String> getChildren(String path);

    /**
     * 追加子节点变化监听器
     * @param path 节点路径
     * @param listener 监听器
     * @return 追加监听器之后的最新的子列表
     */
    List<String> addChildListener(String path, ChildListener listener);

    /**
     * 追加节点数据变化监听器
     * @param path 节点路径
     * @param listener 监听器
     */
    void addDataListener(String path, DataListener listener);

    /**
     * @param path:    directory. All of child of path will be listened.
     * @param listener
     * @param executor another thread
     */
    void addDataListener(String path, DataListener listener, Executor executor);

    void removeDataListener(String path, DataListener listener);

    void removeChildListener(String path, ChildListener listener);

    void addStateListener(StateListener listener);

    void removeStateListener(StateListener listener);

    boolean isConnected();

    void close();

    /**
     *
     * @return 连接信息
     */
    URL getUrl();

}
