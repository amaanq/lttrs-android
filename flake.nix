{
  description = "Ltt.rs Android email client";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    inputs:
    let
      system = "x86_64-linux";
      pkgs = import inputs.nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      androidComposition = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "35" ];
        buildToolsVersions = [ "35.0.0" ];
        includeNDK = false;
        includeSystemImages = false;
      };

      androidSdk = androidComposition.androidsdk;
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        buildInputs = [
          pkgs.jdk17
          pkgs.gradle
          androidSdk
        ];

        ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
        GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk}/libexec/android-sdk/build-tools/35.0.0/aapt2";
      };

      packages.${system}.default = pkgs.stdenv.mkDerivation {
        pname = "lttrs-android";
        version = "0.1.0";

        src = ./.;

        buildInputs = [
          pkgs.jdk17
          pkgs.gradle
          androidSdk
        ];

        buildPhase = ''
          export ANDROID_HOME=${androidSdk}/libexec/android-sdk
          export ANDROID_SDK_ROOT=${androidSdk}/libexec/android-sdk
          export GRADLE_USER_HOME=$TMPDIR/gradle

          gradle assembleRelease --no-daemon --offline
        '';

        installPhase = ''
          mkdir -p $out
          cp app/build/outputs/apk/release/*.apk $out/
        '';
      };
    };
}
