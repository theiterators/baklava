---
sidebar_position: 6
title: Output Formats
---

# Output Formats

Baklava provides flexible output format options to accommodate various documentation and integration requirements. You can choose one or multiple output formats depending on your project's needs, allowing you to generate documentation that best suits your development workflow and API consumption patterns.

## OpenAPI Format

The OpenAPI format generates a standards-compliant OpenAPI specification file that can be used with various API documentation and testing tools. To use this format, you need to add the [`baklava-openapi`](installation.md#output-format-dependencies-required) dependency to your project and configure the OpenAPI information settings as described in the [OpenAPI Configuration](installation.md#openapi-configuration-required-for-openapi-format) section of the installation guide.

When you run your tests using `sbt test` or explicitly invoke the `sbt baklavaGenerate` command, Baklava will automatically generate an `openapi.yml` file in the `target/baklava/openapi/` directory. This YAML file conforms to the OpenAPI 3.0.1 specification and contains comprehensive documentation of all your API endpoints, including request/response schemas, parameters, headers, and security requirements.

If you're using Pekko HTTP as your HTTP server framework, you can also expose this generated OpenAPI specification through SwaggerUI for interactive API exploration. To enable this feature, add the [`baklava-pekko-http-routes`](installation.md#optional-swaggerui-support) dependency and configure the SwaggerUI routes as detailed in the [SwaggerUI and Routes Configuration](installation.md#swaggerui-and-routes-configuration) section. This allows developers and API consumers to interact with your API documentation directly through a web interface.

## Simple Format

The Simple format provides a straightforward HTML-based documentation output that's easy to view and share without requiring additional tools or infrastructure. To enable this format, you only need to add the [`baklava-simple`](installation.md#output-format-dependencies-required) dependency to your project—no additional configuration is required, making it the quickest option to get started with Baklava.

When you execute `sbt test` or `sbt baklavaGenerate`, Baklava will generate a collection of HTML files in the `target/baklava/simple/` directory. These files provide a clean, readable representation of your API documentation that can be opened directly in any web browser. The Simple format is particularly useful for quick reference during development or for sharing documentation with team members who prefer a lightweight, no-setup-required documentation format.

## TypeScript REST Format

The TypeScript REST (TS-REST) format generates a complete TypeScript package that provides type-safe client code for consuming your API. This format is especially valuable for full-stack TypeScript projects where you want to ensure type safety between your backend API and frontend client code. To use this format, you need to add the [`baklava-tsrest`](installation.md#output-format-dependencies-required) dependency and provide the TypeScript package configuration as described in the [TS-REST Configuration](installation.md#ts-rest-configuration-required-for-ts-rest-format) section.

When you run `sbt test` or `sbt baklavaGenerate`, Baklava will generate a complete TypeScript package in the `target/baklava/tsrest/` directory. This package includes TypeScript type definitions, contract definitions, and all the necessary files to publish it as an npm package. You can then import this package into your frontend applications to get full type safety and autocompletion when making API calls, significantly reducing the likelihood of runtime errors caused by API contract mismatches.

The generated TypeScript package can be published to your organization's private npm registry or used directly as a local dependency, providing a seamless integration between your Scala backend and TypeScript frontend applications.
