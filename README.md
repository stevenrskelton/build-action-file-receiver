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

## Project Install

(These assume you are using _"com.eed3si9n" % "sbt-assembly"_ to create uber jars)

Copy `publishAssemblyToGithubPackages.sbt` and `uploadAssemblyByPost.sbt` to the root directory of your project.
Copy `upload-assembly-to-maven-post.yml` to the `.github/workflows` folder in your project.
In `upload-assembly-to-maven-post.yml` set `POST_URI` to the URL you want to upload to, eg:
```
POST_URI="http://yourdomain.com:8080/upload"
```

Running this Github Action will compile your code, upload the artifact to Github Packages for the project, and then upload the file to your `POST_URI` destination.

## Server-side Reciever Install

Compile this Scala project, and run on your server. You will need to specify the IP address to bind the server to, and what port to use.
By default, this project will use port 8080.
Set the values in `application.conf`, or use command line arguments to set them, eg:
```
java -Dhttp-maven-receiver.host="192.168.0.1" -jar http-maven-receiver-assembly-0.1.0-SNAPSHOT.jar
```

