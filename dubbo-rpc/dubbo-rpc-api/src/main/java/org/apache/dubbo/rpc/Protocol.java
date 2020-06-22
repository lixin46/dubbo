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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import java.util.Collections;
import java.util.List;

/**
 * Protocol. (API/SPI, Singleton, ThreadSafe)
 * 如果url对象中存在protocol参数,则使用其参数值对应的扩展实例,否则使用dubbo对应的扩展实例.
 *
 * 协议有两种职责:
 * 1.根据接口类型和url配置,产生调用器
 * 2.导出调用器,返回导出器
 */
@SPI("dubbo")
public interface Protocol {

    /**
     *
     * @return 当用户没有配置端口时,获取默认端口
     */
    int getDefaultPort();

    /**
     * 导出调用器,获取导出器
     * 暴露服务用于远程调用:
     * 1.协议应该记录请求来源地址,在收到一个请求之后
     * 2.export()方法必须幂等,也就是说,针对相同的url,多次调用应该返回相同的导出器实例
     * 3.Invoker实例通过框架传递,协议不需要关心
     * 服务端使用
     * @param invoker 指定的调用器
     * @param <T> 服务接口类型
     * @return 调用器对应的导出器,导出器为了引用暴露的服务,取消导出时很有用
     * @throws RpcException 导出过程中异常
     */
    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

    /**
     * 引用
     * 客户端使用的API
     * @param type 接口类型
     * @param url 地址,包含配置信息
     * @param <T> 接口类型
     * @return 调用器
     * @throws RpcException 远程调用
     */
    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;

    /**
     * 销毁协议:
     * 1.取消所有当前协议导出和引用的服务
     * 2.释放所有占用的资源,比如:连接,端口等等
     * 3.协议销毁后可以继续导出和引用新的服务
     */
    void destroy();

    /**
     * @return 当前协议中所有服务端服务
     */
    default List<ProtocolServer> getServers() {
        return Collections.emptyList();
    }

}