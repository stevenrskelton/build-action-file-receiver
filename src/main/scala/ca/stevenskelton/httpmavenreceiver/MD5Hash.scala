package ca.stevenskelton.httpmavenreceiver

case class MD5Hash(value: String) extends AnyVal

object MD5Hash {
  val Empty: MD5Hash = MD5Hash("")
}
