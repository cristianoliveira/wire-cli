{
  description = "Wire CLI - A command-line interface for Wire messaging";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    build-gradle-application = {
      url = "github:raphiz/buildGradleApplication";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      build-gradle-application,
      ...
    }:
    let
      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forAllSystems = nixpkgs.lib.genAttrs supportedSystems;

      # Gradle version from wrapper properties
      gradleVersion = "9.1.0";

      # JDK version based on kotlin { jvmToolchain(17) }
      jdkVersion = 17;
    in
    {
      packages = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            overlays = [ build-gradle-application.overlays.default ];
          };

          jdk = pkgs."jdk${toString jdkVersion}";

          # Use gradleFromWrapper to match the project's gradle version
          gradle = pkgs.gradleFromWrapper {
            wrapperPropertiesPath = ./gradle/wrapper/gradle-wrapper.properties;
            defaultJava = jdk;
          };
        in
        {
          default = pkgs.buildGradleApplication {
            pname = "wire-cli";
            version = "0.1.0";

            src = ./.;

            inherit gradle;

            # Repositories must be explicitly specified since this project
            # does not use centralized repository declaration in settings.gradle.kts
            # See: https://github.com/raphiz/buildGradleApplication#rule-4-centralized-repository
            repositories = [
              "https://repo1.maven.org/maven2/"
              "https://dl.google.com/android/maven2/"
              "https://plugins.gradle.org/m2/"
            ];

            # Java toolchain for the build
            nativeBuildInputs = [ jdk ];

            meta = with pkgs.lib; {
              description = "A command-line interface for Wire messaging";
              homepage = "https://github.com/wireapp/wire-cli";
              license = licenses.gpl3Only;
              maintainers = [ ];
              mainProgram = "wire-cli";
              platforms = supportedSystems;
            };
          };
        }
      );

      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs { inherit system; };
          jdk = pkgs."jdk${toString jdkVersion}";
        in
        {
          default = pkgs.mkShell {
            buildInputs = with pkgs; [
              jdk
              gradle
              # Additional development tools
              nil # Nix LSP
              nixfmt-rfc-style # Nix formatter
            ];

            shellHook = ''
              echo "Wire CLI development environment"
              echo "Java: $(java --version | head -n1)"
              echo "Gradle: $(gradle --version | grep 'Gradle' | head -n1)"
              echo ""
              echo "To build: gradle build"
              echo "To run: gradle run"
              echo ""
              echo "NOTE: Before building with Nix, generate verification-metadata.xml:"
              echo "  gradle --refresh-dependencies --write-verification-metadata sha256 --write-locks dependencies"
            '';
          };
        }
      );

      # Formatter for `nix fmt`
      formatter = forAllSystems (system: (import nixpkgs { inherit system; }).nixfmt-rfc-style);
    };
}
