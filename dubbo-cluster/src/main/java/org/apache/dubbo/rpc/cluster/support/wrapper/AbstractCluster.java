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
package org.apache.dubbo.rpc.cluster.support.wrapper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import org.apache.dubbo.rpc.cluster.interceptor.ClusterInterceptor;
import org.apache.dubbo.rpc.cluster.support.AbstractClusterInvoker;

import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.REFERENCE_INTERCEPTOR_KEY;

public abstract class AbstractCluster implements Cluster {

    /**
     * 构建集群调用器拦截器链,也创建代理链
     *
     * InterceptorInvokerNode -> InterceptorInvokerNode -> AbstractClusterInvoker
     * @param clusterInvoker 原始的集群调用器
     * @param key 拦截器key
     * @param <T>
     * @return 构建后的调用器
     */
    private <T> Invoker<T> buildClusterInterceptors(AbstractClusterInvoker<T> clusterInvoker, String key) {
        AbstractClusterInvoker<T> last = clusterInvoker;

        ExtensionLoader<ClusterInterceptor> extensionLoader = ExtensionLoader.getExtensionLoader(ClusterInterceptor.class);
        // 集群拦截器
        List<ClusterInterceptor> interceptors = extensionLoader.getActivateExtension(clusterInvoker.getUrl(), key);
        // 拦截器实例非空
        if (!interceptors.isEmpty()) {
            // 倒着遍历
            for (int i = interceptors.size() - 1; i >= 0; i--) {
                // 获取拦截器
                final ClusterInterceptor interceptor = interceptors.get(i);
                final AbstractClusterInvoker<T> next = last;
                // 封装拦截器,拦截调用器
                last = new InterceptorInvokerNode<>(clusterInvoker, interceptor, next);
            }
        }
        return last;
    }

    @Override
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        // 子类实现,比如:创建 FailoverClusterInvoker
        AbstractClusterInvoker<T> invoker = doJoin(directory);
        // 获取reference.interceptor参数,引用拦截器配置
        String interceptor = directory.getUrl().getParameter(REFERENCE_INTERCEPTOR_KEY);
        // 构建拦截器链
        return buildClusterInterceptors(invoker, interceptor);
    }

    protected abstract <T> AbstractClusterInvoker<T> doJoin(Directory<T> directory) throws RpcException;

    /**
     * 拦截器代理
     * @param <T>
     */
    protected class InterceptorInvokerNode<T> extends AbstractClusterInvoker<T> {

        /**
         * 原始的调用器
         */
        private AbstractClusterInvoker<T> clusterInvoker;
        /**
         * 拦截器
         */
        private ClusterInterceptor interceptor;
        /**
         * 下一个要调用的调用器
         */
        private AbstractClusterInvoker<T> next;

        /**
         * 构造方法
         * @param clusterInvoker 原始调用器
         * @param interceptor 当前拦截器
         * @param next 下一个需要调用的调用器
         */
        public InterceptorInvokerNode(AbstractClusterInvoker<T> clusterInvoker,
                                      ClusterInterceptor interceptor,
                                      AbstractClusterInvoker<T> next) {
            this.clusterInvoker = clusterInvoker;
            this.interceptor = interceptor;
            this.next = next;
        }

        @Override
        public Class<T> getInterface() {
            return clusterInvoker.getInterface();
        }

        @Override
        public URL getUrl() {
            return clusterInvoker.getUrl();
        }

        @Override
        public boolean isAvailable() {
            return clusterInvoker.isAvailable();
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            Result asyncResult;
            try {
                // 前置拦截
                interceptor.before(next, invocation);
                // 传递下一个调用器
                asyncResult = interceptor.intercept(next, invocation);
            } catch (Exception e) {
                // onError callback
                if (interceptor instanceof ClusterInterceptor.Listener) {
                    ClusterInterceptor.Listener listener = (ClusterInterceptor.Listener) interceptor;
                    listener.onError(e, clusterInvoker, invocation);
                }
                throw e;
            } finally {
                // 后置拦截
                interceptor.after(next, invocation);
            }
            return asyncResult.whenCompleteWithContext((r, t) -> {
                // onResponse callback
                if (interceptor instanceof ClusterInterceptor.Listener) {
                    ClusterInterceptor.Listener listener = (ClusterInterceptor.Listener) interceptor;
                    if (t == null) {
                        listener.onMessage(r, clusterInvoker, invocation);
                    } else {
                        listener.onError(t, clusterInvoker, invocation);
                    }
                }
            });
        }

        @Override
        public void destroy() {
            clusterInvoker.destroy();
        }

        @Override
        public String toString() {
            return clusterInvoker.toString();
        }

        @Override
        protected Result doInvoke(Invocation invocation, List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {
            // The only purpose is to build a interceptor chain, so the cluster related logic doesn't matter.
            return null;
        }
    }
}
