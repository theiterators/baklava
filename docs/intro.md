---
sidebar_position: 1
title: Introduction to Baklava
---

# Baklava - Test-Based API Documentation Generator

Baklava is a Scala library that generates API documentation directly from your unit tests, taking a fundamentally different approach to API documentation.

## Overview

Most OpenAPI documentation follows a contract-first approach, where you define an API contract upfront, generate documentation from it, and then implement the contract in your code.

This library takes a fundamentally different test-based approach:
* **Tested by design** - Your documented endpoints are already tested, ensuring they actually work
* **Selective exposure** - Only expose the endpoints you explicitly test and want to document
* **Non-intrusive** - No need to restructure your existing code or follow specific router patterns - simply add endpoint description to your tests in the current codebase and generate documentation

This approach gives you living documentation that stays in sync with your actual implementation while maintaining full control over what gets exposed.

## Key Benefits

- **Always Up-to-Date**: Documentation is generated from actual working tests, eliminating the common problem of outdated API docs
- **Quality Assurance**: If your test passes, your documented endpoint works
- **Minimal Overhead**: Add documentation DSL to existing tests without changing your application architecture
- **Framework Agnostic**: Works with multiple testing frameworks (ScalaTest, Specs2, MUnit)
- **Multiple Output Formats**: Generate OpenAPI, simple documentation, or TypeScript REST definitions
- **HTTP Server Integration**: Works with popular Scala HTTP servers (Pekko HTTP, HTTP4s)
- **Standard Output**: Generates standard OpenAPI 3.0 documentation that works with any OpenAPI tooling

## Output Formats

- **OpenAPI** - Standard OpenAPI 3.0 YAML/JSON specification
- **Simple** - Simplified documentation format
- **TypeScript REST** - TypeScript client definitions

## Quick Start

1. [Installation](installation.md)
2. [Integration with Pekko HTTP](pekko-http.md)
3. [Integration with http4s](http4s.md)
4. [DSL Reference](dsl-reference.md)
5. [Output Formats](output-formats.md)
6. [Configuration](configuration.md)
7. [Examples](examples.md)

## Supported HTTP Servers and Test Frameworks

- Specs2: [with Pekko HTTP](pekko-http.md#spec2), [with http4s](http4s.md#spec2)
- ScalaTest: [with Pekko HTTP](pekko-http.md#scalatest), [with http4s](http4s.md#scalatest)
- MUnit: [with Pekko HTTP](pekko-http.md#munit), [with http4s](http4s.md#munit)
