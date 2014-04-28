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

import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import junit.framework.Assert;
import org.junit.Test;

import com.liveramp.megadesk.base.state.InMemoryLocal;
import com.liveramp.megadesk.core.state.Variable;
import com.liveramp.megadesk.test.BaseTestCase;

import static org.junit.Assert.assertEquals;

public class TestAggregator extends BaseTestCase {

  private final static class SumAggregator implements Aggregator<Integer, Integer> {
    @Override
    public Integer initialValue() {
      return 0;
    }

    @Override
    public Integer aggregate(Integer aggregate, Integer value) {
      return aggregate + value;
    }

    @Override
    public Integer merge(Integer lhs, Integer rhs) {
      return lhs + rhs;
    }
  }

  private class SetAggregator<T> implements Aggregator<T, ImmutableSet<T>> {

    @Override
    public ImmutableSet<T> initialValue() {
      return ImmutableSet.of();
    }

    @Override
    public ImmutableSet<T> aggregate(T value, ImmutableSet<T> aggregate) {
      Set<T> result = Sets.newHashSet();
      result.addAll(aggregate);
      result.add(value);
      return ImmutableSet.copyOf(result);
    }

    @Override
    public ImmutableSet<T> merge(ImmutableSet<T> lhs, ImmutableSet<T> rhs) {
      Set<T> result = Sets.newHashSet(lhs);
      result.addAll(rhs);
      return ImmutableSet.copyOf(result);
    }
  }

  @Test
  public void testAggregators() throws Exception {
    Variable<Integer> variable = new InMemoryLocal<Integer>();
    Aggregator<Integer, Integer> sumAggregator = new SumAggregator();

    InterProcessAggregator<Integer, Integer> aggregator1 = new InterProcessAggregator<Integer, Integer>(variable, sumAggregator);
    InterProcessAggregator<Integer, Integer> aggregator2 = new InterProcessAggregator<Integer, Integer>(variable, sumAggregator);
    InterProcessAggregator<Integer, Integer> aggregator3 = new InterProcessAggregator<Integer, Integer>(variable, sumAggregator);

    iterAggregate(10, aggregator1);
    iterAggregate(5, aggregator2);
    iterAggregate(3, aggregator3);

    Assert.assertEquals(Integer.valueOf(36), aggregator1.readRemote());
  }

  private void iterAggregate(int number, InterProcessAggregator<Integer, Integer> aggregator) throws Exception {
    for (int i = 0; i < number; ++i) {
      aggregator.aggregateLocal(1);
    }
    aggregator.aggregateRemote();
    for (int i = 0; i < number; ++i) {
      aggregator.aggregateLocal(1);
    }
    aggregator.aggregateRemote();
  }

  @Test
  public void testKeyedAggregator() throws Exception {
    Variable<ImmutableMap<String, ImmutableSet<Integer>>> variable = new InMemoryLocal<ImmutableMap<String, ImmutableSet<Integer>>>();
    Aggregator<Integer, ImmutableSet<Integer>> aggregator = new SetAggregator<Integer>();

    InterProcessKeyedAggregator<String, Integer, ImmutableSet<Integer>> aggregator1 = new InterProcessKeyedAggregator<String, Integer, ImmutableSet<Integer>>(variable, aggregator);
    InterProcessKeyedAggregator<String, Integer, ImmutableSet<Integer>> aggregator2 = new InterProcessKeyedAggregator<String, Integer, ImmutableSet<Integer>>(variable, aggregator);
    InterProcessKeyedAggregator<String, Integer, ImmutableSet<Integer>> aggregator3 = new InterProcessKeyedAggregator<String, Integer, ImmutableSet<Integer>>(variable, aggregator);

    aggregator1.aggregateLocal("a", 1);
    aggregator1.aggregateLocal("b", 1);
    aggregator1.aggregateLocal("c", 1);
    aggregator3.aggregateLocal("e", 1);

    aggregator2.aggregateLocal("a", 1);
    aggregator2.aggregateLocal("d", 2);
    aggregator3.aggregateLocal("e", 2);

    aggregator2.aggregateLocal("a", 1);
    aggregator3.aggregateLocal("c", 3);
    aggregator3.aggregateLocal("d", 3);
    aggregator3.aggregateLocal("e", 3);

    aggregator1.aggregateRemote();
    aggregator2.aggregateRemote();
    aggregator3.aggregateRemote();

    assertEquals(ImmutableSet.of(1), aggregator1.readRemote("a"));
    assertEquals(ImmutableSet.of(1), aggregator1.readRemote("b"));
    assertEquals(ImmutableSet.of(1, 3), aggregator1.readRemote("c"));
    assertEquals(ImmutableSet.of(2, 3), aggregator1.readRemote("d"));
    assertEquals(ImmutableSet.of(1, 2, 3), aggregator1.readRemote("e"));
  }
}
