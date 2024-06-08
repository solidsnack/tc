## Building & Running the Application

Build the shadow JAR with Gradle:

```bash
./gradlew shadowJar
```

Or:

```bash
./gradlew --offline shadowJar
```

Then run the copy of the JAR:

```bash
java -jar build/cli.jar
```
