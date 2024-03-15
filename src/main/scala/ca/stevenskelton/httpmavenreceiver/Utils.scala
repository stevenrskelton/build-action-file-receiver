package ca.stevenskelton.httpmavenreceiver

object Utils {

  def byteArrayToHexString(bytes: Array[Byte]): String = {
    // this array of bytes has bytes in decimal format
    // so we need to convert it into hexadecimal format

    // for this we create an object of StringBuilder
    // since it allows us to update the string i.e. its
    // mutable
    val sb = new StringBuilder

    // loop through the bytes array
    for (i <- bytes.indices) { // the following line converts the decimal into
      // hexadecimal format and appends that to the
      // StringBuilder object
      sb.append(Integer.toString((bytes(i) & 0xff) + 0x100, 16).substring(1))
    }
    sb.toString
  }

  def humanReadableBytes(bytes: Long): String = {
    if (bytes > (1024 * 1024)) {
      s"${(bytes / (1024 * 1024)).toInt}mb"
    } else if (bytes > 1024) {
      s"${(bytes / 1024).toInt}kb"
    } else {
      s"$bytes bytes"
    }
  }

}
