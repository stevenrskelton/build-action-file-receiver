package ca.stevenskelton.buildactionfilereceiver

import ca.stevenskelton.buildactionfilereceiver.Main.ExitException

case class UserRepository(user: String, repository: Option[String]):
  def matches(fileUploadFormData: FileUploadFormData): Boolean =
    user == fileUploadFormData.user && (repository.isEmpty || repository.contains(fileUploadFormData.repository))

object UserRepository:
  def parse(s: String): UserRepository =
    s.split('/').toList match
      case user :: Nil => UserRepository(user, None)
      case user :: repository :: Nil => UserRepository(user, Some(repository))
      case _ => throw ExitException(s"Could not parse user/repository `$s`")
