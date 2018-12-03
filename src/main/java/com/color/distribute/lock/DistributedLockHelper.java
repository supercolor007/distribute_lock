package com.color.distribute.lock;

import com.color.service.base.ColorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import java.io.Serializable;
import java.util.Collections;
import java.util.UUID;

/**
 * Redis分布式锁
 *
 * @author yue.zhang
 * @create 2018-09-28 10:05
 **/
@Service
@Slf4j
public class DistributedLockHelper implements IDistributedLock, Serializable {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LOCK_SUCCESS = "OK";
    private static final String SET_IF_NOT_EXIST = "NX";

    /**
     * 是否设置过期时间
     */
    private static final String SET_WITH_EXPIRE_TIME = "PX";
    private static final String RELEASE_SUCCESS = "1";

    @Autowired
    public DistributedLockHelper(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取分布式锁
     *
     * @param lockKey    锁
     * @param expireTime 超期时间 避免死锁
     * @return 是否获取成功
     * @desc 1. 当前没有锁（key不存在），那么就进行加锁操作，并对锁设置个有效期，同时value表示加锁的客户端。2. 已有锁存在，不做任何操作。
     */
    @Override
    public String tryLock(String lockKey, int expireTime) {

        String requestId = UUID.randomUUID().toString();
        String status = stringRedisTemplate.execute(new RedisCallback<String>() {
            @Override
            public String doInRedis(RedisConnection connection) throws DataAccessException {
                Jedis jedis = (Jedis) connection.getNativeConnection();
                String result = jedis.set(lockKey, requestId, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, expireTime);
                return result;
            }
        });

        if (LOCK_SUCCESS.equals(status)) {
            log.info("加锁成功，redisKey: {}, requestId: {}, expireTime: {}", lockKey, requestId, expireTime);
        } else {
            log.error("加锁失败，redisKey: {}, requestId: {}, status: {}", lockKey, requestId, status);
            //throw new ColorException(500, "加锁失败，redisKey: {0}, requestId: {1}, status: {2}", lockKey, requestId, status);
            throw new ColorException(500, "加锁失败，redisKey: {0}, requestId: {1}, status: {2}");
        }

        return requestId;
    }

    /**
     * 释放分布式锁
     *
     * @param lockKey   锁
     * @param requestId 请求标识
     * @return 是否释放成功
     */
    @Override
    public boolean unLock(String lockKey, String requestId) {

        //Lua脚本确保原子性
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

        String status = stringRedisTemplate.execute(new RedisCallback<String>() {
            @Override
            public String doInRedis(RedisConnection connection) throws DataAccessException {
                Jedis jedis = (Jedis) connection.getNativeConnection();
                Object result = jedis.eval(script, Collections.singletonList(lockKey), Collections.singletonList(requestId));
                return result.toString();
            }
        });

        if (RELEASE_SUCCESS.equals(status)) {
            log.info("解锁成功, redisKey: {}, requestId: {}, status: {}", lockKey, requestId, status);
            return true;
        }

        log.info("解锁失败, redisKey: {}, requestId: {}, status: {}", lockKey, requestId, status);
        return false;

    }

}
