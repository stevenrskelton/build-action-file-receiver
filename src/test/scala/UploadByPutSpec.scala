import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source
import scala.util.Using
import scala.xml.XML

class UploadByPutSpec extends AnyWordSpec with Matchers {

  /**
   * Code from uploadByPut.sbt
   * https://maven.pkg.github.com/$githubUser/$repository/$groupId/$artifactId/$version/maven-metadata.xml
   */
  def uploadByPut(fileToUpload: File, mavenMetadataXMLString: String): Option[File] = {
    val mavenMetadataXML = XML.loadString(mavenMetadataXMLString)
    (mavenMetadataXML \\ "snapshot").headOption.map {
      n =>
        val mavenVersion = s"${(n \ "timestamp").text}-${(n \ "buildNumber").text}"
        new File(fileToUpload.getAbsolutePath.replace("SNAPSHOT", mavenVersion))
    }
  }

  private lazy val mavenMetadataXMLString: String = {
    Using(Source.fromFile(new File("src/test/resources/maven/0.1.0-SNAPSHOT/maven-metadata.xml"))) {
      _.mkString
    }.get
  }

  "uploadByPut" should {
    "replace SNAPSHOT if found" in {
      val fileToUpload = new File("/some/path/artifact-assembly-1.0.1-SNAPSHOT.jar")
      val versionedFile = uploadByPut(fileToUpload, mavenMetadataXMLString)
      versionedFile.get.getAbsolutePath shouldBe "/some/path/artifact-assembly-1.0.1-20230330.234307-29.jar"
    }
  }

}
