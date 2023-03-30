# http-maven-receiver
HTTP server that receives artifact uploads and verifies MD5 against Maven.

## Two Parts

SBT build tasks
- publishAssemblyToGithubPackages: uploads compiled code to Github Packages (Maven)
- uploadAssemblyByPost: uploads compiled code to your server (HTTP POST)

HTTP Upload Server
- built on Akka, handles HTTP POST
- validates upload is latest version in Maven, and has correct MD5 checksum
- performs any custom server-side tasks, such as deployment and restarting

![Request Flow](./requests.drawio.svg)

