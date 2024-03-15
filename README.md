# http-maven-receiver

Written in Scala 3, using FS2/Cats.

Receives Scala artifacts uploaded during a GitHub Action.

**An HTTP server accepting HTTP PUT requests of files, validating authenticity against GitHub Packages Maven MD5
hashes.**

Run this if you have a *private repo* and want to get your artifacts out of GitHub using the unlimited egress bandwidth
available during GitHub Actions.

✅ Runs as fat-jar using the `java -jar` command.  
✅ Runs as native assembly compiled with GraalVM.  
🚫 Almost compiles using Scala Native.  

## User Permissions

This application doesn't manage user permissions. It validates all PUT requests for:

- auth token is in HTTP request headers, and
- auth token has access to read GitHub Packages.

Since your GitHub Packages is for a private repo, this token is all the security you need.  
All requests without a valid token, or for repos not explicitly allowed will be rejected.

*Do not use this for public repos, download the files directly from GitHub Packages.*

## SBT Tasks

This is a server which accepts file uploads, use these SBT tasks to upload:

- `publishToGitHubPackages`: uploads to GitHub Packages (Maven)
- `uploadByPut`: uploads to this server (HTTP PUT)

![Request Flow](./requests.drawio.svg)

# Setup

## GitHub Action Install

(These assume you are using _"com.eed3si9n" % "sbt-assembly"_ to create executable jars that containing all necessary
dependencies)

- Copy `publishToGitHubPackages.sbt` and `uploadByPut.sbt` to the root directory of your SBT project.
- Copy `upload-assembly-to-maven-put.yml` to the `.github/workflows` folder in your project.
- Create new `PUT_URI` environmental variable in your `upload-assembly-to-maven-put.yml` workflow, or hard-code it into
  the YML file.

example:
```shell
PUT_URI="http://yourdomain.com:8080/releases"
```

Running this GitHub Action will compile your code, upload the artifact to GitHub Packages, then upload the artifact to
the `PUT_URI` destination, and optionally execute a server-side script.

## Server-side Receiver Install

Compile this project, and run using appropriate command line arguments:

### Command Line Arguments

- `--disable-maven` : Do not validate against Maven, **DISABLED ALL SECURITY**
- `--allow-all-versions` : Allow upload of non-latest versions in Maven
- `--allowed-repos=[STRING]` : Comma-separated list of allowed repositories
- `--host=[STRING]` : Host/IP address to bind to.  _REQUIRED_
- `--port=[INTEGER]` : Port to bind to. _DEFAULT: 8080_
- `--exec=[STRING]` : Command to execute after successful upload.
- `--upload-directory=[STRING]` : Directory to upload to. _DEFAULT: "./files"_

JVM example:

```shell
java -Xmx=40m -Dhost="192.168.0.1" -jar http-maven-receiver-assembly-1.1.1.jar
```

GraalVM / Scala Native example:

```shell
./http-maven-receiver --host="192.168.0.1"
```

### Post Upload Tasks

When an `exec` command is specified, it will be run after a successful upload has been completed and verified.
It will run in the system shell with the current directory set to the `upload-directory` and have the following
environment variables set:

- `HMV_USER` : GitHub user/org
- `HMV_REPOSITORY` : GitHub repository
- `HMV_GROUPID` :  Maven groupId
- `HMV_ARTIFACTID` :  Maven artifactId
- `HMV_PACKAGING` :  Maven packaging (eg: _jar_, _bin_)
- `HMV_VERSION` :  Maven version
- `HMV_FILENAME` :  Local filename

#### Sample

This sample script has actions depending on the artifact name, allowing it to handle multiple repositories:

```shell
#!/bin/bash

if [[ $HMV_FILENAME == project-assembly-* ]] ; then
    echo "Moving $HMV_FILENAME to /home/project"
    sudo -- chown project:project $HMV_FILENAME
    sudo -- mv $HMV_FILENAME /home/project/
    echo "Successfully installed new version $HMV_FILENAME"
fi
```

# About

See https://www.stevenskelton.ca/examples/#http-maven-receiver for additional information.

## GraalVM on MacOS M1

Uses [SBT NativeImage plugin](https://github.com/scalameta/sbt-native-image).  
Uses [GraalVM](https://www.graalvm.org/downloads/) installed to   
`/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.2+13.1/Contents/Home`  

Run `nativeImageRunAgent` to capture files in  
`/META-INF/native-image/ca.stevenskelton/httpmavenreceiver`.
Run `nativeImage` to compile http-maven-receiver (executable).  

## Scala Native

Attempting to support Scala Native compilation.  
**Currently doesn't compile.**

👎Scala Native isn't well suited to this application, it will diminish performance and is a headache with no upside,
but why not? The rest of this document are working notes.  

See https://www.stevenskelton.ca/compiling-scala-native-github-actions-alternative-to-graalvm/  

## Modifications Required to Enable Scala Native

Code blocks have been commented out in `build.sbt` and `project/plugins.sbt` which when uncommented will provide Scala
Native support.  
Code blocks will need to be commented out in `build.sbt` and `project/DisabledScalaNativePlugin.scala`.

## Compiling Scala Native

This project uses the `upload-native-linux-to-maven-put.yml` GitHub Action workflow to compile, so it can be used as a
reference.  

Follow the setup instructions https://scala-native.org/en/stable/user/setup.html  

In addition, the AWS S2N-TLS (https://github.com/aws/s2n-tls) is required to be installed.  
If it is not in a standard library path; modify `build.sbt` to specify the location manually.  

```scala
nativeLinkingOptions += s"-L/home/runner/work/http-maven-receiver/http-maven-receiver/s2n-tls/s2n-tls-install/lib"
```

The `nativeLink` SBT task will produce the native executable as `/target/scala-3.4.0/http-maven-receiver-out`
