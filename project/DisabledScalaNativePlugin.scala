import sbt.{AutoPlugin, Compile, Def, File, Setting, inConfig, taskKey}

object DisabledScalaNativePlugin extends AutoPlugin {
  object autoImport {
    val nativeLink = taskKey[File]("Generates native binary without running it.")
  }

  override lazy val globalSettings: Seq[Setting[_]] = inConfig(Compile)(Seq(
    autoImport.nativeLink := Def.task { ??? }.value
  ))
}
