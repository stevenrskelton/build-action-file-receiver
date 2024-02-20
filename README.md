# http-maven-receiver

Written in Scala 3, and (evolving) support for [Scala Native](https://github.com/stevenrskelton/http-maven-receiver/tree/main#scala-native).

Simple HTTP server that accepts HTTP PUT requests which are validated against GitHub Packages.
This allows for GitHub Actions to upload artifacts using unmetered egress bandwidth.

This avoids using metered bandwidth from *private* GitHub Packages to download Maven artifacts.

See https://www.stevenskelton.ca/data-transfers-github-actions/ additional information about this project.


### Why would you use this?

You are trying to maximize the utility of the GitHub Free-tier for your private project.

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
- validates upload is the latest version in Maven, and validates upload has correct MD5 checksum
- can optionally execute command after upload; useful for deployment and restarting

![Request Flow](./requests.drawio.svg)

### GitHub Action Install

(These assume you are using _"com.eed3si9n" % "sbt-assembly"_ to create executable jars that contain all necessary dependencies)

- Copy `publishToGitHubPackages.sbt` and `uploadByPut.sbt` to the root directory of your SBT project.
- Copy `upload-assembly-to-maven-put.yml` to the `.github/workflows` folder in your project.
- Create new `PUT_URI` environmental variable in your `upload-assembly-to-maven-put.yml` workflow, or just hard-code it in the YML file.

example:
```
PUT_URI="http://yourdomain.com:8080/releases"
```

Running this GitHub Action will compile your code, upload the artifact to GitHub Packages for the project, upload the file to your `PUT_URI` destination, and execute server-side actions all in 1 step.

### Server-side Receiver Install

Compile and run on your server using the appropriate command line arguments.

#### Command Line Arguments

- `--disable-maven` : Do not validate against Maven, **This disables all security**
- `--allow-all-versions` : Allow upload of non-latest versions in Maven
- `--host=[STRING]` : Host/IP address to bind to.  _Required_
- `--port=[INTEGER]` : Port to bind to. _Default = 8080_
- `--max-upload-size=[STRING]` : Maximum file size to handle. _Default = 30M_
- `--exec=[STRING]` : Command to execute after successful upload.
- `--upload-directory=[STRING]` : Directory to upload to. _Default = "./files"_

example:
```
java -Dhost="192.168.0.1" -jar http-maven-receiver-assembly-0.1.0-SNAPSHOT.jar
```

## Post Upload Tasks

When the `exec` command is executed in the system shell, it will:  
run in `upload-directory` and have access to the following environment variables:

- `HMV_USER` : GitHub user/org
- `HMV_REPOSITORY` : GitHub repository
- `HMV_GROUPID` :  Maven groupId
- `HMV_ARTIFACTID` :  Maven artifactId
- `HMV_PACKAGING` :  Maven packaging (eg: _jar_, _bin_)
- `HMV_VERSION` :  Maven version
- `HMV_FILENAME` :  Local filename

### Sample

If using this to upload multiple project artifacts, it makes sense to use `if` statements in the filename:
```shell
#!/bin/bash

if [[ $HMV_FILENAME == project-assembly-* ]] ; then
    echo "Moving $HMV_FILENAME to /home/project"
    sudo -- chown project:project $HMV_FILENAME
    sudo -- mv $HMV_FILENAME /home/project/
    echo "Successfully installed new tradeaudit version $HMV_FILENAME"
fi
```

# Scala Native

This project is attempting to support Scala Native compilation.  **It currently doesn't compile.**

Scala Native isn't well suited to this application, it will diminish performance and is a headache with no upside, 
but why not? The rest of this README are working notes.

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
