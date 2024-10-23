import com.typesafe.sbt.packager.docker.Cmd

import scala.io.Source
import scala.util.parsing.json.JSON

name := "codacy-staticcheck"

ThisBuild / scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-engine-scala-seed" % "4.0.0",
  "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
  "com.lihaoyi" %% "ujson" % "1.2.2",
  "org.scalatest" %% "scalatest" % "3.2.0" % Test
)

val staticcheckVersion = "2024.1.1"

dependsOn(shared)

lazy val shared = project
  .settings(libraryDependencies += "com.codacy" %% "codacy-analysis-cli-model" % "2.2.0")

lazy val `doc-generator` = project
  .settings(
    Compile / sourceGenerators += Def.task {
      val file = (Compile / sourceManaged).value / "codacy" / "staticcheck" / "Versions.scala"
      IO.write(file, s"""package com.codacy.staticcheck
                        |object Versions {
                        |  val staticcheckVersion: String = "$staticcheckVersion"
                        |}
                        |""".stripMargin)
      Seq(file)
    }.taskValue,
    libraryDependencies ++= Seq(
      "com.github.pathikrit" %% "better-files" % "3.9.1",
      "com.lihaoyi" %% "ujson" % "1.2.2",
    )
  )
  .dependsOn(shared)

enablePlugins(NativeImagePlugin)

nativeImageOptions ++= Seq(
  "-O1",
  "-H:+ReportExceptionStackTraces",
  "--no-fallback",
  "--no-server",
  "--report-unsupported-elements-at-runtime",
  "--static"
)
