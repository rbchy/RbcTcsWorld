# ADR 0002: One Spring Boot application, one package per business capability

- **Status:** Accepted
- **Date:** 2026-10 (Module 0, first commit)

## Context
An Amazon-inspired shop has many capabilities: catalog, cart, pricing, coupons, checkout, orders, inventory,
payment, fulfillment, returns, reviews, wishlist, notifications. The project's purpose is to be **tested**
thoroughly by one person, with every gate running on every push.

## Decision
- A **modular monolith**: one deployable backend, one PostgreSQL database.
- Each capability is its own package (`product/`, `cart/`, `order/`, `payment/`, ...). Each package owns its
  entities, service, controller and tests.
- Packages talk through services or Spring events (`OrderNotificationEvent`), never through each other's
  repositories.
- Admin endpoints sit in `admin/` and call the capability services.

## Alternatives considered
- **Microservices:** they would show distributed-systems testing (contract tests between services, eventual
  consistency). But they cost a service mesh, many databases and slow CI. They would also bury the actual QA work.
- **Layered packages** (`controller/`, `service/`, `repository/`): every feature change would touch every layer
  folder, and module boundaries would be invisible.

## Consequences
- One `docker compose up`. Integration tests start one Spring context. Transactions keep stock and orders
  consistent (no overselling under 300 parallel buyers, NFR-PERF-02).
- A capability can still be cut out later along its package line. Search, for example, lives inside `product/`
  until it needs its own engine.
