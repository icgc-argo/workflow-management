/*
 * Copyright (c) 2020 The Ontario Institute for Cancer Research. All rights reserved
 *
 * This program and the accompanying materials are made available under the terms of the GNU Affero General Public License v3.0.
 * You should have received a copy of the GNU Affero General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
