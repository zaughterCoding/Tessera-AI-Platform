package com.aiplatform.cache;

public interface ILock {
  boolean tryLock(long timeoutSec);

  void unlock();
}
