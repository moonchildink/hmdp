package com.hmdp.utils;

public interface iLock {
    public boolean tryLock(long ttl);

    public void releaseLock();
}
