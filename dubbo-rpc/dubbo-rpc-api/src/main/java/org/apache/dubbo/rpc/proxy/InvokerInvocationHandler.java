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
package org.apache.dubbo.rpc.proxy;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Constants;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ConsumerMethodModel;
import org.apache.dubbo.rpc.model.ConsumerModel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * InvokerHandler
 */
public class InvokerInvocationHandler implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvokerInvocationHandler.class);

    /**
     * 调用器
     */
    private final Invoker<?> invoker;
    /**
     * 消费者模型,根据服务键获取
     */
    private ConsumerModel consumerModel;

    /**
     * 构造方法
     * @param handler 调用器
     */
    public InvokerInvocationHandler(Invoker<?> handler) {
        this.invoker = handler;
        String serviceKey = invoker.getUrl().getServiceKey();
        if (serviceKey != null) {
            this.consumerModel = ApplicationModel.getConsumerModel(serviceKey);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Object方法调用,则使用invoker实例
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) {
            if ("toString".equals(methodName)) {
                return invoker.toString();
            } else if ("$destroy".equals(methodName)) {
                invoker.destroy();
                return null;
            } else if ("hashCode".equals(methodName)) {
                return invoker.hashCode();
            }
        } else if (parameterTypes.length == 1 && "equals".equals(methodName)) {
            return invoker.equals(args[0]);
        }

        // 封装rpc调用
        RpcInvocation rpcInvocation = new RpcInvocation(
                method, // 方法对象
                invoker.getInterface().getName(),// 接口名
                args// 实参
        );
        //
        String serviceKey = invoker.getUrl().getServiceKey();
        // 目标服务唯一名称
        rpcInvocation.setTargetServiceUniqueName(serviceKey);

        // 添加看到attribute中
        if (consumerModel != null) {
            // consumerModel
            rpcInvocation.put(Constants.CONSUMER_MODEL, consumerModel);
            // methodModel
            ConsumerMethodModel methodModel = consumerModel.getMethodModel(method);
            rpcInvocation.put(Constants.METHOD_MODEL, methodModel);
        }
        // 发起调用
        Result result = invoker.invoke(rpcInvocation);
        // 返回异常则抛出,正常返回则返回
        return result.recreate();
    }
}
