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

import java.util.List;


/**
 * 注册服务(SPI, Prototype, ThreadSafe)
 * 这是dubbo自己定义的服务接口
 *
 * 提供以下能力:
 * 1.注册/注销,属于注册中心变更
 * 2.订阅/取消订阅(推模式),属于监听器注册
 * 3.查询(拉模式)
 */
public interface RegistryService {

    /**
     * 注册数据,例如:Provider服务,consumer地址,路由规则,重写规则和其他数据
     * 注册被要求支持协议:
     * 1.当url设置了check=false参数,当注册失败,不能抛出异常且需要后台重试.否则异常将被抛出.
     * 2.当url设置了dynamic=false参数,需要持久化存储,否则当注册终止退出时被自动删除.(动态配置自动删除)
     * 3.当url设置了category=routers参数,意味着分类存储,默认分类为providers,且数据可以被分类区域通知.
     * 4.当注册中心重启,网络抖动,数据不能丢失,包括自动删除数据
     * 5.允许相同url包含不同参数,他们不能彼此覆盖
     * @param url 注册信息,不允许为空
     */
    void register(URL url);

    /**
     * 注销数据
     * 注销被要求支持以下协议:
     * 1.如果是dynamic=false配置的持久化数据,则注册数据不能被找到,之后抛出IllegalStateException异常,否则会忽略.
     * 2.注销根据完整url匹配
     * @param url 指定的url
     */
    void unregister(URL url);


    /**
     * consumer订阅provider
     * @param url provider
     * @param listener 接收变更通知,首次拉取也以通知的方式下发
     */
    void subscribe(URL url, NotifyListener listener);

    /**
     *
     * @param url 取消订阅
     * @param listener 监听器
     */
    void unsubscribe(URL url, NotifyListener listener);

    /**
     * 查询匹配条件的注册数据.对应于订阅的推模式,这是拉模式只返回一个结果
     * @param url 查询条件,不允许为空,如:consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @return 注册信息列表,可以为空
     */
    List<URL> lookup(URL url);

}