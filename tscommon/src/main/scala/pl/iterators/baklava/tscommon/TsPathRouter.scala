package pl.iterators.baklava.tscommon

import pl.iterators.baklava.*
import sttp.model.Method

/** Path-segment router tree shared by the TypeScript formatters, so all of them organize output by the same module boundaries.
  *
  * Endpoints nest by path segment: `/v1/auctions/{auctionId}/bids` is reached as `<root>.v1.auctions.byAuctionId.bids.<method>`. Path
  * parameters read as `by<Param>` — the router-tree spelling of the `getUsersByUserId` function-name convention (see
  * [[TsNaming.segmentKey]]).
  */
object TsPathRouter {

  type Endpoint = ((Option[Method], String), Seq[BaklavaSerializableCall])

  final case class RouterChild(rawSegment: String, node: RouterNode)
  final case class RouterNode(
      procedures: Map[String, Endpoint],
      children: Map[String, RouterChild]
  )
  object RouterNode {
    val empty: RouterNode = RouterNode(Map.empty, Map.empty)
  }

  /** One generated source module: a subtree mounted at `mountPath` inside the aggregate router. `fileSegments` is the module's
    * path-derived file identity (formatters map it to their own layout, e.g. `v1/auctions.contract.ts` or `v1/auctions/endpoints.ts`).
    * `spread = true` marks a subtree holding only the procedures declared directly at its mount point (e.g. `GET /v1` or `GET /`), merged
    * in via object spread.
    */
  final case class RouterModule(
      constName: String,
      fileSegments: List[String],
      mountPath: List[String],
      spread: Boolean,
      node: RouterNode
  )

  def hash4(s: String): String = f"${s.hashCode.abs}%x".take(4)

  private def insert(node: RouterNode, segments: List[String], methodKey: String, endpoint: Endpoint): RouterNode =
    segments match {
      case Nil =>
        // Distinct symbolic paths can collapse to one key path (`/users/{id}` vs `/users/by-id`);
        // the later (sorted) endpoint keeps a suffixed method key instead of silently overwriting.
        val key = if (node.procedures.contains(methodKey)) methodKey + hash4(endpoint._1._2) else methodKey
        node.copy(procedures = node.procedures.updated(key, endpoint))
      case segment :: rest =>
        val base = TsNaming.segmentKey(segment)
        val key  = node.children.get(base) match {
          case Some(child) if child.rawSegment != segment => base + hash4(segment)
          case _                                          => base
        }
        val childNode = node.children.get(key).map(_.node).getOrElse(RouterNode.empty)
        node.copy(children = node.children.updated(key, RouterChild(segment, insert(childNode, rest, methodKey, endpoint))))
    }

  /** Endpoints must be pre-sorted (path, then method) so collision-suffix assignment is deterministic. */
  def buildRouterTree(endpoints: Seq[Endpoint]): RouterNode =
    endpoints.foldLeft(RouterNode.empty) { case (tree, endpoint @ ((method, path), _)) =>
      val segments  = path.split("/").toList.filter(_.nonEmpty)
      val methodKey = method.map(_.method.toLowerCase).getOrElse("any")
      insert(tree, segments, methodKey, endpoint)
    }

  private def versionLike(segment: String): Boolean = segment.matches("v[0-9]+")

  /** Named (non-parameter) sub-resources directly under this node. `/admin/config` and `/admin/loggers` count; `/auctions/{id}` does not.
    */
  private def namedChildCount(node: RouterNode): Int =
    node.children.values.count(child => !TsNaming.isPathParamSegment(child.rawSegment))

  // A top-level segment is exploded into a folder-of-files when it's a grouping rather than a
  // single resource: a version prefix (`/v1/...`), or any segment fronting two or more named
  // sub-resources (`/admin/config`, `/admin/loggers`, ...). A single-resource area (`/users` +
  // `/users/{id}`) stays one flat file.
  private def isNamespace(child: RouterChild): Boolean =
    child.node.children.nonEmpty && (versionLike(child.rawSegment) || namedChildCount(child.node) >= 2)

  private def constNameOf(name: String): String = {
    val cleaned = name.filter(c => c.isLetterOrDigit || c == '_' || c == '$')
    if (cleaned.isEmpty || cleaned.head.isDigit) "_" + cleaned else cleaned
  }

  /** A namespace segment (a version prefix, or any grouping of ≥2 named sub-resources) becomes a folder with one module file per
    * sub-resource; a single-resource area becomes one flat module file.
    */
  def modulesOf(tree: RouterNode): Seq[RouterModule] = {
    val rootModule =
      if (tree.procedures.isEmpty) Seq.empty
      else Seq(RouterModule("root", List("root"), Nil, spread = true, tree.copy(children = Map.empty)))

    val areaModules = tree.children.toSeq.sortBy(_._1).flatMap { case (key, child) =>
      if (isNamespace(child)) {
        // Procedures declared directly on the namespace itself (e.g. `GET /v1`) go in an index
        // module, merged into the mount point via spread.
        val indexModule =
          if (child.node.procedures.isEmpty) Seq.empty
          else
            Seq(
              RouterModule(
                constNameOf(key + "Root"),
                List(key, "index"),
                List(key),
                spread = true,
                child.node.copy(children = Map.empty)
              )
            )
        val subModules = child.node.children.toSeq.sortBy(_._1).map { case (subKey, subChild) =>
          RouterModule(
            constNameOf(key + TsNaming.capitalize(subKey)),
            List(key, subKey),
            List(key, subKey),
            spread = false,
            subChild.node
          )
        }
        indexModule ++ subModules
      } else {
        Seq(RouterModule(constNameOf(key), List(key), List(key), spread = false, child.node))
      }
    }
    rootModule ++ areaModules
  }

  /** All endpoints in a subtree, in deterministic (key-sorted) order. */
  def endpointsOf(node: RouterNode): Seq[Endpoint] =
    node.procedures.toSeq.sortBy(_._1).map(_._2) ++
      node.children.toSeq.sortBy(_._1).flatMap { case (_, child) => endpointsOf(child.node) }

  private def reindent(block: String, depth: Int): String =
    if (depth == 0) block
    else {
      val pad = "  " * depth
      block.linesIterator.map(line => if (line.isEmpty) line else pad + line).mkString("\n")
    }

  /** Render a subtree as nested TS object entries. `renderProcedure(endpoint, entryKey)` must emit the entry at base indent 2 (the shape
    * every formatter's per-endpoint renderer already uses); nested levels are re-indented here.
    */
  def render(
      node: RouterNode,
      depth: Int,
      tsObjectKey: String => String,
      renderProcedure: (Endpoint, String) => String
  ): String = {
    val procedureEntries = node.procedures.toSeq.sortBy(_._1).map { case (methodKey, endpoint) =>
      reindent(renderProcedure(endpoint, methodKey), depth)
    }
    val procedureKeys = node.procedures.keySet
    val childEntries  = node.children.toSeq.sortBy(_._1).map { case (baseKey, child) =>
      // A static segment named like an HTTP method used at the same node (`GET /api` + `/api/get/...`)
      // would duplicate the object key; the child yields.
      val key = if (procedureKeys.contains(baseKey)) baseKey + hash4(child.rawSegment) else baseKey
      val pad = "  " * (depth + 1)
      s"$pad${tsObjectKey(key)}: {\n${render(child.node, depth + 1, tsObjectKey, renderProcedure)}\n$pad}"
    }
    (procedureEntries ++ childEntries).mkString(",\n")
  }
}
