package com.color.distribute.lock;

/**
 * @author yue.zhang
 * @create 2018-09-28 11:46
 **/
public interface IDistributedLock {
    public String tryLock(String lockKey, int expireTime) ;
    public boolean unLock(String lockKey, String requestId);
}
