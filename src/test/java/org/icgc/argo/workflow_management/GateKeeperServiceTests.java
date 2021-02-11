/// *
// * Copyright (c) 2021 The Ontario Institute for Cancer Research. All rights reserved
// *
// * This program and the accompanying materials are made available under the terms of the GNU
// Affero General Public License v3.0.
// * You should have received a copy of the GNU Affero General Public License along with
// * this program. If not, see <http://www.gnu.org/licenses/>.
// *
// * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
// * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
// * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
// * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
// * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
// * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
// * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
// */
//
// package org.icgc.argo.workflow_management;
//
// import lombok.val;
// import org.icgc.argo.workflow_management.rabbitmq.schema.EngineParams;
// import org.icgc.argo.workflow_management.rabbitmq.schema.RunState;
// import org.icgc.argo.workflow_management.rabbitmq.schema.WfMgmtRunMsg;
// import org.icgc.argo.workflow_management.gatekeeper.service.GateKeeperService;
// import org.junit.jupiter.api.Test;
//
// import java.time.Instant;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// public class GateKeeperServiceTests {
//    GateKeeperService service = new GateKeeperService();
//    private final String RUN_ID = "run-1";
//
//    @Test
//    void testGateKeeperHappyPath() {
//        val queuedMsg = createNewMsgWithState(RunState.QUEUED);
//        assertTrue(service.validateMessage(queuedMsg));
//        assertEquals(queuedMsg, service.getById(RUN_ID));
//
//        val initializedMsg = createNewMsgWithState(RunState.INITIALIZING);
//        assertTrue(service.validateMessage(initializedMsg));
//        assertEquals(initializedMsg, service.getById(RUN_ID));
//
//        val runningMsg = createNewMsgWithState(RunState.RUNNING);
//        assertTrue(service.validateMessage(runningMsg));
//        assertEquals(runningMsg, service.getById(RUN_ID));
//
//        // Completed msg is removed
//        val completedMsg = createNewMsgWithState(RunState.COMPLETE);
//        assertTrue(service.validateMessage(completedMsg));
//        assertNull(service.getById(RUN_ID));
//    }
//
//    @Test
//    void testGateKeeperCancellingQueued() {
//        val queudMsg = createNewMsgWithState(RunState.QUEUED);
//        assertTrue(service.validateMessage(queudMsg));
//        assertEquals(queudMsg, service.getById(RUN_ID));
//
//        // Queued is cancelled and removed
//        val cancelledMsg = createNewMsgWithState(RunState.CANCELED);
//        assertTrue(service.validateMessage(cancelledMsg));
//        assertNull(service.getById(RUN_ID));
//
//        // Msg canceled can't be initialized
//        val initializedMsg = createNewMsgWithState(RunState.INITIALIZING);
//        assertFalse(service.validateMessage(initializedMsg));
//        assertNull(service.getById(RUN_ID));
//    }
//
//    WfMgmtRunMsg createNewMsgWithState(RunState state) {
//       return WfMgmtRunMsg.newBuilder().setRunId(RUN_ID)
//                .setState(state)
//                .setWorkflowUrl("a-url")
//                .setWorkflowParamsJsonStr("")
//                .setWorkflowEngineParams(EngineParams.newBuilder().build())
//                .setTimestamp(Instant.now().toEpochMilli())
//                .build();
//    }
// }
