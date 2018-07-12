# Steps
1. Setup a pgp key
1. [Set up settings.xml](http://central.sonatype.org/pages/apache-maven.html)
1. **Make sure pom version is right (no -SNAPSHOT)**
1. Commit and push to github
1. `mvn clean deploy -Dmaven.test.skip -P public-release`
1. [Release the deployment from Nexus](http://central.sonatype.org/pages/releasing-the-deployment.html)
1. Tag the release and push to github 
1. **Increment the pom version with -SNAPSHOT**
1. Commit and push to github

# Known issues
* Inappropriate ioctl for device
   ```bash
   > GPG_TTY=$(tty)
   > export GPG_TTY
   ```
