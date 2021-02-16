/*
 * Copyright (c) 2021 The Ontario Institute for Cancer Research. All rights reserved
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

package org.icgc.argo.workflow_management;

import static org.icgc.argo.workflow_management.rabbitmq.WfMgmtRunMsgConverters.createWfMgmtRunMsg;
import static org.icgc.argo.workflow_management.util.TransactionUtils.*;
import static org.icgc.argo.workflow_management.util.WesUtils.generateWesRunId;
import static org.junit.Assert.*;

import com.pivotal.rabbitmq.stream.Transaction;
import java.time.Duration;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.gatekeeper.service.GatekeeperProcessor;
import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.testcontainers.containers.PostgreSQLContainer;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

@Slf4j
@ActiveProfiles("gatekeeper-test")
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@ContextConfiguration(initializers = {GateKeeperProcessorTests.Initializer.class})
public class GateKeeperProcessorTests {

  @ClassRule
  public static PostgreSQLContainer postgreSQLContainer =
      new PostgreSQLContainer("postgres:10-alpine")
          .withDatabaseName("gatekeeperdb")
          .withUsername("test")
          .withPassword("test");

  @Autowired GatekeeperProcessor processor;

  //  @Autowired
  //  ActiveRunsRepo repo;

  @Test
  public void testStreamsHappyPath() {
    val runId = generateWesRunId();
    assertPassWithNoInvalidMsgs(
        runId,
        RunStateWrapper.builder().runState(RunState.QUEUED).from(MsgFrom.RABBIT_QUEUE).build(),
        RunStateWrapper.builder()
            .runState(RunState.INITIALIZING)
            .from(MsgFrom.RABBIT_QUEUE)
            .build(),
        RunStateWrapper.builder().runState(RunState.RUNNING).from(MsgFrom.WEBLOG).build(),
        RunStateWrapper.builder().runState(RunState.COMPLETE).from(MsgFrom.WEBLOG).build());
  }

  @Test
  public void testStreamsSadPathWithExecutorError() {
    val runId = generateWesRunId();
    assertPassWithNoInvalidMsgs(
        runId,
        RunStateWrapper.builder().runState(RunState.QUEUED).from(MsgFrom.RABBIT_QUEUE).build(),
        RunStateWrapper.builder()
            .runState(RunState.INITIALIZING)
            .from(MsgFrom.RABBIT_QUEUE)
            .build(),
        RunStateWrapper.builder().runState(RunState.RUNNING).from(MsgFrom.WEBLOG).build(),
        RunStateWrapper.builder().runState(RunState.EXECUTOR_ERROR).from(MsgFrom.WEBLOG).build());
  }

  @Test
  public void testStreamsSadPathWithSystemError() {
    val runId = generateWesRunId();
    assertPassWithNoInvalidMsgs(
        runId,
        RunStateWrapper.builder().runState(RunState.QUEUED).from(MsgFrom.RABBIT_QUEUE).build(),
        RunStateWrapper.builder()
            .runState(RunState.INITIALIZING)
            .from(MsgFrom.RABBIT_QUEUE)
            .build(),
        RunStateWrapper.builder().runState(RunState.RUNNING).from(MsgFrom.WEBLOG).build(),
        RunStateWrapper.builder().runState(RunState.SYSTEM_ERROR).from(MsgFrom.WEBLOG).build());
  }

  @Test
  public void testStreamsInvalidMsgs() {
    val runId = generateWesRunId();
    val gatekeeperInput = TestPublisher.<Transaction<WfMgmtRunMsg>>create();
    val weblogInput = TestPublisher.<Transaction<WfMgmtRunMsg>>create();

    val gatekeeperOutFlux =
        processor
            .apply(gatekeeperInput.flux(), weblogInput.flux())
            .timeout(Duration.ofSeconds(300));

    val invalidMsg = createWfMgmtRunMsgTransaction(runId, RunState.INITIALIZING);

    // before running test msg is not rejected, its just in queue
    assertFalse(isRejected(invalidMsg));

    StepVerifier.create(gatekeeperOutFlux)
        .then(() -> gatekeeperInput.next(createWfMgmtRunMsgTransaction(runId, RunState.QUEUED)))
        .expectNextMatches(tx -> tx.get().getState().equals(RunState.QUEUED))
        .then(
            () -> gatekeeperInput.next(createWfMgmtRunMsgTransaction(runId, RunState.INITIALIZING)))
        .expectNextMatches(tx -> tx.get().getState().equals(RunState.INITIALIZING))
        .then(() -> weblogInput.next(createWfMgmtRunMsgTransaction(runId, RunState.RUNNING)))
        .expectNextMatches(tx -> tx.get().getState().equals(RunState.RUNNING))
        .then(() -> weblogInput.next(invalidMsg))
        .then(
            () -> {
              gatekeeperInput.complete();
              weblogInput.complete();
            })
        .expectComplete()
        .verifyThenAssertThat()
        .hasNotDroppedElements()
        .hasNotDroppedErrors()
        .hasNotDiscardedElements();

    // after running invalid msg has been rejected
    assertTrue(isRejected(invalidMsg));
  }

  private void assertPassWithNoInvalidMsgs(String runId, RunStateWrapper... statesInOrderToCheck) {
    val gatekeeperInput = TestPublisher.<Transaction<WfMgmtRunMsg>>create();
    val weblogInput = TestPublisher.<Transaction<WfMgmtRunMsg>>create();
    val gatekeeperOutFlux =
        processor
            .apply(gatekeeperInput.flux(), weblogInput.flux())
            .timeout(Duration.ofSeconds(300));

    StepVerifier.Step<Transaction<WfMgmtRunMsg>> stepVerifier =
        StepVerifier.create(gatekeeperOutFlux);

    for (final RunStateWrapper runStateWrapper : statesInOrderToCheck) {
      TestPublisher<Transaction<WfMgmtRunMsg>> publisherToUse;
      if (runStateWrapper.from.equals(MsgFrom.RABBIT_QUEUE)) {
        publisherToUse = gatekeeperInput;
      } else {
        publisherToUse = weblogInput;
      }

      stepVerifier =
          stepVerifier
              .then(
                  () ->
                      publisherToUse.next(
                          createWfMgmtRunMsgTransaction(runId, runStateWrapper.getRunState())))
              .assertNext(tx -> assertEquals(tx.get().getState(), runStateWrapper.getRunState()));
    }

    stepVerifier
        .then(
            () -> {
              gatekeeperInput.complete();
              weblogInput.complete();
            })
        .expectComplete()
        .verifyThenAssertThat()
        .hasNotDroppedElements()
        .hasNotDroppedErrors()
        .hasNotDiscardedElements();
  }

  private Transaction<WfMgmtRunMsg> createWfMgmtRunMsgTransaction(String runId, RunState state) {
    return wrapWithTransaction(createWfMgmtRunMsg(runId, state));
  }

  static class Initializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
      TestPropertyValues.of(
              "spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
              "spring.datasource.username=" + postgreSQLContainer.getUsername(),
              "spring.datasource.password=" + postgreSQLContainer.getPassword())
          .applyTo(configurableApplicationContext.getEnvironment());
    }
  }

  @Value
  @Builder
  static class RunStateWrapper {
    RunState runState;
    MsgFrom from;
  }

  enum MsgFrom {
    WEBLOG,
    RABBIT_QUEUE
  }
}
