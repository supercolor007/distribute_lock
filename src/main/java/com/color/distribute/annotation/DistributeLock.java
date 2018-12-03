package com.color.distribute.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分布式锁, 标记方法
 *
 * @desc key和value数量需要保持一致
 * @author yue.zhang
 * @create 2018-09-28 14:35
 **/
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    /**
     * 是否支持重试
     * @return
     */
    boolean isRetry() default false;

    /**
     * 等待时间，毫秒
     * @return
     */
    int waitTime() default 200;

    /**
     * key(el表达式)
     * @desc key1,key2,key3
     * @return
     */
    String key();

    /**
     * value(el表达式)
     * @desc #args1.key1,#args2.key2,#args3
     * @return
     */
    String value();
}
