lazy val publishAssemblyToGitHubPackages = taskKey[Unit]("Publish Über Jar to GitHub Packages")
publishAssemblyToGitHubPackages := Def.taskDyn(publishToGitHubPackages(assembly.value)).value

lazy val publishNativeToGitHubPackages = taskKey[Unit]("Publish Native to GitHub Packages")
publishNativeToGitHubPackages := Def.taskDyn(publishToGitHubPackages((Compile / nativeLink).value)).value

def publishToGitHubPackages(fileToPublish: File): Def.Initialize[Task[Unit]] = Def.task {

  println(s"Publishing ${fileToPublish.getName} to GitHub Maven")

  val repository = sys.env.getOrElse("GITHUB_REPOSITORY", throw new Exception("You must set environmental variable GITHUB_REPOSITORY, eg: owner/repository"))
  if (!sys.env.keySet.contains("GITHUB_REPOSITORY_OWNER")) throw new Exception("You must set environmental variable GITHUB_REPOSITORY_OWNER, eg: your username")
  if (!sys.env.keySet.contains("GITHUB_TOKEN")) throw new Exception("You must set environmental variable GITHUB_TOKEN")

  val settingsXMLFile = new File(s"target/${fileToPublish.getName}-settings.xml")
  println(s"Writing ${fileToPublish.getName}-settings.xml")
  val settingsXML =
    """<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                    http://maven.apache.org/xsd/settings-1.0.0.xsd">

    <activeProfiles>
      <activeProfile>github</activeProfile>
    </activeProfiles>

    <profiles>
      <profile>
        <id>github</id>
        <repositories>
          <repository>
            <id>github</id>
            <url>https://maven.pkg.github.com/${GITHUB_REPOSITORY}</url>
            <snapshots>
              <enabled>true</enabled>
            </snapshots>
          </repository>
        </repositories>
      </profile>
    </profiles>

    <servers>
      <server>
        <id>github</id>
        <username>${GITHUB_REPOSITORY_OWNER}</username>
        <password>${GITHUB_TOKEN}</password>
      </server>
    </servers>
  </settings>"""
  IO.write(settingsXMLFile, settingsXML)

  val (artifactId: String, packaging: String) = {
    if (fileToPublish.getName.contains("-assembly-")) (s"${name.value}-assembly", "jar")
    else if (fileToPublish.getName.endsWith("-out")) (s"${name.value}-linux", "bin")
    else (name.value, "jar")
  }

  val exe =
    s"""mvn deploy:deploy-file
    -Durl=https://maven.pkg.github.com/$repository
    -DrepositoryId=github
    -Dfile=${fileToPublish.getAbsolutePath}
    -DgroupId=${organization.value}
    -DartifactId=$artifactId
    -Dpackaging=$packaging
    -Dversion=${version.value}
    --settings=target/${fileToPublish.getName}-settings.xml
  """.stripLineEnd

  println(s"Executing shell command $exe")
  import scala.sys.process._
  if (exe.! != 0) throw new Exception("publishAssemblyToGitHubPackages failed")
}
