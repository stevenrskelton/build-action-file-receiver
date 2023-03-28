import play.shaded.ahc.org.asynchttpclient.request.body.multipart.{FilePart, StringPart}
import play.shaded.ahc.org.asynchttpclient.{DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig}

import java.util.concurrent.TimeUnit
import scala.util.Try
import scala.xml.XML

lazy val uploadAssemblyToLunaNode = taskKey[Unit](s"Upload Jar to LunaNode via HTTP POST")
uploadAssemblyToLunaNode := {
  val githubToken = sys.env.getOrElse("GITHUB_TOKEN", throw new Exception("You must set environmental variable GITHUB_TOKEN"))
  val githubUser = sys.env.getOrElse("GITHUB_REPOSITORY_OWNER", throw new Exception("You must set environmental variable GITHUB_REPOSITORY_OWNER, eg: your username"))

  //  val url = "http://168.235.90.16/releases"
  val url = "http://172.81.182.36:8080/releases"
  //  val url = "http://127.0.0.1:8080/releases"

  val asyncHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder()
    .setMaxRequestRetry(0)
    .setShutdownQuietPeriod(0)
    .setShutdownTimeout(0).build
  val asyncHttpClient = new DefaultAsyncHttpClient(asyncHttpClientConfig)

  val githubRepository = name.value
  val githubGroupId = organization.value.replace(".", "/")
  val githubArtifactId = s"${name.value}-assembly"
  val githubVersion = version.value

  val mavenUrl = s"https://maven.pkg.github.com/$githubUser/$githubRepository/$githubGroupId/$githubArtifactId/$githubVersion/maven-metadata.xml"
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
        val assemblyJar = assembly.value
        val versionedFile = new File(assemblyJar.getAbsolutePath.replace("SNAPSHOT", mavenVersion))
        if (!assemblyJar.renameTo(versionedFile)) throw new Exception(s"Could not rename ${assemblyJar.getName} to ${versionedFile.getName}")
        val filename = assemblyJar.getName.replace("SNAPSHOT", mavenVersion)
        val postBuilder = asyncHttpClient.preparePost(url)

        val builder = postBuilder
          .addBodyPart(new StringPart("githubUser", githubUser))
          .addBodyPart(new StringPart("githubRepository", githubRepository))
          .addBodyPart(new StringPart("groupId", githubGroupId))
          .addBodyPart(new StringPart("artifactId", githubArtifactId))
          .addBodyPart(new StringPart("version", githubVersion))
          .addBodyPart(new FilePart("jar", versionedFile))

        println(s"Uploading ${assemblyJar.getName} to $url as filename $filename")
        val response = asyncHttpClient.executeRequest(builder.build()).toCompletableFuture.get(5, TimeUnit.MINUTES)
        if (response.hasResponseStatus) {
          response.getStatusCode match {
            case 200 => println(s"Upload successful: ${response.getResponseBody}")
            case status =>
              throw new Exception(s"Upload failed $status: ${Try(response.getResponseBody).getOrElse("")}")
          }
        } else {
          throw new Exception(s"Upload failed ${Try(response.getResponseBody).map(_.take(100)).getOrElse("")}")
        }
    }.getOrElse {
      println(s"No maven artifact created")
      throw new Exception(s"No maven artifact created")
    }
  } else {
    println(s"Maven failed with ${mavenMetadata.getStatusCode}")
    throw new Exception(s"Maven failed with ${mavenMetadata.getStatusCode} for $mavenUrl")
  }
}
