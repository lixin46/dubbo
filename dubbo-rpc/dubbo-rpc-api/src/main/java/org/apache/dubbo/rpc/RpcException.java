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

import javax.naming.LimitExceededException;

/**
 * RPC Exception. (API, Prototype, ThreadSafe)
 *
 * @serial Don't change the class name and properties.
 * @export
 * @see org.apache.dubbo.rpc.Invoker#invoke(Invocation)
 * @since 1.0
 */
public /**final**/ class RpcException extends RuntimeException {

    private static final long serialVersionUID = 7815426752583648734L;

    /**
     * 未知异常,兜底的
     */
    public static final int UNKNOWN_EXCEPTION = 0;
    /**
     * 网络异常???
     */
    public static final int NETWORK_EXCEPTION = 1;
    /**
     * 传输超时
     */
    public static final int TIMEOUT_EXCEPTION = 2;
    /**
     * 业务异常,是不会发起重试的
     */
    public static final int BIZ_EXCEPTION = 3;
    /**
     * 拒绝???
     */
    public static final int FORBIDDEN_EXCEPTION = 4;
    /**
     * 序列化异常
     */
    public static final int SERIALIZATION_EXCEPTION = 5;
    /**
     * 无可用的调用器异常
     */
    public static final int NO_INVOKER_AVAILABLE_AFTER_FILTER = 6;
    /**
     * 限流异常
     */
    public static final int LIMIT_EXCEEDED_EXCEPTION = 7;
    /**
     * ???
     */
    public static final int TIMEOUT_TERMINATE = 8;

    /**
     * RpcException cannot be extended, use error code for exception type to keep compatibility
     *
     * 为异常类型使用错误码以此来保持兼容性
     */
    private int code;

    /**
     * 构造方法
     */
    public RpcException() {
        super();
    }

    /**
     * 构造方法
     * @param message 消息
     * @param cause 原因
     */
    public RpcException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造方法
     * @param message 消息
     */
    public RpcException(String message) {
        super(message);
    }

    /**
     * 构造方法
     * @param cause 原因
     */
    public RpcException(Throwable cause) {
        super(cause);
    }

    /**
     * 构造方法
     * @param code 错误码
     */
    public RpcException(int code) {
        super();
        this.code = code;
    }

    /**
     * 构造方法
     * @param code 错误码
     * @param message 消息
     * @param cause 原因
     */
    public RpcException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 构造方法
     * @param code 错误码
     * @param message 消息
     */
    public RpcException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造方法
     * @param code 错误码
     * @param cause 原因
     */
    public RpcException(int code, Throwable cause) {
        super(cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public boolean isBiz() {
        return code == BIZ_EXCEPTION;
    }

    public boolean isForbidded() {
        return code == FORBIDDEN_EXCEPTION;
    }

    public boolean isTimeout() {
        return code == TIMEOUT_EXCEPTION;
    }

    public boolean isNetwork() {
        return code == NETWORK_EXCEPTION;
    }

    public boolean isSerialization() {
        return code == SERIALIZATION_EXCEPTION;
    }

    public boolean isNoInvokerAvailableAfterFilter() {
        return code == NO_INVOKER_AVAILABLE_AFTER_FILTER;
    }

    public boolean isLimitExceed() {
        return code == LIMIT_EXCEEDED_EXCEPTION || getCause() instanceof LimitExceededException;
    }
}