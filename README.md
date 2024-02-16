# http-maven-receiver

Written in Scala 3, and (evolving) support for [Scala Native](https://github.com/stevenrskelton/http-maven-receiver/tree/main#scala-native).

Simple HTTP server that accepts HTTP PUT requests which are validated against GitHub Packages.
This allows for GitHub Actions to upload artifacts using unmetered bandwidth.

This avoids using metered bandwidth from *private* GitHub Packages to download Maven artifacts.

See https://www.stevenskelton.ca/data-transfers-github-actions/ additional information about this project.


### Why would you use this?

You are trying to maximumize the utility of the GitHub Free-tier for your private project.

### User Permissions 

This application doesn't manage any user permissions. It validates all PUT requests for:
- a valid GitHub auth token in the request HTTP headers, and
- valid Maven artifact in GitHub Packages.

If the file doesn't already exist on your server, and the user has permissions to read it, the server will accept the file upload request.

You probably don't want to use this with a *public* GitHub repo because you could be DDOS attacked, and it's pointless because then GitHub Packages has unlimited bandwidth.


## Two Deployment Parts

SBT build tasks
- `publishToGitHubPackages`: pushes compiled code to GitHub Packages (Maven)
- `uploadByPut`: pushes compiled code to your server (HTTP PUT)

HTTP Upload Server
- built on FS2, handles HTTP PUT
- validates upload is latest version in Maven, and validates upload correct MD5 checksum
- can optionally execute command after upload; useful for deployment and restarting

![Request Flow](./requests.drawio.svg)

### GitHub Action Install

(These assume you are using _"com.eed3si9n" % "sbt-assembly"_ to create executable jars that contain all necessary dependencies)

- Copy `publishToGitHubPackages.sbt` and `uploadByPut.sbt` to the root directory of your SBT project.
- Copy `upload-assembly-to-maven-put.yml` to the `.github/workflows` folder in your project.
- Create new `PUT_URI` environmental variable in your `upload-assembly-to-maven-put.yml` workflow, or just hard-code it in the YML file.

example:
```
PUT_URI="http://yourdomain.com:8080/upload"
```

Running this GitHub Action will compile your code, upload the artifact to GitHub Packages for the project, upload the file to your `PUT_URI` destination, and execute server-side actions all in 1 step.

### Server-side Receiver Install

Compile and run on your server using the appropriate command line arguments.

#### Command Line Arguments

- `http-maven-receiver.host` : Host/IP address to bind to.  _Required_
- `http-maven-receiver.port` : Port to bind to. _Default = 8080_
- `http-maven-receiver.file-directory` : Directory to upload to. _Default = "./files"_
- `http-maven-receiver.max-upload-size` : Maximum file size to handle. _Default = 1M_

example:
```
java -Dhost="192.168.0.1" -jar http-maven-receiver-assembly-0.1.0-SNAPSHOT.jar
```

## Post Upload Tasks

TODO: document param




# Scala Native

This project supports Scala Native compilation my making a few modifications. This is a poor application of Scala Native as it will diminish performance, but this isn't the type of application 
where performance is a concern.
See https://www.stevenskelton.ca/compiling-scala-native-github-actions-alternative-to-graalvm/

## Modifications Required to Enable Scala Native

Code blocks have been commented out in `build.sbt` and `project/plugins.sbt` which when uncommented will provide Scala Native support.
Code blocks will need to be commented out in `build.sbt` and `project/DisabledScalaNativePlugin.scala`.

## Compiling Scala Native

This project uses the `upload-native-linux-to-maven-put.yml` GitHub Action workflow to compile, so it can be used as a reference.

Follow the setup instructions https://scala-native.org/en/stable/user/setup.html

In addition, the AWS S2N-TLS (https://github.com/aws/s2n-tls) is required to be installed.
If it is not in a standard library path; modify `build.sbt` to specify the location manually.

```
nativeLinkingOptions += s"-L/home/runner/work/http-maven-receiver/http-maven-receiver/s2n-tls/s2n-tls-install/lib"
```

The `nativeLink` SBT task will produce the native executable as `/target/scala-3.3.1/http-maven-receiver-out`
