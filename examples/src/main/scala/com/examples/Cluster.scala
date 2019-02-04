package com.examples
import cats.effect.IO
import cio.{CIO, Interpreter}
import io.atomix.core.Atomix
import cio.cluster_atomix.AtomixInterpreter
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

object Cluster {
  def main(args: Array[String]): Unit = {
    implicit val interpreter: Interpreter =
      AtomixInterpreter(
        Atomix
          .builder()
          .withMemberId("client1")
          .withAddress("localhost:8070")
          .build()
      )

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

    println(s"Result: ${Await.result(result.unsafeToFuture, Duration.Inf)}")
  }
}
