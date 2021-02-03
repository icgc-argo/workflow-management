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

![WES States Diagram](docs/WES%20States%20and%20Transitions.png)

#### Workflow State Verifier

The workflow state verifier is essentially a state machine that ensure that only valid state transitions are processed whilst invalid transitions are ignored.

\_#\_#\_#\_INSERT_DIAGRAM_HERE\_#\_#\_#\_

#### Workflow Management Run State

In order for the state verifier to work correctly, a centralized record of runs, and their last valid state, will be maintained in a database (PostgreSQL).
This state will be used as the ground truth when making any decisions regarding state transitions within Workflow Management and as the backing repository
for the Workflow Management API (TBD but this will be available to any "middleware" services as well). 

### RabbitMQ Queues and How They Work

\_#\_#\_#\_INSERT_DIAGRAM_HERE\_#\_#\_#\_

## Middleware(ish) Service Support

Workflow Management approaches to concept of middleware a little differently than a more traditional application might. Let's first look at the default case of Management
running with no middleware whatsoever.

![Management - Standalone](docs/Managment%20Standalone.png)

In this case, there is no concept of queuing because there would be no difference between messages being set to `QUEUED` vs `INITIALIZING` as there is no action for any service to
take between those states. The diagram shows two distinct Management deploys but this is really only for getting the point across, this would likely be a single Management instance with
the right profiles enabled.

What if you did want to have something managing a queue, or processing templated requests, or sending notification on certain state transitions. We allow for this by enabling an
architecture where standing up services that speak to Management via it's API's is able to transition runs to any number of intermediary states `QUEUED` and `INITIALIZING`
(TBD, currently we only allow the base WES states however middleware would still be able to take any number of action before completing the transition).

![Management - Middleware(ish)](docs/Managment%20with%20Middleware.png)

In the diagram above you can see that the flow basically works as described. Management receives the request and queues the run immediately. A middleware service is listening on the `state.QUEUED` topic,
picks up the message for processing, and once complete sends a request to Management to initialize the run (with the updates state if applicable). Management then continues from this point just as if
it was running in standalone mode. In the future once the state transitions are configurable, any number of middleware can run in sequence or even in parallel as long as the ultimately get the message
back to management to initialize the run.  


## Tech Stack
- Java 11
- RabbitMQ 3.X
- PostgreSQL 13
- [Spring Reactive Stack](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html)
- [Reactor RabbitMQ Streams](https://pivotal.github.io/reactor-rabbitmq-streams/docs/current/)
- [Apache Avro](https://avro.apache.org/)

## Build and Run
