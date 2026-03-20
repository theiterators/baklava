import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import CodeBlock from '@theme/CodeBlock';

import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={styles.heroBanner}>
      <div className="container">
        <Heading as="h1" className={styles.heroTitle}>
          {siteConfig.title}
        </Heading>
        <p className={styles.heroSubtitle}>{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link className="button button--primary button--lg" to="/docs/intro">
            Get Started
          </Link>
          <Link className="button button--outline button--lg" to="https://github.com/theiterators/baklava" style={{marginLeft: '1rem'}}>
            GitHub
          </Link>
        </div>
      </div>
    </header>
  );
}

const exampleCode = `path("/users/{userId}")(
  supports(
    GET,
    pathParameters = p[Long]("userId"),
    summary = "Get user by ID",
    tags = Seq("Users")
  )(
    onRequest(pathParameters = 1L)
      .respondsWith[User](OK, description = "User found")
      .assert { ctx =>
        val response = ctx.performRequest(routes)
        response.body.name shouldBe "Alice"
      },
    onRequest(pathParameters = 999L)
      .respondsWith[ErrorResponse](NotFound, description = "User not found")
      .assert { ctx => ctx.performRequest(routes) }
  )
)`;

const installCode = `// project/plugins.sbt
addSbtPlugin("pl.iterators" % "baklava-sbt-plugin" % "1.1.1")

// build.sbt
libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-pekko-http" % "1.1.1" % Test,
  "pl.iterators" %% "baklava-scalatest"  % "1.1.1" % Test,
  "pl.iterators" %% "baklava-openapi"    % "1.1.1" % Test,
)`;

function Features(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          <div className={styles.feature}>
            <Heading as="h3">Tests = Documentation</Heading>
            <p>
              Your routing tests define paths, methods, parameters, security, and response examples.
              Baklava captures all of this and generates documentation automatically. No separate
              spec files to maintain.
            </p>
          </div>
          <div className={styles.feature}>
            <Heading as="h3">Multiple Output Formats</Heading>
            <p>
              Generate <strong>OpenAPI 3.0</strong> specs (with optional SwaggerUI),
              browsable <strong>HTML pages</strong>, or <strong>TypeScript contracts</strong> with
              ts-rest and Zod schemas. Add any format by adding a dependency — auto-discovered, zero config.
            </p>
          </div>
          <div className={styles.feature}>
            <Heading as="h3">Works With Your Stack</Heading>
            <p>
              Pekko HTTP or http4s. ScalaTest, Specs2, or MUnit. Scala 2.13 or 3.
              Mix and match — Baklava adapts to your project, not the other way around.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}

function CodeExample(): ReactNode {
  return (
    <section className={styles.codeSection}>
      <div className="container">
        <div className="row">
          <div className="col col--6">
            <Heading as="h2">Write tests, get docs</Heading>
            <p>
              The Baklava DSL is a thin layer on top of your existing test framework.
              Define your API structure, write assertions, run <code>sbt test</code> — documentation appears in <code>target/baklava/</code>.
            </p>
            <CodeBlock language="scala" title="build.sbt + project/plugins.sbt">
              {installCode}
            </CodeBlock>
          </div>
          <div className="col col--6">
            <CodeBlock language="scala" title="UserSpec.scala">
              {exampleCode}
            </CodeBlock>
          </div>
        </div>
      </div>
    </section>
  );
}

export default function Home(): ReactNode {
  return (
    <Layout
      title="API docs from routing tests"
      description="Generate OpenAPI, HTML Docs or TypeScript client contracts from Scala routing tests">
      <HomepageHeader />
      <main>
        <Features />
        <CodeExample />
      </main>
    </Layout>
  );
}
