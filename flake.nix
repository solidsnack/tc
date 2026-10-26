{
    description = "TimeCodes: sortable, short timestamps";

    inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

    outputs =
        { self, nixpkgs }:
        let
            inherit (nixpkgs) lib;

            systems = [
                "aarch64-darwin"
                "aarch64-linux"
                "x86_64-linux"
            ];

            # An attribute for each system, made from its packages.
            each = f: lib.genAttrs systems (s: f nixpkgs.legacyPackages.${s});
        in
        {
            # For building the command line tools with other packages, as in
            # a Home Manager configuration.
            overlays.default = final: _: {
                timecode = final.callPackage ./rs/package.nix { };
                timecode-ts = final.callPackage ./ts/package.nix { };
            };

            # Both command line tools are called timecode, so a profile can
            # have only one of them.
            packages = each (pkgs: rec {
                default = timecode;
                timecode = pkgs.callPackage ./rs/package.nix { };
                timecode-ts = pkgs.callPackage ./ts/package.nix { };
            });

            # Cargo links with the C compiler, which only mkShell brings.
            devShells = each (pkgs: rec {
                rs = pkgs.mkShell {
                    packages = with pkgs; [
                        cargo
                        clippy
                        just
                        rust-analyzer
                        rustc
                        rustfmt
                    ];
                };
                ts = pkgs.mkShellNoCC {
                    packages = with pkgs; [
                        just
                        nodejs
                    ];
                };
                # The JDK of jvmToolchain(17), in build.gradle.kts. The Gradle
                # wrapper brings Gradle, which brings Kotlin.
                kt = pkgs.mkShellNoCC {
                    packages = with pkgs; [
                        jdk17_headless
                        just
                    ];
                };
                rosetta = pkgs.mkShellNoCC {
                    packages = with pkgs; [
                        duckdb
                        # For the Bash port's --git, and its tests.
                        git
                        go
                        just
                        nodejs
                        postgresql
                        # For the Python port: its build backend and
                        # frontend, type checker and tests.
                        (python3.withPackages (ps: [
                            ps.build
                            ps.hatchling
                            ps.mypy
                            ps.pytest
                            ps.pyyaml
                        ]))
                        rubocop
                        ruby
                        ruff
                    ];
                };
                default = pkgs.mkShell {
                    inputsFrom = [
                        rs
                        ts
                        kt
                        rosetta
                    ];
                };
            });

            # Building the packages runs their tests.
            checks = lib.mapAttrs (_: packages: {
                inherit (packages) timecode timecode-ts;
            }) self.packages;
        };
}
