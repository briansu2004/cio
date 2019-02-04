package cio

import cats.effect.{ExitCode, IO, IOApp}

trait CIOApp extends IOApp {
  def interpreter: Interpreter

  def main(args: List[String]): CIO[ExitCode]

  final def run(args: List[String]): IO[ExitCode] = interpreter.toIO[ExitCode](main(args))
}

