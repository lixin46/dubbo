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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.Node;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;


/**
 *  (SPI, Prototype, ThreadSafe)
 * 目录的作用就是根据调用描述,筛选调用器返回
 * @param <T>
 */
public interface Directory<T> extends Node {

    /**
     * 目录要对应某一特定服务接口类型
     * @return 服务接口
     */
    Class<T> getInterface();

    /**
     * 根据调用描述,获取所有可用的调用器
     * @param invocation 指定的调用描述
     * @return 匹配的调用器
     * @throws RpcException ???
     */
    List<Invoker<T>> list(Invocation invocation) throws RpcException;

    /**
     * 获取所有调用器
     * @return 所有的调用器
     */
    List<Invoker<T>> getAllInvokers();

    /**
     *
     * @return 消费者url???
     */
    URL getConsumerUrl();

}