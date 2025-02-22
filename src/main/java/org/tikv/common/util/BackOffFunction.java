/*
 * Copyright 2021 TiKV Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.tikv.common.util;

import java.util.concurrent.ThreadLocalRandom;

public class BackOffFunction {
  private final int base;
  private final int cap;
  private final BackOffer.BackOffStrategy strategy;
  private long lastSleep;
  private int attempts;

  private BackOffFunction(int base, int cap, BackOffer.BackOffStrategy strategy) {
    this.base = base;
    this.cap = cap;
    this.strategy = strategy;
    lastSleep = base;
  }

  public static BackOffFunction create(int base, int cap, BackOffer.BackOffStrategy strategy) {
    return new BackOffFunction(base, cap, strategy);
  }

  /**
   * Do back off in exponential with optional jitters according to different back off strategies.
   * See http://www.awsarchitectureblog.com/2015/03/backoff.html
   */
  long getSleepMs(long maxSleepMs) {
    long sleep = 0;
    long v = expo(base, cap, attempts);
    switch (strategy) {
      case NoJitter:
        sleep = v;
        break;
      case FullJitter:
        sleep = ThreadLocalRandom.current().nextLong(v);
        break;
      case EqualJitter:
        sleep = v / 2 + ThreadLocalRandom.current().nextLong(v / 2);
        break;
      case DecorrJitter:
        sleep = Math.min(cap, base + ThreadLocalRandom.current().nextLong(lastSleep * 3 - base));
        break;
    }

    if (maxSleepMs > 0 && sleep > maxSleepMs) {
      sleep = maxSleepMs;
    }

    attempts++;
    lastSleep = sleep;
    return lastSleep;
  }

  private int expo(int base, int cap, int n) {
    return (int) Math.min(cap, base * Math.pow(2.0d, n));
  }

  public enum BackOffFuncType {
    BoTiKVRPC,
    BoTxnLock,
    BoTxnLockFast,
    BoPDRPC,
    BoRegionMiss,
    BoUpdateLeader,
    BoServerBusy,
    BoTxnNotFound,
    BoCheckTimeout,
    BoCheckHealth
  }
}
