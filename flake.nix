{
  description = "Wire CLI - A command-line interface for Wire messaging";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    build-gradle-application = {
      url = "github:raphiz/buildGradleApplication";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    # Kalium submodule as a separate input
    # kalium = {
    #   url = "github:wireapp/kalium";
    #   flake = false;
    # };
    kalium = {
      url = "github:cristianoliveira/kalium/6764d0ff048fd78e02c21feec9636df74303950d";
      flake = false;
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      build-gradle-application,
      kalium,
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

          # Combine main source with kalium submodule in vendor/kalium
          srcWithKalium = pkgs.runCommand "wire-cli-source" { buildInputs = [ pkgs.protobuf ]; } ''
            cp -r ${./.} $out
            # Make everything writable so we can modify files
            chmod -R u+w $out
            mkdir -p $out/vendor
            cp -r ${kalium} $out/vendor/kalium
            chmod -R u+w $out/vendor/kalium

            # Patch buildSrc to use centralized repository declaration
            # This allows buildGradleApplication to replace repositories with the offline maven repo

            # Update buildSrc/settings.gradle.kts to use centralized repository management
            cat > $out/vendor/kalium/buildSrc/settings.gradle.kts << 'EOF'
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    google()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
                }
                versionCatalogs {
                    create("libs") {
                        from(files("../gradle/libs.versions.toml"))
                    }
                }
            }
            EOF

            # Remove repositories block from buildSrc/build.gradle.kts since we use FAIL_ON_PROJECT_REPOS
            sed -i '/^repositories {/,/^}/d' $out/vendor/kalium/buildSrc/build.gradle.kts

            # Remove com.wire:detekt-rules dependency - it's hosted on a custom Ivy repo (GitHub raw)
            # which doesn't follow Maven patterns. Since detekt is only for linting and nix build
            # doesn't run detekt, we can safely remove this dependency.
            # Remove the entire block: detektPlugins("com.wire:detekt-rules:...") { isChanging = true }
            sed -i '/detektPlugins("com.wire:detekt-rules:/,/}/d' $out/vendor/kalium/buildSrc/src/main/kotlin/scripts/detekt.gradle.kts

            # Patch main kalium settings.gradle.kts to use centralized repository management
            # This is required for buildGradleApplication to replace repositories with the offline maven repo
            # Replace the dependencyResolutionManagement block with one that includes FAIL_ON_PROJECT_REPOS
            sed -i '/^dependencyResolutionManagement {/,/^}/c\
            dependencyResolutionManagement {\
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\
                repositories {\
                    mavenCentral()\
                }\
                versionCatalogs {\
                    create("awssdk") {\
                        from("aws.sdk.kotlin:version-catalog:1.5.89")\
                    }\
                }\
            }' $out/vendor/kalium/settings.gradle.kts

            # Remove buildscript repositories block from kalium/build.gradle.kts since we use FAIL_ON_PROJECT_REPOS
            # This is needed because buildscript dependencies will come from pluginManagement in settings
            sed -i '/^buildscript {/,/^}/s/repositories {[^}]*}//' $out/vendor/kalium/build.gradle.kts || true

            # Remove repositories block from kalium/build.gradle.kts since we use FAIL_ON_PROJECT_REPOS
            # This block includes wireDetektRulesRepo() which is an Ivy repo that doesn't follow Maven patterns
            sed -i '/^repositories {/,/^}/d' $out/vendor/kalium/build.gradle.kts

            # Also remove repositories block from allprojects block (lines 123-126)
            # This is needed because FAIL_ON_PROJECT_REPOS doesn't allow any project-level repositories
            sed -i '/allprojects {/,/}/s/repositories {[^}]*}//' $out/vendor/kalium/build.gradle.kts || true
            # More robust: remove the repositories block inside allprojects
            sed -i '/allprojects {/,/^}/{
                /^[[:space:]]*repositories {/,/^[[:space:]]*}/d
            }' $out/vendor/kalium/build.gradle.kts

            # Patch main wire-cli settings.gradle.kts to use centralized repository management
            # This ensures the main project also uses the offline maven repo
            sed -i '1a\
            dependencyResolutionManagement {\
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\
                repositories {\
                    mavenCentral()\
                    google()\
                }\
            }' $out/settings.gradle.kts
            # Remove repositories block from main build.gradle.kts since we use FAIL_ON_PROJECT_REPOS
            sed -i '/^repositories {/,/^}/d' $out/build.gradle.kts

            # Patch protobuf plugin to use nix protoc instead of downloading from Maven
            # This is needed because downloaded executables don't have execute permissions in nix store
            sed -i 's|artifact = "com.google.protobuf:protoc:3.24.0"|path = "${pkgs.protobuf}/bin/protoc"|' \
              $out/vendor/kalium/tools/protobuf-codegen/build.gradle.kts

                        # Disable Android Gradle Plugin analytics to avoid sandbox issues
                        echo "android.disableAnalytics=true" >> $out/gradle.properties

                        # For Nix builds, simply comment out iOS/Apple target creation in logic/build.gradle.kts
                        # The logic module explicitly creates iOS targets which fail in sandbox without SDK
                        sed -i '42,52s/^/\/\/ /' $out/vendor/kalium/logic/build.gradle.kts

            # Also remove the detekt-rules entry from verification-metadata.xml
            # This prevents Gradle from trying to verify and download the non-existent artifact
            sed -i '/<component group="com.wire" name="detekt-rules"/,/<\/component>/d' $out/gradle/verification-metadata.xml
          '';
        in
        {
          default = pkgs.buildGradleApplication {
            pname = "wire-cli";
            version = "0.1.0";

            src = srcWithKalium;

            inherit gradle;

            # Repositories must be explicitly specified since this project
            # does not use centralized repository declaration in settings.gradle.kts
            # See: https://github.com/raphiz/buildGradleApplication#rule-4-centralized-repository
            repositories = [
              "https://repo1.maven.org/maven2/"
              "https://dl.google.com/android/maven2/"
              "https://plugins.gradle.org/m2/"
            ];

            # Java toolchain, git, and protobuf for the build
            # git is needed by kalium's build
            # protobuf is needed for protoc (protobuf compiler)
            nativeBuildInputs = [
              jdk
              pkgs.git
              pkgs.protobuf
            ];

            # This is a CLI, but Kalium/Gradle plugins can still initialize Android/Kotlin
            # metrics paths. In the Nix sandbox HOME may resolve to /var/empty, so we force
            # writable paths to avoid stalls/warnings during configuration.
            env = {
              JAVA_HOME = "${jdk}";
              GITHUB_SHA = "nixbuild";
              HOME = "/tmp/wire-cli-home";
              GRADLE_USER_HOME = "/tmp/wire-cli-gradle";
              ANDROID_USER_HOME = "/tmp/wire-cli-android";
              XDG_CACHE_HOME = "/tmp/wire-cli-xdg-cache";
              XDG_CONFIG_HOME = "/tmp/wire-cli-xdg-config";
              XDG_DATA_HOME = "/tmp/wire-cli-xdg-data";
              GRADLE_OPTS = "-Dnix.build=true -Duser.home=/tmp/wire-cli-home -Dgradle.user.home=/tmp/wire-cli-gradle";
            };

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

              # Nix build tools
              devenv

              # Testing
              bats
              # Additional development tools
              nil # Nix LSP
              nixfmt-rfc-style # Nix formatter
              # Git hooks
              prek
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
