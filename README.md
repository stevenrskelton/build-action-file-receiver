# http-maven-receiver

Written in Scala 3, using FS2 / HTTP4s / Cats.

Receives Scala artifacts uploaded during a GitHub Action.

**An HTTP server accepting HTTP PUT requests of files, validating authenticity against GitHub Packages Maven MD5
hashes.**

Run this if you have a *private repo* and want to get your artifacts out of GitHub using the unlimited egress bandwidth
available during GitHub Actions.

![Runs as fat-jar using the `java -jar` command](https://github.com/stevenrskelton/http-maven-receiver/actions/workflows/http-maven-receiver-assembly-jar.yml/badge.svg)  
![Runs as native assembly compiled with GraalVM](https://github.com/stevenrskelton/http-maven-receiver/actions/workflows/http-maven-receiver-graal-native.yml/badge.svg)  
![Almost compiles using Scala Native](https://github.com/stevenrskelton/http-maven-receiver/actions/workflows/http-maven-receiver-scala-native.yml/badge.svg)  

## User Permissions

This application doesn't manage user permissions or security. It relies on GitHub auth tokens, and requires that:

- auth token is in HTTP request headers, and
- auth token has access to read GitHub Packages.

Since your GitHub Packages is a private repo, the auth token is secure.  
All requests without a valid token, or for repos not explicitly allowed by startup parameter will be rejected.

*Do not use this for public repos.* It's not needed, download the files directly from GitHub Packages.

## SBT Tasks

Use these SBT files to initiate a file upload:

- *publishToGitHubPackages.sbt* contains tasks to upload to GitHub Packages (Maven):
  - `publishAssemblyToGitHubPackages` for fat-jar
  - `publishGraalNativeToGitHubPackages` for GraalVM native
  - `publishNativeToGitHubPackages` for Scala Native

- *uploadByPut.sbt* contains tasks to upload to this server (HTTP PUT)
  - `uploadAssemblyByPut` for fat-jar
  - `uploadGraalNativeByPut` for GraalVM native
  - `uploadNativeByPut` for Scala Native

These tasks are dependent on SBT plugins being installed in *project/plugins.sbt*.  
If not using an artifact output, the SBT task needs to be removed from the sbt file above to avoid compilation errors.  

The SBT plugins required are:
- `addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")` for fat-jar
- `addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.2")` for GraalVM native
- `addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.16")` for Scala Native

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

This project uses the `upload-native-linux-to-maven-put.yml` GitHub Action workflow to compile, so it can be used as a
reference.  

Follow the setup instructions https://scala-native.org/en/stable/user/setup.html  

In addition, the AWS S2N-TLS (https://github.com/aws/s2n-tls) is required to be installed.  
If it is not in a standard library path; modify `build.sbt` to specify the location manually.  

```scala
nativeLinkingOptions += s"-L/home/runner/work/http-maven-receiver/http-maven-receiver/s2n-tls/s2n-tls-install/lib"
```

The `nativeLink` SBT task will produce the native executable as `/target/scala-3.4.0/http-maven-receiver-out`
