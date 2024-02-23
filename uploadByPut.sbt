import play.shaded.ahc.org.asynchttpclient.request.body.multipart.{FilePart, StringPart}
import play.shaded.ahc.org.asynchttpclient.{DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig}

import java.util.concurrent.TimeUnit
import scala.util.Try
import scala.xml.XML

lazy val uploadAssemblyByPut = taskKey[Unit](s"Upload Jar via HTTP PUT")
uploadAssemblyByPut := Def.taskDyn(uploadByPut(assembly.value)).value

lazy val uploadNativeByPut = taskKey[Unit](s"Upload Native via HTTP PUT")
uploadNativeByPut := Def.taskDyn(uploadByPut((Compile / nativeLink).value)).value

def uploadByPut(fileToUpload: File): Def.Initialize[Task[Unit]] = Def.task {

  println(s"uploadByPut called for ${fileToUpload.getName}")

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
  val (artifactId: String, packaging: String) = {
    if (fileToUpload.getName.contains("-assembly-")) (s"${name.value}-assembly", "jar")
    else if (fileToUpload.getName.endsWith("-out")) (s"${name.value}-linux", "bin")
    else (name.value, "jar")
  }

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

  val builder = asyncHttpClient.preparePut(url)
    .addBodyPart(new StringPart("authToken", githubToken))
    .addBodyPart(new StringPart("user", githubUser))
    .addBodyPart(new StringPart("repository", repository))
    .addBodyPart(new StringPart("groupId", groupId))
    .addBodyPart(new StringPart("artifactId", artifactId))
    .addBodyPart(new StringPart("packaging", packaging))
    .addBodyPart(new StringPart("version", version.value))
    .addBodyPart(new FilePart("file", destinationFile))

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
