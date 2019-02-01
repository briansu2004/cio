package cio

sealed trait CIO[+A]

object CIO {
  // todo: implement
  def pure[A](a: A): CIO[A] = ???

  // todo: implement
  def raiseError[A](e: Throwable): CIO[A] = ???
}