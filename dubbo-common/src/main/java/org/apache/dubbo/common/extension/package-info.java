/**
 * 整体模式为:
 * 框架为带有@SPI标记的接口生成代理类实例,
 * 并通过识别@Adaptive注解value()配置,
 * 决定代理哪个实例.
 */
package org.apache.dubbo.common.extension;