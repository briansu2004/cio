package com.examples

import cats.effect.IO
import cio._
import NodeContext.Implicits.local
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Local {
  def main(args: Array[String]): Unit = {
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
