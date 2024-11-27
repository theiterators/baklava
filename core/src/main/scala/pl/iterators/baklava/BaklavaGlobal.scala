package pl.iterators.baklava

object BaklavaGlobal {
  def updateStorage(ctx: Baklava2Context[?, ?, ?, ?, ?], response: Baklava2ResponseContext[?]): Unit =
    synchronized {
      storage = storage :+ (ctx, response)
    }

  def print(): Unit =
    // println in openapi like format - mock
    storage.groupBy(_._1.symbolicPath).foreach { case (path, responses) =>
      println(s"Path: $path")
      responses.groupBy(_._1.method).foreach { case (method, responses) =>
        println(s"  Method: ${method.get.value}")
        responses.foreach { case (ctx, response) =>
          println(s"    Request: ${ctx.method.get} ${ctx.path} ${ctx.headers.headers.mkString(", ")} ${ctx.body}")
          println(s"    Response: ${response.status.status} ${response.headers.headers} ${response.body}")
        }
      }
    }

  @volatile private var storage: List[(Baklava2Context[?, ?, ?, ?, ?], Baklava2ResponseContext[?])] = List.empty
}
