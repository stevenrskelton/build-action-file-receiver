# http-maven-receiver
Maximizing Github Free Tier as a CI/CD pipeline, using Scala/Java/JVM for almost everything.

- Github for private source repos.
- Github Actions for building.
- Github Packages (Maven) for release backups (500MB limit).
- Local JVM executes your deployment.

### Why would you use this?

Other tools like Ansible is probably better for you. Ansible has secure file upload and lots of plugins for simple server-side actions.

If you don't want to create SSH accounts, install clients (other than this one), or use custom scripting languages, maybe this works for you.

This project is basically server-side deployment scripts written in Scala, with Akka HTTP receiving builds from Github.


### User Permissions 

Upload permissions are limited to the ability to publish to Github Packages Maven.

Server-side permissions are completely internal to your server.


## Two Deployment Parts

SBT build tasks
- publishAssemblyToGithubPackages: pushes compiled code to Github Packages (Maven)
- uploadAssemblyByPut: pushes compiled code to your server (HTTP PUT)

HTTP Upload Server
- built on Akka, handles HTTP PUT
- validates upload is latest version in Maven, and has correct MD5 checksum
- performs any custom server-side tasks, such as deployment and restarting

![Request Flow](./requests.drawio.svg)

### Github Action Install

(These assume you are using _"com.eed3si9n" % "sbt-assembly"_ to create uber jars)

- Copy `publishAssemblyToGithubPackages.sbt` and `uploadAssemblyByPut.sbt` to the root directory of your project.
- Copy `upload-assembly-to-maven-put.yml` to the `.github/workflows` folder in your project.
- In `upload-assembly-to-maven-put.yml` set `PUT_URI` to the URL you want to upload to, eg:
```
PUT_URI="http://yourdomain.com:8080/upload"
```

Running this Github Action will compile your code, upload the artifact to Github Packages for the project, upload the file to your `PUT_URI` destination, and execute server-side actions all in 1 step.

### Server-side Receiver Install

Compile this Scala project, and run on your server. 

You will need to specify the IP address to bind the server to, optionally the port.

Set the values in `application.conf`, or use command line arguments to set them, eg:
```
java -Dhttp-maven-receiver.host="192.168.0.1" -jar http-maven-receiver-assembly-0.1.0-SNAPSHOT.jar
```
#### Command Line Params

- `http-maven-receiver.host` : Host/IP address to bind to.  _Required_
- `http-maven-receiver.port` : Port to bind to. _Default = 8080_
- `http-maven-receiver.file-directory` : Directory to upload to. _Default = "./files"_
- `http-maven-receiver.max-upload-size` : Maximum file size to handle. _Default = 1M_


## Post Upload Tasks

In Main.scala, the ArtifactUploadRoute takes a `Seq[AllowedGithubUser]` as a parameter.

Only uploads from these Github userid repositories will be allowed.  AllowedGithubUser defines:

```
def postHook(file: File): Future[Done]
```

This can be used to perform any actions on the uploaded `File`.
A simple example would be to move this file out of the upload folder to somewhere else.

eg:
```
sys.process.Process(s"sudo -- mv ${file.getAbsolutePath} /home/hosted/").!
```
