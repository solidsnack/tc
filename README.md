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
java -jar build/app.jar
```

## Licensing

Copyright 2024 Jason Dusek

This software, except the items under `./buildSrc/`, which have their own,
more permissive license, is licensed under the Apache License, Version 2.0 (the
"License"); you may not use this software except in compliance with the
License.

You may obtain a copy of the License at:

       http://www.apache.org/licenses/LICENSE-2.0

It is also reproduced in this repository as `LICENSE`.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
