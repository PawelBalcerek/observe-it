# Observe-It

An open-source observability reference system demonstrating how to instrument, collect, and explore telemetry from
Kotlin/Spring Boot backend applications using OpenTelemetry and OpenSearch.

## Language

**Observed App**: The backend application under observation that processes incoming orders and emits telemetry via the
OpenTelemetry Java Agent. _Avoid_: Target app, server, demo service, main app

**Traffic Generator**: The companion application that continuously sends synthetic requests to the Observed App to
exercise normal, warning, and failure paths. _Avoid_: Load tester, benchmark runner, client app, simulator

**Order**: A simulated customer purchase request submitted by the Traffic Generator to the Observed App. _Avoid_:
Purchase, transaction, basket

**Order State**: The current lifecycle stage of an Order (`CREATED`, `RESERVED`, `PAID`, `SHIPPED`, `FAILED`, or
`CANCELLED`). _Avoid_: Status, phase, step

**Order Line Item**: A requested product SKU, quantity, and unit price within an Order. _Avoid_: Cart item, product row,
entry

**Checkout Scenario**: A predefined end-to-end behavioral path (such as Happy Path, Low-Stock Warning, or Payment
Gateway Timeout) driven by the Traffic Generator across an Order's lifecycle. _Avoid_: Test case, simulation script,
workload profile

**SS4O Log Index**: The OpenSearch index conforming to the Simple Schema for Observability where structured log records
from the Observed App and Traffic Generator are stored and queried. _Avoid_: Log bucket, log table, ELK index
