package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取分布式锁
     * set nx ex
     * @param timeOutSec
     * @return
     */
    boolean tryLock(Long timeOutSec);

    /**
     * 释放锁
     */
    void unlock();
}
