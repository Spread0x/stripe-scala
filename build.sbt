import xerial.sbt.Sonatype._

name := "stripe-scala"

val currentScalaVersion = "2.13.2"
val scala211Version     = "2.11.11"
val circeVersion        = "0.13.0"

scalaVersion := currentScalaVersion

crossScalaVersions := Seq(currentScalaVersion, scala211Version)

organization := "com.github.jacke"

scalacOptions ++= Seq(
  "-target:jvm-1.8",
  "-encoding",
  "UTF-8",
  "-deprecation",         // warning and location for usages of deprecated APIs
  "-feature",             // warning and location for usages of features that should be imported explicitly
  "-unchecked",           // additional warnings where generated code depends on assumptions
  "-Xlint",               // recommended additional warnings
  "-Xcheckinit",          // runtime error when a val is not initialized due to trait hierarchies (instead of NPE somewhere else)
  "-language:postfixOps"
)

Defaults.itSettings

configs(IntegrationTest)

val enumeratumVersion      = "1.6.1"
val enumeratumCirceVersion = enumeratumVersion
val akkaStreamJson         = "3.4.0"

sonatypeProjectHosting := Some(GitHubHosting("Spread0x", "better-files-cats", "iamjacke@gmail.com"))
developers := List(
  Developer(id = "jacke", name = "jacke", email = "iamjacke@gmail.com", url = url("https://github.com/Jacke"))
)
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
sonatypeProfileName := "com.github.jacke"
publishMavenStyle := true

libraryDependencies ++= Seq(
  "com.typesafe.akka"          %% "akka-stream"       % "2.6.5",
  "com.typesafe.akka"          % "akka-http_2.13"     % "10.1.12",
  "org.mdedetrich"             %% "akka-stream-circe" % "0.6.0",
  "org.mdedetrich"             %% "akka-http-circe" % "0.6.0",
  "io.circe"                   %% "circe-core"        % circeVersion,
  "io.circe"                   %% "circe-generic"     % circeVersion,
  "io.circe"                   %% "circe-parser"      % circeVersion,
  "com.beachape"               %% "enumeratum"        % enumeratumVersion,
  "com.beachape"               %% "enumeratum-circe"  % enumeratumCirceVersion,
  "com.iheart"                 %% "ficus"             % "1.4.7",
  "com.typesafe.scala-logging" %% "scala-logging"     % "3.9.2",
  "org.scalatest"              %% "scalatest"         % "3.1.2" % "test, it",
  "ch.qos.logback"             % "logback-classic"    % "1.2.3" % "test, it"
)

homepage := Some(url("https://github.com/mdedetrich/stripe-scala"))

scmInfo := Some(
  ScmInfo(url("https://github.com/mdedetrich/stripe-scala"), "git@github.com:mdedetrich/stripe-scala.git")
)

developers := List(
  Developer("mdedetrich", "Matthew de Detrich", "mdedetrich@gmail.com", url("https://github.com/mdedetrich")),
  Developer("leonardehrenfried", "Leonard Ehrenfried", "leonard.ehrenfried@gmail.com", url("https://leonard.io"))
)

licenses += ("BSD 3 Clause", url("https://opensource.org/licenses/BSD-3-Clause"))

publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := (_ => false)

import ReleaseTransformations._
releaseCrossBuild := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value // Use publishSigned in publishArtifacts step
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)

val flagsFor11 = Seq(
  "-Yconst-opt",
)

val flagsFor12 = Seq(
)

scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, n)) if n >= 12 =>
      flagsFor12
    case Some((2, n)) if n == 11 =>
      flagsFor11
  }
}

parallelExecution in IntegrationTest := false
