/*
 * Copyright IBM Corporation 2016-2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.typesafe.sbt.packager.MappingsHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import com.typesafe.sbt.packager.docker._

// Scalaiform auto code formatting settings
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

SbtScalariform.scalariformSettings

name := "csync"
lazy val commonSettings = Seq(
  version := "1.4.0",
  scalaVersion := "2.12.1",
  scalacOptions ++= Seq("-deprecation") /*, "-Xexperimental")*/ ,
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(SpacesAroundMultiImports, false),
  exportJars := true,
  mappings in Universal += ((packageBin in Compile) map { jar =>
    jar -> ("lib/" + jar.getName)
  }).value
)

lazy val postgresDriver = "org.postgresql" % "postgresql" % "9.4.1208"
lazy val logging = "org.slf4j" % "slf4j-simple" % "1.7.21"
lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1"
lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4"
lazy val json4s = "org.json4s" %% "json4s-native" % "3.5.0"

// Keeping the silly style thing happy
lazy val P9000 = 9000
lazy val P9443 = 9443

lazy val server = (project in file("."))
  .enablePlugins(UniversalPlugin, JavaAppPackaging, DockerPlugin)
  .settings(commonSettings: _*)
  .aggregate(core, vertx).dependsOn(core, vertx)
  .aggregate(core)
  .settings(
    mappings in Universal ++= directory("vertx/public"),
    aggregate in Docker := false,
    NativePackagerKeys.maintainer in Docker := "CSync",
    NativePackagerKeys.dockerExposedPorts in Docker := Seq(P9000, P9443),
    NativePackagerKeys.dockerBaseImage := "ibmcom/csync-base",
    NativePackagerKeys.daemonUser in Docker := "postgres",
    NativePackagerKeys.dockerCommands := dockerCommands.value.filterNot {
      // ExecCmd is a case class, and args is a varargs variable, so you need to bind it with @
      // case ExecCmd("USER", args @ _*) => true
      case Cmd("USER", arg) => true
      case ExecCmd("ENTRYPOINT", args@_*) => true
      // don't filter the rest
      case _ => false
    },

    NativePackagerKeys.dockerCommands ++= Seq(
      Cmd("RUN", "echo 'net.ipv4.icmp_echo_ignore_broadcasts = 1'>>/etc/sysctl.conf&&echo 'net.ipv4.tcp_syncookies = 1'>>/etc/sysctl.conf&&echo 'net.ipv4.ip_forward = 0'>>/etc/sysctl.conf"),
      Cmd("RUN", "touch /var/log/wtmp /etc/security/opasswd &&chmod 664 /var/log/wtmp&&chmod 600 /etc/security/opasswd"),
      Cmd("RUN", "sed -i.foo 's/.*PASS_MAX_DAYS.*$/PASS_MAX_DAYS  90/' /etc/login.defs"),
      Cmd("RUN", "echo 'password  requisite pam_cracklib.so retry=3 minlen=8' >> /etc/pam.d/common-password"),
      Cmd("RUN", "sed -i.foo 's/.*PASS_MIN_DAYS.*$/PASS_MIN_DAYS  1/' /etc/login.defs"),
      Cmd("RUN", "apt-mark hold postgresql-common && apt-key update && apt-get update && apt-get -y install apt-utils && apt-get -y upgrade && apt-get clean && rm -rf /var/lib/apt/lists/*"),
      ExecCmd("ENTRYPOINT", "/csync.sh")))

lazy val vertx = project.dependsOn(core)
  .enablePlugins(UniversalPlugin, JavaAppPackaging, DockerPlugin)
  .settings(commonSettings: _*)
  .settings(

    mainClass := Some("com.ibm.csync.vertx.Main"),

    libraryDependencies ++= Seq(

      // Vertx
      "io.vertx" % "vertx-core" % "3.4.1",
      "io.vertx" % "vertx-codegen" % "3.4.0",

      logging,
      json4s,
      postgresDriver,
      "com.zaxxer" % "HikariCP" % "2.4.6",
      "com.ibm.bluemix.deploymenttracker" % "cf-java-app-tracker-client" % "0.3.0"
    ),

    libraryDependencies ++= Seq(
      scalaTest,
      scalaCheck
    ) map {
      _ % "test"
    }
  )

lazy val core = project
  .settings(commonSettings: _*)
  .enablePlugins(UniversalPlugin, JavaAppPackaging, DockerPlugin)
  .settings(

    libraryDependencies ++= Seq(

      // Brilliant source info macros
      "com.lihaoyi" %% "sourcecode" % "0.1.3",

      "me.chrons" %% "boopickle" % "1.2.5",

      // Rest client
      "org.scalaj" %% "scalaj-http" % "2.3.0",

      // Logging
      "org.slf4j" % "slf4j-api" % "1.7.21",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",

      // Rabbit
      "com.rabbitmq" % "amqp-client" % "3.6.2",

      // google-api-client, version 1.22.0
      "com.google.api-client" % "google-api-client" % "1.22.0",
      "com.google.api-client" % "google-api-client-gson" % "1.22.0",

      json4s
      // avoid conflict
      //xml
    ),

    libraryDependencies ++= Seq(
      postgresDriver,
      logging,
      scalaTest,
      scalaCheck
    ) map {
      _ % "test"
    }
  )

lazy val testScalastyle = taskKey[Unit]("testScalastyle")
scalastyleSources in Test := Seq(file("core/"))
testScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Test).toTask("").value
(test in Test) := ((test in Test) dependsOn testScalastyle).value

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

run in Compile := (run in Compile in vertx).evaluated
mainClass in Compile := (mainClass in Compile in vertx).value
