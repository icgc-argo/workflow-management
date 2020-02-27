package org.icgc.argo.workflow_management.util;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Integer.MAX_VALUE;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.common.base.Predicate;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@Slf4j
public class RandomGenerator {

  @Getter private final String id;
  private final Random random;
  @Getter private final long seed;

  private RandomGenerator(@NonNull String id, @NonNull Random random, long seed) {
    this.id = id;
    this.random = random;
    this.seed = seed;
  }

  /** Select a random enum with a filter */
  public <E extends Enum<E>> E randomEnum(@NonNull Class<E> enumClass, Predicate<E> filter) {
    val enumList = EnumSet.allOf(enumClass).stream().filter(filter).collect(toUnmodifiableList());
    return randomElement(enumList);
  }

  /**
   * Select a random Enum
   *
   * @param enumClass Enumeration class to use
   */
  public <E extends Enum<E>> E randomEnum(Class<E> enumClass) {
    return randomEnum(enumClass, x -> true);
  }

  /**
   * Select a random element from a list
   *
   * @param list input list to select from
   */
  public <T> T randomElement(List<T> list) {
    return list.get(generateRandomIntRange(0, list.size()));
  }

  /**
   * Generate a random integer between the interval [inclusiveMin, exlusiveMax)
   *
   * @param inclusiveMin inclusive lower bound
   * @param exlusiveMax exclusive upper bound
   */
  public int generateRandomIntRange(int inclusiveMin, int exlusiveMax) {
    checkArgument(
        inclusiveMin < exlusiveMax,
        "The inclusiveMin(%s) must be LESS THAN exclusiveMax(%s)",
        inclusiveMin,
        exlusiveMax);
    val difference = (long) exlusiveMax - inclusiveMin;
    checkArgument(
        difference <= MAX_VALUE,
        "The difference (%s) between exclusiveMax (%s) and (%s) must not exceed the integer exclusiveMax (%s)",
        difference,
        exlusiveMax,
        inclusiveMin,
        MAX_VALUE);
    return generateRandomInt(inclusiveMin, exlusiveMax - inclusiveMin);
  }

  /**
   * Generate a random integer between the interval [offset, offset+length]
   *
   * @param offset inclusive lower bound
   * @param length number of integers to randomize
   */
  public int generateRandomInt(int offset, int length) {
    long maxPossibleValue = offset + (long) length;

    checkArgument(length > 0, "The length(%s) must be GREATER THAN 0", length);
    checkArgument(
        maxPossibleValue <= (long) MAX_VALUE,
        "The offset(%s) + length (%s) = %s must be less than the max integer value (%s)",
        offset,
        length,
        maxPossibleValue,
        MAX_VALUE);
    return offset + random.nextInt(length);
  }

  /**
   * Create a {@link RandomGenerator} with an id, and specific seed value
   *
   * @param id unique name describing the generator
   * @param seed
   */
  public static RandomGenerator createRandomGenerator(String id, long seed) {
    return new RandomGenerator(id, new Random(seed), seed);
  }

  /**
   * Create a {@link RandomGenerator} with an id and randomly seed
   *
   * @param id unique name describing the generator
   */
  public static RandomGenerator createRandomGenerator(String id) {
    val seed = System.currentTimeMillis();
    return createRandomGenerator(id, seed);
  }
}
