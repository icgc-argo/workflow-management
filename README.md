# Workflow Management

This service is responsible for managing the lifecycle of a workflow execution within the larger Workflow Execution System (WES).
At it's simplest, Workflow Management takes a workflow request and initiates a run using the requested workflow engine (currently only supporting Nextflow).
However, it is possible to configure Workflow Management to go beyond being a direct initiator and instead it can be configured to queue runs
which can then be processed by various "middleware" before finally hitting the initiator, more on this below. In addition to queuing and initiating,
Workflow Management also handles cancelling runs and ensuring that run state transitions are valid (ie. a late received duplicate message to cancel a run
will be ignored as the run is already in a state of `CANCELLING` or `CANCELLED` at that point)

## Workflow Run Lifecycle and State Diagram

Workflow runs can be in a number of states as they make their way through the WES, these states can be updated by user-action (starting, cancelling),
by events in the Workflow Management domain (going from queued to initiating, cancelling to cancelled, etc), and from external events from the workflow
execution engine itself (Nextflow at the present time).

\_#\_#\_#\_INSERT_STATE_DIAGRAM_HERE\_#\_#\_#\_

#### Workflow State Verifier

The workflow state verifier is essentially a state machine that ensure that only valid state transitions are processed whilst invalid transitions are ignored.

\_#\_#\_#\_INSERT_DIAGRAM_HERE\_#\_#\_#\_

#### Workflow Run State



## Middleware Service Support

Workflow Management approaches to concept of middleware a little differently than a more traditional application might. Once a run is queued 

## Tech Stack
- Java 11
- RabbitMQ 3.X
- PostgreSQL 13
- [Spring Reactive Stack](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Reactor RabbitMQ Streams](https://pivotal.github.io/reactor-rabbitmq-streams/docs/current/)
- [Apache Avro](https://avro.apache.org/)

## Build and Run
