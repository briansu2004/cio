import sbt._

object Dependencies {
  val `scala-2-12`      = "2.12.8"
  val `scala-2-11`      = "2.11.12"
  val testV             = "3.0.4"
  val catsEffectsV      = "1.1.0"
  val slf4jV            = "1.7.25"
  val scalaJava8CompatV = "0.9.0"
  val mockitoV          = "2.23.4"
  val skuberV           = "2.1.0"
  val grpcV             = "1.18.0"
  val ckiteV            = " 0.2.1"
  val atomixV           = "3.0.6"

  object Testing {
    val scalastic = "org.scalactic" %% "scalactic"   % testV withSources ()
    val scalatest = "org.scalatest" %% "scalatest"   % testV withSources ()
    val mockito   = "org.mockito"   % "mockito-core" % mockitoV withSources ()
  }

  object K8s {
    val skuber = "io.skuber" %% "skuber" % skuberV
  }

  object GRPC {
    val nettyShaded = "io.grpc" % "grpc-netty-shaded" % grpcV
    val protobuf    = "io.grpc" % "grpc-protobuf"     % grpcV
    val stub        = "io.grpc" % "grpc-stub"         % grpcV
  }

  object Atomix {
    val kernel = "io.atomix" % "atomix"      % atomixV
    val raft   = "io.atomix" % "atomix-raft" % atomixV
  }

  object Typelevel {
    val catsEffect = "org.typelevel" %% "cats-effect" % catsEffectsV withSources ()
  }

  object Utils {
    val slf4j = "org.slf4j" % "slf4j-api" % slf4jV withSources ()
  }

  object Macros {
    val resetallattrs = "org.scalamacros" %% "resetallattrs" % "1.0.0" withSources ()
  }

  object ScalaCompat {
    val java8compat = "org.scala-lang.modules" %% "scala-java8-compat" % scalaJava8CompatV withSources ()
  }

  val commonDeps = Seq(
    Testing.scalastic,
    Testing.scalatest % "test",
    Testing.mockito   % "test",
    Utils.slf4j
  )
}
