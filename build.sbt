name := "akka-persistence-sql-async"

version := "0.1"

val akkaVersion = "2.3.6"
val mauricioVersion = "0.2.14"

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %% "akka-actor"                        % akkaVersion,
  "com.typesafe.akka"   %% "akka-persistence-experimental"     % akkaVersion,
  "org.scalikejdbc"     %% "scalikejdbc-async"                 % "0.5.1",
  "com.github.mauricio" %% "mysql-async"                       % mauricioVersion % "provided",
  "com.github.mauricio" %% "postgresql-async"                  % mauricioVersion % "provided",
  "com.typesafe.akka"   %% "akka-persistence-tck-experimental" % akkaVersion     % "test",
  "com.typesafe.akka"   %% "akka-slf4j"                        % akkaVersion     % "test",
  "com.typesafe.akka"   %% "akka-testkit"                      % akkaVersion     % "test",
  "org.slf4j"            % "slf4j-log4j12"                     % "1.7.7"         % "test"
)
