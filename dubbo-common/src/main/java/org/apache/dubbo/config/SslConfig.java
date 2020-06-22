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
package org.apache.dubbo.config;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.config.support.Parameter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 加密ssl配置
 * <dubbo:ssl></dubbo:ssl>
 */
public class SslConfig extends AbstractConfig {

    private static final Logger logger = LoggerFactory.getLogger(SslConfig.class);
    private AtomicBoolean inited = new AtomicBoolean(false);

    private String serverKeyCertChainPath;
    private String serverPrivateKeyPath;
    private String serverKeyPassword;
    private String serverTrustCertCollectionPath;

    private String clientKeyCertChainPath;
    private String clientPrivateKeyPath;
    private String clientKeyPassword;
    private String clientTrustCertCollectionPath;

    private InputStream serverKeyCertChainPathStream;
    private InputStream serverPrivateKeyPathStream;
    private InputStream serverTrustCertCollectionPathStream;

    private InputStream clientKeyCertChainPathStream;
    private InputStream clientPrivateKeyPathStream;
    private InputStream clientTrustCertCollectionPathStream;

    // -----------------------------------------------------------------------------------------------------------------
    // 可导出getter
    @Parameter(key = "server-key-cert-chain-path")
    public String getServerKeyCertChainPath() {
        return serverKeyCertChainPath;
    }

    @Parameter(key = "server-private-key-path")
    public String getServerPrivateKeyPath() {
        return serverPrivateKeyPath;
    }

    @Parameter(key = "server-key-password")
    public String getServerKeyPassword() {
        return serverKeyPassword;
    }

    @Parameter(key = "server-trust-cert-collection-path")
    public String getServerTrustCertCollectionPath() {
        return serverTrustCertCollectionPath;
    }

    @Parameter(key = "client-key-cert-chain-path")
    public String getClientKeyCertChainPath() {
        return clientKeyCertChainPath;
    }

    @Parameter(key = "client-private-key-path")
    public String getClientPrivateKeyPath() {
        return clientPrivateKeyPath;
    }

    @Parameter(key = "client-key-password")
    public String getClientKeyPassword() {
        return clientKeyPassword;
    }

    @Parameter(key = "client-trust-cert-collection-path")
    public String getClientTrustCertCollectionPath() {
        return clientTrustCertCollectionPath;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 可注入setter
    public void setServerKeyCertChainPath(String serverKeyCertChainPath) {
        this.serverKeyCertChainPath = serverKeyCertChainPath;
    }

    public void setServerPrivateKeyPath(String serverPrivateKeyPath) {
        this.serverPrivateKeyPath = serverPrivateKeyPath;
    }

    public void setServerKeyPassword(String serverKeyPassword) {
        this.serverKeyPassword = serverKeyPassword;
    }

    public void setServerTrustCertCollectionPath(String serverTrustCertCollectionPath) {
        this.serverTrustCertCollectionPath = serverTrustCertCollectionPath;
    }

    public void setClientKeyCertChainPath(String clientKeyCertChainPath) {
        this.clientKeyCertChainPath = clientKeyCertChainPath;
    }

    public void setClientPrivateKeyPath(String clientPrivateKeyPath) {
        this.clientPrivateKeyPath = clientPrivateKeyPath;
    }

    public void setClientKeyPassword(String clientKeyPassword) {
        this.clientKeyPassword = clientKeyPassword;
    }

    public void setClientTrustCertCollectionPath(String clientTrustCertCollectionPath) {
        this.clientTrustCertCollectionPath = clientTrustCertCollectionPath;
    }

    public void setServerKeyCertChainPathStream(InputStream serverKeyCertChainPathStream) {
        this.serverKeyCertChainPathStream = serverKeyCertChainPathStream;
    }

    public void setServerPrivateKeyPathStream(InputStream serverPrivateKeyPathStream) {
        this.serverPrivateKeyPathStream = serverPrivateKeyPathStream;
    }

    public void setServerTrustCertCollectionPathStream(InputStream serverTrustCertCollectionPathStream) {
        this.serverTrustCertCollectionPathStream = serverTrustCertCollectionPathStream;
    }

    public void setClientKeyCertChainPathStream(InputStream clientKeyCertChainPathStream) {
        this.clientKeyCertChainPathStream = clientKeyCertChainPathStream;
    }

    public void setClientPrivateKeyPathStream(InputStream clientPrivateKeyPathStream) {
        this.clientPrivateKeyPathStream = clientPrivateKeyPathStream;
    }

    public void setClientTrustCertCollectionPathStream(InputStream clientTrustCertCollectionPathStream) {
        this.clientTrustCertCollectionPathStream = clientTrustCertCollectionPathStream;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // 普通
    public InputStream getServerKeyCertChainPathStream() throws FileNotFoundException {
        if (serverKeyCertChainPath != null) {
            serverKeyCertChainPathStream = new FileInputStream(serverKeyCertChainPath);
        }
        return serverKeyCertChainPathStream;
    }

    public InputStream getServerPrivateKeyPathStream() throws FileNotFoundException {
        if (serverPrivateKeyPath != null) {
            serverPrivateKeyPathStream = new FileInputStream(serverPrivateKeyPath);
        }
        return serverPrivateKeyPathStream;
    }

    public InputStream getServerTrustCertCollectionPathStream() throws FileNotFoundException {
        if (serverTrustCertCollectionPath != null) {
            serverTrustCertCollectionPathStream = new FileInputStream(serverTrustCertCollectionPath);
        }
        return serverTrustCertCollectionPathStream;
    }

    public InputStream getClientKeyCertChainPathStream() throws FileNotFoundException {
        if (clientKeyCertChainPath != null) {
            clientKeyCertChainPathStream = new FileInputStream(clientKeyCertChainPath);
        }
        return clientKeyCertChainPathStream;
    }

    public InputStream getClientPrivateKeyPathStream() throws FileNotFoundException {
        if (clientPrivateKeyPath != null) {
            clientPrivateKeyPathStream = new FileInputStream(clientPrivateKeyPath);
        }
        return clientPrivateKeyPathStream;
    }

    public InputStream getClientTrustCertCollectionPathStream() throws FileNotFoundException {
        if (clientTrustCertCollectionPath != null) {
            clientTrustCertCollectionPathStream = new FileInputStream(clientTrustCertCollectionPath);
        }
        return clientTrustCertCollectionPathStream;
    }
    // -----------------------------------------------------------------------------------------------------------------


}
