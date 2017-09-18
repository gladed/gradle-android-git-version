To release:

* Update version number in build.gradle
* Update version number in README.md.
* Run `./gradlew clean test publishPlugins`
* Commit any changes and tag the release version number with `git tag $VERSION`
* Run `git push origin master --tags` to sync with github.com
