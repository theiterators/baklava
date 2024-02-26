package pl.iterators.baklava.generator

object Main {
  def main(args: Array[String]): Unit =
    Generator.generate(args(0), args(1), args(2), args.slice(3, args.length).toIndexedSeq)
}
