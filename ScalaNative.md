# Scala Native

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

Refer to `.github/workflows/build-action-file-receiver-scala-native.yml` for how the GitHub Action workflow is configured.

Follow the setup instructions https://scala-native.org/en/stable/user/setup.html

In addition, the AWS S2N-TLS (https://github.com/aws/s2n-tls) is required to be installed.  
If it is not in a standard library path; modify `build.sbt` to specify the location manually.

```scala
nativeLinkingOptions += s"-L/home/runner/work/build-action-file-receiver/build-action-file-receiver/s2n-tls/s2n-tls-install/lib"
```

The `nativeLink` SBT task will produce the native executable as `/target/scala-3.4.0/build-action-file-receiver-out`
