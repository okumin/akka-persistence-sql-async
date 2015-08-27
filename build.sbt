
lazy val root = (project in file(".")).aggregate(core, tckTest)

lazy val core = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "akka-persistence-sql-async"
  )

lazy val tckTest = (project in file("tck-test"))
  .settings(commonSettings: _*)
  .settings(
    name := "akka-persistence-sql-async-tck-test"
  )
  .dependsOn(core)

lazy val performanceTest = (project in file("performance-test"))
  .settings(commonSettings: _*)
  .settings(
    name := "akka-persistence-sql-async-performance-test"
  )
  .dependsOn(
    tckTest % "test->test"
  )

lazy val commonSettings = Seq(
  organization := "com.okumin",
  version := "0.2",
  scalaVersion := "2.11.7",
  parallelExecution in Test := false,
  libraryDependencies := commonDependencies
)

val akkaVersion = "2.3.12"
val mauricioVersion = "0.2.16"

lazy val commonDependencies = Seq(
  "com.typesafe.akka"   %% "akka-actor"                        % akkaVersion,
  "com.typesafe.akka"   %% "akka-persistence-experimental"     % akkaVersion,
  "org.scalikejdbc"     %% "scalikejdbc-async"                 % "0.5.5",
  "com.github.mauricio" %% "mysql-async"                       % mauricioVersion % "provided",
  "com.github.mauricio" %% "postgresql-async"                  % mauricioVersion % "provided",
  "com.typesafe.akka"   %% "akka-persistence-tck-experimental" % akkaVersion     % "test",
  "com.typesafe.akka"   %% "akka-slf4j"                        % akkaVersion     % "test",
  "com.typesafe.akka"   %% "akka-testkit"                      % akkaVersion     % "test",
  "org.slf4j"            % "slf4j-log4j12"                     % "1.7.12"        % "test"
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo <<= version { (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := {
    <url>https://github.com/okumin/akka-persistence-sql-async</url>
    <licenses>
      <license>
        <name>Apache 2 License</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:okumin/akka-persistence-sql-async.git</url>
      <connection>scm:git:git@github.com:okumin/akka-persistence-sql-async.git</connection>
    </scm>
    <developers>
      <developer>
        <id>okumin</id>
        <name>okumin</name>
        <url>http://okumin.com/</url>
      </developer>
    </developers>
  }
)
