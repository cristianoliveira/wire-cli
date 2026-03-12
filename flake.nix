{
  description = "Kotlin CLI with Clikt development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    kalium = {
      url = "git+file:./.local/kalium";
      flake = false;
    };
    self.submodules = true;
  };

  outputs = {
    self,
    nixpkgs,
    flake-utils,
    kalium,
  }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };
        jdk = pkgs.jdk17;
        kaliumSrc = kalium;
        gradleCmd = pkgs.writeShellScriptBin "gradle" ''
          if [ -x "./gradlew" ]; then
            exec ./gradlew "$@"
          fi

          exec ${pkgs.gradle}/bin/gradle "$@"
        '';
      in {
        packages.cli = pkgs.stdenvNoCC.mkDerivation {
          name = "wire-cli";

          src = self;

          nativeBuildInputs = with pkgs; [ git makeBinaryWrapper ];
          buildInputs = [ jdk ];

          buildPhase = ''
            export HOME="$TMPDIR"
            export GRADLE_USER_HOME="$TMPDIR/.gradle"
            mkdir -p "$GRADLE_USER_HOME"
            export JAVA_HOME="${jdk}"
            if [ -d "${jdk}/zulu-17.jdk/Contents/Home" ]; then
              export JAVA_HOME="${jdk}/zulu-17.jdk/Contents/Home"
            elif [ -d "${jdk}/Contents/Home" ]; then
              export JAVA_HOME="${jdk}/Contents/Home"
            fi
            export PATH="$JAVA_HOME/bin:$PATH"
            chmod +x gradlew

            # Support Kalium build scripts that expect a git worktree.
            git init
            git config user.email "build@nix"
            git config user.name "Nix Build"
            git add .
            git commit -m "Initial commit for build"

            mkdir -p .local
            cp -R "${kaliumSrc}" .local/kalium
            chmod -R u+w .local/kalium

            ./gradlew --no-daemon --console=plain --stacktrace \
              -Duser.home="$TMPDIR" \
              -Dorg.gradle.jvmargs="-Xmx2g -XX:+UseParallelGC" \
              -Pkalium.dir=.local/kalium \
              installDist
          '';

          installPhase = ''
            mkdir -p "$out/bin" "$out/lib"
            cp build/install/wire-cli/lib/* "$out/lib/"

            makeBinaryWrapper "${jdk}/bin/java" "$out/bin/cli" \
              --add-flags "-cp $out/lib/*" \
              --add-flags "wirecli.MainKt"
          '';
        };

        apps.cli = {
          type = "app";
          program = "${self.packages.${system}.cli}/bin/cli";
        };

        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            git
            just
            jdk17
            gradleCmd
            kotlin
            bats
            shellcheck
            prek
          ];

          shellHook = ''
            echo "Kotlin + Clikt dev shell ready"
            echo "Run: gradle run --args='--name Kotlin'"
          '';
        };
      });
}
