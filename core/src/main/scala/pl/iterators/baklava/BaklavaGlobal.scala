package pl.iterators.baklava

import java.util.concurrent.atomic.AtomicReference

object BaklavaGlobal {
  def updateStorage(ctx: BaklavaRequestContext[?, ?, ?, ?, ?], response: BaklavaResponseContext[?, ?, ?]): Unit =
    storage.getAndUpdate(_ :+ (ctx -> response))

  def get: List[(BaklavaRequestContext[?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])] = storage.get

  def print(): Unit = {
    // println in openapi like format - mock
    storage.get.groupBy(_._1.symbolicPath).foreach { case (path, responses) =>
      println(s"Path: $path")
      responses.groupBy(_._1.method).foreach { case (method, responses) =>
        println(s"  Method: ${method.get.value}")
        responses.foreach { case (ctx, response) =>
          println(s"    Request: ${ctx.method.get} ${ctx.path} ${ctx.headers.headers.mkString(", ")} ${ctx.body}")
          println(s"    Response: ${response.status.status} ${response.headers.headers} ${response.body}")
        }
      }
    }
  }

  private val storage: AtomicReference[List[(BaklavaRequestContext[?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]] =
    new AtomicReference(List.empty)
}
