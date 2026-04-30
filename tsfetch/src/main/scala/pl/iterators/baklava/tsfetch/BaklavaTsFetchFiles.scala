package pl.iterators.baklava.tsfetch

/** Static boilerplate files emitted into `target/baklava/tsfetch/` (outside `src/`). Kept here so the formatter stays focused on
  * call-driven code generation.
  */
private[tsfetch] object BaklavaTsFetchFiles {

  val staticFiles: Seq[(String, String)] = Seq(
    "tsconfig.json" -> tsconfigJson,
    "package.json"  -> packageJson
  )

  private def tsconfigJson: String =
    """{
      |  "compilerOptions": {
      |    "target": "ES2022",
      |    "module": "ESNext",
      |    "moduleResolution": "bundler",
      |    "strict": true,
      |    "declaration": true,
      |    "outDir": "dist",
      |    "esModuleInterop": true,
      |    "skipLibCheck": true,
      |    "lib": ["ES2022", "DOM"]
      |  },
      |  "include": ["src/**/*.ts"]
      |}
      |""".stripMargin

  private def packageJson: String =
    """{
      |  "name": "baklava-generated-client",
      |  "version": "0.0.1",
      |  "type": "module",
      |  "main": "dist/index.js",
      |  "types": "dist/index.d.ts",
      |  "scripts": {
      |    "build": "tsc"
      |  },
      |  "devDependencies": {
      |    "typescript": "^5.4.0"
      |  }
      |}
      |""".stripMargin
}
