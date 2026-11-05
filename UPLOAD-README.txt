Kaelix 2.1.0, Minecraft 26.1.2 / Fabric Loader 0.19.5 / Fabric API 0.155.3+26.1.2 / Java 25.
Full corresponding source (all files listed in SOURCE-FILES.txt) — the same tree as patch kaelix-2.1.0.patch applied to main (f03e5ae), or kaelix-2.1.0-from-2.0.2.patch applied to the 2.0.2 commit.
Install the release JAR kaelix-2.1.0.jar (GitHub Releases) instead of the old JAR. JARs are not committed to git; pushing tag v2.1.0 builds and publishes the release (release.yml).
GitHub web upload ("Add files") does NOT delete old files: delete every path from the 2.0.2 and 2.1.0 sections of REMOVED-FILES.txt (2.1.0: root RELEASE-2.0.2.md), otherwise checkRemovedFiles stops the build with the list.
All Java sources are reformatted (palantir-java-format). Run ./gradlew spotlessApply on local branches before merging them onto 2.1.0.
Verified: ./gradlew clean build compileStressJava routeStress (Error Prone/NullAway, spotlessCheck, checkRemovedFiles, JUnit incl. old quickCheck) and client game tests in real Minecraft 26.1.2 under Xvfb. Not verified on Hypixel/network/Discord/sound.
See RELEASE-2.1.0.md / CHANGELOG.md / README.md / UPDATING.md. Preserve LICENSE/third_party/notices. No server permission or ban safety guarantee.
