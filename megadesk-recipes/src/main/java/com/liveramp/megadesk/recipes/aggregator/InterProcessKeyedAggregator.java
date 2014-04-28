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

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import com.liveramp.megadesk.core.state.Variable;

public class InterProcessKeyedAggregator<KEY, AGGREGAND, AGGREGATE> extends InterProcessAggregator<ImmutableMap<KEY, AGGREGAND>, ImmutableMap<KEY, AGGREGATE>> {

  public InterProcessKeyedAggregator(Variable<ImmutableMap<KEY, AGGREGATE>> variable,
                                     Aggregator<AGGREGAND, AGGREGATE> aggregator) {
    super(variable, new KeyedAggregator<KEY, AGGREGAND, AGGREGATE>(aggregator));
  }

  public AGGREGATE aggregateLocal(KEY key, AGGREGAND value) {
    ImmutableMap<KEY, AGGREGAND> aggregate = ImmutableMap.of(key, value);
    return aggregateLocal(aggregate).get(key);
  }

  public AGGREGATE readLocal(KEY key) {
    return readLocal().get(key);
  }

  public AGGREGATE readRemote(KEY key) throws Exception {
    ImmutableMap<KEY, AGGREGATE> remote = readRemote();
    if (remote == null) {
      return null;
    } else {
      return remote.get(key);
    }
  }

  private static class KeyedAggregator<KEY, AGGREGAND, AGGREGATE> implements Aggregator<ImmutableMap<KEY, AGGREGAND>, ImmutableMap<KEY, AGGREGATE>> {

    private final Aggregator<AGGREGAND, AGGREGATE> aggregator;

    private KeyedAggregator(Aggregator<AGGREGAND, AGGREGATE> aggregator) {
      this.aggregator = aggregator;
    }

    @Override
    public ImmutableMap<KEY, AGGREGATE> initialValue() {
      return ImmutableMap.of();
    }

    @Override
    public ImmutableMap<KEY, AGGREGATE> aggregate(ImmutableMap<KEY, AGGREGAND> value, ImmutableMap<KEY, AGGREGATE> aggregate) {
      Map<KEY, AGGREGATE> result = Maps.newHashMap(aggregate);
      aggregateMap(value, result);
      return ImmutableMap.copyOf(result);
    }

    @Override
    public ImmutableMap<KEY, AGGREGATE> merge(ImmutableMap<KEY, AGGREGATE> lhs, ImmutableMap<KEY, AGGREGATE> rhs) {
      Map<KEY, AGGREGATE> result = Maps.newHashMap(lhs);
      mergeMap(rhs, result);
      return ImmutableMap.copyOf(result);
    }

    private void aggregateMap(ImmutableMap<KEY, AGGREGAND> input, Map<KEY, AGGREGATE> result) {
      for (Map.Entry<KEY, AGGREGAND> entry : input.entrySet()) {
        KEY key = entry.getKey();
        AGGREGAND value = entry.getValue();
        if (!result.containsKey(key)) {
          result.put(key, aggregator.initialValue());
        }
        result.put(key, aggregator.aggregate(value, result.get(key)));
      }
    }

    private void mergeMap(ImmutableMap<KEY, AGGREGATE> input, Map<KEY, AGGREGATE> result) {
      for (Map.Entry<KEY, AGGREGATE> entry : input.entrySet()) {
        KEY key = entry.getKey();
        AGGREGATE aggregate = entry.getValue();
        if (!result.containsKey(key)) {
          result.put(key, aggregator.initialValue());
        }
        result.put(key, aggregator.merge(aggregate, result.get(key)));
      }
    }
  }
}
