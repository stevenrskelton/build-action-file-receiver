package ca.stevenskelton.buildactionfilereceiver

import cats.effect.IO
import fs2.io.file.Path
import org.http4s.Request

class MultipartMainSpec extends MainSpec {
  override def createRequest(resource: Path, formFields: Map[AuthToken, AuthToken]): Request[IO] =
    UploadRequestHelper.multipartFilePutRequest(resource, formFields, requestUri)
}
