package pl.iterators.baklava.tsrest

object BaklavaTsRestFiles {

  val files = List(
    (
      "package.json",
      """
        |{
        |  "name": "my-contracts",
        |  "version": "1.0.0",
        |  "description": "ts-rest contract package",
        |  "main": "dist/index.js",
        |  "types": "dist/index.d.ts",
        |  "exports": {
        |    ".": {
        |      "import": "./dist/index.js",
        |      "types": "./dist/index.d.ts"
        |    }
        |  },
        |  "files": [
        |    "dist"
        |  ],
        |  "scripts": {
        |    "build:js": "esbuild src/contracts.ts --bundle --platform=node --target=es2022 --format=esm --tree-shaking=true --outfile=dist/index.js",
        |    "build:dts": "dts-bundle-generator -o dist/index.d.ts src/contracts.ts",
        |    "build:package": "cp package-contracts.json dist/package.json && sed -i \"s/VERSION/${VERSION}/g\" dist/package.json",
        |    "build": "pnpm run build:js && pnpm run build:dts && pnpm run build:package"
        |  },
        |  "peerDependencies": {
        |    "@ts-rest/core": "^3.52.1",
        |    "zod": "^3.25.32"
        |  },
        |  "devDependencies": {
        |    "esbuild": "^0.25.2",
        |    "dts-bundle-generator": "^9.5.1",
        |    "typescript": "^5.8.3"
        |  }
        |}
        |""".stripMargin
    ),
    (
      "tsconfig.json",
      """
        |{
        |  "compilerOptions": {
        |    "target": "ES2022",
        |    "module": "ES2022",
        |    "declaration": true,
        |    "declarationMap": false,
        |    "emitDeclarationOnly": true,
        |    "strict": true,
        |    "moduleResolution": "node",
        |    "esModuleInterop": true,
        |    "baseUrl": "./src",
        |    "outDir": "dist"
        |  },
        |  "include": ["src/*.ts"]
        |}
        |""".stripMargin
    )
  )

}
