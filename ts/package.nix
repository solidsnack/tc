{
    lib,
    buildNpmPackage,
    importNpmLock,
}:

let
    package = lib.importJSON ./package.json;
in
buildNpmPackage {
    pname = "timecode-ts";
    inherit (package) version;

    # The package, and the test cases its tests read from ../specification.
    src = lib.fileset.toSource {
        root = ../.;
        fileset = lib.fileset.unions [
            ./package-lock.json
            ./package.json
            ./src
            ./test
            ./tsconfig.build.json
            ./tsconfig.json
            ../specification/tests.yaml
        ];
    };
    sourceRoot = "source/ts";

    # The dependencies in package-lock.json, with no hash to keep up to date.
    npmDeps = importNpmLock {
        inherit package;
        packageLock = lib.importJSON ./package-lock.json;
    };
    npmConfigHook = importNpmLock.npmConfigHook;

    doCheck = true;
    checkPhase = ''
        runHook preCheck
        npm test
        runHook postCheck
    '';

    meta = {
        inherit (package) description;
        license = lib.licenses.unlicense;
        mainProgram = "timecode";
    };
}
