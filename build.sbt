import xerial.sbt.Sonatype._
import Dependencies._

lazy val snapshot: Boolean = true
lazy val v: String = {
  val vv = "0.1.0"
  if (!snapshot) vv
  else vv + "-SNAPSHOT"
}

lazy val scalaReflect = Def.setting {
  "org.scala-lang" % "scala-reflect" % scalaVersion.value
}

organization in ThisBuild := "ua.pp.itkpi"

val ScalaVersions = Seq(`scala-2-12`, `scala-2-11`)

def sonatypeProject(id: String, base: File) =
  Project(id, base)
    .enablePlugins(JmhPlugin)
    .settings(
      name := id,
      isSnapshot := snapshot,
      version := v,
      scalaVersion := `scala-2-12`,
      crossScalaVersions := ScalaVersions,
      publishTo := {
        val nexus = "https://oss.sonatype.org/"
        if (isSnapshot.value)
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases" at nexus + "service/local/staging/deploy/maven2")
      },
      updateOptions := updateOptions.value.withGigahorse(false),
      scalacOptions ++= Seq("-Ypartial-unification", "-feature"),
      sourceDirectory in Jmh := (sourceDirectory in Test).value,
      classDirectory in Jmh := (classDirectory in Test).value,
      dependencyClasspath in Jmh := (dependencyClasspath in Test).value,
      compile in Jmh := (compile in Jmh).dependsOn(compile in Test).value,
      run in Jmh := (run in Jmh).dependsOn(Keys.compile in Jmh).evaluated,
      resolvers += Resolver.sonatypeRepo("releases"),
      addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8"),
      libraryDependencies ++= commonDeps
    )

lazy val kernel = sonatypeProject(id = "cio", base = file("kernel"))
  .settings(
    libraryDependencies ++= Seq(
      Typelevel.catsEffect
    )
  )
lazy val cio_atomix = sonatypeProject(id = "cio-atomix", base = file("atomix"))
  .dependsOn(kernel)
  .settings(
    libraryDependencies ++= Seq(
      Atomix.kernel,
      Atomix.raft,
      ScalaCompat.java8compat
    )
  )

lazy val examples = Project(id = "cio-examples", base = file("examples"))
  .dependsOn(kernel, cio_atomix)
  .settings(
    name := "cio-examples",
    version := v,
    scalaVersion := `scala-2-12`,
    crossScalaVersions := ScalaVersions,
    scalacOptions += "-Ypartial-unification",
    isSnapshot := snapshot,
    skip in publish := true,
    publish := {},
    publishLocal := {},
    libraryDependencies ++= {
      val logbackVersion = "1.1.3"
      Seq(
        "ch.qos.logback" % "logback-classic" % logbackVersion
      )
    }
  )

lazy val root = Project(id = "cio-root", base = file("."))
  .aggregate(kernel, cio_atomix, examples)
  .settings(
    name := "cio-root",
    version := v,
    scalaVersion := `scala-2-12`,
    crossScalaVersions := ScalaVersions,
    scalacOptions += "-Ypartial-unification",
    isSnapshot := snapshot,
    skip in publish := true,
    publish := {},
    publishLocal := {},
    coverageExcludedPackages := ".*operations.*",
    coverageExcludedFiles := ".*orderingInstances | .*arrows* | .*ToCaseClass* | .*22*"
  )
