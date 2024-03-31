# http-maven-receiver

Written in Scala 3, using FS2 / HTTP4s / Cats.

### A light-weight HTTP server accepting file uploads, validating authenticity with GitHub Packages Maven MD5 hashes.

Run this if you have a GitHub *private repo* and want to get your artifacts out of GitHub CI/CD using the unlimited 
egress bandwidth available to GitHub Actions.

#### ✅ Runs as fat-jar using the `java -jar` command Java JDK 17  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
![](https://github.com/stevenrskelton/http-maven-receiver/actions/workflows/http-maven-receiver-assembly-jar.yml/badge.svg)    

#### ✅ Runs as native assembly compiled with GraalVM JDK 21.0  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
![](https://github.com/stevenrskelton/http-maven-receiver/actions/workflows/http-maven-receiver-graal-native.yml/badge.svg)  

#### 🚫 Almost compiles using Scala Native 0.4.17  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
![](https://github.com/stevenrskelton/http-maven-receiver/actions/workflows/http-maven-receiver-scala-native.yml/badge.svg)  


## User Permissions

This application doesn't manage user permissions or security. It relies on GitHub auth tokens, and requires that:

- auth token is in HTTP request headers, and
- auth token has access to read GitHub Packages.

Since your GitHub Packages is a private repo, the auth token is secure.  
All requests without a valid token, or for repos not explicitly allowed by server config will be rejected.

*Do not use this for public repos.* It's not needed, download the files directly from GitHub Packages.

## SBT Tasks

The _http-maven-receivers.sbt_ file has 2 helper tasks:
- `publishToGitHubPackages` contains tasks to upload to GitHub Packages (Maven)
- `uploadByPut` contains tasks to upload to this server (HTTP PUT)

Your GitHub Action should call one of 3 tasks, depending on the artifact to be compiled:
- `httpMavenReceiverUploadAssembly` for fat-jar
- `httpMavenReceiverUploadGraalNative` for GraalVM native
- `httpMavenReceiverUploadScalaNative` for Scala Native

These tasks are dependent on external libraries, meaning SBT plugins need to be installed in _project/plugins.sbt_.  
If not using a particular artifact output, that SBT dependency can be omitted and the SBT task above can be removed
from _http-maven-receivers.sbt_ to avoid compilation errors.  

The SBT plugins required to be added to _project/plugins.sbt_ are:
- `addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")` for fat-jar
- `addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.2")` for GraalVM native
- `addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.16")` for Scala Native

![Request Flow](./requests.drawio.svg)

# Setup

## GitHub Action Install

- Copy `http-mave-receiver.sbt` to the root directory of your project.
- Add plugins to _project/plugins.sbt_ in your project.
- Copy `.github/workflows/http-maven-receiver-*.yml` to the `.github/workflows` folder in your project.
- Create new `PUT_URI` environmental variable in your `http-maven-receiver-*.yml` workflow, or hard-code it into
  the YML file in the `env` section.

example:
```shell
PUT_URI="http://yourdomain.com:8080/releases"
```

Running this GitHub Action will compile your code, upload the artifact to GitHub Packages, then upload the artifact to
the `PUT_URI` destination, and the receiver server optionally execute a server-side script.

## Server-side Receiver Install

This program is configured via command line arguments:

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

Run server with `--exec=script.sh`. Where *script.sh* contains:

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

Uses [SBT Native-Image plugin](https://github.com/scalameta/sbt-native-image).  

Uses [GraalVM](https://www.graalvm.org/downloads/) installed to `/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.2+13.1/Contents/Home`  

Run `nativeImageRunAgent` to capture files in `/META-INF/native-image/ca.stevenskelton/httpmavenreceiver`.  

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

Refer to `.github/workflows/http-maven-receiver-scala-native.yml` for how the GitHub Action workflow is configured.

Follow the setup instructions https://scala-native.org/en/stable/user/setup.html  

In addition, the AWS S2N-TLS (https://github.com/aws/s2n-tls) is required to be installed.  
If it is not in a standard library path; modify `build.sbt` to specify the location manually.  

```scala
nativeLinkingOptions += s"-L/home/runner/work/http-maven-receiver/http-maven-receiver/s2n-tls/s2n-tls-install/lib"
```

The `nativeLink` SBT task will produce the native executable as `/target/scala-3.4.0/http-maven-receiver-out`
