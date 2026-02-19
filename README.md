Sunday 🙏 The code generator of REST
===

![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/outfoxx/sunday-generator/ci.yml?branch=main)
![Coverage](https://sonarcloud.io/api/project_badges/measure?project=outfoxx_sunday-generator&metric=coverage)
![Maven Central](https://img.shields.io/maven-central/v/io.outfoxx.sunday/generator.svg)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/https/oss.sonatype.org/io.outfoxx.sunday/generator.svg)

Code generator for Sunday client libraries and standard server libraries.

### [Read the Documentation](https://outfoxx.github.io/sunday)

Gradle Plugin Cache Notes
-------------------------

- The Gradle plugin now defaults to deterministic output for build/cache correctness by omitting generated timestamps unless explicitly set.
- Includes are discovered by parsing RAML sources; changes in nested include chains are tracked automatically.
- To opt in to a timestamp, set `generationTimestamp` on a generation (for example, a fixed string or a time you compute in the build script).


License
-------

    Copyright 2021 Outfox, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
