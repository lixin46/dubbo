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

package org.apache.dubbo.common.constants;

import javafx.scene.shape.StrokeLineCap;
import org.apache.dubbo.common.URL;

import java.net.NetworkInterface;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * 很重要的公共常量定义
 */
public interface CommonConstants {

    // -----------------------------------------------------------------------------------------------------------------
    // key定义
    /**
     * 接口
     */
    String INTERFACE_KEY = "interface";
    /**
     * 接口方法
     */
    String METHODS_KEY = "methods";
    /**
     * 元数据类型,remote或
     */
    String METADATA_KEY = "metadata-type";
    // -----------------------------------------------------------------------------------------------------------------
    // value定义
    /**
     * 默认的元数据存储方式为本地
     */
    String DEFAULT_METADATA_STORAGE_TYPE = "local";
    /**
     * 元数据存储方式为远程
     */
    String REMOTE_METADATA_STORAGE_TYPE = "remote";
    /**
     * 本机ip
     */
    String LOCALHOST_VALUE = "127.0.0.1";
    // -----------------------------------------------------------------------------------------------------------------
    /**
     * 代表任意值
     */
    String ANY_VALUE = "*";
    /**
     * 逗号
     */
    String COMMA_SEPARATOR = ",";
    /**
     * 点
     */
    String DOT_SEPARATOR = ".";
    // -----------------------------------------------------------------------------------------------------------------

    String DUBBO = "dubbo";

    String PROVIDER = "provider";

    String CONSUMER = "consumer";

    String APPLICATION_KEY = "application";

    /**
     * 远程应用名称对应的url参数名
     */
    String REMOTE_APPLICATION_KEY = "remote.application";

    String ENABLED_KEY = "enabled";

    String DISABLED_KEY = "disabled";

    String DUBBO_PROPERTIES_KEY = "dubbo.properties.file";

    String DEFAULT_DUBBO_PROPERTIES = "dubbo.properties";




    Pattern COMMA_SPLIT_PATTERN = Pattern.compile("\\s*[,]+\\s*");

    String PATH_SEPARATOR = "/";

    String PROTOCOL_SEPARATOR = "://";

    String PROTOCOL_SEPARATOR_ENCODED = URL.encode(PROTOCOL_SEPARATOR);

    String REGISTRY_SEPARATOR = "|";

    Pattern REGISTRY_SPLIT_PATTERN = Pattern.compile("\\s*[|;]+\\s*");

    Pattern D_REGISTRY_SPLIT_PATTERN = Pattern.compile("\\s*[|]+\\s*");

    String SEMICOLON_SEPARATOR = ";";

    Pattern SEMICOLON_SPLIT_PATTERN = Pattern.compile("\\s*[;]+\\s*");

    Pattern EQUAL_SPLIT_PATTERN = Pattern.compile("\\s*[=]+\\s*");

    String DEFAULT_PROXY = "javassist";

    String DEFAULT_DIRECTORY = "dubbo";

    String PROTOCOL_KEY = "protocol";

    String DEFAULT_PROTOCOL = "dubbo";



    String EXECUTOR_SERVICE_COMPONENT_KEY = ExecutorService.class.getName();
    // -----------------------------------------------------------------------------------------------------------------
    // 线程池相关
    /**
     * 默认的线程名前缀
     */
    String DEFAULT_THREAD_NAME = "Dubbo";
    /**
     * 默认的核心线程数
     */
    int DEFAULT_CORE_THREADS = 0;
    /**
     * 默认的最大线程数
     */
    int DEFAULT_THREADS = 200;
    /**
     * 默认使用的线程池实现的名称
     */
    String THREADPOOL_KEY = "threadpool";
    /**
     * 线程名称前缀的参数名
     */
    String THREAD_NAME_KEY = "threadname";
    /**
     * 核心线程数参数名
     */
    String CORE_THREADS_KEY = "corethreads";
    /**
     * 最大线程数参数名
     */
    String THREADS_KEY = "threads";
    /**
     * 队列容量参数名
     */
    String QUEUES_KEY = "queues";
    /**
     * 线程空闲时长参数名
     */
    String ALIVE_KEY = "alive";
    /**
     * 默认的线程池实现
     */
    String DEFAULT_THREADPOOL = "limited";
    /**
     * 默认的客户端线程池实现
     */
    String DEFAULT_CLIENT_THREADPOOL = "cached";

    String IO_THREADS_KEY = "iothreads";
    /**
     * 默认的队列长度值
     */
    int DEFAULT_QUEUES = 0;
    /**
     * 默认的线程空闲
     */
    int DEFAULT_ALIVE = 60 * 1000;
    // -----------------------------------------------------------------------------------------------------------------

    String TIMEOUT_KEY = "timeout";

    int DEFAULT_TIMEOUT = 1000;

    // used by invocation attachments to transfer timeout from Consumer to Provider.
    // works as a replacement of TIMEOUT_KEY on wire, which seems to be totally useless in previous releases).
    String TIMEOUT_ATTACHENT_KEY = "_TO";

    String TIME_COUNTDOWN_KEY = "timeout-countdown";

    String ENABLE_TIMEOUT_COUNTDOWN_KEY = "enable-timeout-countdown";

    String REMOVE_VALUE_PREFIX = "-";

    String PROPERTIES_CHAR_SEPERATOR = "-";

    String UNDERLINE_SEPARATOR = "_";

    String SEPARATOR_REGEX = "_|-";

    String GROUP_CHAR_SEPERATOR = ":";

    String HIDE_KEY_PREFIX = ".";

    String DOT_REGEX = "\\.";

    String DEFAULT_KEY_PREFIX = "default.";

    String DEFAULT_KEY = "default";

    String PREFERRED_KEY = "preferred";

    /**
     * Default timeout value in milliseconds for server shutdown
     */
    int DEFAULT_SERVER_SHUTDOWN_TIMEOUT = 10000;

    /**
     * 用于指定是服务端还是客户端
     */
    String SIDE_KEY = "side";
    /**
     * side=provider为服务端
     */
    String PROVIDER_SIDE = "provider";
    /**
     * side=consumer为消费端
     */
    String CONSUMER_SIDE = "consumer";


    /**
     * 用于指明dubbo协议的版本
     */
    String DUBBO_VERSION_KEY = "dubbo";
    /**
     * package version in the manifest
     * dubbo的jar包版本
     */
    String RELEASE_KEY = "release";
    /**
     * 启动时间戳
     */
    String TIMESTAMP_KEY = "timestamp";
    /**
     * jvm进程id
     */
    String PID_KEY = "pid";


    String ANYHOST_KEY = "anyhost";

    String ANYHOST_VALUE = "0.0.0.0";

    String LOCALHOST_KEY = "localhost";





    String METHOD_KEY = "method";





    String GROUP_KEY = "group";

    String PATH_KEY = "path";


    /**
     * 缓存文件路径
     */
    String FILE_KEY = "file";

    String DUMP_DIRECTORY = "dump.directory";

    String CLASSIFIER_KEY = "classifier";

    String VERSION_KEY = "version";
    /**
     * 修订版本
     */
    String REVISION_KEY = "revision";





    /**
     * Consumer side 's proxy class
     */
    String PROXY_CLASS_REF = "refClass";

    /**
     * generic call
     */
    String $INVOKE = "$invoke";
    String $INVOKE_ASYNC = "$invokeAsync";



    int MAX_PROXY_COUNT = 65535;

    String MONITOR_KEY = "monitor";
    String CLUSTER_KEY = "cluster";
    String USERNAME_KEY = "username";
    String PASSWORD_KEY = "password";
    String HOST_KEY = "host";
    String PORT_KEY = "port";
    String DUBBO_IP_TO_BIND = "DUBBO_IP_TO_BIND";

    /**
     * The property name for {@link NetworkInterface#getDisplayName() the name of network interface} that
     * the Dubbo application prefers
     *
     * @since 2.7.6
     */
    String DUBBO_PREFERRED_NETWORK_INTERFACE = "dubbo.network.interface.preferred";

    @Deprecated
    String SHUTDOWN_WAIT_SECONDS_KEY = "dubbo.service.shutdown.wait.seconds";
    String SHUTDOWN_WAIT_KEY = "dubbo.service.shutdown.wait";
    String DUBBO_PROTOCOL = "dubbo";

    String DUBBO_LABELS = "dubbo.labels";
    String DUBBO_ENV_KEYS = "dubbo.env.keys";

    String CONFIG_CONFIGFILE_KEY = "config-file";
    String CONFIG_ENABLE_KEY = "highest-priority";
    String CONFIG_NAMESPACE_KEY = "namespace";
    String CHECK_KEY = "check";

    String BACKLOG_KEY = "backlog";

    String HEARTBEAT_EVENT = null;
    String MOCK_HEARTBEAT_EVENT = "H";
    String READONLY_EVENT = "R";

    String REFERENCE_FILTER_KEY = "reference.filter";

    String INVOKER_LISTENER_KEY = "invoker.listener";



    String TAG_KEY = "dubbo.tag";

    /**
     * To decide whether to make connection when the client is created
     */
    String LAZY_CONNECT_KEY = "lazy";

    String STUB_EVENT_KEY = "dubbo.stub.event";

    String REFERENCE_INTERCEPTOR_KEY = "reference.interceptor";

    String SERVICE_FILTER_KEY = "service.filter";

    String EXPORTER_LISTENER_KEY = "exporter.listener";

    String METRICS_PORT = "metrics.port";

    String METRICS_PROTOCOL = "metrics.protocol";

    /**
     * After simplify the registry, should add some parameter individually for provider.
     *
     * @since 2.7.0
     */
    String EXTRA_KEYS_KEY = "extra-keys";

    /**
     * 通用服务本地java序列化方式,值定义
     */
    String GENERIC_SERIALIZATION_NATIVE_JAVA = "nativejava";

    /**
     * 通用服务默认序列化方式,值定义
     */
    String GENERIC_SERIALIZATION_DEFAULT = "true";
    /**
     * 通用服务bean序列化方式,值定义
     */
    String GENERIC_SERIALIZATION_BEAN = "bean";
    /**
     * 通用服务返回原始
     */
    String GENERIC_RAW_RETURN = "raw.return";
    /**
     * 通用服务gson序列化
     */
    String GENERIC_SERIALIZATION_PROTOBUF = "protobuf-json";

    String GENERIC_WITH_CLZ_KEY = "generic.include.class";

    /**
     * The limit of callback service instances for one interface on every client
     */
    String CALLBACK_INSTANCES_LIMIT_KEY = "callbacks";

    /**
     * The default limit number for callback service instances
     *
     * @see #CALLBACK_INSTANCES_LIMIT_KEY
     */
    int DEFAULT_CALLBACK_INSTANCES = 1;

    String LOADBALANCE_KEY = "loadbalance";

    String DEFAULT_LOADBALANCE = "random";

    String RETRIES_KEY = "retries";

    String FORKS_KEY = "forks";

    int DEFAULT_RETRIES = 2;

    int DEFAULT_FAILBACK_TIMES = 3;

    String REGISTER_KEY = "register";

    String INTERFACES = "interfaces";

    String SSL_ENABLED_KEY = "ssl-enabled";

}
