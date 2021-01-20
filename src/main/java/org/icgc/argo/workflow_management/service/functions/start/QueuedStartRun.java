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

package org.icgc.argo.workflow_management.service.functions.start;

import static org.icgc.argo.workflow_management.service.WebLogEventSender.Event.QUEUED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.icgc.argo.workflow_management.service.WebLogEventSender;
import org.icgc.argo.workflow_management.service.functions.StartRunFunc;
import org.icgc.argo.workflow_management.service.model.RunParams;
import org.icgc.argo.workflow_management.wes.controller.model.RunsResponse;
import reactor.core.publisher.Mono;

/**
 * StartRunFunction that queues workflow run commands to weblog, which can be consumed by middleware
 * service(s) for various usecases and later consumed by queuedToStartConsumer instance for
 * startRun.
 */
@Slf4j
@RequiredArgsConstructor
public class QueuedStartRun implements StartRunFunc {
  private final WebLogEventSender webLogSender;

  @Override
  public Mono<RunsResponse> apply(RunParams runParams) {
    webLogSender.sendManagementEvent(runParams, QUEUED);
    return Mono.just(new RunsResponse(runParams.getRunName()));
  }
}
