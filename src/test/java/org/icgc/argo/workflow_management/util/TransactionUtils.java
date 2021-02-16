package org.icgc.argo.workflow_management.util;

import com.pivotal.rabbitmq.stream.Transaction;
import com.pivotal.rabbitmq.stream.Transactional;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;

@UtilityClass
public final class TransactionUtils {
  private static final Field receivedRejectedField = getReceivedRejectedField();
  private static final Field receivedRequeuedField = getReceivedRequeuedField();
  private static final Field committedField = getCommittedField();

  @SneakyThrows
  public static <T> Transaction<T> wrapWithTransaction(T obj) {
    final Consumer<Transactional.Identifier> NOOP = something -> {};
    return new Transactional<>(
        new Transactional.Identifier("componentTest", 0), obj, NOOP, NOOP, NOOP);
  }

  @SneakyThrows
  public static Boolean isRejected(Object transaction) {
    return receivedRejectedFieldValue(transaction).get()
        && !receivedRequeuedFieldValue(transaction).get()
        && committedFieldValue(transaction);
  }

  @SneakyThrows
  public static Boolean isRequeued(Object transaction) {
    return !receivedRejectedFieldValue(transaction).get()
        && receivedRequeuedFieldValue(transaction).get()
        && committedFieldValue(transaction);
  }

  @SneakyThrows
  public static Boolean isAcknowledged(Object transaction) {
    return !receivedRejectedFieldValue(transaction).get()
        && !receivedRequeuedFieldValue(transaction).get()
        && committedFieldValue(transaction);
  }

  @SneakyThrows
  public static Boolean isNotAcknowledged(Object transaction) {
    return !receivedRejectedFieldValue(transaction).get()
        && !receivedRequeuedFieldValue(transaction).get()
        && !committedFieldValue(transaction);
  }

  @SneakyThrows
  private static AtomicBoolean receivedRejectedFieldValue(Object transaction) {
    return (AtomicBoolean) receivedRejectedField.get(transaction);
  }

  @SneakyThrows
  private static AtomicBoolean receivedRequeuedFieldValue(Object transaction) {
    return (AtomicBoolean) receivedRequeuedField.get(transaction);
  }

  @SneakyThrows
  private static Boolean committedFieldValue(Object transaction) {
    return (Boolean) committedField.get(transaction);
  }

  @SneakyThrows
  private static Field getReceivedRejectedField() {
    val receivedRejectedField = Transactional.class.getDeclaredField("receivedRejected");
    receivedRejectedField.setAccessible(true);
    return receivedRejectedField;
  }

  @SneakyThrows
  private static Field getReceivedRequeuedField() {
    val receivedRequeuedField = Transactional.class.getDeclaredField("receivedRequeued");
    receivedRequeuedField.setAccessible(true);
    return receivedRequeuedField;
  }

  @SneakyThrows
  private static Field getCommittedField() {
    val committedField = Transactional.class.getDeclaredField("committed");
    committedField.setAccessible(true);
    return committedField;
  }
}
