package org.icgc.argo.workflow_management.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConditionalPutMap<K, V> implements Map<K, V> {

  @NonNull private final Predicate<V> putConditionPredicate;
  @NonNull private final Map<K, V> internalMap;

  /** Decorated methods */
  @Override
  public V put(@NonNull K k, V v) {
    if (putConditionPredicate.test(v)) {
      return internalMap.put(k, v);
    }
    return null;
  }

  public V put(@NonNull K k, V v, @NonNull UnaryOperator<V> transform) {
    if (putConditionPredicate.test(v)) {
      return internalMap.put(k, transform.apply(v));
    }
    return null;
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> map) {
    map.forEach(this::put);
  }

  /** Delegated methods */
  @Override
  public int size() {
    return internalMap.size();
  }

  @Override
  public boolean isEmpty() {
    return internalMap.isEmpty();
  }

  @Override
  public boolean containsKey(Object o) {
    return internalMap.containsKey(o);
  }

  @Override
  public boolean containsValue(Object o) {
    return internalMap.containsValue(o);
  }

  @Override
  public V get(Object o) {
    return internalMap.get(o);
  }

  @Override
  public V remove(Object o) {
    return internalMap.remove(o);
  }

  @Override
  public void clear() {
    internalMap.clear();
  }

  @Override
  public Set<K> keySet() {
    return internalMap.keySet();
  }

  @Override
  public Collection<V> values() {
    return internalMap.values();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return internalMap.entrySet();
  }
}
