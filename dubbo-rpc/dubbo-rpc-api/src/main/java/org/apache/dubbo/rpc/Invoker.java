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

import org.apache.dubbo.common.Node;


/**
 * 统一的调用器
 * 调用器内部可能调用本地实现实例,也可能调用远程,还可能调用集群
 * @param <T>
 */
public interface Invoker<T> extends Node {

    /**
     * 调用器要调用的接口实例
     * @return 当前调用器绑定的接口
     */
    Class<T> getInterface();

    /**
     * 进行调用
     * @param invocation 指定的调用封装
     * @return 调用结果
     * @throws RpcException 调用异常
     */
    Result invoke(Invocation invocation) throws RpcException;

}