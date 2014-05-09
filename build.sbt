import AssemblyKeys._

name               := "Mellite"

version            := "0.8.0-SNAPSHOT"

organization       := "de.sciss"

homepage           := Some(url("https://github.com/Sciss/" + name.value))

description        := "A computer music application based on SoundProcesses"

licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt"))

scalaVersion       := "2.11.0"

crossScalaVersions := Seq("2.11.0", "2.10.4")

libraryDependencies ++= Seq(
  "de.sciss" %% "soundprocesses"                  % "2.4.0-SNAPSHOT",  // computer-music framework
  "de.sciss" %% "scalacolliderswing-interpreter"  % "1.16.0", // REPL
  "de.sciss" %% "lucreswing"                      % "0.2.2-SNAPSHOT",  // reactive Swing components
  "de.sciss" %% "lucrestm-bdb"                    % "2.0.4",  // database backend
  "de.sciss" %% "audiowidgets-app"                % "1.6.2",  // audio application widgets
  "de.sciss" %% "desktop-mac"                     % "0.5.3",  // desktop framework; TODO: should be only added on OS X platforms
  "de.sciss" %% "sonogramoverview"                % "1.7.1",  // sonogram component
  "de.sciss" %% "treetable-scala"                 % "1.3.6",  // tree-table component
  "de.sciss" %% "fscapejobs"                      % "1.4.1",  // remote FScape invocation
  "de.sciss" %% "strugatzki"                      % "2.4.1",  // feature extraction
  "de.sciss" %% "raphael-icons"                   % "1.0.2",  // icon set
  "de.sciss" %% "pdflitz"                         % "1.1.0",  // PDF export
  "de.sciss" %  "weblaf"                          % "1.27"    // Swing look-and-feel
)

// retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture")

// scalacOptions ++= Seq("-Xelide-below", "INFO")

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

initialCommands in console :=
  """import de.sciss.mellite._
    |import de.sciss.indeterminus._""".stripMargin

fork in run := true  // required for shutdown hook, and also the scheduled thread pool, it seems

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (version.value endsWith "-SNAPSHOT")
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
}


// ---- packaging ----

seq(appbundle.settings: _*)

appbundle.icon      := Some(file("icons") / "application.png")

appbundle.target    := baseDirectory.value

appbundle.signature := "Ttm "

appbundle.javaOptions ++= Seq("-XX:+CMSClassUnloadingEnabled", "-XX:+UseConcMarkSweepGC", "-XX:MaxPermSize=128m")

appbundle.documents += appbundle.Document(
  name       = "Mellite Document",
  role       = appbundle.Document.Editor,
  icon       = Some(file("icons") / "document.png"),
  extensions = Seq("mllt"),
  isPackage  = true
)

assemblySettings

target in assembly := baseDirectory.value

jarName in assembly := s"${name.value}.jar"

