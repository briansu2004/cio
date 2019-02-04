package com.examples

import cats.effect.{ExitCode, IO}
import cio.cluster_atomix.AtomixInterpreter
import cio._
import io.atomix.core.Atomix
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Cluster extends CIOApp {
  implicit val interpreter: Interpreter =
    AtomixInterpreter(
      Atomix
        .builder()
        .withMemberId("client1")
        .withAddress("localhost:8070")
        .build()
    )

  def main(args: List[String]): CIO[ExitCode] = {
    val a = CIO.pure(1)
    val b = CIO.delay(2 + 2)
    val c = CIO.raiseError[Int](new IllegalArgumentException).handleError { _ =>
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
    } yield a + b + c + d + e

    result.flatMap { result =>
      putStrLn(s"Result: $result")
    }.toExitCode
  }
}
