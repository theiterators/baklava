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

  private def constNameOf(name: String): String = {
    val cleaned = name.filter(c => c.isLetterOrDigit || c == '_' || c == '$')
    if (cleaned.isEmpty || cleaned.head.isDigit) "_" + cleaned else cleaned
  }

  /** A version prefix (`/v1/...`) is organizational, not a resource: modules live one level below it (module per `/v1/<area>`), while
    * non-versioned APIs get a module per top-level area.
    */
  def modulesOf(tree: RouterNode): Seq[RouterModule] = {
    val rootModule =
      if (tree.procedures.isEmpty) Seq.empty
      else Seq(RouterModule("root", List("root"), Nil, spread = true, tree.copy(children = Map.empty)))

    val areaModules = tree.children.toSeq.sortBy(_._1).flatMap { case (key, child) =>
      if (versionLike(child.rawSegment) && child.node.children.nonEmpty) {
        val versionRoot =
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
        versionRoot ++ subModules
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
