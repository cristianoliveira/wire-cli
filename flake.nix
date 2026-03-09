{
  description = "Kotlin CLI with Clikt development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
        };
        gradleCmd = pkgs.writeShellScriptBin "gradle" ''
          if [ -x "./gradlew" ]; then
            exec ./gradlew "$@"
          fi

          exec ${pkgs.gradle}/bin/gradle "$@"
        '';
      in {
        packages.cli = pkgs.writeShellApplication {
          name = "cli";
          runtimeInputs = [ gradleCmd pkgs.jdk17 ];
          text = ''
            export JAVA_HOME="${pkgs.jdk17}"
            if [ $# -eq 0 ]; then
              exec gradle -Dorg.gradle.java.installations.paths="$JAVA_HOME" run
            else
              exec gradle -Dorg.gradle.java.installations.paths="$JAVA_HOME" run --args="$*"
            fi
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
          ];

          shellHook = ''
            echo "Kotlin + Clikt dev shell ready"
            echo "Run: gradle run --args='--name Kotlin'"
          '';
        };
      });
}
