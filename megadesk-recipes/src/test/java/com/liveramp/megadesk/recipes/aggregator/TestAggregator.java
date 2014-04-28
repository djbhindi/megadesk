/**
 *  Copyright 2014 LiveRamp
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.liveramp.megadesk.recipes.aggregator;

import com.google.common.collect.ImmutableMap;
import junit.framework.Assert;
import org.junit.Test;

import com.liveramp.megadesk.base.state.InMemoryLocal;
import com.liveramp.megadesk.base.transaction.BaseTransactionExecutor;
import com.liveramp.megadesk.core.state.Variable;
import com.liveramp.megadesk.core.transaction.Transaction;
import com.liveramp.megadesk.recipes.transaction.Read;
import com.liveramp.megadesk.test.BaseTestCase;

import static org.junit.Assert.assertEquals;

public class TestAggregator extends BaseTestCase {

  private final static class SumAggregator implements Aggregator<Integer> {
    @Override
    public Integer initialValue() {
      return 0;
    }

    @Override
    public Integer aggregate(Integer integer, Integer newValue) {
      return integer + newValue;
    }
  }

  @Test
  public void testAggregators() throws Exception {
    Variable<Integer> variable = new InMemoryLocal<Integer>();
    Aggregator<Integer> sumAggregator = new SumAggregator();

    Thread thread1 = makeThread(10, variable, sumAggregator);
    Thread thread2 = makeThread(3, variable, sumAggregator);
    Thread thread3 = makeThread(5, variable, sumAggregator);

    thread1.start();
    thread2.start();
    thread3.start();

    thread1.join();
    thread2.join();
    thread3.join();

    Assert.assertEquals(Integer.valueOf(36), new BaseTransactionExecutor().execute(new Read<Integer>(variable)));
  }

  @Test
  public void testKeyedAggregator() throws Exception {
    Variable<ImmutableMap<String, Integer>> variable = new InMemoryLocal<ImmutableMap<String, Integer>>();
    Aggregator<Integer> aggregator = new SumAggregator();

    InterProcessKeyedAggregator<String, Integer> aggregator1 = new InterProcessKeyedAggregator<String, Integer>(variable, aggregator);
    InterProcessKeyedAggregator<String, Integer> aggregator2 = new InterProcessKeyedAggregator<String, Integer>(variable, aggregator);
    InterProcessKeyedAggregator<String, Integer> aggregator3 = new InterProcessKeyedAggregator<String, Integer>(variable, aggregator);

    aggregator1.aggregateLocal("a", 1);
    aggregator1.aggregateLocal("b", 1);
    aggregator1.aggregateLocal("c", 1);

    aggregator2.aggregateLocal("a", 2);
    aggregator2.aggregateLocal("d", 2);

    aggregator3.aggregateLocal("c", 3);
    aggregator3.aggregateLocal("d", 3);
    aggregator3.aggregateLocal("e", 3);

    aggregator1.aggregateRemote();
    aggregator2.aggregateRemote();
    aggregator3.aggregateRemote();

    assertEquals(3, (long)execute(new Read<ImmutableMap<String, Integer>>(variable)).get("a"));
    assertEquals(1, (long)execute(new Read<ImmutableMap<String, Integer>>(variable)).get("b"));
    assertEquals(4, (long)execute(new Read<ImmutableMap<String, Integer>>(variable)).get("c"));
    assertEquals(5, (long)execute(new Read<ImmutableMap<String, Integer>>(variable)).get("d"));
    assertEquals(3, (long)execute(new Read<ImmutableMap<String, Integer>>(variable)).get("e"));
  }

  private static <T> T execute(Transaction<T> transaction) throws Exception {
    return new BaseTransactionExecutor().execute(transaction);
  }

  private Thread makeThread(
      final int amount,
      final Variable<Integer> variable,
      final Aggregator<Integer> sumAggregator) {

    final InterProcessAggregator<Integer> aggregator =
        new InterProcessAggregator<Integer>(variable, sumAggregator);

    Runnable runnable = new Runnable() {

      @Override
      public void run() {
        try {
          for (int i = 0; i < amount; i++) {
            aggregator.aggregateLocal(1);
          }
          aggregator.aggregateRemote();
          for (int i = 0; i < amount; i++) {
            aggregator.aggregateLocal(1);
          }
          aggregator.aggregateRemote();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };

    return new Thread(runnable);
  }
}