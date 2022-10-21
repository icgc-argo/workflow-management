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

package org.icgc.argo.workflow_management.gatekeeper.service;

import com.pivotal.rabbitmq.stream.Transaction;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc.argo.workflow_management.streams.schema.WfMgmtRunMsg;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Functional interface taking two fluxes (one for gatekeeper input and other for weblog input
 * wfmgmtrunmsgs) and returning one merged flux of allowed wfmgmtrunmsgs
 */
@Slf4j
@Profile("gatekeeper")
@Component
@RequiredArgsConstructor
public class GatekeeperProcessor
    implements BiFunction<
        Flux<Transaction<WfMgmtRunMsg>>,
        Flux<Transaction<WfMgmtRunMsg>>,
        Flux<Transaction<WfMgmtRunMsg>>> {
  private final GateKeeperService service;

  @Override
  public Flux<Transaction<WfMgmtRunMsg>> apply(
      Flux<Transaction<WfMgmtRunMsg>> msgFluxFromGatekeeperInput,
      Flux<Transaction<WfMgmtRunMsg>> msgFluxFromWeblog) {
    return Flux.merge(
        msgFluxFromGatekeeperInput.transform(getGateKeeperInputMsgTransformer()),
        msgFluxFromWeblog.transform(getWeblogInputMsgsTransformer()));
  }

  private Function<Flux<Transaction<WfMgmtRunMsg>>, Flux<Transaction<WfMgmtRunMsg>>>
      getWeblogInputMsgsTransformer() {
    return transactionFlux ->
        transactionFlux
            .doOnNext(tx -> log.debug("GateKeeperConsumer Received: " + tx.get()))
            .handle(
                (tx, sink) -> {
                  val msg = tx.get();
                  // WeblogEvents only change run state in gatekeeper service, not other params
                  val allowedMsg =
                      service.checkWithExistingAndUpdateStateOnly(msg.getRunId(), msg.getState());
                  log.debug("getWeblogInputMsgsTransformer:allowedMsg: {}",allowedMsg);
                  if (allowedMsg.isEmpty()) {
                    tx.reject();
                    log.debug("WeblogConsumer - Gatekeeper Rejected: {}, YOU SHALL NOT PASS!", msg);
                    return;
                  }

                  sink.next(tx.map(allowedMsg.get()));
                });
  }

  private Function<Flux<Transaction<WfMgmtRunMsg>>, Flux<Transaction<WfMgmtRunMsg>>>
      getGateKeeperInputMsgTransformer() {
    return fluxOfInterest ->
        fluxOfInterest
            .doOnNext(tx -> log.debug("GateKeeperConsumer Received: " + tx.get()))
            .handle(
                (tx, sink) -> {
                  val msg = tx.get();
                  val allowedMsg = service.checkWfMgmtRunMsgAndUpdate(msg);
                  log.debug("getGateKeeperInputMsgTransformer:allowedMsg: {}",allowedMsg);
                  if (allowedMsg.isEmpty()) {
                    tx.reject();
                    log.debug(
                        "GateKeeperConsumer - Gatekeeper Rejected: {}, YOU SHALL NOT PASS!", msg);
                    return;
                  }

                  sink.next(tx.map(allowedMsg.get()));
                });
  }
}
