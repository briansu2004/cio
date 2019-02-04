package com.examples

import cats.effect.{ExitCode, IO}
import cio._
import scala.concurrent.Future

object Local extends CIOApp {
  implicit val interpreter: Interpreter = Interpreter.Implicits.local

  def main(args: List[String]): CIO[ExitCode] = {
    val a = CIO.pure(1)
    val b = CIO
      .delay {
        println(s"You will see me once")
        2 + 2
      }
      .memoize()

    val c = CIO.raiseError[Int](new IllegalArgumentException).handleError { _ =>
      println(s"You will se me twice")
      -1
    }

    val d = CIO.liftIO(IO.pure(2))
    val e = CIO.liftFuture(Future.successful(1))

    val result = for {
      a <- a
      b <- b
      c <- c
      d <- d
      e <- e
      result = a + b + c + d + e
      _ <- putStrLn(s"Result: $result")
    } yield ()

    val program = for {
      _ <- result
      _ <- result
    } yield ()

    program.toExitCode
  }
}
