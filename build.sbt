import SonatypeKeys._
import sbtrelease._
import ReleaseStateTransformations._

sonatypeSettings

scalaVersion in ThisBuild := "2.11.2"

crossScalaVersions in ThisBuild := Seq("2.10.4", "2.11.2")

organization in ThisBuild := "com.trueaccord.scalapb"

profileName := "com.trueaccord"

resolvers in ThisBuild +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

pomExtra in ThisBuild := {
  <url>https://github.com/trueaccord/ScalaPB</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:github.com:trueaccord/ScalaPB.git</connection>
    <developerConnection>scm:git:git@github.com:trueaccord/ScalaPB.git</developerConnection>
    <url>github.com/trueaccord/ScalaPB</url>
  </scm>
  <developers>
    <developer>
      <id>thesamet</id>
      <name>Nadav S. Samet</name>
      <url>http://www.thesamet.com/</url>
    </developer>
  </developers>
}

lazy val projectReleaseSettings = releaseSettings ++ Seq(
  ReleaseKeys.crossBuild := true,
  ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value
)

lazy val root =
  project.in(file("."))
    .settings(
      publishArtifact := false,
      aggregate in sonatypeRelease := false
    ).settings(projectReleaseSettings: _*).aggregate(runtime, compilerPlugin, proptest)

lazy val runtime = project.in(file("scalapb-runtime")).settings(
  projectReleaseSettings:_*)

lazy val compilerPlugin = project.in(file("compiler-plugin")).settings(
  projectReleaseSettings:_*)

lazy val proptest = project.in(file("proptest"))
  .dependsOn(runtime, compilerPlugin)
    .configs( ShortTest )
    .settings( inConfig(ShortTest)(Defaults.testTasks): _*)
    .settings(
      publishArtifact := false,
      publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo"))),
      testOptions in ShortTest += Tests.Argument(
        // verbosity specified because of ScalaCheck #108.
        "-verbosity", "0",
        "-minSuccessfulTests", "10")
    )

lazy val ShortTest = config("short") extend(Test)

// For e2e test
val sbtPluginVersion = "0.3.2"

def genVersionFile(out: File, version: String): File = {
  out.mkdirs()
  val f = out / "Version.scala"
  val w = new java.io.FileOutputStream(f)
  w.write(s"""|// Generated by ScalaPB's build.sbt.
              |
              |package com.trueaccord.scalapb
              |
              |object Version {
              |  val sbtPluginVersion = "$sbtPluginVersion"
              |  val scalapbVersion = "$version"
              |}
              |""".stripMargin.getBytes("UTF-8"))
  w.close()
  f
}

val createVersionFile = TaskKey[Unit](
  "create-version-file", "Creates a file with the project version to be used by e2e.")

createVersionFile <<= (streams, baseDirectory, version in Compile) map {
  (streams, baseDirectory, version) =>
    val f1 = genVersionFile(baseDirectory / "e2e/project/project", version)
    streams.log.info(s"Created $f1")
    val f2 = genVersionFile(baseDirectory / "e2e/project/", version)
    streams.log.info(s"Created $f2")
}

