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
package org.apache.dubbo.metadata.report;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_DIRECTORY;
import static org.apache.dubbo.metadata.report.support.Constants.METADATA_REPORT_KEY;

/**
 * 2019-08-09
 */
public class MetadataReportInstance {

    /**
     * 单例的初始化状态
     */
    private static AtomicBoolean init = new AtomicBoolean(false);

    /**
     * 单例
     */
    private static MetadataReport metadataReport;

    /**
     * 根据url配置,初始化元数据报告对象
     * @param metadataReportURL 元数据报告的url配置
     */
    public static void init(URL metadataReportURL) {
        if (init.get()) {
            return;
        }
        // 元数据报告工厂
        MetadataReportFactory metadataReportFactory = ExtensionLoader.getExtensionLoader(MetadataReportFactory.class).getAdaptiveExtension();
        // 协议为metadata
        if (METADATA_REPORT_KEY.equals(metadataReportURL.getProtocol())) {
            // 获取metadata参数,默认为dubbo
            String protocol = metadataReportURL.getParameter(METADATA_REPORT_KEY, DEFAULT_DIRECTORY);
            //
            metadataReportURL = URLBuilder.from(metadataReportURL)
                    .setProtocol(protocol)// 把metadata参数变成协议
                    .removeParameter(METADATA_REPORT_KEY)// 删除metadata参数
                    .build();
        }
        // 工厂适配器,根据metadata的配置,映射对应实例
        metadataReport = metadataReportFactory.getMetadataReport(metadataReportURL);
        init.set(true);
    }

    public static MetadataReport getMetadataReport() {
        return getMetadataReport(false);
    }

    public static MetadataReport getMetadataReport(boolean checked) {
        // 检查,则检查初始化
        if (checked) {
            checkInit();
        }
        return metadataReport;
    }

    private static void checkInit() {
        if (!init.get()) {
            throw new IllegalStateException("the metadata report was not inited.");
        }
    }
}
