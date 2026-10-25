{ lib, rustPlatform }:

let
    cargo = lib.importTOML ./Cargo.toml;
in
rustPlatform.buildRustPackage {
    pname = cargo.package.name;
    inherit (cargo.package) version;

    # The crate, and the test cases its tests read from ../specification.
    src = lib.fileset.toSource {
        root = ../.;
        fileset = lib.fileset.unions [
            ./Cargo.lock
            ./Cargo.toml
            ./src
            ./tests
            ../specification/tests.yaml
        ];
    };
    sourceRoot = "source/rs";

    cargoLock.lockFile = ./Cargo.lock;

    buildFeatures = [ "cli" ];

    # The release profile is made for a small binary, with LTO, which is slow
    # to build the tests with.
    checkType = "debug";

    meta = {
        inherit (cargo.package) description;
        license = lib.licenses.unlicense;
        mainProgram = "timecode";
    };
}
