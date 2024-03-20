import play.shaded.ahc.org.asynchttpclient.request.body.multipart.{FilePart, StringPart}
import play.shaded.ahc.org.asynchttpclient.{DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig}

import java.util.concurrent.TimeUnit
import scala.util.Try
import scala.xml.XML

lazy val httpMavenReceiverUploadAssembly = taskKey[Unit]("Upload Jar via HTTP PUT to Maven Receiver")
httpMavenReceiverUploadAssembly := Def.taskDyn {
  val fileToUpload = (Compile / assembly).value
  uploadByPut(fileToUpload, vm = "assembly", useMultipart = false)
    .map(_ => publishToGitHubPackages(fileToUpload, vm ="assembly"))
}.value

lazy val httpMavenReceiverUploadGraalNative = taskKey[Unit]("Upload Graal Native via HTTP PUT to Maven Receiver")
httpMavenReceiverUploadGraalNative := Def.taskDyn {
  val fileToUpload = (Compile / nativeImage).value
  uploadByPut(fileToUpload, vm = "graalvm", useMultipart = false)
    .map(_ => publishToGitHubPackages(fileToUpload, vm ="graalvm"))
}.value

lazy val httpMavenReceiverUploadScalaNative = taskKey[Unit]("Upload Scala Native via HTTP PUT to Maven Receiver")
httpMavenReceiverUploadGraalNative := Def.taskDyn {
  val fileToUpload = (Compile / nativeLink).value
  uploadByPut(fileToUpload, vm = "scala-native", useMultipart = false)
    .map(_ => publishToGitHubPackages(fileToUpload, vm ="scala-native"))
}.value

def publishToGitHubPackages(fileToPublish: File, vm: String): Def.Initialize[Task[Unit]] = Def.task {

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

  val (artifactId, packaging) = vmToArtifact(vm)

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
  if (exe.! != 0) throw new Exception("publishToGitHubPackages failed")
}

def uploadByPut(fileToUpload: File, vm: String, useMultipart: Boolean = false): Def.Initialize[Task[Unit]] = Def.task {

  println(s"uploadByPut called for ${fileToUpload.getName} on $vm")

  val githubToken = sys.env.getOrElse("GITHUB_TOKEN", throw new Exception("You must set environmental variable GITHUB_TOKEN"))
  val githubUser = sys.env.getOrElse("GITHUB_REPOSITORY_OWNER", throw new Exception("You must set environmental variable GITHUB_REPOSITORY_OWNER, eg: your GitHub username"))
  val url = sys.env.getOrElse("PUT_URI", throw new Exception("You must set environmental variable PUT_URI to the PUT destination"))

  val asyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
    .setMaxRequestRetry(0)
    .setShutdownQuietPeriod(0)
    .setShutdownTimeout(0).build
  val asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)

  val repository = name.value
  val groupId = organization.value.replace(".", "/")

  val (artifactId, packaging) = vmToArtifact(vm)

  val destinationFile = if (version.value.contains("SNAPSHOT")) {
    val mavenUrl = s"https://maven.pkg.github.com/$githubUser/$repository/$groupId/$artifactId/${version.value}/maven-metadata.xml"

    val mavenMetadata = asyncHttpClient.prepareGet(mavenUrl)
      .addHeader("Authorization", s"token $githubToken")
      .execute()
      .toCompletableFuture
      .get(5, TimeUnit.SECONDS)

    println(s"Download Maven metadata $mavenUrl")
    if (mavenMetadata.getStatusCode == 200) {
      val mavenMetadataXML = XML.loadString(mavenMetadata.getResponseBody)
      (mavenMetadataXML \\ "snapshot").headOption.map {
        n =>
          val mavenVersion = s"${(n \ "timestamp").text}-${(n \ "buildNumber").text}"
          println(s"Latest Maven upload is $mavenVersion")
          val versionedFile = new File(fileToUpload.getAbsolutePath.replace("SNAPSHOT", mavenVersion))
          if (!fileToUpload.renameTo(versionedFile)) throw new Exception(s"Could not rename ${fileToUpload.getName} to ${versionedFile.getName}")
          versionedFile
      }.getOrElse {
        val msg = s"No maven artifact created"
        println(msg)
        throw new Exception(msg)
      }
    } else {
      val msg = s"Maven failed with ${mavenMetadata.getStatusCode} for $mavenUrl"
      println(msg)
      throw new Exception(msg)
    }
  } else {
    fileToUpload
  }

  val builder = if(useMultipart){
    asyncHttpClient.preparePut(url)
      .addBodyPart(new StringPart("authToken", githubToken))
      .addBodyPart(new StringPart("user", githubUser))
      .addBodyPart(new StringPart("repository", repository))
      .addBodyPart(new StringPart("groupId", groupId))
      .addBodyPart(new StringPart("artifactId", artifactId))
      .addBodyPart(new StringPart("packaging", packaging))
      .addBodyPart(new StringPart("version", version.value))
      .addBodyPart(new FilePart("file", destinationFile))
  } else {
    asyncHttpClient.preparePut(url)
      .addHeader("X-authToken", githubToken)
      .addHeader("X-user", githubUser)
      .addHeader("X-repository", repository)
      .addHeader("X-groupId", groupId)
      .addHeader("X-artifactId", artifactId)
      .addHeader("X-packaging", packaging)
      .addHeader("X-version", version.value)
      .addHeader("X-file", destinationFile.getName)
      .setBody(destinationFile)
  }

  println(s"Uploading ${fileToUpload.getName} to $url as filename ${destinationFile.getName}")
  val response = asyncHttpClient.executeRequest(builder.build()).toCompletableFuture.get(5, TimeUnit.MINUTES)
  if (response.hasResponseStatus) {
    response.getStatusCode match {
      case 200 => println(s"Upload successful: ${response.getResponseBody}")
      case status =>
        val msg = s"Upload failed $status: ${Try(response.getResponseBody).getOrElse("")}"
        println(msg)
        throw new Exception(msg)
    }
  } else {
    val msg = s"Upload failed ${Try(response.getResponseBody).map(_.take(100)).getOrElse("")}"
    println(msg)
    throw new Exception(msg)
  }

}

private def vmToArtifact(vm: String): (String, String) = {
  vm match {
    case "assembly" => (s"${name.value}-assembly", "jar")
    case "graalvm" => (s"${name.value}-graal-linux", "bin")
    case "scala-native" => (s"${name.value}-linux", "bin")
    case _ => (name.value, "jar")
  }
}