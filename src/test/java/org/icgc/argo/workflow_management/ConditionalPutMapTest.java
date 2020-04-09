package org.icgc.argo.workflow_management;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Objects;
import lombok.val;
import org.icgc.argo.workflow_management.util.ConditionalPutMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ConditionalPutMapTest {
  @Test
  public void testConditionalPutMap() {
    val d = new ConditionalPutMap<String, Object>(Objects::nonNull, new HashMap<>());
    d.put("resume", "somevalue");
    d.put("key1", null);
    assertTrue(d.containsKey("resume"));
    assertFalse(d.containsKey("key1"));
  }
}
