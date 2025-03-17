import com.typesafe.sbt.packager.docker.Cmd

import scala.io.Source
import scala.util.parsing.json.JSON

name := "codacy-staticcheck"

ThisBuild / scalaVersion := "2.13.16"

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-engine-scala-seed" % "6.1.3",
  "org.scala-lang.modules" %% "scala-xml" % "2.3.0",
  "com.lihaoyi" %% "ujson" % "4.1.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

val staticcheckVersion = "2025.1.1"

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
      "com.github.pathikrit" %% "better-files" % "3.9.2",
      "com.lihaoyi" %% "ujson" % "4.1.0",
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
