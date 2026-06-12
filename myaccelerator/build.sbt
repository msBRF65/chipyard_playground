organization := "edu.berkeley.cs"

version := "1.0"

name := "myaccelerator"

scalaVersion := "2.13.10"

libraryDependencies ++= Seq(
  "edu.berkeley.cs" %% "chisel3" % "3.6.0",
  "edu.berkeley.cs" %% "rocketchip" % "1.2.+",
  "org.scalanlp" %% "breeze" % "1.1")
