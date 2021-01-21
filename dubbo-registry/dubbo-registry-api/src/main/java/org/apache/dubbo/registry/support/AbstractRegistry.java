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
package org.apache.dubbo.registry.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.FILE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.ACCEPTS_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DEFAULT_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.registry.Constants.REGISTRY_FILESAVE_SYNC_KEY;
import static org.apache.dubbo.registry.Constants.REGISTRY__LOCAL_FILE_CACHE_ENABLED;

/**
 * AbstractRegistry. (SPI, Prototype, ThreadSafe)
 */
public abstract class AbstractRegistry implements Registry {

    // URL address separator, used in file cache, service provider URL separation
    private static final char URL_SEPARATOR = ' ';
    // URL address separated regular expression for parsing the service provider URL list in the file cache
    private static final String URL_SPLIT = "\\s+";
    // Max times to retry to save properties to local cache file
    private static final int MAX_RETRY_TIMES_SAVE_PROPERTIES = 3;

    protected static List<URL> filterEmpty(URL url, List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            // empty://
            return Collections.singletonList(url.setProtocol(EMPTY_PROTOCOL));
        }
        return urls;
    }

    // -----------------------------------------------------------------------------------------------------------------

    // Log output
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    /**
     * 从本地磁盘缓存中加载的配置
     * Local disk cache, where the special key value.registries records the list of registry centers, and the others are the list of notified service providers
     */
    private final Properties properties = new Properties();
    /**
     * File cache timing writing
     * 文件缓存定时写入
     */
    private final ExecutorService registryCacheExecutor = Executors.newFixedThreadPool(1, new NamedThreadFactory("DubboSaveRegistryCache", true));
    // Is it synchronized to save the file
    /**
     * 是否同步保存到文件
     */
    private boolean syncSaveFile;
    /**
     * 最后一次变更版本号
     */
    private final AtomicLong lastCacheChanged = new AtomicLong();
    /**
     * 刷盘重试次数˙
     */
    private final AtomicInteger savePropertiesRetryTimes = new AtomicInteger();

    /**
     * 已注册的信息,调用register()和unregister()时操作的对象
     */
    private final Set<URL> registered = new ConcurrentHashSet<>();
    /**
     * 订阅列表
     * <url,监听器集合>
     * subscribe()和unsubscribe()的操作对象
     */
    private final ConcurrentMap<URL, Set<NotifyListener>> subscribed = new ConcurrentHashMap<>();
    /**
     * 一级key为监听的url,二级key为category,value为变化后的url列表
     * <监听的providerUrl,<category,url列表>>
     */
    private final ConcurrentMap<URL, Map<String, List<URL>>> notified = new ConcurrentHashMap<>();
    /**
     * 注册中心url,保存注册中心服务的配置信息
     * 真实协议的url地址,
     * 比如:zookeeper://或者dubbo://
     */
    private URL registryUrl;
    /**
     * 本地磁盘缓存文件的路径
     */
    private File file;

    /**
     * 唯一构造方法
     *
     * @param registryUrl 注册中心url
     */
    public AbstractRegistry(URL registryUrl) {
        setUrl(registryUrl);
        // 获取file.cache参数,默认为true
        if (registryUrl.getParameter(REGISTRY__LOCAL_FILE_CACHE_ENABLED, true)) {
            // Start file save timer
            // save.file参数,默认为false
            syncSaveFile = registryUrl.getParameter(REGISTRY_FILESAVE_SYNC_KEY, false);
            // 默认文件名
            // ${user.home}/.dubbo/dubbo-registry-{application}-{ip}-port.cache
            String defaultFilename = System.getProperty("user.home") + "/.dubbo/dubbo-registry-" + registryUrl.getParameter(APPLICATION_KEY) + "-" + registryUrl.getAddress().replaceAll(":", "-") + ".cache";
            // 获取file参数值
            String filename = registryUrl.getParameter(FILE_KEY, defaultFilename);
            File file = null;
            if (ConfigUtils.isNotEmpty(filename)) {
                file = new File(filename);
                // 不存在且父路径也不存在
                if (!file.exists() && file.getParentFile() != null && !file.getParentFile().exists()) {
                    // 创建父目录失败,则报错
                    if (!file.getParentFile().mkdirs()) {
                        throw new IllegalArgumentException("Invalid registry cache file " + file + ", cause: Failed to create directory " + file.getParentFile() + "!");
                    }
                }
            }
            this.file = file;
            // When starting the subscription center,
            // we need to read the local cache file for future Registry fault tolerance processing.
            // 磁盘加载缓存
            loadProperties();
            // 获取备用url(包括主url)
            List<URL> backupRegistryUrls = registryUrl.getBackupUrls();
            // 将注册中心url列表通知给监听器NotifyListener
            notify(backupRegistryUrls);
        }
    }
    // -----------------------------------------------------------------------------------------------------------------
    // Node接口实现

    @Override
    public URL getUrl() {
        return registryUrl;
    }

    @Override
    public void destroy() {
        if (logger.isInfoEnabled()) {
            logger.info("Destroy registry:" + getUrl());
        }
        Set<URL> destroyRegistered = new HashSet<>(getRegistered());
        if (!destroyRegistered.isEmpty()) {
            for (URL url : new HashSet<>(getRegistered())) {
                if (url.getParameter(DYNAMIC_KEY, true)) {
                    try {
                        unregister(url);
                        if (logger.isInfoEnabled()) {
                            logger.info("Destroy unregister url " + url);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to unregister url " + url + " to registry " + getUrl() + " on destroy, cause: " + t.getMessage(), t);
                    }
                }
            }
        }
        Map<URL, Set<NotifyListener>> destroySubscribed = new HashMap<>(getSubscribed());
        if (!destroySubscribed.isEmpty()) {
            for (Map.Entry<URL, Set<NotifyListener>> entry : destroySubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    try {
                        unsubscribe(url, listener);
                        if (logger.isInfoEnabled()) {
                            logger.info("Destroy unsubscribe url " + url);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to unsubscribe url " + url + " to registry " + getUrl() + " on destroy, cause: " + t.getMessage(), t);
                    }
                }
            }
        }
        AbstractRegistryFactory.removeDestroyedRegistry(this);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // RegistryService接口实现

    @Override
    public void register(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("register url == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Register: " + url);
        }
        registered.add(url);
    }

    @Override
    public void unregister(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("unregister url == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Unregister: " + url);
        }
        registered.remove(url);
    }

    /**
     * 拉模式获取服务列表
     * @param url 查询条件,不允许为空,如:consumer://10.20.153.10/org.apache.dubbo.foo.BarService?version=1.0.0&application=kylin
     * @return
     */
    @Override
    public List<URL> lookup(URL url) {
        List<URL> result = new ArrayList<>();
        // 最后一次通知的数据
        Map<URL, Map<String, List<URL>>> notified = getNotified();
        Map<String, List<URL>> notifiedUrls = notified.get(url);
        // 非空
        if (CollectionUtils.isNotEmptyMap(notifiedUrls)) {
            // 遍历所有category的value
            for (List<URL> urls : notifiedUrls.values()) {
                // 遍历某个category下的url
                for (URL u : urls) {
                    // 协议不为empty,则追加
                    if (!EMPTY_PROTOCOL.equals(u.getProtocol())) {
                        result.add(u);
                    }
                }
            }
        }
        // 为空
        else {
            final AtomicReference<List<URL>> reference = new AtomicReference<>();
            // 通知监听器实现,把接到的url列表存到原子引用中
            NotifyListener listener = reference::set;
            // 订阅
            subscribe(url, listener); // Subscribe logic guarantees the first notify to return

            // 订阅后获取接到的通知
            List<URL> urls = reference.get();
            // 非空
            if (CollectionUtils.isNotEmpty(urls)) {
                for (URL u : urls) {
                    if (!EMPTY_PROTOCOL.equals(u.getProtocol())) {
                        result.add(u);
                    }
                }
            }
        }
        return result;
    }



    @Override
    public void subscribe(URL url, NotifyListener listener) {
        if (url == null) {
            throw new IllegalArgumentException("subscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("subscribe listener == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Subscribe: " + url);
        }
        // getOrCreate监听器集合
        Set<NotifyListener> listeners = subscribed.computeIfAbsent(url, n -> new ConcurrentHashSet<>());
        // 追加
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(URL url, NotifyListener listener) {
        if (url == null) {
            throw new IllegalArgumentException("unsubscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("unsubscribe listener == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Unsubscribe: " + url);
        }
        Set<NotifyListener> listeners = subscribed.get(url);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    protected void setUrl(URL registryUrl) {
        if (registryUrl == null) {
            throw new IllegalArgumentException("registry url == null");
        }
        this.registryUrl = registryUrl;
    }

    public Set<URL> getRegistered() {
        return Collections.unmodifiableSet(registered);
    }

    public Map<URL, Set<NotifyListener>> getSubscribed() {
        return Collections.unmodifiableMap(subscribed);
    }

    public Map<URL, Map<String, List<URL>>> getNotified() {
        return Collections.unmodifiableMap(notified);
    }

    public File getCacheFile() {
        return file;
    }

    public Properties getCacheProperties() {
        return properties;
    }

    public AtomicLong getLastCacheChanged() {
        return lastCacheChanged;
    }

    public void doSaveProperties(long version) {
        if (version < lastCacheChanged.get()) {
            return;
        }
        if (file == null) {
            return;
        }
        // Save
        try {
            File lockfile = new File(file.getAbsolutePath() + ".lock");
            // 不存在,则创建文件
            if (!lockfile.exists()) {
                lockfile.createNewFile();
            }
            try (RandomAccessFile raf = new RandomAccessFile(lockfile, "rw");
                 FileChannel channel = raf.getChannel()) {
                // 锁定文件
                FileLock lock = channel.tryLock();
                // 锁定失败
                if (lock == null) {
                    throw new IOException("Can not lock the registry cache file " + file.getAbsolutePath() + ", ignore and retry later, maybe multi java process use the file, please config: dubbo.registry.file=xxx.properties");
                }
                // Save
                try {
                    // 缓存文件不存在则创建
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    // 重写文件
                    try (FileOutputStream outputFile = new FileOutputStream(file)) {
                        properties.store(outputFile, "Dubbo Registry Cache");
                    }
                } finally {
                    lock.release();
                }
            }
        } catch (Throwable e) {
            savePropertiesRetryTimes.incrementAndGet();
            // 达到最大重试次数
            if (savePropertiesRetryTimes.get() >= MAX_RETRY_TIMES_SAVE_PROPERTIES) {
                // 告警
                logger.warn("Failed to save registry cache file after retrying " + MAX_RETRY_TIMES_SAVE_PROPERTIES + " times, cause: " + e.getMessage(), e);
                // 重置
                savePropertiesRetryTimes.set(0);
                return;
            }
            // 版本更新,则重置
            if (version < lastCacheChanged.get()) {
                savePropertiesRetryTimes.set(0);
                return;
            }
            // 再次重试
            else {
                registryCacheExecutor.execute(new SaveProperties(lastCacheChanged.incrementAndGet()));
            }
            logger.warn("Failed to save registry cache file, will retry, cause: " + e.getMessage(), e);
        }
    }

    private void loadProperties() {
        if (file != null && file.exists()) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                properties.load(in);
                if (logger.isInfoEnabled()) {
                    logger.info("Load registry cache file " + file + ", data: " + properties);
                }
            } catch (Throwable e) {
                logger.warn("Failed to load registry cache file " + file, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
            }
        }
    }

    public List<URL> getCacheUrls(URL url) {
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if (StringUtils.isNotEmpty(key) && key.equals(url.getServiceKey())
                    && (Character.isLetter(key.charAt(0)) || key.charAt(0) == '_')
                    && StringUtils.isNotEmpty(value)) {
                String[] arr = value.trim().split(URL_SPLIT);
                List<URL> urls = new ArrayList<>();
                for (String u : arr) {
                    urls.add(URL.valueOf(u));
                }
                return urls;
            }
        }
        return null;
    }


    protected void recover() throws Exception {
        // register
        Set<URL> recoverRegistered = new HashSet<>(getRegistered());
        if (!recoverRegistered.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover register url " + recoverRegistered);
            }
            for (URL url : recoverRegistered) {
                register(url);
            }
        }
        // subscribe
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<>(getSubscribed());
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover subscribe url " + recoverSubscribed.keySet());
            }
            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    subscribe(url, listener);
                }
            }
        }
    }

    protected void notify(List<URL> registryUrls) {
        if (CollectionUtils.isEmpty(registryUrls)) {
            return;
        }

        Map<URL, Set<NotifyListener>> subscribed = getSubscribed();
        // 遍历订阅列表
        for (Map.Entry<URL, Set<NotifyListener>> entry : subscribed.entrySet()) {
            URL subscribedUrl = entry.getKey();

            // 不匹配跳过
            // 对比interface,category,group,version
            if (!UrlUtils.isMatch(subscribedUrl, registryUrls.get(0))) {
                continue;
            }

            Set<NotifyListener> listeners = entry.getValue();
            // 存在监听器
            if (listeners != null) {
                // 遍历监听器
                for (NotifyListener listener : listeners) {
                    try {
                        // 把空列表转化成empty://开头的url
                        List<URL> filteredUrls = filterEmpty(subscribedUrl, registryUrls);
                        // 通知
                        notify(subscribedUrl, listener, filteredUrls);
                    } catch (Throwable t) {
                        logger.error("Failed to notify registry event, urls: " + registryUrls + ", cause: " + t.getMessage(), t);
                    }
                }
            }
        }
    }

    /**
     * 服务端通知变更
     * @param key      消费端订阅的url
     * @param listener 消费端订阅的监听器
     * @param urls     变化后的url列表
     */
    protected void notify(URL key, NotifyListener listener, List<URL> urls) {
        if (key == null) {
            throw new IllegalArgumentException("notify url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("notify listener == null");
        }
        // url列表为空,且服务接口不为*
        if ((CollectionUtils.isEmpty(urls)) && !ANY_VALUE.equals(key.getServiceInterface())) {
            logger.warn("Ignore empty notify urls for subscribe url " + key);
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("Notify urls for subscribe url " + key + ", urls: " + urls);
        }
        // 把与key匹配的url,按照category参数值进行分组,category默认参数值为providers
        Map<String, List<URL>> result = groupByCategory(key, urls);

        if (result.isEmpty()) {
            return;
        }
        Map<String, List<URL>> categoryNotified = notified.computeIfAbsent(key, u -> new ConcurrentHashMap<>());
        // 遍历每个类别
        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            String category = entry.getKey();
            // 变化后的列表
            List<URL> categoryList = entry.getValue();
            categoryNotified.put(category, categoryList);
            // 把变化后的列表通知监听器
            // 按照类别维度进行通知
            listener.notify(categoryList);
            // 刷新磁盘文件(同步或异步方式)
            saveProperties(key);
        }
    }

    private Map<String, List<URL>> groupByCategory(URL key,List<URL> urls) {
        // keep every provider's category.
        Map<String, List<URL>> result = new HashMap<>();
        for (URL u : urls) {
            // 匹配
            if (UrlUtils.isMatch(key, u)) {
                // category参数,默认为providers
                String category = u.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);
                // 获取category对应
                List<URL> categoryList = result.computeIfAbsent(category, k -> new ArrayList<>());
                categoryList.add(u);
            }
        }
        return result;
    }

    private void saveProperties(URL url) {
        if (file == null) {
            return;
        }

        try {
            StringBuilder buf = new StringBuilder();
            Map<String, List<URL>> categoryNotified = notified.get(url);
            if (categoryNotified != null) {
                for (List<URL> us : categoryNotified.values()) {
                    for (URL u : us) {
                        if (buf.length() > 0) {
                            buf.append(URL_SEPARATOR);
                        }
                        buf.append(u.toFullString());
                    }
                }
            }
            // 写入属性
            properties.setProperty(url.getServiceKey(), buf.toString());
            // 版本自增
            long version = lastCacheChanged.incrementAndGet();
            // 同步保存到磁盘,则重写配置文件
            if (syncSaveFile) {
                doSaveProperties(version);
            }
            // 异步线程写入
            else {
                registryCacheExecutor.execute(new SaveProperties(version));
            }
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }


    protected boolean acceptable(URL urlToRegistry) {
        // accepts参数值
        String pattern = registryUrl.getParameter(ACCEPTS_KEY);
        // 为空,则可接受
        if (StringUtils.isEmpty(pattern)) {
            return true;
        }
        // 逗号拆分,当做可接受的协议,如果当前协议为可接受的协议则为true
        return Arrays.stream(COMMA_SPLIT_PATTERN.split(pattern))
                .anyMatch(p -> p.equalsIgnoreCase(urlToRegistry.getProtocol()));
    }

    @Override
    public String toString() {
        return getUrl().toString();
    }

    private class SaveProperties implements Runnable {
        private long version;

        private SaveProperties(long version) {
            this.version = version;
        }

        @Override
        public void run() {
            doSaveProperties(version);
        }
    }

}
