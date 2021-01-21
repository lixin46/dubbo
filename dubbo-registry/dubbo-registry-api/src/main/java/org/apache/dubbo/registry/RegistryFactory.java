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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;


/**
 * 注册中心工厂,
 * 默认使用DubboRegistryFactory实现,
 * 项目中通常使用ZookeeperRegistryFactory
 *
 */
@SPI("dubbo")
public interface RegistryFactory {

    /**
     * 获取注册中心服务
     * 连接到注册中心需要支持以下协议:
     * 1.当check=false被设置,则连接不做检查,否则当连接失败时抛异常
     * 2.支持username:password授权认证在url上
     * 3.支持backup=10.20.153.10候选注册中心集群地址
     * 4.支持file=registry.cache本地磁盘文件缓存
     * 5.支持timeout=1000请求超时设置
     * 6.支持session=6000会话超时或过期设置
     * @param url 指定的信息
     * @return 注册中心实例
     */
    @Adaptive({"protocol"})
    Registry getRegistry(URL url);

}