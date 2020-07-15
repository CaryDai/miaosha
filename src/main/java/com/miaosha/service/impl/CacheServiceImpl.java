package com.miaosha.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.miaosha.service.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * @Author dqj
 * @Date 2020/4/23
 * @Version 1.0
 * @Description 使用 Guava Cache 用于本地缓存
 */
@Service
public class CacheServiceImpl implements CacheService {

    private Cache<String,Object> commonCache = null;

    @PostConstruct  // @PostConstruct将在依赖注入完成后被自动调用
    public void init() {
        commonCache = CacheBuilder.newBuilder()
                // 设置缓存容器的初始容量
                .initialCapacity(10)
                // 设置缓存中最大可以存储100个key，超过100个之后会按照LRU的策略移除缓存项
                .maximumSize(100)
                // 设置写缓存后多少秒过期
                .expireAfterWrite(60, TimeUnit.SECONDS).build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
        commonCache.put(key,value);
    }

    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
