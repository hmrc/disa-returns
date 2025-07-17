import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.16.0"
  private val hmrcMongoVersion = "2.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "org.typelevel"     %% "cats-core"                 % "2.13.0",
    "io.lemonlabs"      %% "scala-uri"                 % "4.0.3"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test,
    "org.scalamock"     %% "scalamock"               % "7.4.0",
    "org.typelevel"     %% "cats-core"               % "2.13.0"
  )

  val it: Seq[ModuleID] = Seq(
    "org.typelevel"          %% "cats-core"          % "2.13.0",
    "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
  )
}
